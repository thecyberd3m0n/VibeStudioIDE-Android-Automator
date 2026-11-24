package dev.vibestudio.automator.hud;
import android.animation.ValueAnimator;
import android.content.Context; import android.graphics.*; import android.os.Handler; import android.os.Looper;
import android.view.*; import android.widget.TextView;
import dev.vibestudio.automator.driver.VibeAccessibilityService;

/** Animated rounded RGB frame (agent-controlled area) + STOP pill. Frame is visible only while the agent loop runs. */
public final class AgentHudOverlay {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static FrameView frame; private static View pill;
    private static final int[] COLORS = {0xFFFF0055, 0xFFFF8800, 0xFF00FF88, 0xFF0088FF, 0xFFAA00FF, 0xFFFF0055};

    static final class FrameView extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix(); private final RectF r = new RectF();
        private SweepGradient grad; private float angle, radius, w;
        private ValueAnimator anim; private int lw = -1, lh = -1;
        FrameView(Context c) {
            super(c); float d = c.getResources().getDisplayMetrics().density;
            w = 4 * d; radius = 22 * d;
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(w);
            glow.setStyle(Paint.Style.STROKE); glow.setStrokeWidth(w * 3); glow.setAlpha(70);
            glow.setMaskFilter(new BlurMaskFilter(w * 3, BlurMaskFilter.Blur.NORMAL));
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 360f); anim.setDuration(3000); anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new android.view.animation.LinearInterpolator());
            anim.addUpdateListener(a -> { angle = (Float) a.getAnimatedValue(); invalidate(); }); anim.start(); }
        @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }
        private final RectF cur = new RectF(), from = new RectF(), to = new RectF();
        private ValueAnimator move; boolean forceHidden, inScope = true;
        private final int[] loc = new int[2];
        void updateAlpha() { animate().alpha(forceHidden ? 0f : inScope ? 1f : 0.15f).setDuration(forceHidden ? 0 : 250).start(); }
        private float insetPx(String name, float def) {
            int id = getResources().getIdentifier(name, "dimen", "android");
            return id > 0 ? getResources().getDimensionPixelSize(id) : def;
        }

        /** Animate frame to the given screen bounds (null = out of scope: dim, keep last position). */
        void setTarget(Rect b) {
            boolean in = b != null; if (in != inScope) { inScope = in; updateAlpha(); }
            if (b == null) return;
            getLocationOnScreen(loc);
            RectF t = new RectF(b.left - loc[0], b.top - loc[1], b.right - loc[0], b.bottom - loc[1]);
            // fit the border exactly inside the overlay view and the app window
            float d = getResources().getDisplayMetrics().density;
            if (getWidth() > 0) { t.left = Math.max(t.left, 0); t.right = Math.min(t.right, getWidth()); }
            if (getHeight() > 0) { t.top = Math.max(t.top, 0); t.bottom = Math.min(t.bottom, getHeight()); }

            if (t.equals(to)) return;
            to.set(t);
            if (cur.isEmpty()) { cur.set(t); invalidate(); return; }
            if (move != null) move.cancel();
            from.set(cur);
            move = ValueAnimator.ofFloat(0f, 1f); move.setDuration(220);
            move.setInterpolator(new android.view.animation.DecelerateInterpolator());
            move.addUpdateListener(a -> { float f = (Float) a.getAnimatedValue();
                cur.set(from.left + (to.left - from.left) * f, from.top + (to.top - from.top) * f,
                        from.right + (to.right - from.right) * f, from.bottom + (to.bottom - from.bottom) * f); });
            move.start();
        }
        @Override protected void onDraw(Canvas cv) {
            int W = getWidth(), Hh = getHeight(); if (W == 0) return;
            if (cur.isEmpty()) cur.set(0, 0, W, Hh);
            if (grad == null) { grad = new SweepGradient(0, 0, COLORS, null); stroke.setShader(grad); glow.setShader(grad); }
            m.setRotate(angle, 0, 0); m.postTranslate(cur.centerX(), cur.centerY()); grad.setLocalMatrix(m);
            float rad = Math.min(radius, Math.min(cur.width(), cur.height()) / 4f);
            r.set(cur.left + w * 0.5f, cur.top + w * 0.5f, cur.right - w * 0.5f, cur.bottom - w * 0.5f);
            cv.drawRoundRect(r, rad, rad, glow); cv.drawRoundRect(r, rad, rad, stroke);
        }
    }


    private static Context ctx(Context c) { VibeAccessibilityService s = VibeAccessibilityService.instance; return s != null ? s : c; }

    /** Show frame (agent loop running) and STOP pill. Safe from any thread. */
    public static void show(final Context c) { H.post(() -> showNow(c)); }
    private static void showNow(Context c0) {
        if (frame != null) { frame.updateAlpha(); return; }
        try {
            Context c = ctx(c0); WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            FrameView f = new FrameView(c);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
            wm.addView(f, lp); frame = f;

            H.removeCallbacks(TRACK); H.post(TRACK);
        } catch (Throwable ignored) { frame = null; pill = null; }
    }
    /** Temporarily hide the frame (e.g. during screenshot capture). */
    public static void setVisible(final boolean vis) { H.post(() -> { if (frame != null) { frame.forceHidden = !vis; frame.updateAlpha(); } }); }
    /** Follows the controlled window (fullscreen / split / floating) while the HUD is shown. */
    private static final Runnable TRACK = new Runnable() { @Override public void run() {
        if (frame == null) return;
        VibeAccessibilityService s = VibeAccessibilityService.instance;
        if (s != null) {
            android.graphics.Rect b = s.agentBounds(); frame.setTarget(b);

        }
        H.postDelayed(this, 300);
    } };
    public static void hide() { H.post(() -> {
        H.removeCallbacks(TRACK);
        for (View v : new View[]{frame, pill}) if (v != null) try { ((WindowManager) v.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Throwable ignored) {}
        frame = null; pill = null; }); }
}