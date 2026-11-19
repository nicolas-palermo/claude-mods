package com.plaude.glyph;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphToy;

/**
 * Base de los Glyph Toys AOD que muestran el logo de Plaude en la matriz 13x13
 * (137 LEDs, circular) del Nothing Phone (4a) Pro. La subclase define el LOGO.
 *
 * '#' = LED lleno, '+' = LED a media intensidad, '.' = apagado.
 * Las esquinas fuera del círculo no existen físicamente en el (4a) Pro.
 */
public abstract class PlaudeToyService extends Service {

    private static final int SIZE = 13;
    private static final int FULL = 255;
    private static final int HALF = 128;

    /** 13 filas de 13 caracteres. */
    protected abstract String[] logo();

    private GlyphMatrixManager mGM;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                Bundle data = msg.getData();
                String event = data.getString(GlyphToy.MSG_GLYPH_TOY_DATA);
                if (GlyphToy.EVENT_AOD.equals(event)) {
                    draw(); // llega una vez por minuto en AOD
                }
            } else {
                super.handleMessage(msg);
            }
        }
    };
    private final Messenger mMessenger = new Messenger(mHandler);

    private final GlyphMatrixManager.Callback mCallback = new GlyphMatrixManager.Callback() {
        @Override
        public void onServiceConnected(ComponentName name) {
            try {
                mGM.register(Glyph.DEVICE_25111p);
            } catch (Exception e) {
                android.util.Log.e("PlaudeGlyph", "register falló", e);
            }
            draw();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        mGM = GlyphMatrixManager.getInstance(getApplicationContext());
        mGM.init(mCallback);
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (mGM != null) {
            mGM.unInit();
            mGM = null;
        }
        return false;
    }

    private void draw() {
        if (mGM == null) return;
        try {
            mGM.setMatrixFrame(buildFrame());
        } catch (Exception e) {
            android.util.Log.e("PlaudeGlyph", "setMatrixFrame falló", e);
        }
    }

    private int[] buildFrame() {
        String[] logo = logo();
        int[] frame = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                char c = logo[y].charAt(x);
                frame[y * SIZE + x] = c == '#' ? FULL : c == '+' ? HALF : 0;
            }
        }
        return frame;
    }
}
