package uy.com.mapatrayectos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
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
    private TextView speedText,zoneText,modeText,distanceText,elapsedText,movingText,stoppedText,idleText,gpsText;
    private Button historyBtn,followBtn,endShiftBtn;
    private SlideActionView slider;
    private boolean shiftActive=false,tripActive=false,follow=true;
    private String shiftId="",tripId="";
    private long shiftStarted=0,tripStarted=0,shiftMoving=0,shiftStopped=0,shiftTripMs=0,tripMoving=0,tripStopped=0;
    private double shiftDistance=0,tripDistance=0,tripMax=0;
    private String zone="Buscando zona…",loadedTripId="";
    private final ArrayList<double[]> route=new ArrayList<>();
    private LocationManager previewLm; private long lastCameraAt=0;

    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(TrackingService.ACTION_STATE.equals(i.getAction()))applyState(i);}};
    private final LocationListener previewListener=loc->{if(loc==null)return;float speed=loc.hasSpeed()?loc.getSpeed()*3.6f:0f;zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());updateDriver(loc.getLatitude(),loc.getLongitude(),loc.hasBearing()?loc.getBearing():0f,true);speedText.setText(String.format(Locale.getDefault(),"%.0f",speed));zoneText.setText(zone);gpsText.setText(String.format(Locale.getDefault(),"GPS ±%.0f m",loc.hasAccuracy()?loc.getAccuracy():0f));};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);Api.init(this);MapLibre.getInstance(this);buildUi(b);requestNeededPermissions();Api.syncPendingAsync();
    }

    private void buildUi(Bundle b){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        mapView=new MapView(this);mapView.onCreate(b);root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(8),dp(12),dp(8));top.setBackground(rounded(Color.argb(235,7,25,31),20,0,0));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);TextView title=text("MAPA TRAYECTOS",20,TEXT,true);zoneText=text(zone,13,GOLD,true);labels.addView(title);labels.addView(zoneText);top.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        followBtn=button("SEGUIR");followBtn.setOnClickListener(v->{follow=!follow;followBtn.setText(follow?"SEGUIR":"LIBRE");if(follow)recenter();});top.addView(followBtn,new LinearLayout.LayoutParams(dp(84),dp(46)));
        historyBtn=button("HISTORIAL");historyBtn.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(105),dp(46));hp.setMargins(dp(6),0,0,0);top.addView(historyBtn,hp);
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,dp(78),Gravity.TOP);topLp.setMargins(dp(10),dp(10),dp(10),0);root.addView(top,topLp);

        LinearLayout speedCard=new LinearLayout(this);speedCard.setOrientation(LinearLayout.VERTICAL);speedCard.setGravity(Gravity.CENTER);speedCard.setBackground(rounded(Color.argb(235,255,255,255),26,1,Color.rgb(215,220,224)));speedText=text("0",39,Color.rgb(14,27,32),true);TextView kmh=text("km/h",11,Color.rgb(85,94,98),true);speedCard.addView(speedText);speedCard.addView(kmh);FrameLayout.LayoutParams spdLp=new FrameLayout.LayoutParams(dp(100),dp(96),Gravity.TOP|Gravity.RIGHT);spdLp.setMargins(0,dp(98),dp(14),0);root.addView(speedCard,spdLp);

        gpsText=text("GPS buscando…",11,TEXT,true);gpsText.setGravity(Gravity.CENTER);gpsText.setBackground(rounded(Color.argb(220,7,25,31),18,0,0));FrameLayout.LayoutParams gpsLp=new FrameLayout.LayoutParams(dp(118),dp(34),Gravity.TOP|Gravity.LEFT);gpsLp.setMargins(dp(14),dp(98),0,0);root.addView(gpsText,gpsLp);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(14),dp(12),dp(14),dp(12));bottom.setBackground(rounded(Color.argb(244,7,25,31),26,1,Color.rgb(31,74,84)));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);modeText=text("LISTO PARA JORNADA",16,TEXT,true);head.addView(modeText,new LinearLayout.LayoutParams(0,-2,1));endShiftBtn=button("CERRAR JORNADA");endShiftBtn.setTextSize(9);endShiftBtn.setVisibility(View.GONE);endShiftBtn.setOnClickListener(v->sendAction(TrackingService.ACTION_STOP_SHIFT));head.addView(endShiftBtn,new LinearLayout.LayoutParams(dp(130),dp(38)));bottom.addView(head,new LinearLayout.LayoutParams(-1,dp(42)));

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);distanceText=metric(metrics,"0.0","km");elapsedText=metric(metrics,"00:00","tiempo");movingText=metric(metrics,"00:00","mov.");stoppedText=metric(metrics,"00:00","det.");bottom.addView(metrics,new LinearLayout.LayoutParams(-1,dp(64)));
        idleText=text("Sin viaje: 00:00  ·  Máx.: 0 km/h",12,MUTED,true);idleText.setGravity(Gravity.CENTER);bottom.addView(idleText,new LinearLayout.LayoutParams(-1,dp(30)));
        slider=new SlideActionView(this);slider.setOnCompleted(this::performSliderAction);bottom.addView(slider,new LinearLayout.LayoutParams(-1,dp(72)));
        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,dp(220),Gravity.BOTTOM);bottomLp.setMargins(dp(10),0,dp(10),dp(8));root.addView(bottom,bottomLp);

        setContentView(root);updateUi();
        mapView.getMapAsync(m->{map=m;map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new LatLng(-34.88,-56.08),11.4));map.setStyle(new Style.Builder().fromUri(STYLE_URL),s->{style=s;style.addImage(DRIVER_IMAGE,driverArrow());GeoJsonSource ds=new GeoJsonSource(DRIVER_SOURCE,pointGeoJson(-34.88,-56.08,0));style.addSource(ds);SymbolLayer dl=new SymbolLayer(DRIVER_LAYER,DRIVER_SOURCE).withProperties(PropertyFactory.iconImage(DRIVER_IMAGE),PropertyFactory.iconSize(0.58f),PropertyFactory.iconAllowOverlap(true),PropertyFactory.iconIgnorePlacement(true),PropertyFactory.iconRotate(Expression.get("bearing")));style.addLayer(dl);GeoJsonSource rs=new GeoJsonSource(ROUTE_SOURCE,lineGeoJson(route));style.addSource(rs);LineLayer rl=new LineLayer(ROUTE_LAYER,ROUTE_SOURCE).withProperties(PropertyFactory.lineColor(GOLD),PropertyFactory.lineWidth(5f),PropertyFactory.lineOpacity(0.88f));style.addLayerBelow(rl,DRIVER_LAYER);if(!tripId.isEmpty())loadRoute(tripId);});});
    }

    private void performSliderAction(){if(!shiftActive)sendAction(TrackingService.ACTION_START_SHIFT);else if(!tripActive)sendAction(TrackingService.ACTION_START_TRIP);else sendAction(TrackingService.ACTION_STOP_TRIP);}

    private void sendAction(String action){
        if((TrackingService.ACTION_START_SHIFT.equals(action)||TrackingService.ACTION_START_TRIP.equals(action))&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}
        Intent i=new Intent(this,TrackingService.class).setAction(action);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    }

    private void applyState(Intent i){
        boolean oldTrip=tripActive;String oldTripId=tripId;
        shiftActive=i.getBooleanExtra("shift_active",false);tripActive=i.getBooleanExtra("trip_active",false);shiftId=i.getStringExtra("shift_id");tripId=i.getStringExtra("trip_id");if(shiftId==null)shiftId="";if(tripId==null)tripId="";shiftStarted=i.getLongExtra("shift_started",0);tripStarted=i.getLongExtra("trip_started",0);shiftDistance=i.getDoubleExtra("shift_distance",0);tripDistance=i.getDoubleExtra("trip_distance",0);shiftMoving=i.getLongExtra("shift_moving",0);shiftStopped=i.getLongExtra("shift_stopped",0);shiftTripMs=i.getLongExtra("shift_trip_ms",0);tripMoving=i.getLongExtra("trip_moving",0);tripStopped=i.getLongExtra("trip_stopped",0);tripMax=i.getDoubleExtra("trip_max",0);zone=i.getStringExtra("zone");if(zone==null||zone.isEmpty())zone="Buscando zona…";
        if(i.getBooleanExtra("has_location",false)){double lat=i.getDoubleExtra("lat",0),lon=i.getDoubleExtra("lon",0);float bearing=i.getFloatExtra("bearing",0),speed=i.getFloatExtra("speed",0),accuracy=i.getFloatExtra("accuracy",0);speedText.setText(String.format(Locale.getDefault(),"%.0f",speed));gpsText.setText(String.format(Locale.getDefault(),"GPS ±%.0f m",accuracy));updateDriver(lat,lon,bearing,true);if(tripActive)addRoutePoint(lat,lon);}
        if(!oldTrip&&tripActive){route.clear();loadedTripId="";refreshRoute();}
        if(tripActive&&!tripId.equals(oldTripId)&&!tripId.equals(loadedTripId))loadRoute(tripId);
        if(shiftActive)stopPreview();else startPreview();updateUi();
    }

    private void updateUi(){
        zoneText.setText(zone);
        long now=System.currentTimeMillis();long elapsed=shiftActive?Math.max(0,now-shiftStarted):0;long shownElapsed=tripActive?Math.max(0,now-tripStarted):elapsed;double shownDist=tripActive?tripDistance:shiftDistance;long mv=tripActive?tripMoving:shiftMoving;long st=tripActive?tripStopped:shiftStopped;long idle=shiftActive?Math.max(0,elapsed-shiftTripMs):0;
        distanceText.setText(String.format(Locale.getDefault(),"%.1f\nkm",shownDist/1000.0));elapsedText.setText(formatDuration(shownElapsed)+"\ntiempo");movingText.setText(formatDuration(mv)+"\nmov.");stoppedText.setText(formatDuration(st)+"\ndet.");idleText.setText("Sin viaje: "+formatDuration(idle)+"  ·  Máx.: "+String.format(Locale.getDefault(),"%.0f km/h",tripMax));
        if(!shiftActive){modeText.setText("LISTO PARA JORNADA");slider.setLabel("DESLIZAR PARA INICIAR JORNADA  →");endShiftBtn.setVisibility(View.GONE);}else if(!tripActive){modeText.setText("JORNADA ACTIVA · "+zone);slider.setLabel("DESLIZAR PARA INICIAR VIAJE  →");endShiftBtn.setVisibility(View.VISIBLE);}else{modeText.setText("VIAJE ACTIVO · "+zone);slider.setLabel("DESLIZAR PARA FINALIZAR VIAJE  →");endShiftBtn.setVisibility(View.GONE);}
    }

    private TextView metric(LinearLayout row,String value,String label){TextView t=text(value+"\n"+label,14,TEXT,true);t.setGravity(Gravity.CENTER);row.addView(t,new LinearLayout.LayoutParams(0,-1,1));return t;}

    private void updateDriver(double lat,double lon,float bearing,boolean maybeFollow){if(style==null||map==null)return;GeoJsonSource s=style.getSourceAs(DRIVER_SOURCE);if(s!=null)s.setGeoJson(pointGeoJson(lat,lon,bearing));if(maybeFollow&&follow&&System.currentTimeMillis()-lastCameraAt>2200){lastCameraAt=System.currentTimeMillis();CameraPosition cp=new CameraPosition.Builder().target(new LatLng(lat,lon)).zoom(12.7).tilt(18).bearing(0).build();map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp),700);}}
    private void recenter(){if(map==null)return;follow=true;followBtn.setText("SEGUIR");}

    private void addRoutePoint(double lat,double lon){if(!route.isEmpty()){double[] p=route.get(route.size()-1);float[] out=new float[1];Location.distanceBetween(p[0],p[1],lat,lon,out);if(out[0]<2)return;}route.add(new double[]{lat,lon});if(route.size()>6000)route.remove(0);refreshRoute();}
    private void refreshRoute(){if(style==null)return;GeoJsonSource s=style.getSourceAs(ROUTE_SOURCE);if(s!=null)s.setGeoJson(lineGeoJson(route));}
    private void loadRoute(String id){if(id==null||id.isEmpty())return;loadedTripId=id;new Thread(()->{TrackDb db=new TrackDb(this);JSONArray a=db.getTripPoints(id);db.close();ArrayList<double[]> r=new ArrayList<>();for(int x=0;x<a.length();x++){JSONObject o=a.optJSONObject(x);if(o!=null)r.add(new double[]{o.optDouble("lat"),o.optDouble("lon")});}runOnUiThread(()->{route.clear();route.addAll(r);refreshRoute();});},"load-route").start();}

    private String pointGeoJson(double lat,double lon,float bearing){return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"bearing\":"+bearing+"},\"geometry\":{\"type\":\"Point\",\"coordinates\":["+lon+","+lat+"]}}]}";}
    private String lineGeoJson(List<double[]> pts){StringBuilder sb=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");for(int i=0;i<pts.size();i++){if(i>0)sb.append(',');double[] p=pts.get(i);sb.append('[').append(p[1]).append(',').append(p[0]).append(']');}return sb.append("]}}]}").toString();}

    private Bitmap driverArrow(){int n=72;Bitmap b=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint halo=new Paint(Paint.ANTI_ALIAS_FLAG);halo.setColor(Color.WHITE);halo.setStyle(Paint.Style.FILL);Path ph=new Path();ph.moveTo(36,3);ph.lineTo(64,65);ph.lineTo(36,54);ph.lineTo(8,65);ph.close();c.drawPath(ph,halo);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(BG);p.setStyle(Paint.Style.FILL);Path q=new Path();q.moveTo(36,10);q.lineTo(57,57);q.lineTo(36,49);q.lineTo(15,57);q.close();c.drawPath(q,p);Paint g=new Paint(Paint.ANTI_ALIAS_FLAG);g.setColor(GOLD);g.setStyle(Paint.Style.FILL);Path z=new Path();z.moveTo(36,16);z.lineTo(45,48);z.lineTo(36,44);z.lineTo(27,48);z.close();c.drawPath(z,g);return b;}

    private void requestNeededPermissions(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);else startPreview();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ_LOCATION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startPreview();}

    private void startPreview(){if(shiftActive||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;if(previewLm==null)previewLm=(LocationManager)getSystemService(LOCATION_SERVICE);if(previewLm==null)return;try{Location l=previewLm.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(l!=null)previewListener.onLocationChanged(l);previewLm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2500L,3f,previewListener,Looper.getMainLooper());if(previewLm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))previewLm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,6000L,15f,previewListener,Looper.getMainLooper());}catch(Exception ignored){}}
    private void stopPreview(){if(previewLm!=null)try{previewLm.removeUpdates(previewListener);}catch(Exception ignored){}}

    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setTextColor(TEXT);b.setAllCaps(false);b.setPadding(dp(8),0,dp(8),0);b.setBackground(rounded(PANEL,16,1,Color.rgb(50,91,100)));return b;}
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

    public static final class SlideActionView extends LinearLayout {
        private final TextView label;private final SeekBar seek;private Runnable completed;
        public SlideActionView(Context c){super(c);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(12,4,12,4);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(12,57,66));bg.setCornerRadius(36);setBackground(bg);label=new TextView(c);label.setTextColor(Color.WHITE);label.setTextSize(12);label.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);label.setGravity(Gravity.CENTER);addView(label,new LinearLayout.LayoutParams(-1,28));seek=new SeekBar(c);seek.setMax(100);seek.setProgress(0);addView(seek,new LinearLayout.LayoutParams(-1,38));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){if(s.getProgress()>=88&&completed!=null)completed.run();s.setProgress(0);}});}
        public void setLabel(String s){label.setText(s);}public void setOnCompleted(Runnable r){completed=r;}
    }
}
