package com.plaude.glyph;

import android.app.Application;

/** Arranca Firebase en cada proceso si el teléfono ya está vinculado a Glyph Events. */
public class PlaudeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Events.initFirebase(this);
    }
}
