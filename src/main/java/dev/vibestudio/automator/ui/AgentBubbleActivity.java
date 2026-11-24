package dev.vibestudio.automator.ui;
import android.app.Activity; import android.content.Intent; import android.os.Bundle; import android.view.Gravity;
import android.widget.*; import android.graphics.Color;
import dev.vibestudio.automator.hud.BubbleActionReceiver;

/** Expanded bubble content. TODO: placeholder - replace with the real agent chat/status view. */
public class AgentBubbleActivity extends Activity {
    @Override protected void onCreate(Bundle b) { super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF121212); root.setPadding(40, 40, 40, 40);
        TextView t = new TextView(this); t.setText("VibeStudio Agent"); t.setTextColor(0xFF1ECFBA); t.setTextSize(18);
        TextView s = new TextView(this); s.setText("(placeholder) Agent chat / status will appear here."); s.setTextColor(0xFFAAAAAA); s.setPadding(0, 16, 0, 32);
        Button stop = new Button(this); stop.setText("■  STOP"); stop.setTextColor(Color.WHITE); stop.setBackgroundColor(0xFFCC2222);
        stop.setOnClickListener(v -> sendBroadcast(new Intent(this, BubbleActionReceiver.class).setAction(BubbleActionReceiver.ACTION_STOP)));
        root.addView(t); root.addView(s); root.addView(stop, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root); }
}
