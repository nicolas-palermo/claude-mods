package com.plaude.glyph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.plaude.glyph.core.Config;
import com.plaude.glyph.core.Engine;
import com.plaude.glyph.core.Grid;
import com.plaude.glyph.core.Playlist;
import com.plaude.glyph.core.Screens;

import org.json.JSONException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ContentParserTest {

    /** El glyph.json del repo tiene que ser válido: es lo que baja el teléfono. */
    @Test
    public void repoGlyphJsonIsValid() throws Exception {
        Path p = Paths.get("..", "glyph.json");
        if (!Files.exists(p)) p = Paths.get("glyph.json");
        String json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        Config cfg = ContentParser.parse(json);
        assertTrue(cfg.playlist.entries().size() >= 1);
        // Recorre un ciclo entero renderizando cada medio segundo: nada puede explotar.
        long total = 0;
        for (Playlist.Entry e : cfg.playlist.entries()) total += e.durationMs;
        Engine engine = new Engine();
        for (long t = 0; t < total; t += 500) {
            Grid g = engine.render(cfg, t, 12, 34, (int) (t / 1000 % 60), null).grid;
            assertTrue(g.toSdkFrame(cfg.brightness).length == 169);
        }
    }

    @Test
    public void parsesEveryScreenType() throws Exception {
        String row = "\"" + ".".repeat(13) + "\"";
        String rows = "[" + String.join(",", java.util.Collections.nCopies(13, row)) + "]";
        String json = "{\"brightness\":70,\"screens\":["
                + "{\"type\":\"bitmap\",\"rows\":" + rows + "},"
                + "{\"type\":\"animation\",\"frames\":[" + rows + "," + rows + "],\"fps\":10},"
                + "{\"type\":\"clock\",\"seconds\":5},"
                + "{\"type\":\"text\",\"text\":\"hola che\",\"speed\":20},"
                + "{\"type\":\"data\",\"source\":\"w\"},"
                + "{\"type\":\"icon\",\"icon\":\"heart\",\"blink\":true}"
                + "],\"data\":{\"w\":{\"type\":\"weather\",\"lat\":1,\"lon\":2}},"
                + "\"notifications\":{\"seconds\":3,\"apps\":{\"com.foo\":\"star\",\"com.whatsapp\":null},"
                + "\"default\":\"bell\"}}";
        Config cfg = ContentParser.parse(json);
        assertEquals(70, cfg.brightness);
        assertEquals(6, cfg.playlist.entries().size());
        assertTrue(cfg.playlist.entries().get(1).screen instanceof Screens.Animation);
        assertEquals(100, cfg.playlist.entries().get(1).screen.frameIntervalMs());
        assertEquals(5_000, cfg.playlist.entries().get(2).durationMs);
        assertEquals(60_000, cfg.playlist.entries().get(0).durationMs);
        assertEquals("1", cfg.data.get("w").params.get("lat"));
        assertEquals(3_000, cfg.notifications.showMs);
        assertEquals("star", cfg.notifications.iconFor("com.foo"));
        assertEquals("bell", cfg.notifications.iconFor("com.whatsapp"));
        assertEquals("mail", cfg.notifications.iconFor("com.google.android.gm"));
    }

    @Test
    public void rejectsBrokenContentWithAClearMessage() {
        expectError("{\"screens\":[]}", "no tiene pantallas");
        expectError("{\"screens\":[{\"type\":\"bitmap\",\"rows\":[\"#\"]}]}", "13 filas");
        expectError("{\"screens\":[{\"type\":\"wat\"}]}", "desconocido");
        expectError("{\"screens\":[{\"type\":\"data\",\"source\":\"x\"}]}", "no está en \"data\"");
        expectError("{\"screens\":[{\"type\":\"icon\",\"icon\":\"nope\"}]}", "ícono desconocido");
    }

    @Test
    public void notificationsDefaultIgnoresUnknownApps() throws Exception {
        Config cfg = ContentParser.parse("{\"screens\":[{\"type\":\"clock\"}]}");
        assertNull(cfg.notifications.iconFor("com.unknown"));
        assertTrue(cfg.notifications.enabled);
    }

    private static void expectError(String json, String fragment) {
        try {
            ContentParser.parse(json);
            fail("debería fallar: " + json);
        } catch (JSONException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }
}
