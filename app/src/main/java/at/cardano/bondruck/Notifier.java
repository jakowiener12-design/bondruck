package at.cardano.bondruck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Eine Benachrichtigung je Kunde mit Knöpfen Drucken und Erledigt. */
final class Notifier {
    private static final String CHANNEL = "orders";

    private Notifier() {}

    static void update(Context c, String chat) {
        OrderStore.Order o = OrderStore.get(c, chat);
        if (o == null) { cancel(c, chat); return; }
        Parser.Result r = Parser.parse(o.msgs, Parser.replacements(Prefs.repl(c)));
        String addr = r.address != null ? "Adresse erkannt"
                : OrderStore.knownAddress(c, chat) != null ? "letzte Adresse bekannt" : "keine Adresse";
        String state;
        if (o.printedAt == 0) state = "noch nicht gedruckt";
        else if (o.msgs.size() > o.printedCount) state = "NEUE NACHRICHT nach Druck";
        else state = "gedruckt " + new SimpleDateFormat("HH:mm", Locale.GERMAN).format(new Date(o.printedAt));
        show(c, chat, r.items.size() + " Artikel · " + addr + " · " + state,
                o.printedAt == 0 ? "Drucken" : "Nachdrucken");
    }

    static void error(Context c, String chat, String msg) {
        show(c, chat, "DRUCKFEHLER: " + msg, "Nochmal drucken");
    }

    static void cancel(Context c, String chat) {
        nm(c).cancel(id(chat));
    }

    private static void show(Context c, String chat, String text, String printLabel) {
        NotificationManager nm = nm(c);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Bestellungen",
                NotificationManager.IMPORTANCE_DEFAULT));
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("Bestellung: " + chat)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(null, printLabel,
                        action(c, PrintReceiver.ACTION_PRINT, chat)).build())
                .addAction(new Notification.Action.Builder(null, "Erledigt",
                        action(c, PrintReceiver.ACTION_DONE, chat)).build())
                .build();
        nm.notify(id(chat), n);
    }

    private static PendingIntent action(Context c, String action, String chat) {
        Intent i = new Intent(c, PrintReceiver.class).setAction(action).putExtra(PrintReceiver.EXTRA_CHAT, chat);
        return PendingIntent.getBroadcast(c, (action + chat).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static int id(String chat) { return 1000 + (chat.hashCode() & 0x7FFFFFF); }

    private static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
