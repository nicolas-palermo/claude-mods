package com.plaude.glyph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.plaude.glyph.core.Engine;
import com.plaude.glyph.core.EventAnimation;
import com.plaude.glyph.core.Grid;
import com.plaude.glyph.core.Overlay;
import com.plaude.glyph.core.Config;

import org.json.JSONException;
import org.junit.Test;

import java.util.Collections;

public class EventParserTest {

    private static String row(char c) {
        return String.valueOf(c).repeat(13);
    }

    private static final String FULL = "#".repeat(169);
    private static final String OFF = ".".repeat(169);

    @Test
    public void parsesCompactAndRowFrames() throws Exception {
        String json = "{\"v\":1,\"frameMs\":100,\"repeat\":2,\"brightness\":50,\"frames\":[\"" + FULL + "\",[\""
                + String.join("\",\"", Collections.nCopies(13, row('.'))) + "\"]]}";
        EventAnimation a = EventParser.parse(json);
        assertEquals(2, a.frameCount());
        assertEquals(400, a.totalMs());
        Grid g = new Grid();
        a.render(g, 0);
        assertEquals(Grid.ON * 50 / 100, g.get(6, 6));
        a.render(g, 150);
        assertEquals(0, g.get(6, 6));
        a.render(g, 250); // segunda vuelta
        assertEquals(Grid.ON * 50 / 100, g.get(6, 6));
    }

    @Test
    public void digitsAreBrightnessLevels() throws Exception {
        EventAnimation a = EventParser.parse("{\"frames\":[\"" + "9".repeat(84) + "3" + "0".repeat(84) + "\"]}");
        Grid g = new Grid();
        a.render(g, 0);
        assertEquals(Grid.ON, g.get(6, 0));
        assertEquals(3 * Grid.ON / 9, g.get(6, 6));
        assertEquals(0, g.get(6, 12));
    }

    @Test
    public void rejectsBadFrames() {
        try {
            EventParser.parse("{\"frames\":[\"###\"]}");
            fail();
        } catch (JSONException e) {
            assertTrue(e.getMessage().contains("169"));
        }
        try {
            EventParser.parse("{\"frames\":[]}");
            fail();
        } catch (JSONException e) {
            assertTrue(e.getMessage().contains("cuadros"));
        }
    }

    @Test
    public void totalIsCapped() throws Exception {
        EventAnimation a = EventParser.parse("{\"frameMs\":10000,\"repeat\":50,\"frames\":[\"" + OFF + "\"]}");
        assertEquals(EventAnimation.MAX_TOTAL_MS, a.totalMs());
    }

    @Test
    public void eventOverlayWinsAndThenEnds() throws Exception {
        EventAnimation a = EventParser.parse("{\"frameMs\":200,\"repeat\":1,\"frames\":[\"" + FULL + "\",\"" + OFF + "\"]}");
        Engine e = new Engine();
        e.addOverlay(new Overlay("chat", 0, 10_000, 1));
        e.addOverlay(Overlay.forAnimation(a, 1_000, 3));
        Config cfg = Config.fallback();
        Engine.Result r = e.render(cfg, 1_050, 12, 0, 0, null);
        assertEquals(Grid.LED_COUNT, r.grid.litCount());
        assertEquals(150, r.nextInMs);
        r = e.render(cfg, 1_250, 12, 0, 0, null);
        assertEquals(0, r.grid.litCount());
        // Terminó la animación: vuelve la notificación.
        r = e.render(cfg, 1_500, 12, 0, 0, null);
        assertTrue(r.grid.litCount() > 0 && r.grid.litCount() < Grid.LED_COUNT);
    }
}
