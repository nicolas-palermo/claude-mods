package com.plaude.glyph;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Baja glyph.json del repo y lo guarda. Así, cambiar el dibujo es solo un push:
 * el toy lo levanta en la próxima actualización (cada pocos minutos).
 */
final class RemoteContent {

    static final String URL_JSON =
            "https://raw.githubusercontent.com/nicolas-palermo/claude-mods/main/glyph-plaude/glyph.json";

    private static final String TAG = "PlaudeGlyph";
    private static final String PREFS = "glyph";
    private static final String KEY_JSON = "json";
    private static final long MIN_INTERVAL_MS = 3 * 60 * 1000L;

    private static long lastFetch = 0;
    private static volatile boolean fetching = false;

    private RemoteContent() {
    }

    /** Lo último que se bajó, o null si todavía no hay nada guardado. */
    static GlyphContent cached(Context ctx) {
        String json = prefs(ctx).getString(KEY_JSON, null);
        if (json == null) return null;
        try {
            return GlyphContent.parse(json);
        } catch (Exception e) {
            Log.w(TAG, "glyph.json guardado inválido", e);
            return null;
        }
    }

    /** Baja glyph.json en segundo plano si pasó suficiente tiempo; llama a onChange si cambió. */
    static void refresh(Context ctx, Runnable onChange) {
        long now = System.currentTimeMillis();
        if (fetching || now - lastFetch < MIN_INTERVAL_MS) return;
        lastFetch = now;
        fetching = true;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                String json = download(URL_JSON + "?t=" + now);
                GlyphContent.parse(json); // valida antes de guardar
                SharedPreferences p = prefs(app);
                if (!json.equals(p.getString(KEY_JSON, null))) {
                    p.edit().putString(KEY_JSON, json).apply();
                    if (onChange != null) onChange.run();
                }
            } catch (Exception e) {
                Log.w(TAG, "No pude bajar glyph.json", e);
            } finally {
                fetching = false;
            }
        }, "glyph-fetch").start();
    }

    private static String download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setUseCaches(false);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
