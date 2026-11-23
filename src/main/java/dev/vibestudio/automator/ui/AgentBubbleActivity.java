package dev.vibestudio.automator.ui;
import android.app.Activity; import android.os.Bundle; import android.widget.TextView; import android.graphics.Color;
public class AgentBubbleActivity extends Activity {
    @Override protected void onCreate(Bundle b) { super.onCreate(b);
        TextView t = new TextView(this); t.setText("VibeStudio Agent"); t.setTextColor(0xFF1ECFBA); t.setBackgroundColor(0xFF121212); t.setPadding(32,32,32,32); setContentView(t); }
}
