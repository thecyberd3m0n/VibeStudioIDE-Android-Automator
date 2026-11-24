package dev.vibestudio.automator.driver;
import android.app.NotificationManager; import android.content.*; import android.os.Build; import android.provider.Settings;

/** On-demand permission setup. Auto-enables via Shizuku (shell) when available; otherwise opens the relevant system screen. */
public final class PermissionHelper {
    private PermissionHelper() {}
    static final String PKG = "dev.vibestudio.automator";
    static final String A11Y = PKG + "/" + PKG + ".driver.VibeAccessibilityService";
    private static volatile long lastPrompt;

    public static boolean a11yEnabled() { return VibeAccessibilityService.instance != null; }

    /** Try to get Accessibility running. Blocking (call off the main thread). Returns true if the service is connected. */
    public static boolean ensureAccessibility(Context c) {
        if (a11yEnabled()) return true;
        try {
            if (ShellDriver.shizukuAvailable()) {
                ShellDriver.run("pm", "grant", PKG, "android.permission.WRITE_SECURE_SETTINGS");
                String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (cur == null || cur.equals("null")) cur = "";
                if (!cur.contains(A11Y)) cur = cur.isEmpty() ? A11Y : cur + ":" + A11Y;
                ShellDriver.run("settings", "put", "secure", "enabled_accessibility_services", cur);
                ShellDriver.run("settings", "put", "secure", "accessibility_enabled", "1");
                for (int i = 0; i < 30 && !a11yEnabled(); i++) Thread.sleep(100);
            } else if (Build.VERSION.SDK_INT >= 23 && c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0) {
                String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (cur == null) cur = "";
                if (!cur.contains(A11Y)) Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cur.isEmpty() ? A11Y : cur + ":" + A11Y);
                Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                for (int i = 0; i < 30 && !a11yEnabled(); i++) Thread.sleep(100);
            }
        } catch (Throwable ignored) {}
        if (!a11yEnabled()) openSettings(c, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        return a11yEnabled();
    }

    /** Notifications + bubbles for the agent bubble. Non-blocking best effort. */
    public static void ensureNotifications(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.areNotificationsEnabled() && bubblesOk(nm)) return;
            if (ShellDriver.shizukuAvailable()) {
                ShellDriver.run("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS");
                ShellDriver.run("cmd", "appops", "set", PKG, "POST_NOTIFICATION", "allow");
                ShellDriver.run("settings", "put", "global", "notification_bubbles", "1");
                ShellDriver.run("cmd", "notification", "allow_bubbles", PKG, "true");
            } else if (!nm.areNotificationsEnabled()) {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, PKG);
                openSettings(c, i);
            } else if (Build.VERSION.SDK_INT >= 29) {
                openSettings(c, new Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, PKG));
            }
        } catch (Throwable ignored) {}
    }
    private static boolean bubblesOk(NotificationManager nm) {
        try { return Build.VERSION.SDK_INT < 29 || nm.areBubblesAllowed(); } catch (Throwable t) { return true; }
    }
    /** Rate-limited (once / 30 s) so a looping agent does not spam the user with settings screens. */
    private static void openSettings(Context c, Intent i) {
        long now = System.currentTimeMillis(); if (now - lastPrompt < 30000) return; lastPrompt = now;
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) {}
    }
}
