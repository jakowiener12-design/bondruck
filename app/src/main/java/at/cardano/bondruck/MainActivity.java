package at.cardano.bondruck;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText ip, port, wait, repl, ignore, free, labels, copies;
    private android.widget.Spinner labelPick;
    private Switch auto;
    private RadioGroup size;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences p = Prefs.get(this);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        ll.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Bondruck WhatsApp");
        title.setTextSize(24);
        ll.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, dp(12), 0, dp(4));
        ll.addView(status);

        free = field(ll, "Freier Text zum Drucken", "", InputType.TYPE_CLASS_TEXT, true);
        free.setMinLines(5);
        free.setTextSize(18);
        button(ll, "Text drucken", () -> {
            String t = free.getText().toString();
            if (t.trim().isEmpty()) {
                Toast.makeText(this, "Kein Text", Toast.LENGTH_SHORT).show();
                return;
            }
            save();
            Printer.printText(this, t);
        });
        button(ll, "Text löschen", () -> free.setText(""));

        // Etiketten
        label(ll, "Etikett auswählen");
        labelPick = new android.widget.Spinner(this);
        ll.addView(labelPick);
        copies = field(ll, "Anzahl", "1", InputType.TYPE_CLASS_NUMBER, false);
        button(ll, "Etikett drucken", () -> {
            String[] e = selectedLabel();
            if (e == null) { Toast.makeText(this, "Kein Etikett", Toast.LENGTH_SHORT).show(); return; }
            int n;
            try { n = Integer.parseInt(copies.getText().toString().trim()); } catch (Exception ex) { n = 1; }
            Printer.printLabel(this, e[0], e[1], e[2], Math.min(n, 50));
        });

        button(ll, "1. Benachrichtigungszugriff erlauben", () ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(ll, "2. Akku Optimierung ausschalten", () ->
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        ip = field(ll, "Drucker IP", p.getString(Prefs.IP, Prefs.DEF_IP), InputType.TYPE_CLASS_TEXT, false);
        port = field(ll, "Port", p.getString(Prefs.PORT, Prefs.DEF_PORT), InputType.TYPE_CLASS_NUMBER, false);

        label(ll, "Schriftgröße am Bon");
        size = new RadioGroup(this);
        String[] names = {"Normal", "Groß (doppelt hoch)", "Sehr groß (doppelt hoch und breit)"};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i + 1);
            rb.setText(names[i]);
            size.addView(rb);
        }
        size.check(Prefs.size(this));
        ll.addView(size);

        auto = new Switch(this);
        auto.setText("Automatisch drucken (wenn Artikel und Adresse erkannt)");
        auto.setChecked(Prefs.auto(this));
        auto.setPadding(0, dp(16), 0, dp(4));
        ll.addView(auto);
        wait = field(ll, "Wartezeit ohne neue Nachricht bis Autodruck (Sekunden)",
                p.getString(Prefs.WAIT, Prefs.DEF_WAIT), InputType.TYPE_CLASS_NUMBER, false);

        repl = field(ll, "Produktnamen (Kundentext = Bon Text, eine Zeile je Eintrag)",
                Prefs.repl(this), InputType.TYPE_CLASS_TEXT, true);
        ignore = field(ll, "Chats nie verarbeiten (Name oder Nummer wie in WhatsApp, eine je Zeile)",
                p.getString(Prefs.IGNORE, ""), InputType.TYPE_CLASS_TEXT, true);

        labels = field(ll, "Etiketten (Name | Preis | Beschreibung, eine Zeile je Etikett)",
                Prefs.labels(this), InputType.TYPE_CLASS_TEXT, true);

        button(ll, "Speichern", () -> {
            save();
            fillLabels();
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show();
        });
        button(ll, "Testdruck", () -> { save(); Printer.printTest(this); });

        ScrollView sv = new ScrollView(this);
        sv.addView(ll);
        setContentView(sv);

        fillLabels();
        takeShared(getIntent());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        takeShared(intent);
    }

    private java.util.List<String[]> labelList() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String line : labels.getText().toString().split("\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 2 || p[0].trim().isEmpty()) continue;
            out.add(new String[]{p[0].trim(), p[1].trim(), p.length > 2 ? p[2].trim() : ""});
        }
        return out;
    }

    private void fillLabels() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String[] e : labelList()) names.add(e[0] + "  (" + e[1] + ")");
        android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names);
        labelPick.setAdapter(a);
    }

    private String[] selectedLabel() {
        java.util.List<String[]> l = labelList();
        int i = labelPick.getSelectedItemPosition();
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    /** Text, der über "Teilen" an Bondruck geschickt wurde, ins Textfeld übernehmen. */
    private void takeShared(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (t == null) return;
        String cur = free.getText().toString();
        free.setText(cur.isEmpty() ? t.toString() : cur + "\n" + t);
        free.setSelection(free.getText().length());
        free.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String list = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean ok = list != null && list.contains(getPackageName());
        status.setText(ok ? "Status: Aktiv, WhatsApp wird mitgelesen"
                          : "Status: Benachrichtigungszugriff fehlt (Button 1)");
        status.setTextColor(ok ? Color.rgb(0, 130, 0) : Color.rgb(200, 0, 0));
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
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
                .apply();
    }

    private void label(LinearLayout ll, String text) {
        TextView l = new TextView(this);
        l.setText(text);
        l.setPadding(0, dp(16), 0, 0);
        ll.addView(l);
    }

    private EditText field(LinearLayout ll, String label, String value, int type, boolean multi) {
        label(ll, label);
        EditText e = new EditText(this);
        e.setText(value);
        if (multi) {
            e.setInputType(type | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(3);
            e.setSingleLine(false);
        } else {
            e.setInputType(type);
        }
        ll.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void button(LinearLayout ll, String text, Runnable r) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        ll.addView(b, lp);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
