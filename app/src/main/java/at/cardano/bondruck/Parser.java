package at.cardano.bondruck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Macht aus freiem WhatsApp Text eine geordnete Bestellung. Keine Android Abhängigkeiten. */
final class Parser {

    static final class Item {
        String qty;
        String name;
        final List<String> notes = new ArrayList<>();
    }

    static final class Result {
        final List<Item> items = new ArrayList<>();
        final List<String> hints = new ArrayList<>();
        String address;
        String type; // LIEFERUNG oder ABHOLUNG
    }

    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final String STREET =
            "(?:stra(?:ß|ss)e|str\\.?(?=\\s|\\d)|gasse|platz|weg|allee|ring|zeile|lände|kai|steig|promenade)";
    private static final String WORD = "[A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß.\\-]*";
    private static final Pattern ADDRESS = Pattern.compile(
            "((?:" + WORD + "\\s+){0,3}[A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß.\\-]{2,}?\\s?" + STREET + ")"
                    + "\\s*(\\d{1,4}[a-zA-Z]?(?:\\s*[/\\-]\\s*\\d{1,4}[a-zA-Z]?)*)"
                    + "((?:\\s*,?\\s*(?:top|tür|stiege|stg\\.?|stock|tor)\\s*\\d{1,3}[a-zA-Z]?)*)"
                    + "(?:\\s*,?\\s*(1\\d{3})(?:\\s+wien)?)?", CI);

    private static final Set<String> ADDRESS_LEAD = new HashSet<>(Arrays.asList(
            "lieferung", "liefern", "adresse", "an", "nach", "bitte", "in", "die", "der", "zur", "zu",
            "ist", "meine", "unsere", "wohne", "wohnen", "lieferadresse", "addresse", "hallo"));

    private static final String NUMWORDS =
            "ein|eine|einen|einmal|zwei|zweimal|drei|dreimal|vier|fünf|sechs|sieben|acht|neun|zehn";
    private static final Pattern QTY = Pattern.compile(
            "(?:(?<![\\d.,/\\w])(\\d{1,2})\\s*(?:x|×|stk\\.?|stück|mal)?\\s*|\\b(" + NUMWORDS + ")\\s+)"
                    + "(?=[a-zäöüß])(?!(?:uhr|euro|eur|min|minuten|personen|leute|bisschen|wenig|paar)\\b)", CI);

    private static final Pattern NOTE_START = Pattern.compile("\\b(ohne|nur|extra|bitte|mit)\\b", CI);
    private static final Pattern NOTE_SPLIT = Pattern.compile("\\s+(?=bitte\\b)", CI);
    private static final Pattern CONTAINER = Pattern.compile("\\b(dosen?|flaschen?|fl\\.?|stk\\.?|stück)\\b", CI);

    private static final Set<String> TOPPINGS = new HashSet<>(Arrays.asList(
            "salami", "mais", "schinken", "thunfisch", "tunfisch", "pilze", "champignon", "champignons",
            "zwiebel", "zwiebeln", "peperoni", "pfefferoni", "oliven", "ananas", "paprika", "spinat",
            "sucuk", "knoblauch", "käse", "rucola", "tomaten", "broccoli", "brokkoli", "speck",
            "hühner", "huhn", "chili", "kebap", "kebab", "döner", "feta", "mozzarella", "gorgonzola"));
    private static final Set<String> FILLER = new HashSet<>(Arrays.asList(
            "hallo", "hi", "servus", "hey", "danke", "dankeschön", "bitte", "lg", "mfg", "grüße", "gruß",
            "guten", "abend", "tag", "morgen", "ok", "okay", "passt", "super", "ja", "nein", "und", "noch"));

    private Parser() {}

    /** Ersetzungen aus Text "von = zu" je Zeile. */
    static Map<String, String> replacements(String text) {
        Map<String, String> m = new LinkedHashMap<>();
        if (text == null) return m;
        for (String line : text.split("\\n")) {
            int i = line.indexOf('=');
            if (i <= 0) continue;
            String k = norm(line.substring(0, i));
            String v = line.substring(i + 1).trim();
            if (!k.isEmpty() && !v.isEmpty()) m.put(k, v);
        }
        return m;
    }

    static Result parse(List<String> messages, Map<String, String> repl) {
        Result r = new Result();
        for (String raw : messages) {
            if (raw == null) continue;
            String msg = Phone.remove(raw).replace('\r', ' ')
                    .replaceAll("(?i)\\b(tel|telefon|telefonnummer|handy|handynummer|nummer|nr)\\b\\.?:?", " ");
            String low = msg.toLowerCase(Locale.GERMAN);
            if (low.matches("(?s).*\\b(abholung|abholen|hole .*ab|holen .*ab|selbst ?abholung)\\b.*")) r.type = "ABHOLUNG";
            else if (low.matches("(?s).*\\b(lieferung|liefern|zustellung)\\b.*") && r.type == null) r.type = "LIEFERUNG";

            Matcher am = ADDRESS.matcher(msg);
            if (am.find()) {
                r.address = cleanAddress(am);
                msg = msg.substring(0, am.start()) + " " + msg.substring(am.end());
            }

            for (String line : msg.split("\\n")) parseLine(line, r, repl);
        }
        if (r.address != null && r.type == null) r.type = "LIEFERUNG";
        return r;
    }

