package com.plaude.glyph.core;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fuente de 3x5 píxeles pensada para la matriz de 13x13. */
public final class Font {

    public static final int HEIGHT = 5;
    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    private static void g(char c, String... rows) {
        GLYPHS.put(c, rows);
    }

    static {
        g('0', "###", "#.#", "#.#", "#.#", "###");
        g('1', ".#.", "##.", ".#.", ".#.", "###");
        g('2', "###", "..#", "###", "#..", "###");
        g('3', "###", "..#", ".##", "..#", "###");
        g('4', "#.#", "#.#", "###", "..#", "..#");
        g('5', "###", "#..", "###", "..#", "###");
        g('6', "###", "#..", "###", "#.#", "###");
        g('7', "###", "..#", "..#", ".#.", ".#.");
        g('8', "###", "#.#", "###", "#.#", "###");
        g('9', "###", "#.#", "###", "..#", "###");
        g('A', ".#.", "#.#", "###", "#.#", "#.#");
        g('B', "##.", "#.#", "##.", "#.#", "##.");
        g('C', ".##", "#..", "#..", "#..", ".##");
        g('D', "##.", "#.#", "#.#", "#.#", "##.");
        g('E', "###", "#..", "##.", "#..", "###");
        g('F', "###", "#..", "##.", "#..", "#..");
        g('G', ".##", "#..", "#.#", "#.#", ".##");
        g('H', "#.#", "#.#", "###", "#.#", "#.#");
        g('I', "###", ".#.", ".#.", ".#.", "###");
        g('J', "..#", "..#", "..#", "#.#", ".#.");
        g('K', "#.#", "#.#", "##.", "#.#", "#.#");
        g('L', "#..", "#..", "#..", "#..", "###");
        g('M', "#.#", "###", "###", "#.#", "#.#");
        g('N', "##.", "#.#", "#.#", "#.#", "#.#");
        g('O', ".#.", "#.#", "#.#", "#.#", ".#.");
        g('P', "##.", "#.#", "##.", "#..", "#..");
        g('Q', ".#.", "#.#", "#.#", "##.", ".##");
        g('R', "##.", "#.#", "##.", "#.#", "#.#");
        g('S', ".##", "#..", ".#.", "..#", "##.");
        g('T', "###", ".#.", ".#.", ".#.", ".#.");
        g('U', "#.#", "#.#", "#.#", "#.#", "###");
        g('V', "#.#", "#.#", "#.#", "#.#", ".#.");
        g('W', "#.#", "#.#", "###", "###", "#.#");
        g('X', "#.#", "#.#", ".#.", "#.#", "#.#");
        g('Y', "#.#", "#.#", ".#.", ".#.", ".#.");
        g('Z', "###", "..#", ".#.", "#..", "###");
        g(' ', "..", "..", "..", "..", "..");
        g('.', ".", ".", ".", ".", "#");
        g(',', ".", ".", ".", "#", "#");
        g(':', ".", "#", ".", "#", ".");
        g('!', "#", "#", "#", ".", "#");
        g('\'', "#", "#", ".", ".", ".");
        g('?', "###", "..#", ".#.", "...", ".#.");
        g('-', "...", "...", "###", "...", "...");
        g('+', "...", ".#.", "###", ".#.", "...");
        g('/', "..#", "..#", ".#.", "#..", "#..");
        g('%', "#.#", "..#", ".#.", "#..", "#.#");
        g('°', "##", "##", "..", "..", "..");
        g('<', "..#", ".#.", "#..", ".#.", "..#");
        g('>', "#..", ".#.", "..#", ".#.", "#..");
        g('#', "#.#", "###", "#.#", "###", "#.#");
        g('*', "...", "#.#", ".#.", "#.#", "...");
        g('=', "...", "###", "...", "###", "...");
        g('(', ".#", "#.", "#.", "#.", ".#");
        g(')', "#.", ".#", ".#", ".#", "#.");
        g('♥', ".#.#.", "#####", "#####", ".###.", "..#..");
    }

    private Font() {
    }

    /** Pasa a mayúsculas y saca acentos; lo que no existe en la fuente se vuelve '?'. */
    public static String normalize(String text) {
        if (text == null) return "";
        String up = text.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            if (GLYPHS.containsKey(c)) {
                sb.append(c);
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            char b = base.isEmpty() ? '?' : base.charAt(0);
            sb.append(GLYPHS.containsKey(b) ? b : '?');
        }
        return sb.toString();
    }

    public static String[] glyph(char c) {
        String[] g = GLYPHS.get(c);
        return g != null ? g : GLYPHS.get('?');
    }

    /** Ancho en píxeles, con 1 de separación entre letras. */
    public static int width(String text) {
        String t = normalize(text);
        int w = 0;
        for (int i = 0; i < t.length(); i++) {
            if (i > 0) w += 1;
            w += glyph(t.charAt(i))[0].length();
        }
        return w;
    }

    public static void draw(Grid grid, String text, int x, int y, int level) {
        String t = normalize(text);
        int cx = x;
        for (int i = 0; i < t.length(); i++) {
            String[] g = glyph(t.charAt(i));
            for (int gy = 0; gy < g.length; gy++) {
                for (int gx = 0; gx < g[gy].length(); gx++) {
                    if (g[gy].charAt(gx) == '#') grid.set(cx + gx, y + gy, level);
                }
            }
            cx += g[0].length() + 1;
        }
    }
}
