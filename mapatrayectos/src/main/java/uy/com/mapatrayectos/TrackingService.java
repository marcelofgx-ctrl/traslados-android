package uy.com.mapatrayectos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.util.Log;
import android.graphics.PixelFormat;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.ImageView;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import java.util.*;

public class TrackingService extends Service implements LocationListener {
    public static final String ACTION_START_SHIFT="uy.com.mapatrayectos.START_SHIFT";
    public static final String ACTION_STOP_SHIFT="uy.com.mapatrayectos.STOP_SHIFT";
    public static final String ACTION_PAUSE_SHIFT="uy.com.mapatrayectos.PAUSE_SHIFT";
    public static final String ACTION_RESUME_SHIFT="uy.com.mapatrayectos.RESUME_SHIFT";
    public static final String ACTION_START_TRIP="uy.com.mapatrayectos.START_TRIP";
    public static final String ACTION_PICKUP_PASSENGER="uy.com.mapatrayectos.PICKUP_PASSENGER";
    public static final String ACTION_START_STOP="uy.com.mapatrayectos.START_STOP";
    public static final String ACTION_END_STOP="uy.com.mapatrayectos.END_STOP";
    public static final String ACTION_STOP_TRIP="uy.com.mapatrayectos.STOP_TRIP";
    public static final String ACTION_REQUEST_STATE="uy.com.mapatrayectos.REQUEST_STATE";
    public static final String ACTION_UI_VISIBLE="uy.com.mapatrayectos.UI_VISIBLE";
    public static final String ACTION_UI_HIDDEN="uy.com.mapatrayectos.UI_HIDDEN";
    public static final String ACTION_STATE="uy.com.mapatrayectos.STATE";
    public static final String ACTION_FLOATING_BUBBLE_POSITION="uy.com.mapatrayectos.FLOATING_BUBBLE_POSITION";

    private static final String CHANNEL="mapa_trayectos_tracking";
    private static final String IDLE_CHANNEL="mapa_trayectos_burbuja";
    private static final long IDLE_BUBBLE_HEARTBEAT_MS=5000L;
    private static final int NOTIFICATION_ID=9115;
    private static final float ZERO_DISPLAY_KMH=3.0f;
    private static final float START_MOVING_KMH=5.0f;
    private static final float STOP_MOVING_KMH=2.5f;
    private static final float STATIONARY_GHOST_KMH=13.0f;
    private static final float STRONG_GPS_SPEED_KMH=14.0f;
    private static final float MAX_ACCEPTED_ACCURACY_M=45.0f;
    private static final float MAX_STATS_ACCURACY_M=25.0f;
    private static final float MAX_PLAUSIBLE_KMH=160.0f;
    private static final float MAX_ACCEL_MPS2=7.0f;
    private static final long NETWORK_FALLBACK_AFTER_MS=15000L;
    private static final long MAX_STATS_GAP_MS=15000L;
    private static final long CLOCK_TICK_MS=1000L;
    private static final long SPEED_STALE_DISPLAY_MS=3500L;
    private static final long SPEED_STALE_STOP_MS=6000L;
    private static final long SPEED_STALE_FORCE_STOP_MS=10000L;
    private static final long BUBBLE_SHOW_DELAY_MS=320L;

    private LocationManager lm;
    private TrackDb db;
    private SharedPreferences sp;
    private Handler clockHandler;
    private Location lastAccepted;
    private long lastAcceptedTs=0;
    private float lastAcceptedAccuracy=999f,lastBearing=0f,filteredSpeed=0f;
    private long filteredSpeedTs=0L,lastGoodGpsTs=0L;
    private float prevMaxSample=-1f;
    private long prevMaxSampleTs=0L;
    private boolean vehicleMoving=false,shiftActive=false,tripActive=false,appVisible=false,tripOriginCaptured=false,stopActive=false,shiftPaused=false;
    private int movingEvidence=0,stoppedEvidence=0,clockPersistTick=0;
    private long lastClockAccountedAt=0L;
    private String shiftId="",tripId="",zone="Buscando zona…",tripType="other",tripStatus="completed",tripStage="none",stopId="";
    private long shiftStarted=0,tripStarted=0,pickupAt=0,stopStarted=0,pausedAt=0,totalPauseMs=0;
    private int pauseCount=0,shiftCompletedTrips=0;
    private double shiftDistance=0,tripDistance=0,tripMax=0,tripAmountUyu=0;
    private long shiftMoving=0,shiftStopped=0,shiftTripMs=0,tripMoving=0,tripStopped=0;

