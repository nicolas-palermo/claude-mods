package com.plaude.glyph;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** Cada sonido se baja una sola vez: queda en files/sounds/ con su clave (id + versión). */
final class SoundCache {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private SoundCache() {
    }

    static synchronized File get(Context ctx, String key, String url) throws IOException {
        File dir = new File(ctx.getFilesDir(), "sounds");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no pude crear " + dir);
        File f = new File(dir, hash(key));
        if (f.isFile() && f.length() > 0) return f;
        File tmp = new File(dir, f.getName() + ".tmp");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " bajando el sonido");
            long total = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("el sonido pesa más de 5 MB");
                    out.write(buf, 0, n);
                }
            }
        } finally {
            c.disconnect();
        }
        if (!tmp.renameTo(f)) throw new IOException("no pude guardar el sonido");
        return f;
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
