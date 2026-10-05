package uy.com.traslados.conductor;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;

public class ReservationMonitorService extends Service {
    // v10.5: globito + contador de novedades. Reutiliza este foreground service,
    // evitando tener dos servicios permanentes y dos notificaciones persistentes.
    private static final String DRIVER_BUBBLE_SHOW="uy.com.traslados.conductor.SHOW_BUBBLE";
    private static final String DRIVER_BUBBLE_HIDE="uy.com.traslados.conductor.HIDE_BUBBLE";
    private static final String DRIVER_TEST_TRIP="uy.com.traslados.conductor.TEST_TRIP_REMINDER";
    private static final long DRIVER_BUBBLE_POLL_MS=15000L;
    private android.view.WindowManager driverBubbleWindowManager;
    private android.widget.FrameLayout driverBubbleView;
    private android.widget.TextView driverBubbleBadge;
    private android.view.WindowManager.LayoutParams driverBubbleParams;
    private final android.os.Handler driverBubbleHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService driverBubbleWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean driverBubblePollInFlight=false;
    private boolean driverBubbleLoopStarted=false;
    private android.view.View driverTripCardView;
    private android.view.WindowManager.LayoutParams driverTripCardParams;

    public static final String CHANNEL_SERVICE="traslados_servicio_v91";
    public static final String CHANNEL_NEW="traslados_nuevas_v114_r9_visual";
    public static final String CHANNEL_TRIP="traslados_proximo_v114_r9_visual";
    private volatile boolean running=true;
    private Thread worker;
    private final Set<String> known=new HashSet<>();

