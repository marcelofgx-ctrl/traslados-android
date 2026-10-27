package uy.com.mapatrayectos.qa;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class LiveQaService extends Service {
 static final String START="uy.com.mapatrayectos.qa.START",STOP="uy.com.mapatrayectos.qa.STOP";
 private final ExecutorService queue=Executors.newSingleThreadExecutor();
 private final AtomicBoolean sending=new AtomicBoolean(false);
 private String endpoint,id,token;private volatile boolean sharing=false;private boolean receiverOn=false,startedTrip=false,stopping=false;
 private BroadcastReceiver receiver;private long lastAttempt=0,lastSeq=0;
 static JSONObject request(String base,String id,String token,String path,JSONObject body)throws Exception{
  URL url=new URL(LiveQaPolicy.endpoint(base)+"/v1/requests/"+LiveQaPolicy.requestId(id)+path);
  HttpURLConnection c=(HttpURLConnection)url.openConnection();
  c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(7000);
  c.setUseCaches(false);c.setRequestMethod(body==null?"GET":"POST");
  c.setRequestProperty("Authorization","Bearer "+token);
  c.setRequestProperty("Accept","application/json");
  if(body!=null){
   c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
   try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
  }
  int status=c.getResponseCode();if(status<200||status>299){c.disconnect();throw new IOException("QA HTTP "+status);}
  StringBuilder out=new StringBuilder();
  try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
   String line;while((line=in.readLine())!=null){out.append(line);if(out.length()>10000)throw new IOException("Reply too large");}
  }finally{c.disconnect();}
  return new JSONObject(out.toString());
 }
 private static void consent(String base,String id,String token,boolean enable)throws Exception{
  JSONObject row=request(base,id,token,"",null).getJSONObject("request");
  String state=row.getString("state");
  if(!row.optBoolean("qaOnly")|| !Arrays.asList("ACEPTADA","EN_CAMINO","EN_PUNTO","EN_VIAJE").contains(state))
   throw new IOException("Not an accepted QA request");
  JSONObject input=new JSONObject().put("enabled",enable).put("expectedVersion",row.getInt("version"));
  JSONObject out=request(base,id,token,"/tracking-consent",input);
  if(out.optBoolean("trackingEnabled")!=enable)throw new IOException("Consent not applied");
 }
 @Override public void onCreate(){
  super.onCreate();
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(nm!=null)nm.createNotificationChannel(new NotificationChannel("qt_live_qa","Quick Trip GPS QA",NotificationManager.IMPORTANCE_LOW));
 }
 private void notifyActive(String message){
  Notification note=new Notification.Builder(this,"qt_live_qa")
   .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Quick Trip Live · QA")
   .setContentText(message).setOngoing(true).setOnlyAlertOnce(true).build();
  startForeground(9126,note);
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if(STOP.equals(intent.getAction())){shutdown();return START_NOT_STICKY;}
  if(!START.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(sharing||stopping)return START_NOT_STICKY;
  try{
   endpoint=LiveQaPolicy.endpoint(intent.getStringExtra("endpoint"));
   id=LiveQaPolicy.requestId(intent.getStringExtra("request"));
   token=intent.getStringExtra("token");
   if(token==null||token.length()<32||token.length()>240)throw new IllegalArgumentException("Token inválido");
   notifyActive("Verificando consentimiento GPS de esta reserva QA");
   receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent state){accept(state);}};
   IntentFilter filter=new IntentFilter("uy.com.mapatrayectos.STATE");
   if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
   else registerReceiver(receiver,filter);
   receiverOn=true;
   queue.execute(()->{
    try{consent(endpoint,id,token,true);sharing=true;notifyActive("GPS QA: solo jornada y viaje activos");}
    catch(Exception ignored){stopSelf();}
   });
  }catch(Exception e){stopSelf();}
  return START_NOT_STICKY;
 }
 private void accept(Intent state){
  if(!sharing||stopping||state==null)return;
  boolean shift=state.getBooleanExtra("shift_active",false);
  boolean paused=state.getBooleanExtra("shift_paused",false),trip=state.getBooleanExtra("trip_active",false);
  if(trip)startedTrip=true;
  if(startedTrip&&(!trip||!shift)){shutdown();return;}
  long now=System.currentTimeMillis(),age=state.getLongExtra("location_age_ms",Long.MAX_VALUE);
  double lat=state.getDoubleExtra("lat",0),lng=state.getDoubleExtra("lon",0);
  float accuracy=state.getFloatExtra("accuracy",999f);
  if(!LiveQaPolicy.valid(shift,paused,trip,state.getBooleanExtra("has_location",false),
                         lat,lng,accuracy,age))return;
  if(now-lastAttempt<8000||!sending.compareAndSet(false,true))return;
  lastAttempt=now;long seq=lastSeq=Math.max(lastSeq+1,now);
  String target=endpoint,booking=id,secret=token;
  float bearing=state.getFloatExtra("bearing",Float.NaN);
  String measured=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US){{
   setTimeZone(TimeZone.getTimeZone("UTC"));
  }}.format(new Date(now-age));
  queue.execute(()->{
   try{
    if(!sharing||stopping)return;
    JSONObject sample=new JSONObject().put("lat",lat).put("lng",lng)
      .put("accuracyM",accuracy).put("seq",seq).put("measuredAt",measured);
    if(Float.isFinite(bearing)&&bearing>=0&&bearing<360)sample.put("headingDeg",bearing);
    request(target,booking,secret,"/location",sample);
   }catch(Exception ignored){}finally{sending.set(false);}
  });
 }
 private void shutdown(){
  if(stopping)return;stopping=true;sharing=false;
  if(receiverOn){try{unregisterReceiver(receiver);}catch(Exception ignored){}receiverOn=false;}
  String base=endpoint,rid=id,secret=token;
  if(base==null||rid==null||secret==null){stopSelf();return;}
  queue.execute(()->{
   try{consent(base,rid,secret,false);}catch(Exception ignored){}
   new Handler(Looper.getMainLooper()).post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
  });
 }
 @Override public void onDestroy(){
  sharing=false;if(receiverOn){try{unregisterReceiver(receiver);}catch(Exception ignored){}receiverOn=false;}
  queue.shutdownNow();token=null;super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}
