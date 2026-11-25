package dev.vibestudio.automator.driver;

/** Session contract between the LLM and the Automator: the LLM declares a descriptor (package), the Automator binds that app's window.
 *  All coordinates, the UI tree and screenshots are relative to that window only (0,0 = its top-left corner). */
public final class AgentSession {
    private AgentSession() {}
    private static volatile String pkg; private static volatile boolean full, installing;
    public static String pkg() { return pkg; }
    public static boolean isFull() { return full; }
    public static boolean isActive() { return full || pkg != null; }
    public static void begin(String p) { pkg = p; full = false; }
    public static void beginFull() { pkg = null; full = true; }
    public static void end() { pkg = null; full = false; installing = false; }
    /** While the system installer is on screen the agent must neither see nor read the screen. */
    public static boolean installing() { return installing; }
    public static void setInstalling(boolean v) { installing = v; installerSeen = false; installerLeftAt = 0; }
    private static volatile boolean installerSeen; private static volatile long installerLeftAt;
    /** Fed by accessibility window events: focus went to the system installer / came back to another app (user acted). */
    public static void onFocusPackage(String p) {
        if (!installing || p == null) return;
        boolean inst = p.contains("packageinstaller") || p.contains("permissioncontroller");
        if (inst) { installerSeen = true; installerLeftAt = 0; }
        else if (installerSeen && installerLeftAt == 0 && !p.equals("android") && !p.contains("systemui")) installerLeftAt = System.currentTimeMillis();
    }
    /** ms since focus returned from the installer to another app; -1 while the installer still has focus. */
    public static long installerLeftMs() { return installerLeftAt == 0 ? -1 : System.currentTimeMillis() - installerLeftAt; }
}
