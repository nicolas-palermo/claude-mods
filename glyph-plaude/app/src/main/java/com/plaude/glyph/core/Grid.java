package com.plaude.glyph.core;

import java.util.Arrays;

/**
 * La matriz del Nothing Phone (4a) Pro: 13x13 celdas, de las que existen 137 (un círculo).
 * Cada celda guarda un nivel de 0 a 255. Lo que cae fuera del círculo se ignora.
 */
public final class Grid {

    public static final int SIZE = 13;
    public static final int ON = 255;
    public static final int HALF = 128;
    /** Máximo del array crudo que espera el SDK (GlyphMatrixUtils escala a 0..4095). */
    public static final int SDK_MAX = 4095;

    private static final boolean[] MASK = new boolean[SIZE * SIZE];
    public static final int LED_COUNT;

    static {
        int n = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int dx = x - 6, dy = y - 6;
                boolean in = dx * dx + dy * dy <= 42.25;
                MASK[y * SIZE + x] = in;
                if (in) n++;
            }
        }
        LED_COUNT = n;
    }

    private final int[] px = new int[SIZE * SIZE];

    public static boolean exists(int x, int y) {
        return x >= 0 && y >= 0 && x < SIZE && y < SIZE && MASK[y * SIZE + x];
    }

    public Grid clear() {
        Arrays.fill(px, 0);
        return this;
    }

    public int get(int x, int y) {
        return exists(x, y) ? px[y * SIZE + x] : 0;
    }

    public Grid set(int x, int y, int level) {
        if (exists(x, y)) px[y * SIZE + x] = Math.max(0, Math.min(ON, level));
        return this;
    }

    /** Pone la celda al máximo entre lo que había y level (para superponer capas). */
    public Grid lighten(int x, int y, int level) {
        if (exists(x, y)) px[y * SIZE + x] = Math.max(px[y * SIZE + x], Math.min(ON, level));
        return this;
    }

    /**
     * Dibuja filas de texto: '#' lleno, '+' medio, '1'..'9' niveles (9 = lleno), cualquier otra
     * cosa no toca la celda. Con opaque=true, los '.' (y '0') apagan la celda.
     */
    public Grid drawRows(String[] rows, int ox, int oy, boolean opaque) {
        for (int y = 0; y < rows.length; y++) {
            String r = rows[y];
            if (r == null) continue;
            for (int x = 0; x < r.length(); x++) {
                char c = r.charAt(x);
                if (c == '#') set(ox + x, oy + y, ON);
                else if (c == '+') set(ox + x, oy + y, HALF);
                else if (c >= '1' && c <= '9') set(ox + x, oy + y, (c - '0') * ON / 9);
                else if (opaque) set(ox + x, oy + y, 0);
            }
        }
        return this;
    }

    public Grid drawRows(String[] rows) {
        return drawRows(rows, 0, 0, true);
    }

    /** Dibuja centrado en la grilla (ancho y alto de las filas). */
    public Grid drawCentered(String[] rows) {
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        return drawRows(rows, (SIZE - w) / 2, (SIZE - rows.length) / 2, false);
    }

    public Grid drawText(String text, int x, int y, int level) {
        Font.draw(this, text, x, y, level);
        return this;
    }

    /** Texto centrado horizontalmente en la fila y. */
    public Grid drawTextCentered(String text, int y, int level) {
        int w = Font.width(text);
        return drawText(text, (SIZE - w + 1) / 2, y, level);
    }

    /** Multiplica todos los niveles por pct/100. */
    public Grid dim(int pct) {
        for (int i = 0; i < px.length; i++) px[i] = px[i] * pct / 100;
        return this;
    }

    public Grid copyFrom(Grid other) {
        System.arraycopy(other.px, 0, px, 0, px.length);
        return this;
    }

    /** Niveles 0..255, 169 valores, fila por fila. */
    public int[] levels() {
        return px.clone();
    }

    /** Array listo para setMatrixFrame / setAppMatrixFrame (0..4095), con brillo general en %. */
    public int[] toSdkFrame(int brightnessPct) {
        int pct = Math.max(0, Math.min(100, brightnessPct));
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            out[i] = MASK[i] ? (int) ((long) px[i] * SDK_MAX * pct / (ON * 100L)) : 0;
        }
        return out;
    }

    public int litCount() {
        int n = 0;
        for (int v : px) if (v > 0) n++;
        return n;
    }

    /** Para depurar y para los tests: '#', '+', '.' y ' ' fuera del círculo. */
    public String toAscii() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int v = px[y * SIZE + x];
                sb.append(!MASK[y * SIZE + x] ? ' ' : v >= 192 ? '#' : v > 0 ? '+' : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
