package at.cardano.bondruck;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class Prefs {
    static final String IP = "ip";
    static final String PORT = "port";
    static final String SIZE = "size";       // 1 normal, 2 groß, 3 sehr groß
    static final String AUTO = "auto";
    static final String WAIT = "wait";       // Sekunden Ruhe bis Autodruck
    static final String REPL = "repl";
    static final String IGNORE = "ignore";
    static final String LABELS = "labels";
    static final String HEADER = "header";
    static final String PRICES = "prices";
    static final String STREETS = "streets";
    static final String LEARNED = "streetsLearned";
    static final String PFAND = "pfand";
    static final String LABEL_H = "labelHeight";
    static final String WINDOW = "window";
    static final String ENC = "encoding";
    static final String LABEL_TOP = "labelTop";
    private static boolean streetsLoaded;
    static final String LOGO_BON = "logoBon";
    static final String LOGO_LABEL = "logoLabel";
    static final String HEADER_BON = "headerBon";
    static final String HEADER_LABEL = "headerLabel";
    private static final int DEFAULTS_VERSION = 4;
    static final String LABEL_STYLE = "labelStyle"; // 1 kompakt, 2 groß zentriert
    private static final java.util.Map<String, String> assets = new java.util.HashMap<>();

    static final String DEF_IP = "192.168.68.51";
    static final String DEF_PORT = "9100";
    static final String DEF_WAIT = "120";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        SharedPreferences sp = c.getSharedPreferences("bondruck", Context.MODE_PRIVATE);
        int v = sp.getInt("defver", 0);
        if (v < DEFAULTS_VERSION) {
            SharedPreferences.Editor e = sp.edit();
            if (v < 2) e.remove(REPL).remove(LABELS);          // neue Speisekarte übernehmen
            if (v < 3) e.putBoolean(LOGO_LABEL, false).putBoolean(HEADER_LABEL, false);
            if (v < 4) e.remove(REPL);        // Produktnamen jetzt: Standard + eigene Ergänzungen
            e.putInt("defver", DEFAULTS_VERSION).apply();
        }
        if (!streetsLoaded) {
            streetsLoaded = true;
            reloadStreets(c, sp);
        }
        return sp;
    }

    /** Standardtexte aus assets (Speisekarte, Produktnamen, Firmendaten). */
    static synchronized String asset(Context c, String name) {
        String v = assets.get(name);
        if (v != null) return v;
        try (java.io.InputStream in = c.getAssets().open(name)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            v = new String(b.toByteArray(), "UTF-8").trim();
        } catch (Exception e) {
            v = "";
        }
        assets.put(name, v);
        return v;
    }

    static byte[] logo(Context c) {
        try (java.io.InputStream in = c.getAssets().open("logo.bin")) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    static void reloadStreets(Context c) { reloadStreets(c, c.getSharedPreferences("bondruck", Context.MODE_PRIVATE)); }

    private static void reloadStreets(Context c, SharedPreferences sp) {
        Streets.load(sp.getString(STREETS, ""), sp.getString(LEARNED, ""), asset(c, "strassen.txt"));
    }

    /** Erkannte Straße (mit Straßen-Endung) für Tippfehler-Korrektur merken. */
    static void learnStreet(Context c, String address) {
        if (address == null) return;
        String street = address.replaceAll("\\s+\\d.*$", "").trim();
        if (street.length() < 5 || Streets.match(street) != null) return;
        if (!street.matches("(?i).*(straße|strasse|gasse|weg|platz|allee|ring|zeile|lände)$")) return;
        SharedPreferences sp = get(c);
        String plz = address.matches(".*,\\s*(\\d{4}.*)$") ? address.replaceAll(".*,\\s*(\\d{4}.*)$", "$1") : "";
        sp.edit().putString(LEARNED, sp.getString(LEARNED, "") + "\n" + street + ";" + plz).apply();
        reloadStreets(c, sp);
    }

    static String encoding(Context c) { return get(c).getString(ENC, "utf8"); }
    static int windowMin(Context c) { return Math.max(5, num(get(c).getString(WINDOW, "30"), 30)); }
    static int labelHeight(Context c) { return num(get(c).getString(LABEL_H, "40"), 40); }
    static int labelTop(Context c) { return num(get(c).getString(LABEL_TOP, "12"), 12); }

    static int pfandCents(Context c) {
        Integer v = Prices.cents(get(c).getString(PFAND, "0,25"));
        return v == null ? 0 : v;
    }

    static String defRepl(Context c) { return asset(c, "produktnamen.txt"); }
    static String defLabels(Context c) { return asset(c, "etiketten.txt"); }
    static String defHeader(Context c) { return asset(c, "firmendaten.txt"); }
    static String header(Context c) { return get(c).getString(HEADER, defHeader(c)); }
    static boolean logoBon(Context c) { return get(c).getBoolean(LOGO_BON, true); }
    static boolean logoLabel(Context c) { return get(c).getBoolean(LOGO_LABEL, false); }
    static boolean prices(Context c) { return get(c).getBoolean(PRICES, true); }
    static int labelStyle(Context c) { return get(c).getInt(LABEL_STYLE, 1); }
    static boolean headerBon(Context c) { return get(c).getBoolean(HEADER_BON, true); }
    static boolean headerLabel(Context c) { return get(c).getBoolean(HEADER_LABEL, false); }

    static String ip(Context c) { return get(c).getString(IP, DEF_IP).trim(); }
    static int port(Context c) { return num(get(c).getString(PORT, DEF_PORT), 9100); }
    static int size(Context c) { return get(c).getInt(SIZE, 2); }
    static boolean auto(Context c) { return get(c).getBoolean(AUTO, true); }
    static int waitSec(Context c) { return Math.max(15, num(get(c).getString(WAIT, DEF_WAIT), 120)); }
    static String labels(Context c) { return get(c).getString(LABELS, defLabels(c)); }
    /** Für die Erkennung: Speisekarte plus eigene Einträge (eigene überschreiben). */
    static String repl(Context c) { return defRepl(c) + "\n" + get(c).getString(REPL, ""); }
    /** Nur die eigenen Einträge (Textfeld in den Einstellungen). */
    static String replOwn(Context c) { return get(c).getString(REPL, ""); }

    static Set<String> ignored(Context c) {
        Set<String> s = new HashSet<>();
        for (String l : get(c).getString(IGNORE, "").split("[\\n,]")) {
            String t = l.trim().toLowerCase(Locale.GERMAN);
            if (!t.isEmpty()) s.add(t);
        }
        return s;
    }

    private static int num(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
