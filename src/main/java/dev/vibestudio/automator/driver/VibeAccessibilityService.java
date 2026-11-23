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

    public synchronized String dumpTree(boolean compact) throws JSONException {
        nodes.clear();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) throw new IllegalStateException("No active window");
        JSONObject o = walk(root, compact, new int[]{0});
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
        Rect r = new Rect(); n.getBoundsInScreen(r); o.put("bounds", r.flattenToString());
        if (n.isClickable()) o.put("clickable", true);
        if (n.isEditable()) o.put("editable", true);
        JSONArray ch = new JSONArray();
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i); if (c == null) continue;
            if (compact && !c.isVisibleToUser()) continue;
            ch.put(walk(c, compact, ctr));
        }
        if (ch.length() > 0) o.put("children", ch);
        return o;
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
    public boolean gesture(float x1, float y1, float x2, float y2, long dur) {
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
    public String screenshot() throws Exception {
        if (Build.VERSION.SDK_INT < 30) throw new UnsupportedOperationException("screenshot needs Android 11+");
        final CountDownLatch l = new CountDownLatch(1); final Bitmap[] bm = {null};
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult r) {
                Bitmap hw = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                bm[0] = hw.copy(Bitmap.Config.ARGB_8888, false); r.getHardwareBuffer().close(); l.countDown(); }
            @Override public void onFailure(int c) { l.countDown(); }
        });
        l.await(5, TimeUnit.SECONDS);
        if (bm[0] == null) throw new IllegalStateException("screenshot failed");
        return ScreenCompressor.toBase64(bm[0], 64 * 1024);
    }
}
