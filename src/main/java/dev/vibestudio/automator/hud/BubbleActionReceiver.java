package dev.vibestudio.automator.hud;
import android.content.*;
public class BubbleActionReceiver extends BroadcastReceiver {
    public static final String ACTION_STOP = "dev.vibestudio.automator.STOP";
    @Override public void onReceive(Context c, Intent i) { AgentHudOverlay.hide(); }
}
