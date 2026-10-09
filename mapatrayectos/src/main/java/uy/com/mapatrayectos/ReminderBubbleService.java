package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;

/**
 * R23.0: a reminder is tethered to the REAL floating shortcut or not shown.
 * A persistent Android reminder notification is always available as fallback.
 */
public final class ReminderBubbleService extends Service {
    private static final int FOREGROUND_ID=23991;
    private static final long MAX_ANCHOR_AGE_MS=12000L;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private ReminderCallout bubble;
    private WindowManager.LayoutParams bubbleLp;
    private int anchorX,anchorY,anchorSize;
    private final Runnable timeout=this::stopSelf;

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

    private final BroadcastReceiver bubblePositionReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i==null||!TrackingService.ACTION_FLOATING_BUBBLE_POSITION.equals(i.getAction()))return;
            if(!i.getBooleanExtra("visible",false)){stopSelf();return;}
            anchorX=i.getIntExtra("x",anchorX);
            anchorY=i.getIntExtra("y",anchorY);
            anchorSize=i.getIntExtra("size",anchorSize);
            realign(); // Follow shortcut as it is being dragged.
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        IntentFilter f=new IntentFilter(TrackingService.ACTION_FLOATING_BUBBLE_POSITION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bubblePositionReceiver,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(bubblePositionReceiver,f);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        int id=intent==null?-1:intent.getIntExtra("id",-1);
        ReminderStore.Item item=ReminderStore.get(this,id);
        if(item==null||item.done||!Settings.canDrawOverlays(this)){
            stopSelf();return START_NOT_STICKY;
        }
        SharedPreferences state=getSharedPreferences("bubble_state",MODE_PRIVATE);
        long age=System.currentTimeMillis()-state.getLong("floating_seen_ms",0);
        if(state.getBoolean("ui_visible",false)
                ||!state.getBoolean("floating_visible",false)
                ||age<0||age>MAX_ANCHOR_AGE_MS){
            // Never use old/stale positions of a bubble that is not on screen.
            stopSelf();return START_NOT_STICKY;
        }
        anchorX=state.getInt("x",-1);
        anchorY=state.getInt("y",-1);
        anchorSize=state.getInt("floating_size",dp(52));
        if(anchorX<0||anchorY<0||anchorSize<=0){
            stopSelf();return START_NOT_STICKY;
        }
        try{createForeground();show(item);}
        catch(Exception e){
            Log.w("MapaReminders","Overlay unavailable; Android notification remains",e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void createForeground(){
        final String ch="mapa_reminder_overlay";
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null&&Build.VERSION.SDK_INT>=26)
            nm.createNotificationChannel(new NotificationChannel(ch,
                "Aviso visual temporal",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,ch)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Recordatorio visual").setContentText("Mostrando aviso")
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)
            startForeground(FOREGROUND_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(FOREGROUND_ID,n);
    }

    private ReminderAnchorGeometry.Placement position(ReminderCallout card){
        int width=dp(ReminderCallout.TOTAL_WIDTH_DP);
        int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        if(screenWidth<=0||screenHeight<=0)return null;
        card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenHeight,View.MeasureSpec.AT_MOST));
        return ReminderAnchorGeometry.place(anchorX,anchorY,anchorSize,
            screenWidth,screenHeight,width,card.getMeasuredHeight(),
            dp(ReminderCallout.TAIL_CENTER_DP),dp(ReminderCallout.TAIL_HEIGHT_DP)/2,dp(4));
    }

    private void show(ReminderStore.Item item){
        removeBubble();
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(manager==null)throw new IllegalStateException("WindowManager no disponible");
        ReminderCallout candidate=new ReminderCallout(this,item.text,
            ()->{ReminderStore.change(this,item.id,true,false,-1);stopSelf();},
            ()->{ReminderStore.change(this,item.id,false,false,System.currentTimeMillis()+600000L);stopSelf();},
            this::stopSelf);
        ReminderAnchorGeometry.Placement p=position(candidate);
        if(p==null){stopSelf();return;} // No orphaned overlay; notification survives.
        candidate.setAnchorPlacement(p.tailOnLeft,p.tailCenterY);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            dp(ReminderCallout.TOTAL_WIDTH_DP),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=p.x;lp.y=p.y;
        manager.addView(candidate,lp);
        bubble=candidate;bubbleLp=lp;
        bubble.reveal();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout,30000L);
    }

    private void realign(){
        if(bubble==null||manager==null||bubbleLp==null)return;
        ReminderAnchorGeometry.Placement p=position(bubble);
        if(p==null){stopSelf();return;}
        bubble.setAnchorPlacement(p.tailOnLeft,p.tailCenterY);
        if(bubbleLp.x!=p.x||bubbleLp.y!=p.y){
            bubbleLp.x=p.x;bubbleLp.y=p.y;
            try{manager.updateViewLayout(bubble,bubbleLp);}
            catch(Exception e){Log.w("MapaReminders","Lost floating anchor",e);stopSelf();}
        }
    }

    private void removeBubble(){
        handler.removeCallbacks(timeout);
        if(manager!=null&&bubble!=null)
            try{manager.removeView(bubble);}catch(Exception ignored){}
        bubble=null;bubbleLp=null;
    }

    @Override public void onDestroy(){
        removeBubble();
        try{unregisterReceiver(bubblePositionReceiver);}catch(Exception ignored){}
        try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public void onTimeout(int startId,int fgsType){stopSelf();}
    @Override public IBinder onBind(Intent i){return null;}
}
