package com.plaude.glyph;

import android.os.Handler;
import android.os.Looper;

import java.util.Calendar;

/** El toy: muestra lo que diga glyph.json en el repo, con el logo de Plaude como respaldo. */
public class PlaudeToy extends PlaudeToyService {

    private static final String[] FALLBACK = {
            ".......##....",
            ".......####..",
            ".......#####.",
            ".......#####.",
            ".......#####.",
            ".......####..",
            ".......##....",
            ".....##......",
            "...####......",
            "..#####......",
            "..#####......",
            "..#####......",
            "....###......",
    };

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected int[] frame() {
        RemoteContent.refresh(this, () -> main.post(this::redraw));
        GlyphContent content = RemoteContent.cached(this);
        if (content == null) content = GlyphContent.ofBitmap(FALLBACK);
        return content.frameAt(Calendar.getInstance());
    }
}
