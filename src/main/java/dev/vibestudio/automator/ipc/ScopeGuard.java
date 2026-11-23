package dev.vibestudio.automator.ipc;
import android.content.Context; import android.content.SharedPreferences; import java.util.*;
public final class ScopeGuard {
    public static final String[] DEFAULTS = {"com.vibestudio.dev","com.vibestudio.app","com.vibestudio.app.debug","dev.vibestudio.ide","dev.vibestudio.ide.debug","dev.vibestudio.automator","com.google.android.packageinstaller","com.android.packageinstaller"};
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("scope", 0); }
    public static boolean fullDevice(Context c) { return sp(c).getBoolean("full", false); }
    public static void setFullDevice(Context c, boolean v) { sp(c).edit().putBoolean("full", v).apply(); }
    public static Set<String> allowed(Context c) {
        Set<String> s = new HashSet<>(Arrays.asList(DEFAULTS)); s.addAll(sp(c).getStringSet("extra", new HashSet<String>())); return s;
    }
    public static void addPackage(Context c, String p) {
        Set<String> e = new HashSet<>(sp(c).getStringSet("extra", new HashSet<String>())); e.add(p); sp(c).edit().putStringSet("extra", e).apply();
    }
    public static boolean isAllowed(Context c, String pkg) { return fullDevice(c) || pkg == null || allowed(c).contains(pkg); }
}