    private static void parseLine(String line, Result r, Map<String, String> repl) {
        Matcher m = QTY.matcher(line);
        List<int[]> spans = new ArrayList<>();
        List<String> qtys = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            qtys.add(m.group(1) != null ? m.group(1) : wordToNum(m.group(2)));
        }
        int first = spans.isEmpty() ? line.length() : spans.get(0)[0];
        addHint(line.substring(0, first), r);
        for (int i = 0; i < spans.size(); i++) {
            int end = i + 1 < spans.size() ? spans.get(i + 1)[0] : line.length();
            String seg = line.substring(spans.get(i)[1], end).replaceAll("(?i)[\\s,;.!]+(bitte|danke)?[\\s,;.!]*$", "").trim();
            if (seg.isEmpty()) continue;
            Item it = new Item();
            it.qty = qtys.get(i);
            Matcher nm = NOTE_START.matcher(seg);
            String product = seg;
            if (nm.find() && nm.start() > 0) {
                product = seg.substring(0, nm.start());
                for (String n : NOTE_SPLIT.split(seg.substring(nm.start()))) {
                    String t = n.replaceAll("^\\s*bitte\\s+", "").replaceAll("[,;.!]+$", "").trim();
                    if (!t.isEmpty()) it.notes.add(cap(t));
                }
            }
            it.name = pretty(product, repl);
            if (it.name.isEmpty()) continue;
            r.items.add(it);
        }
    }

    private static void addHint(String text, Result r) {
        String t = text.replaceAll("[\\s,;.!?:+\\-]+", " ").trim();
        if (t.isEmpty()) return;
        boolean onlyFiller = true;
        for (String w : t.toLowerCase(Locale.GERMAN).split(" ")) {
            if (!FILLER.contains(w) && !w.matches("lieferung|abholung|adresse|pizza|bestellung|bestellen|möchte|hätte|gerne|gern|ich|wir")) {
                onlyFiller = false;
                break;
            }
        }
        if (!onlyFiller) r.hints.add(cap(t));
    }

    static String pretty(String product, Map<String, String> repl) {
        String p = norm(product);
        String container = null;
        Matcher c = CONTAINER.matcher(p);
        if (c.find()) {
            String w = c.group(1).toLowerCase(Locale.GERMAN);
            container = w.startsWith("d") ? "Dose" : w.startsWith("f") ? "Flasche" : null;
            p = CONTAINER.matcher(p).replaceAll(" ").replaceAll("\\s+", " ").trim();
        }
        p = p.replaceAll("^(und|noch|bitte)\\s+", "").replaceAll("\\s+(und|bitte)$", "").trim();
        if (p.isEmpty()) return "";

        String result = null;
        String noPizza = p.replaceAll("\\bpizz(?:a|en)\\b", " ").replaceAll("\\s+", " ").trim();
        if (repl.containsKey(p)) result = repl.get(p);
        else if (!noPizza.isEmpty() && repl.containsKey(noPizza)) result = repl.get(noPizza);

        if (result == null && !noPizza.isEmpty()) {
            List<String> tops = new ArrayList<>();
            boolean all = true;
            for (String w : noPizza.split(" ")) {
                if (w.equals("und") || w.equals("mit") || w.equals("+")) continue;
                if (!TOPPINGS.contains(w)) { all = false; break; }
                tops.add(repl.containsKey(w) && w.startsWith("tun") ? repl.get(w) : cap(w));
            }
            if (all && tops.size() >= 2) result = String.join(" + ", tops);
        }

        if (result == null) {
            String best = null;
            for (String k : repl.keySet()) {
                if (Pattern.compile("\\b" + Pattern.quote(k) + "\\b", CI).matcher(p).find()
                        && (best == null || k.length() > best.length())) best = k;
            }
            if (best != null) {
                String rest = p.replaceFirst("\\b" + Pattern.quote(best) + "\\b", " ")
                        .replaceAll("\\bpizz(?:a|en)\\b", " ").replaceAll("\\s+", " ").trim();
                result = repl.get(best) + (rest.isEmpty() ? "" : " " + titleCase(rest));
            }
        }
        if (result == null) result = titleCase(p);
        if (container != null && !result.toLowerCase(Locale.GERMAN).contains(container.toLowerCase(Locale.GERMAN))) {
            result += " " + container;
        }
        return result;
    }

    private static String cleanAddress(Matcher am) {
        String street = am.group(1).trim();
        String[] w = street.split("\\s+");
        int s = 0;
        while (s < w.length - 1 && ADDRESS_LEAD.contains(w[s].toLowerCase(Locale.GERMAN).replaceAll("[:,.]", ""))) s++;
        street = String.join(" ", Arrays.copyOfRange(w, s, w.length));
        street = Character.toUpperCase(street.charAt(0)) + street.substring(1);
        StringBuilder b = new StringBuilder(street).append(' ').append(am.group(2).replaceAll("\\s+", ""));
        if (am.group(3) != null && !am.group(3).trim().isEmpty()) {
            b.append(", ").append(am.group(3).replaceAll("^[\\s,]+", "").replaceAll("\\s+", " ").trim());
        }
        if (am.group(4) != null) b.append(", ").append(am.group(4)).append(" Wien");
        return b.toString();
    }

    static String norm(String s) {
        return s.toLowerCase(Locale.GERMAN).replaceAll("[,.;:!?()\"]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String wordToNum(String w) {
        switch (w.toLowerCase(Locale.GERMAN)) {
            case "zwei": case "zweimal": return "2";
            case "drei": case "dreimal": return "3";
            case "vier": return "4";
            case "fünf": return "5";
            case "sechs": return "6";
            case "sieben": return "7";
            case "acht": return "8";
            case "neun": return "9";
            case "zehn": return "10";
            default: return "1";
        }
    }

    static String cap(String s) {
        s = s.trim();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.matches("mit|und|ohne|in|auf") ? w : cap(w));
        }
        return b.toString();
    }
}
