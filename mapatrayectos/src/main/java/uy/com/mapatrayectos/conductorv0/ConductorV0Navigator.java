package uy.com.mapatrayectos.conductorv0;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Phase 2 UI navigation ONLY. Keeps the existing MapView in place and never
 * starts a second Activity, location service, reservation client or shift.
 * Request state is served by the existing authenticated Conductor pipeline.
 */
public final class ConductorV0Navigator {
    private final Activity activity;
    private final FrameLayout root;
    private final SwipeHost host;
    private final ConductorLiveView conductor;
    private final ConductorLiveController live;
    private final DriverRideSheet rideSheet;
    private boolean open,hostResumed;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable rideTick=new Runnable(){
        @Override public void run(){
            if(!hostResumed)return;
            rideSheet.refreshFromSnapshot(live.isBusy());
            ui.postDelayed(this,3500L);
        }
    };
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
        conductor=new ConductorLiveView(activity);
        live=new ConductorLiveController(activity,conductor);
        rideSheet=new DriverRideSheet(activity,new DriverRideSheet.Listener(){
            @Override public void onVerifiedAction(String id,DriverSheetPolicy.Action action){
                // Reuse exactly the existing authorized conductor operations.
                switch(action){
                    case GO_TO_PICKUP:live.onPickupPhase(id,"EN_CAMINO");break;
                    case ARRIVED:live.onPickupPhase(id,"EN_PUNTO");break;
                    case START_TRIP:live.onStartTrip(id);break;
                    case FINISH_TRIP:live.onFinishTrip(id);break;
                    default:break;
                }
            }
            @Override public void onRoute(String destination){live.onRoute(destination);}
            @Override public void onDetails(){
                boolean shift=activity.getSharedPreferences("tracking_state",Activity.MODE_PRIVATE)
                    .getBoolean("shift_active",false);
                open(shift);
            }
        });
        // Draw over the SAME live map but below the old full conductor panel.
        // This does not replace or intercept native MapLibre gestures.
        FrameLayout.LayoutParams rideLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        rideLp.leftMargin=dp(8);rideLp.rightMargin=dp(8);rideLp.bottomMargin=dp(16);
        root.addView(rideSheet,rideLp);
        live.setOnConfirmedImmediateDecision((id,accepted)->{
            close(); // preserve legacy post-decision behavior
            if(accepted)rideSheet.focusConfirmed(id);
        });
        host.addView(conductor,new FrameLayout.LayoutParams(-1,-1));
        host.setVisibility(View.GONE);
        host.setElevation(dp(64));
        root.addView(host,new FrameLayout.LayoutParams(-1,-1));
        // Swipe right is the sole return action: no overlapping Mapa tab.
    }


    public boolean isOpen() {return open;}

    public void open(boolean shiftActive) {
        if(open)return;
        open=true;
        live.open(shiftActive);
        host.animate().cancel();
        host.bringToFront();
        host.setVisibility(View.VISIBLE);
        int width=root.getWidth()>0?root.getWidth():
                activity.getResources().getDisplayMetrics().widthPixels;
        host.setTranslationX(width);
        setConductorSystemBars(true);
        host.animate().translationX(0f).setDuration(190).start();
    }

    public void onHostResume(){
        hostResumed=true;
        live.onHostResume();
        ui.removeCallbacks(rideTick);
        ui.post(rideTick);
    }
    public void onHostPause(){
        hostResumed=false;ui.removeCallbacks(rideTick);live.onHostPause();
    }
    public void onReservationSnapshotUpdated(){rideSheet.refreshFromSnapshot(live.isBusy());}
    public void updateBottomInset(int px){rideSheet.updateInsets(px);}
    public void dispose(){hostResumed=false;ui.removeCallbacks(rideTick);live.dispose();}

    public boolean handleBack() {
        if(!open)return false;
        close();
        return true;
    }

    public void close() {
        if(!open)return;
        open=false;
        live.close();
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
