package dev.vibestudio.automator.ipc;
import android.app.Service; import android.content.*; import android.os.*;
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
                o.put("version", "0.2"); return o.toString(); } catch (JSONException e) { return "{}"; }
        }
        public void executeTool(final String tool, final String args, final IAutomatorCallback cb) {
            CallerVerifier.check(AutomatorBoundService.this);
            ex.execute(() -> run(tool, args, cb));
        }
        public void emergencyStop() { CallerVerifier.check(AutomatorBoundService.this); stopped = true; state = "stopped"; AgentHudOverlay.hide(); log("stop", "emergency stop"); }
        public void pause() { CallerVerifier.check(AutomatorBoundService.this); paused = true; state = "paused"; }
        public void resume() { CallerVerifier.check(AutomatorBoundService.this); paused = false; stopped = false; state = "idle"; }
        public String getState() { CallerVerifier.check(AutomatorBoundService.this); return state; }
        public void registerLogListener(IAutomatorLogListener l) { CallerVerifier.check(AutomatorBoundService.this); if (l != null) listeners.add(l); }
        public void unregisterLogListener(IAutomatorLogListener l) { CallerVerifier.check(AutomatorBoundService.this); if (l != null) for (IAutomatorLogListener x : listeners) if (x.asBinder() == l.asBinder()) listeners.remove(x); }
        public String getLogs() { CallerVerifier.check(AutomatorBoundService.this); synchronized (logs) { return new JSONArray(logs).toString(); } }
    };
    @Override public IBinder onBind(Intent i) { return binder; }

    private void run(String tool, String argsJson, IAutomatorCallback cb) {
        try {
            if (stopped) { cb.onError(ERR_STOPPED, "Stopped by user"); return; }
            if (paused) { cb.onError(ERR_PAUSED, "Paused by user"); return; }
            state = "running:" + tool; log(tool, argsJson);
            String res = dispatch(tool.replaceFirst("^android_", ""), new JSONObject(argsJson == null || argsJson.isEmpty() ? "{}" : argsJson));
            state = "idle"; cb.onSuccess(res);
        } catch (Throwable t) {
            state = "idle"; log(tool, "error: " + t);
            try { cb.onError(ERR_GENERIC, String.valueOf(t.getMessage() != null ? t.getMessage() : t)); } catch (Throwable ignored) {}
        }
    }
    private VibeAccessibilityService a11y() {
        VibeAccessibilityService s = VibeAccessibilityService.instance;
        if (s == null) throw new IllegalStateException("Accessibility service not enabled");
        return s;
    }
    private static String ok(boolean b) { return "{\"ok\":" + b + "}"; }
    private String dispatch(String t, JSONObject a) throws Exception {
        switch (t) {
            case "dump_ui_tree": return a11y().dumpTree(a.optBoolean("compact", true));
            case "tap_node": return ok(a11y().tapNode(a.has("id") ? a.getString("id") : a.getString("nodeId")));
            case "tap": case "tap_coordinates": { double sf = a.optDouble("scaleFactor", 1.0);
                float x = (float) (a.getDouble("x") * sf), y = (float) (a.getDouble("y") * sf); return ok(a11y().gesture(x, y, x, y, 50)); }
            case "swipe": { double sf = a.optDouble("scaleFactor", 1.0);
                return ok(a11y().gesture((float)(a.getDouble("x1")*sf), (float)(a.getDouble("y1")*sf), (float)(a.getDouble("x2")*sf), (float)(a.getDouble("y2")*sf), a.optLong("durationMs", 300))); }
            case "input_text": return ok(a11y().inputText(a.has("id") ? a.getString("id") : null, a.getString("text")));
            case "global_action": return ok(a11y().global(a.getString("action")));
            case "launch_app": { String p = a.getString("package");
                if (!ScopeGuard.isAllowed(this, p)) throw new SecurityException("Package not in scope: " + p);
                Intent i = getPackageManager().getLaunchIntentForPackage(p); if (i == null) throw new IllegalArgumentException("No launcher for " + p);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return ok(true); }
            case "screenshot": case "capture_screenshot": { JSONObject o = new JSONObject(); o.put("jpegBase64", a11y().screenshot()); return o.toString(); }
            case "logcat": return ShellDriver.run("logcat", "-d", "-t", String.valueOf(a.optInt("lines", 200)));
            case "force_stop": { String p = a.getString("package");
                if (!ScopeGuard.isAllowed(this, p)) throw new SecurityException("Package not in scope: " + p);
                return ShellDriver.run("am", "force-stop", p); }
            case "shell_install": return ShellDriver.run("pm", "install", "-r", a.getString("path"));
            default: throw new IllegalArgumentException("Unknown tool: " + t);
        }
    }
    @Override public void onDestroy() { ex.shutdownNow(); super.onDestroy(); }
}
