package uy.com.mapatrayectos.qa;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Foreground GPS QA sender, activated exclusively by consented driver action. */
public final class LiveQaTrackingService extends Service implements LocationListener {
    private static final String CHANNEL="qt_live_qa_gps";
    private static final int NOTIFICATION=7127;
    private LocationManager manager;
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    private final AtomicLong sequence=new AtomicLong(System.currentTimeMillis());
    private volatile boolean running=false;
    private long lastSentMs=0;
    private String endpoint,requestId,token;
    @Override public void onCreate(){
        super.onCreate();
        manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Quick Trip GPS QA",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Comparte GPS solamente mientras autorices una solicitud QA.");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
    private Notification notification(String line){
        PendingIntent launch=PendingIntent.getActivity(this,721,
            new Intent(this,LiveQaActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("MAPA QT · GPS QA")
            .setContentText(line).setOnlyAlertOnce(true).setOngoing(true)
            .setContentIntent(launch).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        try{
            endpoint=LiveQaNet.base(intent.getStringExtra("endpoint"));
            requestId=LiveQaNet.id(intent.getStringExtra("requestId"));
            token=LiveQaNet.token(intent.getStringExtra("driverToken"));
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Sin ubicación precisa");
            startForeground(NOTIFICATION,notification("Transmisión consentida · sólo QA"));
            running=true;
            if(manager==null)throw new SecurityException("GPS no disponible");
            int providers=0;
            if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,3f,this);providers++;
            }
            if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000,5f,this);providers++;
            }
            if(providers==0)throw new SecurityException("Proveedores GPS inactivos");
        }catch(Exception e){stopSelf();}
        // Never automatically restart: token/consent must be explicitly supplied again.
        return START_NOT_STICKY;
    }
    @Override public void onLocationChanged(Location point){
        if(!running || point==null || !point.hasAccuracy())return;
        if(point.getAccuracy()<=0||point.getAccuracy()>50)return;
        if(point.getLatitude() < -35.5||point.getLatitude()>-30||
           point.getLongitude() < -59.5||point.getLongitude()>-52)return;
        long now=System.currentTimeMillis(),age;
        if(Build.VERSION.SDK_INT>=17)age=TimeUnit.NANOSECONDS.toMillis(
            android.os.SystemClock.elapsedRealtimeNanos()-point.getElapsedRealtimeNanos());
        else age=now-point.getTime();
        if(age<0||age>15000||now-lastSentMs<5000)return;
        lastSentMs=now;
        long seq=sequence.incrementAndGet();
        final double lat=point.getLatitude(),lng=point.getLongitude();
        final float accuracy=point.getAccuracy();
        final Float heading=point.hasBearing()?point.getBearing():null;
        final long stamp=point.getTime();
        final String e=endpoint,id=requestId,t=token;
        pool.execute(()->{
            if(!running)return;
            try{
                SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                JSONObject json=new JSONObject().put("seq",seq)
                    .put("lat",lat).put("lng",lng).put("accuracyM",(double)accuracy)
                    .put("measuredAt",fmt.format(new Date(stamp)));
                if(heading!=null)json.put("headingDeg",(double)heading);
                if(!running)return;
                LiveQaNet.call(e,t,"POST","/v1/requests/"+id+"/location",json);
            }catch(Exception ex){
                // On authorization failure or network loss we stop locally, no retries/leaked tokens.
                running=false;stopSelf();
            }
        });
    }
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onDestroy(){
        running=false;
        if(manager!=null)try{manager.removeUpdates(this);}catch(Exception ignored){}
        pool.shutdownNow();
        endpoint=null;token=null;requestId=null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