    @Override public void onCreate(){super.onCreate();Api.init(getApplicationContext());createChannels();}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        driverBubbleStartLoop();
        if(intent!=null){
  String bubbleAction=intent.getAction();
  if(DRIVER_BUBBLE_HIDE.equals(bubbleAction)){
      getSharedPreferences("driver_bubble",MODE_PRIVATE).edit().putBoolean("app_foreground",true).putInt("unread",0).apply();
      driverBubbleHide();driverBubbleHideTripCard();
  }else if(DRIVER_BUBBLE_SHOW.equals(bubbleAction)){
      getSharedPreferences("driver_bubble",MODE_PRIVATE).edit().putBoolean("app_foreground",false).apply();
      driverBubbleShowIfNeeded();
  }else if(DRIVER_TEST_TRIP.equals(bubbleAction)){
      boolean leave=intent.getBooleanExtra("leave",false);long now=System.currentTimeMillis();android.content.SharedPreferences tp=driverTripReminderPrefs();tp.edit().putString("reservation_id","TEST-R7").putString("code","TEST-R7").putString("level",leave?"LEAVE":"PREP").putLong("at",now).putLong("pickup_at",now+30*60000L).putInt("travel_min",8).putInt("leave_in_min",leave?0:10).putString("pickup","10:30").putString("customer","Cliente de prueba").putString("origin","Ciudad de la Costa").putString("destination","Aeropuerto Internacional de Carrasco").apply();driverBubbleShowIfNeeded();driverBubbleShowTripCard();
  }
        }

        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,7,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification ongoing=new Notification.Builder(this,CHANNEL_SERVICE)
                .setContentTitle("Traslados Conductor")
                .setContentText("Monitor activo · esperando solicitudes")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentIntent(pi)
                .setOngoing(true).build();
        startForeground(9,ongoing);
        if(worker==null||!worker.isAlive()){
            worker=new Thread(this::loop,"reservation-monitor-v91");worker.start();
        }
        return START_STICKY;
    }

    private void loop(){
        boolean first=true;
        while(running){
            try{
                String pin=getSharedPreferences("driver_session",MODE_PRIVATE).getString("pin","");
                if(!pin.isEmpty()){
                    JSONArray a=Api.listActive(pin);Set<String> now=new HashSet<>();
                    for(int i=0;i<a.length();i++){
                        JSONObject r=a.getJSONObject(i);
                        if(!"PENDIENTE".equals(r.optString("status")))continue;
                        String id=r.optString("id");now.add(id);
                        if(!first&&!known.contains(id))notifyNew(r);
                    }
                    known.clear();known.addAll(now);first=false;
                }
            }catch(Exception ignored){}
            try{Thread.sleep(15000);}catch(InterruptedException e){return;}
        }
    }

    private void notifyNew(JSONObject r){
        driverBubbleMarkNew(r);
        driverNewReservationCueOnce(r);

        Intent open=new Intent(this,MainActivity.class);open.putExtra("open_requests",true);open.putExtra("reservation_id",r.optString("id",""));open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode=1100+Math.abs(r.optString("id",r.optString("code","")).hashCode()%7000);PendingIntent pi=PendingIntent.getActivity(this,requestCode,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String text=r.optString("customer_name")+" · "+shortText(r.optString("origin_text"))+" → "+shortText(r.optString("destination_text"));
        Notification n=new Notification.Builder(this,CHANNEL_NEW)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("NUEVA SOLICITUD")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        getSystemService(NotificationManager.class).notify((int)(System.currentTimeMillis()%100000),n);
        new Thread(()->Api.logEvent("new_reservation_notification","Nueva reserva notificada","{\"reservation\":\""+r.optString("code")+"\"}"),"log-new-reservation").start();
    }

    private String shortText(String s){if(s==null)return "";return s.length()>55?s.substring(0,52)+"…":s;}

    private boolean driverClaimNewCue(String id){if(id==null||id.isEmpty())return true;synchronized(Api.class){android.content.SharedPreferences p=getSharedPreferences("driver_alert_delivery",MODE_PRIVATE);String key="new_"+id;long now=System.currentTimeMillis(),at=p.getLong(key,0);if(at>0&&now-at<6*60*60*1000L)return false;p.edit().putLong(key,now).putString("last_id",id).putLong("last_at",now).apply();return true;}}
    private void driverNewReservationCueOnce(org.json.JSONObject r){String id=r.optString("id",r.optString("code",""));if(!driverClaimNewCue(id))return;android.content.SharedPreferences ap=getSharedPreferences("driver_alert_settings",MODE_PRIVATE);boolean sound=ap.getBoolean("new_sound",true),vibrate=ap.getBoolean("vibrate",true);if(sound){try{android.media.MediaPlayer mp=android.media.MediaPlayer.create(this,R.raw.reserva_calida);if(mp!=null){mp.setVolume(.78f,.78f);mp.setOnCompletionListener(android.media.MediaPlayer::release);mp.start();}}catch(Exception ignored){}}if(vibrate){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0,160,90,220},-1));else v.vibrate(new long[]{0,160,90,220},-1);}}catch(Exception ignored){}}getSharedPreferences("driver_alert_delivery",MODE_PRIVATE).edit().putBoolean("last_sound",sound).putBoolean("last_vibrate",vibrate).putString("last_source",driverBubbleForeground()?"SERVICIO/APP":"SEGUNDO PLANO").apply();}
    private void driverTripReminderCue(boolean leave){android.content.SharedPreferences ap=getSharedPreferences("driver_alert_settings",MODE_PRIVATE);try{android.media.MediaPlayer mp=android.media.MediaPlayer.create(this,R.raw.viaje_proximo);if(mp!=null){mp.setVolume(leave?.86f:.70f,leave?.86f:.70f);mp.setOnCompletionListener(android.media.MediaPlayer::release);mp.start();}}catch(Exception ignored){}if(ap.getBoolean("vibrate",true)){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){long[] pattern=leave?new long[]{0,230,90,230}:new long[]{0,140,90,170};if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(android.os.VibrationEffect.createWaveform(pattern,-1));else v.vibrate(pattern,-1);}}catch(Exception ignored){}}}

    private void createChannels(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel service=new NotificationChannel(CHANNEL_SERVICE,"Monitor de reservas",NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Mantiene activo el monitor de Traslados Conductor");nm.createNotificationChannel(service);
        NotificationChannel fresh=new NotificationChannel(CHANNEL_NEW,"Nuevas reservas",NotificationManager.IMPORTANCE_HIGH);
        fresh.setDescription("Aviso visual de nuevas solicitudes; Traslados controla sonido y vibración directamente");fresh.enableVibration(false);fresh.setSound(null,null);nm.createNotificationChannel(fresh);
        NotificationChannel trip=new NotificationChannel(CHANNEL_TRIP,"Próximos viajes",NotificationManager.IMPORTANCE_HIGH);
        trip.setDescription("Aviso visual de próximos viajes; Traslados controla sonido y vibración directamente");trip.enableVibration(false);trip.setSound(null,null);nm.createNotificationChannel(trip);
    }

    @Override public void onDestroy(){
        driverBubbleShutdown();
running=false;if(worker!=null)worker.interrupt();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}


    private final Runnable driverBubbleTick=new Runnable(){
        @Override public void run(){
  if(!driverBubbleLoopStarted)return;
  driverBubblePollStatusChanges();
  driverBubbleSyncVisibility();
  driverBubbleHandler.postDelayed(this,DRIVER_BUBBLE_POLL_MS);
        }
    };

    private void driverBubbleStartLoop(){
        if(driverBubbleLoopStarted)return;
        driverBubbleLoopStarted=true;
        driverBubbleHandler.post(driverBubbleTick);
    }

    private void driverBubbleShutdown(){
        driverBubbleLoopStarted=false;
        driverBubbleHandler.removeCallbacks(driverBubbleTick);
        try{driverBubbleWorker.shutdownNow();}catch(Exception ignored){}
        driverBubbleHide();driverBubbleHideTripCard();
    }

    private android.content.SharedPreferences driverBubblePrefs(){return getSharedPreferences("driver_bubble",MODE_PRIVATE);}
    private boolean driverBubbleForeground(){
        boolean hinted=driverBubblePrefs().getBoolean("app_foreground",false);
        try{
  android.app.ActivityManager.RunningAppProcessInfo info=new android.app.ActivityManager.RunningAppProcessInfo();
  android.app.ActivityManager.getMyMemoryState(info);
  boolean visible=info.importance<=android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
  return hinted&&visible;
        }catch(Exception ignored){return hinted;}
    }
    private boolean driverBubbleEnabled(){return driverBubblePrefs().getBoolean("bubble_enabled",false);}

    private void driverBubbleMarkNew(org.json.JSONObject r){
        android.content.SharedPreferences p=driverBubblePrefs();
        String id=r.optString("id","");
        p.edit().putString("last_reservation_id",id).apply();
        if(driverBubbleForeground())return;
        int unread=Math.min(99,p.getInt("unread",0)+1);
        p.edit().putInt("unread",unread).apply();
        driverBubbleHandler.post(()->{driverBubbleShowIfNeeded();driverBubbleUpdateBadge(unread);});
    }

    private void driverBubblePollStatusChanges(){
        if(driverBubblePollInFlight)return;
        String pin=getSharedPreferences("driver_session",MODE_PRIVATE).getString("pin","");
        if(pin==null||pin.isEmpty())return;
        driverBubblePollInFlight=true;
        driverBubbleWorker.execute(()->{
  try{
      org.json.JSONObject b=new org.json.JSONObject();b.put("p_pin",pin);
      String raw=Api.postRpc("driver_list_reservations_v2",b);
      org.json.JSONArray arr=new org.json.JSONArray(raw==null||raw.trim().isEmpty()?"[]":raw);
      driverTripReminderCheck(arr);
      android.content.SharedPreferences p=driverBubblePrefs();
      String old=p.getString("status_snapshot","");
      String now=driverBubbleSnapshot(arr);
      if(!old.isEmpty()&&!old.equals(now)){
          DriverBubbleChange ch=driverBubbleFindExistingChange(old,arr);
          // New rows are ignored here because notifyNew() already counts them instantly.
          if(ch!=null&&!driverBubbleForeground()){
              int unread=Math.min(99,p.getInt("unread",0)+1);
              android.content.SharedPreferences.Editor ed=p.edit().putInt("unread",unread);
              if(ch.id!=null&&!ch.id.isEmpty())ed.putString("last_reservation_id",ch.id);
              ed.putString("status_snapshot",now).apply();
              final int count=unread;final String msg=ch.message;
              driverBubbleHandler.post(()->{driverBubbleShowIfNeeded();driverBubbleUpdateBadge(count);driverBubbleStatusAlert(msg);});
          }else p.edit().putString("status_snapshot",now).apply();
      }else p.edit().putString("status_snapshot",now).apply();
  }catch(Exception ignored){}finally{driverBubblePollInFlight=false;}
        });
    }

    private String driverBubbleSnapshot(org.json.JSONArray arr){
        java.util.ArrayList<String> rows=new java.util.ArrayList<>();
        for(int i=0;i<arr.length();i++){
  org.json.JSONObject r=arr.optJSONObject(i);if(r==null)continue;
  String key=r.optString("id",r.optString("code",String.valueOf(i)));
  rows.add(key+"\t"+r.optString("code","")+"\t"+r.optString("status","")+"\t"+r.optString("quote_status","")+"\t"+String.format(java.util.Locale.US,"%.0f",r.optDouble("quote_final_total",0)));
        }
        java.util.Collections.sort(rows);return android.text.TextUtils.join("\n",rows);
    }

    private java.util.HashMap<String,String> driverBubbleParseSnapshot(String value){
        java.util.HashMap<String,String> out=new java.util.HashMap<>();
        if(value==null||value.isEmpty())return out;
        for(String line:value.split("\\n")){int pos=line.indexOf('\t');if(pos>0)out.put(line.substring(0,pos),line.substring(pos+1));}
        return out;
    }

    private boolean driverBubbleConsumeSelfChange(String id){
        if(id==null||id.isEmpty())return false;android.content.SharedPreferences p=driverBubblePrefs();String self=p.getString("self_change_id","");long at=p.getLong("self_change_at",0);boolean hit=id.equals(self)&&System.currentTimeMillis()-at<65000L;if(hit)p.edit().remove("self_change_id").remove("self_change_at").apply();return hit;
    }

    private DriverBubbleChange driverBubbleFindExistingChange(String oldSnapshot,org.json.JSONArray arr){
        java.util.HashMap<String,String> old=driverBubbleParseSnapshot(oldSnapshot);
        java.util.HashSet<String> current=new java.util.HashSet<>();
        for(int i=0;i<arr.length();i++){
  org.json.JSONObject r=arr.optJSONObject(i);if(r==null)continue;
  String key=r.optString("id",r.optString("code",String.valueOf(i)));current.add(key);
  String now=r.optString("code","")+"\t"+r.optString("status","")+"\t"+r.optString("quote_status","")+"\t"+String.format(java.util.Locale.US,"%.0f",r.optDouble("quote_final_total",0));
  String before=old.get(key);
  if(before==null)continue; // notifyNew() cubre las filas nuevas.
  if(!before.equals(now)){String changedId=r.optString("id",key);if(driverBubbleConsumeSelfChange(changedId))continue;return new DriverBubbleChange(changedId,driverBubbleChangeMessage(r));}
        }
        for(String oldKey:old.keySet())if(!current.contains(oldKey)){if(driverBubbleConsumeSelfChange(oldKey))continue;return new DriverBubbleChange(oldKey,"Una solicitud se cerró, rechazó o canceló");}
        return null;
    }

    private String driverBubbleChangeMessage(org.json.JSONObject r){
        String st=r.optString("status",""),qs=r.optString("quote_status","");
        String code=r.optString("code","");String suffix=code.isEmpty()?"":" · "+code;
        if("ACEPTADO".equals(qs)||"ACEPTADA".equals(st))return "Cliente aceptó el presupuesto"+suffix;
        if("RECHAZADO".equals(qs)||"RECHAZADA".equals(st))return "Presupuesto rechazado"+suffix;
        if("CANCELADA".equals(st))return "Reserva cancelada"+suffix;
        if("EN_VIAJE".equals(st))return "Viaje iniciado"+suffix;
        return "Una solicitud fue actualizada"+suffix;
    }

    private static class DriverBubbleChange{final String id,message;DriverBubbleChange(String i,String m){id=i;message=m;}}

    private void driverBubbleStatusAlert(String msg){
        android.content.SharedPreferences ap=getSharedPreferences("driver_alert_settings",MODE_PRIVATE);
        if(ap.getBoolean("change_sound",true)){try{android.media.MediaPlayer mp=android.media.MediaPlayer.create(this,R.raw.reserva_calida);if(mp!=null){mp.setVolume(0.55f,0.55f);mp.setOnCompletionListener(android.media.MediaPlayer::release);mp.start();}}catch(Exception ignored){}}
        if(ap.getBoolean("vibrate",true)){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(android.os.VibrationEffect.createOneShot(140,android.os.VibrationEffect.DEFAULT_AMPLITUDE));else v.vibrate(140);}}catch(Exception ignored){}}
        try{android.widget.Toast.makeText(this,msg,android.widget.Toast.LENGTH_LONG).show();}catch(Exception ignored){}
    }

    private void driverBubbleSyncVisibility(){if(driverBubbleForeground())driverBubbleHide();else driverBubbleShowIfNeeded();}

    private void driverBubbleShowIfNeeded(){
        if(driverBubbleView!=null){driverBubbleUpdateBadge(driverBubblePrefs().getInt("unread",0));return;}
        if(!driverBubbleEnabled()||driverBubbleForeground()||!android.provider.Settings.canDrawOverlays(this))return;
        driverBubbleWindowManager=(android.view.WindowManager)getSystemService(WINDOW_SERVICE);if(driverBubbleWindowManager==null)return;
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);root.setPadding(driverBubbleDp(3),driverBubbleDp(3),driverBubbleDp(3),driverBubbleDp(3));
        android.graphics.drawable.GradientDrawable shell=new android.graphics.drawable.GradientDrawable();shell.setShape(android.graphics.drawable.GradientDrawable.OVAL);shell.setColor(android.graphics.Color.rgb(5,32,40));shell.setStroke(driverBubbleDp(2),android.graphics.Color.rgb(224,193,111));root.setBackground(shell);root.setElevation(driverBubbleDp(10));
        android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(R.drawable.app_icon);logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        android.widget.FrameLayout.LayoutParams llp=new android.widget.FrameLayout.LayoutParams(driverBubbleDp(46),driverBubbleDp(46),android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL);llp.topMargin=driverBubbleDp(3);root.addView(logo,llp);
        android.widget.TextView role=new android.widget.TextView(this);role.setText("CONDUCTOR");role.setTextColor(android.graphics.Color.rgb(235,205,125));role.setTextSize(6.0f);role.setGravity(android.view.Gravity.CENTER);role.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);role.setLetterSpacing(.09f);role.setShadowLayer(2f,0,1f,android.graphics.Color.BLACK);
        android.widget.FrameLayout.LayoutParams rlp=new android.widget.FrameLayout.LayoutParams(driverBubbleDp(56),driverBubbleDp(13),android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);rlp.bottomMargin=driverBubbleDp(2);root.addView(role,rlp);
        android.widget.TextView badge=new android.widget.TextView(this);badge.setTextColor(android.graphics.Color.WHITE);badge.setTextSize(10);badge.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);badge.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable red=new android.graphics.drawable.GradientDrawable();red.setShape(android.graphics.drawable.GradientDrawable.OVAL);red.setColor(android.graphics.Color.rgb(218,64,72));badge.setBackground(red);
        android.widget.FrameLayout.LayoutParams blp=new android.widget.FrameLayout.LayoutParams(driverBubbleDp(21),driverBubbleDp(21),android.view.Gravity.TOP|android.view.Gravity.RIGHT);root.addView(badge,blp);
        driverBubbleView=root;driverBubbleBadge=badge;
        driverBubbleParams=new android.view.WindowManager.LayoutParams(driverBubbleDp(66),driverBubbleDp(66),android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,android.graphics.PixelFormat.TRANSLUCENT);
        driverBubbleParams.gravity=android.view.Gravity.TOP|android.view.Gravity.LEFT;
        android.content.SharedPreferences bp=driverBubblePrefs();
        driverBubbleParams.x=bp.getInt("bubble_x",Math.max(driverBubbleDp(8),getResources().getDisplayMetrics().widthPixels-driverBubbleDp(82)));
        driverBubbleParams.y=bp.getInt("bubble_y",driverBubbleDp(170));
        try{driverBubbleWindowManager.addView(driverBubbleView,driverBubbleParams);}catch(Exception e){driverBubbleView=null;driverBubbleBadge=null;return;}
        driverBubbleUpdateBadge(driverBubblePrefs().getInt("unread",0));driverBubbleAttachTouch();
    }

    private void driverBubbleAttachTouch(){
        if(driverBubbleView==null)return;
        driverBubbleView.setOnTouchListener(new android.view.View.OnTouchListener(){float downX,downY;int startX,startY;boolean moved;
  @Override public boolean onTouch(android.view.View v,android.view.MotionEvent e){
      switch(e.getActionMasked()){
          case android.view.MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=driverBubbleParams.x;startY=driverBubbleParams.y;moved=false;return true;
          case android.view.MotionEvent.ACTION_MOVE:float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(Math.abs(dx)>driverBubbleDp(4)||Math.abs(dy)>driverBubbleDp(4))moved=true;driverBubbleParams.x=startX+(int)dx;driverBubbleParams.y=Math.max(driverBubbleDp(20),startY+(int)dy);try{driverBubbleWindowManager.updateViewLayout(driverBubbleView,driverBubbleParams);}catch(Exception ignored){}return true;
          case android.view.MotionEvent.ACTION_UP:if(!moved){if(driverTripReminderActive())driverBubbleShowTripCard();else driverBubbleOpenApp();}else driverBubbleSnap();return true;
      }return false;
  }});
    }

    private void driverBubbleSnap(){
        int w=getResources().getDisplayMetrics().widthPixels;
        driverBubbleParams.x=(driverBubbleParams.x+driverBubbleDp(33)<w/2)?driverBubbleDp(8):Math.max(driverBubbleDp(8),w-driverBubbleDp(74));
        try{driverBubbleWindowManager.updateViewLayout(driverBubbleView,driverBubbleParams);}catch(Exception ignored){}
        driverBubblePrefs().edit().putInt("bubble_x",driverBubbleParams.x).putInt("bubble_y",driverBubbleParams.y).apply();
    }

    private void driverBubbleOpenApp(){
        android.content.SharedPreferences p=driverBubblePrefs();String id=p.getString("last_reservation_id","");p.edit().putInt("unread",0).putBoolean("app_foreground",true).apply();driverBubbleUpdateBadge(0);driverBubbleHide();
        android.content.Intent i=new android.content.Intent(this,MainActivity.class);i.putExtra("open_requests",true);i.putExtra("reservation_id",id);i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);
    }

    private android.content.SharedPreferences driverTripReminderPrefs(){return getSharedPreferences("driver_trip_reminder",MODE_PRIVATE);}
    private boolean driverTripReminderActive(){android.content.SharedPreferences p=driverTripReminderPrefs();long at=p.getLong("at",0),pickup=p.getLong("pickup_at",0),now=System.currentTimeMillis();return !p.getString("reservation_id","").isEmpty()&&now-at<120*60000L&&(pickup<=0||now<pickup+15*60000L);}
    private boolean driverTripConfirmed(org.json.JSONObject r){String st=r.optString("status","");return "ACEPTADA".equals(st)||"CONFIRMADA".equals(st)||"ACEPTADA_CLIENTE".equals(st);}
    private long driverTripPickupMillis(org.json.JSONObject r){try{String d=r.optString("pickup_date",""),t=r.optString("pickup_time","");if(t.length()>5)t=t.substring(0,5);java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US);f.setLenient(false);return f.parse(d+" "+t).getTime();}catch(Exception e){return 0;}}
    private android.location.Location driverLastLocation(){try{if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return null;android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);if(lm==null)return null;android.location.Location best=null;for(String provider:lm.getProviders(true)){try{android.location.Location l=lm.getLastKnownLocation(provider);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}catch(SecurityException ignored){}}if(best!=null&&System.currentTimeMillis()-best.getTime()>15*60000L)return null;return best;}catch(Exception e){return null;}}
    private int[] driverTravelToOrigin(org.json.JSONObject r){int minutes=15;int method=0;try{double lat=r.optDouble("origin_lat",Double.NaN),lng=r.optDouble("origin_lng",Double.NaN);android.location.Location l=driverLastLocation();if(l!=null&&!Double.isNaN(lat)&&!Double.isNaN(lng)){try{org.json.JSONObject est=Api.routeEstimate(l.getLatitude(),l.getLongitude(),lat,lng);minutes=Math.max(1,est.optInt("duration_min",15));method=1;}catch(Exception e){float[] out=new float[1];android.location.Location.distanceBetween(l.getLatitude(),l.getLongitude(),lat,lng,out);double roadKm=(out[0]/1000.0)*1.30;minutes=Math.max(1,(int)Math.ceil(roadKm/40.0*60.0));method=2;}}}catch(Exception ignored){}return new int[]{minutes,method};}
    private void driverTripReminderCheck(org.json.JSONArray arr){
        android.content.SharedPreferences ap=getSharedPreferences("driver_alert_settings",MODE_PRIVATE);if(!ap.getBoolean("trip_reminder",true))return;
        long now=System.currentTimeMillis(),bestAt=Long.MAX_VALUE;org.json.JSONObject best=null;
        for(int i=0;i<arr.length();i++){org.json.JSONObject r=arr.optJSONObject(i);if(r==null||!driverTripConfirmed(r))continue;long at=driverTripPickupMillis(r);if(at>now-10*60000L&&at<bestAt){bestAt=at;best=r;}}
        if(best==null){android.content.SharedPreferences clear=driverTripReminderPrefs();if(driverTripReminderActive())clear.edit().clear().apply();return;}String id=best.optString("id",best.optString("code",""));if(id.isEmpty())return;if(bestAt-now>120*60000L)return;
        android.content.SharedPreferences cache=driverTripReminderPrefs();int travelMin,method;long calcAt=cache.getLong("calc_at",0);if(id.equals(cache.getString("calc_id",""))&&now-calcAt<5*60000L){travelMin=cache.getInt("calc_travel_min",15);method=cache.getInt("calc_method",0);}else{int[] travel=driverTravelToOrigin(best);travelMin=travel[0];method=travel[1];cache.edit().putString("calc_id",id).putLong("calc_at",now).putInt("calc_travel_min",travelMin).putInt("calc_method",method).apply();}int safetyMin=getSharedPreferences("driver_availability_cache",MODE_PRIVATE).getInt("before",15),prepLeadMin=getSharedPreferences("driver_alert_settings",MODE_PRIVATE).getInt("trip_reminder_lead_min",10);long departAt=bestAt-(travelMin+safetyMin)*60000L;String level=null;
        if(now>=departAt&&now<bestAt+10*60000L)level="LEAVE";else if(now>=departAt-prepLeadMin*60000L&&now<departAt)level="PREPARE";else return;
        android.content.SharedPreferences p=driverTripReminderPrefs();if(id.equals(p.getString("snooze_id",""))&&now<p.getLong("snooze_until",0))return;String sentKey="sent_"+level+"_"+id;if(p.getBoolean(sentKey,false))return;
        int leaveIn=(int)Math.max(0,Math.ceil((departAt-now)/60000.0));String code=best.optString("code","");String customer=best.optString("customer_name","");String pickup=best.optString("pickup_time","");if(pickup.length()>5)pickup=pickup.substring(0,5);
        p.edit().putBoolean(sentKey,true).putString("reservation_id",id).putString("level",level).putLong("at",now).putLong("departure_at",departAt).putLong("pickup_at",bestAt).putInt("travel_min",travelMin).putInt("leave_in_min",leaveIn).putString("code",code).putString("customer",customer).putString("pickup",pickup).putString("origin",best.optString("origin_text","")).putString("destination",best.optString("destination_text","")).apply();
        driverTripReminderAlert(best,level,travelMin,leaveIn,method);
    }
    private void driverTripReminderAlert(org.json.JSONObject r,String level,int travelMin,int leaveIn,int method){
        boolean leave="LEAVE".equals(level);String title=leave?"HORA DE SALIR":"PRÓXIMO VIAJE";String text=leave?"Para llegar con margen al origen, salí ahora.":"Conviene salir en "+Math.max(1,leaveIn)+" min · aprox. "+travelMin+" min hasta el origen";
        boolean foreground=driverBubbleForeground();driverTripReminderCue(leave);if(!foreground){try{android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);android.content.Intent open=new android.content.Intent(this,MainActivity.class);open.putExtra("open_requests",true);open.putExtra("reservation_id",r.optString("id",""));open.addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);android.app.PendingIntent pi=android.app.PendingIntent.getActivity(this,41,open,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);android.app.Notification n=new android.app.Notification.Builder(this,CHANNEL_TRIP).setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle(title+" · "+shortText(r.optString("pickup_time",""))).setContentText(text).setStyle(new android.app.Notification.BigTextStyle().bigText(text+"\n"+shortText(r.optString("origin_text","")))).setPriority(android.app.Notification.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi).build();nm.notify(11440,n);}catch(Exception ignored){}driverBubbleHandler.post(()->{driverBubbleShowIfNeeded();driverBubbleUpdateBadge(driverBubblePrefs().getInt("unread",0));});}
        String details="{\"reservation\":\""+r.optString("code","")+"\",\"level\":\""+level+"\",\"travel_min\":"+travelMin+",\"leave_in_min\":"+leaveIn+",\"route_method\":"+method+"}";new Thread(()->Api.logEvent(leave?"trip_leave_now_reminder":"trip_prepare_reminder",text,details),"log-trip-reminder").start();
    }
    private void driverBubbleShowTripCard(){
        if(!driverTripReminderActive()){driverBubbleOpenApp();return;}if(driverTripCardView!=null){driverBubbleHideTripCard();return;}if(driverBubbleWindowManager==null)driverBubbleWindowManager=(android.view.WindowManager)getSystemService(WINDOW_SERVICE);if(driverBubbleWindowManager==null)return;
        android.content.SharedPreferences p=driverTripReminderPrefs();final String rid=p.getString("reservation_id","");final String origin=p.getString("origin","");String level=p.getString("level","");int travel=p.getInt("travel_min",0),leaveIn=p.getInt("leave_in_min",0);
        android.widget.LinearLayout shell=new android.widget.LinearLayout(this);shell.setOrientation(android.widget.LinearLayout.VERTICAL);shell.setPadding(driverBubbleDp(15),driverBubbleDp(13),driverBubbleDp(15),driverBubbleDp(13));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(android.graphics.Color.rgb(7,52,63));bg.setCornerRadius(driverBubbleDp(20));bg.setStroke(driverBubbleDp(2),android.graphics.Color.rgb(184,145,55));shell.setBackground(bg);shell.setElevation(driverBubbleDp(14));
        android.widget.LinearLayout top=new android.widget.LinearLayout(this);top.setGravity(android.view.Gravity.CENTER_VERTICAL);android.widget.TextView title=new android.widget.TextView(this);title.setText("LEAVE".equals(level)?"HORA DE SALIR":"PRÓXIMO VIAJE");title.setTextColor(android.graphics.Color.rgb(224,193,111));title.setTextSize(14);title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);top.addView(title,new android.widget.LinearLayout.LayoutParams(0,driverBubbleDp(38),1));android.widget.Button close=new android.widget.Button(this);close.setText("×");close.setTextSize(20);close.setTextColor(android.graphics.Color.WHITE);close.setBackgroundColor(android.graphics.Color.TRANSPARENT);top.addView(close,new android.widget.LinearLayout.LayoutParams(driverBubbleDp(44),driverBubbleDp(38)));shell.addView(top);
        android.widget.TextView when=new android.widget.TextView(this);when.setText(p.getString("pickup","")+" · "+p.getString("customer",""));when.setTextColor(android.graphics.Color.WHITE);when.setTextSize(17);when.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);shell.addView(when);
        android.widget.TextView route=new android.widget.TextView(this);route.setText(shortText(origin)+" → "+shortText(p.getString("destination","")));route.setTextColor(android.graphics.Color.rgb(177,188,190));route.setTextSize(12);route.setPadding(0,driverBubbleDp(5),0,driverBubbleDp(7));shell.addView(route);
        android.widget.TextView info=new android.widget.TextView(this);info.setText((travel>0?"Aprox. "+travel+" min hasta el origen · ":"")+("LEAVE".equals(level)?"salí ahora":"salí en "+Math.max(1,leaveIn)+" min"));info.setTextColor(android.graphics.Color.rgb(235,205,125));info.setTextSize(12);info.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);shell.addView(info);
        android.widget.LinearLayout actions=new android.widget.LinearLayout(this);actions.setPadding(0,driverBubbleDp(10),0,0);android.widget.Button go=new android.widget.Button(this);go.setText("IR AL ORIGEN");go.setTextSize(10);go.setTextColor(android.graphics.Color.WHITE);android.graphics.drawable.GradientDrawable goBg=new android.graphics.drawable.GradientDrawable();goBg.setColor(android.graphics.Color.rgb(7,52,63));goBg.setCornerRadius(driverBubbleDp(16));goBg.setStroke(driverBubbleDp(1),android.graphics.Color.rgb(184,145,55));go.setBackground(goBg);android.widget.Button app=new android.widget.Button(this);app.setText("ABRIR VIAJE");app.setTextSize(10);app.setTextColor(android.graphics.Color.rgb(25,35,34));android.graphics.drawable.GradientDrawable appBg=new android.graphics.drawable.GradientDrawable();appBg.setColor(android.graphics.Color.rgb(224,193,111));appBg.setCornerRadius(driverBubbleDp(16));app.setBackground(appBg);actions.addView(go,new android.widget.LinearLayout.LayoutParams(0,driverBubbleDp(48),1));android.view.View gap=new android.view.View(this);actions.addView(gap,new android.widget.LinearLayout.LayoutParams(driverBubbleDp(7),1));actions.addView(app,new android.widget.LinearLayout.LayoutParams(0,driverBubbleDp(48),1));shell.addView(actions);
        android.widget.LinearLayout snooze=new android.widget.LinearLayout(this);snooze.setPadding(0,driverBubbleDp(7),0,0);android.widget.Button s5=driverBubbleSmallButton("RECORDAR EN 5 MIN"),s10=driverBubbleSmallButton("EN 10 MIN");snooze.addView(s5,new android.widget.LinearLayout.LayoutParams(0,driverBubbleDp(42),1));android.view.View sg=new android.view.View(this);snooze.addView(sg,new android.widget.LinearLayout.LayoutParams(driverBubbleDp(6),1));snooze.addView(s10,new android.widget.LinearLayout.LayoutParams(0,driverBubbleDp(42),1));shell.addView(snooze);
        close.setOnClickListener(v->driverBubbleHideTripCard());go.setOnClickListener(v->{try{android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+android.net.Uri.encode(origin)+"&travelmode=driving"));i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception ignored){}});app.setOnClickListener(v->{driverBubbleHideTripCard();android.content.Intent i=new android.content.Intent(this,MainActivity.class);i.putExtra("open_requests",true);i.putExtra("reservation_id",rid);i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP|android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);});s5.setOnClickListener(v->{driverTripReminderSnooze(5);driverBubbleHideTripCard();});s10.setOnClickListener(v->{driverTripReminderSnooze(10);driverBubbleHideTripCard();});
        int width=Math.min(driverBubbleDp(340),getResources().getDisplayMetrics().widthPixels-driverBubbleDp(24));driverTripCardParams=new android.view.WindowManager.LayoutParams(width,-2,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,android.graphics.PixelFormat.TRANSLUCENT);driverTripCardParams.gravity=android.view.Gravity.TOP|android.view.Gravity.LEFT;driverTripCardParams.x=Math.max(driverBubbleDp(10),Math.min(driverBubbleParams!=null?driverBubbleParams.x:driverBubbleDp(10),getResources().getDisplayMetrics().widthPixels-width-driverBubbleDp(10)));driverTripCardParams.y=Math.max(driverBubbleDp(70),(driverBubbleParams!=null?driverBubbleParams.y:driverBubbleDp(170))+driverBubbleDp(72));driverTripCardView=shell;try{driverBubbleWindowManager.addView(driverTripCardView,driverTripCardParams);}catch(Exception e){driverTripCardView=null;driverTripCardParams=null;}
    }
    private android.widget.Button driverBubbleSmallButton(String text){android.widget.Button b=new android.widget.Button(this);b.setText(text);b.setTextSize(9);b.setTextColor(android.graphics.Color.WHITE);android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(android.graphics.Color.rgb(7,52,63));g.setCornerRadius(driverBubbleDp(14));g.setStroke(driverBubbleDp(1),android.graphics.Color.rgb(184,145,55));b.setBackground(g);return b;}
    private void driverTripReminderSnooze(int minutes){android.content.SharedPreferences p=driverTripReminderPrefs();String id=p.getString("reservation_id","");String level=p.getString("level","");p.edit().putString("snooze_id",id).putLong("snooze_until",System.currentTimeMillis()+minutes*60000L).remove("sent_"+level+"_"+id).apply();new Thread(()->Api.logEvent("trip_reminder_snoozed","Recordatorio pospuesto "+minutes+" min","{\"minutes\":"+minutes+"}"),"log-snooze").start();}
    private void driverBubbleHideTripCard(){if(driverBubbleWindowManager!=null&&driverTripCardView!=null){try{driverBubbleWindowManager.removeView(driverTripCardView);}catch(Exception ignored){}}driverTripCardView=null;driverTripCardParams=null;}

    private void driverBubbleUpdateBadge(int count){if(driverBubbleBadge==null)return;if(count<=0&&driverTripReminderActive()){driverBubbleBadge.setVisibility(android.view.View.VISIBLE);driverBubbleBadge.setText("!");return;}if(count<=0){driverBubbleBadge.setVisibility(android.view.View.GONE);return;}driverBubbleBadge.setVisibility(android.view.View.VISIBLE);driverBubbleBadge.setText(count>9?"9+":String.valueOf(count));}
    private void driverBubbleHide(){if(driverBubbleWindowManager!=null&&driverBubbleView!=null){try{driverBubbleWindowManager.removeView(driverBubbleView);}catch(Exception ignored){}}driverBubbleView=null;driverBubbleBadge=null;driverBubbleParams=null;}
    private int driverBubbleDp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
