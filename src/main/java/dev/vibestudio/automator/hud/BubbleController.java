package dev.vibestudio.automator.hud;
import android.app.*; import android.content.*; import android.content.pm.ShortcutInfo; import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon; import android.os.Build; import java.util.Collections;
import dev.vibestudio.automator.ui.AgentBubbleActivity;

/** Messenger-style system bubble (Notification.BubbleMetadata) for the agent. Expanded bubble hosts AgentBubbleActivity (placeholder + STOP). */
public final class BubbleController {
    static final String CHANNEL = "agent_bubble", SHORTCUT = "agent_chat"; static final int ID = 4201;
    private BubbleController() {}

    public static void show(Context c) {
        BubbleOverlay.show(c);
        if (true) return; // system bubble replaced by BubbleOverlay (drawn above the HUD frame)
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Agent", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Agent control bubble"); if (Build.VERSION.SDK_INT >= 29) ch.setAllowBubbles(true);
                nm.createNotificationChannel(ch);
            }
            if (Build.VERSION.SDK_INT < 29) return; // bubbles need Android 10+
            Icon icon = Icon.createWithResource(c, dev.vibestudio.automator.R.drawable.ic_launcher);
            if (Build.VERSION.SDK_INT >= 30) {
                ShortcutInfo si = new ShortcutInfo.Builder(c, SHORTCUT).setShortLabel("Agent").setLongLived(true).setIcon(icon)
                    .setIntent(new Intent(c, AgentBubbleActivity.class).setAction(Intent.ACTION_VIEW)).build();
                ((ShortcutManager) c.getSystemService(Context.SHORTCUT_SERVICE)).setDynamicShortcuts(Collections.singletonList(si));
            }
            int mut = Build.VERSION.SDK_INT >= 31 ? 1 << 25 /* FLAG_MUTABLE */ : 0;
            PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, AgentBubbleActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | mut);
            Notification.BubbleMetadata.Builder bb = Build.VERSION.SDK_INT >= 30
                ? new Notification.BubbleMetadata.Builder(open, icon) : new Notification.BubbleMetadata.Builder();
            if (Build.VERSION.SDK_INT < 30) bb.setIntent(open).setIcon(icon);
            bb.setDesiredHeight(360).setSuppressNotification(true);
            android.app.Person p = new android.app.Person.Builder().setName("Agent").setIcon(icon).setImportant(true).build();
            Notification.Builder nb = new Notification.Builder(c, CHANNEL).setSmallIcon(icon).setContentTitle("Agent").setContentText("Agent is working")
                .setOngoing(true).setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(open)
                .setStyle(new Notification.MessagingStyle(p).addMessage("Agent is working", System.currentTimeMillis(), p))
                .setBubbleMetadata(bb.build());
            if (Build.VERSION.SDK_INT >= 30) nb.setShortcutId(SHORTCUT);
            nm.notify(ID, nb.build());
        } catch (Throwable ignored) {}
    }
    public static void hide(Context c) {
        BubbleOverlay.hide();
        try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID); } catch (Throwable ignored) {}
    }
}
