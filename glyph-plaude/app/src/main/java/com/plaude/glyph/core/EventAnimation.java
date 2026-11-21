package com.plaude.glyph.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Una animación de Glyph Events: la manda el backend completa en el push y el teléfono solo la
 * reproduce. Cada cuadro es la matriz de 13x13 como texto ('#' lleno, '+' medio, '1'..'9' niveles,
 * '.' apagado), en 13 filas o en un solo string de 169 caracteres.
 */
public final class EventAnimation {

    public static final int MAX_FRAMES = 120;
    /** Tope de duración total, para que un evento no se quede con la matriz. */
    public static final long MAX_TOTAL_MS = 30_000;

    private final List<String[]> frames;
    private final long frameMs;
    private final int repeat;
    private final int brightness;

    public EventAnimation(List<String[]> frames, long frameMs, int repeat, int brightness) {
        if (frames == null || frames.isEmpty()) throw new IllegalArgumentException("la animación no tiene cuadros");
        if (frames.size() > MAX_FRAMES) throw new IllegalArgumentException("más de " + MAX_FRAMES + " cuadros");
        this.frames = Collections.unmodifiableList(new ArrayList<>(frames));
        this.frameMs = Math.max(Engine.MIN_FRAME_MS, Math.min(10_000, frameMs));
        this.repeat = Math.max(1, Math.min(50, repeat));
        this.brightness = Math.max(0, Math.min(100, brightness));
    }

    /** Un cuadro dado como 13 filas o como un string de 169 caracteres. */
    public static String[] frame(String compact) {
        if (compact.length() != Grid.SIZE * Grid.SIZE) {
            throw new IllegalArgumentException("un cuadro compacto tiene que tener 169 caracteres, tiene " + compact.length());
        }
        String[] rows = new String[Grid.SIZE];
        for (int y = 0; y < Grid.SIZE; y++) rows[y] = compact.substring(y * Grid.SIZE, (y + 1) * Grid.SIZE);
        return rows;
    }

    public int frameCount() {
        return frames.size();
    }

    public long frameIntervalMs() {
        return frameMs;
    }

    public int brightness() {
        return brightness;
    }

    public long totalMs() {
        return Math.min(MAX_TOTAL_MS, frames.size() * frameMs * repeat);
    }

    public int indexAt(long elapsedMs) {
        if (elapsedMs < 0) return 0;
        return (int) ((elapsedMs / frameMs) % frames.size());
    }

    public void render(Grid g, long elapsedMs) {
        g.clear().drawRows(frames.get(indexAt(elapsedMs)));
        if (brightness < 100) g.dim(brightness);
    }
}
