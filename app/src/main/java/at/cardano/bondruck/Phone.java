package at.cardano.bondruck;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Findet Telefonnummern und formatiert sie einheitlich. */
final class Phone {
    // +43 660 1603061, 0660/1603061, 0043-660-160 30 61, 06601603061 ...
    private static final Pattern NUMBER =
            Pattern.compile("(?:\\+|00)?\\d[\\d\\s/\\-()]{6,18}\\d");

    private Phone() {}

    /** Alle Nummern aus einem Text, sauber formatiert, ohne Duplikate. */
    static List<String> findAll(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return new ArrayList<>(out);
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String f = format(m.group());
            if (f != null) out.add(f);
        }
        return new ArrayList<>(out);
    }

    /** Entfernt alle erkannten Telefonnummern aus dem Text. */
    static String remove(String text) {
        if (text == null) return "";
        Matcher m = NUMBER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, format(m.group()) != null ? " " : Matcher.quoteReplacement(m.group()));
        m.appendTail(sb);
        return sb.toString();
    }

    /** Liefert die formatierte Nummer, wenn der ganze Text eine Nummer ist, sonst null. */
    static String ifNumber(String text) {
        if (text == null) return null;
        String t = text.replace('‪', ' ').replace('‬', ' ').trim();
        if (!t.matches("(?:\\+|00)?[\\d\\s/\\-()]{7,20}")) return null;
        return format(t);
    }

    static String format(String raw) {
        boolean plus = raw.trim().startsWith("+");
        String d = raw.replaceAll("\\D", "");
        if (plus) d = "+" + d;
        else if (d.startsWith("00")) d = "+" + d.substring(2);

        if (d.startsWith("+43")) {
            String rest = d.substring(3);
            if (rest.length() < 6 || rest.length() > 13) return null;
            return "0" + group(rest);
        }
        if (d.startsWith("+")) {
            if (d.length() < 9 || d.length() > 16) return null;
            return d.substring(0, 3) + " " + d.substring(3);
        }
        if (d.startsWith("0") && d.length() >= 8 && d.length() <= 14) {
            return "0" + group(d.substring(1));
        }
        return null; // z.B. Postleitzahlen, Preise, Uhrzeiten
    }

    /** Vorwahl abtrennen: Mobil (6xx) 3 Stellen, Wien (1) 1 Stelle, sonst 3 Stellen. */
    private static String group(String n) {
        int split = n.startsWith("1") ? 1 : Math.min(3, n.length());
        return n.substring(0, split) + " " + n.substring(split);
    }
}
