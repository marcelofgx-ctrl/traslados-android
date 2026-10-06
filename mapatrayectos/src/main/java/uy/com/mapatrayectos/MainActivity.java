package uy.com.mapatrayectos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.*;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.*;
import org.maplibre.android.style.sources.GeoJsonSource;

import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION=7001,REQ_NOTIF=7002;
    private static final String STYLE_URL="https://tiles.openfreemap.org/styles/liberty";
    private static final String DRIVER_SOURCE="driver-source",DRIVER_LAYER="driver-layer",DRIVER_IMAGE="driver-arrow";
    private static final String ROUTE_SOURCE="route-source",ROUTE_LAYER="route-layer";
    private static final int BG=Color.rgb(7,25,31),PANEL=Color.rgb(8,42,50),GOLD=Color.rgb(224,193,111),TEXT=Color.rgb(245,244,238),MUTED=Color.rgb(174,188,191);

    private MapView mapView; private MapLibreMap map; private Style style;
    private TextView zoneText,modeText,distanceText,elapsedText,movingText,stoppedText,idleText,gpsText;
    private Button historyBtn,followBtn;
    private SpeedGaugeView speedGauge;
    private SlideActionView slider,endShiftSlider;
    private boolean shiftActive=false,tripActive=false,follow=true;
    private String shiftId="",tripId="";
    private long shiftStarted=0,tripStarted=0,shiftMoving=0,shiftStopped=0,shiftTripMs=0,tripMoving=0,tripStopped=0;
    private double shiftDistance=0,tripDistance=0,tripMax=0;
    private String zone="Buscando zona…",loadedTripId="";
    private final ArrayList<double[]> route=new ArrayList<>();
    private LocationManager previewLm; private long lastCameraAt=0;
    private double lastLat=Double.NaN,lastLon=Double.NaN; private float lastBearing=0f,currentSpeedKmh=0f;
    private float previewLastSpeed=0f; private long previewLastTs=0L;
    private Location previewLastLocation; private boolean previewMoving=false; private int previewMovingEvidence=0,previewStoppedEvidence=0;

    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(TrackingService.ACTION_STATE.equals(i.getAction()))applyState(i);}};
    private final LocationListener previewListener=loc->{
        if(loc==null)return;
        float accuracy=loc.hasAccuracy()?loc.getAccuracy():50f;if(accuracy>45f)return;
        long ts=loc.getTime()>0?loc.getTime():System.currentTimeMillis();
        boolean isGps=LocationManager.GPS_PROVIDER.equals(loc.getProvider());
        float raw=loc.hasSpeed()?Math.max(0f,loc.getSpeed()*3.6f):0f;if(raw>160f)return;

        if(previewLastLocation==null){
            boolean speedAccuracyGood=!loc.hasSpeedAccuracy()||loc.getSpeedAccuracyMetersPerSecond()*3.6f<=5.0f;
            previewMoving=isGps&&accuracy<=10f&&speedAccuracyGood&&raw>=15f;
            previewMovingEvidence=previewStoppedEvidence=0;previewLastLocation=new Location(loc);previewLastTs=ts;previewLastSpeed=previewMoving?raw:0f;
            float shown=previewMoving?raw:0f;if(shown<3f)shown=0f;currentSpeedKmh=shown;
            zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());updateDriver(loc.getLatitude(),loc.getLongitude(),loc.hasBearing()?loc.getBearing():lastBearing,true);speedGauge.setSpeed(shown);zoneText.setText(zone);setGpsBadge(accuracy,0);return;
        }

        long dt=ts-previewLastTs;if(dt<=0)return;double sec=dt/1000.0;double dist=previewLastLocation.distanceTo(loc);float derived=(float)((dist/sec)*3.6);if(derived>160f||(dt<800L&&derived>60f))return;
        float candidate=loc.hasSpeed()?raw:derived;
        if(candidate>previewLastSpeed&&previewLastTs>0){double accel=((candidate-previewLastSpeed)/3.6)/Math.max(.25,sec);if(accel>7.0)candidate=derived;}
        if(candidate>160f)return;

        float lastAccuracy=previewLastLocation.hasAccuracy()?previewLastLocation.getAccuracy():accuracy;
        float noiseRadius=Math.max(3.0f,Math.min(14.0f,(lastAccuracy+accuracy)*0.30f));
        float movementThreshold=Math.max(1.8f,Math.min(4.5f,noiseRadius*0.35f));
        boolean displacementSupportsMove=dist>=movementThreshold&&derived>=3.5f;
        boolean speedAccuracyGood=!loc.hasSpeedAccuracy()||loc.getSpeedAccuracyMetersPerSecond()*3.6f<=5.0f;
        boolean strongGpsSpeed=isGps&&accuracy<=12f&&speedAccuracyGood&&candidate>=10f;
        boolean movementEvidenceNow=candidate>=5f&&(displacementSupportsMove||strongGpsSpeed);
        boolean stationaryGeometry=dist<=movementThreshold;
        boolean stoppedEvidenceNow=candidate<=2.5f||(stationaryGeometry&&candidate<9f);

        if(previewMoving){
            previewMovingEvidence=0;if(stoppedEvidenceNow){previewStoppedEvidence++;if(previewStoppedEvidence>=2){previewMoving=false;previewStoppedEvidence=0;}}else previewStoppedEvidence=0;
        }else{
            previewStoppedEvidence=0;if(movementEvidenceNow){previewMovingEvidence++;if(previewMovingEvidence>=2){previewMoving=true;previewMovingEvidence=0;}}else previewMovingEvidence=0;
        }

        float shown=previewMoving?candidate:0f;if(stationaryGeometry&&candidate<9f)shown=0f;if(shown<3f)shown=0f;
        previewLastLocation=new Location(loc);previewLastTs=ts;previewLastSpeed=shown;currentSpeedKmh=shown;
        zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());updateDriver(loc.getLatitude(),loc.getLongitude(),loc.hasBearing()&&shown>=5f?loc.getBearing():lastBearing,true);speedGauge.setSpeed(shown);zoneText.setText(zone);setGpsBadge(accuracy,0);
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);Api.init(this);MapLibre.getInstance(this);buildUi(b);requestNeededPermissions();
        new Thread(()->{TelemetryQuality.repairHistoricalMaxima(getApplicationContext());Api.syncPendingAsync();},"repair-telemetry").start();
    }

    private void buildUi(Bundle b){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        mapView=new MapView(this);mapView.onCreate(b);root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(6),dp(12),dp(6));top.setBackground(rounded(Color.argb(240,7,25,31),19,0,0));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("MAPA TRAYECTOS",17,TEXT,true);title.setSingleLine(true);zoneText=text(zone,12,GOLD,true);zoneText.setSingleLine(true);labels.addView(title);labels.addView(zoneText);top.addView(labels,new LinearLayout.LayoutParams(0,-1,1));
        followBtn=button("SEGUIRME");followBtn.setOnClickListener(v->{follow=!follow;followBtn.setText(follow?"SEGUIRME":"MAPA LIBRE");if(follow)recenter();});top.addView(followBtn,new LinearLayout.LayoutParams(dp(84),dp(40)));
        historyBtn=button("HISTORIAL");historyBtn.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(88),dp(40));hp.setMargins(dp(5),0,0,0);top.addView(historyBtn,hp);
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,dp(66),Gravity.TOP);topLp.setMargins(dp(10),dp(8),dp(10),0);root.addView(top,topLp);

        speedGauge=new SpeedGaugeView(this);FrameLayout.LayoutParams spdLp=new FrameLayout.LayoutParams(dp(94),dp(92),Gravity.TOP|Gravity.RIGHT);spdLp.setMargins(0,dp(84),dp(14),0);root.addView(speedGauge,spdLp);

        gpsText=text("GPS buscando…",11,TEXT,true);gpsText.setGravity(Gravity.CENTER);gpsText.setBackground(rounded(Color.argb(228,7,25,31),17,1,Color.argb(80,224,193,111)));FrameLayout.LayoutParams gpsLp=new FrameLayout.LayoutParams(dp(116),dp(32),Gravity.TOP|Gravity.LEFT);gpsLp.setMargins(dp(14),dp(84),0,0);root.addView(gpsText,gpsLp);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(14),dp(10),dp(14),dp(10));bottom.setBackground(rounded(Color.argb(247,7,25,31),25,1,Color.rgb(31,74,84)));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);modeText=text("LISTO PARA JORNADA",15,TEXT,true);modeText.setSingleLine(true);head.addView(modeText,new LinearLayout.LayoutParams(0,-2,1));
        endShiftSlider=new SlideActionView(this);endShiftSlider.setMode(SlideActionView.MODE_SHIFT_CLOSE);endShiftSlider.setLabel("CERRAR JORNADA");endShiftSlider.setOnCompleted(()->sendAction(TrackingService.ACTION_STOP_SHIFT));endShiftSlider.setVisibility(View.GONE);LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(dp(154),dp(42));closeLp.setMargins(dp(6),0,0,0);head.addView(endShiftSlider,closeLp);bottom.addView(head,new LinearLayout.LayoutParams(-1,dp(44)));

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);distanceText=metric(metrics,"0.0","km");elapsedText=metric(metrics,"00:00","tiempo");movingText=metric(metrics,"00:00","mov.");stoppedText=metric(metrics,"00:00","det.");bottom.addView(metrics,new LinearLayout.LayoutParams(-1,dp(56)));
        idleText=text("Sin viaje: 00:00  ·  Vel. máx.: 0 km/h",11,MUTED,true);idleText.setGravity(Gravity.CENTER);bottom.addView(idleText,new LinearLayout.LayoutParams(-1,dp(26)));
        slider=new SlideActionView(this);slider.setOnCompleted(this::performSliderAction);bottom.addView(slider,new LinearLayout.LayoutParams(-1,dp(72)));
        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,dp(218),Gravity.BOTTOM);bottomLp.setMargins(dp(10),0,dp(10),dp(8));root.addView(bottom,bottomLp);

        root.setOnApplyWindowInsetsListener((v,insets)->{
            int topInset=insets.getSystemWindowInsetTop();int bottomInset=insets.getSystemWindowInsetBottom();
            FrameLayout.LayoutParams a=(FrameLayout.LayoutParams)top.getLayoutParams();a.topMargin=topInset+dp(6);top.setLayoutParams(a);
            FrameLayout.LayoutParams c=(FrameLayout.LayoutParams)speedGauge.getLayoutParams();c.topMargin=topInset+dp(78);speedGauge.setLayoutParams(c);
            FrameLayout.LayoutParams g=(FrameLayout.LayoutParams)gpsText.getLayoutParams();g.topMargin=topInset+dp(78);gpsText.setLayoutParams(g);
            FrameLayout.LayoutParams z=(FrameLayout.LayoutParams)bottom.getLayoutParams();z.bottomMargin=bottomInset+dp(8);bottom.setLayoutParams(z);return insets;
        });
        root.requestApplyInsets();

        setContentView(root);updateUi();
        mapView.getMapAsync(m->{map=m;map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new LatLng(-34.88,-56.08),11.6));map.setStyle(new Style.Builder().fromUri(STYLE_URL),s->{style=s;style.addImage(DRIVER_IMAGE,driverArrow());GeoJsonSource ds=new GeoJsonSource(DRIVER_SOURCE,pointGeoJson(-34.88,-56.08,0));style.addSource(ds);SymbolLayer dl=new SymbolLayer(DRIVER_LAYER,DRIVER_SOURCE).withProperties(PropertyFactory.iconImage(DRIVER_IMAGE),PropertyFactory.iconSize(0.72f),PropertyFactory.iconAllowOverlap(true),PropertyFactory.iconIgnorePlacement(true),PropertyFactory.iconRotate(Expression.get("bearing")));style.addLayer(dl);GeoJsonSource rs=new GeoJsonSource(ROUTE_SOURCE,lineGeoJson(route));style.addSource(rs);LineLayer rl=new LineLayer(ROUTE_LAYER,ROUTE_SOURCE).withProperties(PropertyFactory.lineColor(GOLD),PropertyFactory.lineWidth(5f),PropertyFactory.lineOpacity(0.90f));style.addLayerBelow(rl,DRIVER_LAYER);if(!tripId.isEmpty())loadRoute(tripId);});});
    }

    private void performSliderAction(){if(!shiftActive)sendAction(TrackingService.ACTION_START_SHIFT);else if(!tripActive)sendAction(TrackingService.ACTION_START_TRIP);else sendAction(TrackingService.ACTION_STOP_TRIP);}

    private void sendAction(String action){
        if((TrackingService.ACTION_START_SHIFT.equals(action)||TrackingService.ACTION_START_TRIP.equals(action))&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}
        Intent i=new Intent(this,TrackingService.class).setAction(action);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    }

    private void applyState(Intent i){
        boolean oldTrip=tripActive;String oldTripId=tripId;
        shiftActive=i.getBooleanExtra("shift_active",false);tripActive=i.getBooleanExtra("trip_active",false);shiftId=i.getStringExtra("shift_id");tripId=i.getStringExtra("trip_id");if(shiftId==null)shiftId="";if(tripId==null)tripId="";shiftStarted=i.getLongExtra("shift_started",0);tripStarted=i.getLongExtra("trip_started",0);shiftDistance=i.getDoubleExtra("shift_distance",0);tripDistance=i.getDoubleExtra("trip_distance",0);shiftMoving=i.getLongExtra("shift_moving",0);shiftStopped=i.getLongExtra("shift_stopped",0);shiftTripMs=i.getLongExtra("shift_trip_ms",0);tripMoving=i.getLongExtra("trip_moving",0);tripStopped=i.getLongExtra("trip_stopped",0);tripMax=i.getDoubleExtra("trip_max",0);zone=i.getStringExtra("zone");if(zone==null||zone.isEmpty())zone="Buscando zona…";
        if(i.getBooleanExtra("has_location",false)){double lat=i.getDoubleExtra("lat",0),lon=i.getDoubleExtra("lon",0);float bearing=i.getFloatExtra("bearing",0),speed=i.getFloatExtra("speed",0),accuracy=i.getFloatExtra("accuracy",0);long age=i.getLongExtra("location_age_ms",0);if(speed<3f)speed=0f;currentSpeedKmh=speed;speedGauge.setSpeed(speed);setGpsBadge(accuracy,age);updateDriver(lat,lon,bearing,true);if(tripActive)addRoutePoint(lat,lon);}
        if(!oldTrip&&tripActive){route.clear();loadedTripId="";refreshRoute();}
        if(tripActive&&!tripId.equals(oldTripId)&&!tripId.equals(loadedTripId))loadRoute(tripId);
        if(shiftActive)stopPreview();else startPreview();updateUi();
    }

    private void setGpsBadge(float accuracy,long ageMs){
        int border;if(ageMs>7000L)border=Color.rgb(210,88,76);else if(accuracy<=10f)border=Color.rgb(59,190,126);else if(accuracy<=25f)border=GOLD;else border=Color.rgb(218,132,67);
        String suffix=ageMs>7000L?" · demora":"";gpsText.setText(String.format(Locale.getDefault(),"GPS ±%.0f m%s",accuracy,suffix));gpsText.setBackground(rounded(Color.argb(228,7,25,31),17,1,border));
    }

    private void updateUi(){
        zoneText.setText(zone);
        long now=System.currentTimeMillis();long elapsed=shiftActive?Math.max(0,now-shiftStarted):0;long shownElapsed=tripActive?Math.max(0,now-tripStarted):elapsed;double shownDist=tripActive?tripDistance:shiftDistance;long mv=tripActive?tripMoving:shiftMoving;long st=tripActive?tripStopped:shiftStopped;long idle=shiftActive?Math.max(0,elapsed-shiftTripMs):0;
        distanceText.setText(String.format(Locale.getDefault(),"%.1f\nkm",shownDist/1000.0));elapsedText.setText(formatDuration(shownElapsed)+"\ntiempo");movingText.setText(formatDuration(mv)+"\nmov.");stoppedText.setText(formatDuration(st)+"\ndet.");idleText.setText("Sin viaje: "+formatDuration(idle)+"  ·  Vel. máx.: "+String.format(Locale.getDefault(),"%.0f km/h",tripMax));
        if(!shiftActive){modeText.setText("LISTO PARA JORNADA");slider.setMode(SlideActionView.MODE_SHIFT_START);slider.setLabel("DESLIZAR PARA INICIAR JORNADA");endShiftSlider.setVisibility(View.GONE);}else if(!tripActive){modeText.setText("JORNADA ACTIVA · "+zone);slider.setMode(SlideActionView.MODE_TRIP_START);slider.setLabel("DESLIZAR PARA INICIAR VIAJE");endShiftSlider.setMode(SlideActionView.MODE_SHIFT_CLOSE);endShiftSlider.setLabel("← CERRAR JORNADA");endShiftSlider.setVisibility(View.VISIBLE);}else{modeText.setText("VIAJE ACTIVO · "+zone);slider.setMode(SlideActionView.MODE_TRIP_STOP);slider.setLabel("DESLIZAR PARA FINALIZAR VIAJE");endShiftSlider.setVisibility(View.GONE);}
    }

    private TextView metric(LinearLayout row,String value,String label){TextView t=text(value+"\n"+label,13,TEXT,true);t.setGravity(Gravity.CENTER);row.addView(t,new LinearLayout.LayoutParams(0,-1,1));return t;}

    private float followZoom(){if(currentSpeedKmh<12f)return 13.15f;if(currentSpeedKmh<45f)return 13.00f;if(currentSpeedKmh<80f)return 12.75f;return 12.50f;}
    private void updateDriver(double lat,double lon,float bearing,boolean maybeFollow){lastLat=lat;lastLon=lon;lastBearing=bearing;if(style==null||map==null)return;GeoJsonSource s=style.getSourceAs(DRIVER_SOURCE);if(s!=null)s.setGeoJson(pointGeoJson(lat,lon,bearing));if(maybeFollow&&follow&&System.currentTimeMillis()-lastCameraAt>1800){lastCameraAt=System.currentTimeMillis();CameraPosition cp=new CameraPosition.Builder().target(new LatLng(lat,lon)).zoom(followZoom()).tilt(12).bearing(0).build();map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp),600);}}
    private void recenter(){if(map==null)return;follow=true;followBtn.setText("SEGUIRME");if(!Double.isNaN(lastLat)){lastCameraAt=System.currentTimeMillis();CameraPosition cp=new CameraPosition.Builder().target(new LatLng(lastLat,lastLon)).zoom(followZoom()).tilt(12).bearing(0).build();map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp),500);}}

    private void addRoutePoint(double lat,double lon){if(!route.isEmpty()){double[] p=route.get(route.size()-1);float[] out=new float[1];Location.distanceBetween(p[0],p[1],lat,lon,out);if(out[0]<2)return;}route.add(new double[]{lat,lon});if(route.size()>6000)route.remove(0);refreshRoute();}
    private void refreshRoute(){if(style==null)return;GeoJsonSource s=style.getSourceAs(ROUTE_SOURCE);if(s!=null)s.setGeoJson(lineGeoJson(route));}
    private void loadRoute(String id){if(id==null||id.isEmpty())return;loadedTripId=id;new Thread(()->{TrackDb db=new TrackDb(this);JSONArray a=TelemetryQuality.cleanRoute(db.getTripPoints(id));db.close();ArrayList<double[]> r=new ArrayList<>();for(int x=0;x<a.length();x++){JSONObject o=a.optJSONObject(x);if(o!=null)r.add(new double[]{o.optDouble("lat"),o.optDouble("lon")});}runOnUiThread(()->{route.clear();route.addAll(r);refreshRoute();});},"load-route").start();}

    private String pointGeoJson(double lat,double lon,float bearing){return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"bearing\":"+bearing+"},\"geometry\":{\"type\":\"Point\",\"coordinates\":["+lon+","+lat+"]}}]}";}
    private String lineGeoJson(List<double[]> pts){StringBuilder sb=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");for(int i=0;i<pts.size();i++){if(i>0)sb.append(',');double[] p=pts.get(i);sb.append('[').append(p[1]).append(',').append(p[0]).append(']');}return sb.append("]}}]}").toString();}

    private Bitmap driverArrow(){int n=88;Bitmap b=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Path outer=new Path();outer.moveTo(44,3);outer.lineTo(78,79);outer.lineTo(44,66);outer.lineTo(10,79);outer.close();Paint halo=new Paint(Paint.ANTI_ALIAS_FLAG);halo.setColor(Color.WHITE);c.drawPath(outer,halo);Path body=new Path();body.moveTo(44,8);body.lineTo(71,72);body.lineTo(44,62);body.lineTo(17,72);body.close();Paint dark=new Paint(Paint.ANTI_ALIAS_FLAG);dark.setColor(BG);c.drawPath(body,dark);Path core=new Path();core.moveTo(44,13);core.lineTo(63,66);core.lineTo(44,57);core.lineTo(25,66);core.close();Paint gold=new Paint(Paint.ANTI_ALIAS_FLAG);gold.setColor(GOLD);c.drawPath(core,gold);return b;}

    private void requestNeededPermissions(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);else startPreview();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ_LOCATION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startPreview();}

    private void startPreview(){if(shiftActive||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;if(previewLm==null)previewLm=(LocationManager)getSystemService(LOCATION_SERVICE);if(previewLm==null)return;try{boolean gps=previewLm.isProviderEnabled(LocationManager.GPS_PROVIDER);Location l=previewLm.getLastKnownLocation(gps?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER);if(l!=null&&Math.abs(System.currentTimeMillis()-l.getTime())<120000L)previewListener.onLocationChanged(l);if(gps)previewLm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500L,1f,previewListener,Looper.getMainLooper());else if(previewLm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))previewLm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000L,12f,previewListener,Looper.getMainLooper());}catch(Exception ignored){}}
    private void stopPreview(){if(previewLm!=null)try{previewLm.removeUpdates(previewListener);}catch(Exception ignored){}previewLastLocation=null;previewLastTs=0L;previewLastSpeed=0f;previewMoving=false;previewMovingEvidence=previewStoppedEvidence=0;}

    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setTextColor(TEXT);b.setAllCaps(false);b.setPadding(dp(7),0,dp(7),0);b.setBackground(rounded(PANEL,15,1,Color.rgb(50,91,100)));return b;}
    private GradientDrawable rounded(int fill,int radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String formatDuration(long ms){long total=Math.max(0,ms)/1000;long h=total/3600,m=(total%3600)/60;if(h>0)return String.format(Locale.getDefault(),"%d:%02d",h,m);return String.format(Locale.getDefault(),"%02d:%02d",m,total%60);}

    @Override protected void onResume(){super.onResume();if(Build.VERSION.SDK_INT>=33)registerReceiver(stateReceiver,new IntentFilter(TrackingService.ACTION_STATE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(stateReceiver,new IntentFilter(TrackingService.ACTION_STATE));SharedPreferences s=getSharedPreferences("tracking_state",MODE_PRIVATE);shiftActive=s.getBoolean("shift_active",false);tripActive=s.getBoolean("trip_active",false);if(shiftActive){Intent svc=new Intent(this,TrackingService.class).setAction(TrackingService.ACTION_REQUEST_STATE);if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);}else startPreview();updateUi();if(mapView!=null)mapView.onResume();}
    @Override protected void onPause(){try{unregisterReceiver(stateReceiver);}catch(Exception ignored){}stopPreview();if(mapView!=null)mapView.onPause();super.onPause();}
    @Override protected void onStart(){super.onStart();if(mapView!=null)mapView.onStart();}
    @Override protected void onStop(){if(mapView!=null)mapView.onStop();super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();if(mapView!=null)mapView.onLowMemory();}
    @Override protected void onDestroy(){if(mapView!=null)mapView.onDestroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(mapView!=null)mapView.onSaveInstanceState(out);}

    public static final class SpeedGaugeView extends View {
        private float speed=0f;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();
        public SpeedGaugeView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        public void setSpeed(float v){speed=v<3f?0f:Math.max(0f,Math.min(199f,v));setContentDescription(String.format(Locale.getDefault(),"Velocidad %.0f kilómetros por hora",speed));invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();r.set(1,1,w-1,h-1);p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,w,h,Color.argb(245,7,25,31),Color.argb(245,13,49,57),Shader.TileMode.CLAMP));c.drawRoundRect(r,dpv(23),dpv(23),p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(1.2f));p.setColor(Color.argb(180,224,193,111));c.drawRoundRect(r,dpv(23),dpv(23),p);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(120,224,193,111));c.drawRoundRect(new RectF(dpv(18),dpv(9),w-dpv(18),dpv(11)),dpv(2),dpv(2),p);drawCentered(c,"VELOCIDAD",10f,Color.rgb(174,188,191),h*.24f,false);drawCentered(c,String.format(Locale.getDefault(),"%.0f",speed),36f,Color.WHITE,h*.60f,true);drawCentered(c,"km/h",11f,Color.rgb(224,193,111),h*.84f,true);}
        private void drawCentered(Canvas c,String s,float sp,int color,float cy,boolean bold){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(bold?Typeface.create(Typeface.DEFAULT,Typeface.BOLD):Typeface.DEFAULT);p.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);Paint.FontMetrics fm=p.getFontMetrics();c.drawText(s,getWidth()/2f,cy-(fm.ascent+fm.descent)/2f,p);}
        private float dpv(float v){return v*getResources().getDisplayMetrics().density;}
    }

    public static final class SlideActionView extends View {
        public static final int MODE_SHIFT_START=0,MODE_TRIP_START=1,MODE_TRIP_STOP=2,MODE_SHIFT_CLOSE=3;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF track=new RectF();private Runnable completed;private String label="";private int mode=MODE_SHIFT_START;private boolean dragging=false,thresholdBuzzed=false;private float progress=0f;
        public SlideActionView(Context c){super(c);setClickable(true);setFocusable(true);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("Control deslizable");}
        public void setLabel(String s){if(Objects.equals(label,s))return;label=s;setContentDescription(s);invalidate();}
        public void setMode(int m){if(mode==m)return;mode=m;progress=isReverse()?1f:0f;invalidate();}
        public void setOnCompleted(Runnable r){completed=r;}
        private boolean isReverse(){return mode==MODE_SHIFT_CLOSE;}
        private int dark(){if(mode==MODE_TRIP_START)return Color.rgb(7,79,53);if(mode==MODE_TRIP_STOP)return Color.rgb(91,27,32);return Color.rgb(88,61,10);}
        private int bright(){if(mode==MODE_TRIP_START)return Color.rgb(36,187,119);if(mode==MODE_TRIP_STOP)return Color.rgb(217,67,73);return Color.rgb(222,174,55);}
        private int accent(){if(mode==MODE_TRIP_START)return Color.rgb(31,184,114);if(mode==MODE_TRIP_STOP)return Color.rgb(222,72,78);return Color.rgb(231,187,69);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),pad=dpv(4);track.set(pad,pad,w-pad,h-pad);p.setShader(new LinearGradient(0,0,w,0,dark(),bright(),Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);c.drawRoundRect(track,h/2,h/2,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(1));p.setColor(Color.argb(190,255,255,255));c.drawRoundRect(track,h/2,h/2,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(18,255,255,255));for(float x=-h;x<w+h;x+=dpv(16))c.drawRect(x,0,x+dpv(5),h,p);
            float radius=(h-pad*2)/2-dpv(4),minX=pad+dpv(4)+radius,maxX=w-pad-dpv(4)-radius,cx=minX+(maxX-minX)*progress,cy=h/2f;p.setShadowLayer(dpv(6),0,dpv(2),Color.argb(110,0,0,0));p.setColor(Color.rgb(248,247,241));c.drawCircle(cx,cy,radius,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(2));p.setColor(accent());c.drawCircle(cx,cy,radius-dpv(2),p);p.setStyle(Paint.Style.FILL);p.setColor(dark());Path arrow=new Path();float a=dpv(6);if(isReverse()){arrow.moveTo(cx+a,cy-a);arrow.lineTo(cx-a,cy);arrow.lineTo(cx+a,cy+a);}else{arrow.moveTo(cx-a,cy-a);arrow.lineTo(cx+a,cy);arrow.lineTo(cx-a,cy+a);}arrow.close();c.drawPath(arrow,p);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize((getHeight()<dpv(55)?9.5f:12f)*getResources().getDisplayMetrics().scaledDensity);p.setColor(Color.WHITE);Paint.FontMetrics fm=p.getFontMetrics();float offset=isReverse()?-dpv(8):dpv(10),textX=w/2f+offset,textY=h/2f-(fm.ascent+fm.descent)/2f;c.drawText(label,textX,textY,p);
        }
        @Override public boolean onTouchEvent(MotionEvent e){float h=getHeight(),pad=dpv(4),radius=(h-pad*2)/2-dpv(4),minX=pad+dpv(4)+radius,maxX=getWidth()-pad-dpv(4)-radius;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:dragging=true;thresholdBuzzed=false;getParent().requestDisallowInterceptTouchEvent(true);performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);updateProgress(e.getX(),minX,maxX);return true;case MotionEvent.ACTION_MOVE:if(dragging){updateProgress(e.getX(),minX,maxX);boolean armed=isReverse()?progress<=.18f:progress>=.82f;if(armed&&!thresholdBuzzed){thresholdBuzzed=true;performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);}else if(!armed)thresholdBuzzed=false;return true;}break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(dragging){boolean fire=e.getActionMasked()==MotionEvent.ACTION_UP&&(isReverse()?progress<=.16f:progress>=.84f);dragging=false;getParent().requestDisallowInterceptTouchEvent(false);progress=isReverse()?1f:0f;invalidate();if(fire){performClick();performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);if(completed!=null)completed.run();}return true;}break;}return super.onTouchEvent(e);}
        private void updateProgress(float x,float min,float max){progress=Math.max(0f,Math.min(1f,(x-min)/Math.max(1f,max-min)));invalidate();}
        @Override public boolean performClick(){super.performClick();return true;}
        private float dpv(float v){return v*getResources().getDisplayMetrics().density;}
    }
}
