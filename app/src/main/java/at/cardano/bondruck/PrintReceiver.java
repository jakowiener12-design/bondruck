package at.cardano.bondruck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Knöpfe aus der Benachrichtigung. */
public class PrintReceiver extends BroadcastReceiver {
    static final String ACTION_PRINT = "at.cardano.bondruck.PRINT";
    static final String ACTION_DONE = "at.cardano.bondruck.DONE";
    static final String EXTRA_CHAT = "chat";

    @Override
    public void onReceive(Context c, Intent i) {
        String chat = i.getStringExtra(EXTRA_CHAT);
        if (chat == null) return;
        if (ACTION_PRINT.equals(i.getAction())) {
            Printer.printOrder(c, chat, true);
        } else if (ACTION_DONE.equals(i.getAction())) {
            WhatsAppListener.cancelTimer(chat);
            OrderStore.remove(c, chat);
            Notifier.cancel(c, chat);
        }
    }
}
