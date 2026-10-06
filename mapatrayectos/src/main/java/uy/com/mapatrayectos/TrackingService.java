package uy.com.mapatrayectos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import androidx.annotation.Nullable;
import java.util.*;

public class TrackingService extends Service implements LocationListener {
    public static final String ACTION_START_SHIFT="uy.com.mapatrayectos.START_SHIFT";
    public static final String ACTION_STOP_SHIFT="uy.com.mapatrayectos.STOP_SHIFT";
    public static final String ACTION_START_TRIP="uy.com.mapatrayectos.START_TRIP";
    public static final String ACTION_STOP_TRIP="uy.com.mapatrayectos.STOP_TRIP";
    public static final String ACTION_REQUEST_STATE="uy.com.mapatrayectos.REQUEST_STATE";
    public static final String ACTION_STATE="uy.com.mapatrayectos.STATE";

    private static final String CHANNEL="mapa_trayectos_tracking";
    private static final int NOTIFICATION_ID=9115;
    private static final float MOVING_KMH=3.0f;
    private static final float MAX_ACCEPTED_ACCURACY_M=45.0f;
    private static final float MAX_STATS_ACCURACY_M=25.0f;
    private static final float MAX_PLAUSIBLE_KMH=160.0f;
    private static final float MAX_ACCEL_MPS2=7.0f;
    private static final long NETWORK_FALLBACK_AFTER_MS=15000L;
    private static final long MAX_STATS_GAP_MS=15000L;

    private LocationManager lm;
    private TrackDb db;
    private SharedPreferences sp;
    private Location lastAccepted;
    private long lastAcceptedTs=0;
    private float lastAcceptedAccuracy=999f;
    private float lastBearing=0f;
    private float filteredSpeed=0f;
    private long filteredSpeedTs=0L;
    private long lastGoodGpsTs=0L;
    private float prevMaxSample=-1f;
    private long prevMaxSampleTs=0L;
    private boolean shiftActive=false,tripActive=false;
    private String shiftId="",tripId="",zone="Buscando zona…";
    private long shiftStarted=0,tripStarted=0;
    private double shiftDistance=0,tripDistance=0;
    private long shiftMoving=0,shiftStopped=0,shiftTripMs=0,tripMoving=0,tripStopped=0;
    private double tripMax=0;

    @Override public void onCreate(){
        super.onCreate();db=new TrackDb(this);sp=getSharedPreferences("tracking_state",MODE_PRIVATE);loadState();Api.init(this);createChannel();
        if(shiftActive){startForeground(NOTIFICATION_ID,notification());startLocation();}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String a=intent==null?null:intent.getAction();
        if(ACTION_START_SHIFT.equals(a))startShift();
        else if(ACTION_STOP_SHIFT.equals(a))stopShift();
        else if(ACTION_START_TRIP.equals(a))startTrip();
        else if(ACTION_STOP_TRIP.equals(a))stopTrip();
        else if(ACTION_REQUEST_STATE.equals(a))broadcastState();
        else if(shiftActive){startForeground(NOTIFICATION_ID,notification());startLocation();}
        return shiftActive?START_STICKY:START_NOT_STICKY;
    }

    private void startShift(){
        if(shiftActive){broadcastState();return;}
        long now=System.currentTimeMillis();shiftActive=true;tripActive=false;shiftId=UUID.randomUUID().toString();tripId="";shiftStarted=now;tripStarted=0;shiftDistance=tripDistance=0;shiftMoving=shiftStopped=shiftTripMs=tripMoving=tripStopped=0;tripMax=0;prevMaxSample=-1f;prevMaxSampleTs=0;zone=lastAccepted==null?"Buscando zona…":ZoneResolver.resolve(lastAccepted.getLatitude(),lastAccepted.getLongitude());db.beginShift(shiftId,now,zone);persist();startForeground(NOTIFICATION_ID,notification());startLocation();broadcastState();Api.syncPendingAsync();
    }

