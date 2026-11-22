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
        final List<String> extras = new ArrayList<>();   // kostenpflichtige Beilagen je Stück
    }

    private static final Pattern EXTRA = Pattern.compile("^(extra|dazu|plus|zusätzlich|ekstra|\\+)\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Gleiche Artikel (Name, Hinweise, Beilagen) zusammenfassen. */
    static void merge(Result r) {
        Map<String, Item> m = new LinkedHashMap<>();
        for (Item it : r.items) {
            String key = (it.name + "|" + it.notes + "|" + it.extras).toLowerCase(Locale.GERMAN);
            Item have = m.get(key);
            if (have == null) { m.put(key, it); continue; }
            try { have.qty = String.valueOf(Integer.parseInt(have.qty) + Integer.parseInt(it.qty)); }
            catch (NumberFormatException e) { m.put(key + m.size(), it); }
        }
        r.items.clear();
        r.items.addAll(m.values());
    }

    static final class Result {
        final List<Item> items = new ArrayList<>();
        final List<String> hints = new ArrayList<>();
        String address;
        String type; // LIEFERUNG oder ABHOLUNG
    }

    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final String STREET =
            "(?:stra(?:ß|ss|s)e|s(?:rt|tar|ta)a?(?:ß|ss|s)e|str\\.?(?=\\s|\\d)|ga(?:ss|ß|s)e|weg|pla(?:tz|z)|allee|ring|zeile|lände|lande|kai|steig|promenade)";
    private static final String WORD = "[A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß.\\-]*";
    // Vorwörter nur groß geschrieben und auf derselben Zeile (z.B. "Simmeringer Hauptstraße", "Kaiser Ebersdorfer Straße")
    private static final String PREFIX = "(?:(?-i:[A-ZÄÖÜ][a-zäöüß]+\\.?|St\\.|Dr\\.)[ \\t]+){0,3}";
    private static final Pattern ADDRESS = Pattern.compile(
            "(" + PREFIX + "[A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß.\\-]{2,}?[ \\t]?" + STREET + ")"
                    + "[ \\t]*(\\d{1,4}[a-zA-Z]?(?:[ \\t]*[/\\-][ \\t]*\\d{1,4}[a-zA-Z]?)*)"
                    + "((?:[ \\t]*,?[ \\t]*(?:top|tür|stiege|stg\\.?|stock|tor)[ \\t]*\\d{1,3}[a-zA-Z]?)*)"
                    + "(?:[ \\t]*,?[ \\t]*(1\\d{3})(?:[ \\t]+wien)?)?", CI);

    private static final Pattern REST = Pattern.compile(
            "((?:[ \\t]*,?[ \\t]*(?:top|tür|stiege|stg\\.?|stock|tor)[ \\t]*\\d{1,3}[a-zA-Z]?)*)(?:[ \\t]*,?[ \\t]*([12]\\d{3})(?:[ \\t]+(?:wien|schwechat))?)?", CI);
    private static final Pattern TIME = Pattern.compile(
            "(?<![\\wäöü])(in|um|gegen|bis|ca\\.?|circa|ungefähr|so um|saat)\\s*\\d{1,2}([:.]\\d{2})?\\s*(uhr|min\\.?|minuten|std|stunde|stunden|h|dakika|dk|da|de)?(?![\\wäöü])"
                    + "|(?<![\\wäöü])\\d{1,2}([:.]\\d{2})?\\s*(uhr|dakika(ya)?|dk)(?![\\wäöü])"
                    + "|\\b(so schnell wie möglich|schnellstmöglich|asap|sofort|gleich jetzt|jetzt gleich|hemen)\\b", CI);
    private static final Pattern PHRASES = Pattern.compile(
            "\\b((ich|wir) )?(hole|holen|hol|komme|kommen|komm)( es| das| sie| ihn| alles)?( dann| gleich| selbst)?( ab| vorbei)\\b"
                    + "|\\b(zum mitnehmen|zum abholen|zur abholung|bitte liefern|bitte bringen|nach hause)\\b"
                    + "|\\b(gelip alaca(ğ|g)ım|gelip alırım|gelip alirim|gelip alacam|alaca(ğ|g)ım|getirin|getirir misiniz|getirebilir misiniz|gönderin)\\b"
                    + "|\\b(ich (hätte|möchte|will|bräuchte|nehme) gerne?|ich (hätte|möchte|will|bräuchte|nehme)|wir (hätten|möchten|nehmen)( gerne?)?|hätte gern(e)?|möchte gern(e)?|bestellen|bestellung|istiyorum|istiyoruz|sipariş|siparis)\\b", CI);

    private static final Set<String> ADDRESS_LEAD = new HashSet<>(Arrays.asList(
            "lieferung", "liefern", "adresse", "an", "nach", "bitte", "in", "die", "der", "zur", "zu",
            "ist", "meine", "unsere", "wohne", "wohnen", "lieferadresse", "addresse", "hallo", "bei", "hausnummer",
            "adres", "adresim", "adresi", "adresimiz", "adress"));

    private static final String NUMWORDS =
            "a|ein|eine|einen|einer|eins|eines|einmal|zwei|zweimal|zwa|zwoa|drei|dreimal|vier|fünf|sechs|sieben|acht|neun|zehn"
            + "|bir|iki|üç|uc|dört|dort|beş|bes|altı|alti|yedi|sekiz|dokuz";
    private static final Pattern QTY = Pattern.compile(
            "(?:(?<![\\d.,/\\w])(\\d{1,2})\\s*(?:(?:x|×)(?![a-zäöüß])|stk\\.?|stück|mal|tane|adet)?\\s*|\\b(" + NUMWORDS + ")\\s+(?:tane\\s+|adet\\s+|mal\\s+|stück\\s+)?)"
                    + "(?=\\p{L})(?!(?:uhr|euro|eur|min|minuten|personen|leute|bisschen|bissl|bisserl|wenig|paar|tanesi|tanesine|tanesinde|dakika|dakikaya|saat|bisl)\\b)", CI);

    private static final String NOTE_WORDS = "ohne|nur|extra|dazu|zusätzlich|bitte|mit|kein|keine|keinen|scharf|nicht scharf|wenig|viel|mehr|halbiert|halbieren|schneiden|separat|getrennt|gut durch"
            + "|soğansız|sogansiz|soğan|acılı|acili|acısız|acisiz|sadece|salatasız|salatasiz|sossuz|soslu|bol|az|domatessiz|ekstra|lütfen|lutfen|ikiye|ortadan|ayrı|ayri";
    private static final Pattern NOTE_START = Pattern.compile("(?<![\\wäöüßçğışü])(" + NOTE_WORDS + ")(?![\\wäöüßçğışü])", CI);
    private static final Pattern NOTE_SPLIT = Pattern.compile("\\s+(?=(bitte|extra|dazu|ohne)\\b)", CI);
    private static final Pattern CONTAINER = Pattern.compile("\\b(dosen?|flaschen?|fl\\.?|stk\\.?|stück)\\b", CI);

    private static final Set<String> TOPPINGS = new HashSet<>(Arrays.asList(
            "salami", "mais", "schinken", "thunfisch", "tunfisch", "pilze", "champignon", "champignons",
            "zwiebel", "zwiebeln", "peperoni", "pfefferoni", "oliven", "ananas", "paprika", "spinat",
            "sucuk", "knoblauch", "käse", "rucola", "tomaten", "broccoli", "brokkoli", "speck",
            "hühner", "huhn", "chili", "kebap", "kebab", "döner", "feta", "mozzarella", "gorgonzola"));
    private static final Set<String> FILLER = new HashSet<>(Arrays.asList(
            "hallo", "hi", "servus", "hey", "danke", "dankeschön", "bitte", "lg", "mfg", "grüße", "gruß",
            "guten", "abend", "tag", "morgen", "ok", "okay", "passt", "super", "ja", "nein", "und", "noch",
            "griaß", "di", "grüß", "gott", "moin", "baba", "tschüss", "ciao", "bussi", "vielen", "dank", "liebe",
            "merhaba", "selam", "selamün", "aleyküm", "selamun", "aleykum", "slm", "mrb", "abi", "abla", "kardeş", "kardes",
            "hocam", "teşekkürler", "tesekkurler", "teşekkür", "sağol", "sagol", "sağolun", "tamam", "tmm", "evet", "hayır",
            "iyi", "akşamlar", "aksamlar", "günler", "kolay", "gelsin", "lütfen", "lutfen", "rica", "ederim", "ve", "da", "de"));

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
            if (low.matches("(?s).*\\b(abholung|abholen|abhole|hole\\b.*\\bab|holen\\b.*\\bab|hol\\b.*\\bab|selbst ?abholung|komme?n? (gleich |dann )?vorbei|zum mitnehmen|mitnehmen|take ?away|pick ?up|abholer|abhol"
                    + "|gelip alaca(ğ|g)ım|gelip alırım|gelip alirim|gelip alacam|gel al|alaca(ğ|g)ım|alacam|almaya gelece(ğ|g)im|paket alaca(ğ|g)ım|dükkana gelece(ğ|g)im)\\b.*")) r.type = "ABHOLUNG";
            else if (low.matches("(?s).*\\b(lieferung|liefern|liefert|zustellung|zustellen|bringen|bringt|vorbeibringen|schicken|delivery|nach hause|zu mir"
                    + "|getirin|getirir misiniz|getirebilir misiniz|gönderin|gonderin|yollayın|yollayin|eve|adrese|adresime|teslimat|sipariş adres)\\b.*") && r.type == null) r.type = "LIEFERUNG";

            Matcher tm = TIME.matcher(msg);
            while (tm.find()) {
                String z = tm.group().trim().replaceAll("[,.!]+$", "");
                if (!r.hints.contains("Zeit: " + z)) r.hints.add("Zeit: " + z);
            }
            msg = msg.replaceAll("(?iu)(?<![\\p{L}\\d])(bir|1)\\s+tane(si|sine|sinde)(?![\\p{L}])", "1x")
                     .replaceAll("(?iu)(?<![\\p{L}\\d])(iki|2)\\s+tane(si|sine|sinde)(?![\\p{L}])", "2x")
                     .replaceAll("(?iu)(?<![\\p{L}])(einer|eins|einen) davon(?![\\p{L}])", "1x")
                     .replaceAll("(?iu)(?<![\\p{L}])davon (einer|eins|einen)(?![\\p{L}])", "1x");
            msg = TIME.matcher(msg).replaceAll(" ").replaceAll("[ \\t]+", " ");
            msg = PHRASES.matcher(msg).replaceAll(" ").replaceAll("[ \\t]+", " ");

            // 1. bekannte Straße (auch mit Tippfehler / ohne Endung)
            Object[] f = Streets.find(msg);
            if (f != null) {
                int end = (Integer) f[1];
                Matcher rest = REST.matcher(msg.substring(end));
                String a = f[2] + " " + f[3];
                String plz = (String) f[4];
                if (rest.lookingAt()) {
                    if (rest.group(1) != null && !rest.group(1).trim().isEmpty())
                        a += ", " + rest.group(1).replaceAll("^[\\s,]+", "").replaceAll("\\s+", " ").trim();
                    if (rest.group(2) != null) plz = rest.group(2) + (rest.group(2).startsWith("1") ? " Wien" : "");
                    end += rest.end();
                }
                if (!plz.isEmpty()) a += ", " + plz;
                r.address = a;
                msg = msg.substring(0, (Integer) f[0]) + " " + msg.substring(end);
            } else {
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
                    r.address = correctStreet(cleanAddress(am, keep));
                    msg = msg.substring(0, am.start()) + " " + dropped + msg.substring(am.end());
                }


            }

            for (String line : msg.split("\\n")) parseLine(line, r, repl);
        }
        if (r.address != null && r.type == null) r.type = "LIEFERUNG";
        return r;
    }

    private static final List<String> pendingExtra = new ArrayList<>();

    private static synchronized void parseLine(String line, Result r, Map<String, String> repl) {
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
            String seg = line.substring(spans.get(i)[1], end)
                    .replaceAll("(?i)(\\s+(lieferung|liefern|abholung|abholen|adresse|lieferadresse|an|die|der|nach|zur|zu|bitte|um|für|fuer))+[\\s,;.!:]*$", "")
                    .replaceAll("(?iu)[\\s,;.!]+(bitte|danke|und|oder|ve|veya|lütfen|lutfen)?[\\s,;.!]*$", "").trim();
            if (seg.isEmpty()) continue;
            // "1x ohne zwiebel", "einer nur sauce": Hinweis für einen Teil des vorigen Artikels
            String segN = seg.replaceAll("(?iu)^(tanesi|tanesine|tanesinde|davon|bitte|biri|birinde)\\s+", "");
            if (!r.items.isEmpty() && segN.matches("(?isu)^(ohne|nur|mit|extra|dazu|bitte|scharf|mild|halbiert|kein|keine|soğansız|sogansiz|acılı|acili|acısız|acisiz|sadece|salatasız|salatasiz|sossuz|bol|az|ekstra|domatessiz)(?![\\p{L}]).*")) {
                String t = segN.replaceAll("[,;.!]+$", "").trim();
                r.items.get(r.items.size() - 1).notes.add(qtys.get(i) + "x " + cap(translate(t)));
                continue;
            }
            Item it = new Item();
            it.qty = qtys.get(i);
            // "karışık porsiyon, patates ve 2 su" / "2 kebap und cola": weitere Artikel ohne Menge abtrennen
            String[] parts = seg.split("(?iu)\\s*,\\s*|\\s+(?:und|ve|u\\.?|\\+)\\s+");
            if (parts.length > 1) {
                StringBuilder keep = new StringBuilder(parts[0]);
                List<String> extraItems = new ArrayList<>();
                for (int k = 1; k < parts.length; k++) {
                    String pn = norm(parts[k]);
                    boolean product1 = !pn.isEmpty() && !TOPPINGS.contains(pn) && repl.containsKey(pn)
                            && !NOTE_START.matcher(pn).lookingAt();
                    if (product1 && extraItems.isEmpty()) extraItems.add(parts[k]);
                    else if (product1) extraItems.add(parts[k]);
                    else if (extraItems.isEmpty()) keep.append(", ").append(parts[k]);
                    else keep.append(", ").append(parts[k]);
                }
                if (!extraItems.isEmpty()) {
                    seg = keep.toString();
                    pendingExtra.addAll(extraItems);
                }
            }
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
                    String t = n.replaceAll("^\\s*bitte\\s+", "").replaceAll("(?iu)[,;.!\\s]+(und|bitte|ve|lütfen|lutfen|oder)?\\s*$", "").trim();
                    t = translate(t);
                    if (t.isEmpty()) continue;
                    // "extra pommes", "dazu ketchup", "mit extra käse" -> Beilage mit Preis
                    String tx = t.replaceAll("(?i)^(mit|und)\\s+", "");
                    Matcher em = EXTRA.matcher(tx);
                    if (em.find()) {
                        for (String part : em.group(2).split("(?i)\\s*(,|\\bund\\b|\\+)\\s*")) {
                            String ex = pretty(part, repl);
                            if (!ex.isEmpty()) it.extras.add(ex);
                        }
                    } else {
                        it.notes.add(cap(t));
                    }
                }
            }
            it.name = pretty(product, repl);
            if (!it.name.isEmpty()) r.items.add(it);
            for (String ex : pendingExtra) {
                Item e = new Item();
                e.qty = "1";
                e.name = pretty(ex, repl);
                if (!e.name.isEmpty()) r.items.add(e);
            }
            pendingExtra.clear();
        }
    }

    private static void addHint(String text, Result r) {
        String t = text.replaceAll("[\\s,;.!?:+\\-]+", " ").trim();
        if (t.isEmpty()) return;
        boolean onlyFiller = true;
        for (String w : t.toLowerCase(Locale.GERMAN).split(" ")) {
            if (!FILLER.contains(w) && !w.matches("lieferung|abholung|adresse|pizza|bestellung|bestellen|möchte|hätte|gerne|gern|ich|wir|bitte|für|mich|uns|istiyorum|sipariş|siparis|ver|verir|misin|misiniz|eve|adrese|adresime|adresim|adres|adresimiz|lütfen|lutfen|tane|wien|schwechat|simmering|mannswörth|mannswoerth|rannersdorf|kaiserebersdorf|kledering")) {
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
            if (size != null && repl.containsKey("pizza " + q)) result = repl.get("pizza " + q);
            else result = lookup(q, repl);
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

    /** Straßennamen mit bekannter Liste korrigieren und PLZ ergänzen. */
    static String correctStreet(String a) {
        Matcher sm = Pattern.compile("^(.*?)\\s+(\\d.*)$").matcher(a);
        if (!sm.find()) return a;
        Streets.Street st = Streets.match(sm.group(1));
        if (st == null) return a;
        String out = st.name + " " + sm.group(2);
        if (!out.matches(".*,\\s*\\d{4}.*") && !st.plz.isEmpty()) out += ", " + st.plz;
        return out;
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

    private static final String[][] TR = {
            {"soğansız", "ohne Zwiebel"}, {"sogansiz", "ohne Zwiebel"}, {"salatasız", "ohne Salat"}, {"salatasiz", "ohne Salat"},
            {"domatessiz", "ohne Tomaten"}, {"sossuz", "ohne Sauce"}, {"acısız", "nicht scharf"}, {"acisiz", "nicht scharf"},
            {"acılı", "scharf"}, {"acili", "scharf"}, {"bol sos", "viel Sauce"}, {"az sos", "wenig Sauce"}, {"soslu", "mit Sauce"},
            {"sadece", "nur"}, {"salata", "Salat"}, {"soğan", "Zwiebel"}, {"domates", "Tomaten"}, {"beyaz sos", "Mildsauce"},
            {"acı sos", "scharfe Sauce"}, {"sos", "Sauce"}, {"ekstra", "extra"}, {"ikiye bölün", "halbieren"}, {"ikiye böl", "halbieren"},
            {"ikiye kesin", "halbieren"}, {"ikiye kes", "halbieren"}, {"ortadan kesin", "halbieren"}, {"ortadan kes", "halbieren"},
            {"ayrı", "separat"}, {"ayri", "separat"}, {"bol", "viel"}, {"az", "wenig"}, {"lütfen", ""}, {"lutfen", ""},
            {"salt", "Salat"}, {"zwiebl", "Zwiebel"}, {"zwibel", "Zwiebel"}, {"sose", "Sauce"}, {"soße", "Sauce"}, {"sosse", "Sauce"}};

    /** Türkische und falsch geschriebene Sonderwünsche auf Deutsch. */
    static String translate(String t) {
        String out = " " + t + " ";
        for (String[] p : TR) {
            out = Pattern.compile("(?<![\\wäöüßçğış])" + Pattern.quote(p[0]) + "(?![\\wäöüßçğış])", CI)
                    .matcher(out).replaceAll(Matcher.quoteReplacement(p[1]));
        }
        return out.replaceAll("\\s+", " ").trim();
    }

    static String norm(String s) {
        return s.toLowerCase(Locale.GERMAN).replaceAll("[,.;:!?()\"]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String wordToNum(String w) {
        switch (w.toLowerCase(Locale.GERMAN)) {
            case "a": return "1";
            case "zwei": case "zweimal": case "zwa": case "zwoa": case "iki": return "2";
            case "drei": case "dreimal": case "üç": case "uc": return "3";
            case "vier": case "dört": case "dort": return "4";
            case "fünf": case "beş": case "bes": return "5";
            case "sechs": case "altı": case "alti": return "6";
            case "sieben": case "yedi": return "7";
            case "acht": case "sekiz": return "8";
            case "neun": case "dokuz": return "9";
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
