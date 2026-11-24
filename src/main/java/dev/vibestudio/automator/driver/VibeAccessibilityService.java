package dev.vibestudio.automator.driver;
import android.accessibilityservice.*; import android.graphics.*; import android.os.*;
import android.view.accessibility.*; import android.view.Display;
import org.json.*; import java.util.*; import java.util.concurrent.*;
public class VibeAccessibilityService extends AccessibilityService {
    public static volatile VibeAccessibilityService instance;
    private final Map<String, AccessibilityNodeInfo> nodes = new HashMap<>();
    @Override public void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }
    /** Clicks the confirm button of the system package installer dialog; false if it is not on screen. */
    public boolean clickInstallConfirm() {
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        String pkg = String.valueOf(root.getPackageName());
        if (!pkg.contains("packageinstaller") && !pkg.contains("permissioncontroller")) return false;
        for (String label : new String[]{"Install", "Update", "Zainstaluj", "Aktualizuj", "Install anyway", "Zainstaluj mimo to"})
            for (android.view.accessibility.AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(label))
                if (n.isClickable() && n.isEnabled() && n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true;
        return false;
    }


    /** The one window the agent controls (bounds, UI tree and screenshot all use it): an allowed app window, never the Automator's own; the active one wins, else the focused one. */
    private AccessibilityWindowInfo agentWindow() {
        AccessibilityWindowInfo best = null;
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo r = w.getRoot(); if (r == null) continue;
                CharSequence pkg = r.getPackageName();
                if (pkg == null || pkg.toString().equals(getPackageName()) || !dev.vibestudio.automator.ipc.ScopeGuard.isAllowed(this, pkg.toString())) continue;
                if (best == null || w.isActive() || (w.isFocused() && !best.isActive())) best = w;
            }
        } catch (Throwable t) {}
        return best;
    }

    /** Bounds of the window the agent controls; whole display in Full Device Mode; null if none. */
    public Rect agentBounds() {
        try {
            if (dev.vibestudio.automator.ipc.ScopeGuard.fullDevice(this) && dev.vibestudio.automator.driver.ShellDriver.shizukuAvailable()) {
                android.graphics.Point p = new android.graphics.Point();
                ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p);
                return new Rect(0, 0, p.x, p.y);
            }
            AccessibilityWindowInfo w = agentWindow(); if (w == null) return null;
            Rect out = new Rect(); w.getBoundsInScreen(out); return out;
        } catch (Throwable t) { return null; }
    }

    private AccessibilityNodeInfo agentRoot() {
        AccessibilityWindowInfo w = agentWindow(); AccessibilityNodeInfo r = w != null ? w.getRoot() : null;
        if (r != null) return r;
        r = getRootInActiveWindow();
        return r != null && !getPackageName().contentEquals(String.valueOf(r.getPackageName())) ? r : null;
    }

    public synchronized String dumpTree(boolean compact) throws JSONException {
        nodes.clear();
        AccessibilityNodeInfo root = agentRoot();
        if (root == null) throw new IllegalStateException("No active window");
        Rect wb = agentBounds(); JSONObject o = walk(root, compact, new int[]{0, wb == null ? 0 : wb.left, wb == null ? 0 : wb.top});
        return o.toString();
    }
    private JSONObject walk(AccessibilityNodeInfo n, boolean compact, int[] ctr) throws JSONException {
        String id = "n" + (++ctr[0]); nodes.put(id, n);
        JSONObject o = new JSONObject(); o.put("id", id);
        if (n.getClassName() != null) o.put("class", n.getClassName().toString());
        if (n.getText() != null) o.put("text", n.getText().toString());
        if (n.getContentDescription() != null) o.put("desc", n.getContentDescription().toString());
        if (n.getViewIdResourceName() != null) o.put("res", n.getViewIdResourceName());
        if (n.getPackageName() != null) o.put("pkg", n.getPackageName().toString());
        Rect r = new Rect(); n.getBoundsInScreen(r); r.offset(-ctr[1], -ctr[2]); o.put("bounds", r.flattenToString());
        if (n.isClickable()) o.put("clickable", true);
        if (n.isEditable()) o.put("editable", true);
        JSONArray ch = new JSONArray();
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i); if (c == null) continue;
            if (compact && !c.isVisibleToUser()) continue;
            ch.put(walk(c, compact, ctr));
        }
        if (ch.length() > 0) o.put("children", ch);
        boolean bare = !o.has("text") && !o.has("desc") && !o.has("res") && !o.has("clickable") && !o.has("editable");
        return compact && bare && ch.length() == 1 ? ch.getJSONObject(0) : o; // compact: drop empty single-child wrappers

    }
    public synchronized boolean tapNode(String id) {
        AccessibilityNodeInfo n = nodes.get(id); if (n == null) return false;
        for (AccessibilityNodeInfo p = n; p != null; p = p.getParent())
            if (p.isClickable() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        Rect r = new Rect(); n.getBoundsInScreen(r); return gesture(r.centerX(), r.centerY(), r.centerX(), r.centerY(), 50);
    }
    public synchronized boolean inputText(String id, String text) {
        AccessibilityNodeInfo n = id != null ? nodes.get(id) : findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (n == null) return false;
        Bundle b = new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }
    /** Gesture in window coordinates (same as screenshot and UI-tree bounds): the window origin is added here. */
    public boolean windowGesture(float x1, float y1, float x2, float y2, long dur) {
        Rect b = agentBounds(); float ox = b == null ? 0 : b.left, oy = b == null ? 0 : b.top;
        return gesture(x1 + ox, y1 + oy, x2 + ox, y2 + oy, dur);
    }
    public boolean gesture(float x1, float y1, float x2, float y2, long dur) {
        dev.vibestudio.automator.hud.BubbleOverlay.setPassThrough(true);
        try { return gesture0(x1, y1, x2, y2, dur); } finally { dev.vibestudio.automator.hud.BubbleOverlay.setPassThrough(false); }
    }
    private boolean gesture0(float x1, float y1, float x2, float y2, long dur) {
        Path p = new Path(); p.moveTo(x1, y1); if (x1 != x2 || y1 != y2) p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(1, dur))).build();
        final CountDownLatch l = new CountDownLatch(1); final boolean[] ok = {false};
        boolean d = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription x) { ok[0] = true; l.countDown(); }
            @Override public void onCancelled(GestureDescription x) { l.countDown(); }
        }, new Handler(Looper.getMainLooper()));
        if (!d) return false;
        try { l.await(dur + 3000, TimeUnit.MILLISECONDS); } catch (InterruptedException e) {}
        return ok[0];
    }
    public boolean global(String a) {
        switch (a.toUpperCase()) {
            case "BACK": return performGlobalAction(GLOBAL_ACTION_BACK);
            case "HOME": return performGlobalAction(GLOBAL_ACTION_HOME);
            case "RECENTS": return performGlobalAction(GLOBAL_ACTION_RECENTS);
            case "NOTIFICATIONS": return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        }
        return false;
    }
    /** Screenshot of the agent's app window only (no HUD frame, no bubble). Android 14+ (takeScreenshotOfWindow, called via reflection). */
    /** Screenshot of the agent's app window only (no HUD frame, no bubble). Android 14+ (takeScreenshotOfWindow, called via reflection). */
    public String screenshot() throws Exception {
        if (Build.VERSION.SDK_INT < 34) throw new UnsupportedOperationException("screenshot needs Android 14+");
        AccessibilityWindowInfo aw = agentWindow(); int wid = aw == null ? -1 : aw.getId();
        if (wid == -1) throw new IllegalStateException("No allowed app window to capture");
        final CountDownLatch l = new CountDownLatch(1); final Bitmap[] bm = {null};
        TakeScreenshotCallback cb = new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult r) {
                Bitmap hw = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                bm[0] = hw.copy(Bitmap.Config.ARGB_8888, false); r.getHardwareBuffer().close(); l.countDown(); }
            @Override public void onFailure(int c) { l.countDown(); }
        };
        AccessibilityService.class.getMethod("takeScreenshotOfWindow", int.class, java.util.concurrent.Executor.class, TakeScreenshotCallback.class)
            .invoke(this, wid, getMainExecutor(), cb);
        l.await(5, TimeUnit.SECONDS);
        if (bm[0] == null) throw new IllegalStateException("screenshot failed");
        return ScreenCompressor.toBase64(bm[0], 64 * 1024);
    }
}