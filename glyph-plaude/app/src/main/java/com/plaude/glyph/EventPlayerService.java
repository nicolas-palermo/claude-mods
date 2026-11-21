package com.plaude.glyph;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;
import com.plaude.glyph.core.EventAnimation;
import com.plaude.glyph.core.Grid;

import java.io.File;

/**
 * Reproduce un evento: el sonido (bajado una vez y guardado en caché) y, si el toy no está activo,
 * la animación en la matriz con setAppMatrixFrame. Corre en su propio proceso (":player") porque
 * el SDK usa una sola conexión por proceso y el toy ya tiene la suya.
 */
public class EventPlayerService extends Service {

    static final String EXTRA_ANIM = "anim";
    static final String EXTRA_SOUND = "sound";
    static final String EXTRA_SOUND_KEY = "soundKey";

    private static final String TAG = "PlaudeGlyph";
    private static final String CHANNEL = "events";
    private static final long SAFETY_MS = 45_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable frame = this::frame;
    private final Runnable stop = this::finish;

    private GlyphMatrixManager gm;
    private boolean glyphReady;
    private EventAnimation anim;
    private long animStart;
    private boolean animDone = true;
    private boolean soundDone = true;
    private MediaPlayer player;
    private int generation;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Glyph Events", NotificationManager.IMPORTANCE_MIN));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.plaude_logo)
                .setContentTitle("Glyph Events")
                .setContentText("Reproduciendo un evento")
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (intent == null) {
            finish();
            return START_NOT_STICKY;
        }
        // Un evento nuevo corta al anterior.
        generation++;
        stopSound();
        main.removeCallbacks(frame);
        main.removeCallbacks(stop);
        main.postDelayed(stop, SAFETY_MS);

        String animJson = intent.getStringExtra(EXTRA_ANIM);
        anim = null;
        animDone = true;
        if (animJson != null) {
            try {
                anim = EventParser.parse(animJson);
                animDone = false;
                startAnimation();
            } catch (Exception e) {
                Log.e(TAG, "animación inválida", e);
            }
        }
        String sound = intent.getStringExtra(EXTRA_SOUND);
        soundDone = true;
        if (sound != null && !sound.isEmpty()) {
            soundDone = false;
            String key = intent.getStringExtra(EXTRA_SOUND_KEY);
            int gen = generation;
            new Thread(() -> {
                try {
                    File f = SoundCache.get(this, key == null ? sound : key, sound);
                    main.post(() -> {
                        if (gen == generation) playSound(f);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "no pude bajar el sonido", e);
                    main.post(() -> {
                        if (gen == generation) {
                            soundDone = true;
                            maybeFinish();
                        }
                    });
                }
            }, "events-sound").start();
        }
        maybeFinish();
        return START_NOT_STICKY;
    }

    private void startAnimation() {
        animStart = System.currentTimeMillis();
        if (gm == null) {
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
                    animStart = System.currentTimeMillis();
                    frame();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    glyphReady = false;
                }
            });
        } else if (glyphReady) {
            frame();
        }
    }

    private void frame() {
        if (anim == null || !glyphReady) return;
        long t = System.currentTimeMillis() - animStart;
        if (t >= anim.totalMs()) {
            closeMatrix();
            animDone = true;
            maybeFinish();
            return;
        }
        Grid g = new Grid();
        anim.render(g, t);
        try {
            gm.setAppMatrixFrame(g.toSdkFrame(100));
        } catch (Exception e) {
            Log.e(TAG, "setAppMatrixFrame falló", e);
        }
        long step = anim.frameIntervalMs();
        main.postDelayed(frame, step - (t % step));
    }

    private void playSound(File f) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                stopSound();
                soundDone = true;
                maybeFinish();
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "no pude reproducir el sonido", e);
            stopSound();
            soundDone = true;
            maybeFinish();
        }
    }

    private void stopSound() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
                // ya estaba liberado
            }
            player = null;
        }
    }

    private void closeMatrix() {
        main.removeCallbacks(frame);
        if (gm != null && glyphReady) {
            try {
                gm.closeAppMatrix();
            } catch (Exception e) {
                Log.w(TAG, "closeAppMatrix falló", e);
            }
        }
    }

    private void maybeFinish() {
        if (animDone && soundDone) finish();
    }

    private void finish() {
        main.removeCallbacks(stop);
        closeMatrix();
        stopSound();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        stopSound();
        if (gm != null) {
            closeMatrix();
            try {
                gm.unInit();
            } catch (Exception ignored) {
                // nada que hacer
            }
            gm = null;
        }
        super.onDestroy();
    }
}
