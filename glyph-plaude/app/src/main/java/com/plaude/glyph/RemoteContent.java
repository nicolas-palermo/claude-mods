package com.plaude.glyph;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.plaude.glyph.core.Config;

/**
 * Baja glyph.json del repo y lo guarda. Así, cambiar lo que muestra el Glyph es solo un push:
 * el toy lo levanta en la próxima actualización (cada pocos minutos).
 */
final class RemoteContent {

    static final String URL_JSON =
            "https://raw.githubusercontent.com/nicolas-palermo/claude-mods/main/glyph-plaude/glyph.json";

    private static final String TAG = "PlaudeGlyph";
    private static final String PREFS = "glyph";
    private static final String KEY_JSON = "json";
    private static final String KEY_FETCHED_AT = "fetched_at";
    private static final String KEY_ERROR = "error";
    private static final long MIN_INTERVAL_MS = 3 * 60 * 1000L;

    private static long lastAttempt = 0;
    private static volatile boolean fetching = false;
    private static String cachedJson;
    private static Config cachedConfig;

    private RemoteContent() {
    }

    /** El último glyph.json válido, o el logo si todavía no hay nada. */
    static synchronized Config config(Context ctx) {
        String json = prefs(ctx).getString(KEY_JSON, null);
        if (json == null) return Config.fallback();
        if (json.equals(cachedJson) && cachedConfig != null) return cachedConfig;
        try {
            cachedConfig = ContentParser.parse(json);
            cachedJson = json;
            return cachedConfig;
        } catch (Exception e) {
            Log.w(TAG, "glyph.json guardado inválido", e);
            return Config.fallback();
        }
    }

    static long fetchedAt(Context ctx) {
        return prefs(ctx).getLong(KEY_FETCHED_AT, 0);
    }

    /** El último error al bajar o leer glyph.json, o null si salió bien. */
    static String lastError(Context ctx) {
        return prefs(ctx).getString(KEY_ERROR, null);
    }

    /** Baja glyph.json en segundo plano si pasó suficiente tiempo (o si force). */
    static void refresh(Context ctx, boolean force, Runnable onChange) {
        long now = System.currentTimeMillis();
        if (fetching || (!force && now - lastAttempt < MIN_INTERVAL_MS)) return;
        lastAttempt = now;
        fetching = true;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            SharedPreferences p = prefs(app);
            try {
                String json = Http.get(URL_JSON + "?t=" + now, null);
                ContentParser.parse(json); // valida antes de guardar
                boolean changed = !json.equals(p.getString(KEY_JSON, null));
                p.edit().putString(KEY_JSON, json).putLong(KEY_FETCHED_AT, now).remove(KEY_ERROR).apply();
                if (changed && onChange != null) onChange.run();
            } catch (Exception e) {
                Log.w(TAG, "No pude actualizar glyph.json", e);
                p.edit().putString(KEY_ERROR, e.getMessage()).apply();
            } finally {
                fetching = false;
            }
        }, "glyph-fetch").start();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
