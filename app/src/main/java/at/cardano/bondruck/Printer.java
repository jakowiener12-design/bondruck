package at.cardano.bondruck;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Baut den Bon und schickt ihn per LAN (Port 9100, ESC/POS) an den Drucker. */
final class Printer {
    private static final String TAG = "Bondruck";
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private Printer() {}

    /** Druckt die gesammelte Bestellung eines Chats. */
    static void printOrder(Context ctx, String chat, boolean manual) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            OrderStore.Order o = OrderStore.get(app, chat);
            if (o == null) { toast(app, "Keine Bestellung für " + chat); return; }
            List<String> msgs;
            List<Long> times;
            synchronized (OrderStore.class) {
                msgs = new ArrayList<>(o.msgs);
                times = new ArrayList<>(o.times);
            }
            Parser.Result r = Parser.parse(msgs, Parser.replacements(Prefs.repl(app)));
            boolean known = false;
            if (r.address != null) OrderStore.rememberAddress(app, chat, r.address);
            else if (!"ABHOLUNG".equals(r.type)) {
                r.address = OrderStore.knownAddress(app, chat);
                known = r.address != null;
            }
            boolean reprint = o.printedAt > 0;
            try {
                send(Prefs.ip(app), Prefs.port(app),
                        build(chat, msgs, times, r, known, reprint, Prefs.size(app),
                                Prefs.logoBon(app) ? Prefs.logo(app) : null,
                                Prefs.headerBon(app) ? Prefs.header(app) : null));
                OrderStore.markPrinted(app, chat);
                OrderStore.log(chat + ": gedruckt");
                Notifier.update(app, chat);
                if (manual) toast(app, "Gedruckt");
            } catch (Exception e) {
                Log.e(TAG, "Druck fehlgeschlagen", e);
                OrderStore.log(chat + ": DRUCKFEHLER " + e.getMessage());
                toast(app, "Druck fehlgeschlagen: " + e.getMessage());
                Notifier.error(app, chat, e.getMessage());
            }
        }).start();
    }

    /** Freien Text drucken (Texteditor in der App). */
    static void printText(Context ctx, String text) {
        final Context app = ctx.getApplicationContext();
        final byte sz = Prefs.size(app) >= 3 ? (byte) 0x11 : Prefs.size(app) == 2 ? (byte) 0x01 : (byte) 0x00;
        new Thread(() -> {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                b.write(new byte[]{0x1B, 0x40, 0x1C, 0x2E, 0x1B, 0x74, 16});
                b.write(new byte[]{0x1D, 0x21, sz});
                txt(b, text.replace("\r", "").trim() + "\n");
                b.write(new byte[]{0x1D, 0x21, 0x00});
                b.write(new byte[]{0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00});
                send(Prefs.ip(app), Prefs.port(app), b.toByteArray());
                toast(app, "Gedruckt");
            } catch (Exception e) {
                toast(app, "Druck fehlgeschlagen: " + e.getMessage());
            }
        }).start();
    }

    /** Etikett mit festem Raster: Name (2 Zeilen), Beschreibung (3 Zeilen), Preis. */
    static void printLabel(Context ctx, String name, String price, String desc, int copies) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                byte[] logo = Prefs.logoLabel(app) ? Prefs.logo(app) : null;
                String header = Prefs.headerLabel(app) ? Prefs.header(app) : null;
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                b.write(new byte[]{0x1B, 0x40, 0x1C, 0x2E, 0x1B, 0x74, 16});
                boolean compact = Prefs.labelStyle(app) == 1;
                for (int c = 0; c < Math.max(1, copies); c++) {
                    head(b, logo, header);
                    if (compact) { compactLabel(b, name, price, desc); continue; }
                    b.write(new byte[]{0x1B, 0x61, 0x01});             // zentriert
                    // Name: doppelt hoch und breit, 24 Zeichen je Zeile, 2 Zeilen fix
                    b.write(new byte[]{0x1D, 0x21, 0x11, 0x1B, 0x45, 0x01});
                    for (String l : fixLines(name, 24, 2)) txt(b, l + "\n");
                    b.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x45, 0x00});
                    txt(b, "\n");
                    // Beschreibung: normal, 48 Zeichen je Zeile, 3 Zeilen fix
                    for (String l : fixLines(desc, 48, 3)) txt(b, l + "\n");
                    txt(b, "\n");
                    // Preis: doppelt hoch und breit, fett
                    b.write(new byte[]{0x1D, 0x21, 0x11, 0x1B, 0x45, 0x01});
                    String pr = price.trim();
                    if (!pr.isEmpty() && !pr.contains("€")) pr = "€ " + pr;
                    txt(b, pr + "\n");
                    b.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x45, 0x00});
                    b.write(new byte[]{0x1B, 0x61, 0x00});
                    b.write(new byte[]{0x1B, 0x64, 0x03, 0x1D, 0x56, 0x42, 0x00});
                }
                send(Prefs.ip(app), Prefs.port(app), b.toByteArray());
                toast(app, "Etikett gedruckt");
            } catch (Exception e) {
                toast(app, "Druck fehlgeschlagen: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Regaletikett: Name groß links, Beschreibung links, Preis rechts invertiert in derselben Zeile.
     * Feste Höhe: Name 2 Zeilen, Beschreibung 3 Zeilen (letzte neben dem Preis).
     */
    static void compactLabel(ByteArrayOutputStream b, String name, String price, String desc) throws IOException {
        String pr = price.trim();
        if (!pr.isEmpty() && !pr.contains("€")) pr = "€ " + pr;
        String box = " " + pr + " ";
        int priceCols = box.length() * 2;                   // doppelt breit
        int descCols = 48 - priceCols - 1;

        b.write(new byte[]{0x1B, 0x61, 0x00});              // links
        b.write(new byte[]{0x1D, 0x21, 0x11, 0x1B, 0x45, 0x01});
        for (String l : fixLines(name.toUpperCase(Locale.GERMAN), 24, 2)) txt(b, l + "\n");
        b.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x45, 0x00});

        List<String> d = fixLines(desc, descCols, 3);
        // nach unten ausrichten: letzte Beschreibungszeile steht neben dem Preis
        List<String> filled = new ArrayList<>();
        for (String l : d) if (!l.isEmpty()) filled.add(l);
        d = new ArrayList<>();
        for (int i = filled.size(); i < 3; i++) d.add("");
        d.addAll(filled);
        txt(b, d.get(0) + "\n");
        txt(b, d.get(1) + "\n");
        String last = d.get(2);
        StringBuilder pad = new StringBuilder(last);
        while (pad.length() < descCols + 1) pad.append(' ');
        txt(b, pad.toString());
        b.write(new byte[]{0x1D, 0x21, 0x11, 0x1B, 0x45, 0x01, 0x1D, 0x42, 0x01}); // groß, fett, invertiert
        txt(b, box);
        b.write(new byte[]{0x1D, 0x42, 0x00, 0x1D, 0x21, 0x00, 0x1B, 0x45, 0x00});
        txt(b, "\n");
        b.write(new byte[]{0x1B, 0x64, 0x03, 0x1D, 0x56, 0x42, 0x00});
    }

    /** Text auf genau n Zeilen umbrechen (auffüllen oder abschneiden), damit die Position fix bleibt. */
    static List<String> fixLines(String text, int width, int n) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : (text == null ? "" : text.trim()).split("\\s+")) {
            if (w.isEmpty()) continue;
            while (w.length() > width) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                out.add(w.substring(0, width));
                w = w.substring(width);
            }
            if (cur.length() > 0 && cur.length() + 1 + w.length() > width) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.size() > n) {
            out = new ArrayList<>(out.subList(0, n));
            String last = out.get(n - 1);
            out.set(n - 1, (last.length() > width - 3 ? last.substring(0, width - 3) : last) + "...");
        }
        while (out.size() < n) out.add("");
        return out;
    }

    private static final java.util.regex.Pattern WA_LINE = java.util.regex.Pattern.compile(
            "^\\[?(\\d{1,2}[:.]\\d{2})(?::\\d{2})?,?\\s+(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})\\]?\\s*(?:-\\s*)?([^:]{1,60}):\\s?(.*)$");

    /** Kopierten Text (auch WhatsApp Kopie mit [Zeit, Datum] Name:) wie eine Bestellung drucken. */
    static void printManual(Context ctx, String text) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            List<String> msgs = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            java.util.Map<String, Integer> senders = new java.util.LinkedHashMap<>();
            SimpleDateFormat f = new SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.GERMAN);
            for (String line : text.replace("\r", "").split("\n")) {
                java.util.regex.Matcher m = WA_LINE.matcher(line.trim());
                if (m.find()) {
                    String date = m.group(2);
                    if (date.matches(".*\\.\\d{2}$")) date = date.substring(0, date.length() - 2) + "20" + date.substring(date.length() - 2);
                    long t;
                    try { t = f.parse(m.group(1).replace('.', ':') + " " + date).getTime(); }
                    catch (Exception e) { t = System.currentTimeMillis(); }
                    String who = m.group(3).replace("\u202A", "").replace("\u202C", "").trim();
                    senders.put(who, senders.containsKey(who) ? senders.get(who) + 1 : 1);
                    msgs.add(m.group(4));
                    times.add(t);
                } else if (!line.trim().isEmpty()) {
                    if (msgs.isEmpty() || senders.isEmpty()) {
                        msgs.add(line);
                        times.add(System.currentTimeMillis());
                    } else {
                        int last = msgs.size() - 1;
                        msgs.set(last, msgs.get(last) + "\n" + line);
                    }
                }
            }
            if (msgs.isEmpty()) { toast(app, "Kein Text"); return; }
            // Kunde = häufigster Absender, sonst erste Telefonnummer im Text
            String chat = null;
            int best = 0;
            for (java.util.Map.Entry<String, Integer> e : senders.entrySet()) {
                if (e.getValue() > best) { best = e.getValue(); chat = e.getKey(); }
            }
            if (chat == null) {
                List<String> ph = Phone.findAll(text);
                chat = ph.isEmpty() ? "Manuelle Bestellung" : ph.get(0);
            }
            Parser.Result r = Parser.parse(msgs, Parser.replacements(Prefs.repl(app)));
            boolean known = false;
            if (r.address != null) OrderStore.rememberAddress(app, chat, r.address);
            else if (!"ABHOLUNG".equals(r.type)) {
                r.address = OrderStore.knownAddress(app, chat);
                known = r.address != null;
            }
            try {
                send(Prefs.ip(app), Prefs.port(app),
                        build(chat, msgs, times, r, known, false, Prefs.size(app),
                                Prefs.logoBon(app) ? Prefs.logo(app) : null,
                                Prefs.headerBon(app) ? Prefs.header(app) : null));
                toast(app, "Bestellung gedruckt");
            } catch (Exception e) {
                toast(app, "Druck fehlgeschlagen: " + e.getMessage());
            }
        }).start();
    }

    /** Testbon mit Beispielbestellung. */
    static void printTest(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            List<String> msgs = new ArrayList<>();
            msgs.add("2x salami mais pizza eine mexicana pizza 2 dosen eistee Pfirsich 3 cola 1 cola zero\n"
                    + "2x keba nur mit salat bitte beide kebap in Hälfte schneiden");
            msgs.add("Dreherstraße 56");
            List<Long> times = new ArrayList<>();
            long now = System.currentTimeMillis();
            times.add(now); times.add(now);
            Parser.Result r = Parser.parse(msgs, Parser.replacements(Prefs.repl(app)));
            try {
                send(Prefs.ip(app), Prefs.port(app),
                        build("+43 660 1234567", msgs, times, r, false, false, Prefs.size(app),
                                Prefs.logoBon(app) ? Prefs.logo(app) : null,
                                Prefs.headerBon(app) ? Prefs.header(app) : null));
                toast(app, "Testdruck gesendet");
            } catch (Exception e) {
                toast(app, "Druck fehlgeschlagen: " + e.getMessage());
            }
        }).start();
    }

    /** Kopf: Logo und Firmendaten zentriert. */
    static void head(ByteArrayOutputStream b, byte[] logo, String header) throws IOException {
        b.write(new byte[]{0x1B, 0x61, 0x01});
        if (logo != null) {
            b.write(logo);
            txt(b, "\n");
        }
        if (header != null && !header.trim().isEmpty()) {
            String[] lines = header.trim().split("\n");
            for (int i = 0; i < lines.length; i++) {
                b.write(new byte[]{0x1B, 0x45, (byte) (i < 2 ? 1 : 0)});
                txt(b, lines[i].trim() + "\n");
            }
            b.write(new byte[]{0x1B, 0x45, 0x00});
        }
        b.write(new byte[]{0x1B, 0x61, 0x00});
        if (logo != null || (header != null && !header.trim().isEmpty())) txt(b, "\n");
    }

    static byte[] build(String chat, List<String> msgs, List<Long> times, Parser.Result r,
                        boolean knownAddress, boolean reprint, int size) throws IOException {
        return build(chat, msgs, times, r, knownAddress, reprint, size, null, null);
    }

    static byte[] build(String chat, List<String> msgs, List<Long> times, Parser.Result r,
                        boolean knownAddress, boolean reprint, int size,
                        byte[] logo, String header) throws IOException {
        byte sz = size >= 3 ? (byte) 0x11 : size == 2 ? (byte) 0x01 : (byte) 0x00;
        int width = size >= 3 ? 24 : 48;
        String line = repeat('-', width);
        String thin = repeat('-', 48);

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(new byte[]{0x1B, 0x40});              // Initialisieren
        b.write(new byte[]{0x1C, 0x2E});              // Chinesisch aus
        b.write(new byte[]{0x1B, 0x74, 16});          // Zeichensatz WPC1252
        head(b, logo, header);

        // Kopf
        b.write(new byte[]{0x1B, 0x61, 0x01});
        b.write(new byte[]{0x1D, 0x21, 0x11, 0x1B, 0x45, 0x01});
        txt(b, (r.type != null ? r.type : "BESTELLUNG") + "\n");
        b.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x45, 0x00});
        if (reprint) txt(b, "*** NACHDRUCK ***\n");
        long t = times.isEmpty() ? System.currentTimeMillis() : times.get(0);
        txt(b, new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.GERMAN).format(new Date(t)) + " Uhr\n");
        b.write(new byte[]{0x1B, 0x61, 0x00});
        txt(b, repeat('=', 48) + "\n");

        String dbl = repeat('=', 48);

        // Artikel
        b.write(new byte[]{0x1B, 0x45, 0x01});
        txt(b, "ARTIKEL\n");
        b.write(new byte[]{0x1B, 0x45, 0x00});
        txt(b, "\n");
        b.write(new byte[]{0x1D, 0x21, sz});
        if (r.items.isEmpty()) txt(b, "(keine Artikel erkannt,\n siehe Original unten)\n");
        for (int i = 0; i < r.items.size(); i++) {
            Parser.Item it = r.items.get(i);
            b.write(new byte[]{0x1B, 0x45, 0x01});
            txt(b, it.qty + "x " + it.name + "\n");
            b.write(new byte[]{0x1B, 0x45, 0x00});
            for (String n : it.notes) txt(b, "   - " + n + "\n");
            if (i < r.items.size() - 1) txt(b, "\n");          // Leerzeile zwischen Artikeln
        }
        b.write(new byte[]{0x1D, 0x21, 0x00});

        if (!r.hints.isEmpty()) {
            txt(b, "\n" + thin + "\n");
            b.write(new byte[]{0x1B, 0x45, 0x01});
            txt(b, "INFO\n");
            b.write(new byte[]{0x1B, 0x45, 0x00});
            b.write(new byte[]{0x1D, 0x21, sz});
            for (String h : r.hints) txt(b, h + "\n");
            b.write(new byte[]{0x1D, 0x21, 0x00});
        }
        txt(b, dbl + "\n");

        // Kunde, Telefon, Adresse jeweils mit Überschrift und Wert darunter
        LinkedHashSet<String> phones = new LinkedHashSet<>();
        String chatPhone = Phone.ifNumber(chat);
        if (chatPhone != null) phones.add(chatPhone);
        for (String m : msgs) phones.addAll(Phone.findAll(m));

        if (chatPhone == null) {
            section(b, "KUNDE", sz);
            value(b, chat, sz);
            txt(b, "\n");
        }
        section(b, "TELEFON", sz);
        if (phones.isEmpty()) value(b, "-", sz);
        for (String p : phones) value(b, p, sz);

        if (!"ABHOLUNG".equals(r.type)) {
            txt(b, "\n");
            section(b, "ADRESSE", sz);
            if (r.address == null) value(b, "-", sz);
            else for (String part : r.address.split(",\\s*")) value(b, part, sz);
            if (knownAddress) txt(b, "(letzte bekannte Adresse, bitte pruefen)\n");
        }
        txt(b, dbl + "\n");

        // Original klein zur Kontrolle
        b.write(new byte[]{0x1B, 0x4D, 0x01});        // kleine Schrift
        txt(b, "Original:\n");
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMAN);
        for (int i = 0; i < msgs.size(); i++) {
            txt(b, hm.format(new Date(times.get(i))) + "  " + msgs.get(i).replace("\r", "").trim() + "\n\n");
        }
        b.write(new byte[]{0x1B, 0x4D, 0x00});

        b.write(new byte[]{0x1B, 0x64, 0x04});        // Vorschub
        b.write(new byte[]{0x1D, 0x56, 0x42, 0x00});  // Schnitt
        return b.toByteArray();
    }

    private static void section(ByteArrayOutputStream b, String title, byte sz) throws IOException {
        b.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x45, 0x01});
        txt(b, title + "\n");
        b.write(new byte[]{0x1B, 0x45, 0x00});
    }

    private static void value(ByteArrayOutputStream b, String v, byte sz) throws IOException {
        b.write(new byte[]{0x1D, 0x21, sz, 0x1B, 0x45, 0x01});
        txt(b, v.trim() + "\n");
        b.write(new byte[]{0x1D, 0x21, 0x00, 0x1B, 0x45, 0x00});
    }

    private static String repeat(char c, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append(c);
        return s.toString();
    }

    private static void txt(ByteArrayOutputStream b, String s) throws IOException {
        StringBuilder clean = new StringBuilder();
        CharsetEncoder enc = CP1252.newEncoder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (cp == '\n' || enc.canEncode(ch)) clean.append(ch);
            i += Character.charCount(cp);
        }
        b.write(clean.toString().getBytes(CP1252));
    }

    private static synchronized void send(String ip, int port, byte[] data) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 5000);
            OutputStream o = s.getOutputStream();
            o.write(data);
            o.flush();
        }
    }

    static void toast(Context c, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(c, msg, Toast.LENGTH_LONG).show());
    }
}
