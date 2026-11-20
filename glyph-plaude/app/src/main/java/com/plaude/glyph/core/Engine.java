package com.plaude.glyph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decide qué se ve en cada momento: una notificación o llamada si hay alguna activa,
 * si no la pantalla que toca en la playlist. También dice cuándo hay que volver a dibujar.
 */
public final class Engine {

    /** Para no gastar batería de más: nunca más de 25 cuadros por segundo. */
    public static final long MIN_FRAME_MS = 40;
    /** Las pantallas estáticas igual se refrescan cada tanto (por la hora, los datos, etc.). */
    public static final long STATIC_REFRESH_MS = 5_000;

    public static final class Result {
        public final Grid grid;
        public final long nextInMs;

        Result(Grid grid, long nextInMs) {
            this.grid = grid;
            this.nextInMs = nextInMs;
        }
    }

    private final List<Overlay> overlays = new ArrayList<>();

    public synchronized void addOverlay(Overlay o) {
        overlays.add(o);
    }

    public synchronized void removeOverlays(String icon) {
        overlays.removeIf(o -> o.icon.equals(icon) && o.durationMs == 0);
    }

    public synchronized int overlayCount() {
        return overlays.size();
    }

    private synchronized Overlay currentOverlay(long now) {
        overlays.removeIf(o -> !o.activeAt(now) && o.startMs <= now);
        Overlay best = null;
        for (Overlay o : overlays) {
            if (!o.activeAt(now)) continue;
            if (best == null || o.priority > best.priority
                    || (o.priority == best.priority && o.startMs > best.startMs)) best = o;
        }
        return best;
    }

    public Result render(Config cfg, long nowMs, int hour, int minute, int second,
                         Map<String, RenderContext.DataValue> data) {
        Grid g = new Grid();
        Overlay o = currentOverlay(nowMs);
        long next;
        if (o != null) {
            o.render(g, nowMs);
            next = o.frameIntervalMs(nowMs);
            if (o.durationMs > 0) next = Math.min(next, o.startMs + o.durationMs - nowMs);
        } else {
            Playlist.Position pos = cfg.playlist.at(nowMs);
            RenderContext ctx = new RenderContext(pos.elapsedMs, hour, minute, second, data);
            pos.entry.screen.render(g, ctx);
            long interval = pos.entry.screen.frameIntervalMs();
            next = interval > 0 ? interval - (pos.elapsedMs % interval) : STATIC_REFRESH_MS;
            next = Math.min(next, pos.remainingMs);
        }
        return new Result(g, Math.max(MIN_FRAME_MS, next));
    }
}
