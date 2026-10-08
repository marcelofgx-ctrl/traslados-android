package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;

/** Best-effort speech balloon over other apps, sharing EXACTLY the same layout as foreground UI. */
public final class ReminderBubbleService extends Service {
    private static final int FOREGROUND_ID=23991;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager manager;private ReminderCallout bubble;
    private final Runnable timeout=this::stopSelf;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        int id=intent==null?-1:intent.getIntExtra("id",-1);
        ReminderStore.Item item=ReminderStore.get(this,id);
        if(item==null||item.done||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        try{createForeground();show(item);}
        catch(Exception e){
            Log.w("MapaReminders","System restricted foreground overlay; Android notification remains",e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void createForeground(){
        final String ch="mapa_reminder_overlay";
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null&&Build.VERSION.SDK_INT>=26)
            nm.createNotificationChannel(new NotificationChannel(ch,"Aviso visual temporal",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,ch).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Recordatorio visual").setContentText("Mostrando aviso").setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(FOREGROUND_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(FOREGROUND_ID,n);
    }
    private void show(ReminderStore.Item item){
        removeBubble();
        SharedPreferences prefs=getSharedPreferences("bubble_state",MODE_PRIVATE);
        int[] coords=resolveAnchor(prefs);
        int anchorX=coords[0],anchorY=coords[1],size=coords[2];
        int width=dp(ReminderCallout.TOTAL_WIDTH_DP);
        int desiredX=anchorX-width+dp(1);
        DisplayMetrics display=getResources().getDisplayMetrics();
        // When the floating shortcut is near the left edge, keep the bubble on-screen.
        int x=Math.max(dp(5),Math.min(desiredX,display.widthPixels-width-dp(4)));
        int y=Math.max(dp(64),Math.min(anchorY+size/2-dp(ReminderCallout.TAIL_CENTER_DP),
            display.heightPixels-dp(170)));
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        bubble=new ReminderCallout(this,item.text,
            ()->{ReminderStore.change(this,item.id,true,false,-1);stopSelf();},
            ()->{ReminderStore.change(this,item.id,false,false,System.currentTimeMillis()+600000L);stopSelf();},
            this::stopSelf);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            width,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=x;lp.y=y;
        manager.addView(bubble,lp);bubble.reveal();
        handler.removeCallbacks(timeout);handler.postDelayed(timeout,30000);
    }
    private int[] resolveAnchor(SharedPreferences p){
        boolean visible=p.getBoolean("ui_visible",false);
        if(visible && p.contains("reminder_anchor_x"))
            return new int[]{p.getInt("reminder_anchor_x",dp(330)),p.getInt("reminder_anchor_y",dp(245)),p.getInt("reminder_anchor_size",dp(44))};
        return new int[]{p.getInt("x",dp(320)),p.getInt("y",dp(235)),dp(52)};
    }
    private void removeBubble(){
        handler.removeCallbacks(timeout);
        if(manager!=null&&bubble!=null)try{manager.removeView(bubble);}catch(Exception ignored){}
        bubble=null;
    }
    @Override public void onDestroy(){
        removeBubble();
        try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public void onTimeout(int startId,int fgsType){stopSelf();}
    @Override public IBinder onBind(Intent i){return null;}
}
