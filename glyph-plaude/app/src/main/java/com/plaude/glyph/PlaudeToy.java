package com.plaude.glyph;

/** Logo de Plaude a 12x12 (2x2 px por LED), corrido medio LED dentro de la matriz de 13x13. */
public class PlaudeToy extends PlaudeToyService {
    private static final String[] LOGO = {
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

    @Override
    protected String[] logo() {
        return LOGO;
    }
}
