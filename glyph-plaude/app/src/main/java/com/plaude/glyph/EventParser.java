package com.plaude.glyph;

import com.plaude.glyph.core.EventAnimation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lee la animación que manda el backend de Glyph Events:
 * {"v":1,"frames":[...],"frameMs":100,"repeat":2,"brightness":100}.
 * Cada cuadro es un array de 13 filas o un string de 169 caracteres.
 */
public final class EventParser {

    private EventParser() {
    }

    public static EventAnimation parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONArray arr = o.getJSONArray("frames");
        List<String[]> frames = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            Object f = arr.get(i);
            try {
                if (f instanceof String) {
                    frames.add(EventAnimation.frame((String) f));
                } else {
                    JSONArray rows = (JSONArray) f;
                    String[] r = new String[rows.length()];
                    for (int y = 0; y < r.length; y++) r[y] = rows.getString(y);
                    frames.add(r);
                }
            } catch (ClassCastException | IllegalArgumentException e) {
                throw new JSONException("cuadro " + i + ": " + e.getMessage());
            }
        }
        try {
            return new EventAnimation(frames, o.optLong("frameMs", 100), o.optInt("repeat", 1),
                    o.optInt("brightness", 100));
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }
}
