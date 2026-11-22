package at.cardano.bondruck;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Sammelt die Nachrichten je Kunde zu einer Bestellung und merkt sich Adressen. */
final class OrderStore {

    /** Nach so langer Pause beginnt eine neue Bestellung. */
    static final long ORDER_GAP_MS = 45 * 60 * 1000L;

    static final class Order {
        String chat;
        final List<String> msgs = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        long first, last, printedAt;
        int printedCount;     // Anzahl Nachrichten beim letzten Druck
    }

    private static final Map<String, Order> orders = new HashMap<>();
    private static final Map<String, String> addresses = new HashMap<>();
    private static final Map<String, String> phones = new HashMap<>();
    private static boolean loaded;
    private static final List<String> log = new ArrayList<>();

    /** Protokoll der letzten Ereignisse (nur im Speicher). */
    static synchronized void log(String line) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.GERMAN).format(new java.util.Date());
        log.add(0, t + "  " + line);
        while (log.size() > 40) log.remove(log.size() - 1);
    }

    static synchronized List<String> getLog() { return new ArrayList<>(log); }

    private OrderStore() {}

    static synchronized Order add(Context c, String chat, String text, long time) {
        load(c);
        Order o = orders.get(chat);
        long window = Prefs.windowMin(c) * 60 * 1000L;
        boolean startNew = o == null || time - o.first > window;   // alles im Zeitfenster = eine Bestellung
        if (startNew) {
            o = new Order();
            o.chat = chat;
            o.first = time;
            orders.put(chat, o);
        }
        o.msgs.add(text);
        o.times.add(time);
        o.last = Math.max(o.last, time);
        save(c);
        return o;
    }

    static boolean hasItems(Context c, String text) {
        List<String> one = new ArrayList<>();
        one.add(text);
        return !Parser.parse(one, Parser.replacements(Prefs.repl(c))).items.isEmpty();
    }

    static synchronized Order get(Context c, String chat) {
        load(c);
        return orders.get(chat);
    }

    /** Offene Bestellungen, neueste zuerst. */
    static synchronized List<Order> list(Context c) {
        load(c);
        List<Order> l = new ArrayList<>(orders.values());
        java.util.Collections.sort(l, (a, b) -> Long.compare(b.last, a.last));
        return l;
    }

    static synchronized void markPrinted(Context c, String chat) {
        load(c);
        Order o = orders.get(chat);
        if (o == null) return;
        o.printedAt = System.currentTimeMillis();
        o.printedCount = o.msgs.size();
        save(c);
    }

    static synchronized void remove(Context c, String chat) {
        load(c);
        orders.remove(chat);
        save(c);
    }

    static synchronized void rememberAddress(Context c, String chat, String address) {
        load(c);
        if (address == null || address.equals(addresses.get(chat))) return;
        addresses.put(chat, address);
        save(c);
    }

    static synchronized void rememberPhone(Context c, String chat, String phone) {
        load(c);
        if (phone == null || phone.equals(phones.get(chat))) return;
        phones.put(chat, phone);
        save(c);
    }

    static synchronized String knownPhone(Context c, String chat) {
        load(c);
        return phones.get(chat);
    }

    static synchronized String knownAddress(Context c, String chat) {
        load(c);
        return addresses.get(chat);
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("orders", Context.MODE_PRIVATE);
    }

    private static void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject root = new JSONObject(sp(c).getString("data", "{}"));
            JSONArray arr = root.optJSONArray("orders");
            long now = System.currentTimeMillis();
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                Order o = new Order();
                o.chat = j.getString("chat");
                o.first = j.getLong("first");
                o.last = j.getLong("last");
                o.printedAt = j.optLong("printedAt");
                o.printedCount = j.optInt("printedCount");
                JSONArray m = j.getJSONArray("msgs");
                JSONArray t = j.getJSONArray("times");
                for (int k = 0; k < m.length(); k++) { o.msgs.add(m.getString(k)); o.times.add(t.getLong(k)); }
                if (now - o.last < ORDER_GAP_MS * 4) orders.put(o.chat, o);
            }
            JSONObject ph = root.optJSONObject("phones");
            if (ph != null) {
                Iterator<String> keys = ph.keys();
                while (keys.hasNext()) { String k = keys.next(); phones.put(k, ph.getString(k)); }
            }
            JSONObject a = root.optJSONObject("addresses");
            if (a != null) {
                Iterator<String> keys = a.keys();
                while (keys.hasNext()) { String k = keys.next(); addresses.put(k, a.getString(k)); }
            }
        } catch (Exception ignored) { }
    }

    private static void save(Context c) {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Order o : orders.values()) {
                JSONObject j = new JSONObject();
                j.put("chat", o.chat);
                j.put("first", o.first);
                j.put("last", o.last);
                j.put("printedAt", o.printedAt);
                j.put("printedCount", o.printedCount);
                j.put("msgs", new JSONArray(o.msgs));
                j.put("times", new JSONArray(o.times));
                arr.put(j);
            }
            root.put("orders", arr);
            root.put("addresses", new JSONObject(addresses));
            root.put("phones", new JSONObject(phones));
            sp(c).edit().putString("data", root.toString()).apply();
        } catch (Exception ignored) { }
    }
}
