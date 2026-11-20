package com.plaude.glyph.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Todo lo que define glyph.json, ya validado. */
public final class Config {

    /** Una fuente de datos en vivo: "weather" o "github", con sus parámetros. */
    public static final class DataSource {
        public final String type;
        public final Map<String, String> params;

        public DataSource(String type, Map<String, String> params) {
            this.type = type;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }

        public String param(String key, String fallback) {
            String v = params.get(key);
            return v == null || v.isEmpty() ? fallback : v;
        }
    }

    /** Qué hacer con las notificaciones del teléfono. */
    public static final class Notifications {
        public final boolean enabled;
        public final long showMs;
        public final boolean calls;
        /** Ícono por paquete de app. */
        public final Map<String, String> apps;
        /** Ícono para apps que no están en la lista; null = ignorarlas. */
        public final String defaultIcon;

        public Notifications(boolean enabled, long showMs, boolean calls, Map<String, String> apps,
                             String defaultIcon) {
            this.enabled = enabled;
            this.showMs = showMs;
            this.calls = calls;
            this.apps = Collections.unmodifiableMap(new LinkedHashMap<>(apps));
            this.defaultIcon = defaultIcon;
        }

        public String iconFor(String pkg) {
            String i = apps.get(pkg);
            return i != null ? i : defaultIcon;
        }

        public static Notifications defaults() {
            Map<String, String> apps = new LinkedHashMap<>();
            apps.put("com.whatsapp", "chat");
            apps.put("com.whatsapp.w4b", "chat");
            apps.put("org.telegram.messenger", "chat");
            apps.put("com.google.android.apps.messaging", "chat");
            apps.put("com.slack", "chat");
            apps.put("com.google.android.gm", "mail");
            apps.put("com.microsoft.office.outlook", "mail");
            apps.put("com.github.android", "git");
            apps.put("com.google.android.calendar", "calendar");
            apps.put("com.instagram.android", "heart");
            apps.put("com.spotify.music", "music");
            return new Notifications(true, 8000, true, apps, null);
        }
    }

    public final int brightness;
    public final Playlist playlist;
    public final Notifications notifications;
    public final Map<String, DataSource> data;

    public Config(int brightness, Playlist playlist, Notifications notifications,
                  Map<String, DataSource> data) {
        this.brightness = Math.max(0, Math.min(100, brightness));
        this.playlist = playlist;
        this.notifications = notifications;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    /** Lo que se ve si todavía no se bajó nada: el logo de Plaude. */
    public static Config fallback() {
        Playlist p = new Playlist(List.of(new Playlist.Entry(
                new Screens.Bitmap(Icons.get("plaude")), 60_000)));
        return new Config(100, p, Notifications.defaults(), Collections.emptyMap());
    }
}
