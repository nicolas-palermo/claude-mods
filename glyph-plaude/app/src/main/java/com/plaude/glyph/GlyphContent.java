package com.plaude.glyph;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Contenido del toy, leído de glyph.json. Formato:
 *
 * {
 *   "screens": [
 *     { "type": "bitmap", "rows": ["13 caracteres", ...13 filas] },
 *     { "type": "clock" }
 *   ]
 * }
 *
 * Si hay varias pantallas, rota una por minuto. En "rows": '#' lleno, '+' media, '.' apagado.
 */
final class GlyphContent {

    static final int SIZE = 13;
    private static final int FULL = 255;
    private static final int HALF = 128;

    // Dígitos de 3x5 para el reloj.
    private static final String[][] DIGITS = {
            {"###", "#.#", "#.#", "#.#", "###"},
            {".#.", "##.", ".#.", ".#.", "###"},
            {"###", "..#", "###", "#..", "###"},
            {"###", "..#", "###", "..#", "###"},
            {"#.#", "#.#", "###", "..#", "..#"},
            {"###", "#..", "###", "..#", "###"},
            {"###", "#..", "###", "#.#", "###"},
            {"###", "..#", ".#.", ".#.", ".#."},
            {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "###"},
    };

    private final List<Object> screens; // String[] (bitmap) o "clock"

    private GlyphContent(List<Object> screens) {
        this.screens = screens;
    }

    static GlyphContent parse(String json) throws JSONException {
        JSONArray arr = new JSONObject(json).getJSONArray("screens");
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            String type = s.optString("type", "bitmap");
            if ("clock".equals(type)) {
                list.add("clock");
            } else {
                JSONArray rows = s.getJSONArray("rows");
                String[] r = new String[SIZE];
                for (int y = 0; y < SIZE; y++) r[y] = y < rows.length() ? rows.getString(y) : "";
                list.add(r);
            }
        }
        if (list.isEmpty()) throw new JSONException("glyph.json no tiene pantallas");
        return new GlyphContent(list);
    }

    static GlyphContent ofBitmap(String[] rows) {
        List<Object> list = new ArrayList<>();
        list.add(rows);
        return new GlyphContent(list);
    }

    int[] frameAt(Calendar now) {
        int minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        Object screen = screens.get(minuteOfDay % screens.size());
        if (screen instanceof String[]) return bitmap((String[]) screen);
        return clock(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    private static int[] bitmap(String[] rows) {
        int[] frame = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            String row = rows[y] == null ? "" : rows[y];
            for (int x = 0; x < SIZE && x < row.length(); x++) {
                char c = row.charAt(x);
                frame[y * SIZE + x] = c == '#' ? FULL : c == '+' ? HALF : 0;
            }
        }
        return frame;
    }

    private static int[] clock(int h, int m) {
        int[] frame = new int[SIZE * SIZE];
        drawDigit(frame, h / 10, 3, 1);
        drawDigit(frame, h % 10, 7, 1);
        drawDigit(frame, m / 10, 3, 7);
        drawDigit(frame, m % 10, 7, 7);
        return frame;
    }

    private static void drawDigit(int[] frame, int d, int x0, int y0) {
        String[] g = DIGITS[d];
        for (int y = 0; y < 5; y++)
            for (int x = 0; x < 3; x++)
                if (g[y].charAt(x) == '#') frame[(y0 + y) * SIZE + x0 + x] = FULL;
    }
}
