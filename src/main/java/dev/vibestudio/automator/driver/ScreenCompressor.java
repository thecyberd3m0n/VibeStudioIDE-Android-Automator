package dev.vibestudio.automator.driver;
import android.graphics.Bitmap; import android.util.Base64; import java.io.ByteArrayOutputStream;
public final class ScreenCompressor {
    public static String toBase64(Bitmap src, int limit) {
        float scale = 1f; int q = 70; byte[] out;
        while (true) {
            Bitmap b = scale == 1f ? src : Bitmap.createScaledBitmap(src, Math.max(1,(int)(src.getWidth()*scale)), Math.max(1,(int)(src.getHeight()*scale)), true);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG, q, bo); out = bo.toByteArray();
            if (out.length <= limit || scale < 0.1f) break;
            if (q > 40) q -= 15; else scale *= 0.75f;
        }
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }
}
