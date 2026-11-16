package com.smart.ringcut;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView checks, log;
    private NumberPicker sec;
    private Switch on;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** adb: am start -n com.smart.ringcut/.MainActivity --ei sec 30 [--ez on true] */
    private void applyExtras(Intent i) {
        SharedPreferences.Editor e = getSharedPreferences("p", MODE_PRIVATE).edit();
        if (i.hasExtra("sec")) e.putInt("sec", i.getIntExtra("sec", CutService.DEFAULT_SEC));
        if (i.hasExtra("on")) e.putBoolean("on", i.getBooleanExtra("on", true));
        e.commit();
        if (sec != null) sec.setValue(CutService.seconds(this));
        if (on != null) on.setChecked(CutService.enabled(this));
        CutService.log("settings: " + (CutService.enabled(this) ? "on" : "off") + ", " + CutService.seconds(this) + " s");
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.hasExtra("sec") || i.hasExtra("on")) applyExtras(i);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("p", MODE_PRIVATE);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 3, pad, pad);

        TextView about = new TextView(this);
        about.setTextSize(15);
        about.setText("Declines an incoming call that is still ringing after the time below. "
                + "Your network then treats it as busy, so \"forward when busy\" sends it on.\n\n"
                + "Turn off \"forward when unanswered\" (dial #61# in Google Phone), "
                + "or the network forwards first.\n");
        col.addView(about);

        on = new Switch(this);
        on.setText("Decline unanswered calls");
        on.setChecked(CutService.enabled(this));
        on.setOnCheckedChangeListener((v, c) -> p.edit().putBoolean("on", c).apply());
        on.setPadding(0, pad, 0, pad);
        col.addView(on);

        TextView label = new TextView(this);
        label.setTextSize(15);
        label.setText("Ring time (seconds). Keep it under your network's unanswered limit (about 30 s):");
        col.addView(label);

        sec = new NumberPicker(this);
        sec.setMinValue(CutService.MIN_SEC);
        sec.setMaxValue(CutService.MAX_SEC);
        sec.setValue(CutService.seconds(this));
        sec.setWrapSelectorWheel(false);
        sec.setOnValueChangedListener((v, o, n) -> p.edit().putInt("sec", n).apply());
        col.addView(sec);

        checks = new TextView(this);
        checks.setTextSize(15);
        checks.setPadding(0, pad, 0, 0);
        col.addView(checks);

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(12);
        col.addView(log);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);

        if (getIntent().hasExtra("sec") || getIntent().hasExtra("on")) applyExtras(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            checks.setText(CutService.bound
                    ? "✅ Receiving calls from Android\n"
                    : "⏳ Not seen a call yet (normal until your next call)\n");
            StringBuilder sb = new StringBuilder();
            synchronized (CutService.LOG) { for (String s : CutService.LOG) sb.append(s).append('\n'); }
            log.setText(sb);
            ui.postDelayed(this, 1000);
        }
    };
}
