package com.plaude.glyph;

import android.content.Context;

import com.plaude.glyph.core.Config;
import com.plaude.glyph.core.Engine;
import com.plaude.glyph.core.Grid;
import com.plaude.glyph.core.Overlay;

import java.util.Calendar;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Estado compartido dentro del proceso principal: el motor, las notificaciones activas y el
 * último frame dibujado (para la vista previa en la app).
 */
final class GlyphHub {

    interface Listener {
        void onChanged();
    }

    static final int PRIORITY_NOTIFICATION = 1;
    static final int PRIORITY_CALL = 2;

    private static final Engine ENGINE = new Engine();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile int[] lastLevels = new int[Grid.SIZE * Grid.SIZE];
    private static volatile long lastDrawAt = 0;
    private static volatile boolean toyRunning = false;

    private GlyphHub() {
    }

    /** Dibuja el frame de ahora y devuelve en cuántos ms hay que volver a dibujar. */
    static Engine.Result render(Context ctx) {
        Config cfg = RemoteContent.config(ctx);
        Calendar c = Calendar.getInstance();
        Engine.Result r = ENGINE.render(cfg, System.currentTimeMillis(),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
                LiveData.values());
        lastLevels = new Grid().copyFrom(r.grid).dim(cfg.brightness).levels();
        lastDrawAt = System.currentTimeMillis();
        return r;
    }

    /** Pide contenido y datos nuevos si toca. Avisa a los oyentes si algo cambió. */
    static void refresh(Context ctx, boolean force) {
        RemoteContent.refresh(ctx, force, GlyphHub::changed);
        LiveData.refresh(RemoteContent.config(ctx), force, GlyphHub::changed);
    }

    static void onNotification(Context ctx, String pkg) {
        Config.Notifications n = RemoteContent.config(ctx).notifications;
        if (!n.enabled) return;
        String icon = n.iconFor(pkg);
        if (icon == null) return;
        ENGINE.addOverlay(new Overlay(icon, System.currentTimeMillis(), n.showMs, PRIORITY_NOTIFICATION));
        changed();
    }

    static void callStarted(Context ctx) {
        if (!RemoteContent.config(ctx).notifications.calls) return;
        ENGINE.removeOverlays("phone");
        ENGINE.addOverlay(new Overlay("phone", System.currentTimeMillis(), 0, PRIORITY_CALL));
        changed();
    }

    static void callEnded() {
        ENGINE.removeOverlays("phone");
        changed();
    }

    static int[] lastLevels() {
        return lastLevels;
    }

    static long lastDrawAt() {
        return lastDrawAt;
    }

    static boolean toyRunning() {
        return toyRunning;
    }

    static void setToyRunning(boolean running) {
        toyRunning = running;
    }

    static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    static void changed() {
        for (Listener l : LISTENERS) l.onChanged();
    }
}
