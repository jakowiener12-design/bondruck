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
    static final String DEF_LABELS =
            "Pizza Salami | 9,50 | Tomatensauce, Käse, Salami\n" +
            "Pizza Mexikaner | 11,00 | Tomatensauce, Käse, Faschiertes, Mais, Pfefferoni, scharf\n" +
            "Hühner Sandwich | 6,50 | Hühnerfleisch, Salat, Tomaten, Zwiebel, Sauce";

    static final String DEF_IP = "192.168.68.51";
    static final String DEF_PORT = "9100";
    static final String DEF_WAIT = "120";
    static final String DEF_REPL =
            "mexicana = Mexikaner\n" +
            "mexikana = Mexikaner\n" +
            "mexikaner = Mexikaner\n" +
            "margarita = Margherita\n" +
            "eistee pfirsich = Eistee Pfirsich Dose\n" +
            "eistee zitrone = Eistee Zitrone Dose\n" +
            "cola zero = Cola Zero Dose\n" +
            "cola = Cola Dose\n" +
            "fanta = Fanta Dose\n" +
            "sprite = Sprite Dose\n" +
            "keba = Hühner Sandwich\n" +
            "kebap = Hühner Sandwich\n" +
            "kebab = Hühner Sandwich\n" +
            "döner = Hühner Sandwich\n" +
            "tunfisch = Thunfisch";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("bondruck", Context.MODE_PRIVATE);
    }

    static String ip(Context c) { return get(c).getString(IP, DEF_IP).trim(); }
    static int port(Context c) { return num(get(c).getString(PORT, DEF_PORT), 9100); }
    static int size(Context c) { return get(c).getInt(SIZE, 2); }
    static boolean auto(Context c) { return get(c).getBoolean(AUTO, true); }
    static int waitSec(Context c) { return Math.max(15, num(get(c).getString(WAIT, DEF_WAIT), 120)); }
    static String labels(Context c) { return get(c).getString(LABELS, DEF_LABELS); }
    static String repl(Context c) { return get(c).getString(REPL, DEF_REPL); }

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
