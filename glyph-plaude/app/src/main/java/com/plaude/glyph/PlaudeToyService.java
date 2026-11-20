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
import android.util.Log;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphToy;

/**
 * Base de un Glyph Toy para el Nothing Phone (4a) Pro: se conecta al servicio de Glyph,
 * registra el dispositivo y reenvía los eventos. La subclase decide qué dibujar.
 */
public abstract class PlaudeToyService extends Service {

    protected static final String TAG = "PlaudeGlyph";

    private GlyphMatrixManager mGM;
    private boolean mConnected;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                Bundle data = msg.getData();
                onGlyphEvent(data.getString(GlyphToy.MSG_GLYPH_TOY_DATA));
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
                Log.e(TAG, "register falló", e);
            }
            mConnected = true;
            onConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mConnected = false;
            onDisconnected();
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
        mConnected = false;
        onDisconnected();
        if (mGM != null) {
            try {
                mGM.turnOff();
            } catch (Exception e) {
                Log.w(TAG, "turnOff falló", e);
            }
            mGM.unInit();
            mGM = null;
        }
        return false;
    }

    /** Ya se puede dibujar. */
    protected abstract void onConnected();

    /** Ya no se puede dibujar: frenar loops. */
    protected abstract void onDisconnected();

    /** Eventos del sistema: "aod" cada minuto; en el (4a) Pro no hay botón. */
    protected void onGlyphEvent(String event) {
    }

    protected boolean isConnected() {
        return mConnected && mGM != null;
    }

    /** Manda un frame crudo (169 valores de 0 a 4095). */
    protected void show(int[] frame) {
        if (!isConnected()) return;
        try {
            mGM.setMatrixFrame(frame);
        } catch (Exception e) {
            Log.e(TAG, "setMatrixFrame falló", e);
        }
    }
}
