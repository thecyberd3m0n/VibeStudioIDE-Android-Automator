package dev.vibestudio.automator.driver;
import android.graphics.Bitmap; import android.util.Base64; import java.io.ByteArrayOutputStream;
/** Compresses a screenshot so that what the IDE receives (base64, JSON-escaped, plus wrapper) fits in {@code limit} bytes. */
public final class ScreenCompressor {
    private static final int WRAPPER_MARGIN = 256;
    private static final int[] QUALITIES = {70, 50, 35};
    public static String toBase64(Bitmap src, int limit) {
        int budget = limit - WRAPPER_MARGIN; float scale = 1f; String last = "";
        while (true) {
            int w = Math.max(1, (int) (src.getWidth() * scale)), h = Math.max(1, (int) (src.getHeight() * scale));
            Bitmap b = scale == 1f ? src : Bitmap.createScaledBitmap(src, w, h, true);
            try {
                for (int q : QUALITIES) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG, q, bo);
                    last = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    int escaped = last.length(); for (int i = 0; i < last.length(); i++) if (last.charAt(i) == '/') escaped++;
                    if (escaped <= budget) return last;
                }
            } finally { if (b != src) b.recycle(); }
            if (w == 1 && h == 1) return last;
            scale *= 0.8f;
        }
    }
}
