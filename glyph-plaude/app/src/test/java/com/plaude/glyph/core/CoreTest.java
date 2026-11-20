package com.plaude.glyph.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CoreTest {

    @Test
    public void matrixHas137Leds() {
        assertEquals(137, Grid.LED_COUNT);
        assertTrue(Grid.exists(6, 0));
        assertFalse(Grid.exists(0, 0));
        assertFalse(Grid.exists(12, 12));
        assertTrue(Grid.exists(0, 6));
    }

    @Test
    public void sdkFrameUsesFull4095Scale() {
        Grid g = new Grid().set(6, 6, Grid.ON).set(5, 6, Grid.HALF).set(0, 0, Grid.ON);
        int[] f = g.toSdkFrame(100);
        assertEquals(169, f.length);
        assertEquals(4095, f[6 * 13 + 6]);
        assertEquals(4095 * 128 / 255, f[6 * 13 + 5]);
        assertEquals(0, f[0]); // fuera del círculo no se prende nunca
        assertEquals(4095 / 2, g.toSdkFrame(50)[6 * 13 + 6]);
    }

    @Test
    public void fontWidthsAndNormalization() {
        assertEquals(3, Font.width("1"));
        assertEquals(7, Font.width("23"));
        assertEquals("ACENTO?", Font.normalize("acentó€"));
        assertEquals(10, Font.width("23°"));
    }

    @Test
    public void everyGlyphAndIconIsRectangular() {
        for (char c : "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ .,:!'?-+/%°<>#*=()".toCharArray()) {
            String[] g = Font.glyph(c);
            assertEquals("alto de " + c, Font.HEIGHT, g.length);
            for (String r : g) assertEquals("ancho de " + c, g[0].length(), r.length());
        }
        for (String n : Icons.names()) {
            String[] rows = Icons.get(n);
            for (String r : rows) assertEquals("ícono " + n, rows[0].length(), r.length());
        }
    }

    @Test
    public void notificationIconsFitInsideTheCircle() {
        for (String n : Icons.names()) {
            String[] rows = Icons.get(n);
            if (rows.length == 13) continue; // el logo ya está pensado para el círculo
            Grid g = new Grid().drawCentered(rows);
            int expected = 0;
            for (String r : rows) for (char c : r.toCharArray()) if (c == '#') expected++;
            assertEquals("el ícono " + n + " pierde LEDs fuera del círculo", expected, g.litCount());
        }
    }

    @Test
    public void clockDrawsBothRowsInsideTheCircle() {
        Grid g = new Grid();
        new Screens.Clock(false).render(g, new RenderContext(0, 23, 58, 0, null));
        String ascii = g.toAscii();
        // "23" ocupa las filas 1 a 5, "58" las filas 7 a 11, y el punto del medio está en (6,6).
        assertTrue(ascii, g.get(3, 1) > 0);
        assertTrue(ascii, g.get(9, 11) > 0);
        assertTrue(ascii, g.get(6, 6) > 0);
        int lit = 0;
        for (String r : new String[]{"2", "3", "5", "8"}) {
            for (String row : Font.glyph(r.charAt(0))) for (char c : row.toCharArray()) if (c == '#') lit++;
        }
        assertEquals("ningún segmento del reloj cae fuera del círculo", lit + 1, g.litCount());
    }

    @Test
    public void textScrollsAcrossAndLoops() {
        Screens.Text t = new Screens.Text("HOLA", 10);
        assertEquals(100, t.frameIntervalMs());
        Grid start = new Grid();
        t.render(start, new RenderContext(0, 0, 0, 0, null));
        assertEquals("arranca afuera, a la derecha", 0, start.litCount());
        Grid mid = new Grid();
        t.render(mid, new RenderContext(1300, 0, 0, 0, null));
        assertTrue(mid.litCount() > 0);
        Grid again = new Grid();
        t.render(again, new RenderContext(t.cycleMs(), 0, 0, 0, null));
        assertEquals(start.toAscii(), again.toAscii());
    }

    @Test
    public void animationLoopsOrHolds() {
        List<String[]> frames = new ArrayList<>();
        frames.add(rows('#'));
        frames.add(rows('.'));
        Screens.Animation loop = new Screens.Animation(frames, 100, true);
        assertEquals(0, loop.indexAt(0));
        assertEquals(1, loop.indexAt(150));
        assertEquals(0, loop.indexAt(250));
        Screens.Animation once = new Screens.Animation(frames, 100, false);
        assertEquals(1, once.indexAt(10_000));
        assertEquals(40, new Screens.Animation(frames, 1, true).frameIntervalMs());
    }

    @Test
    public void dataScreenShowsPlaceholderAndValue() {
        Screens.Data d = new Screens.Data("weather", "sun");
        Grid empty = new Grid();
        d.render(empty, new RenderContext(0, 0, 0, 0, null));
        assertTrue("muestra -- sin datos", empty.get(5, 9) > 0);
        Grid g = new Grid();
        d.render(g, new RenderContext(0, 0, 0, 0,
                Map.of("weather", new RenderContext.DataValue("23°", "rain"))));
        int lit = g.litCount();
        assertTrue(g.toAscii(), lit > 15);

        // "-12°" no entra en 11 columnas: se saca el símbolo de grados y queda centrado.
        Grid neg = new Grid();
        d.render(neg, new RenderContext(0, 0, 0, 0,
                Map.of("weather", new RenderContext.DataValue("-12°", "snow"))));
        int textLit = 0;
        for (char c : "-12".toCharArray()) {
            for (String row : Font.glyph(c)) for (char p : row.toCharArray()) if (p == '#') textLit++;
        }
        int iconLit = 0;
        for (String row : Icons.get("snow")) for (char p : row.toCharArray()) if (p == '#') iconLit++;
        assertEquals(neg.toAscii(), textLit + iconLit, neg.litCount());
    }

    @Test
    public void playlistRotatesByDuration() {
        Screen a = new Screens.Icon("heart", false);
        Screen b = new Screens.Icon("star", false);
        Playlist p = new Playlist(List.of(new Playlist.Entry(a, 10_000), new Playlist.Entry(b, 5_000)));
        assertSame(a, p.at(0).entry.screen);
        assertSame(a, p.at(9_999).entry.screen);
        assertSame(b, p.at(10_000).entry.screen);
        assertEquals(2_000, p.at(12_000).elapsedMs);
        assertEquals(3_000, p.at(12_000).remainingMs);
        assertSame(a, p.at(15_000).entry.screen);
    }

    @Test
    public void engineShowsNotificationThenGoesBack() {
        Engine e = new Engine();
        Config cfg = Config.fallback();
        String logo = new Grid().drawRows(Icons.get("plaude")).toAscii();
        assertEquals(logo, e.render(cfg, 1_000, 0, 0, 0, null).grid.toAscii());

        e.addOverlay(new Overlay("chat", 2_000, 8_000, 1));
        String chat = new Grid().drawCentered(Icons.get("chat")).toAscii();
        assertEquals(chat, e.render(cfg, 4_000, 0, 0, 0, null).grid.toAscii());
        // Mientras titila, redibuja seguido.
        assertEquals(250, e.render(cfg, 2_100, 0, 0, 0, null).nextInMs);
        assertEquals(logo, e.render(cfg, 10_001, 0, 0, 0, null).grid.toAscii());
        assertEquals(0, e.overlayCount());
    }

    @Test
    public void callBeatsNotificationsUntilItEnds() {
        Engine e = new Engine();
        Config cfg = Config.fallback();
        e.addOverlay(new Overlay("phone", 1_000, 0, 2));
        e.addOverlay(new Overlay("chat", 2_000, 8_000, 1));
        String phone = new Grid().drawCentered(Icons.get("phone")).toAscii();
        assertEquals(phone, e.render(cfg, 60_000, 0, 0, 0, null).grid.toAscii());
        e.removeOverlays("phone");
        String logo = new Grid().drawRows(Icons.get("plaude")).toAscii();
        assertEquals(logo, e.render(cfg, 60_000, 0, 0, 0, null).grid.toAscii());
    }

    @Test
    public void engineNeverRendersFasterThan25Fps() {
        List<String[]> frames = new ArrayList<>(Collections.nCopies(3, rows('#')));
        Playlist p = new Playlist(List.of(new Playlist.Entry(new Screens.Animation(frames, 10, true), 5_000)));
        Config cfg = new Config(100, p, Config.Notifications.defaults(), Map.of());
        assertTrue(new Engine().render(cfg, 0, 0, 0, 0, null).nextInMs >= Engine.MIN_FRAME_MS);
    }

    @Test
    public void notificationDefaultsMapCommonApps() {
        Config.Notifications n = Config.Notifications.defaults();
        assertEquals("chat", n.iconFor("com.whatsapp"));
        assertEquals("mail", n.iconFor("com.google.android.gm"));
        assertNull(n.iconFor("com.random.app"));
        for (String icon : n.apps.values()) assertNotNull("ícono " + icon, Icons.get(icon));
    }

    private static String[] rows(char c) {
        String[] r = new String[13];
        String line = String.valueOf(c).repeat(13);
        for (int i = 0; i < 13; i++) r[i] = line;
        return r;
    }
}
