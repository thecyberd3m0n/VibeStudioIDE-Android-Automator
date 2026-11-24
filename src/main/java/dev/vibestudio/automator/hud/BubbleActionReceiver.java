package dev.vibestudio.automator.hud;
import android.content.*;
public class BubbleActionReceiver extends BroadcastReceiver {
    public static final String ACTION_STOP = "dev.vibestudio.automator.STOP";
    @Override public void onReceive(Context c, Intent i) {
        // same effect as STOP in the IDE chat: abort the running loop, reject further calls until resume()
        if (!dev.vibestudio.automator.ipc.AutomatorBoundService.stopFromUser()) { AgentHudOverlay.hide(); BubbleController.hide(c); }
    }
}
