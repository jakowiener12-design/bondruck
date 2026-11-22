package at.cardano.bondruck;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liest neue eingehende Nachrichten im geöffneten WhatsApp Chat (dann gibt es keine Benachrichtigung).
 * Beim Öffnen eines Chats werden alle sichtbaren Nachrichten nur gemerkt. Neu ist nur, was unten dazukommt.
 */
public class ChatReader extends AccessibilityService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Set<String>> seen = new HashMap<>();
    private String currentChat;
    private boolean scanPending;

    private final Runnable scan = () -> {
        scanPending = false;
        try { scanNow(); } catch (Exception ignored) { }
    };

    @Override
    protected void onServiceConnected() {
        OrderStore.log("Chat Mitlesen aktiv");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (!pkg.startsWith("com.whatsapp")) return;
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) currentChat = null; // neuer Bildschirm
        if (!scanPending) {
            scanPending = true;
            handler.postDelayed(scan, 600);
        }
    }

    private void scanNow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();
        if (!pkg.startsWith("com.whatsapp")) return;

        String chat = firstText(root, pkg + ":id/conversation_contact_name");
        if (chat == null) { currentChat = null; return; }        // kein Chat offen
        chat = chat.replace("‪", "").replace("‬", "").trim();
        if (Prefs.ignored(this).contains(chat.toLowerCase(java.util.Locale.GERMAN))) return;

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(pkg + ":id/message_text");
        if (nodes == null || nodes.isEmpty()) return;

        List<String> texts = new ArrayList<>();
        List<Boolean> incoming = new ArrayList<>();
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence t = n.getText();
            if (t == null) continue;
            texts.add(t.toString().trim());
            incoming.add(!isOutgoing(n, pkg));
        }

        Set<String> known = seen.get(chat);
        if (known == null) { known = new LinkedHashSet<>(); seen.put(chat, known); }

        if (!chat.equals(currentChat)) {
            // Chat gerade geöffnet: alles Sichtbare ist alt
            currentChat = chat;
            known.addAll(texts);
            return;
        }

        // nur Nachrichten unterhalb der letzten bekannten sind neu (Hochscrollen ignorieren)
        int lastKnown = -1;
        for (int i = 0; i < texts.size(); i++) if (known.contains(texts.get(i))) lastKnown = i;
        for (int i = lastKnown + 1; i < texts.size(); i++) {
            String t = texts.get(i);
            known.add(t);
            if (incoming.get(i) && !t.isEmpty()) {
                WhatsAppListener.incoming(this, chat, t, System.currentTimeMillis(), " (Chat offen)");
            }
        }
        if (known.size() > 500) {
            List<String> keep = new ArrayList<>(known);
            known.clear();
            known.addAll(keep.subList(keep.size() - 200, keep.size()));
        }
    }

    /** Eigene Nachrichten haben ein Häkchen (status) in der Sprechblase. */
    private boolean isOutgoing(AccessibilityNodeInfo n, String pkg) {
        AccessibilityNodeInfo p = n;
        for (int i = 0; i < 3 && p != null; i++) {
            p = p.getParent();
            if (p == null) break;
            CharSequence cls = p.getClassName();
            if (cls != null && (cls.toString().endsWith("ListView") || cls.toString().endsWith("RecyclerView"))) break;
            List<AccessibilityNodeInfo> st = p.findAccessibilityNodeInfosByViewId(pkg + ":id/status");
            if (st != null && !st.isEmpty()) return true;
        }
        return false;
    }

    private static String firstText(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(id);
        if (l == null) return null;
        for (AccessibilityNodeInfo n : l) if (n.getText() != null && n.getText().length() > 0) return n.getText().toString();
        return null;
    }

    @Override
    public void onInterrupt() { }
}
