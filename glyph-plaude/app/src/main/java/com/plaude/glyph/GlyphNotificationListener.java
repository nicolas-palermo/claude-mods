package com.plaude.glyph;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Escucha las notificaciones del teléfono (con el permiso que el usuario da en Ajustes) y las
 * pasa al toy: un ícono por app y el teléfono mientras dura una llamada.
 */
public class GlyphNotificationListener extends NotificationListenerService {

    private static volatile boolean connected = false;

    static boolean isConnected() {
        return connected;
    }

    @Override
    public void onListenerConnected() {
        connected = true;
        GlyphHub.changed();
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        GlyphHub.changed();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (isCall(n)) {
            GlyphHub.callStarted(this);
            return;
        }
        // Ignoramos lo que no es un aviso nuevo: resúmenes de grupo, persistentes y actualizaciones
        // que la app marca para no volver a avisar.
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (sbn.isOngoing()) return;
        if ((n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0) return;
        GlyphHub.onNotification(this, sbn.getPackageName());
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && isCall(sbn.getNotification())) GlyphHub.callEnded();
    }

    private static boolean isCall(Notification n) {
        return n != null && Notification.CATEGORY_CALL.equals(n.category);
    }
}
