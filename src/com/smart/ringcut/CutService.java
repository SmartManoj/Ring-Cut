package com.smart.ringcut;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Non-UI InCallService. Telecom binds it once the MANAGE_ONGOING_CALLS app-op is allowed.
 * An incoming call that is still ringing after N seconds is declined, which the network treats
 * as busy, so "forward when busy" sends it on (here: to the user's AI assistant).
 *
 * Why: Jio's "forward when unanswered" always fires after about 15 s and ignores the no-reply
 * timer set with **61*number**T# (measured 2026-11-16: 25 s stored, forwarded after 14.4 s).
 * Turn that forwarding off (#61#) so the phone keeps ringing until this app declines.
 */
public class CutService extends InCallService {
    static volatile boolean bound;
    static final ArrayDeque<String> LOG = new ArrayDeque<>();
    // Must stay under the network's own unanswered limit, or the call ends unforwarded. Jio (2026-11-16):
    // the network ended the ring after 29.7 s, so 30 s never declined; 29 s declined and was forwarded.
    static final int MIN_SEC = 5, MAX_SEC = 29, DEFAULT_SEC = 25;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<Call, Runnable> pending = new HashMap<>();

    static void log(String s) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        synchronized (LOG) {
            LOG.addFirst(t + "  " + s);
            while (LOG.size() > 60) LOG.removeLast();
        }
        android.util.Log.i("RingCut", s);
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("p", MODE_PRIVATE).getBoolean("on", true);
    }

    static int seconds(Context c) {
        int s = c.getSharedPreferences("p", MODE_PRIVATE).getInt("sec", DEFAULT_SEC);
        return Math.max(MIN_SEC, Math.min(MAX_SEC, s));
    }

    private final Call.Callback callback = new Call.Callback() {
        @Override public void onStateChanged(Call call, int state) {
            if (state == Call.STATE_RINGING) arm(call);
            else disarm(call, state == Call.STATE_ACTIVE ? "answered, not declined"
                    : state == Call.STATE_DISCONNECTED ? "ended before " + seconds(CutService.this) + " s" : null);
        }
    };

    @Override public void onCallAdded(Call call) {
        bound = true;
        call.registerCallback(callback);
        if (call.getDetails().getState() == Call.STATE_RINGING) arm(call);
    }

    @Override public void onCallRemoved(Call call) {
        call.unregisterCallback(callback);
        disarm(call, null);
    }

    private void arm(Call call) {
        if (pending.containsKey(call)) return;
        Call.Details d = call.getDetails();
        if (d.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;
        if (d.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)) return;
        if (!enabled(this)) {
            log("ringing (Ring Cut is off)");
            return;
        }
        int sec = seconds(this);
        Runnable cut = () -> {
            pending.remove(call);
            if (call.getDetails().getState() == Call.STATE_RINGING) {
                call.reject(false, null); // same as tapping Decline
                log("declined after " + sec + " s");
            }
        };
        pending.put(call, cut);
        h.postDelayed(cut, sec * 1000L);
        log("ringing, decline in " + sec + " s");
    }

    private void disarm(Call call, String why) {
        Runnable cut = pending.remove(call);
        if (cut == null) return;
        h.removeCallbacks(cut);
        if (why != null) log(why);
    }
}
