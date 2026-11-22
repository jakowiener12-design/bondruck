package at.cardano.bondruck;

import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Preise aus der Etikettenliste (Name | Preis | Beschreibung), Speisekarte als Ersatz. */
final class Prices {
    private Prices() {}

    static Map<String, Integer> load(Context c) {
        Map<String, Integer> m = new HashMap<>();
        read(m, Prefs.labels(c));
        read(m, Prefs.defLabels(c));    // fehlende Namen aus der Speisekarte
        return m;
    }

    static void read(Map<String, Integer> m, String text) {
        if (text == null) return;
        for (String line : text.split("\\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 2) continue;
            Integer cents = cents(p[1]);
            String k = p[0].trim().toLowerCase(Locale.GERMAN);
            if (cents != null && !k.isEmpty() && !m.containsKey(k)) m.put(k, cents);
        }
    }

    static Integer cents(String s) {
        String t = s.replace("€", "").replace("EUR", "").trim().replace(".", ",");
        if (t.isEmpty()) return null;
        try {
            String[] p = t.split(",");
            int euro = Integer.parseInt(p[0].trim());
            int ct = 0;
            if (p.length > 1) {
                String c = (p[1].trim() + "00").substring(0, 2);
                ct = Integer.parseInt(c);
            }
            return euro * 100 + ct;
        } catch (Exception e) {
            return null;
        }
    }

    /** Preis für einen Artikelnamen vom Bon, null wenn unbekannt. */
    static Integer of(Map<String, Integer> m, String name) {
        if (m == null || name == null) return null;
        String k = name.trim().toLowerCase(Locale.GERMAN);
        if (m.containsKey(k)) return m.get(k);
        // eigene Kombination = Wunschpizza
        if (k.contains(" + ") || k.startsWith("wunschpizza") || k.contains("wunschpizza")) {
            if (k.startsWith("mini")) return m.get("mini-wunschpizza");
            if (k.startsWith("famil")) return m.get("familien wunschpizza");
            return m.get("wunschpizza");
        }
        // "Dose"/"Flasche" Zusatz ignorieren
        String k2 = k.replaceAll("\\s+(dose|flasche)$", "");
        if (m.containsKey(k2)) return m.get(k2);
        return null;
    }

    static String fmt(int cents) {
        return (cents / 100) + "," + String.format(Locale.GERMAN, "%02d", cents % 100);
    }

    /** Getränk mit Pfand: Name enthält eine Literangabe wie 0,33l oder 0,5l. */
    static boolean isDrink(String name) {
        return name != null && name.matches("(?i).*\\b\\d+[,.]\\d+\\s*l\\b.*");
    }

    static int[] total(Map<String, Integer> m, List<Parser.Item> items) { return total(m, items, 0); }

    /** Summe (inkl. Pfand) und Anzahl Positionen ohne Preis. */
    static int[] total(Map<String, Integer> m, List<Parser.Item> items, int pfand) {
        int sum = 0, missing = 0;
        for (Parser.Item it : items) {
            int q = qty(it);
            if (pfand > 0 && isDrink(it.name)) sum += pfand * q;
            Integer p = of(m, it.name);
            if (p == null) missing++; else sum += p * q;
            for (String ex : it.extras) {
                Integer pe = of(m, ex);
                if (pe == null) missing++; else sum += pe * q;
            }
        }
        return new int[]{sum, missing};
    }

    static int qty(Parser.Item it) {
        try { return Integer.parseInt(it.qty); } catch (Exception e) { return 1; }
    }
}
