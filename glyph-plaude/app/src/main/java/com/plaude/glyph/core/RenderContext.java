package com.plaude.glyph.core;

import java.util.Collections;
import java.util.Map;

/** Todo lo que una pantalla puede necesitar para dibujarse. */
public final class RenderContext {

    /** Milisegundos desde que esta pantalla empezó a mostrarse. */
    public final long elapsedMs;
    public final int hour;
    public final int minute;
    public final int second;
    /** Valores de datos en vivo ya formateados, por clave de fuente (por ejemplo "weather"). */
    public final Map<String, DataValue> data;

    public RenderContext(long elapsedMs, int hour, int minute, int second, Map<String, DataValue> data) {
        this.elapsedMs = elapsedMs;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.data = data == null ? Collections.emptyMap() : data;
    }

    public RenderContext withElapsed(long elapsed) {
        return new RenderContext(elapsed, hour, minute, second, data);
    }

    /** Un dato en vivo: el texto a mostrar y un ícono opcional que lo acompaña. */
    public static final class DataValue {
        public final String text;
        public final String icon;

        public DataValue(String text, String icon) {
            this.text = text;
            this.icon = icon;
        }
    }
}
