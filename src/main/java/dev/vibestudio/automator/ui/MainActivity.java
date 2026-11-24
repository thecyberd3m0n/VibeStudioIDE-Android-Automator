package dev.vibestudio.automator.ui;
import android.app.Activity; import android.content.Intent; import android.os.Bundle; import android.provider.Settings;
import android.widget.*; import android.view.*; import android.graphics.Color;
import dev.vibestudio.automator.driver.*; import dev.vibestudio.automator.ipc.ScopeGuard; import dev.vibestudio.automator.hud.AgentHudOverlay;
public class MainActivity extends Activity {
    private TextView status; private LinearLayout listBox;
    private void refreshList() { listBox.removeAllViews();
        for (String p : ScopeGuard.list(this)) { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this); t.setText(p); t.setTextColor(Color.WHITE); row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            Button rm = btn("Remove", v -> { ScopeGuard.removePackage(this, p); refreshList(); }); row.addView(rm); listBox.addView(row); } }
    private Button btn(String t, View.OnClickListener l) { Button b = new Button(this); b.setText(t); b.setOnClickListener(l); b.setTextColor(0xFF1ECFBA); return b; }
    @Override protected void onCreate(Bundle s) { super.onCreate(s);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,64,32,32); root.setBackgroundColor(0xFF121212);
        TextView title = new TextView(this); title.setText("VibeStudio Automator"); title.setTextSize(22); title.setTextColor(0xFF1ECFBA); root.addView(title);
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setPadding(0,24,0,24); root.addView(status);
        root.addView(btn("Accessibility settings", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        Switch full = new Switch(this); full.setText("Full Device Mode"); full.setTextColor(Color.WHITE); full.setChecked(ScopeGuard.fullDevice(this));
        full.setOnCheckedChangeListener((b, c) -> ScopeGuard.setFullDevice(this, c)); root.addView(full);
        root.addView(btn("Test HUD", v -> { AgentHudOverlay.show(this); v.postDelayed(AgentHudOverlay::hide, 3000); }));
        root.addView(btn("Test screenshot", v -> new Thread(() -> { String m; try { m = "OK " + VibeAccessibilityService.instance.screenshot().length() + " b64"; } catch (Throwable t) { m = "Fail: " + t; }
            final String mm = m; runOnUiThread(() -> Toast.makeText(this, mm, Toast.LENGTH_LONG).show()); }).start()));
        TextView lt = new TextView(this); lt.setText("Allowed apps"); lt.setTextSize(18); lt.setTextColor(0xFF1ECFBA); lt.setPadding(0,32,0,8); root.addView(lt);
        listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL); root.addView(listBox);
        LinearLayout add = new LinearLayout(this); EditText in = new EditText(this); in.setHint("package name, e.g. com.example.app"); in.setTextColor(Color.WHITE); in.setHintTextColor(0xFF888888); in.setSingleLine(true);
        add.addView(in, new LinearLayout.LayoutParams(0, -2, 1));
        add.addView(btn("Add", v -> { if (ScopeGuard.addPackage(this, in.getText().toString())) { in.setText(""); refreshList(); } else Toast.makeText(this, "Invalid package name", Toast.LENGTH_SHORT).show(); }));
        root.addView(add); refreshList();
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(0xFF121212); sv.addView(root); setContentView(sv);
    }
    @Override protected void onResume() { super.onResume();
        status.setText("Accessibility: " + (VibeAccessibilityService.instance != null ? "ON" : "OFF") + "\nShizuku: " + (ShellDriver.shizukuAvailable() ? "ON" : "OFF")
            + "\nFloating windows: " + (FreeformStatus.isOn() ? "ON" : "OFF") + "\n(" + FreeformStatus.detail() + ")"); }
}