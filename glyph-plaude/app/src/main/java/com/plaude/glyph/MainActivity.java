package com.plaude.glyph;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.plaude.glyph.core.Config;

import java.util.Map;

/** Pantalla de la app: vista previa en vivo, estado y accesos a lo que hay que configurar. */
public class MainActivity extends Activity implements GlyphHub.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;

    private MatrixView preview;
    private TextView previewNote;
    private TextView status;
    private Button notifButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        col.setPadding(pad, pad * 2, pad, pad * 2);

        TextView title = new TextView(this);
        title.setText("Plaude Glyph");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        col.addView(title);

        TextView sub = label("Lo que muestra la matriz sale de glyph.json en GitHub. Un push lo cambia en minutos.");
        col.addView(sub);

        preview = new MatrixView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(260));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(24);
        col.addView(preview, lp);

        previewNote = label("");
        previewNote.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(previewNote);

        status = label("");
        status.setTextIsSelectable(true);
        status.setPadding(0, dp(16), 0, dp(8));
        col.addView(status);

        col.addView(button("Elegir Plaude como Always-on Glyph Toy", v -> openToyPicker()));
        notifButton = button("", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        col.addView(notifButton);
        col.addView(button("Jugar Snake en la matriz", v -> startActivity(new Intent(this, SnakeActivity.class))));
        col.addView(button("Actualizar contenido ahora", v -> GlyphHub.refresh(this, true)));

        TextView tip = label("Si la matriz queda apagada después de elegir el toy, apagá y prendé la Glyph Interface: es un bug conocido del sistema.");
        tip.setPadding(0, dp(16), 0, 0);
        col.addView(tip);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        GlyphHub.addListener(this);
        GlyphHub.refresh(this, false);
        poll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        GlyphHub.removeListener(this);
        main.removeCallbacks(poll);
    }

    @Override
    public void onChanged() {
        main.post(this::updateStatus);
    }

    /** La vista previa sigue al toy si está corriendo; si no, dibuja ella misma. */
    private void poll() {
        if (GlyphHub.toyRunning() && System.currentTimeMillis() - GlyphHub.lastDrawAt() < 10_000) {
            preview.setLevels(GlyphHub.lastLevels());
            previewNote.setText("En vivo: esto es lo que está mostrando la matriz.");
            main.postDelayed(poll, 100);
        } else {
            long next = GlyphHub.render(this).nextInMs;
            preview.setLevels(GlyphHub.lastLevels());
            previewNote.setText("Vista previa. El toy no está activo ahora.");
            main.postDelayed(poll, Math.min(next, 1000));
        }
        updateStatus();
    }

    private void updateStatus() {
        Config cfg = RemoteContent.config(this);
        StringBuilder sb = new StringBuilder();
        long at = RemoteContent.fetchedAt(this);
        sb.append("Contenido: ").append(cfg.playlist.entries().size()).append(" pantalla(s)");
        sb.append(at == 0 ? ", todavía sin bajar" : ", actualizado " + DateUtils.getRelativeTimeSpanString(at));
        String err = RemoteContent.lastError(this);
        if (err != null) sb.append("\nError al actualizar: ").append(err);
        Map<String, com.plaude.glyph.core.RenderContext.DataValue> data = LiveData.values();
        for (String key : cfg.data.keySet()) {
            com.plaude.glyph.core.RenderContext.DataValue v = data.get(key);
            sb.append("\nDato ").append(key).append(": ").append(v == null ? "--" : v.text);
            String e = LiveData.errors().get(key);
            if (e != null) sb.append(" (error: ").append(e).append(")");
        }
        boolean notif = GlyphNotificationListener.isConnected();
        sb.append("\nNotificaciones: ").append(notif ? "activadas" : "sin permiso");
        if (Events.paired(this)) {
            android.content.SharedPreferences ep = Events.prefs(this);
            sb.append("\nGlyph Events: vinculado a ").append(android.net.Uri.parse(Events.baseUrl(this)).getHost());
            long last = ep.getLong("last_play_at", 0);
            if (last > 0) sb.append(", último evento \"").append(ep.getString("last_play", "")).append("\" ")
                    .append(DateUtils.getRelativeTimeSpanString(last));
            String e = ep.getString("error", null);
            if (e != null) sb.append(" (error: ").append(e).append(")");
        } else {
            sb.append("\nGlyph Events: sin vincular. Abrí la web de Glyph Events en este teléfono y tocá Vincular.");
        }
        sb.append("\nVersión ").append(BuildConfigCompat.versionName(this));
        status.setText(sb.toString());
        notifButton.setText(notif ? "Acceso a notificaciones: activado" : "Dar acceso a notificaciones");
    }

    private void openToyPicker() {
        String pkg = "com.nothing.thirdparty";
        String[] activities = {
                "com.nothing.thirdparty.matrix.toys.manager.AodToySelectActivity",
                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
        };
        for (String a : activities) {
            try {
                startActivity(new Intent().setComponent(new ComponentName(pkg, a)));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // probamos la siguiente
            }
        }
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(28));
        b.setBackground(bg);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.rgb(170, 170, 170));
        t.setTextSize(15);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
