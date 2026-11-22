package at.cardano.bondruck;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

/** Telefonnummer eines gespeicherten Kontakts (WhatsApp zeigt dann nur den Namen). */
final class Contacts {
    private Contacts() {}

    static String phoneOf(Context c, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try (Cursor cur = c.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?",
                new String[]{name.trim()}, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                String f = Phone.format(n);
                return f != null ? f : n;
            }
        } catch (Exception ignored) {   // keine Berechtigung oder kein Kontakt
        }
        return null;
    }
}