    private WindowManager bubbleWm;private View bubbleView;private WindowManager.LayoutParams bubbleLp;
    private ReminderCallout activeTransferCallout;
    private WindowManager.LayoutParams activeTransferLp;
    private final Runnable transferTimeout=this::hideTransferCallout;
    private final BroadcastReceiver transferEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(bubbleView!=null)bubbleView.invalidate();
            if(TransferAlerts.ACTION_NEW.equals(i.getAction())){
                if(bubbleView!=null)showTransferCallout();
            }else if(TransferAlerts.pendingCount(TrackingService.this)==0)
                hideTransferCallout();
        }
    };

    private final Runnable bubbleShow=()->showBubbleIfAllowed();
    private final Runnable bubbleHeartbeat=new Runnable(){
        @Override public void run(){
            if(clockHandler==null||bubbleView==null)return;
            if(appVisible||!Settings.canDrawOverlays(TrackingService.this)){
                hideBubble();
                if(!FloatingShortcutPolicy.maintainIdleService(appVisible,Settings.canDrawOverlays(TrackingService.this),shiftActive)){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
                return;
            }
            // Keep the R23.0 reminder anchor fresh even if the journey timer is off.
            saveFloatingBubbleState(true);
            clockHandler.postDelayed(this,IDLE_BUBBLE_HEARTBEAT_MS);
        }
    };
    private void promoteActiveTracking(){
        if(Build.VERSION.SDK_INT>=29)
            startForeground(NOTIFICATION_ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFICATION_ID,notification());
    }
    private void promoteIdleBubble(){
        if(!Settings.canDrawOverlays(this))return;
        Notification n=idleBubbleNotification();
        if(Build.VERSION.SDK_INT>=34)
            startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION_ID,n);
    }
    private Notification idleBubbleNotification(){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent tap=PendingIntent.getActivity(this,19,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,IDLE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Mapa Trayectos · acceso rápido")
            .setContentText("Burbuja disponible · jornada sin iniciar · GPS apagado")
            .setOnlyAlertOnce(true).setOngoing(true).setContentIntent(tap).build();
    }

    private final Runnable clockTick=new Runnable(){
        @Override public void run(){
            if(!shiftActive||clockHandler==null)return;
            long now=System.currentTimeMillis();expireStaleMotion(now);accountClock(now);broadcastState();clockPersistTick++;
            if(clockPersistTick>=5){clockPersistTick=0;persist();persistLiveStats(now);updateNotification();if(bubbleView!=null)saveFloatingBubbleState(true);}
            clockHandler.postDelayed(this,CLOCK_TICK_MS);
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        IntentFilter tf=new IntentFilter();tf.addAction(TransferAlerts.ACTION_NEW);
        tf.addAction(TransferAlerts.ACTION_COUNT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(transferEvents,tf,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(transferEvents,tf);
        appVisible=getSharedPreferences("bubble_state",MODE_PRIVATE).getBoolean("ui_visible",true);getSharedPreferences("bubble_state",MODE_PRIVATE).edit().putBoolean("floating_visible",false).apply();db=new TrackDb(this);sp=getSharedPreferences("tracking_state",MODE_PRIVATE);clockHandler=new Handler(Looper.getMainLooper());loadState();shiftCompletedTrips=db.completedTripsForShift(shiftId);Api.init(this);createChannel();
        if(tripActive){JSONObjectStub t=readTripStartState();tripOriginCaptured=t.hasStart;}
        if(shiftActive){lastClockAccountedAt=System.currentTimeMillis();promoteActiveTracking();if(!shiftPaused)startLocation();startClock();showBubbleIfAllowed();}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String a=intent==null?null:intent.getAction();
        if(ACTION_START_SHIFT.equals(a))startShift(intent);
        else if(ACTION_STOP_SHIFT.equals(a))stopShift();
        else if(ACTION_PAUSE_SHIFT.equals(a))pauseShift();
        else if(ACTION_RESUME_SHIFT.equals(a))resumeShift();
        else if(ACTION_START_TRIP.equals(a))startTrip(intent==null?null:intent.getStringExtra("trip_type"));
        else if(ACTION_PICKUP_PASSENGER.equals(a))pickupPassenger();
        else if(ACTION_START_STOP.equals(a))startRegisteredStop();
        else if(ACTION_END_STOP.equals(a))endRegisteredStop();
        else if(ACTION_STOP_TRIP.equals(a))stopTrip(intent==null?null:intent.getStringExtra("trip_status"),intent!=null&&intent.hasExtra("amount_uyu")?intent.getDoubleExtra("amount_uyu",0):0);
        else if(ACTION_UI_VISIBLE.equals(a)){
            appVisible=true;
            if(clockHandler!=null)clockHandler.removeCallbacks(bubbleShow);
            hideBubble();
            if(!shiftActive){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
            else broadcastState();
        }
        else if(ACTION_UI_HIDDEN.equals(a)){
            appVisible=false;
            if(!shiftActive){
                if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
                try{promoteIdleBubble();}
                catch(Exception e){Log.w("MapaBubble","Idle foreground shortcut rejected",e);stopSelf();return START_NOT_STICKY;}
            }
            if(clockHandler!=null){
                clockHandler.removeCallbacks(bubbleShow);
                clockHandler.postDelayed(bubbleShow,BUBBLE_SHOW_DELAY_MS);
            }else showBubbleIfAllowed();
        }
        else if(ACTION_REQUEST_STATE.equals(a))broadcastState();
        else if(shiftActive){
            promoteActiveTracking();if(!shiftPaused)startLocation();startClock();showBubbleIfAllowed();
        }else if(!appVisible&&Settings.canDrawOverlays(this)){
            // START_STICKY service restart when the app was previously minimized.
            promoteIdleBubble();showBubbleIfAllowed();
        }else stopSelf();
        return FloatingShortcutPolicy.maintainIdleService(appVisible,Settings.canDrawOverlays(this),shiftActive)?START_STICKY:START_NOT_STICKY;
    }

    private void startShift(Intent source){
        if(shiftActive){broadcastState();return;}
        appVisible=source!=null&&source.getBooleanExtra("ui_visible",false);
        long now=System.currentTimeMillis();shiftActive=true;tripActive=false;stopActive=false;shiftPaused=false;pausedAt=totalPauseMs=0;pauseCount=0;shiftCompletedTrips=0;shiftId=UUID.randomUUID().toString();tripId="";stopId="";shiftStarted=now;tripStarted=pickupAt=stopStarted=0;shiftDistance=tripDistance=0;shiftMoving=shiftStopped=shiftTripMs=tripMoving=tripStopped=0;tripMax=tripAmountUyu=0;tripType="other";tripStatus="completed";tripStage="none";prevMaxSample=-1f;prevMaxSampleTs=0;movingEvidence=stoppedEvidence=0;lastClockAccountedAt=now;vehicleMoving=false;filteredSpeed=0f;zone=lastAccepted==null?"Buscando zona…":ZoneResolver.resolve(lastAccepted.getLatitude(),lastAccepted.getLongitude());db.beginShift(shiftId,now,zone);persist();promoteActiveTracking();startLocation();startClock();broadcastState();showBubbleIfAllowed();Api.syncPendingAsync();
    }

    private void stopShift(){
        if(!shiftActive){broadcastState();return;}
        long now=System.currentTimeMillis();expireStaleMotion(now);accountClock(now);if(tripActive)stopTripInternal(false,"completed",0);if(shiftPaused){totalPauseMs+=Math.max(0,now-pausedAt);shiftPaused=false;pausedAt=0;}db.updateShiftPaused(shiftId,totalPauseMs,pauseCount);db.updateShift(shiftId,now,shiftDistance,shiftMoving,shiftStopped,shiftTripMs,zone);String endedShift=shiftId;shiftActive=false;tripActive=false;persist();broadcastState();Api.syncShiftAsync(endedShift);stopClock();stopLocation();
        if(!appVisible&&Settings.canDrawOverlays(this)){
            // End the GPS journey but keep the already visible shortcut alive.
            promoteIdleBubble();showBubbleIfAllowed();
        }else{
            hideBubble();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }

    private void pauseShift(){
        if(!shiftActive||tripActive||shiftPaused){broadcastState();return;}
        long now=System.currentTimeMillis();accountClock(now);shiftPaused=true;pausedAt=now;pauseCount++;
        vehicleMoving=false;filteredSpeed=0f;movingEvidence=stoppedEvidence=0;stopLocation();
        db.updateShiftPaused(shiftId,totalPauseMs,pauseCount);persist();broadcastState();updateNotification();
    }
    private void resumeShift(){
        if(!shiftActive||tripActive||!shiftPaused){broadcastState();return;}
        long now=System.currentTimeMillis();totalPauseMs+=Math.max(0,now-pausedAt);pausedAt=0;shiftPaused=false;
        lastClockAccountedAt=now;lastAccepted=null;lastAcceptedTs=0;filteredSpeedTs=0;vehicleMoving=false;
        db.updateShiftPaused(shiftId,totalPauseMs,pauseCount);startLocation();persist();broadcastState();updateNotification();
    }

    private void startTrip(String type){
        if(!shiftActive||tripActive||shiftPaused){broadcastState();return;}
        long now=System.currentTimeMillis();expireStaleMotion(now);accountClock(now);tripActive=true;stopActive=false;tripId=UUID.randomUUID().toString();stopId="";tripStarted=now;pickupAt=stopStarted=0;tripDistance=0;tripMoving=0;tripStopped=0;tripMax=0;tripAmountUyu=0;tripType=normalizeType(type);tripStatus="active";tripStage="to_pickup";prevMaxSample=-1f;prevMaxSampleTs=0L;tripOriginCaptured=false;Double startLat=null,startLon=null;if(lastAccepted!=null){startLat=lastAccepted.getLatitude();startLon=lastAccepted.getLongitude();tripOriginCaptured=true;}db.beginTrip(tripId,shiftId,tripStarted,zone,tripType,startLat,startLon,null);if(startLat!=null&&startLon!=null)AddressResolver.resolveTripStartAsync(this,tripId,startLat,startLon,zone);persist();broadcastState();updateNotification();
    }

    private void pickupPassenger(){
        if(!tripActive||!"to_pickup".equals(tripStage)){broadcastState();return;}
        long now=System.currentTimeMillis();expireStaleMotion(now);accountClock(now);pickupAt=now;tripStage="onboard";
        Double lat=null,lon=null;if(lastAccepted!=null){lat=lastAccepted.getLatitude();lon=lastAccepted.getLongitude();}
        db.markPassengerPickedUp(tripId,now,lat,lon,null,tripDistance,tripMoving,tripStopped);
        if(lat!=null&&lon!=null)AddressResolver.resolveTripPickupAsync(this,tripId,lat,lon,zone);
        persist();broadcastState();updateNotification();
    }

    private void startRegisteredStop(){
        if(!tripActive||!"onboard".equals(tripStage)||stopActive){broadcastState();return;}
        long now=System.currentTimeMillis();stopActive=true;stopStarted=now;stopId=UUID.randomUUID().toString();
        Double lat=null,lon=null;if(lastAccepted!=null){lat=lastAccepted.getLatitude();lon=lastAccepted.getLongitude();}
        db.beginStop(stopId,tripId,now,lat,lon,null,zone);
        if(lat!=null&&lon!=null)AddressResolver.resolveTripStopAsync(this,stopId,lat,lon,zone);
        persist();broadcastState();updateNotification();
    }

    private void endRegisteredStop(){
        if(!tripActive||!stopActive){broadcastState();return;}
        long now=System.currentTimeMillis();db.finishStop(stopId,now);stopActive=false;stopStarted=0;stopId="";persist();broadcastState();updateNotification();
    }

    private void stopTrip(String status,double amount){stopTripInternal(true,status,amount);}

    private void stopTripInternal(boolean send,String status,double amount){
        if(!tripActive){if(send)broadcastState();return;}
        long now=System.currentTimeMillis();expireStaleMotion(now);accountClock(now);if(stopActive&&!stopId.isEmpty())db.finishStop(stopId,now);double avg=tripMoving>0?(tripDistance/1000.0)/(tripMoving/3600000.0):0;tripStatus=normalizeStatus(status);tripAmountUyu=Math.max(0,amount);String endedTrip=tripId;Double endLat=null,endLon=null;if(lastAccepted!=null){endLat=lastAccepted.getLatitude();endLon=lastAccepted.getLongitude();}db.updateTrip(tripId,now,tripDistance,tripMoving,tripStopped,tripMax,avg,zone);if(endLat!=null&&endLon!=null)db.setTripEndLocation(tripId,endLat,endLon,null);db.finalizeTrip(tripId,tripStatus,tripAmountUyu>0?tripAmountUyu:null);shiftCompletedTrips=db.completedTripsForShift(shiftId);if(endLat!=null&&endLon!=null)AddressResolver.resolveTripEndAsync(this,endedTrip,endLat,endLon,zone);shiftTripMs+=Math.max(0,now-tripStarted);tripActive=false;stopActive=false;tripOriginCaptured=false;tripStage="none";stopId="";tripId="";tripStarted=pickupAt=stopStarted=0;tripDistance=0;tripMoving=0;tripStopped=0;tripMax=0;prevMaxSample=-1f;prevMaxSampleTs=0;db.updateShift(shiftId,null,shiftDistance,shiftMoving,shiftStopped,shiftTripMs,zone);persist();Api.syncTripAsync(endedTrip);if(send)broadcastState();updateNotification();
    }

    private void startClock(){if(clockHandler==null||!shiftActive)return;clockHandler.removeCallbacks(clockTick);if(lastClockAccountedAt<=0)lastClockAccountedAt=System.currentTimeMillis();clockHandler.post(clockTick);}
    private void stopClock(){if(clockHandler!=null)clockHandler.removeCallbacks(clockTick);lastClockAccountedAt=0L;clockPersistTick=0;}
    private void expireStaleMotion(long now){
        if(lastAcceptedTs<=0)return;long age=Math.max(0,now-lastAcceptedTs);boolean softStop=age>=SPEED_STALE_STOP_MS&&filteredSpeed<=15f;boolean hardStop=age>=SPEED_STALE_FORCE_STOP_MS;
        if(softStop||hardStop){vehicleMoving=false;filteredSpeed=0f;movingEvidence=0;stoppedEvidence=0;}
    }
    private float speedForUi(long now){if(lastAcceptedTs<=0)return 0f;long age=Math.max(0,now-lastAcceptedTs);return age>SPEED_STALE_DISPLAY_MS?0f:Math.max(0f,filteredSpeed);}
    private void accountClock(long now){if(!shiftActive||shiftPaused){lastClockAccountedAt=now;return;}if(lastClockAccountedAt<=0){lastClockAccountedAt=now;return;}long dt=now-lastClockAccountedAt;if(dt<=0)return;lastClockAccountedAt=now;if(vehicleMoving)shiftMoving+=dt;else shiftStopped+=dt;if(tripActive){if(vehicleMoving)tripMoving+=dt;else tripStopped+=dt;}}
    private void persistLiveStats(long now){if(!shiftActive)return;db.updateShift(shiftId,null,shiftDistance,shiftMoving,shiftStopped,shiftTripMs+(tripActive?Math.max(0,now-tripStarted):0),zone);if(tripActive){double avg=tripMoving>0?(tripDistance/1000.0)/(tripMoving/3600000.0):0;db.updateTrip(tripId,null,tripDistance,tripMoving,tripStopped,tripMax,avg,zone);}}

    private void startLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        if(lm==null)lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(lm==null)return;
        try{Location best=null;Location g=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);Location n=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);long now=System.currentTimeMillis();if(g!=null&&Math.abs(now-g.getTime())<120000L)best=g;if(best==null&&n!=null&&Math.abs(now-n.getTime())<60000L)best=n;if(best!=null)onLocationChanged(best);if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500L,1f,this,Looper.getMainLooper());if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,6000L,12f,this,Looper.getMainLooper());}catch(Exception ignored){}
    }
    private void stopLocation(){if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){} }

    @Override public void onLocationChanged(Location loc){
        if(loc==null||!FloatingShortcutPolicy.collectLocation(shiftActive,shiftPaused))return;final long ts=loc.getTime()>0?loc.getTime():System.currentTimeMillis();final float accuracy=loc.hasAccuracy()?loc.getAccuracy():50f;final boolean isGps=LocationManager.GPS_PROVIDER.equals(loc.getProvider());final boolean isNetwork=LocationManager.NETWORK_PROVIDER.equals(loc.getProvider());
        if(isGps&&accuracy<=60f)lastGoodGpsTs=ts;if(isNetwork&&lastGoodGpsTs>0&&Math.abs(ts-lastGoodGpsTs)<NETWORK_FALLBACK_AFTER_MS){broadcastState();return;}if(accuracy>MAX_ACCEPTED_ACCURACY_M){broadcastState();return;}if(lastAccepted==null){acceptBaseline(loc,ts,accuracy,isGps);return;}
        long dt=ts-lastAcceptedTs;if(dt<=0){broadcastState();return;}double dist=lastAccepted.distanceTo(loc);double dtSec=dt/1000.0;float derived=(float)((dist/dtSec)*3.6);if(derived>MAX_PLAUSIBLE_KMH||(dt<800L&&derived>60f)){broadcastState();return;}
        float raw=loc.hasSpeed()?Math.max(0f,loc.getSpeed()*3.6f):derived;float candidate=raw<=MAX_PLAUSIBLE_KMH?raw:derived;if(filteredSpeedTs>0&&candidate>filteredSpeed&&dt<=5000L){float accel=(float)(((candidate-filteredSpeed)/3.6)/Math.max(0.25,dtSec));if(accel>MAX_ACCEL_MPS2)candidate=derived;}if(candidate>MAX_PLAUSIBLE_KMH)candidate=filteredSpeed;if(loc.hasSpeed()&&accuracy>12f){float diff=Math.abs(candidate-derived);float tolerance=Math.max(18f,Math.max(candidate,derived)*0.45f);if(diff>tolerance)candidate=derived;}

        accountClock(System.currentTimeMillis());
        float noiseRadius=Math.max(3.0f,Math.min(14.0f,(lastAcceptedAccuracy+accuracy)*0.30f));float movementDistanceThreshold=Math.max(1.8f,Math.min(4.5f,noiseRadius*0.35f));boolean displacementSupportsMove=dist>=movementDistanceThreshold&&derived>=3.5f;boolean speedAccuracyGood=!loc.hasSpeedAccuracy()||loc.getSpeedAccuracyMetersPerSecond()*3.6f<=5.0f;boolean strongGpsSpeed=isGps&&accuracy<=12f&&speedAccuracyGood&&candidate>=STRONG_GPS_SPEED_KMH;boolean movementEvidenceNow=candidate>=START_MOVING_KMH&&(displacementSupportsMove||strongGpsSpeed);boolean stationaryGeometry=dist<=movementDistanceThreshold;boolean stoppedEvidenceNow=candidate<=STOP_MOVING_KMH||(stationaryGeometry&&candidate<STATIONARY_GHOST_KMH);
        if(vehicleMoving){movingEvidence=0;if(stoppedEvidenceNow){stoppedEvidence++;if(stoppedEvidence>=2){vehicleMoving=false;stoppedEvidence=0;filteredSpeed=0f;}}else stoppedEvidence=0;}else{stoppedEvidence=0;if(movementEvidenceNow){movingEvidence++;if(movingEvidence>=2){vehicleMoving=true;movingEvidence=0;}}else movingEvidence=0;}
        float displaySpeed=vehicleMoving?candidate:0f;if(stationaryGeometry&&candidate<STATIONARY_GHOST_KMH)displaySpeed=0f;if(displaySpeed<ZERO_DISPLAY_KMH)displaySpeed=0f;
        double segment=(vehicleMoving&&!stoppedEvidenceNow&&accuracy<=MAX_STATS_ACCURACY_M&&dist>Math.max(1.5f,noiseRadius*0.30f))?dist:0.0;if(!vehicleMoving||derived>140f||stationaryGeometry)segment=0.0;
        long statDt=dt<=MAX_STATS_GAP_MS?dt:0L;zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());if(shiftActive&&statDt>0){shiftDistance+=segment;if(tripActive){tripDistance+=segment;if(vehicleMoving)considerConfirmedMaximum(displaySpeed,accuracy,isGps,ts);}}
        filteredSpeed=Math.max(0f,displaySpeed);filteredSpeedTs=ts;if(loc.hasBearing()&&filteredSpeed>=START_MOVING_KMH)lastBearing=loc.getBearing();lastAccepted=new Location(loc);lastAcceptedTs=ts;lastAcceptedAccuracy=accuracy;
        if(shiftActive){if(tripActive&&!tripOriginCaptured){tripOriginCaptured=true;db.setTripStartLocation(tripId,loc.getLatitude(),loc.getLongitude(),null);AddressResolver.resolveTripStartAsync(this,tripId,loc.getLatitude(),loc.getLongitude(),zone);}db.addPoint(UUID.randomUUID().toString(),shiftId,tripActive?tripId:null,ts,loc.getLatitude(),loc.getLongitude(),accuracy,filteredSpeed,lastBearing,zone,tripActive);persistLiveStats(System.currentTimeMillis());persist();}
        broadcastState();updateNotification();
    }

    private void acceptBaseline(Location loc,long ts,float accuracy,boolean isGps){float raw=loc.hasSpeed()?Math.max(0f,loc.getSpeed()*3.6f):0f;if(raw>MAX_PLAUSIBLE_KMH)raw=0f;boolean speedAccuracyGood=!loc.hasSpeedAccuracy()||loc.getSpeedAccuracyMetersPerSecond()*3.6f<=5.0f;vehicleMoving=isGps&&accuracy<=10f&&speedAccuracyGood&&raw>=15f;movingEvidence=stoppedEvidence=0;filteredSpeed=vehicleMoving?raw:0f;if(filteredSpeed<ZERO_DISPLAY_KMH)filteredSpeed=0f;filteredSpeedTs=ts;if(loc.hasBearing()&&filteredSpeed>=START_MOVING_KMH)lastBearing=loc.getBearing();lastAccepted=new Location(loc);lastAcceptedTs=ts;lastAcceptedAccuracy=accuracy;if(isGps)lastGoodGpsTs=ts;zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());if(shiftActive){if(tripActive&&!tripOriginCaptured){tripOriginCaptured=true;db.setTripStartLocation(tripId,loc.getLatitude(),loc.getLongitude(),null);AddressResolver.resolveTripStartAsync(this,tripId,loc.getLatitude(),loc.getLongitude(),zone);}db.addPoint(UUID.randomUUID().toString(),shiftId,tripActive?tripId:null,ts,loc.getLatitude(),loc.getLongitude(),accuracy,filteredSpeed,lastBearing,zone,tripActive);persist();}broadcastState();updateNotification();}
    private void considerConfirmedMaximum(float speed,float accuracy,boolean isGps,long ts){if(!tripActive||!isGps||accuracy>MAX_STATS_ACCURACY_M||speed<START_MOVING_KMH||speed>MAX_PLAUSIBLE_KMH)return;if(prevMaxSample>=0f&&prevMaxSampleTs>0){long dt=Math.abs(ts-prevMaxSampleTs);if(dt>=500L&&dt<=8000L){float delta=Math.abs(speed-prevMaxSample);float tolerance=Math.max(8f,Math.max(speed,prevMaxSample)*0.25f);float accel=(float)(((delta/3.6)/(dt/1000.0)));if(delta<=tolerance&&accel<=MAX_ACCEL_MPS2)tripMax=Math.max(tripMax,Math.max(speed,prevMaxSample));}}prevMaxSample=speed;prevMaxSampleTs=ts;}

    private void broadcastState(){long now=System.currentTimeMillis();float uiSpeed=speedForUi(now);Intent i=new Intent(ACTION_STATE).setPackage(getPackageName());i.putExtra("shift_active",shiftActive);i.putExtra("shift_paused",shiftPaused);i.putExtra("pause_ms",totalPauseMs+(shiftPaused?Math.max(0,now-pausedAt):0));i.putExtra("pause_count",pauseCount);i.putExtra("shift_completed_trips",shiftCompletedTrips);i.putExtra("trip_active",tripActive);i.putExtra("vehicle_moving",vehicleMoving&&uiSpeed>0f);i.putExtra("state_at",now);i.putExtra("shift_id",shiftId);i.putExtra("trip_id",tripId);i.putExtra("trip_type",tripType);i.putExtra("trip_status",tripStatus);i.putExtra("trip_stage",tripStage);i.putExtra("stop_active",stopActive);i.putExtra("stop_started",stopStarted);i.putExtra("pickup_at",pickupAt);i.putExtra("amount_uyu",tripAmountUyu);i.putExtra("shift_started",shiftStarted);i.putExtra("trip_started",tripStarted);i.putExtra("shift_distance",shiftDistance);i.putExtra("trip_distance",tripDistance);i.putExtra("shift_moving",shiftMoving);i.putExtra("shift_stopped",shiftStopped);i.putExtra("shift_trip_ms",shiftTripMs+(tripActive?Math.max(0,now-tripStarted):0));i.putExtra("trip_moving",tripMoving);i.putExtra("trip_stopped",tripStopped);i.putExtra("trip_max",tripMax);i.putExtra("zone",zone);if(lastAccepted!=null){i.putExtra("has_location",true);i.putExtra("lat",lastAccepted.getLatitude());i.putExtra("lon",lastAccepted.getLongitude());i.putExtra("accuracy",lastAcceptedAccuracy);i.putExtra("speed",uiSpeed);i.putExtra("bearing",lastBearing);i.putExtra("location_age_ms",Math.max(0,now-lastAcceptedTs));}sendBroadcast(i);}

    private void persist(){sp.edit().putBoolean("shift_paused",shiftPaused).putLong("paused_at",pausedAt).putLong("total_pause_ms",totalPauseMs).putInt("pause_count",pauseCount).putBoolean("shift_active",shiftActive).putBoolean("trip_active",tripActive).putBoolean("stop_active",stopActive).putBoolean("vehicle_moving",vehicleMoving).putString("shift_id",shiftId).putString("trip_id",tripId).putString("stop_id",stopId).putString("trip_type",tripType).putString("trip_status",tripStatus).putString("trip_stage",tripStage).putLong("shift_started",shiftStarted).putLong("trip_started",tripStarted).putLong("pickup_at",pickupAt).putLong("stop_started",stopStarted).putLong("shift_distance_bits",Double.doubleToLongBits(shiftDistance)).putLong("trip_distance_bits",Double.doubleToLongBits(tripDistance)).putLong("shift_moving",shiftMoving).putLong("shift_stopped",shiftStopped).putLong("shift_trip_ms",shiftTripMs).putLong("trip_moving",tripMoving).putLong("trip_stopped",tripStopped).putLong("trip_max_bits",Double.doubleToLongBits(tripMax)).putLong("trip_amount_bits",Double.doubleToLongBits(tripAmountUyu)).putString("zone",zone).apply();}
    private void loadState(){shiftPaused=sp.getBoolean("shift_paused",false);pausedAt=sp.getLong("paused_at",0);totalPauseMs=sp.getLong("total_pause_ms",0);pauseCount=sp.getInt("pause_count",0);shiftActive=sp.getBoolean("shift_active",false);tripActive=sp.getBoolean("trip_active",false);stopActive=sp.getBoolean("stop_active",false);vehicleMoving=sp.getBoolean("vehicle_moving",false);shiftId=sp.getString("shift_id","");tripId=sp.getString("trip_id","");stopId=sp.getString("stop_id","");tripType=sp.getString("trip_type","other");tripStatus=sp.getString("trip_status",tripActive?"active":"completed");tripStage=sp.getString("trip_stage",tripActive?"onboard":"none");shiftStarted=sp.getLong("shift_started",0);tripStarted=sp.getLong("trip_started",0);pickupAt=sp.getLong("pickup_at",0);stopStarted=sp.getLong("stop_started",0);shiftDistance=Double.longBitsToDouble(sp.getLong("shift_distance_bits",Double.doubleToLongBits(0)));tripDistance=Double.longBitsToDouble(sp.getLong("trip_distance_bits",Double.doubleToLongBits(0)));shiftMoving=sp.getLong("shift_moving",0);shiftStopped=sp.getLong("shift_stopped",0);shiftTripMs=sp.getLong("shift_trip_ms",0);tripMoving=sp.getLong("trip_moving",0);tripStopped=sp.getLong("trip_stopped",0);tripMax=Double.longBitsToDouble(sp.getLong("trip_max_bits",Double.doubleToLongBits(0)));tripAmountUyu=Double.longBitsToDouble(sp.getLong("trip_amount_bits",Double.doubleToLongBits(0)));zone=sp.getString("zone","Buscando zona…");}

    private static final class JSONObjectStub{final boolean hasStart;JSONObjectStub(boolean hasStart){this.hasStart=hasStart;}}
    private JSONObjectStub readTripStartState(){try{org.json.JSONObject t=db.getTrip(tripId);return new JSONObjectStub(t!=null&&!t.isNull("start_lat")&&!t.isNull("start_lon"));}catch(Exception e){return new JSONObjectStub(false);}}
    private String normalizeType(String s){if(s==null)return "other";String x=s.toLowerCase(Locale.ROOT);if(x.equals("uber")||x.equals("cabify")||x.equals("personal"))return x;return "other";}
    private String normalizeStatus(String s){return "cancelled".equalsIgnoreCase(s)?"cancelled":"completed";}
    private String typeLabel(){if("uber".equals(tripType))return "Uber";if("cabify".equals(tripType))return "Cabify";if("personal".equals(tripType))return "Personal";return "Viaje";}

    private Notification notification(){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);String title;if(!tripActive)title=shiftPaused?"Jornada en pausa":shiftActive?"Jornada activa":"Mapa Trayectos";else if(stopActive)title="Parada registrada · "+typeLabel();else if("to_pickup".equals(tripStage))title="Yendo a recoger · "+typeLabel();else title="Pasajero a bordo · "+typeLabel();String text=String.format(Locale.getDefault(),"%.1f km · %s",(tripActive?tripDistance:shiftDistance)/1000.0,zone);return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(title).setContentText(text).setOngoing(shiftActive).setContentIntent(pi).setOnlyAlertOnce(true).build();}
    private void updateNotification(){if(shiftActive){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFICATION_ID,notification());}}
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm!=null){
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Mapa Trayectos · Seguimiento",NotificationManager.IMPORTANCE_LOW));
                nm.createNotificationChannel(new NotificationChannel(IDLE_CHANNEL,
                    "Mapa Trayectos · Acceso flotante",NotificationManager.IMPORTANCE_LOW));
            }
        }
    }

    /**
     * R24.1: 64dp touch surface with a slightly larger (56dp) visible logo.
     * Paint the request badge OUTSIDE the clipped round logo on the parent,
     * leaving a 2dp safe area so numbers (including 9+) cannot be cropped.
     */
    private void showBubbleIfAllowed(){
        if(!FloatingShortcutPolicy.canDisplay(appVisible,Settings.canDrawOverlays(this),bubbleView!=null))return;
        try{
            bubbleWm=(WindowManager)getSystemService(WINDOW_SERVICE);
            if(bubbleWm==null)return;
            final int size=dp(64),iconSize=dp(56),iconInset=dp(4);
            FrameLayout bubble=new FrameLayout(this){
                private final Paint badgePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void dispatchDraw(Canvas canvas){
                    super.dispatchDraw(canvas);
                    int count=TransferAlerts.pendingCount(TrackingService.this);
                    if(count<=0)return;
                    final float cx=getWidth()-dp(12),cy=dp(12),radius=dp(10);
                    badgePaint.setStyle(Paint.Style.FILL);
                    badgePaint.setColor(Color.rgb(182,53,58));
                    canvas.drawCircle(cx,cy,radius,badgePaint);
                    badgePaint.setStyle(Paint.Style.STROKE);
                    badgePaint.setStrokeWidth(dp(1));
                    badgePaint.setColor(Color.rgb(231,202,130));
                    canvas.drawCircle(cx,cy,radius,badgePaint);
                    badgePaint.setStyle(Paint.Style.FILL);
                    badgePaint.setColor(Color.WHITE);
                    badgePaint.setTextAlign(Paint.Align.CENTER);
                    badgePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    badgePaint.setTextSize(dp(count>9?9:11));
                    canvas.drawText(count>9?"9+":Integer.toString(count),cx,cy+dp(4),badgePaint);
                }
            };
            bubble.setClipChildren(false);
            bubble.setClipToPadding(false);
            bubble.setContentDescription("Mapa Trayectos. Tocá para abrir. Mantené pulsado para cerrar el mapa sin jornada.");
            ImageView icon=new ImageView(this);
            icon.setImageResource(R.drawable.app_icon);
            GradientDrawable chrome=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(5,49,58),Color.rgb(8,85,91)});
            chrome.setCornerRadius(dp(28));
            chrome.setStroke(dp(1),Color.rgb(222,190,112));
            icon.setBackground(chrome);icon.setClipToOutline(true);
            icon.setPadding(dp(6),dp(6),dp(6),dp(6));
            FrameLayout.LayoutParams logoLp=new FrameLayout.LayoutParams(iconSize,iconSize,Gravity.TOP|Gravity.LEFT);
            logoLp.leftMargin=iconInset;logoLp.topMargin=iconInset;
            bubble.addView(icon,logoLp);
            bubble.setElevation(dp(7));

            bubbleLp=new WindowManager.LayoutParams(size,size,
                Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            bubbleLp.gravity=Gravity.TOP|Gravity.START;
            SharedPreferences bp=getSharedPreferences("bubble_state",MODE_PRIVATE);
            bubbleLp.x=bp.getInt("window_x",bp.getInt("x",getResources().getDisplayMetrics().widthPixels-size-dp(12)));
            bubbleLp.y=bp.getInt("window_y",bp.getInt("y",dp(220)));
            bubble.setOnTouchListener(new View.OnTouchListener(){
                float downX,downY;
                int startX,startY;
                long downAt;
                boolean moved=false,longHandled=false;
                final Runnable longPress=()->{
                    if(moved)return;
                    longHandled=true;
                    bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    closeMapFromFloatingBubble();
                };
                @Override public boolean onTouch(View v,MotionEvent e){
                    switch(e.getActionMasked()){
                        case MotionEvent.ACTION_DOWN:
                            downX=e.getRawX();downY=e.getRawY();
                            startX=bubbleLp.x;startY=bubbleLp.y;
                            downAt=System.currentTimeMillis();moved=false;longHandled=false;
                            clockHandler.removeCallbacks(longPress);
                            clockHandler.postDelayed(longPress,800L);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if(longHandled)return true;
                            float dxMove=Math.abs(e.getRawX()-downX),dyMove=Math.abs(e.getRawY()-downY);
                            if(dxMove>dp(8)||dyMove>dp(8)){
                                moved=true;clockHandler.removeCallbacks(longPress);
                            }
                            if(!moved)return true;
                            int nx=startX+(int)(e.getRawX()-downX);
                            int ny=startY+(int)(e.getRawY()-downY);
                            int maxX=Math.max(0,getResources().getDisplayMetrics().widthPixels-size);
                            int maxY=Math.max(dp(70),getResources().getDisplayMetrics().heightPixels-size-dp(90));
                            bubbleLp.x=Math.max(0,Math.min(maxX,nx));
                            bubbleLp.y=Math.max(dp(45),Math.min(maxY,ny));
                            try{
                                bubbleWm.updateViewLayout(bubble,bubbleLp);
                                signalFloatingBubblePosition(true);
                                realignTransferCallout();
                            }catch(Exception ignored){}
                            return true;
                        case MotionEvent.ACTION_UP:
                            clockHandler.removeCallbacks(longPress);
                            if(longHandled)return true;
                            float dx=Math.abs(e.getRawX()-downX),dy=Math.abs(e.getRawY()-downY);
                            if(!moved&&dx<dp(8)&&dy<dp(8)&&System.currentTimeMillis()-downAt<800){
                                Intent open=new Intent(TrackingService.this,MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(open);
                            }else{
                                int edge=Math.max(0,getResources().getDisplayMetrics().widthPixels-size);
                                bubbleLp.x=bubbleLp.x<edge/2?0:edge;
                                try{bubbleWm.updateViewLayout(bubble,bubbleLp);}catch(Exception ignored){}
                                saveFloatingBubbleState(true);
                                signalFloatingBubblePosition(true);
                                realignTransferCallout();
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            clockHandler.removeCallbacks(longPress);
                            return true;
                    }
                    return false;
                }
            });
            bubbleWm.addView(bubble,bubbleLp);
            bubbleView=bubble;saveFloatingBubbleState(true);
            if(clockHandler!=null){
                clockHandler.removeCallbacks(bubbleHeartbeat);
                clockHandler.postDelayed(bubbleHeartbeat,IDLE_BUBBLE_HEARTBEAT_MS);
            }
            signalFloatingBubblePosition(true);
        }catch(Exception ex){Log.w("MapaBubble","Unable to show shortcut",ex);hideBubble();}
    }

    /** Closing the map shortcut must NEVER disable authenticated transfer alerts. */
    private void closeMapFromFloatingBubble(){
        // Check durable state as well as service state before allowing a close.
        SharedPreferences persisted=getSharedPreferences("tracking_state",MODE_PRIVATE);
        if(!BubbleClosePolicy.mayClose(shiftActive,tripActive,
                persisted.getBoolean("shift_active",false),
                persisted.getBoolean("trip_active",false))){
            android.widget.Toast.makeText(this,
                "Hay una jornada abierta. Cerrala antes de salir de Mapa Trayectos.",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        // Without PIN the monitor does not run; with PIN leave the reservation
        // monitor untouched and ask it to remain available while Mapa disappears.
        TransferAlerts.ensureMonitor(this);
        boolean watching=!getSharedPreferences("driver_session",MODE_PRIVATE)
            .getString("pin","").isEmpty();
        android.widget.Toast.makeText(this,
            watching?"Mapa cerrado · avisos mientras siga activo el monitor":"Mapa Trayectos cerrado",
            android.widget.Toast.LENGTH_SHORT).show();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf(); // onDestroy removes overlays, location listeners and bubble.
    }
    /**
     * The reminder overlay follows the REAL 52dp floating shortcut, not the old
     * in-map ✦ coordinates. Notify it on every drag and when the bubble vanishes.
     */
    private void signalFloatingBubblePosition(boolean visible){
        Intent moved=new Intent(ACTION_FLOATING_BUBBLE_POSITION).setPackage(getPackageName());
        moved.putExtra("visible",visible);
        if(visible&&bubbleLp!=null){
            moved.putExtra("x",bubbleLp.x+dp(4)).putExtra("y",bubbleLp.y+dp(4))
                .putExtra("size",dp(56));
        }
        sendBroadcast(moved);
    }
    private void saveFloatingBubbleState(boolean visible){
        SharedPreferences.Editor e=getSharedPreferences("bubble_state",MODE_PRIVATE).edit();
        e.putBoolean("floating_visible",visible).putLong("floating_seen_ms",System.currentTimeMillis());
        if(visible&&bubbleLp!=null)e.putInt("window_x",bubbleLp.x).putInt("window_y",bubbleLp.y)
            .putInt("x",bubbleLp.x+dp(4)).putInt("y",bubbleLp.y+dp(4))
            .putInt("floating_size",dp(56));
        e.apply();
    }
    private void showTransferCallout(){
        hideTransferCallout();
        if(bubbleWm==null||bubbleView==null||bubbleLp==null)return;
        activeTransferCallout=new ReminderCallout(this,TransferAlerts.lastText(this),
            ()->{
                hideTransferCallout();TransferAlerts.markViewed(this);
                Intent open=new Intent(this,uy.com.traslados.conductor.MainActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(open);
            },this::hideTransferCallout,this::hideTransferCallout,
            "🚘  NUEVO TRASLADO","Mapa Trayectos · Solicitud","DESPUÉS","VER PEDIDO");
        int width=dp(ReminderCallout.TOTAL_WIDTH_DP);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            width,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;
        activeTransferLp=lp;
        ReminderAnchorGeometry.Placement placement=transferPlacement(activeTransferCallout);
        if(placement==null){hideTransferCallout();return;}
        activeTransferCallout.setAnchorPlacement(placement.tailOnLeft,placement.tailCenterY);
        lp.x=placement.x;lp.y=placement.y;
        try{
            bubbleWm.addView(activeTransferCallout,lp);
            activeTransferCallout.reveal();
            clockHandler.postDelayed(transferTimeout,30000L);
        }catch(Exception ex){Log.w("MapaTransfers","Speech callout unavailable, Android notification remains",ex);hideTransferCallout();}
    }
    private ReminderAnchorGeometry.Placement transferPlacement(ReminderCallout callout){
        if(bubbleLp==null||callout==null)return null;
        int width=dp(ReminderCallout.TOTAL_WIDTH_DP),screenHeight=getResources().getDisplayMetrics().heightPixels;
        callout.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenHeight,View.MeasureSpec.AT_MOST));
        return ReminderAnchorGeometry.place(bubbleLp.x+dp(4),bubbleLp.y+dp(4),dp(56),
            getResources().getDisplayMetrics().widthPixels,screenHeight,
            width,callout.getMeasuredHeight(),
            dp(ReminderCallout.TAIL_CENTER_DP),dp(ReminderCallout.TAIL_HEIGHT_DP)/2,dp(4));
    }
    private void realignTransferCallout(){
        if(activeTransferCallout==null||activeTransferLp==null||bubbleWm==null)return;
        ReminderAnchorGeometry.Placement p=transferPlacement(activeTransferCallout);
        if(p==null){hideTransferCallout();return;}
        activeTransferCallout.setAnchorPlacement(p.tailOnLeft,p.tailCenterY);
        if(activeTransferLp.x!=p.x||activeTransferLp.y!=p.y){
            activeTransferLp.x=p.x;activeTransferLp.y=p.y;
            try{bubbleWm.updateViewLayout(activeTransferCallout,activeTransferLp);}
            catch(Exception e){hideTransferCallout();}
        }
    }
    private void hideTransferCallout(){
        if(clockHandler!=null)clockHandler.removeCallbacks(transferTimeout);
        if(activeTransferCallout!=null&&bubbleWm!=null)
            try{bubbleWm.removeView(activeTransferCallout);}catch(Exception ignored){}
        activeTransferCallout=null;activeTransferLp=null;
    }
    private void hideBubble(){
        hideTransferCallout();
        if(clockHandler!=null)clockHandler.removeCallbacks(bubbleHeartbeat);
        boolean wasVisible=bubbleView!=null;
        if(bubbleView!=null&&bubbleWm!=null)try{bubbleWm.removeView(bubbleView);}catch(Exception ignored){}
        bubbleView=null;bubbleLp=null;
        saveFloatingBubbleState(false);
        if(wasVisible)signalFloatingBubblePosition(false);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onDestroy(){if(clockHandler!=null){clockHandler.removeCallbacks(bubbleShow);clockHandler.removeCallbacks(bubbleHeartbeat);}stopClock();hideBubble();stopLocation();try{unregisterReceiver(transferEvents);}catch(Exception ignored){}if(db!=null)db.close();super.onDestroy();}
    @Nullable @Override public IBinder onBind(Intent intent){return null;}
}
