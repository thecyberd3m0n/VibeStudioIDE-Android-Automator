package dev.vibestudio.automator.hud;
import android.content.Context; import android.content.Intent; import android.graphics.*; import android.os.Handler; import android.os.Looper;
import android.view.*; import android.widget.*;
import dev.vibestudio.automator.driver.VibeAccessibilityService;

/** User-only agent bubble: own accessibility-overlay window added AFTER the HUD frame, so it is drawn above the frame.
 *  Drag to move, tap to show/hide STOP. Never part of an app window, so dump/screenshot/agentBounds never see it. */
public final class BubbleOverlay {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static FrameLayout root; private static WindowManager.LayoutParams lp; private static int lastX = -1, lastY = -1;
    private BubbleOverlay() {}

    public static void show(final Context c0) { H.post(() -> {
        if (root != null) return;
        try {
            VibeAccessibilityService s = VibeAccessibilityService.instance; final Context c = s != null ? s : c0;
            final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            final float d = c.getResources().getDisplayMetrics().density;
            final FrameLayout f = new FrameLayout(c); final LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
            final View dot = new View(c) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas cv) { float w = getWidth(), r = w / 2;
                    p.setColor(0xFF121212); cv.drawCircle(r, r, r, p); p.setColor(0xFF1ECFBA); cv.drawCircle(r, r, r * 0.72f, p); p.setColor(0xFF121212); cv.drawCircle(r * 1.25f, r * 1.25f, r * 0.3f, p); }
            };
            col.addView(dot, new LinearLayout.LayoutParams((int) (52 * d), (int) (52 * d)));
            final TextView stop = new TextView(c); stop.setText("STOP"); stop.setTextColor(Color.WHITE); stop.setTypeface(Typeface.DEFAULT_BOLD); stop.setGravity(Gravity.CENTER);
            stop.setPadding((int) (14 * d), (int) (6 * d), (int) (14 * d), (int) (6 * d)); stop.setVisibility(View.GONE);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(0xFFD32F2F); bg.setCornerRadius(20 * d); stop.setBackground(bg);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2); sl.topMargin = (int) (4 * d); col.addView(stop, sl);
            stop.setOnClickListener(v -> c.sendBroadcast(new Intent(c, BubbleActionReceiver.class).setAction(BubbleActionReceiver.ACTION_STOP).setPackage(c.getPackageName())));
            f.addView(col);
            lp = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START; lp.x = lastX >= 0 ? lastX : (int) (8 * d); lp.y = lastY >= 0 ? lastY : (int) (160 * d);
            dot.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy; int sx, sy; boolean moved;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false; return true;
                        case MotionEvent.ACTION_MOVE: if (Math.abs(e.getRawX() - dx) > 8 * d || Math.abs(e.getRawY() - dy) > 8 * d) moved = true;
                            if (moved) { lp.x = sx + (int) (e.getRawX() - dx); lp.y = sy + (int) (e.getRawY() - dy); lastX = lp.x; lastY = lp.y; try { wm.updateViewLayout(f, lp); } catch (Throwable ignored) {} } return true;
                        case MotionEvent.ACTION_UP: if (!moved) stop.setVisibility(stop.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); return true;
                    }
                    return false;
                }
            });
            wm.addView(f, lp); root = f; // added after the frame => above it
        } catch (Throwable ignored) { root = null; }
    }); }
    public static void hide() { H.post(() -> {
        if (root == null) return;
        try { ((WindowManager) root.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(root); } catch (Throwable ignored) {}
        root = null; }); }
}
