package dev.vibestudio.automator.hud;
import android.content.Context; import android.graphics.*; import android.view.*; import android.widget.TextView;
public final class AgentHudOverlay {
    private static View view;
    public static void show(Context c) {
        if (view != null) return;
        try {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            TextView v = new TextView(c); v.setText("STOP"); v.setTextColor(Color.WHITE); v.setBackgroundColor(0xFFCC2222); v.setPadding(32, 12, 32, 12);
            v.setOnClickListener(x -> c.sendBroadcast(new android.content.Intent(c, BubbleActionReceiver.class).setAction(BubbleActionReceiver.ACTION_STOP)));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; wm.addView(v, lp); view = v;
        } catch (Throwable ignored) {}
    }
    public static void hide() {
        View v = view; view = null;
        if (v != null) try { ((WindowManager) v.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Throwable ignored) {}
    }
}
