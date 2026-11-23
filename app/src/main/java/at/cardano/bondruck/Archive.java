package at.cardano.bondruck;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Archiv aller Bestellungen (letzte 90 Tage, max. 2000). Gespeichert als JSON Datei im App Speicher. */
final class Archive {
    static final class Entry {
        String id, chat, type, address, items;
        long first, printedAt;
        int sum;
        final List<String> msgs = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
    }

    private static final long KEEP_MS = 90L * 24 * 60 * 60 * 1000;
    private static final int MAX = 2000;
    private static List<Entry> list;

    private Archive() {}

    /** Bestellung (neu oder aktualisiert) ins Archiv übernehmen. */
    static void put(Context c, String chat, long first, long printedAt, List<String> msgs, List<Long> times) {
        try {
            Parser.Result r = Parser.parse(new ArrayList<>(msgs), Parser.replacements(Prefs.repl(c)));
            Parser.merge(r);
            if (r.address == null && !"ABHOLUNG".equals(r.type)) r.address = OrderStore.knownAddress(c, chat);
            Map<String, Integer> pm = Prices.load(c);
            StringBuilder it = new StringBuilder();
            for (Parser.Item i : r.items) {
                if (it.length() > 0) it.append('\n');
                it.append(i.qty).append("x ").append(i.name);
                for (String e : i.extras) it.append(" + ").append(e);
                for (String n : i.notes) it.append(" (").append(n).append(')');
            }
            store(c, chat, first, printedAt, msgs, times, r, it.toString(), Prices.total(pm, r.items, Prefs.pfandCents(c))[0]);
        } catch (Exception ignored) { }
    }

    /** Nur hier wird das Archiv gesperrt; keine Aufrufe anderer Speicher darin (keine Verklemmung). */
    private static synchronized void store(Context c, String chat, long first, long printedAt, List<String> msgs,
                                           List<Long> times, Parser.Result r, String items, int sum) {
        load(c);
        try {
            String id = chat + "|" + first;
            Entry e = null;
            for (Entry x : list) if (x.id.equals(id)) { e = x; break; }
            if (e == null) { e = new Entry(); e.id = id; list.add(0, e); }
            e.chat = chat;
            e.first = first;
            if (printedAt > 0) e.printedAt = printedAt;
            e.type = r.type == null ? "" : r.type;
            e.address = r.address == null ? "" : r.address;
            e.items = items;
            e.sum = sum;
            e.msgs.clear(); e.msgs.addAll(msgs);
            e.times.clear(); e.times.addAll(times);
            Collections.sort(list, (a, b) -> Long.compare(b.first, a.first));
            long cut = System.currentTimeMillis() - KEEP_MS;
            while (!list.isEmpty() && (list.size() > MAX || list.get(list.size() - 1).first < cut)) list.remove(list.size() - 1);
            save(c);
        } catch (Exception ignored) { }
    }

    static void put(Context c, OrderStore.Order o) {
        if (o == null || o.msgs.isEmpty()) return;
        put(c, o.chat, o.first, o.printedAt, new ArrayList<>(o.msgs), new ArrayList<>(o.times));
    }

    static synchronized List<Entry> all(Context c) {
        load(c);
        return new ArrayList<>(list);
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "archiv.json"); }

    private static void load(Context c) {
        if (list != null) return;
        list = new ArrayList<>();
        try {
            File f = file(c);
            if (!f.exists()) return;
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            JSONArray arr = new JSONArray(new String(b, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                Entry e = new Entry();
                e.id = j.getString("id");
                e.chat = j.optString("chat");
                e.type = j.optString("type");
                e.address = j.optString("address");
                e.items = j.optString("items");
                e.first = j.optLong("first");
                e.printedAt = j.optLong("printedAt");
                e.sum = j.optInt("sum");
                JSONArray m = j.optJSONArray("msgs"), t = j.optJSONArray("times");
                if (m != null) for (int k = 0; k < m.length(); k++) { e.msgs.add(m.getString(k)); e.times.add(t != null ? t.optLong(k) : e.first); }
                list.add(e);
            }
        } catch (Exception ignored) { }
    }

    private static void save(Context c) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : list) {
                JSONObject j = new JSONObject();
                j.put("id", e.id); j.put("chat", e.chat); j.put("type", e.type); j.put("address", e.address);
                j.put("items", e.items); j.put("first", e.first); j.put("printedAt", e.printedAt); j.put("sum", e.sum);
                j.put("msgs", new JSONArray(e.msgs)); j.put("times", new JSONArray(e.times));
                arr.put(j);
            }
            File tmp = new File(c.getFilesDir(), "archiv.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(arr.toString().getBytes("UTF-8")); }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file(c));
        } catch (Exception ignored) { }
    }
}
