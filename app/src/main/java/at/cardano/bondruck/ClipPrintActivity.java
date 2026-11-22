package at.cardano.bondruck;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

/** Unsichtbar: liest die Zwischenablage (geht nur mit Fokus) und druckt sie als Bestellung. */
public class ClipPrintActivity extends Activity {
    private boolean done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String text = null;
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence t = clip.getItemAt(0).coerceToText(this);
                if (t != null) text = t.toString();
            }
        }
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "Zwischenablage ist leer. Zuerst Nachrichten kopieren.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Drucke Bestellung…", Toast.LENGTH_SHORT).show();
            Printer.printManual(this, text);
        }
        finish();
    }
}
