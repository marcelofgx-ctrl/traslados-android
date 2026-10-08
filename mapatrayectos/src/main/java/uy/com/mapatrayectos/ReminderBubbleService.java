package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Optional 60-second illustrated speech-bubble overlay; the actual reminder remains in the notification shade. */
public final class ReminderBubbleService extends Service {
    private static final int FOREGROUND_ID=23991;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager manager;private View bubble;
    private final Runnable timeout=()->stopSelf();
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(){super.onCreate();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        int id=intent==null?-1:intent.getIntExtra("id",-1);
        ReminderStore.Item item=ReminderStore.get(this,id);
        if(item==null||item.done){stopSelf();return START_NOT_STICKY;}
        try{createForeground();}
        catch(Exception ex){
            android.util.Log.w("MapaReminders","Overlay service restricted by Android; normal notification stays",ex);
            stopSelf();return START_NOT_STICKY;
        }
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        try{show(item);}
        catch(Exception e){android.util.Log.w("MapaReminders","Cannot show speech overlay",e);stopSelf();}
        return START_NOT_STICKY;
    }
    private void createForeground(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        String ch="mapa_reminder_overlay";
        if(nm!=null&&Build.VERSION.SDK_INT>=26)
            nm.createNotificationChannel(new NotificationChannel(ch,"Globo temporal del recordatorio",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,ch)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Recordatorio visual").setContentText("Mostrando aviso")
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(FOREGROUND_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(FOREGROUND_ID,n);
    }
    private GradientDrawable shape(int fill,int stroke,int rad){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(rad));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private TextView txt(String v,int sz,int color,boolean bold){
        TextView t=new TextView(this);t.setText(v);t.setTextColor(color);t.setTextSize(sz);
        if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private void show(ReminderStore.Item item){
        removeBubble();
        final int petrol=Color.rgb(6,49,57),gold=Color.rgb(213,179,113),ivory=Color.rgb(252,249,239);
        LinearLayout whole=new LinearLayout(this);whole.setOrientation(LinearLayout.VERTICAL);
        whole.setPadding(dp(8),dp(4),dp(8),dp(12));
        whole.setElevation(dp(12));

        // Arrow/callout tail, like a comics speech balloon, pointing toward the app's floating icon.
        View tail=new View(this){
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);
                Path path=new Path();
                float cx=getWidth()-dp(44),h=getHeight();
                path.moveTo(cx-dp(11),h);
                path.lineTo(cx+dp(11),h);
                path.lineTo(cx,h*0.07f);
                path.close();
                p.setColor(gold);c.drawPath(path,p);
                path.reset();path.moveTo(cx-dp(9),h);
                path.lineTo(cx+dp(9),h);path.lineTo(cx,h*0.19f);path.close();
                p.setColor(ivory);c.drawPath(path,p);
            }
        };
        whole.addView(tail,new LinearLayout.LayoutParams(-1,dp(25)));

        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17),dp(13),dp(17),dp(16));card.setBackground(shape(ivory,gold,24));
        whole.addView(card,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.app_icon);
        avatar.setBackground(shape(petrol,gold,40));avatar.setPadding(dp(4),dp(4),dp(4),dp(4));
        top.addView(avatar,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout headings=new LinearLayout(this);headings.setOrientation(LinearLayout.VERTICAL);
        TextView title=txt("✦  TE RECUERDO ALGO",13,petrol,true);
        TextView sub=txt("Mapa Trayectos · Aviso personal",11,Color.rgb(77,96,97),false);
        headings.addView(title);headings.addView(sub);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(9);
        top.addView(headings,tp);card.addView(top);

        TextView message=txt(item.text,17,petrol,true);
        message.setMaxLines(6);message.setPadding(dp(4),dp(16),dp(4),dp(15));
        card.addView(message);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView later=txt("+10 MIN",12,petrol,true);later.setGravity(Gravity.CENTER);
        later.setBackground(shape(Color.rgb(236,221,189),0,12));
        TextView done=txt("HECHO",12,ivory,true);done.setGravity(Gravity.CENTER);
        done.setBackground(shape(petrol,0,12));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.rightMargin=dp(9);
        buttons.addView(later,p);buttons.addView(done,new LinearLayout.LayoutParams(0,dp(44),1));
        card.addView(buttons);
        TextView dismiss=txt("CERRAR AVISO",11,petrol,false);dismiss.setGravity(Gravity.CENTER);
        dismiss.setPadding(0,dp(13),0,0);card.addView(dismiss);

        later.setOnClickListener(v->{ReminderStore.change(this,item.id,false,false,System.currentTimeMillis()+10L*60L*1000L);stopSelf();});
        done.setOnClickListener(v->{ReminderStore.change(this,item.id,true,false,-1);stopSelf();});
        dismiss.setOnClickListener(v->stopSelf());

        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            dp(335),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.RIGHT;
        int preferred=getSharedPreferences("bubble_state",MODE_PRIVATE).getInt("y",dp(190));
        lp.y=Math.max(dp(85),Math.min(preferred+dp(60),getResources().getDisplayMetrics().heightPixels-dp(370)));
        lp.x=dp(10);
        manager.addView(whole,lp);bubble=whole;
        handler.removeCallbacks(timeout);handler.postDelayed(timeout,60000L);
    }
    private void removeBubble(){
        handler.removeCallbacks(timeout);
        if(manager!=null&&bubble!=null){try{manager.removeView(bubble);}catch(Exception ignored){}}
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