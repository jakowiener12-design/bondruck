package at.cardano.bondruck;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bekannte Straßen (Simmering, Schwechat, Mannswörth, eigene) und Tippfehler-Korrektur. Keine Android Abhängigkeit. */
final class Streets {
    static final class Street { String name; String plz; String key; String stem; }

    private static volatile List<Street> list = new ArrayList<>();

    private Streets() {}

    /** Zeilen "Name;PLZ Ort" (PLZ optional). */
    static void load(String... texts) {
        List<Street> l = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String t : texts) {
            if (t == null) continue;
            for (String line : t.split("\n")) {
                String[] p = line.split(";", 2);
                String n = p[0].trim();
                if (n.length() < 3) continue;
                Street s = new Street();
                s.name = n;
                s.plz = p.length > 1 ? p[1].trim() : "";
                s.key = key(n);
                s.stem = s.key.replaceAll("(strasse|gasse|weg|platz|allee|ring|zeile|lande)$", "");
                if (seen.add(s.key)) l.add(s);
                else for (Street o : l) if (o.key.equals(s.key) && !o.plz.equals(s.plz)) o.plz = "";  // gleiche Straße in 2 Orten
            }
        }
        list = l;
    }

    static int size() { return list.size(); }

    /** Vergleichsschlüssel: klein, ß->ss, Umlaute vereinfacht, "str." -> "strasse", ohne Leer-/Bindestriche. */
    static String key(String s) {
        String k = s.toLowerCase(Locale.GERMAN).replace("ß", "ss");
        k = k.replaceAll("str\\.?(?=\\s|$|\\d)", "strasse").replaceAll("\\bstr\\b", "strasse");
        k = Normalizer.normalize(k, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        k = k.replaceAll("[^a-z0-9]", "");
        k = k.replace("strase", "strasse").replace("srtasse", "strasse").replace("starsse", "strasse")
                .replace("gase", "gasse").replace("plaz", "platz");
        return k;
    }

    /** Beste bekannte Straße zu einem (evtl. falsch geschriebenen) Namen, sonst null. */
    static Street match(String raw) {
        String k = key(raw);
        if (k.length() < 4) return null;
        Street best = null;
        int bestD = Integer.MAX_VALUE;
        for (Street s : list) if (s.key.equals(k)) return s;
        for (Street s : list) if (s.stem.length() >= 5 && s.stem.equals(k)) return s;   // "etrich" -> Etrichstraße
        for (Street s : list) {
            int max = s.key.length() <= 8 ? 1 : s.key.length() <= 14 ? 2 : 3;
            if (Math.abs(s.key.length() - k.length()) > max) continue;
            int d = dist(k, s.key, max);
            if (d <= max && d < bestD) { best = s; bestD = d; }
        }
        return best;
    }

    private static final Pattern CANDIDATE = Pattern.compile(
            "((?:[\\p{L}][\\p{L}.\\-]*[ \\t]){0,3}[\\p{L}][\\p{L}.\\-]{3,})[ \\t]*(\\d{1,4}[a-zA-Z]?(?:[ \\t]*[/\\-][ \\t]*\\d{1,4}[a-zA-Z]?)*)",
            Parser.UCC);

    /** Sucht im Text eine bekannte Straße (auch mit Tippfehler) mit Hausnummer. {start, end, name, nummer, plz} */
    static Object[] find(String text) {
        Matcher m = CANDIDATE.matcher(text);
        while (m.find()) {
            String[] w = m.group(1).trim().split("[ \\t]+");
            // längste passende Wortfolge am Ende vor der Hausnummer
            for (int from = 0; from < w.length; from++) {
                StringBuilder sb = new StringBuilder();
                for (int i = from; i < w.length; i++) { if (sb.length() > 0) sb.append(' '); sb.append(w[i]); }
                Street s = match(sb.toString());
                if (s != null) {
                    int start = m.start(1) + m.group(1).indexOf(w[from]);
                    return new Object[]{start, m.end(), s.name, m.group(2).replaceAll("\\s+", ""), s.plz};
                }
            }
        }
        return null;
    }

    private static int dist(String a, String b, int max) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= m; j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                rowMin = Math.min(rowMin, d[i][j]);
            }
            if (rowMin > max) return max + 1;
        }
        return d[n][m];
    }
}
