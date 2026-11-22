package at.cardano.bondruck;

import android.app.Notification;
import android.app.Person;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Liest WhatsApp Benachrichtigungen, sammelt Bestellungen und druckt automatisch. */
public class WhatsAppListener extends NotificationListenerService {

    private static final Set<String> PACKAGES =
            new HashSet<>(Arrays.asList("com.whatsapp", "com.whatsapp.w4b"));

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Map<String, Runnable> timers = new HashMap<>();

    private final Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 2000;
        }
    };

    @Override
    public void onListenerConnected() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification sbn : active) process(sbn, false);
        } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try { process(sbn, true); } catch (Exception ignored) { }
    }

    private synchronized void process(StatusBarNotification sbn, boolean live) {
        if (sbn == null || !PACKAGES.contains(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle ex = n.extras;
        if (ex == null) return;
        if (ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return; // Gruppen ignorieren

        String chat = str(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        if (chat.isEmpty()) chat = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        chat = chat.replace("‪", "").replace("‬", "").trim();
        if (chat.isEmpty() || chat.equalsIgnoreCase("WhatsApp")) return;
        if (Prefs.ignored(this).contains(chat.toLowerCase(Locale.GERMAN))) return;

        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0) {
            for (Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle m = (Bundle) p;
                String text = str(m.getCharSequence("text"));
                long time = m.getLong("time", sbn.getPostTime());
                handle(chat, text, time, chat + "|" + time + "|" + text, live);
            }
        } else {
            String text = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty()) text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
            if (text.matches("\\d+ neue Nachrichten?.*")) return;
            handle(chat, text, sbn.getPostTime(), chat + "|" + text, live);
        }
    }

    private void handle(String chat, String text, long time, String key, boolean live) {
        if (text.isEmpty() || seen.containsKey(key)) return;
        seen.put(key, Boolean.TRUE);
        if (!live) return;
        // Medien ohne Text überspringen
        if (text.matches("(?i)^(📷|🎤|🎥|📄)?\\s*(foto|bild|sprachnachricht|video|sticker|gif|dokument|standort|kontakt)\\s*$")) return;

        OrderStore.add(this, chat, text, time);
        Notifier.update(this, chat);
        if (Prefs.auto(this)) schedule(chat);
    }

    private void schedule(String chat) {
        final android.content.Context app = getApplicationContext();
        cancelTimer(chat);
        Runnable r = () -> {
            timers.remove(chat);
            OrderStore.Order o = OrderStore.get(app, chat);
            if (o == null || o.printedAt > 0) return;          // nur einmal automatisch
            Parser.Result res = Parser.parse(o.msgs, Parser.replacements(Prefs.repl(app)));
            boolean hasAddress = res.address != null || "ABHOLUNG".equals(res.type)
                    || OrderStore.knownAddress(app, chat) != null;
            if (!res.items.isEmpty() && hasAddress) Printer.printOrder(app, chat, false);
        };
        timers.put(chat, r);
        handler.postDelayed(r, Prefs.waitSec(app) * 1000L);
    }

    static void cancelTimer(String chat) {
        Runnable old = timers.remove(chat);
        if (old != null) handler.removeCallbacks(old);
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
