package com.plaude.glyph;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Vincula el teléfono con el backend de Glyph Events. Se abre desde el link
 * plaude://pair?u=<url del backend>&k=<clave del dispositivo> que muestra la web de administración.
 */
public class PairActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.BLACK);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        col.setPadding(pad, pad * 2, pad, pad);
        TextView title = new TextView(this);
        title.setText("Glyph Events");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        col.addView(title);
        status = new TextView(this);
        status.setTextColor(Color.rgb(190, 190, 190));
        status.setTextSize(16);
        status.setPadding(0, pad, 0, 0);
        status.setTextIsSelectable(true);
        col.addView(status);
        setContentView(col);

        Uri u = getIntent().getData();
        String base = u == null ? null : u.getQueryParameter("u");
        String key = u == null ? null : u.getQueryParameter("k");
        if (base == null || key == null || !base.startsWith("https://")) {
            status.setText("El link de vinculación está incompleto. Abrí la web de Glyph Events en este teléfono, "
                    + "entrá a Teléfono y tocá Vincular este teléfono.");
            return;
        }
        status.setText("Vinculando con " + Uri.parse(base).getHost() + "…");
        Events.pair(this, base, key, err -> runOnUiThread(() -> status.setText(err == null
                ? "Listo: este teléfono va a recibir los eventos. Probalo con el botón Probar de la web."
                : "No se pudo vincular: " + err)));
    }
}
