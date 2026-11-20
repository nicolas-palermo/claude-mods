package com.plaude.glyph.core;

import java.util.List;

/** Las pantallas que se pueden declarar en glyph.json. */
public final class Screens {

    private Screens() {
    }

    /** Un dibujo fijo de 13 filas. */
    public static final class Bitmap implements Screen {
        private final String[] rows;

        public Bitmap(String[] rows) {
            this.rows = rows;
        }

        @Override
        public long frameIntervalMs() {
            return 0;
        }

        @Override
        public void render(Grid g, RenderContext ctx) {
            g.drawRows(rows);
        }
    }

    /** Una animación cuadro a cuadro. */
    public static final class Animation implements Screen {
        private final List<String[]> frames;
        private final long frameMs;
        private final boolean loop;

        public Animation(List<String[]> frames, long frameMs, boolean loop) {
            if (frames.isEmpty()) throw new IllegalArgumentException("la animación no tiene frames");
            this.frames = frames;
            this.frameMs = Math.max(40, frameMs);
            this.loop = loop;
        }

        public int frameCount() {
            return frames.size();
        }

        public int indexAt(long elapsedMs) {
            long i = elapsedMs / frameMs;
            return loop ? (int) (i % frames.size()) : (int) Math.min(i, frames.size() - 1);
        }

        @Override
        public long frameIntervalMs() {
            return frames.size() > 1 ? frameMs : 0;
        }

        @Override
        public void render(Grid g, RenderContext ctx) {
            g.drawRows(frames.get(indexAt(ctx.elapsedMs)));
        }
    }

    /** Hora en dos renglones: HH arriba, MM abajo. */
    public static final class Clock implements Screen {
        private final boolean blinkDot;

        public Clock(boolean blinkDot) {
            this.blinkDot = blinkDot;
        }

        @Override
        public long frameIntervalMs() {
            return blinkDot ? 1000 : 0;
        }

        @Override
        public void render(Grid g, RenderContext ctx) {
            g.drawTextCentered(two(ctx.hour), 1, Grid.ON);
            g.drawTextCentered(two(ctx.minute), 7, Grid.ON);
            if (!blinkDot || ctx.second % 2 == 0) g.set(6, 6, Grid.HALF);
        }

        private static String two(int v) {
            return v < 10 ? "0" + v : String.valueOf(v);
        }
    }

    /** Texto que se desplaza de derecha a izquierda. */
    public static final class Text implements Screen {
        private final String text;
        private final int pxPerSecond;
        private final int width;

        public Text(String text, int pxPerSecond) {
            this.text = Font.normalize(text);
            this.pxPerSecond = Math.max(2, Math.min(40, pxPerSecond));
            this.width = Font.width(this.text);
        }

        /** Cuánto dura una pasada completa, en ms. */
        public long cycleMs() {
            return (Grid.SIZE + width) * 1000L / pxPerSecond;
        }

        @Override
        public long frameIntervalMs() {
            return 1000L / pxPerSecond;
        }

        @Override
        public void render(Grid g, RenderContext ctx) {
            long offset = (ctx.elapsedMs * pxPerSecond / 1000) % (Grid.SIZE + width);
            g.drawText(text, Grid.SIZE - (int) offset, 4, Grid.ON);
        }
    }

    /** Un dato en vivo (clima, PRs, lo que sea) con su ícono. */
    public static final class Data implements Screen {
        private final String source;
        private final String fallbackIcon;

        public Data(String source, String fallbackIcon) {
            this.source = source;
            this.fallbackIcon = fallbackIcon;
        }

        public String source() {
            return source;
        }

        @Override
        public long frameIntervalMs() {
            return 0;
        }

        @Override
        public void render(Grid g, RenderContext ctx) {
            RenderContext.DataValue v = ctx.data.get(source);
            String text = v == null || v.text == null ? "--" : v.text;
            // Si no entra (por ejemplo "-12°"), el símbolo de grados es lo primero que se va.
            if (Font.width(text) > 11 && text.endsWith("°")) text = text.substring(0, text.length() - 1);
            String[] icon = Icons.get(v != null && v.icon != null ? v.icon : fallbackIcon);
            int w = Font.width(text);
            // La fila 11 tiene 9 LEDs y la 10 tiene 11: el texto ancho sube una fila.
            int textY = w <= 9 ? 7 : 6;
            int iconY = textY - 6;
            if (icon != null) {
                int iw = icon[0].length();
                g.drawRows(icon, (Grid.SIZE - iw) / 2, Math.max(0, iconY), false);
            }
            if (w > 11) {
                // No entra: se muestra recortado al centro.
                g.drawText(text, 1, textY, Grid.ON);
            } else {
                g.drawTextCentered(text, textY, Grid.ON);
            }
        }
    }

    /** Un ícono incorporado, centrado, opcionalmente titilando. */
    public static final class Icon implements Screen {
        private final String name;
        private final boolean blink;

        public Icon(String name, boolean blink) {
            this.name = name;
            this.blink = blink;
        }

        @Override
        public long frameIntervalMs() {
            return blink ? 500 : 0;
        }

        @Override
        public void render(Grid g, RenderContext ctx) {
            String[] icon = Icons.get(name);
            if (icon == null) icon = Icons.get("dot");
            if (blink && (ctx.elapsedMs / 500) % 2 == 1) {
                Grid tmp = new Grid().drawCentered(icon);
                g.copyFrom(tmp.dim(35));
            } else {
                g.drawCentered(icon);
            }
        }
    }
}
