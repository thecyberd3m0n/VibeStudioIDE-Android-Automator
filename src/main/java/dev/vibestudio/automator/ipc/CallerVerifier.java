package dev.vibestudio.automator.ipc;
import android.content.Context; import android.os.Binder; import android.os.Process;
import java.util.*;
public final class CallerVerifier {
    public static final Set<String> IDE_PACKAGES = new HashSet<>(Arrays.asList(
        "com.vibestudio.dev","com.vibestudio.app","com.vibestudio.app.debug","dev.vibestudio.ide","dev.vibestudio.ide.debug"));
    public static void check(Context c) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return;
        String[] pk = c.getPackageManager().getPackagesForUid(uid);
        if (pk != null) for (String p : pk) if (IDE_PACKAGES.contains(p)) return;
        throw new SecurityException("Caller not allowed: uid " + uid);
    }
}
