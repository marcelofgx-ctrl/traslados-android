package uy.com.mapatrayectos.conductorv0;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Phase 2 UI navigation ONLY. Keeps the existing MapView in place and never
 * starts a second Activity, location service, reservation client or shift.
 * The overlay is not connected to bookings; actions change local DEMO state only.
 */
public final class ConductorV0Navigator {
    private final Activity activity;
    private final FrameLayout root;
    private final SwipeHost host;
    private final ConductorV0View conductor;
    private final ConductorV0DemoState demoState=new ConductorV0DemoState();
    private boolean open;
    private final TextView returnTab;
    private int oldStatusBarColor, oldNavigationBarColor, oldDecorFlags;

    public ConductorV0Navigator(Activity activity, FrameLayout root) {
        this.activity = activity;
        this.root = root;

        // Small right-edge gesture zone, never a floating action button.
        // Leaves the map and its controls untouched outside this narrow edge.
        View edge = new View(activity);
        edge.setBackgroundColor(Color.TRANSPARENT);
        edge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        edge.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX=event.getX(); downY=event.getY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(ConductorSwipePolicy.isOpenGesture(event.getX()-downX,
                                event.getY()-downY, dp(55))) {
                            // Never call backend or change journey availability.
                            boolean shift=activity.getSharedPreferences(
                                "tracking_state",Activity.MODE_PRIVATE)
                                .getBoolean("shift_active",false);
                            open(shift);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_MOVE:
                        return true;
                    default:
                        return true;
                }
            }
        });
        FrameLayout.LayoutParams edgeParams=new FrameLayout.LayoutParams(
                dp(28),FrameLayout.LayoutParams.MATCH_PARENT,Gravity.RIGHT);
        edgeParams.topMargin=dp(112);
        edgeParams.bottomMargin=dp(118);
        root.addView(edge,edgeParams);

        host=new SwipeHost(activity,this::close);
        conductor=new ConductorV0View(activity);
        // Demonstration only: never invoke reservations, RPCs, GPS or Supabase.
        conductor.setPreviewMode(true);
        conductor.setOnDemoReset(()->{
            demoState.resetExample();
            conductor.setDemoFeedback("Solicitud ficticia reiniciada.",false);
            refreshDemoUi();
        });
        conductor.setListener(new ConductorV0View.Listener() {
            @Override public void onAccept(String requestId) {
                decideDemo(requestId, ConductorV0DemoState.Decision.ACCEPTED);
            }
            @Override public void onReject(String requestId) {
                decideDemo(requestId, ConductorV0DemoState.Decision.REJECTED);
            }
        });
        refreshDemoUi();
        host.addView(conductor,new FrameLayout.LayoutParams(-1,-1));
        host.setVisibility(View.GONE);
        // Above the edge zone while open; hidden views never block MapView.
        // Raised above Mapa's elevated widgets without changing their state.
        host.setElevation(dp(64));
        root.addView(host,new FrameLayout.LayoutParams(-1,-1));
        returnTab = new TextView(activity);
        returnTab.setText("❮  Mapa");
        returnTab.setTextColor(Color.WHITE);
        returnTab.setTextSize(12);
        returnTab.setGravity(Gravity.CENTER);
        returnTab.setContentDescription("Volver al Mapa");
        returnTab.setClickable(true);
        returnTab.setBackgroundColor(Color.rgb(28,57,60));
        returnTab.setElevation(dp(68));
        returnTab.setOnClickListener(v -> close());
        FrameLayout.LayoutParams tabParams = new FrameLayout.LayoutParams(
                dp(76),dp(48),Gravity.LEFT|Gravity.CENTER_VERTICAL);
        host.addView(returnTab,tabParams);
    }

    public boolean isOpen() {return open;}

    public void open(boolean shiftActive) {
        if(open)return;
        open=true;
        conductor.setShiftActive(shiftActive);
        // Keep the local demo decision when the user visits Mapa and returns.
        refreshDemoUi();
        host.animate().cancel();
        host.bringToFront();
        host.setVisibility(View.VISIBLE);
        int width=root.getWidth()>0?root.getWidth():
                activity.getResources().getDisplayMetrics().widthPixels;
        host.setTranslationX(width);
        setConductorSystemBars(true);
        host.animate().translationX(0f).setDuration(190).start();
    }

    private void refreshDemoUi() {
        conductor.setCounters(demoState.newCount(),demoState.acceptedCount(),
                demoState.inJourneyCount());
        conductor.setPendingRequest(demoState.hasPending()
                ? new ConductorV0View.Request(ConductorV0DemoState.SAMPLE_ID,
                    "18 de Julio, Montevideo", "Aeropuerto de Carrasco",
                    "21 km", "30 min", "$ 840 UYU")
                : null);
        conductor.setDemoHistory(demoState.demoHistory());
    }

    private void decideDemo(String requestId,ConductorV0DemoState.Decision decision) {
        if(!demoState.decide(requestId,decision))return;
        boolean accepted=decision==ConductorV0DemoState.Decision.ACCEPTED;
        conductor.setDemoFeedback(accepted
            ? "Aceptada en demostración · NO enviada al pasajero"
            : "Rechazada en demostración · NO enviada al pasajero",accepted);
        refreshDemoUi();
        playFeedbackTone(accepted);
    }

    /** Respect the phone's notification volume and silent/vibrate mode. */
    private void playFeedbackTone(boolean accepted) {
        try {
            AudioManager audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
            if(audio==null || audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL
                    || audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)==0)return;
            ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,60);
            boolean started=tone.startTone(
                    accepted?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_NACK,200);
            if(!started){tone.release();return;}
            new Handler(Looper.getMainLooper()).postDelayed(tone::release,320);
        } catch(RuntimeException ignored) {
            // Lack of an audio output must never block the UI.
        }
    }

    public boolean handleBack() {
        if(!open)return false;
        close();
        return true;
    }

    public void close() {
        if(!open)return;
        open=false;
        host.animate().cancel();
        setConductorSystemBars(false);
        int width=root.getWidth()>0?root.getWidth():
                activity.getResources().getDisplayMetrics().widthPixels;
        host.animate().translationX(width).setDuration(190).withEndAction(()->{
            if(!open) {
                host.setVisibility(View.GONE);
                host.setTranslationX(0f);
            }
        }).start();
    }

    private void setConductorSystemBars(boolean enter) {
        View decor=activity.getWindow().getDecorView();
        if(enter) {
            oldStatusBarColor=activity.getWindow().getStatusBarColor();
            oldNavigationBarColor=activity.getWindow().getNavigationBarColor();
            oldDecorFlags=decor.getSystemUiVisibility();
            activity.getWindow().setStatusBarColor(Color.BLACK);
            activity.getWindow().setNavigationBarColor(Color.BLACK);
            decor.setSystemUiVisibility(oldDecorFlags
                & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            activity.getWindow().setStatusBarColor(oldStatusBarColor);
            activity.getWindow().setNavigationBarColor(oldNavigationBarColor);
            decor.setSystemUiVisibility(oldDecorFlags);
        }
    }

    private int dp(float value) {
        return (int)(activity.getResources().getDisplayMetrics().density*value+.5f);
    }

    private static final class SwipeHost extends FrameLayout {
        private final Runnable onClose;
        private float x0,y0;
        private boolean intercepting=false;

        SwipeHost(Activity activity,Runnable onClose) {
            super(activity);
            this.onClose=onClose;
            setClickable(true);
        }
        private int dp(float value) {
            return (int)(getResources().getDisplayMetrics().density*value+.5f);
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0=e.getX(); y0=e.getY(); intercepting=false;
                    return false; // Preserve vertical scroll and normal child clicks.
                case MotionEvent.ACTION_MOVE:
                    if(ConductorSwipePolicy.isCloseGesture(
                            e.getX()-x0,e.getY()-y0,dp(55))) {
                        intercepting=true;
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_UP) {
                boolean close=intercepting || ConductorSwipePolicy.isCloseGesture(
                    e.getX()-x0,e.getY()-y0,dp(55));
                intercepting=false;
                if(close)onClose.run();
            } else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) {
                intercepting=false;
            }
            return true;
        }
    }
}
