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
    private static final String[] TABS = {"Bestellungen", "Text", "Etiketten", "Einstellungen"};

    private EditText ip, port, wait, repl, ignore, free, labels, copies, header;
    private Switch logoBon, logoLabel, headerBon, headerLabel;
    private Switch auto;
    private RadioGroup size, labelStyle;
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
        pages.add(page(content, buildText()));
        pages.add(page(content, buildLabels()));
        pages.add(page(content, buildSettings()));

        setContentView(root);
        fillLabels();

        boolean shared = Intent.ACTION_SEND.equals(getIntent().getAction());
        showTab(shared ? 1 : 0);
        takeShared(getIntent());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
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
        button(c, "Als Bestellung drucken", true, () -> {
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
        label(c, "Produkt");
        labelPick = new Spinner(this);
        c.addView(labelPick);
        copies = input(c, "Anzahl", "1", InputType.TYPE_CLASS_NUMBER, false);
        button(c, "Etikett drucken", true, () -> {
            String[] e = selectedLabel();
            if (e == null) { toast("Kein Etikett angelegt"); return; }
            int n;
            try { n = Integer.parseInt(copies.getText().toString().trim()); } catch (Exception ex) { n = 1; }
            Printer.printLabel(this, e[0], e[1], e[2], Math.min(n, 50));
        });

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
        button(perm, "Benachrichtigungszugriff erlauben", false, () ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(perm, "Akku Optimierung ausschalten", false, () ->
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        LinearLayout pr = card(p, "Drucker");
        LinearLayout r = row(pr);
        ip = input(weighted(r, 2), "IP Adresse", sp.getString(Prefs.IP, Prefs.DEF_IP), InputType.TYPE_CLASS_TEXT, false);
        port = input(weighted(r, 1), "Port", sp.getString(Prefs.PORT, Prefs.DEF_PORT), InputType.TYPE_CLASS_NUMBER, false);
        button(pr, "Testdruck", true, () -> { save(); Printer.printTest(this); });

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

        LinearLayout a = card(p, "Automatisch drucken");
        auto = new Switch(this);
        auto.setText("Bon automatisch drucken, wenn Artikel und Adresse erkannt");
        auto.setChecked(Prefs.auto(this));
        a.addView(auto);
        wait = input(a, "Wartezeit ohne neue Nachricht (Sekunden)",
                sp.getString(Prefs.WAIT, Prefs.DEF_WAIT), InputType.TYPE_CLASS_NUMBER, false);

        LinearLayout fd = card(p, "Logo und Firmendaten");
        logoBon = sw(fd, "Logo auf Bon", Prefs.logoBon(this));
        headerBon = sw(fd, "Firmendaten auf Bon", Prefs.headerBon(this));
        logoLabel = sw(fd, "Logo auf Etiketten", Prefs.logoLabel(this));
        headerLabel = sw(fd, "Firmendaten auf Etiketten", Prefs.headerLabel(this));
        label(fd, "Firmendaten (erste zwei Zeilen werden fett gedruckt)");
        header = input(fd, null, Prefs.header(this), InputType.TYPE_CLASS_TEXT, true);
        button(fd, "Firmendaten zurücksetzen", false, () -> header.setText(Prefs.defHeader(this)));

        LinearLayout pn = card(p, "Produktnamen");
        hint(pn, "Kundentext = Text am Bon, eine Zeile je Eintrag. Beispiel: kebap = Hühner Sandwich");
        repl = input(pn, null, Prefs.repl(this), InputType.TYPE_CLASS_TEXT, true);
        repl.setMaxLines(12);
        repl.setVerticalScrollBarEnabled(true);
        button(pn, "Speisekarte von Webseite wiederherstellen", false, () -> {
            repl.setText(Prefs.defRepl(this));
            labels.setText(Prefs.defLabels(this));
            save();
            fillLabels();
            toast("Produktnamen und Etiketten zurückgesetzt");
        });

        LinearLayout ig = card(p, "Ausgenommene Chats");
        hint(ig, "Name oder Nummer wie in WhatsApp, eine je Zeile. Diese Chats werden nie gedruckt.");
        ignore = input(ig, null, sp.getString(Prefs.IGNORE, ""), InputType.TYPE_CLASS_TEXT, true);

        button(p, "Einstellungen speichern", true, () -> { save(); toast("Gespeichert"); });
        return p;
    }

    // ---------- Bestellungen anzeigen ----------

    private void refreshOrders() {
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
            t.setText(o.chat + "   " + hm.format(new Date(o.last)));
            t.setTextSize(17);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(t);

            StringBuilder sb = new StringBuilder();
            for (Parser.Item it : r.items) sb.append(it.qty).append("x ").append(it.name).append('\n');
            if (r.items.isEmpty()) sb.append("(keine Artikel erkannt)\n");
            sb.append(r.address != null ? "Adresse: " + r.address : "Adresse: -");
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
        if (current == 3 && i != 3) save();
        current = i;
        for (int k = 0; k < pages.size(); k++) {
            pages.get(k).setVisibility(k == i ? View.VISIBLE : View.GONE);
            TextView t = tabViews.get(k);
            t.setTextColor(k == i ? ORANGE : Color.DKGRAY);
            t.setTypeface(k == i ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setBackground(k == i ? underline(ORANGE) : null);
        }
        if (i == 0) refreshOrders();
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

    // ---------- Lebenszyklus ----------

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEND.equals(intent.getAction())) showTab(1);
        takeShared(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String list = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean ok = list != null && list.contains(getPackageName());
        if (ok) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                        new android.content.ComponentName(this, WhatsAppListener.class));
            } catch (Exception ignored) { }
        }
        status.setText(ok ? "Aktiv: WhatsApp wird mitgelesen"
                          : "Benachrichtigungszugriff fehlt\nEinstellungen > Berechtigungen");
        status.setTextColor(ok ? Color.rgb(0, 130, 0) : Color.rgb(200, 0, 0));
        if (current == 0) refreshOrders();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void takeShared(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (t == null) return;
        String cur = free.getText().toString();
        free.setText(cur.isEmpty() ? t.toString() : cur + "\n" + t);
        free.setSelection(free.getText().length());
    }

    private void save() {
        int s = size.getCheckedRadioButtonId();
        Prefs.get(this).edit()
                .putString(Prefs.IP, ip.getText().toString().trim())
                .putString(Prefs.PORT, port.getText().toString().trim())
                .putInt(Prefs.SIZE, s >= 1 && s <= 3 ? s : 2)
                .putBoolean(Prefs.AUTO, auto.isChecked())
                .putString(Prefs.WAIT, wait.getText().toString().trim())
                .putString(Prefs.REPL, repl.getText().toString())
                .putString(Prefs.IGNORE, ignore.getText().toString())
                .putString(Prefs.LABELS, labels.getText().toString())
                .putString(Prefs.HEADER, header.getText().toString())
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
        for (String[] e : labelList()) names.add(e[0] + "   € " + e[1]);
        labelPick.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
    }

    private String[] selectedLabel() {
        List<String[]> l = labelList();
        int i = labelPick.getSelectedItemPosition();
        return i >= 0 && i < l.size() ? l.get(i) : null;
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
