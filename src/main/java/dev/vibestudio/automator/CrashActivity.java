package dev.vibestudio.automator;
import android.app.Activity; import android.os.Bundle; import android.widget.*; import android.graphics.Color;
public class CrashActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this); TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE); tv.setPadding(24,24,24,24); tv.setTextSize(11);
        tv.setText("Automator crashed:\n\n" + getIntent().getStringExtra("err"));
        sv.setBackgroundColor(0xFF121212); sv.addView(tv); setContentView(sv);
    }
}
