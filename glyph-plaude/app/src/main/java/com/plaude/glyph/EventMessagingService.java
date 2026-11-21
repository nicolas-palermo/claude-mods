package com.plaude.glyph;

import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.plaude.glyph.core.EventAnimation;

import org.json.JSONObject;

import java.util.Map;

/**
 * Recibe los push de Glyph Events. Es un reproductor tonto: la animación viene completa en el
 * mensaje (o, si no entraba, se baja del backend por su id) y no hay reglas acá.
 *
 * Si el toy Plaude está activo, la animación se dibuja como overlay del toy (los toys tienen
 * prioridad sobre las apps). Si no, la dibuja EventPlayerService con setAppMatrixFrame.
 * El sonido siempre lo reproduce EventPlayerService.
 */
public class EventMessagingService extends FirebaseMessagingService {

    private static final String TAG = "PlaudeGlyph";

    @Override
    public void onNewToken(String token) {
        Events.registerToken(this, token, null);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> d = message.getData();
        if (!"play".equals(d.get("type"))) return;
        String playId = d.get("playId");
        String anim = d.get("anim");
        String sound = d.get("sound");
        String soundKey = d.get("soundKey");
        try {
            if ((anim == null || anim.isEmpty()) && playId != null && Events.baseUrl(this) != null) {
                // No entraba en el push (límite de 4 KB): la bajamos.
                JSONObject full = new JSONObject(Http.get(
                        Events.baseUrl(this) + "/api/device/play/" + playId, Events.authHeaders(this)));
                anim = full.optString("anim", null);
                if (sound == null) sound = full.optString("sound", null);
                if (soundKey == null) soundKey = full.optString("soundKey", null);
            }
            EventAnimation a = anim == null || anim.isEmpty() ? null : EventParser.parse(anim);
            String via = "sin animación";
            boolean appMatrix = false;
            if (a != null) {
                if (GlyphHub.toyRunning()) {
                    GlyphHub.playEvent(a);
                    via = "toy";
                } else {
                    appMatrix = true;
                    via = "app";
                }
            }
            if (appMatrix || (sound != null && !sound.isEmpty())) {
                Intent i = new Intent(this, EventPlayerService.class)
                        .putExtra(EventPlayerService.EXTRA_ANIM, appMatrix ? anim : null)
                        .putExtra(EventPlayerService.EXTRA_SOUND, sound)
                        .putExtra(EventPlayerService.EXTRA_SOUND_KEY, soundKey);
                startForegroundService(i);
            }
            Events.recordPlay(this, d.containsKey("title") ? d.get("title") : "evento");
            Events.ack(this, playId, "played", via + (sound != null && !sound.isEmpty() ? " + sonido" : ""));
        } catch (Exception e) {
            Log.e(TAG, "No pude reproducir el evento", e);
            Events.ack(this, playId, "error", e.getMessage());
        }
    }
}
