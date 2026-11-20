package com.plaude.glyph;

import android.os.Handler;
import android.os.Looper;

import com.plaude.glyph.core.Engine;

/**
 * El toy: muestra lo que diga glyph.json (dibujos, animaciones, reloj, texto, datos en vivo)
 * y se interrumpe con las notificaciones y llamadas. Mientras el sistema lo tenga conectado,
 * redibuja a su ritmo, no solo con el tic de un minuto.
 */
public class PlaudeToy extends PlaudeToyService implements GlyphHub.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    private boolean running;

    @Override
    protected void onConnected() {
        running = true;
        GlyphHub.setToyRunning(true);
        GlyphHub.addListener(this);
        GlyphHub.refresh(this, false);
        main.removeCallbacks(tick);
        main.post(tick);
    }

    @Override
    protected void onDisconnected() {
        running = false;
        GlyphHub.setToyRunning(false);
        GlyphHub.removeListener(this);
        main.removeCallbacks(tick);
    }

    @Override
    protected void onGlyphEvent(String event) {
        // El tic de AOD: aprovechamos para pedir contenido nuevo y redibujar ya.
        GlyphHub.refresh(this, false);
        onChanged();
    }

    @Override
    public void onChanged() {
        main.post(() -> {
            if (!running) return;
            main.removeCallbacks(tick);
            tick();
        });
    }

    private void tick() {
        if (!running) return;
        Engine.Result r = GlyphHub.render(this);
        show(r.grid.toSdkFrame(RemoteContent.config(this).brightness));
        GlyphHub.refresh(this, false);
        main.postDelayed(tick, r.nextInMs);
    }
}
