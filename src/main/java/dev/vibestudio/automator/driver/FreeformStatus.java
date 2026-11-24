package dev.vibestudio.automator.driver;
import android.content.Context; import android.provider.Settings;

/** Whether floating (freeform) windows are available. Checked once at Automator start; informational only, behaviour is unchanged. */
public final class FreeformStatus {
    private static volatile boolean on; private static volatile String detail = "not checked";
    private FreeformStatus() {}
    public static void check(Context c) {
        boolean feature = false, setting = false, forceResizable = false;
        try { feature = c.getPackageManager().hasSystemFeature("android.software.freeform_window_management"); } catch (Throwable ignored) {}
        try { setting = Settings.Global.getInt(c.getContentResolver(), "enable_freeform_support", 0) == 1; } catch (Throwable ignored) {}
        try { forceResizable = Settings.Global.getInt(c.getContentResolver(), "force_resizable_activities", 0) == 1; } catch (Throwable ignored) {}
        on = feature || setting;
        detail = "feature=" + feature + ", enable_freeform_support=" + setting + ", force_resizable=" + forceResizable;
    }
    public static boolean isOn() { return on; }
    public static String detail() { return detail; }
}
