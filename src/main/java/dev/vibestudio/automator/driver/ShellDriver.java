package dev.vibestudio.automator.driver;
import java.io.*; import java.lang.reflect.Method;
/** Runs shell commands through Shizuku (reflection on Shizuku.newProcess), falling back to a plain process. */
public final class ShellDriver {
    public static boolean shizukuAvailable() {
        try { return (Boolean) Class.forName("rikka.shizuku.Shizuku").getMethod("pingBinder").invoke(null)
            && (Integer) Class.forName("rikka.shizuku.Shizuku").getMethod("checkSelfPermission").invoke(null) == 0; }
        catch (Throwable t) { return false; }
    }
    public static String run(String... cmd) throws Exception {
        Process p;
        if (shizukuAvailable()) {
            Method m = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true); p = (Process) m.invoke(null, cmd, null, null);
        } else p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder(); BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String l; while ((l = r.readLine()) != null && sb.length() < 60000) sb.append(l).append('\n');
        p.waitFor(); return sb.toString();
    }
}
