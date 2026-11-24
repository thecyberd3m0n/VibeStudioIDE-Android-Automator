package dev.vibestudio.automator.ipc;
import android.content.Context; import android.content.pm.*; import android.os.Binder; import android.os.Process;
import java.security.MessageDigest; import java.util.*;
/** Allows only known IDE packages whose signing certificate SHA-256 is trusted (release + debug keys). */
public final class CallerVerifier {
    public static final Set<String> IDE_PACKAGES = new HashSet<>(Arrays.asList(
        "com.vibestudio.dev","com.vibestudio.app"));
    public static final Set<String> TRUSTED_CERTS = new HashSet<>(Arrays.asList(
        "8e7b9ac41d31427d9555550a447dcea65e755fbf2142f6ca322233a0d8ff7ab3",   // release IDE
        "990d5c69b5ce75454e9dc33e520f3d2f5fc0a687ea1c11a2d248f6fc0507b444")); // debug.keystore
    public static void check(Context c) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return;
        PackageManager pm = c.getPackageManager();
        String[] pk = pm.getPackagesForUid(uid);
        if (pk != null) for (String p : pk) if (IDE_PACKAGES.contains(p) && certOk(pm, p)) return;
        throw new SecurityException("Caller not allowed: uid " + uid);
    }
    @SuppressWarnings("deprecation")
    private static boolean certOk(PackageManager pm, String pkg) {
        try {
            Signature[] sigs;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                SigningInfo si = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                sigs = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            } else sigs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
            if (sigs == null) return false;
            for (Signature s : sigs) {
                byte[] d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray());
                StringBuilder sb = new StringBuilder(); for (byte b : d) sb.append(String.format("%02x", b));
                if (TRUSTED_CERTS.contains(sb.toString())) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
