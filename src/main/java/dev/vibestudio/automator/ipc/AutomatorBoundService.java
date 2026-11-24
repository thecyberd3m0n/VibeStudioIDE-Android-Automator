package dev.vibestudio.automator.ipc;
import android.app.PendingIntent; import android.app.Service; import android.content.*; import android.content.pm.PackageInstaller; import android.net.Uri; import android.os.*; import java.io.*;

import dev.vibestudio.automator.driver.*; import dev.vibestudio.automator.hud.AgentHudOverlay;
import org.json.*; import java.util.*; import java.util.concurrent.*;
public class AutomatorBoundService extends Service {
    public static final int ERR_PAUSED = 10, ERR_STOPPED = 15, ERR_GENERIC = 1;
    private volatile String state = "idle"; private volatile boolean paused, stopped;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final List<IAutomatorLogListener> listeners = new CopyOnWriteArrayList<>();
    private final LinkedList<String> logs = new LinkedList<>();

    private void log(String tool, String msg) {
        try { JSONObject o = new JSONObject(); o.put("ts", System.currentTimeMillis()); o.put("tool", tool); o.put("msg", msg);
            String s = o.toString(); synchronized (logs) { logs.add(s); if (logs.size() > 200) logs.removeFirst(); }
            for (IAutomatorLogListener l : listeners) try { l.onLog(s); } catch (Throwable t) { listeners.remove(l); }
        } catch (JSONException ignored) {}
    }
    private final IAutomatorService.Stub binder = new IAutomatorService.Stub() {
        public String getDriverStatus() {
            CallerVerifier.check(AutomatorBoundService.this);
            try { JSONObject o = new JSONObject();
                o.put("accessibility", VibeAccessibilityService.instance != null);
                o.put("shizuku", ShellDriver.shizukuAvailable());
                o.put("fullDevice", ScopeGuard.fullDevice(AutomatorBoundService.this));
                o.put("freeform", dev.vibestudio.automator.driver.FreeformStatus.isOn()); o.put("version", "0.2.1"); return o.toString(); } catch (JSONException e) { return "{}"; }
        }
        public void executeTool(final String tool, final String args, final IAutomatorCallback cb) {
            CallerVerifier.check(AutomatorBoundService.this);
            ex.execute(() -> run(tool, args, cb));
        }

        public void installApk(final android.os.ParcelFileDescriptor apk, final long size, final IAutomatorCallback cb) {
            CallerVerifier.check(AutomatorBoundService.this);
            ex.execute(() -> {
                try {
                    if (stopped) { cb.onError(ERR_STOPPED, "Stopped by user"); return; }
                    if (paused) { cb.onError(ERR_PAUSED, "Paused by user"); return; }
                    PermissionHelper.ensureNotifications(AutomatorBoundService.this);
                    main.post(() -> { AgentHudOverlay.show(AutomatorBoundService.this); dev.vibestudio.automator.hud.BubbleController.show(AutomatorBoundService.this); });
                    state = "running:install_apk"; log("install_apk", "fd size=" + size);
                    String res = install(new android.os.ParcelFileDescriptor.AutoCloseInputStream(apk), size);
                    state = "idle"; cb.onSuccess(res);
                } catch (Throwable t) {
                    state = "idle"; log("install_apk", "error: " + t);
                    try { cb.onError(ERR_GENERIC, String.valueOf(t.getMessage() != null ? t.getMessage() : t)); } catch (Throwable ignored) {}
                }
            });
        }

        public void emergencyStop() { CallerVerifier.check(AutomatorBoundService.this); doStop("emergency stop"); }
        public void pause() { CallerVerifier.check(AutomatorBoundService.this); paused = true; state = "paused"; }
        public void resume() { CallerVerifier.check(AutomatorBoundService.this); paused = false; stopped = false; state = "idle"; }
        public String getState() { CallerVerifier.check(AutomatorBoundService.this); return state; }
        public void registerLogListener(IAutomatorLogListener l) { CallerVerifier.check(AutomatorBoundService.this); if (l != null) listeners.add(l); }
        public void unregisterLogListener(IAutomatorLogListener l) { CallerVerifier.check(AutomatorBoundService.this); if (l != null) for (IAutomatorLogListener x : listeners) if (x.asBinder() == l.asBinder()) listeners.remove(x); }
        public String getLogs() { CallerVerifier.check(AutomatorBoundService.this); synchronized (logs) { return new JSONArray(logs).toString(); } }
    };
    @Override public IBinder onBind(Intent i) { return binder; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private static volatile AutomatorBoundService inst;
    @Override public void onCreate() { super.onCreate(); inst = this; }
    /** STOP from the bubble/HUD: returns false if the service is not running (caller just hides UI). */
    public static boolean stopFromUser() { AutomatorBoundService s = inst; if (s == null) return false; s.doStop("stopped by user (bubble)"); return true; }
    private void doStop(String why) { stopped = true; state = "stopped"; main.removeCallbacks(hideFrame); main.post(hideFrame); log("stop", why); }
    private final Runnable hideFrame = () -> { AgentHudOverlay.hide(); dev.vibestudio.automator.hud.BubbleController.hide(this); };
    private static String nodeId(Object o) { String s = String.valueOf(o).trim(); return s.matches("\\d+") ? "n" + s : s; }
    private static boolean needsA11y(String tool) {
        String t = tool.replaceFirst("^android_", "");
        return !(t.equals("logcat") || t.equals("force_stop") || t.equals("shell_install") || t.equals("install_apk") || t.equals("launch_app"));
    }
    private void run(String tool, String argsJson, IAutomatorCallback cb) {
        try {
            String bare = tool.replaceFirst("^android_", "");
            if (bare.equals("end_session")) { main.post(hideFrame); state = "idle"; cb.onSuccess(ok(true)); return; }
            if (stopped) { cb.onError(ERR_STOPPED, "Stopped by user"); return; }
            if (paused) { cb.onError(ERR_PAUSED, "Paused by user"); return; }
            PermissionHelper.ensureNotifications(this);
            if (needsA11y(tool) && !PermissionHelper.ensureAccessibility(this)) throw new IllegalStateException("PERMISSION_REQUIRED: Accessibility service not enabled - Settings was opened for the user. Finish the task now (task_complete) and ask the user to enable it manually.");
            // frame + bubble stay up for the whole session; only end_session / STOP / client unbind hide them
            main.post(() -> { AgentHudOverlay.show(this); dev.vibestudio.automator.hud.BubbleController.show(this); });

            state = "running:" + tool; log(tool, argsJson);
            String res = dispatch(bare, new JSONObject(argsJson == null || argsJson.isEmpty() ? "{}" : argsJson));
            state = "idle"; cb.onSuccess(res);
        } catch (Throwable t) {
            state = "idle"; log(tool, "error: " + t);
            try { cb.onError(ERR_GENERIC, String.valueOf(t.getMessage() != null ? t.getMessage() : t)); } catch (Throwable ignored) {}
        }
    }
    @Override public boolean onUnbind(Intent i) { main.post(hideFrame); return super.onUnbind(i); }


    private VibeAccessibilityService a11y() {
        VibeAccessibilityService s = VibeAccessibilityService.instance;
        if (s == null) throw new IllegalStateException("Accessibility service not enabled");
        return s;
    }
    /** PackageInstaller session fed from a stream (APK handed over via Binder fd, no shared path needed). The system confirm dialog is opened and its button is clicked via Accessibility when available. */
    private String install(InputStream apk, long size) throws Exception {
        final String action = "dev.vibestudio.automator.INSTALL_RESULT." + System.nanoTime();
        final CountDownLatch done = new CountDownLatch(1); final String[] res = new String[1];
        BroadcastReceiver rcv = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(confirm); }
                new Thread(() -> { for (int k = 0; k < 60 && done.getCount() > 0; k++) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                    VibeAccessibilityService a = VibeAccessibilityService.instance;
                    if (a != null && a.clickInstallConfirm()) return; } }).start();
                return; }
            res[0] = "{\"ok\":" + (st == PackageInstaller.STATUS_SUCCESS) + ",\"status\":" + st + ",\"message\":" + JSONObject.quote(String.valueOf(i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))) + "}";
            done.countDown(); } };
        registerReceiver(rcv, new IntentFilter(action));
        try {
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            int id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
            PackageInstaller.Session s = pi.openSession(id);
            try (InputStream in = apk; OutputStream out = s.openWrite("base.apk", 0, size > 0 ? size : -1)) {
                byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n); s.fsync(out); }
            s.commit(PendingIntent.getBroadcast(this, id, new Intent(action).setPackage(getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 1 << 25 /* FLAG_MUTABLE */ : 0)).getIntentSender());
            s.close();
            if (!done.await(120, TimeUnit.SECONDS)) return "{\"ok\":false,\"message\":\"Timed out waiting for install confirmation (approve the system dialog)\"}";
            return res[0];
        } finally { unregisterReceiver(rcv); }
    }

    private static String ok(boolean b) { return "{\"ok\":" + b + "}"; }
    private String dispatch(String t, JSONObject a) throws Exception {
        switch (t) {
            case "dump_ui_tree": return a11y().dumpTree(a.optBoolean("compact", true));
            case "tap_node": return ok(a11y().tapNode(nodeId(a.has("id") ? a.get("id") : a.get("nodeId"))));
            case "tap": case "tap_coordinates": { double sf = a.optDouble("scaleFactor", 1.0);
                float x = (float) (a.getDouble("x") * sf), y = (float) (a.getDouble("y") * sf); return ok(a11y().windowGesture(x, y, x, y, 50)); }
            case "swipe": { double sf = a.optDouble("scaleFactor", 1.0);
                return ok(a11y().windowGesture((float)(a.getDouble("x1")*sf), (float)(a.getDouble("y1")*sf), (float)(a.getDouble("x2")*sf), (float)(a.getDouble("y2")*sf), a.optLong("durationMs", 300))); }
            case "input_text": return ok(a11y().inputText(a.has("id") ? nodeId(a.get("id")) : null, a.getString("text")));
            case "global_action": return ok(a11y().global(a.getString("action")));
            case "launch_app": { String p = a.getString("package");
                if (!ScopeGuard.isAllowed(this, p)) throw new SecurityException("Package not in scope: " + p);
                Intent i = getPackageManager().getLaunchIntentForPackage(p); if (i == null) throw new IllegalArgumentException("No launcher for " + p);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                String q = a.optString("quarter", "");
                if (q.isEmpty()) { startActivity(i); return ok(true); }
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int w = dm.widthPixels, h = dm.heightPixels, hw = w / 2, hh = h / 2;
                android.graphics.Rect r;
                switch (q) {
                    case "top_left": r = new android.graphics.Rect(0, 0, hw, hh); break;
                    case "top_right": r = new android.graphics.Rect(hw, 0, w, hh); break;
                    case "bottom_left": r = new android.graphics.Rect(0, hh, hw, h); break;
                    case "bottom_right": r = new android.graphics.Rect(hw, hh, w, h); break;
                    default: throw new IllegalArgumentException("Bad quarter: " + q);
                }
                android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                o.setLaunchBounds(r);
                // setLaunchWindowingMode is a hidden API: use meta-reflection to bypass hidden-API restrictions (no Shizuku).
                String modeErr = null;
                try {
                    java.lang.reflect.Method getDecl = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
                    java.lang.reflect.Method m = (java.lang.reflect.Method) getDecl.invoke(android.app.ActivityOptions.class, "setLaunchWindowingMode", new Class[]{int.class});
                    m.setAccessible(true);
                    m.invoke(o, 5); // WINDOWING_MODE_FREEFORM
                } catch (Throwable ex2) { modeErr = String.valueOf(ex2.getCause() != null ? ex2.getCause() : ex2); }

                if (modeErr != null) throw new IllegalStateException("Cannot set freeform windowing mode: " + modeErr);
                startActivity(i, o.toBundle()); return ok(true); }

            case "screenshot": case "capture_screenshot": { JSONObject o = new JSONObject(); o.put("jpegBase64", a11y().screenshot()); return o.toString(); }
            case "logcat": {
java.util.List<String> c = new java.util.ArrayList<>(java.util.Arrays.asList("logcat", "-d", "-t", String.valueOf(a.optInt("lines", 200))));
String pkg = a.optString("package", "");
if (!pkg.isEmpty()) {
if (!ScopeGuard.isAllowed(this, pkg)) throw new SecurityException("Package not in scope: " + pkg);
String pid = ShellDriver.run("pidof", pkg).trim().split("\\s+")[0];
if (pid.isEmpty()) throw new IllegalStateException("Process not running: " + pkg);
c.add("--pid=" + pid);
}
String lvl = a.optString("level", "");
if (lvl.matches("[VDIWEF]")) c.add("*:" + lvl);
return ShellDriver.run(c.toArray(new String[0])); }
            case "force_stop": { String p = a.getString("package");
                if (!ScopeGuard.isAllowed(this, p)) throw new SecurityException("Package not in scope: " + p);
                if (ShellDriver.shizukuAvailable()) return ShellDriver.run("am", "force-stop", p);
                ((android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(p);
                return ok(true); }

            default: throw new IllegalArgumentException("Unknown tool: " + t);
        }
    }
    @Override public void onDestroy() { ex.shutdownNow(); super.onDestroy(); }
}