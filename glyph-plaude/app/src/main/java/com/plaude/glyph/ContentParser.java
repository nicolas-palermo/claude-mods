package com.plaude.glyph;

import com.plaude.glyph.core.Config;
import com.plaude.glyph.core.Grid;
import com.plaude.glyph.core.Icons;
import com.plaude.glyph.core.Playlist;
import com.plaude.glyph.core.Screen;
import com.plaude.glyph.core.Screens;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Convierte glyph.json en un Config. Ver el README para el formato. */
public final class ContentParser {

    private static final long DEFAULT_SCREEN_MS = 60_000;

    private ContentParser() {
    }

    public static Config parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);

        JSONArray arr = root.getJSONArray("screens");
        List<Playlist.Entry> entries = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            Screen screen = screen(s, i);
            long ms = s.has("seconds") ? (long) (s.getDouble("seconds") * 1000) : defaultMs(screen);
            entries.add(new Playlist.Entry(screen, ms));
        }
        if (entries.isEmpty()) throw new JSONException("glyph.json no tiene pantallas");

        Config.Notifications notifications = notifications(root.optJSONObject("notifications"));

        Map<String, Config.DataSource> data = new LinkedHashMap<>();
        JSONObject d = root.optJSONObject("data");
        if (d != null) {
            for (Iterator<String> it = d.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject src = d.getJSONObject(key);
                Map<String, String> params = new LinkedHashMap<>();
                for (Iterator<String> pk = src.keys(); pk.hasNext(); ) {
                    String p = pk.next();
                    params.put(p, String.valueOf(src.get(p)));
                }
                String type = src.optString("type", key);
                if (!type.equals("weather") && !type.equals("github")) {
                    throw new JSONException("data." + key + ": tipo desconocido '" + type + "'");
                }
                data.put(key, new Config.DataSource(type, params));
            }
        }

        for (Playlist.Entry e : entries) {
            if (e.screen instanceof Screens.Data) {
                String src = ((Screens.Data) e.screen).source();
                if (!data.containsKey(src)) {
                    throw new JSONException("pantalla de datos usa '" + src + "' pero no está en \"data\"");
                }
            }
        }

        return new Config(root.optInt("brightness", 100), new Playlist(entries), notifications, data);
    }

    private static long defaultMs(Screen screen) {
        if (screen instanceof Screens.Text) {
            // Al menos una pasada completa del texto.
            return Math.max(10_000, ((Screens.Text) screen).cycleMs());
        }
        return DEFAULT_SCREEN_MS;
    }

    private static Screen screen(JSONObject s, int index) throws JSONException {
        String type = s.optString("type", "bitmap");
        String where = "screens[" + index + "] (" + type + ")";
        switch (type) {
            case "bitmap":
                return new Screens.Bitmap(rows(s.getJSONArray("rows"), where));
            case "animation": {
                JSONArray fr = s.getJSONArray("frames");
                List<String[]> frames = new ArrayList<>();
                for (int i = 0; i < fr.length(); i++) {
                    frames.add(rows(fr.getJSONArray(i), where + " frame " + i));
                }
                if (frames.isEmpty()) throw new JSONException(where + ": sin frames");
                long frameMs = s.has("fps") ? Math.round(1000.0 / s.getDouble("fps")) : s.optLong("frameMs", 150);
                return new Screens.Animation(frames, frameMs, s.optBoolean("loop", true));
            }
            case "clock":
                return new Screens.Clock(s.optBoolean("blink", true));
            case "text":
                return new Screens.Text(s.getString("text"), s.optInt("speed", 12));
            case "data":
                return new Screens.Data(s.getString("source"), s.optString("icon", "dot"));
            case "icon": {
                String name = s.getString("icon");
                if (Icons.get(name) == null) throw new JSONException(where + ": ícono desconocido '" + name + "'");
                return new Screens.Icon(name, s.optBoolean("blink", false));
            }
            default:
                throw new JSONException(where + ": tipo de pantalla desconocido");
        }
    }

    private static String[] rows(JSONArray a, String where) throws JSONException {
        if (a.length() != Grid.SIZE) throw new JSONException(where + ": tiene que tener 13 filas");
        String[] r = new String[Grid.SIZE];
        for (int i = 0; i < Grid.SIZE; i++) {
            r[i] = a.getString(i);
            if (r[i].length() != Grid.SIZE) {
                throw new JSONException(where + ": la fila " + i + " no tiene 13 caracteres");
            }
        }
        return r;
    }

    private static Config.Notifications notifications(JSONObject n) throws JSONException {
        Config.Notifications def = Config.Notifications.defaults();
        if (n == null) return def;
        Map<String, String> apps = new LinkedHashMap<>(def.apps);
        JSONObject a = n.optJSONObject("apps");
        if (a != null) {
            for (Iterator<String> it = a.keys(); it.hasNext(); ) {
                String pkg = it.next();
                if (a.isNull(pkg)) {
                    apps.remove(pkg);
                } else {
                    apps.put(pkg, a.getString(pkg));
                }
            }
        }
        String defIcon = n.has("default") && !n.isNull("default") ? n.getString("default") : null;
        return new Config.Notifications(
                n.optBoolean("enabled", true),
                (long) (n.optDouble("seconds", def.showMs / 1000.0) * 1000),
                n.optBoolean("calls", true),
                apps,
                defIcon);
    }
}
