package dev.vibestudio.automator.ipc;
import android.content.Context; import android.content.SharedPreferences; import java.util.*;
public final class ScopeGuard {
    /** Always allowed, not editable: the Automator itself and the system package installer. */
    public static final String[] SYSTEM = {"dev.vibestudio.automator","com.google.android.packageinstaller","com.android.packageinstaller"};
    /** Seeded into the user-managed list on first run. */
    public static final String[] DEFAULTS = {"com.vibestudio.app","com.vibestudio.dev"};
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("scope", 0); }
    public static boolean fullDevice(Context c) { return sp(c).getBoolean("full", false); }
    public static void setFullDevice(Context c, boolean v) { sp(c).edit().putBoolean("full", v).apply(); }
    /** User-managed allowlist (sorted). Seeded with DEFAULTS (plus legacy "extra") on first read. */
    public static synchronized List<String> list(Context c) {
        SharedPreferences p = sp(c);
        Set<String> s;
        if (!p.contains("pkgs")) {
            s = new HashSet<>(Arrays.asList(DEFAULTS)); s.addAll(p.getStringSet("extra", new HashSet<String>()));
            p.edit().putStringSet("pkgs", s).apply();
        } else s = p.getStringSet("pkgs", new HashSet<String>());
        List<String> l = new ArrayList<>(s); Collections.sort(l); return l;
    }
    public static Set<String> allowed(Context c) { Set<String> s = new HashSet<>(Arrays.asList(SYSTEM)); s.addAll(list(c)); return s; }
    public static synchronized boolean addPackage(Context c, String pkg) {
        if (pkg == null) return false; pkg = pkg.trim(); if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) return false;
        Set<String> s = new HashSet<>(list(c)); s.add(pkg); sp(c).edit().putStringSet("pkgs", s).apply(); return true;
    }
    public static synchronized void removePackage(Context c, String pkg) {
        Set<String> s = new HashSet<>(list(c)); s.remove(pkg); sp(c).edit().putStringSet("pkgs", s).apply();
    }
    public static boolean isAllowed(Context c, String pkg) { return fullDevice(c) || pkg == null || allowed(c).contains(pkg); }
}