    private void stopShift(){
        if(!shiftActive){broadcastState();return;}
        if(tripActive)stopTripInternal(false);
        long now=System.currentTimeMillis();db.updateShift(shiftId,now,shiftDistance,shiftMoving,shiftStopped,shiftTripMs,zone);String endedShift=shiftId;shiftActive=false;tripActive=false;persist();broadcastState();Api.syncShiftAsync(endedShift);stopLocation();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }

    private void startTrip(){
        if(!shiftActive||tripActive){broadcastState();return;}
        tripActive=true;tripId=UUID.randomUUID().toString();tripStarted=System.currentTimeMillis();tripDistance=0;tripMoving=0;tripStopped=0;tripMax=0;prevMaxSample=-1f;prevMaxSampleTs=0L;db.beginTrip(tripId,shiftId,tripStarted,zone);persist();broadcastState();updateNotification();
    }

    private void stopTrip(){stopTripInternal(true);}

    private void stopTripInternal(boolean send){
        if(!tripActive){if(send)broadcastState();return;}
        long now=System.currentTimeMillis();double avg=tripMoving>0?(tripDistance/1000.0)/(tripMoving/3600000.0):0;db.updateTrip(tripId,now,tripDistance,tripMoving,tripStopped,tripMax,avg,zone);shiftTripMs+=Math.max(0,now-tripStarted);String endedTrip=tripId;tripActive=false;tripId="";tripStarted=0;tripDistance=0;tripMoving=0;tripStopped=0;tripMax=0;prevMaxSample=-1f;prevMaxSampleTs=0;db.updateShift(shiftId,null,shiftDistance,shiftMoving,shiftStopped,shiftTripMs,zone);persist();Api.syncTripAsync(endedTrip);if(send)broadcastState();updateNotification();
    }

