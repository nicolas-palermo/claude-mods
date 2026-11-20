package com.plaude.glyph;

import android.util.Log;

import com.plaude.glyph.core.Config;
import com.plaude.glyph.core.RenderContext.DataValue;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Baja los datos en vivo de glyph.json (clima, búsquedas de GitHub) cada algunos minutos. */
final class LiveData {

    private static final String TAG = "PlaudeGlyph";
    private static final long INTERVAL_MS = 10 * 60 * 1000L;
    private static final long RETRY_MS = 2 * 60 * 1000L;

    private static final Map<String, DataValue> VALUES = new ConcurrentHashMap<>();
    private static final Map<String, Long> NEXT_FETCH = new ConcurrentHashMap<>();
    private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();

    private LiveData() {
    }

    static Map<String, DataValue> values() {
        return Collections.unmodifiableMap(new HashMap<>(VALUES));
    }

    static Map<String, String> errors() {
        return Collections.unmodifiableMap(new HashMap<>(ERRORS));
    }

    /** Pide en segundo plano las fuentes que estén vencidas. */
    static void refresh(Config cfg, boolean force, Runnable onChange) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Config.DataSource> e : cfg.data.entrySet()) {
            String key = e.getKey();
            Long next = NEXT_FETCH.get(key);
            if (!force && next != null && now < next) continue;
            NEXT_FETCH.put(key, now + RETRY_MS);
            Config.DataSource src = e.getValue();
            new Thread(() -> {
                try {
                    DataValue v = fetch(src);
                    DataValue old = VALUES.put(key, v);
                    ERRORS.remove(key);
                    NEXT_FETCH.put(key, System.currentTimeMillis() + INTERVAL_MS);
                    if (onChange != null && (old == null || !old.text.equals(v.text)
                            || !String.valueOf(old.icon).equals(String.valueOf(v.icon)))) {
                        onChange.run();
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "No pude bajar el dato " + key, ex);
                    ERRORS.put(key, String.valueOf(ex.getMessage()));
                }
            }, "glyph-data-" + key).start();
        }
    }

    private static DataValue fetch(Config.DataSource src) throws Exception {
        switch (src.type) {
            case "weather":
                return weather(src);
            case "github":
                return github(src);
            default:
                throw new IllegalArgumentException("tipo desconocido " + src.type);
        }
    }

    /** Open-Meteo: sin clave. Muestra la temperatura redondeada y un ícono según el código WMO. */
    private static DataValue weather(Config.DataSource src) throws Exception {
        String lat = src.param("lat", "-34.6037");
        String lon = src.param("lon", "-58.3816");
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code&timezone=auto";
        JSONObject cur = new JSONObject(Http.get(url, null)).getJSONObject("current");
        long temp = Math.round(cur.getDouble("temperature_2m"));
        return new DataValue(temp + "°", weatherIcon(cur.getInt("weather_code")));
    }

    static String weatherIcon(int code) {
        if (code <= 1) return "sun";
        if (code <= 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if (code >= 95) return "storm";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        return "rain";
    }

    /** Cuenta resultados de una búsqueda de GitHub (issues y PRs). Sin token: 10 por minuto. */
    private static DataValue github(Config.DataSource src) throws Exception {
        String q = src.param("query", "is:pr is:open author:nicolas-palermo");
        String url = "https://api.github.com/search/issues?per_page=1&q="
                + URLEncoder.encode(q, StandardCharsets.UTF_8.name());
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/vnd.github+json");
        int count = new JSONObject(Http.get(url, h)).getInt("total_count");
        return new DataValue(count > 999 ? "999" : String.valueOf(count), src.param("icon", "pr"));
    }
}
