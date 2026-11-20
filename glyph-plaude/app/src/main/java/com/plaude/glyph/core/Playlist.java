package com.plaude.glyph.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * La lista de pantallas que rota el toy. Cada entrada dura sus segundos y después pasa a la
 * siguiente; el ciclo se ancla al reloj para que dos instancias muestren lo mismo.
 */
public final class Playlist {

    public static final class Entry {
        public final Screen screen;
        public final long durationMs;

        public Entry(Screen screen, long durationMs) {
            this.screen = screen;
            this.durationMs = Math.max(1000, durationMs);
        }
    }

    /** Qué mostrar ahora: la pantalla, cuánto lleva y cuánto le falta. */
    public static final class Position {
        public final Entry entry;
        public final long elapsedMs;
        public final long remainingMs;

        Position(Entry entry, long elapsedMs, long remainingMs) {
            this.entry = entry;
            this.elapsedMs = elapsedMs;
            this.remainingMs = remainingMs;
        }
    }

    private final List<Entry> entries;
    private final long totalMs;

    public Playlist(List<Entry> entries) {
        if (entries.isEmpty()) throw new IllegalArgumentException("no hay pantallas");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        long t = 0;
        for (Entry e : entries) t += e.durationMs;
        this.totalMs = t;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Position at(long epochMs) {
        long t = Math.floorMod(epochMs, totalMs);
        for (Entry e : entries) {
            if (t < e.durationMs) return new Position(e, t, e.durationMs - t);
            t -= e.durationMs;
        }
        Entry last = entries.get(entries.size() - 1);
        return new Position(last, 0, last.durationMs);
    }
}