    private void startLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        if(lm==null)lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(lm==null)return;
        try{
            Location best=null;Location g=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);Location n=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            long now=System.currentTimeMillis();
            if(g!=null&&Math.abs(now-g.getTime())<120000L)best=g;
            if(best==null&&n!=null&&Math.abs(now-n.getTime())<60000L)best=n;
            if(best!=null)onLocationChanged(best);
            if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,2f,this,Looper.getMainLooper());
            if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,6000L,12f,this,Looper.getMainLooper());
        }catch(Exception ignored){}
    }

    private void stopLocation(){if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){} }

    @Override public void onLocationChanged(Location loc){
        if(loc==null)return;
        final long ts=loc.getTime()>0?loc.getTime():System.currentTimeMillis();
        final float accuracy=loc.hasAccuracy()?loc.getAccuracy():50f;
        final boolean isGps=LocationManager.GPS_PROVIDER.equals(loc.getProvider());
        final boolean isNetwork=LocationManager.NETWORK_PROVIDER.equals(loc.getProvider());

        if(isGps&&accuracy<=60f)lastGoodGpsTs=ts;
        if(isNetwork&&lastGoodGpsTs>0&&Math.abs(ts-lastGoodGpsTs)<NETWORK_FALLBACK_AFTER_MS){broadcastState();return;}
        if(accuracy>MAX_ACCEPTED_ACCURACY_M){broadcastState();return;}

        if(lastAccepted==null){
            acceptBaseline(loc,ts,accuracy,isGps);
            return;
        }

        long dt=ts-lastAcceptedTs;
        if(dt<=0){broadcastState();return;}
        double dist=lastAccepted.distanceTo(loc);
        double dtSec=dt/1000.0;
        float derived=(float)((dist/dtSec)*3.6);

        // Saltos de posicion imposibles: no contaminan ni distancia ni la proxima referencia.
        if(derived>MAX_PLAUSIBLE_KMH||(dt<800L&&derived>60f)){broadcastState();return;}

        float raw=loc.hasSpeed()?Math.max(0f,loc.getSpeed()*3.6f):derived;
        boolean rawPlausible=raw<=MAX_PLAUSIBLE_KMH;
        float candidate=rawPlausible?raw:derived;

        // Un salto brusco hacia arriba en pocos segundos es tratado como glitch.
        if(filteredSpeedTs>0&&candidate>filteredSpeed&&dt<=5000L){
            float accel=(float)(((candidate-filteredSpeed)/3.6)/Math.max(0.25,dtSec));
            if(accel>MAX_ACCEL_MPS2)candidate=derived;
        }
        if(candidate>MAX_PLAUSIBLE_KMH)candidate=filteredSpeed;

        // Si velocidad del sensor y desplazamiento discrepan mucho con precision mediocre,
        // preferimos la velocidad derivada del recorrido aceptado.
        if(loc.hasSpeed()&&accuracy>12f){
            float diff=Math.abs(candidate-derived);
            float tolerance=Math.max(18f,Math.max(candidate,derived)*0.45f);
            if(diff>tolerance)candidate=derived;
        }

        float noise=Math.max(1.5f,Math.min(6f,(lastAcceptedAccuracy+accuracy)*0.10f));
        double segment=(dist<noise&&candidate<MOVING_KMH)?0:dist;
        if(derived>140f)segment=0;

        long statDt=dt<=MAX_STATS_GAP_MS?dt:0L;
        boolean moving=candidate>=MOVING_KMH||derived>=MOVING_KMH;
        zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());

        if(shiftActive&&statDt>0){
            shiftDistance+=segment;
            if(moving)shiftMoving+=statDt;else shiftStopped+=statDt;
            if(tripActive){
                tripDistance+=segment;
                if(moving)tripMoving+=statDt;else tripStopped+=statDt;
                considerConfirmedMaximum(candidate,accuracy,isGps,ts);
            }
        }

        filteredSpeed=Math.max(0f,candidate);filteredSpeedTs=ts;
        if(loc.hasBearing()&&filteredSpeed>2f)lastBearing=loc.getBearing();
        lastAccepted=new Location(loc);lastAcceptedTs=ts;lastAcceptedAccuracy=accuracy;

        if(shiftActive){
            db.addPoint(UUID.randomUUID().toString(),shiftId,tripActive?tripId:null,ts,loc.getLatitude(),loc.getLongitude(),accuracy,filteredSpeed,lastBearing,zone,tripActive);
            db.updateShift(shiftId,null,shiftDistance,shiftMoving,shiftStopped,shiftTripMs+(tripActive?Math.max(0,System.currentTimeMillis()-tripStarted):0),zone);
            if(tripActive){double avg=tripMoving>0?(tripDistance/1000.0)/(tripMoving/3600000.0):0;db.updateTrip(tripId,null,tripDistance,tripMoving,tripStopped,tripMax,avg,zone);}persist();
        }
        broadcastState();updateNotification();
    }

    private void acceptBaseline(Location loc,long ts,float accuracy,boolean isGps){
        float raw=loc.hasSpeed()?Math.max(0f,loc.getSpeed()*3.6f):0f;
        filteredSpeed=raw<=MAX_PLAUSIBLE_KMH?raw:0f;filteredSpeedTs=ts;
        if(loc.hasBearing()&&filteredSpeed>2f)lastBearing=loc.getBearing();
        lastAccepted=new Location(loc);lastAcceptedTs=ts;lastAcceptedAccuracy=accuracy;
        if(isGps)lastGoodGpsTs=ts;
        zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());
        if(shiftActive){db.addPoint(UUID.randomUUID().toString(),shiftId,tripActive?tripId:null,ts,loc.getLatitude(),loc.getLongitude(),accuracy,filteredSpeed,lastBearing,zone,tripActive);persist();}
        broadcastState();updateNotification();
    }

    private void considerConfirmedMaximum(float speed,float accuracy,boolean isGps,long ts){
        if(!tripActive||!isGps||accuracy>MAX_STATS_ACCURACY_M||speed<MOVING_KMH||speed>MAX_PLAUSIBLE_KMH)return;
        if(prevMaxSample>=0f&&prevMaxSampleTs>0){
            long dt=Math.abs(ts-prevMaxSampleTs);
            if(dt>=500L&&dt<=8000L){
                float delta=Math.abs(speed-prevMaxSample);
                float tolerance=Math.max(8f,Math.max(speed,prevMaxSample)*0.25f);
                float accel=(float)(((delta/3.6)/(dt/1000.0)));
                if(delta<=tolerance&&accel<=MAX_ACCEL_MPS2)tripMax=Math.max(tripMax,Math.max(speed,prevMaxSample));
            }
        }
        prevMaxSample=speed;prevMaxSampleTs=ts;
    }

    private void broadcastState(){
        Intent i=new Intent(ACTION_STATE).setPackage(getPackageName());i.putExtra("shift_active",shiftActive);i.putExtra("trip_active",tripActive);i.putExtra("shift_id",shiftId);i.putExtra("trip_id",tripId);i.putExtra("shift_started",shiftStarted);i.putExtra("trip_started",tripStarted);i.putExtra("shift_distance",shiftDistance);i.putExtra("trip_distance",tripDistance);i.putExtra("shift_moving",shiftMoving);i.putExtra("shift_stopped",shiftStopped);i.putExtra("shift_trip_ms",shiftTripMs+(tripActive?Math.max(0,System.currentTimeMillis()-tripStarted):0));i.putExtra("trip_moving",tripMoving);i.putExtra("trip_stopped",tripStopped);i.putExtra("trip_max",tripMax);i.putExtra("zone",zone);if(lastAccepted!=null){i.putExtra("has_location",true);i.putExtra("lat",lastAccepted.getLatitude());i.putExtra("lon",lastAccepted.getLongitude());i.putExtra("accuracy",lastAcceptedAccuracy);i.putExtra("speed",filteredSpeed);i.putExtra("bearing",lastBearing);}sendBroadcast(i);
    }

    private void persist(){sp.edit().putBoolean("shift_active",shiftActive).putBoolean("trip_active",tripActive).putString("shift_id",shiftId).putString("trip_id",tripId).putLong("shift_started",shiftStarted).putLong("trip_started",tripStarted).putLong("shift_distance_bits",Double.doubleToLongBits(shiftDistance)).putLong("trip_distance_bits",Double.doubleToLongBits(tripDistance)).putLong("shift_moving",shiftMoving).putLong("shift_stopped",shiftStopped).putLong("shift_trip_ms",shiftTripMs).putLong("trip_moving",tripMoving).putLong("trip_stopped",tripStopped).putLong("trip_max_bits",Double.doubleToLongBits(tripMax)).putString("zone",zone).apply();}

    private void loadState(){shiftActive=sp.getBoolean("shift_active",false);tripActive=sp.getBoolean("trip_active",false);shiftId=sp.getString("shift_id","");tripId=sp.getString("trip_id","");shiftStarted=sp.getLong("shift_started",0);tripStarted=sp.getLong("trip_started",0);shiftDistance=Double.longBitsToDouble(sp.getLong("shift_distance_bits",Double.doubleToLongBits(0)));tripDistance=Double.longBitsToDouble(sp.getLong("trip_distance_bits",Double.doubleToLongBits(0)));shiftMoving=sp.getLong("shift_moving",0);shiftStopped=sp.getLong("shift_stopped",0);shiftTripMs=sp.getLong("shift_trip_ms",0);tripMoving=sp.getLong("trip_moving",0);tripStopped=sp.getLong("trip_stopped",0);tripMax=Double.longBitsToDouble(sp.getLong("trip_max_bits",Double.doubleToLongBits(0)));zone=sp.getString("zone","Buscando zona…");}

    private Notification notification(){
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);String title=tripActive?"Viaje activo":(shiftActive?"Jornada activa":"Mapa Trayectos");String text=String.format(Locale.getDefault(),"%.1f km · %s",(tripActive?tripDistance:shiftDistance)/1000.0,zone);return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(title).setContentText(text).setOngoing(shiftActive).setContentIntent(pi).setOnlyAlertOnce(true).build();
    }

    private void updateNotification(){if(shiftActive){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFICATION_ID,notification());}}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Mapa Trayectos · Seguimiento",NotificationManager.IMPORTANCE_LOW));}}

    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onDestroy(){stopLocation();db.close();super.onDestroy();}
    @Nullable @Override public IBinder onBind(Intent intent){return null;}
}
