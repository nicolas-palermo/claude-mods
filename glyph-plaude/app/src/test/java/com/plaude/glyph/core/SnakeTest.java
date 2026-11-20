package com.plaude.glyph.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SnakeTest {

    @Test
    public void startsInTheMiddleHeadingRight() {
        Snake s = new Snake(1);
        assertEquals(6, s.head()[0]);
        assertEquals(6, s.head()[1]);
        assertEquals(3, s.length());
        assertNotNull(s.food());
        s.step();
        assertEquals(7, s.head()[0]);
    }

    @Test
    public void diesAtTheEdgeOfTheCircle() {
        Snake s = new Snake(1);
        int steps = 0;
        while (s.state() == Snake.State.PLAYING && steps < 20) {
            s.step();
            steps++;
        }
        assertEquals(Snake.State.DEAD, s.state());
        // De (6,6) a la derecha: (7..12, 6) existen, (13, 6) no.
        assertTrue("murió en " + steps + " pasos", steps <= 7);
    }

    @Test
    public void turnsAreRelativeToTheCurrentDirection() {
        Snake s = new Snake(1);
        s.turnLeft();
        s.step(); // de derecha a arriba
        assertEquals(6, s.head()[0]);
        assertEquals(5, s.head()[1]);
        s.turnLeft();
        s.step(); // de arriba a izquierda
        assertEquals(5, s.head()[0]);
    }

    @Test
    public void eatingGrowsAndSpeedsUp() {
        Snake s = new Snake(42);
        long before = s.stepMs();
        // Guiamos la víbora hacia la fruta con un piloto simple que no la hace chocar con nada en
        // los primeros pasos: probamos hasta comer una vez.
        int guard = 0;
        while (s.score() == 0 && s.state() == Snake.State.PLAYING && guard++ < 200) {
            steer(s);
            s.step();
        }
        assertEquals(1, s.score());
        assertEquals(4, s.length());
        assertTrue(s.stepMs() < before);
    }

    @Test
    public void deadSnakeShowsScore() {
        Snake s = new Snake(1);
        while (s.state() == Snake.State.PLAYING) s.step();
        Grid g = new Grid();
        s.render(g, 0);
        assertTrue(g.litCount() > 5);
    }

    /** Gira hacia la fruta eligiendo entre seguir, izquierda o derecha sin salir del círculo. */
    private static void steer(Snake s) {
        int[] dx = {0, 1, 0, -1}, dy = {-1, 0, 1, 0};
        int[] h = s.head();
        int[] f = s.food();
        int best = Integer.MAX_VALUE, choice = 0;
        for (int turn = -1; turn <= 1; turn++) {
            int d = Math.floorMod(s.direction() + turn, 4);
            int nx = h[0] + dx[d], ny = h[1] + dy[d];
            if (!Grid.exists(nx, ny)) continue;
            int dist = Math.abs(nx - f[0]) + Math.abs(ny - f[1]);
            if (dist < best) {
                best = dist;
                choice = turn;
            }
        }
        if (choice < 0) s.turnLeft();
        if (choice > 0) s.turnRight();
    }
}
