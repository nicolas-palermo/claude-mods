package com.plaude.glyph.core;

/**
 * Algo que se impone a la playlist por un rato: una notificación o una llamada.
 * Las llamadas duran hasta que se cortan; las notificaciones, sus segundos.
 */
public final class Overlay {

    public final String icon;
    public final long startMs;
    /** 0 = hasta que se saque a mano (llamadas). */
    public final long durationMs;
    public final int priority;
    /** Si no es null, se dibuja esta animación en vez del ícono (eventos de Glyph Events). */
    public final EventAnimation animation;

    public Overlay(String icon, long startMs, long durationMs, int priority) {
        this(icon, startMs, durationMs, priority, null);
    }

    private Overlay(String icon, long startMs, long durationMs, int priority, EventAnimation animation) {
        this.icon = icon;
        this.startMs = startMs;
        this.durationMs = durationMs;
        this.priority = priority;
        this.animation = animation;
    }

    /** Una animación que llegó por push: dura lo que dura la animación. */
    public static Overlay forAnimation(EventAnimation a, long startMs, int priority) {
        return new Overlay("event", startMs, a.totalMs(), priority, a);
    }

    public boolean activeAt(long nowMs) {
        return nowMs >= startMs && (durationMs == 0 || nowMs < startMs + durationMs);
    }

    /** Titila tres veces al entrar y después queda fijo. */
    public void render(Grid g, long nowMs) {
        if (animation != null) {
            animation.render(g, nowMs - startMs);
            return;
        }
        String[] rows = Icons.get(icon);
        if (rows == null) rows = Icons.get("bell");
        long t = nowMs - startMs;
        boolean off = t < 1500 && (t / 250) % 2 == 1;
        g.clear();
        if (!off) g.drawCentered(rows);
    }

    public long frameIntervalMs(long nowMs) {
        if (animation != null) {
            long step = animation.frameIntervalMs();
            return step - Math.max(0, nowMs - startMs) % step;
        }
        return nowMs - startMs < 1500 ? 250 : 1000;
    }
}
