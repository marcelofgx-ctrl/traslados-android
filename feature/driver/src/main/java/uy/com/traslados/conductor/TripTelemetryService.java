package uy.com.traslados.conductor;

import android.app.*;
import android.content.*;
import android.location.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class TripTelemetryService extends Service implements LocationListener {
    public static final String ACTION_START="uy.com.traslados.conductor.telemetry.START";
    public static final String ACTION_STOP="uy.com.traslados.conductor.telemetry.STOP";
    public static final String ACTION_ABORT="uy.com.traslados.conductor.telemetry.ABORT";
    public static final String STATE_STARTING="STARTING";
    public static final String STATE_ACTIVE="ACTIVE";
    public static final String STATE_ERROR="ERROR";
    public static final String STATE_FINISHED="FINISHED";
    private static final String CHANNEL="trip_gps_v109";
    private static final int NOTIF_ID=1097;
    private static final String PREFS="trip_telemetry";
    private static final String SETTINGS="telemetry_settings";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService radarPool=Executors.newSingleThreadExecutor();
    private LocationManager lm;
    private SharedPreferences prefs;
    private String reservationId="", code="";
    private long startedAt=0, finishedAt=0, movingMs=0, stoppedMs=0, lastAcceptedAt=0;
    private double totalMeters=0, maxSpeedKmh=0, currentSpeedKmh=0;
    private int pointCount=0, radarAlertCount=0;
    private Location lastAccepted;
    private volatile boolean tracking=false, radarLoading=false;
    private long lastRadarFetchAt=0, lastRadarAlertAt=0;
    private String lastRadarAlertId="";
    private final ArrayList<CameraPoint> cameras=new ArrayList<>();
    private final Runnable heartbeat=new Runnable(){@Override public void run(){if(tracking&&!reservationId.isEmpty()){prefs.edit().putLong(k(reservationId,"heartbeat_at"),System.currentTimeMillis()).apply();main.postDelayed(this,4000);}}};

    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences(PREFS,MODE_PRIVATE);lm=(LocationManager)getSystemService(LOCATION_SERVICE);createChannel();}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_ABORT.equals(action)){
            String id=intent==null?null:intent.getStringExtra("reservation_id");
            abortTracking(id);
            return START_NOT_STICKY;
        }
        if(ACTION_STOP.equals(action)){
            String id=intent==null?null:intent.getStringExtra("reservation_id");
            finishTrackingFor(id);
            return START_NOT_STICKY;
        }
        if(ACTION_START.equals(action)){
            String id=intent==null?null:intent.getStringExtra("reservation_id"),c=intent==null?null:intent.getStringExtra("code");
            if(id!=null&&!id.isEmpty()){
                try{startTracking(id,c==null?"":c);}catch(Throwable e){markStartError(id,"No se pudo activar el servicio GPS: "+shortError(e));try{stopForeground(true);}catch(Exception ignored){}stopSelf();}
            }
            return START_STICKY;
        }
        String active=prefs.getString("active_id","");
        if(!active.isEmpty()&&prefs.getBoolean(k(active,"active"),false)){
            try{startTracking(active,prefs.getString(k(active,"code"),""));}catch(Throwable e){markStartError(active,"No se pudo reanudar GPS: "+shortError(e));}
        }
        return START_STICKY;
    }

    private void startTracking(String id,String c){
        if(tracking&&id.equals(reservationId)){markServiceState(id,STATE_ACTIVE,"",prefs.getInt(k(id,"providers"),0));updateNotification("GPS activo · registrando viaje");return;}
        if(tracking)stopLocationUpdatesOnly();
        prepareStart(this,id,c);
        restorePersisted(id,c);
        tracking=true;
        try{startForeground(NOTIF_ID,notification("GPS iniciando · esperando ubicación"));}
        catch(Throwable e){tracking=false;markStartError(id,"Android no permitió iniciar GPS: "+shortError(e));throw e;}
        int providers=requestUpdates();
        if(providers<=0){tracking=false;String err=providers<0?prefs.getString(k(id,"last_error"),"Ubicación sin permiso"):"GPS y ubicación de red están desactivados";markStartError(id,err);updateNotification("GPS no disponible · revisá Ubicación");stopForeground(true);stopSelf();return;}
        markServiceState(id,STATE_ACTIVE,"",providers);
        main.removeCallbacks(heartbeat);main.post(heartbeat);
        updateNotification("GPS activo · esperando primer punto");
    }

    private int requestUpdates(){
        int providers=0;
        try{
            if(lm==null){markStartError(reservationId,"Servicio de ubicación no disponible");return 0;}
            if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,2f,this);providers++;}
            if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000L,8f,this);providers++;}
            return providers;
        }catch(SecurityException e){markStartError(reservationId,"Falta permiso de ubicación");updateNotification("Ubicación sin permiso");return -1;}
        catch(Exception e){markStartError(reservationId,"GPS no disponible: "+shortError(e));updateNotification("GPS no disponible");return 0;}
    }

    @Override public void onLocationChanged(Location loc){
        if(!tracking||loc==null)return;
        if(loc.hasAccuracy()&&loc.getAccuracy()>100f)return;
        long t=loc.getTime()>0?loc.getTime():System.currentTimeMillis();
        if(lastAcceptedAt>0&&t<=lastAcceptedAt)return;
        double segment=0,dtSec=0,derivedSpeed=0;boolean moving=false;long accountedDt=0;
        if(lastAccepted!=null&&lastAcceptedAt>0){
            long dt=t-lastAcceptedAt;dtSec=dt/1000.0;
            if(dt>0&&dt<=30000){
                float raw=lastAccepted.distanceTo(loc);double implied=dtSec>0?raw/dtSec:0;
                if(implied<=70.0&&raw<=Math.max(150.0,dtSec*70.0)){
                    double sourceSpeed=loc.hasSpeed()?Math.max(0,loc.getSpeed()):implied;
                    if(sourceSpeed<1.2&&raw<15)raw=0;
                    segment=raw;derivedSpeed=sourceSpeed;totalMeters+=segment;accountedDt=dt;
                    moving=sourceSpeed>=1.2||segment>=Math.max(8,dtSec*1.2);
                    if(moving)movingMs+=accountedDt;else stoppedMs+=accountedDt;
                }
            }
        }
        double speedMps=loc.hasSpeed()?Math.max(0,loc.getSpeed()):derivedSpeed;
        currentSpeedKmh=speedMps*3.6;maxSpeedKmh=Math.max(maxSpeedKmh,currentSpeedKmh);
        lastAccepted=new Location(loc);lastAcceptedAt=t;pointCount++;
        appendPoint(loc,t,moving);
        persistLive(loc,t);
        checkRadarAlert(loc);
        maybeRefreshRadars(loc);
        if(pointCount%3==0)updateNotification("GPS · "+fmtKm(totalMeters/1000.0)+" · "+fmtDuration(System.currentTimeMillis()-startedAt));
    }

    private void persistLive(Location loc,long t){
        prefs.edit()
            .putBoolean(k(reservationId,"active"),true).putLong(k(reservationId,"started_at"),startedAt)
            .putString(k(reservationId,"state"),STATE_ACTIVE).putString(k(reservationId,"last_error"),"").putLong(k(reservationId,"heartbeat_at"),System.currentTimeMillis())
            .putLong(k(reservationId,"moving_ms"),movingMs).putLong(k(reservationId,"stopped_ms"),stoppedMs)
            .putLong(k(reservationId,"meters_bits"),Double.doubleToRawLongBits(totalMeters))
            .putLong(k(reservationId,"max_speed_bits"),Double.doubleToRawLongBits(maxSpeedKmh))
            .putLong(k(reservationId,"current_speed_bits"),Double.doubleToRawLongBits(currentSpeedKmh))
            .putInt(k(reservationId,"points"),pointCount).putInt(k(reservationId,"radar_alerts"),radarAlertCount)
            .putLong(k(reservationId,"last_time"),t)
            .putLong(k(reservationId,"last_lat_bits"),Double.doubleToRawLongBits(loc.getLatitude()))
            .putLong(k(reservationId,"last_lon_bits"),Double.doubleToRawLongBits(loc.getLongitude()))
            .apply();
    }

    private void appendPoint(Location loc,long t,boolean moving){
        try{
            JSONObject o=new JSONObject();o.put("type","point");o.put("t",t);o.put("lat",loc.getLatitude());o.put("lon",loc.getLongitude());
            o.put("accuracy_m",loc.hasAccuracy()?loc.getAccuracy():JSONObject.NULL);o.put("speed_kmh",currentSpeedKmh);o.put("moving",moving);o.put("km",totalMeters/1000.0);
            if(loc.hasBearing())o.put("bearing",loc.getBearing());if(loc.hasAltitude())o.put("altitude_m",loc.getAltitude());
            appendLine(o.toString());
        }catch(Exception ignored){}
    }

    private void appendRadarEvent(CameraPoint cam,float meters){
        try{JSONObject o=new JSONObject();o.put("type","camera");o.put("t",System.currentTimeMillis());o.put("camera_id",cam.id);o.put("lat",cam.lat);o.put("lon",cam.lon);o.put("distance_m",meters);if(cam.maxspeed!=null)o.put("maxspeed",cam.maxspeed);appendLine(o.toString());}catch(Exception ignored){}
    }

    private void appendLine(String line)throws Exception{File f=traceFile(this,reservationId);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f,true)){out.write((line+"\n").getBytes("UTF-8"));}}

    private void stopTracking(boolean finish){
        if(reservationId.isEmpty()){try{stopForeground(true);}catch(Exception ignored){}stopSelf();return;}
        main.removeCallbacks(heartbeat);stopLocationUpdatesOnly();tracking=false;finishedAt=System.currentTimeMillis();currentSpeedKmh=0;
        prefs.edit().putBoolean(k(reservationId,"active"),false).putString(k(reservationId,"state"),STATE_FINISHED).putString(k(reservationId,"last_error"),"").putLong(k(reservationId,"heartbeat_at"),finishedAt).putLong(k(reservationId,"finished_at"),finishedAt).putLong(k(reservationId,"moving_ms"),movingMs).putLong(k(reservationId,"stopped_ms"),stoppedMs).putLong(k(reservationId,"meters_bits"),Double.doubleToRawLongBits(totalMeters)).putLong(k(reservationId,"current_speed_bits"),Double.doubleToRawLongBits(0)).putLong(k(reservationId,"max_speed_bits"),Double.doubleToRawLongBits(maxSpeedKmh)).putInt(k(reservationId,"points"),pointCount).putInt(k(reservationId,"radar_alerts"),radarAlertCount).remove("active_id").apply();
        updateNotification("Viaje registrado · "+fmtKm(totalMeters/1000.0));
        try{stopForeground(true);}catch(Exception ignored){}stopSelf();
    }

    private void finishTrackingFor(String id){
        if(id==null||id.isEmpty())id=reservationId;
        if(id==null||id.isEmpty()){try{stopForeground(true);}catch(Exception ignored){}stopSelf();return;}
        if(!id.equals(reservationId))restorePersisted(id,prefs.getString(k(id,"code"),""));
        stopTracking(true);
    }

    private void abortTracking(String id){
        if(id==null||id.isEmpty())id=reservationId;
        main.removeCallbacks(heartbeat);stopLocationUpdatesOnly();tracking=false;
        if(id!=null&&!id.isEmpty()){
            SharedPreferences.Editor e=prefs.edit();for(String key:new ArrayList<String>(prefs.getAll().keySet()))if(key.startsWith(id+"_"))e.remove(key);if(id.equals(prefs.getString("active_id","")))e.remove("active_id");e.apply();
            try{File f=traceFile(this,id);if(f.isFile())f.delete();}catch(Exception ignored){}
        }
        try{stopForeground(true);}catch(Exception ignored){}stopSelf();
    }

    private void restorePersisted(String id,String c){
        reservationId=id;code=c==null?"":c;startedAt=prefs.getLong(k(id,"started_at"),0);if(startedAt<=0)startedAt=System.currentTimeMillis();finishedAt=0;
        movingMs=prefs.getLong(k(id,"moving_ms"),0);stoppedMs=prefs.getLong(k(id,"stopped_ms"),0);totalMeters=Double.longBitsToDouble(prefs.getLong(k(id,"meters_bits"),Double.doubleToRawLongBits(0)));maxSpeedKmh=Double.longBitsToDouble(prefs.getLong(k(id,"max_speed_bits"),Double.doubleToRawLongBits(0)));currentSpeedKmh=Double.longBitsToDouble(prefs.getLong(k(id,"current_speed_bits"),Double.doubleToRawLongBits(0)));pointCount=prefs.getInt(k(id,"points"),0);radarAlertCount=prefs.getInt(k(id,"radar_alerts"),0);lastAcceptedAt=prefs.getLong(k(id,"last_time"),0);lastRadarAlertAt=prefs.getLong(k(id,"last_radar_at"),0);lastRadarAlertId=prefs.getString(k(id,"last_radar_id"),"");lastAccepted=null;
        double lat=Double.longBitsToDouble(prefs.getLong(k(id,"last_lat_bits"),Double.doubleToRawLongBits(Double.NaN))),lon=Double.longBitsToDouble(prefs.getLong(k(id,"last_lon_bits"),Double.doubleToRawLongBits(Double.NaN)));if(!Double.isNaN(lat)&&!Double.isNaN(lon)&&lastAcceptedAt>0){lastAccepted=new Location("resume");lastAccepted.setLatitude(lat);lastAccepted.setLongitude(lon);lastAccepted.setTime(lastAcceptedAt);}
    }

    private void markServiceState(String id,String state,String error,int providers){prefs.edit().putBoolean(k(id,"active"),STATE_ACTIVE.equals(state)||STATE_STARTING.equals(state)).putString(k(id,"state"),state).putString(k(id,"last_error"),error==null?"":error).putInt(k(id,"providers"),Math.max(0,providers)).putLong(k(id,"heartbeat_at"),System.currentTimeMillis()).apply();}
    private void markStartError(String id,String error){if(id==null||id.isEmpty())return;prefs.edit().putBoolean(k(id,"active"),false).putString(k(id,"state"),STATE_ERROR).putString(k(id,"last_error"),error==null?"Error de GPS":error).putLong(k(id,"heartbeat_at"),System.currentTimeMillis()).apply();}
    private static String shortError(Throwable e){String m=e==null?"":e.getMessage();return (m==null||m.trim().isEmpty())?(e==null?"error":e.getClass().getSimpleName()):m;}

    private void stopLocationUpdatesOnly(){try{if(lm!=null)lm.removeUpdates(this);}catch(Exception ignored){}}
    @Override public void onDestroy(){main.removeCallbacks(heartbeat);stopLocationUpdatesOnly();radarPool.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @SuppressWarnings("deprecation") @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Telemetría de viaje",NotificationManager.IMPORTANCE_LOW);c.setDescription("Registro GPS durante un viaje activo");c.setSound(null,null);c.enableVibration(false);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    private Notification notification(String text){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1097,open,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE:PendingIntent.FLAG_UPDATE_CURRENT);return b.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Traslados · viaje en curso").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();}
    private void updateNotification(String text){try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID,notification(text));}catch(Exception ignored){}}

    private void maybeRefreshRadars(Location loc){
        if(!getSharedPreferences(SETTINGS,MODE_PRIVATE).getBoolean("radar_osm",true))return;
        long now=System.currentTimeMillis();if(radarLoading||now-lastRadarFetchAt<300000)return;radarLoading=true;lastRadarFetchAt=now;
        final double lat=loc.getLatitude(),lon=loc.getLongitude();radarPool.execute(()->{try{ArrayList<CameraPoint> got=fetchCameras(lat,lon);synchronized(cameras){cameras.clear();cameras.addAll(got);}}catch(Exception ignored){}finally{radarLoading=false;}});
    }

    private ArrayList<CameraPoint> fetchCameras(double lat,double lon)throws Exception{
        String q="[out:json][timeout:10];nwr(around:20000,"+lat+","+lon+")[\"highway\"=\"speed_camera\"];out center tags;";
        URL u=new URL("https://overpass-api.de/api/interpreter");HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestMethod("POST");c.setDoOutput(true);byte[] body=("data="+URLEncoder.encode(q,"UTF-8")).getBytes("UTF-8");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");c.setFixedLengthStreamingMode(body.length);try(OutputStream os=c.getOutputStream()){os.write(body);}InputStream in=c.getResponseCode()>=200&&c.getResponseCode()<300?c.getInputStream():c.getErrorStream();StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,"UTF-8"))){String line;while((line=br.readLine())!=null)sb.append(line);}JSONArray e=new JSONObject(sb.toString()).optJSONArray("elements");ArrayList<CameraPoint> out=new ArrayList<>();if(e!=null)for(int i=0;i<e.length();i++){JSONObject x=e.optJSONObject(i);if(x==null)continue;double la=x.optDouble("lat",Double.NaN),lo=x.optDouble("lon",Double.NaN);if((Double.isNaN(la)||Double.isNaN(lo))&&x.optJSONObject("center")!=null){la=x.optJSONObject("center").optDouble("lat",Double.NaN);lo=x.optJSONObject("center").optDouble("lon",Double.NaN);}if(Double.isNaN(la)||Double.isNaN(lo))continue;JSONObject tags=x.optJSONObject("tags");String max=tags==null?null:tags.optString("maxspeed",null);out.add(new CameraPoint(String.valueOf(x.optLong("id",i)),la,lo,max));}return out;
    }

    private void checkRadarAlert(Location loc){
        if(!getSharedPreferences(SETTINGS,MODE_PRIVATE).getBoolean("radar_osm",true))return;
        CameraPoint best=null;float bestM=Float.MAX_VALUE; synchronized(cameras){for(CameraPoint cp:cameras){Location x=new Location("camera");x.setLatitude(cp.lat);x.setLongitude(cp.lon);float d=loc.distanceTo(x);if(d<bestM){if(loc.hasBearing()&&loc.getSpeed()>3){float diff=Math.abs(angleDiff(loc.getBearing(),loc.bearingTo(x)));if(diff>80)continue;}bestM=d;best=cp;}}}
        if(best==null||bestM>700)return;long now=System.currentTimeMillis();if(best.id.equals(lastRadarAlertId)&&now-lastRadarAlertAt<600000)return;
        lastRadarAlertId=best.id;lastRadarAlertAt=now;radarAlertCount++;prefs.edit().putString(k(reservationId,"last_radar_id"),lastRadarAlertId).putLong(k(reservationId,"last_radar_at"),lastRadarAlertAt).putInt(k(reservationId,"radar_alerts"),radarAlertCount).apply();appendRadarEvent(best,bestM);
        final CameraPoint b=best;final int m=Math.round(bestM);main.post(()->{try{ToneGenerator tg=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,75);tg.startTone(ToneGenerator.TONE_PROP_BEEP2,180);main.postDelayed(()->{try{tg.startTone(ToneGenerator.TONE_PROP_ACK,180);}catch(Exception ignored){}main.postDelayed(tg::release,240);},230);}catch(Exception ignored){}try{Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(new long[]{0,90,90,90},-1));else v.vibrate(new long[]{0,90,90,90},-1);}}catch(Exception ignored){}String extra=b.maxspeed==null||b.maxspeed.isEmpty()?"":" · límite "+b.maxspeed;Toast.makeText(this,"Posible cámara OSM a "+m+" m"+extra,Toast.LENGTH_LONG).show();updateNotification("Posible cámara OSM a "+m+" m"+extra);});
    }

    private static float angleDiff(float a,float b){float d=(b-a+540)%360-180;return d;}
    private static class CameraPoint{final String id;final double lat,lon;final String maxspeed;CameraPoint(String i,double a,double o,String m){id=i;lat=a;lon=o;maxspeed=m;}}

    private static String k(String id,String suffix){return id+"_"+suffix;}
    private static String safe(String s){return s==null?"trip":s.replaceAll("[^A-Za-z0-9._-]","_");}
    private static File traceFile(Context c,String id){return new File(new File(c.getFilesDir(),"telemetry"),safe(id)+".jsonl");}
    private static String fmtKm(double km){return String.format(Locale.US,"%.1f km",km);}
    public static String fmtDuration(long ms){if(ms<0)ms=0;long sec=ms/1000,h=sec/3600,m=(sec%3600)/60,s=sec%60;if(h>0)return String.format(Locale.US,"%dh %02d min",h,m);if(m>0)return String.format(Locale.US,"%d min %02d s",m,s);return s+" s";}

    public static void prepareStart(Context c,String id,String code){if(id==null||id.isEmpty())return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);long now=System.currentTimeMillis(),start=p.getLong(k(id,"started_at"),0);if(start<=0)start=now;p.edit().putString("active_id",id).putBoolean(k(id,"active"),true).putString(k(id,"code"),code==null?"":code).putLong(k(id,"started_at"),start).remove(k(id,"finished_at")).putString(k(id,"state"),STATE_STARTING).putString(k(id,"last_error"),"").putLong(k(id,"heartbeat_at"),now).apply();}
    public static void markLaunchError(Context c,String id,String error){if(id==null||id.isEmpty())return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);p.edit().putBoolean(k(id,"active"),false).putString(k(id,"state"),STATE_ERROR).putString(k(id,"last_error"),error==null?"No se pudo iniciar el servicio GPS":error).putLong(k(id,"heartbeat_at"),System.currentTimeMillis()).apply();}

    public static JSONObject summary(Context c,String id){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONObject o=new JSONObject();try{long start=p.getLong(k(id,"started_at"),0),end=p.getLong(k(id,"finished_at"),0);boolean active=p.getBoolean(k(id,"active"),false);String state=p.getString(k(id,"state"),active?STATE_ACTIVE:(end>0?STATE_FINISHED:""));long heartbeat=p.getLong(k(id,"heartbeat_at"),0),moving=p.getLong(k(id,"moving_ms"),0),stopped=p.getLong(k(id,"stopped_ms"),0),now=System.currentTimeMillis(),elapsed=start>0?((active||end<=0)?now:end)-start:0,tracked=moving+stopped;double meters=Double.longBitsToDouble(p.getLong(k(id,"meters_bits"),Double.doubleToRawLongBits(0))),max=Double.longBitsToDouble(p.getLong(k(id,"max_speed_bits"),Double.doubleToRawLongBits(0))),cur=Double.longBitsToDouble(p.getLong(k(id,"current_speed_bits"),Double.doubleToRawLongBits(0)));o.put("active",active);o.put("state",state==null?"":state);o.put("last_error",p.getString(k(id,"last_error"),""));o.put("providers",p.getInt(k(id,"providers"),0));o.put("heartbeat_at",heartbeat);o.put("heartbeat_age_ms",heartbeat>0?Math.max(0,now-heartbeat):Long.MAX_VALUE);o.put("started_at",start);o.put("finished_at",end);o.put("elapsed_ms",Math.max(0,elapsed));o.put("moving_ms",moving);o.put("stopped_ms",stopped);o.put("signal_gap_ms",Math.max(0,elapsed-tracked));o.put("distance_km",meters/1000.0);o.put("current_speed_kmh",cur);o.put("max_speed_kmh",max);o.put("avg_moving_speed_kmh",moving>0?(meters/(moving/1000.0))*3.6:0);o.put("points",p.getInt(k(id,"points"),0));o.put("radar_alerts",p.getInt(k(id,"radar_alerts"),0));o.put("trace_exists",traceFile(c,id).isFile());}catch(Exception ignored){}return o;
    }

    public static String mapsUrl(Context c,String id){
        ArrayList<double[]> pts=readPointCoords(c,id);if(pts.size()<2)return null;StringBuilder u=new StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving");double[] a=pts.get(0),z=pts.get(pts.size()-1);u.append("&origin=").append(a[0]).append(',').append(a[1]).append("&destination=").append(z[0]).append(',').append(z[1]);if(pts.size()>2){u.append("&waypoints=");int max=8;for(int i=1;i<=max;i++){int idx=(int)Math.round(i*(pts.size()-1.0)/(max+1.0));if(idx<=0||idx>=pts.size()-1)continue;double[] p=pts.get(idx);if(u.charAt(u.length()-1)!='=')u.append('|');u.append(p[0]).append(',').append(p[1]);}}return u.toString();
    }

    public static String buildGpx(Context c,String id,String code){
        File f=traceFile(c,id);if(!f.isFile())return null;StringBuilder g=new StringBuilder();g.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Traslados Conductor v10.9\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n<trk><name>").append(xml(code==null?id:code)).append("</name><trkseg>\n");SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);iso.setTimeZone(TimeZone.getTimeZone("UTC"));try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"))){String line;while((line=br.readLine())!=null){JSONObject o=new JSONObject(line);if(!"point".equals(o.optString("type","point")))continue;g.append("<trkpt lat=\"").append(o.optDouble("lat")).append("\" lon=\"").append(o.optDouble("lon")).append("\">");if(o.has("altitude_m"))g.append("<ele>").append(o.optDouble("altitude_m")).append("</ele>");g.append("<time>").append(iso.format(new Date(o.optLong("t")))).append("</time></trkpt>\n");}}catch(Exception e){return null;}g.append("</trkseg></trk></gpx>");return g.toString();
    }

    private static ArrayList<double[]> readPointCoords(Context c,String id){ArrayList<double[]> out=new ArrayList<>();File f=traceFile(c,id);if(!f.isFile())return out;try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"))){String line;while((line=br.readLine())!=null){JSONObject o=new JSONObject(line);if("point".equals(o.optString("type","point")))out.add(new double[]{o.optDouble("lat"),o.optDouble("lon")});}}catch(Exception ignored){}return out;}
    private static String xml(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
