package com.miaoji.ledger;

import android.app.Notification;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class AutoBookkeepingService extends NotificationListenerService {
    private static final String PREFS = "miaoji_settings";

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!preferences.getBoolean("auto_enabled", true)) return;
        if (sbn == null || sbn.isOngoing()) return;

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);

        LedgerEntry entry = NotificationTransactionParser.parse(
                sbn.getPackageName(), title == null ? "" : title.toString(),
                text == null ? "" : text.toString(), sbn.getPostTime());
        if (entry == null) return;

        try (LedgerDb db = new LedgerDb(getApplicationContext())) {
            long id = db.insert(entry);
            if (id != -1) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putLong("last_auto_at", System.currentTimeMillis())
                        .putString("last_auto_merchant", entry.merchant)
                        .apply();
            }
        }
    }
}
