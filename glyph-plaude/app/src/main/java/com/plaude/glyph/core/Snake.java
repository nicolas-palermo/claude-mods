package com.plaude.glyph.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Snake para la matriz redonda: el borde del círculo es la pared y se juega con dos botones
 * (girar a la izquierda y a la derecha), que es lo que dan las teclas de volumen.
 */
public final class Snake {

    public enum State { PLAYING, DEAD }

    // Arriba, derecha, abajo, izquierda.
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};

    private final Random random;
    private final Deque<int[]> body = new ArrayDeque<>();
    private int dir = 1;
    private int pendingTurn = 0;
    private int[] food;
    private int score = 0;
    private State state = State.PLAYING;

    public Snake(long seed) {
        this.random = new Random(seed);
        body.addFirst(new int[]{4, 6});
        body.addFirst(new int[]{5, 6});
        body.addFirst(new int[]{6, 6});
        placeFood();
    }

    public void turnLeft() {
        pendingTurn = -1;
    }

    public void turnRight() {
        pendingTurn = 1;
    }

    public State state() {
        return state;
    }

    public int score() {
        return score;
    }

    public int length() {
        return body.size();
    }

    /** 0 arriba, 1 derecha, 2 abajo, 3 izquierda. */
    public int direction() {
        return dir;
    }

    public int[] head() {
        return body.peekFirst().clone();
    }

    public int[] food() {
        return food == null ? null : food.clone();
    }

    /** Milisegundos por paso: arranca tranquilo y acelera con cada fruta. */
    public long stepMs() {
        return Math.max(110, 320 - score * 15L);
    }

    /** Avanza un paso. Devuelve true si comió. */
    public boolean step() {
        if (state != State.PLAYING) return false;
        dir = Math.floorMod(dir + pendingTurn, 4);
        pendingTurn = 0;
        int[] h = body.peekFirst();
        int nx = h[0] + DX[dir], ny = h[1] + DY[dir];
        boolean eats = food != null && nx == food[0] && ny == food[1];
        if (!Grid.exists(nx, ny) || hitsBody(nx, ny, !eats)) {
            state = State.DEAD;
            return false;
        }
        body.addFirst(new int[]{nx, ny});
        if (eats) {
            score++;
            placeFood();
        } else {
            body.removeLast();
        }
        return eats;
    }

    private boolean hitsBody(int x, int y, boolean tailMoves) {
        int i = 0, last = body.size() - 1;
        for (int[] p : body) {
            if (!(tailMoves && i == last) && p[0] == x && p[1] == y) return true;
            i++;
        }
        return false;
    }

    private void placeFood() {
        List<int[]> free = new ArrayList<>();
        for (int y = 0; y < Grid.SIZE; y++) {
            for (int x = 0; x < Grid.SIZE; x++) {
                if (Grid.exists(x, y) && !hitsBody(x, y, false)) free.add(new int[]{x, y});
            }
        }
        food = free.isEmpty() ? null : free.get(random.nextInt(free.size()));
        if (food == null) state = State.DEAD; // llenó la matriz
    }

    public void render(Grid g, long nowMs) {
        g.clear();
        if (state == State.DEAD) {
            g.drawTextCentered(String.valueOf(score), 4, Grid.ON);
            return;
        }
        int i = 0;
        for (int[] p : body) {
            g.set(p[0], p[1], i == 0 ? Grid.ON : 150);
            i++;
        }
        if (food != null && (nowMs / 200) % 2 == 0) g.set(food[0], food[1], Grid.ON);
        else if (food != null) g.set(food[0], food[1], 60);
    }
}
