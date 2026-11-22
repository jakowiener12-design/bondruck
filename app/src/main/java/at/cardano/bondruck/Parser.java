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
    // Vorwörter nur groß geschrieben und auf derselben Zeile (z.B. "Simmeringer Hauptstraße", "Kaiser Ebersdorfer Straße")
    private static final String PREFIX = "(?:(?-i:[A-ZÄÖÜ][a-zäöüß]+\\.?|St\\.|Dr\\.)[ \\t]+){0,3}";
    private static final Pattern ADDRESS = Pattern.compile(
            "(" + PREFIX + "[A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß.\\-]{2,}?[ \\t]?" + STREET + ")"
                    + "[ \\t]*(\\d{1,4}[a-zA-Z]?(?:[ \\t]*[/\\-][ \\t]*\\d{1,4}[a-zA-Z]?)*)"
                    + "((?:[ \\t]*,?[ \\t]*(?:top|tür|stiege|stg\\.?|stock|tor)[ \\t]*\\d{1,3}[a-zA-Z]?)*)"
                    + "(?:[ \\t]*,?[ \\t]*(1\\d{3})(?:[ \\t]+wien)?)?", CI);

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
                // Produktwörter und Füllwörter vorne abtrennen und im Text lassen
                String[] w = am.group(1).trim().split("[ \\t]+");
                int keep = 0;
                while (keep < w.length - 1) {
                    String lw = w[keep].toLowerCase(Locale.GERMAN).replaceAll("[:,.]", "");
                    if (ADDRESS_LEAD.contains(lw) || repl.containsKey(lw) || TOPPINGS.contains(lw)
                            || FILLER.contains(lw) || CONTAINER.matcher(lw).matches()) keep++;
                    else break;
                }
                StringBuilder dropped = new StringBuilder();
                for (int i = 0; i < keep; i++) {
                    String lw = w[i].toLowerCase(Locale.GERMAN).replaceAll("[:,.]", "");
                    if (!ADDRESS_LEAD.contains(lw) && !FILLER.contains(lw)) dropped.append(w[i]).append(' ');
                }
                r.address = cleanAddress(am, keep);
                msg = msg.substring(0, am.start()) + " " + dropped + msg.substring(am.end());
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
            String seg = line.substring(spans.get(i)[1], end).replaceAll("(?i)[\\s,;.!]+(bitte|danke|und|oder)?[\\s,;.!]*$", "").trim();
            if (seg.isEmpty()) continue;
            Item it = new Item();
            it.qty = qtys.get(i);
            Matcher nm = NOTE_START.matcher(seg);
            String product = seg;
            boolean found = nm.find();
            // "pizza mit sucuk": "mit" gehört hier zum Produkt
            while (found && nm.group(1).equalsIgnoreCase("mit")
                    && seg.substring(0, nm.start()).trim().matches("(?i)(pizza|pizzen|pide|baguette)")) {
                found = nm.find();
            }
            if (found && nm.start() > 0) {
                product = seg.substring(0, nm.start());
                for (String n : NOTE_SPLIT.split(seg.substring(nm.start()))) {
                    String t = n.replaceAll("^\\s*bitte\\s+", "").replaceAll("(?i)[,;.!\\s]+(und|bitte)?\\s*$", "").trim();
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

    private static final Pattern SIZE_MINI = Pattern.compile("\\b(mini|minipizza|mini-pizza|klein[e]?)\\b", CI);
    private static final Pattern SIZE_FAM = Pattern.compile("\\b(familie|familien|family|familienpizza|familypizza|party)\\b", CI);
    private static final Pattern DRINK_BIG = Pattern.compile("(0\\s*[,.]\\s*5\\s*l?|\\bhalbe\\b|\\bgro(ß|ss)e?[rn]?\\b|\\bflaschen?\\b|1/2)", CI);
    private static final Pattern DRINK_SMALL = Pattern.compile("0\\s*[,.]\\s*33\\s*l?", CI);
    private static final String[] BASES = {"pizza", "pide", "baguette"};

    static String pretty(String product, Map<String, String> repl) {
        boolean big = DRINK_BIG.matcher(product).find();
        String raw = DRINK_SMALL.matcher(DRINK_BIG.matcher(product).replaceAll(" ")).replaceAll(" ");
        String p = norm(product);
        String container = null;
        Matcher c = CONTAINER.matcher(p);
        if (c.find()) {
            String w = c.group(1).toLowerCase(Locale.GERMAN);
            container = w.startsWith("d") ? "Dose" : w.startsWith("f") ? "Flasche" : null;
        }
        p = p.replaceAll("^(und|noch|bitte)\\s+", "").replaceAll("\\s+(und|bitte)$", "").trim();
        if (p.isEmpty()) return "";

        String result = repl.get(p);                      // 1. exakter Treffer, z.B. "großer salat"
        String size = null;
        if (result == null) {
            String q = norm(raw);
            q = CONTAINER.matcher(q).replaceAll(" ");
            if (SIZE_MINI.matcher(q).find()) size = "mini";
            else if (SIZE_FAM.matcher(q).find()) size = "fam";
            q = SIZE_FAM.matcher(SIZE_MINI.matcher(q).replaceAll(" ")).replaceAll(" ")
                    .replaceAll("^(und|noch|bitte)\\s+", "").replaceAll("\\s+(und|bitte)$", "")
                    .replaceAll("\\s+", " ").trim();
            if (q.isEmpty()) q = "margherita";
            result = lookup(q, repl);
        }
        if (container != null && result != null && !result.matches(".*\\d[,.]\\d+l.*")
                && !result.toLowerCase(Locale.GERMAN).contains(container.toLowerCase(Locale.GERMAN))) {
            result += " " + container;
        }
        if (big && result != null && result.contains("0,33l")) result = result.replace("0,33l", "0,5l");
        if (size != null && result != null) result = applySize(result, size);
        return result == null ? "" : result;
    }

    private static String applySize(String r, String size) {
        String pre = size.equals("mini") ? "Mini-Pizza" : "Familienpizza";
        if (r.startsWith("Pizza ")) return pre + r.substring(5);
        if (r.startsWith("Wunschpizza"))
            return (size.equals("mini") ? "Mini-Wunschpizza" : "Familien Wunschpizza") + r.substring(11);
        if (r.contains(" + ")) return pre + ": " + r;
        return r;
    }

    private static String lookup(String p, Map<String, String> repl) {
        if (repl.containsKey(p)) return repl.get(p);
        for (String base : BASES) {
            if (Pattern.compile("\\b" + base + "\\b").matcher(p).find()) {
                String rest = p.replaceAll("\\b" + base + "\\b", " ").replaceAll("\\b(mit|von)\\b", " ")
                        .replaceAll("\\s+", " ").trim();
                if (repl.containsKey(base + " " + rest)) return repl.get(base + " " + rest);
                if (base.equals("pizza") && repl.containsKey(rest)) return repl.get(rest);
                if (rest.isEmpty()) return cap(base);
            }
        }
        String noPizza = p.replaceAll("\\bpizz(?:a|en)\\b", " ").replaceAll("\\s+", " ").trim();
        if (!noPizza.isEmpty()) {
            List<String> tops = new ArrayList<>();
            boolean all = true;
            for (String w : noPizza.split(" ")) {
                if (w.equals("und") || w.equals("mit") || w.equals("+")) continue;
                if (!TOPPINGS.contains(w)) { all = false; break; }
                tops.add(w.startsWith("tun") ? "Thunfisch" : cap(w));
            }
            if (all && tops.size() >= 2) return String.join(" + ", tops);
        }
        String best = null;
        for (String k : repl.keySet()) {
            if (k.length() >= 3 && Pattern.compile("\\b" + Pattern.quote(k) + "\\b", CI).matcher(p).find()
                    && (best == null || k.length() > best.length())) best = k;
        }
        if (best != null) {
            String rest = p.replaceFirst("\\b" + Pattern.quote(best) + "\\b", " ")
                    .replaceAll("\\bpizz(?:a|en)\\b", " ").replaceAll("\\s+", " ").trim();
            if (rest.isEmpty()) return repl.get(best);
            List<String> tops = new ArrayList<>();
            for (String w : rest.split(" ")) {
                if (w.equals("und") || w.equals("mit")) continue;
                if (!TOPPINGS.contains(w)) { tops = null; break; }
                tops.add(w.startsWith("tun") ? "Thunfisch" : cap(w));
            }
            if (tops != null && !tops.isEmpty()) return repl.get(best) + ": " + String.join(" + ", tops);
            return repl.get(best) + " " + titleCase(rest);
        }
        return titleCase(p);
    }

    private static String cleanAddress(Matcher am, int skip) {
        String street = am.group(1).trim();
        String[] w = street.split("\\s+");
        int s = Math.min(skip, w.length - 1);
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
