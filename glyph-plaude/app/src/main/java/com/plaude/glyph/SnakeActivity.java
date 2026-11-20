package com.plaude.glyph;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;
import com.plaude.glyph.core.Grid;
import com.plaude.glyph.core.Snake;

/**
 * Snake en la matriz. Corre en su propio proceso (":game") para no pisar la conexión del toy:
 * el SDK usa una sola instancia de GlyphMatrixManager por proceso.
 *
 * Volumen abajo = girar a la izquierda, volumen arriba = girar a la derecha.
 * También se puede tocar la mitad izquierda o derecha de la pantalla.
 */
public class SnakeActivity extends Activity {

    private static final String TAG = "PlaudeGlyph";
    private static final String PREFS = "snake";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable step = this::step;
    private final Runnable draw = this::draw;

    private GlyphMatrixManager gm;
    private boolean glyphReady;
    private Snake snake;
    private MatrixView view;
    private TextView status;
    private long deadAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(24);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = text("SNAKE", 28, Color.WHITE);
        root.addView(title);
        status = text("", 16, Color.rgb(170, 170, 170));
        root.addView(status);

        view = new MatrixView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        root.addView(view, lp);

        TextView help = text("Volumen abajo: izquierda · Volumen arriba: derecha\n"
                + "O tocá la mitad izquierda o derecha de la pantalla.\n"
                + "Dá vuelta el teléfono para jugar mirando la matriz.", 14, Color.rgb(140, 140, 140));
        help.setPadding(0, dp(24), 0, 0);
        root.addView(help);

        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (e.getX() < v.getWidth() / 2f) left();
                else right();
                v.performClick();
            }
            return true;
        });
        setContentView(root);
        newGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gm = GlyphMatrixManager.getInstance(getApplicationContext());
        gm.init(new GlyphMatrixManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName name) {
                try {
                    gm.register(Glyph.DEVICE_25111p);
                } catch (Exception e) {
                    Log.e(TAG, "register falló", e);
                }
                glyphReady = true;
                draw();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                glyphReady = false;
            }
        });
        main.post(step);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(step);
        main.removeCallbacks(draw);
        if (gm != null) {
            try {
                if (glyphReady) gm.closeAppMatrix();
            } catch (Exception e) {
                Log.w(TAG, "closeAppMatrix falló", e);
            }
            gm.unInit();
            glyphReady = false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            left();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            right();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Que el volumen no suba ni baje mientras se juega.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true;
        return super.onKeyUp(keyCode, event);
    }

    private void left() {
        if (restartIfDead()) return;
        snake.turnLeft();
    }

    private void right() {
        if (restartIfDead()) return;
        snake.turnRight();
    }

    /** Después de perder, cualquier tecla arranca otra partida (con un segundo de gracia). */
    private boolean restartIfDead() {
        if (snake.state() != Snake.State.DEAD) return false;
        if (System.currentTimeMillis() - deadAt > 1000) {
            newGame();
            main.removeCallbacks(step);
            main.post(step);
        }
        return true;
    }

    private void newGame() {
        snake = new Snake(System.nanoTime());
        updateStatus();
    }

    private void step() {
        if (snake.state() == Snake.State.PLAYING) {
            snake.step();
            if (snake.state() == Snake.State.DEAD) {
                deadAt = System.currentTimeMillis();
                int best = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("best", 0);
                if (snake.score() > best) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("best", snake.score()).apply();
                }
            }
            updateStatus();
        }
        draw();
        if (snake.state() == Snake.State.PLAYING) main.postDelayed(step, snake.stepMs());
    }

    private void draw() {
        main.removeCallbacks(draw);
        Grid g = new Grid();
        snake.render(g, System.currentTimeMillis());
        view.setLevels(g.levels());
        if (glyphReady) {
            try {
                gm.setAppMatrixFrame(g.toSdkFrame(100));
            } catch (Exception e) {
                Log.e(TAG, "setAppMatrixFrame falló", e);
            }
        }
        // La fruta titila aunque la víbora vaya lento.
        main.postDelayed(draw, 200);
    }

    private void updateStatus() {
        int best = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("best", 0);
        String s = "Puntos: " + snake.score() + "   Récord: " + Math.max(best, snake.score());
        if (snake.state() == Snake.State.DEAD) s += "\nPerdiste. Tocá o apretá volumen para jugar de nuevo.";
        status.setText(s);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
