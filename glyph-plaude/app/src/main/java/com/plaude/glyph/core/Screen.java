package com.plaude.glyph.core;

/** Una pantalla del toy: sabe dibujarse en un momento dado y cada cuánto necesita redibujarse. */
public interface Screen {

    /** Milisegundos entre frames; 0 si la pantalla es estática. */
    long frameIntervalMs();

    void render(Grid grid, RenderContext ctx);
}
