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

    private LocationManager lm;
    private TrackDb db;
    private SharedPreferences sp;
    private Location last;
    private long lastTs=0;
    private float lastBearing=0f;
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
        else if(ACTION_REQUEST_STATE.equals(a))broadcastState(null);
        else if(shiftActive){startForeground(NOTIFICATION_ID,notification());startLocation();}
        return shiftActive?START_STICKY:START_NOT_STICKY;
    }

    private void startShift(){
        if(shiftActive){broadcastState(null);return;}
        long now=System.currentTimeMillis();shiftActive=true;tripActive=false;shiftId=UUID.randomUUID().toString();tripId="";shiftStarted=now;tripStarted=0;shiftDistance=tripDistance=0;shiftMoving=shiftStopped=shiftTripMs=tripMoving=tripStopped=0;tripMax=0;zone=last==null?"Buscando zona…":ZoneResolver.resolve(last.getLatitude(),last.getLongitude());db.beginShift(shiftId,now,zone);persist();startForeground(NOTIFICATION_ID,notification());startLocation();broadcastState(last);Api.syncPendingAsync();
    }

    private void stopShift(){
        if(!shiftActive){broadcastState(last);return;}
        if(tripActive)stopTripInternal(false);
        long now=System.currentTimeMillis();db.updateShift(shiftId,now,shiftDistance,shiftMoving,shiftStopped,shiftTripMs,zone);String endedShift=shiftId;shiftActive=false;tripActive=false;persist();broadcastState(last);Api.syncShiftAsync(endedShift);stopLocation();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }

    private void startTrip(){
        if(!shiftActive||tripActive){broadcastState(last);return;}
        tripActive=true;tripId=UUID.randomUUID().toString();tripStarted=System.currentTimeMillis();tripDistance=0;tripMoving=0;tripStopped=0;tripMax=0;db.beginTrip(tripId,shiftId,tripStarted,zone);persist();broadcastState(last);updateNotification();
    }

    private void stopTrip(){stopTripInternal(true);}

    private void stopTripInternal(boolean send){
        if(!tripActive){if(send)broadcastState(last);return;}
        long now=System.currentTimeMillis();double avg=tripMoving>0?(tripDistance/1000.0)/(tripMoving/3600000.0):0;db.updateTrip(tripId,now,tripDistance,tripMoving,tripStopped,tripMax,avg,zone);shiftTripMs+=Math.max(0,now-tripStarted);String endedTrip=tripId;tripActive=false;tripId="";tripStarted=0;tripDistance=0;tripMoving=0;tripStopped=0;tripMax=0;db.updateShift(shiftId,null,shiftDistance,shiftMoving,shiftStopped,shiftTripMs,zone);persist();Api.syncTripAsync(endedTrip);if(send)broadcastState(last);updateNotification();
    }

    private void startLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        if(lm==null)lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(lm==null)return;
        try{
            Location best=null;Location g=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);Location n=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);if(g!=null)best=g;if(n!=null&&(best==null||n.getTime()>best.getTime()))best=n;if(best!=null)onLocationChanged(best);
            if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,3f,this,Looper.getMainLooper());
            if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000L,10f,this,Looper.getMainLooper());
        }catch(Exception ignored){}
    }

    private void stopLocation(){if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){} }

    @Override public void onLocationChanged(Location loc){
        if(loc==null)return;long ts=loc.getTime()>0?loc.getTime():System.currentTimeMillis();float accuracy=loc.hasAccuracy()?loc.getAccuracy():25f;if(accuracy>100f){broadcastState(loc);return;}
        double dist=0;long dt=0;float derived=0;
        if(last!=null){dt=Math.max(0,ts-lastTs);dist=last.distanceTo(loc);if(dt>0)derived=(float)((dist/(dt/1000.0))*3.6);if(dist<Math.max(3.5,accuracy*0.30)&&(!loc.hasSpeed()||loc.getSpeed()*3.6f<MOVING_KMH))dist=0;if(derived>190f)dist=0;}
        float speed=loc.hasSpeed()?Math.max(0,loc.getSpeed()*3.6f):Math.max(0,derived);if(speed>190f)speed=0;
        float bearing=loc.hasBearing()?loc.getBearing():lastBearing;if(loc.hasBearing()&&speed>2f)lastBearing=bearing;
        zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());
        if(shiftActive&&last!=null&&dt>0&&dt<60000){boolean moving=speed>=MOVING_KMH||derived>=MOVING_KMH;shiftDistance+=dist;if(moving)shiftMoving+=dt;else shiftStopped+=dt;if(tripActive){tripDistance+=dist;if(moving)tripMoving+=dt;else tripStopped+=dt;tripMax=Math.max(tripMax,speed);}}
        if(shiftActive){db.addPoint(UUID.randomUUID().toString(),shiftId,tripActive?tripId:null,ts,loc.getLatitude(),loc.getLongitude(),accuracy,speed,bearing,zone,tripActive);db.updateShift(shiftId,null,shiftDistance,shiftMoving,shiftStopped,shiftTripMs+(tripActive?Math.max(0,System.currentTimeMillis()-tripStarted):0),zone);if(tripActive){double avg=tripMoving>0?(tripDistance/1000.0)/(tripMoving/3600000.0):0;db.updateTrip(tripId,null,tripDistance,tripMoving,tripStopped,tripMax,avg,zone);}persist();}
        last=loc;lastTs=ts;broadcastState(loc);updateNotification();
    }

    private void broadcastState(Location loc){
        Intent i=new Intent(ACTION_STATE).setPackage(getPackageName());i.putExtra("shift_active",shiftActive);i.putExtra("trip_active",tripActive);i.putExtra("shift_id",shiftId);i.putExtra("trip_id",tripId);i.putExtra("shift_started",shiftStarted);i.putExtra("trip_started",tripStarted);i.putExtra("shift_distance",shiftDistance);i.putExtra("trip_distance",tripDistance);i.putExtra("shift_moving",shiftMoving);i.putExtra("shift_stopped",shiftStopped);i.putExtra("shift_trip_ms",shiftTripMs+(tripActive?Math.max(0,System.currentTimeMillis()-tripStarted):0));i.putExtra("trip_moving",tripMoving);i.putExtra("trip_stopped",tripStopped);i.putExtra("trip_max",tripMax);i.putExtra("zone",zone);if(loc!=null){i.putExtra("has_location",true);i.putExtra("lat",loc.getLatitude());i.putExtra("lon",loc.getLongitude());i.putExtra("accuracy",loc.hasAccuracy()?loc.getAccuracy():0f);i.putExtra("speed",loc.hasSpeed()?loc.getSpeed()*3.6f:0f);i.putExtra("bearing",loc.hasBearing()?loc.getBearing():lastBearing);}sendBroadcast(i);
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
