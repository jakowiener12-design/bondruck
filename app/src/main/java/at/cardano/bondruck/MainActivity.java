package at.cardano.bondruck;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int ORANGE = Color.rgb(211, 84, 0);
    private static final int BG = Color.rgb(242, 242, 242);
    private static final String[] TABS = {"Bestellungen", "Archiv", "Text", "Etiketten", "Einstellungen"};
    private static final int T_ORDERS = 0, T_ARCHIVE = 1, T_TEXT = 2, T_LABELS = 3, T_SETTINGS = 4;

    private EditText ip, port, wait, window, repl, ignore, free, labels, copies, header, streets, pfand, lHeight, lTop, search, lName, lPrice, lDesc;
    private final List<Integer> shown = new ArrayList<>();   // Index in labelList je Eintrag im Spinner
    private Switch logoBon, logoLabel, headerBon, headerLabel, prices;
    private Switch auto;
    private RadioGroup size, labelStyle, enc;
    private Spinner labelPick;
    private TextView status;
    private LinearLayout orderList, logList;

    private final List<TextView> tabViews = new ArrayList<>();
    private final List<View> pages = new ArrayList<>();
    private int current = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().setTitle("Bondruck");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Reiterleiste
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(Color.WHITE);
        tabs.setElevation(dp(2));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(TABS[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(16);
            t.setPadding(0, dp(14), 0, dp(14));
            t.setOnClickListener(v -> showTab(idx));
            tabs.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            tabViews.add(t);
        }
        root.addView(tabs);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        pages.add(page(content, buildOrders()));
        pages.add(page(content, buildArchive()));
        pages.add(page(content, buildText()));
        pages.add(page(content, buildLabels()));
        pages.add(page(content, buildSettings()));

        setContentView(root);
        fillLabels();

        String crash = getSharedPreferences("crash", MODE_PRIVATE).getString("last", null);
        if (crash != null) {
            getSharedPreferences("crash", MODE_PRIVATE).edit().remove("last").apply();
            final String c = crash;
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Bondruck ist abgestürzt")
                    .setMessage("Bitte diesen Fehler kopieren und schicken:\n\n" + c)
                    .setPositiveButton("Kopieren", (d, w) -> {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Fehler", c));
                    })
                    .setNegativeButton("Schließen", null)
                    .show();
        }

        boolean shared = Intent.ACTION_SEND.equals(getIntent().getAction());
        showTab(shared ? T_TEXT : T_ORDERS);
        takeShared(getIntent());

        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.READ_CONTACTS);
        if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), 1);
    }

    // ---------- Seiten ----------

    private LinearLayout buildOrders() {
        LinearLayout p = column();
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(110), dp(110));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(12);
        p.addView(logo, lp);
        LinearLayout c = card(p, "Status");
        status = new TextView(this);
        status.setTextSize(16);
        c.addView(status);

        LinearLayout o = card(p, "Offene Bestellungen");
        orderList = new LinearLayout(this);
        orderList.setOrientation(LinearLayout.VERTICAL);
        o.addView(orderList);

        LinearLayout lg = card(p, "Protokoll");
        logList = new LinearLayout(this);
        logList.setOrientation(LinearLayout.VERTICAL);
        lg.addView(logList);
        button(lg, "Aktualisieren", false, this::refreshOrders);
        return p;
    }

    private LinearLayout buildText() {
        LinearLayout p = column();
        LinearLayout c = card(p, "Text oder Bestellung drucken");
        hint(c, "Nachrichten in WhatsApp markieren, kopieren und hier einfügen, oder über Teilen > Bondruck schicken.");
        free = input(c, null, "", InputType.TYPE_CLASS_TEXT, true);
        free.setMinLines(8);
        free.setTextSize(18);
        free.setGravity(Gravity.TOP);
        free.setHint("Text eingeben oder aus einer anderen App über Teilen > Bondruck schicken");
        LinearLayout ph = row(c);
        button(ph, "Letztes WhatsApp Foto lesen", true, this::readLatestPhoto);
        button(ph, "Foto auswählen", false, () -> {
            Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            i.setType("image/*");
            startActivityForResult(i, 4);
        });
        button(c, "Zwischenablage als Bestellung drucken", false, () -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            CharSequence t = null;
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0)
                t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (t == null || t.toString().trim().isEmpty()) { toast("Zwischenablage ist leer"); return; }
            free.setText(t);
            Printer.printManual(this, t.toString());
        });
        button(c, "Als Bestellung drucken", false, () -> {
            String t = free.getText().toString();
            if (t.trim().isEmpty()) { toast("Kein Text"); return; }
            save();
            Printer.printManual(this, t);
        });
        LinearLayout row = row(c);
        button(row, "Als Text drucken", false, () -> {
            String t = free.getText().toString();
            if (t.trim().isEmpty()) { toast("Kein Text"); return; }
            save();
            Printer.printText(this, t);
        });
        button(row, "Löschen", false, () -> free.setText(""));
        return p;
    }

    private LinearLayout buildLabels() {
        LinearLayout p = column();
        LinearLayout c = card(p, "Etikett drucken");
        search = input(c, "Suchen", "", InputType.TYPE_CLASS_TEXT, false);
        search.setHint("z.B. mexi, cola, dürüm");
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int d, int e) { }
            public void onTextChanged(CharSequence a, int b, int d, int e) { }
            public void afterTextChanged(android.text.Editable e) { fillLabels(); }
        });
        label(c, "Produkt");
        labelPick = new Spinner(this);
        labelPick.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) { loadSelected(); }
            public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });
        c.addView(labelPick);

        lName = input(c, "Name", "", InputType.TYPE_CLASS_TEXT, false);
        LinearLayout pr = row(c);
        lPrice = input(weighted(pr, 1), "Preis", "", InputType.TYPE_CLASS_TEXT, false);
        copies = input(weighted(pr, 1), "Anzahl", "1", InputType.TYPE_CLASS_NUMBER, false);
        lDesc = input(c, "Beschreibung", "", InputType.TYPE_CLASS_TEXT, true);
        lDesc.setMinLines(2);
        hint(c, "Getränke mit Literangabe (z.B. 0,33l) bekommen automatisch \"+ Pfand\" aufs Etikett.");

        button(c, "Etikett drucken", true, () -> {
            String n = lName.getText().toString().trim();
            if (n.isEmpty()) { toast("Kein Name"); return; }
            int k;
            try { k = Integer.parseInt(copies.getText().toString().trim()); } catch (Exception ex) { k = 1; }
            save();
            Printer.printLabel(this, n, lPrice.getText().toString().trim(),
                    lDesc.getText().toString().trim().replace("\n", " "), Math.min(k, 50));
        });
        LinearLayout ed = row(c);
        button(ed, "Änderung speichern", false, () -> editLabel(false));
        button(ed, "Als neu hinzufügen", false, () -> editLabel(true));
        button(c, "Ausgewähltes Etikett löschen", false, this::deleteLabel);

        LinearLayout ls = card(p, "Etikett Layout");
        labelStyle = new RadioGroup(this);
        String[] st = {"Kompakt: Name links, Preis rechts invertiert", "Groß: alles zentriert"};
        for (int i = 0; i < 2; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + i + 1);
            rb.setText(st[i]);
            labelStyle.addView(rb);
        }
        labelStyle.check(100 + Prefs.labelStyle(this));
        ls.addView(labelStyle);
        LinearLayout hr = row(ls);
        lHeight = input(weighted(hr, 1), "Etikett Höhe (mm)", Prefs.get(this).getString(Prefs.LABEL_H, "40"),
                InputType.TYPE_CLASS_NUMBER, false);
        lTop = input(weighted(hr, 1), "Leerraum oben (mm)", Prefs.get(this).getString(Prefs.LABEL_TOP, "12"),
                InputType.TYPE_CLASS_NUMBER, false);
        hint(ls, "Gilt für Kompakt. Ein Etikett drucken und nachmessen: ist es länger als eingestellt, "
                + "Leerraum oben um die Differenz erhöhen, ist es kürzer, verringern.");

        LinearLayout m = card(p, "Etiketten verwalten");
        hint(m, "Eine Zeile je Etikett: Name | Preis | Beschreibung");
        labels = input(m, null, Prefs.labels(this), InputType.TYPE_CLASS_TEXT, true);
        labels.setMaxLines(12);
        labels.setVerticalScrollBarEnabled(true);
        button(m, "Speichern", true, () -> { save(); fillLabels(); toast("Gespeichert"); });
        return p;
    }

    private LinearLayout buildSettings() {
        SharedPreferences sp = Prefs.get(this);
        LinearLayout p = column();

        LinearLayout perm = card(p, "Berechtigungen");
        hint(perm, "Einmalig nötig, damit WhatsApp mitgelesen wird und die App im Hintergrund läuft.");
        button(perm, "Kontakte erlauben (Telefonnummer gespeicherter Kunden)", false, () ->
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 2));
        button(perm, "Benachrichtigungszugriff erlauben", false, () ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(perm, "Akku Optimierung ausschalten", false, () ->
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        LinearLayout open = card(p, "Auch bei geöffnetem Chat");
        hint(open, "Bei offenem Chat schickt WhatsApp keine Benachrichtigung. Mit der Bedienungshilfe liest Bondruck neue eingehende Nachrichten im offenen Chat. "
                + "Unter Bedienungshilfe > Installierte Apps > Bondruck WhatsApp Chat einschalten.");
        button(open, "Bedienungshilfe öffnen", false, () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        hint(open, "Schnell per Hand: Wischleiste oben bearbeiten (Stift), Kachel Bondruck hinzufügen. Nachrichten kopieren, Kachel tippen, fertig.");

        LinearLayout pr = card(p, "Drucker");
        LinearLayout r = row(pr);
        ip = input(weighted(r, 2), "IP Adresse", sp.getString(Prefs.IP, Prefs.DEF_IP), InputType.TYPE_CLASS_TEXT, false);
        port = input(weighted(r, 1), "Port", sp.getString(Prefs.PORT, Prefs.DEF_PORT), InputType.TYPE_CLASS_NUMBER, false);
        button(pr, "Testdruck", true, () -> { save(); Printer.printTest(this); });
        label(pr, "Zeichensatz (für ä ö ü ß €)");
        enc = new RadioGroup(this);
        String[][] encs = {{"utf8", "UTF8 (Sunmi Standard)"}, {"cp1252", "CP1252"}, {"cp858", "CP858"}, {"cp437", "CP437"}};
        for (int i = 0; i < encs.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(200 + i);
            rb.setText(encs[i][1]);
            rb.setTag(encs[i][0]);
            enc.addView(rb);
            if (encs[i][0].equals(Prefs.encoding(this))) rb.setChecked(true);
        }
        pr.addView(enc);
        button(pr, "Zeichensatz testen", false, () -> Printer.printCharsetTest(this));
        hint(pr, "Druckt eine Zeile pro Zeichensatz. Den auswählen, bei dem \"Straße äöü €\" richtig gedruckt wird.");

        LinearLayout bon = card(p, "Bon");
        label(bon, "Schriftgröße");
        size = new RadioGroup(this);
        String[] names = {"Normal", "Groß (doppelt hoch)", "Sehr groß (doppelt hoch und breit)"};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i + 1);
            rb.setText(names[i]);
            size.addView(rb);
        }
        size.check(Prefs.size(this));
        bon.addView(size);
        prices = sw(bon, "Preise und Summe auf Bon (aus Etikettenliste)", Prefs.prices(this));
        pfand = input(bon, "Pfand je Getränk (auch auf Etiketten, 0 = aus)", sp.getString(Prefs.PFAND, "0,25"),
                InputType.TYPE_CLASS_TEXT, false);

        LinearLayout a = card(p, "Automatisch drucken");
        auto = new Switch(this);
        auto.setText("Bon automatisch drucken, wenn Artikel und Adresse erkannt");
        auto.setChecked(Prefs.auto(this));
        a.addView(auto);
        wait = input(a, "Wartezeit ohne neue Nachricht (Sekunden)",
                sp.getString(Prefs.WAIT, Prefs.DEF_WAIT), InputType.TYPE_CLASS_NUMBER, false);
        window = input(a, "Zeitfenster: alles innerhalb von … Minuten ist eine Bestellung",
                sp.getString(Prefs.WINDOW, "30"), InputType.TYPE_CLASS_NUMBER, false);
        hint(a, "Kommen nach dem Druck noch Artikel dazu, wird ein aktualisierter Bon mit \"NEU DAZU\" gedruckt.");

        LinearLayout fd = card(p, "Logo und Firmendaten");
        logoBon = sw(fd, "Logo auf Bon", Prefs.logoBon(this));
        headerBon = sw(fd, "Firmendaten auf Bon", Prefs.headerBon(this));
        logoLabel = sw(fd, "Logo auf Etiketten", Prefs.logoLabel(this));
        headerLabel = sw(fd, "Firmendaten auf Etiketten", Prefs.headerLabel(this));
        label(fd, "Firmendaten (erste zwei Zeilen werden fett gedruckt)");
        header = input(fd, null, Prefs.header(this), InputType.TYPE_CLASS_TEXT, true);
        button(fd, "Firmendaten zurücksetzen", false, () -> header.setText(Prefs.defHeader(this)));

        LinearLayout pn = card(p, "Produktnamen");
        hint(pn, "Die Speisekarte mit über 400 Schreibweisen (auch Türkisch) ist eingebaut. Hier nur eigene Ergänzungen eintragen, "
                + "eine Zeile je Eintrag: Kundentext = Text am Bon. Beispiel: big kebap = Rind-Sandwich");
        repl = input(pn, null, Prefs.replOwn(this), InputType.TYPE_CLASS_TEXT, true);
        repl.setMaxLines(12);
        repl.setVerticalScrollBarEnabled(true);
        button(pn, "Etiketten und Produktnamen zurücksetzen", false, () -> {
            repl.setText("");
            labels.setText(Prefs.defLabels(this));
            save();
            fillLabels();
            toast("Produktnamen und Etiketten zurückgesetzt");
        });

        LinearLayout stc = card(p, "Straßen");
        hint(stc, Streets.size() + " Straßen aus Simmering, Schwechat und Mannswörth sind bekannt und werden bei Tippfehlern korrigiert. "
                + "Neue Straßen merkt sich die App automatisch. Hier eigene ergänzen, eine je Zeile: Name;PLZ Ort  z.B. Frauenfeldgasse;2320 Schwechat");
        streets = input(stc, null, sp.getString(Prefs.STREETS, ""), InputType.TYPE_CLASS_TEXT, true);

        LinearLayout ig = card(p, "Ausgenommene Chats");
        hint(ig, "Name oder Nummer wie in WhatsApp, eine je Zeile. Diese Chats werden nie gedruckt.");
        ignore = input(ig, null, sp.getString(Prefs.IGNORE, ""), InputType.TYPE_CLASS_TEXT, true);

        button(p, "Einstellungen speichern", true, () -> { save(); toast("Gespeichert"); });
        return p;
    }

    // ---------- Archiv ----------

    private LinearLayout archiveList;
    private TextView archiveSum;
    private EditText archiveSearch;
    private int archiveRange = 0;   // 0 heute, 1 gestern, 2 alle

    private LinearLayout buildArchive() {
        LinearLayout p = column();
        LinearLayout c = card(p, "Übersicht");
        archiveSum = new TextView(this);
        archiveSum.setTextSize(17);
        archiveSum.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(archiveSum);
        LinearLayout r = row(c);
        button(r, "Heute", false, () -> { archiveRange = 0; refreshArchive(); });
        button(r, "Gestern", false, () -> { archiveRange = 1; refreshArchive(); });
        button(r, "Alle", false, () -> { archiveRange = 2; refreshArchive(); });
        archiveSearch = input(c, "Suchen (Kunde, Telefon, Adresse, Artikel)", "", InputType.TYPE_CLASS_TEXT, false);
        archiveSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int d, int e) { }
            public void onTextChanged(CharSequence a, int b, int d, int e) { }
            public void afterTextChanged(android.text.Editable e) { refreshArchive(); }
        });
        LinearLayout l = card(p, "Bestellungen");
        archiveList = new LinearLayout(this);
        archiveList.setOrientation(LinearLayout.VERTICAL);
        l.addView(archiveList);
        return p;
    }

    private void refreshArchive() {
        if (archiveList == null) return;
        try {
            archiveList.removeAllViews();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0);
            long today = cal.getTimeInMillis(), yesterday = today - 24L * 3600 * 1000;
            String q = archiveSearch.getText().toString().trim().toLowerCase(Locale.GERMAN);
            int nToday = 0, sToday = 0, nY = 0, sY = 0, shown = 0, nSel = 0, sSel = 0;
            SimpleDateFormat df = new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN);
            for (Archive.Entry e : Archive.all(this)) {
                if (e.first >= today) { nToday++; sToday += e.sum; }
                else if (e.first >= yesterday) { nY++; sY += e.sum; }
                boolean inRange = archiveRange == 2 || (archiveRange == 0 ? e.first >= today : e.first >= yesterday && e.first < today);
                if (!inRange) continue;
                if (!q.isEmpty() && !(e.chat + " " + e.address + " " + e.items + " " + String.join(" ", e.msgs))
                        .toLowerCase(Locale.GERMAN).contains(q)) continue;
                nSel++; sSel += e.sum;
                if (shown++ >= 150) continue;
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(0, dp(8), 0, dp(10));
                TextView t = new TextView(this);
                t.setText(df.format(new Date(e.first)) + "   " + e.chat + (e.type.isEmpty() ? "" : "   " + e.type));
                t.setTextSize(16);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                box.addView(t);
                TextView d = new TextView(this);
                d.setText(e.items + (e.address.isEmpty() ? "" : "\nAdresse: " + e.address)
                        + "\nSumme: € " + Prices.fmt(e.sum) + (e.printedAt > 0 ? "" : "   (nicht gedruckt)"));
                d.setTextSize(14);
                box.addView(d);
                final Archive.Entry fe = e;
                LinearLayout br = row(box);
                button(br, "Nachdrucken", false, () -> Printer.printArchive(this, fe));
                archiveList.addView(box);
            }
            archiveSum.setText("Heute: " + nToday + " Bestellungen · € " + Prices.fmt(sToday)
                    + "\nGestern: " + nY + " Bestellungen · € " + Prices.fmt(sY)
                    + "\nAuswahl (" + (archiveRange == 0 ? "heute" : archiveRange == 1 ? "gestern" : "alle")
                    + "): " + nSel + " · € " + Prices.fmt(sSel));
            if (nSel == 0) hint(archiveList, "Keine Bestellungen.");
            else if (shown > 150) hint(archiveList, "… nur die neuesten 150 angezeigt, Suche verwenden.");
        } catch (Exception ex) {
            toast("Archiv Fehler: " + ex.getMessage());
        }
    }

    // ---------- Bestellungen anzeigen ----------

    private void refreshOrders() {
        try { refreshOrdersNow(); }
        catch (Exception e) { toast("Fehler in Liste: " + e.getMessage()); }
    }

    private void refreshOrdersNow() {
        if (orderList == null) return;
        logList.removeAllViews();
        List<String> lines = OrderStore.getLog();
        if (lines.isEmpty()) hint(logList, "Noch keine Ereignisse. Kommt hier bei einer neuen WhatsApp Nachricht nichts, fehlt der Benachrichtigungszugriff.");
        for (String l : lines) {
            TextView t = new TextView(this);
            t.setText(l);
            t.setTextSize(13);
            t.setTypeface(Typeface.MONOSPACE);
            logList.addView(t);
        }
        orderList.removeAllViews();
        List<OrderStore.Order> list = OrderStore.list(this);
        if (list.isEmpty()) {
            hint(orderList, "Keine offenen Bestellungen.");
            return;
        }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMAN);
        for (OrderStore.Order o : list) {
            Parser.Result r = Parser.parse(o.msgs, Parser.replacements(Prefs.repl(this)));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(0, dp(8), 0, dp(12));

            TextView t = new TextView(this);
            t.setText(o.chat + "   " + hm.format(new Date(o.last)) + "   "
                    + (r.type != null ? r.type : "Lieferung oder Abholung?"));
            t.setTextSize(17);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(t);

            Parser.merge(r);
            java.util.Map<String, Integer> pm = Prices.load(this);
            StringBuilder sb = new StringBuilder();
            for (Parser.Item it : r.items) {
                Integer pr = Prices.of(pm, it.name);
                sb.append(it.qty).append("x ").append(it.name);
                if (pr != null) sb.append("   ").append(Prices.fmt(pr * Prices.qty(it)));
                sb.append('\n');
                for (String ex : it.extras) sb.append("   + ").append(ex).append('\n');
            }
            if (r.items.isEmpty()) sb.append("(keine Artikel erkannt)\n");
            else sb.append("Summe: € ").append(Prices.fmt(Prices.total(pm, r.items, Prefs.pfandCents(this))[0])).append('\n');
            List<String> showMsgs = new ArrayList<>(o.msgs);
            boolean old = Printer.enrich(this, o.chat, showMsgs, r);
            String tel = Phone.ifNumber(o.chat);
            if (tel == null && !r.knownPhones.isEmpty()) tel = r.knownPhones.get(0);
            if (tel == null) for (String m : o.msgs) { List<String> f = Phone.findAll(m); if (!f.isEmpty()) { tel = f.get(0); break; } }
            sb.append("Telefon: ").append(tel != null ? tel : "-").append('\n');
            sb.append(r.address != null ? "Adresse: " + r.address + (old ? " (frühere Adresse)" : "") : "Adresse: -");
            sb.append(o.printedAt > 0 ? "\nGedruckt " + hm.format(new Date(o.printedAt)) : "\nNoch nicht gedruckt");
            TextView d = new TextView(this);
            d.setText(sb.toString());
            d.setTextSize(15);
            box.addView(d);

            LinearLayout row = row(box);
            final String chat = o.chat;
            button(row, o.printedAt > 0 ? "Nachdrucken" : "Drucken", true,
                    () -> Printer.printOrder(this, chat, true));
            button(row, "Erledigt", false, () -> {
                WhatsAppListener.cancelTimer(chat);
                OrderStore.remove(this, chat);
                Notifier.cancel(this, chat);
                refreshOrders();
            });
            orderList.addView(box);
        }
    }

    // ---------- Reiter ----------

    private void showTab(int i) {
        if (current == T_SETTINGS && i != T_SETTINGS) save();
        current = i;
        for (int k = 0; k < pages.size(); k++) {
            pages.get(k).setVisibility(k == i ? View.VISIBLE : View.GONE);
            TextView t = tabViews.get(k);
            t.setTextColor(k == i ? ORANGE : Color.DKGRAY);
            t.setTypeface(k == i ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setBackground(k == i ? underline(ORANGE) : null);
        }
        if (i == T_ORDERS) refreshOrders();
        if (i == T_ARCHIVE) refreshArchive();
    }

    private android.graphics.drawable.Drawable underline(int color) {
        GradientDrawable line = new GradientDrawable();
        line.setColor(color);
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        android.graphics.drawable.LayerDrawable ld =
                new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{line, white});
        ld.setLayerInset(1, 0, 0, 0, dp(3));
        return ld;
    }

    private View page(LinearLayout content, LinearLayout inner) {
        ScrollView sv = new ScrollView(this);
        sv.addView(inner);
        content.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return sv;
    }

    // ---------- Titelleiste ----------

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        android.view.MenuItem it = menu.add(0, 1, 0, "Aktualisieren");
        it.setIcon(android.R.drawable.ic_popup_sync);
        it.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS | android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            if (current != T_ARCHIVE) showTab(T_ORDERS);
            updateStatus();
            refreshOrders();
            refreshArchive();
            toast("Aktualisiert");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------- Lebenszyklus ----------

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEND.equals(intent.getAction())) showTab(T_TEXT);
        takeShared(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        if (current == T_ORDERS) refreshOrders();
        if (current == T_ARCHIVE) refreshArchive();
    }

    private void updateStatus() {
        String list = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean ok = list != null && list.contains(getPackageName());
        if (ok) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                        new android.content.ComponentName(this, WhatsAppListener.class));
            } catch (Exception ignored) { }
        }
        String a11y = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean openOk = a11y != null && a11y.contains(getPackageName());
        status.setText((ok ? "Aktiv: WhatsApp Benachrichtigungen werden mitgelesen"
                          : "Benachrichtigungszugriff fehlt (Einstellungen > Berechtigungen)")
                + (openOk ? "\nAktiv: geöffneter Chat wird mitgelesen" : "\nGeöffneter Chat: aus (Einstellungen)"));
        status.setTextColor(ok ? Color.rgb(0, 130, 0) : Color.rgb(200, 0, 0));
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    // ---------- Foto lesen (Texterkennung) ----------

    private void readLatestPhoto() {
        String perm = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, 3);
            return;
        }
        android.net.Uri u = latestImage(true);
        if (u == null) u = latestImage(false);
        if (u == null) { toast("Kein Foto gefunden. Foto in WhatsApp einmal öffnen, damit es geladen wird."); return; }
        ocr(u);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 3) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) readLatestPhoto();
            else toast("Ohne Zugriff auf Fotos geht es nur über \"Foto auswählen\"");
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == 4 && result == RESULT_OK && data != null && data.getData() != null) ocr(data.getData());
    }

    /** Neuestes Bild: zuerst empfangene WhatsApp Fotos, sonst irgendein Foto. */
    private android.net.Uri latestImage(boolean whatsappOnly) {
        android.net.Uri coll = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String sel = null;
        String[] args = null;
        if (whatsappOnly) {
            String col = Build.VERSION.SDK_INT >= 29 ? android.provider.MediaStore.MediaColumns.RELATIVE_PATH
                    : android.provider.MediaStore.MediaColumns.DATA;
            sel = col + " LIKE ? AND " + col + " NOT LIKE ?";
            args = new String[]{"%WhatsApp%Images%", "%Sent%"};
        }
        try (android.database.Cursor cur = getContentResolver().query(coll,
                new String[]{android.provider.MediaStore.Images.Media._ID}, sel, args,
                android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cur != null && cur.moveToFirst()) return android.content.ContentUris.withAppendedId(coll, cur.getLong(0));
        } catch (Exception e) {
            toast("Fotos nicht lesbar: " + e.getMessage());
        }
        return null;
    }

    /** Text aus dem Foto lesen und ins Textfeld schreiben. */
    private void ocr(android.net.Uri u) {
        toast("Lese Text aus Foto…");
        try {
            com.google.mlkit.vision.common.InputImage img = com.google.mlkit.vision.common.InputImage.fromFilePath(this, u);
            com.google.mlkit.vision.text.TextRecognition
                    .getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(img)
                    .addOnSuccessListener(t -> {
                        String txt = t.getText();
                        if (txt == null || txt.trim().isEmpty()) { toast("Kein Text im Foto erkannt"); return; }
                        showTab(T_TEXT);
                        free.setText(txt.trim());
                        free.setSelection(free.getText().length());
                        OrderStore.log("Foto gelesen: " + (txt.length() > 40 ? txt.substring(0, 40).replace('\n', ' ') + "…" : txt));
                        toast("Text erkannt. Prüfen und \"Als Bestellung drucken\" tippen.");
                    })
                    .addOnFailureListener(e -> toast("Texterkennung fehlgeschlagen: " + e.getMessage()));
        } catch (Exception e) {
            toast("Foto nicht lesbar: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void takeShared(Intent i) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction()) && i.getType() != null && i.getType().startsWith("image/")) {
            android.net.Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) ocr(u);
            return;
        }
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (t == null) return;
        String cur = free.getText().toString();
        free.setText(cur.isEmpty() ? t.toString() : cur + "\n" + t);
        free.setSelection(free.getText().length());
    }

    private void save() {
        saveNow();
        Prefs.reloadStreets(this);
    }

    private void saveNow() {
        int s = size.getCheckedRadioButtonId();
        Prefs.get(this).edit()
                .putString(Prefs.IP, ip.getText().toString().trim())
                .putString(Prefs.PORT, port.getText().toString().trim())
                .putInt(Prefs.SIZE, s >= 1 && s <= 3 ? s : 2)
                .putBoolean(Prefs.AUTO, auto.isChecked())
                .putString(Prefs.WAIT, wait.getText().toString().trim())
                .putString(Prefs.WINDOW, window.getText().toString().trim())
                .putString(Prefs.ENC, enc.getCheckedRadioButtonId() >= 200
                        ? (String) enc.findViewById(enc.getCheckedRadioButtonId()).getTag() : "utf8")
                .putString(Prefs.REPL, repl.getText().toString())
                .putString(Prefs.IGNORE, ignore.getText().toString())
                .putString(Prefs.LABELS, labels.getText().toString())
                .putString(Prefs.HEADER, header.getText().toString())
                .putBoolean(Prefs.PRICES, prices.isChecked())
                .putString(Prefs.PFAND, pfand.getText().toString().trim())
                .putString(Prefs.LABEL_H, lHeight.getText().toString().trim())
                .putString(Prefs.LABEL_TOP, lTop.getText().toString().trim())
                .putString(Prefs.STREETS, streets.getText().toString())
                .putInt(Prefs.LABEL_STYLE, labelStyle.getCheckedRadioButtonId() == 102 ? 2 : 1)
                .putBoolean(Prefs.LOGO_BON, logoBon.isChecked())
                .putBoolean(Prefs.HEADER_BON, headerBon.isChecked())
                .putBoolean(Prefs.LOGO_LABEL, logoLabel.isChecked())
                .putBoolean(Prefs.HEADER_LABEL, headerLabel.isChecked())
                .apply();
    }

    // ---------- Etiketten ----------

    private List<String[]> labelList() {
        List<String[]> out = new ArrayList<>();
        for (String line : labels.getText().toString().split("\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 2 || p[0].trim().isEmpty()) continue;
            out.add(new String[]{p[0].trim(), p[1].trim(), p.length > 2 ? p[2].trim() : ""});
        }
        return out;
    }

    private void fillLabels() {
        List<String> names = new ArrayList<>();
        shown.clear();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.GERMAN);
        List<String[]> all = labelList();
        for (int i = 0; i < all.size(); i++) {
            String[] e = all.get(i);
            if (!q.isEmpty() && !(e[0] + " " + e[2]).toLowerCase(Locale.GERMAN).contains(q)) continue;
            names.add(e[0] + "   € " + e[1]);
            shown.add(i);
        }
        labelPick.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        if (names.isEmpty() && lName != null) { lName.setText(""); lPrice.setText(""); lDesc.setText(""); }
    }

    /** Index des ausgewählten Etiketts in der vollen Liste, -1 wenn keins. */
    private int selectedIndex() {
        int i = labelPick.getSelectedItemPosition();
        return i >= 0 && i < shown.size() ? shown.get(i) : -1;
    }

    private void loadSelected() {
        int i = selectedIndex();
        List<String[]> l = labelList();
        if (i < 0 || i >= l.size()) return;
        lName.setText(l.get(i)[0]);
        lPrice.setText(l.get(i)[1]);
        lDesc.setText(l.get(i)[2]);
    }

    private void editLabel(boolean asNew) {
        String n = lName.getText().toString().trim().replace("|", "/");
        if (n.isEmpty()) { toast("Kein Name"); return; }
        String line = n + " | " + lPrice.getText().toString().trim().replace("|", "/")
                + " | " + lDesc.getText().toString().trim().replace("|", "/").replace("\n", " ");
        List<String> lines = new ArrayList<>();
        for (String l : labels.getText().toString().split("\n")) if (!l.trim().isEmpty()) lines.add(l);
        int idx = selectedIndex();
        int target;
        if (asNew || idx < 0) { lines.add(0, line); target = 0; }
        else { lines.set(lineOf(lines, idx), line); target = idx; }
        labels.setText(String.join("\n", lines));
        save();
        if (search != null) search.setText("");
        fillLabels();
        int pos = shown.indexOf(target);
        if (pos >= 0) labelPick.setSelection(pos);
        toast(asNew ? "Etikett hinzugefügt" : "Etikett gespeichert");
    }

    private void deleteLabel() {
        int idx = selectedIndex();
        if (idx < 0) { toast("Kein Etikett ausgewählt"); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Etikett löschen?")
                .setMessage(lName.getText().toString())
                .setPositiveButton("Löschen", (d, w) -> {
                    List<String> lines = new ArrayList<>();
                    for (String l : labels.getText().toString().split("\n")) if (!l.trim().isEmpty()) lines.add(l);
                    lines.remove(lineOf(lines, idx));
                    labels.setText(String.join("\n", lines));
                    save();
                    fillLabels();
                    toast("Gelöscht");
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    /** Zeilennummer des n-ten gültigen Etiketts. */
    private int lineOf(List<String> lines, int n) {
        int k = -1;
        for (int i = 0; i < lines.size(); i++) {
            String[] p = lines.get(i).split("\\|", -1);
            if (p.length >= 2 && !p[0].trim().isEmpty()) k++;
            if (k == n) return i;
        }
        return lines.size() - 1;
    }

    // ---------- Bausteine ----------

    private LinearLayout column() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(8), dp(16), dp(24));
        return p;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        TextView h = new TextView(this);
        h.setText(title.toUpperCase(Locale.GERMAN));
        h.setTextSize(13);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(ORANGE);
        h.setLetterSpacing(0.05f);
        h.setPadding(dp(4), dp(16), 0, dp(6));
        parent.addView(h);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        c.setBackground(bg);
        c.setElevation(dp(1));
        parent.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private LinearLayout weighted(LinearLayout row, float w) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, 0, dp(8), 0);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w));
        return l;
    }

    private Switch sw(LinearLayout parent, String text, boolean on) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setChecked(on);
        s.setTextSize(16);
        s.setPadding(0, dp(6), 0, dp(6));
        parent.addView(s);
        return s;
    }

    private void label(LinearLayout parent, String text) {
        TextView l = new TextView(this);
        l.setText(text);
        l.setTextSize(13);
        l.setTextColor(Color.GRAY);
        l.setPadding(0, dp(8), 0, 0);
        parent.addView(l);
    }

    private void hint(LinearLayout parent, String text) {
        TextView l = new TextView(this);
        l.setText(text);
        l.setTextSize(14);
        l.setTextColor(Color.GRAY);
        l.setPadding(0, 0, 0, dp(6));
        parent.addView(l);
    }

    private EditText input(LinearLayout parent, String label, String value, int type, boolean multi) {
        if (label != null) label(parent, label);
        EditText e = new EditText(this);
        e.setText(value);
        if (multi) {
            e.setInputType(type | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(4);
            e.setSingleLine(false);
            e.setGravity(Gravity.TOP);
        } else {
            e.setInputType(type);
        }
        parent.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void button(LinearLayout parent, String text, boolean primary, Runnable r) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (primary) {
            bg.setColor(ORANGE);
            b.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), ORANGE);
            b.setTextColor(ORANGE);
        }
        b.setBackground(bg);
        b.setOnClickListener(v -> r.run());
        boolean horizontal = parent.getOrientation() == LinearLayout.HORIZONTAL;
        LinearLayout.LayoutParams lp = horizontal
                ? new LinearLayout.LayoutParams(0, dp(48), 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        if (horizontal && parent.getChildCount() > 0) lp.leftMargin = dp(8);
        parent.addView(b, lp);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
