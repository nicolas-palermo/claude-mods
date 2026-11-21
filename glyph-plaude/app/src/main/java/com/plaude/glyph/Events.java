package com.plaude.glyph;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Glyph Events del lado del teléfono: con qué backend está vinculado, cómo arrancar Firebase sin
 * google-services.json (la config la da el backend al vincular) y cómo avisarle el token de push.
 */
final class Events {

    private static final String TAG = "PlaudeGlyph";
    private static final String PREFS = "events";

    interface Done {
        void run(String error);
    }

    private Events() {
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String baseUrl(Context ctx) {
        return prefs(ctx).getString("base", null);
    }

    static boolean paired(Context ctx) {
        return baseUrl(ctx) != null && prefs(ctx).getString("fb_app_id", null) != null;
    }

    static Map<String, String> authHeaders(Context ctx) {
        Map<String, String> h = new HashMap<>();
        h.put("x-device-key", prefs(ctx).getString("key", ""));
        return h;
    }

    /** Arranca Firebase con la config guardada. Se llama al iniciar cada proceso. */
    static synchronized boolean initFirebase(Context ctx) {
        if (!FirebaseApp.getApps(ctx).isEmpty()) return true;
        SharedPreferences p = prefs(ctx);
        String appId = p.getString("fb_app_id", null);
        if (appId == null) return false;
        try {
            FirebaseOptions o = new FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setApiKey(p.getString("fb_api_key", ""))
                    .setProjectId(p.getString("fb_project_id", ""))
                    .setGcmSenderId(p.getString("fb_sender_id", ""))
                    .build();
            FirebaseApp.initializeApp(ctx.getApplicationContext(), o);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "No pude iniciar Firebase", e);
            return false;
        }
    }

    /**
     * Vincula con el backend: baja la config de Firebase, pide el token de push y lo registra.
     * Corre en un hilo propio; done recibe null si salió bien o el error.
     */
    static void pair(Context ctx, String base, String key, Done done) {
        Context app = ctx.getApplicationContext();
        String b = base.replaceAll("/+$", "");
        new Thread(() -> {
            try {
                Map<String, String> h = new HashMap<>();
                h.put("x-device-key", key);
                JSONObject cfg = new JSONObject(Http.get(b + "/api/device/config", h)).getJSONObject("firebase");
                SharedPreferences.Editor ed = prefs(app).edit()
                        .putString("base", b)
                        .putString("key", key)
                        .putString("fb_app_id", cfg.getString("appId"))
                        .putString("fb_api_key", cfg.getString("apiKey"))
                        .putString("fb_project_id", cfg.getString("projectId"))
                        .putString("fb_sender_id", cfg.getString("senderId"));
                ed.commit();
                if (!initFirebase(app)) throw new IllegalStateException("Firebase no arrancó: revisá la config del backend");
                FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
                    if (!t.isSuccessful() || t.getResult() == null) {
                        Exception e = t.getException();
                        done.run("No pude obtener el token de push: " + (e == null ? "?" : e.getMessage()));
                        return;
                    }
                    registerToken(app, t.getResult(), done);
                });
            } catch (Exception e) {
                Log.e(TAG, "Vinculación falló", e);
                done.run(e.getMessage());
            }
        }, "events-pair").start();
    }

    /** Manda el token de FCM al backend (al vincular y cada vez que Firebase lo rota). */
    static void registerToken(Context ctx, String token, Done done) {
        Context app = ctx.getApplicationContext();
        String base = baseUrl(app);
        if (base == null) {
            if (done != null) done.run("No está vinculado");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("token", token)
                        .put("model", Build.MANUFACTURER + " " + Build.MODEL)
                        .put("appVersion", BuildConfigCompat.versionName(app));
                Http.postJson(base + "/api/device/register", authHeaders(app), body.toString());
                prefs(app).edit().putLong("registered_at", System.currentTimeMillis()).remove("error").apply();
                if (done != null) done.run(null);
            } catch (Exception e) {
                Log.e(TAG, "Registro del token falló", e);
                prefs(app).edit().putString("error", e.getMessage()).apply();
                if (done != null) done.run(e.getMessage());
            }
        }, "events-register").start();
    }

    /** Le cuenta al backend qué pasó con una reproducción (para el historial). Bloqueante. */
    static void ack(Context ctx, String playId, String status, String detail) {
        String base = baseUrl(ctx);
        if (base == null || playId == null) return;
        try {
            JSONObject body = new JSONObject().put("playId", playId).put("status", status);
            if (detail != null) body.put("detail", detail);
            Http.postJson(base + "/api/device/ack", authHeaders(ctx), body.toString());
        } catch (Exception e) {
            Log.w(TAG, "ack falló", e);
        }
    }

    static void recordPlay(Context ctx, String what) {
        prefs(ctx).edit().putLong("last_play_at", System.currentTimeMillis()).putString("last_play", what).apply();
    }
}
