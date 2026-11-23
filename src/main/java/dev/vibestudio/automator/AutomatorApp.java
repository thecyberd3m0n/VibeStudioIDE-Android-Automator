package dev.vibestudio.automator;
import android.app.Application;
public class AutomatorApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                android.content.Intent i = new android.content.Intent(this, CrashActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("err", android.util.Log.getStackTraceString(e));
                startActivity(i);
            } catch (Throwable ignored) {}
            if (old != null) old.uncaughtException(t, e);
        });
    }
}
