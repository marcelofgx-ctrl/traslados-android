package uy.com.mapatrayectos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
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
    private static final int REQ_LOCATION=7001,REQ_NOTIF=7002,REQ_OVERLAY=7003,REQ_EXPORT_DB=7010,REQ_IMPORT_DB=7011;
    private static final long SPEED_UI_STALE_MS=3500L;
    private static final String STYLE_URL="https://tiles.openfreemap.org/styles/liberty";
    private static final String DRIVER_SOURCE="driver-source",DRIVER_LAYER="driver-layer",DRIVER_IMAGE="driver-arrow";
    private static final String ROUTE_SOURCE="route-source",ROUTE_CASING_LAYER="route-casing",ROUTE_GLOW_LAYER="route-glow",ROUTE_LAYER="route-layer",ROUTE_POINTS_SOURCE="route-points",ROUTE_POINTS_LAYER="route-points-layer",RETURN_SOURCE="route-return-source",RETURN_GLOW_LAYER="route-return-glow",RETURN_LAYER="route-return-layer";
    private static final String START_SOURCE="route-start",END_SOURCE="route-end",START_HALO="route-start-halo",START_LAYER="route-start-dot",END_HALO="route-end-halo",END_LAYER="route-end-dot";
    private static final int BG=Color.rgb(7,25,31),PANEL=Color.rgb(8,42,50),GOLD=Color.rgb(224,193,111),TEXT=Color.rgb(245,244,238),MUTED=Color.rgb(196,207,209),GREEN=Color.rgb(54,190,125),RED=Color.rgb(225,78,84),ROUTE=Color.rgb(73,199,225),RETURN_ROUTE=Color.rgb(235,166,65);

    private MapView mapView; private MapLibreMap map; private Style style;
    private TextView zoneText,modeText,distanceText,elapsedText,movingText,stoppedText,idleText,gpsText,sheetMetaText,sheetChevron;
    private Button historyBtn,followBtn,bubbleBtn,maintenanceBtn;
    private SpeedGaugeView speedGauge; private CompassView compassView;
    private SlideActionView slider,endShiftSlider;
    private FrameLayout rootFrame;
    private LinearLayout bottomSheet;
    private HistoryBottomSheet historySheet;
    private View sheetHandle;
    private boolean historyOpen=false;
    private int historySheetState=1;
    private float historyDownY=0f;
    private int historyStartHeight=0;
    private boolean historyDragging=false;
    private static final int HISTORY_PEEK_DP=96,HISTORY_MID_DP=360,HISTORY_FULL_DP=600;
    private int sheetState=1;
    private float sheetDownY=0f;
    private int sheetStartHeight=0;
    private boolean sheetDragging=false;
    private static final int SHEET_PEEK_DP=34,SHEET_MID_DP=276,SHEET_FULL_DP=306;
    private boolean shiftActive=false,tripActive=false,stopActive=false,follow=true,routeEnded=false;
    private String shiftId="",tripId="",tripType="other",tripStage="none";
    private long shiftStarted=0,tripStarted=0,pickupAt=0,stopStarted=0,shiftMoving=0,shiftStopped=0,shiftTripMs=0,tripMoving=0,tripStopped=0;
    private double shiftDistance=0,tripDistance=0,tripMax=0;
    private String zone="Buscando zona…",loadedTripId="";
    private final ArrayList<double[]> route=new ArrayList<>();
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private LocationManager previewLm; private long lastCameraAt=0,lastRouteDbSyncAt=0;
    private double lastLat=Double.NaN,lastLon=Double.NaN; private float lastBearing=0f,currentSpeedKmh=0f,cameraBearing=0f;
    private float previewLastSpeed=0f; private long previewLastTs=0L;
    private Location previewLastLocation; private boolean previewMoving=false; private int previewMovingEvidence=0,previewStoppedEvidence=0;

    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(TrackingService.ACTION_STATE.equals(i.getAction()))applyState(i);}};
    private final LocationListener previewListener=loc->{
        if(loc==null)return;float accuracy=loc.hasAccuracy()?loc.getAccuracy():50f;if(accuracy>45f)return;long ts=loc.getTime()>0?loc.getTime():System.currentTimeMillis();boolean isGps=LocationManager.GPS_PROVIDER.equals(loc.getProvider());float raw=loc.hasSpeed()?Math.max(0f,loc.getSpeed()*3.6f):0f;if(raw>160f)return;
        if(previewLastLocation==null){boolean speedAccuracyGood=!loc.hasSpeedAccuracy()||loc.getSpeedAccuracyMetersPerSecond()*3.6f<=5.0f;previewMoving=isGps&&accuracy<=10f&&speedAccuracyGood&&raw>=15f;previewMovingEvidence=previewStoppedEvidence=0;previewLastLocation=new Location(loc);previewLastTs=ts;previewLastSpeed=previewMoving?raw:0f;float shown=previewMoving?raw:0f;if(shown<3f)shown=0f;currentSpeedKmh=shown;zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());updateDriver(loc.getLatitude(),loc.getLongitude(),loc.hasBearing()?loc.getBearing():lastBearing,true);speedGauge.setSpeed(shown);zoneText.setText(zone);setGpsBadge(accuracy,0);return;}
        long dt=ts-previewLastTs;if(dt<=0)return;double sec=dt/1000.0;double dist=previewLastLocation.distanceTo(loc);float derived=(float)((dist/sec)*3.6);if(derived>160f||(dt<800L&&derived>60f))return;float candidate=loc.hasSpeed()?raw:derived;if(candidate>previewLastSpeed&&previewLastTs>0){double accel=((candidate-previewLastSpeed)/3.6)/Math.max(.25,sec);if(accel>7.0)candidate=derived;}if(candidate>160f)return;
        float lastAccuracy=previewLastLocation.hasAccuracy()?previewLastLocation.getAccuracy():accuracy;float noiseRadius=Math.max(3.0f,Math.min(14.0f,(lastAccuracy+accuracy)*0.30f));float movementThreshold=Math.max(1.8f,Math.min(4.5f,noiseRadius*0.35f));boolean displacementSupportsMove=dist>=movementThreshold&&derived>=3.5f;boolean speedAccuracyGood=!loc.hasSpeedAccuracy()||loc.getSpeedAccuracyMetersPerSecond()*3.6f<=5.0f;boolean strongGpsSpeed=isGps&&accuracy<=12f&&speedAccuracyGood&&candidate>=14f;boolean movementEvidenceNow=candidate>=5f&&(displacementSupportsMove||strongGpsSpeed);boolean stationaryGeometry=dist<=movementThreshold;boolean stoppedEvidenceNow=candidate<=2.5f||(stationaryGeometry&&candidate<13f);
        if(previewMoving){previewMovingEvidence=0;if(stoppedEvidenceNow){previewStoppedEvidence++;if(previewStoppedEvidence>=2){previewMoving=false;previewStoppedEvidence=0;}}else previewStoppedEvidence=0;}else{previewStoppedEvidence=0;if(movementEvidenceNow){previewMovingEvidence++;if(previewMovingEvidence>=2){previewMoving=true;previewMovingEvidence=0;}}else previewMovingEvidence=0;}
        float shown=previewMoving?candidate:0f;if(stationaryGeometry&&candidate<13f)shown=0f;if(shown<3f)shown=0f;previewLastLocation=new Location(loc);previewLastTs=ts;previewLastSpeed=shown;currentSpeedKmh=shown;zone=ZoneResolver.resolve(loc.getLatitude(),loc.getLongitude());updateDriver(loc.getLatitude(),loc.getLongitude(),loc.hasBearing()&&shown>=5f?loc.getBearing():lastBearing,true);speedGauge.setSpeed(shown);zoneText.setText(zone);setGpsBadge(accuracy,0);
    };

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);SharedPreferences live=getSharedPreferences("tracking_state",MODE_PRIVATE);shiftActive=live.getBoolean("shift_active",false);tripActive=live.getBoolean("trip_active",false);stopActive=live.getBoolean("stop_active",false);tripType=live.getString("trip_type","other");tripStage=live.getString("trip_stage",tripActive?"onboard":"none");pickupAt=live.getLong("pickup_at",0);stopStarted=live.getLong("stop_started",0);Api.init(this);MapLibre.getInstance(this);buildUi(b);requestNeededPermissions();new Thread(()->{TelemetryQuality.repairHistoricalMaxima(getApplicationContext());Api.syncPendingAsync();},"repair-telemetry").start();}

    private void buildUi(Bundle b){
        FrameLayout root=new FrameLayout(this);rootFrame=root;root.setBackgroundColor(BG);mapView=new MapView(this);mapView.onCreate(b);root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackground(headerGradient());top.setElevation(dp(7));
        TextView brand=text("⌖",17,BG,true);brand.setGravity(Gravity.CENTER);brand.setContentDescription("Mapa Trayectos");brand.setBackground(rounded(Color.rgb(238,198,88),16,0,0));LinearLayout.LayoutParams brandLp=new LinearLayout.LayoutParams(dp(30),dp(30));brandLp.setMargins(0,0,dp(5),0);top.addView(brand,brandLp);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("MAPA TRAYECTOS",13.0f,TEXT,true);title.setSingleLine(true);zoneText=text(zone,10.8f,Color.rgb(236,195,92),true);zoneText.setSingleLine(true);labels.addView(title);labels.addView(zoneText);top.addView(labels,new LinearLayout.LayoutParams(0,-1,1));
        followBtn=button("SEGUIR");followBtn.setBackground(headerButtonGradient(GOLD,Color.argb(62,224,193,111)));followBtn.setOnClickListener(v->{follow=!follow;if(follow){followBtn.setText("SEGUIR");recenter();}else{followBtn.setText("LIBRE");northUpFreeMode();}});top.addView(followBtn,new LinearLayout.LayoutParams(dp(58),dp(42)));
        historyBtn=button("HIST.");historyBtn.setBackground(headerButtonGradient(GOLD,Color.argb(72,224,193,111)));historyBtn.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(52),dp(42));hp.setMargins(dp(3),0,0,0);top.addView(historyBtn,hp);
        bubbleBtn=button("◎");bubbleBtn.setTextSize(18);bubbleBtn.setContentDescription("Burbuja flotante: aparece al salir de Mapa Trayectos durante una jornada");bubbleBtn.setOnClickListener(v->openBubblePermission());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(40),dp(42));bp.setMargins(dp(3),0,0,0);top.addView(bubbleBtn,bp);
        maintenanceBtn=button("⚒");maintenanceBtn.setTextSize(19);maintenanceBtn.setContentDescription("Mantenimiento");maintenanceBtn.setBackground(headerButtonGradient(GOLD,Color.argb(48,224,193,111)));maintenanceBtn.setOnClickListener(this::showMaintenanceMenu);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(40),dp(42));mp.setMargins(dp(3),0,0,0);top.addView(maintenanceBtn,mp);
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,dp(74),Gravity.TOP);topLp.setMargins(dp(10),dp(8),dp(10),0);root.addView(top,topLp);

        speedGauge=new SpeedGaugeView(this);speedGauge.setElevation(dp(6));FrameLayout.LayoutParams spdLp=new FrameLayout.LayoutParams(dp(100),dp(100),Gravity.TOP|Gravity.RIGHT);spdLp.setMargins(0,dp(94),dp(12),0);root.addView(speedGauge,spdLp);
        gpsText=text("●  GPS buscando…",11.2f,TEXT,true);gpsText.setGravity(Gravity.CENTER);gpsText.setElevation(dp(5));gpsText.setBackground(statusPillGradient(GOLD));FrameLayout.LayoutParams gpsLp=new FrameLayout.LayoutParams(dp(142),dp(38),Gravity.TOP|Gravity.LEFT);gpsLp.setMargins(dp(14),dp(96),0,0);root.addView(gpsText,gpsLp);
        compassView=new CompassView(this);compassView.setElevation(dp(5));FrameLayout.LayoutParams compassLp=new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.TOP|Gravity.CENTER_HORIZONTAL);compassLp.setMargins(0,dp(92),0,0);root.addView(compassView,compassLp);

        bottomSheet=new LinearLayout(this);bottomSheet.setOrientation(LinearLayout.VERTICAL);bottomSheet.setPadding(dp(14),dp(4),dp(14),dp(10));bottomSheet.setBackground(panelGradient());bottomSheet.setElevation(dp(9));

        FrameLayout handleBar=new FrameLayout(this);handleBar.setContentDescription("Panel de jornada. Deslizá hacia arriba o abajo para abrir y cerrar.");sheetHandle=handleBar;
        View grip=new View(this);grip.setBackground(rounded(Color.rgb(128,157,161),3,0,0));FrameLayout.LayoutParams gripLp=new FrameLayout.LayoutParams(dp(46),dp(5),Gravity.CENTER);handleBar.addView(grip,gripLp);handleBar.setOnTouchListener(this::onSheetTouch);bottomSheet.addView(handleBar,new LinearLayout.LayoutParams(-1,dp(22)));

        LinearLayout head=new LinearLayout(this);head.setTag("sheet_head");head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(2),0,0,0);head.setOnTouchListener(this::onSheetTouch);
        modeText=text("LISTO PARA JORNADA",16.5f,TEXT,true);modeText.setSingleLine(true);head.addView(modeText,new LinearLayout.LayoutParams(0,-2,1));
        sheetChevron=text("⌃",24,GOLD,true);sheetChevron.setGravity(Gravity.CENTER);sheetChevron.setContentDescription("Expandir o contraer panel");sheetChevron.setOnClickListener(v->cycleSheetState());head.addView(sheetChevron,new LinearLayout.LayoutParams(dp(40),dp(40)));
        bottomSheet.addView(head,new LinearLayout.LayoutParams(-1,dp(46)));

        sheetMetaText=text("Preparado · GPS y recorridos",11.5f,Color.rgb(226,193,105),true);sheetMetaText.setGravity(Gravity.CENTER_VERTICAL);sheetMetaText.setTag("sheet_meta");sheetMetaText.setSingleLine(true);bottomSheet.addView(sheetMetaText,new LinearLayout.LayoutParams(-1,dp(28)));

        idleText=text("Sin viaje 00:00   ·   Vel. máx. 0 km/h",11.5f,Color.rgb(201,216,218),true);idleText.setTag("sheet_detail");idleText.setGravity(Gravity.CENTER_VERTICAL);idleText.setSingleLine(true);bottomSheet.addView(idleText,new LinearLayout.LayoutParams(-1,dp(24)));

        LinearLayout closeRow=new LinearLayout(this);closeRow.setTag("sheet_shift_close");closeRow.setGravity(Gravity.CENTER_VERTICAL);TextView closeLabel=text("FIN DE JORNADA",10.5f,MUTED,true);closeRow.addView(closeLabel,new LinearLayout.LayoutParams(0,-2,1));
        endShiftSlider=new SlideActionView(this);endShiftSlider.setMode(SlideActionView.MODE_SHIFT_CLOSE);endShiftSlider.setLabel("← CERRAR JORNADA");endShiftSlider.setOnCompleted(()->sendAction(TrackingService.ACTION_STOP_SHIFT,null,null,0));LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(dp(164),dp(40));closeRow.addView(endShiftSlider,closeLp);bottomSheet.addView(closeRow,new LinearLayout.LayoutParams(-1,dp(44)));

        LinearLayout metrics=new LinearLayout(this);metrics.setTag("sheet_metrics");metrics.setOrientation(LinearLayout.HORIZONTAL);metrics.setGravity(Gravity.CENTER_VERTICAL);
        distanceText=metric(metrics,"0.0","KM","╱╲");elapsedText=metric(metrics,"00:00","TIEMPO","◷");movingText=metric(metrics,"00:00","MOV.","▶");stoppedText=metric(metrics,"00:00","DET.","▢");
        bottomSheet.addView(metrics,new LinearLayout.LayoutParams(-1,dp(72)));

        FrameLayout actionWrap=new FrameLayout(this);actionWrap.setTag("sheet_action");slider=new SlideActionView(this);slider.setOnCompleted(this::performSliderAction);slider.setOnMidpointCompleted(this::performMidpointAction);
        FrameLayout.LayoutParams actionLp=new FrameLayout.LayoutParams(-1,dp(64));actionLp.setMargins(dp(1),0,dp(1),0);actionWrap.addView(slider,actionLp);
        LinearLayout.LayoutParams actionWrapLp=new LinearLayout.LayoutParams(-1,dp(78));actionWrapLp.setMargins(0,dp(8),0,0);bottomSheet.addView(actionWrap,actionWrapLp);

        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,dp(SHEET_PEEK_DP),Gravity.BOTTOM);bottomLp.setMargins(dp(8),0,dp(8),dp(5));root.addView(bottomSheet,bottomLp);

        root.setOnApplyWindowInsetsListener((v,insets)->{int topInset,bottomInset;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets st=insets.getInsets(WindowInsets.Type.statusBars());android.graphics.Insets nb=insets.getInsets(WindowInsets.Type.navigationBars());topInset=st.top;bottomInset=nb.bottom;}else{topInset=insets.getSystemWindowInsetTop();bottomInset=insets.getSystemWindowInsetBottom();}FrameLayout.LayoutParams a=(FrameLayout.LayoutParams)top.getLayoutParams();a.topMargin=topInset+dp(6);top.setLayoutParams(a);FrameLayout.LayoutParams c=(FrameLayout.LayoutParams)speedGauge.getLayoutParams();c.topMargin=topInset+dp(92);speedGauge.setLayoutParams(c);FrameLayout.LayoutParams g=(FrameLayout.LayoutParams)gpsText.getLayoutParams();g.topMargin=topInset+dp(94);gpsText.setLayoutParams(g);FrameLayout.LayoutParams co=(FrameLayout.LayoutParams)compassView.getLayoutParams();co.topMargin=topInset+dp(90);compassView.setLayoutParams(co);FrameLayout.LayoutParams z=(FrameLayout.LayoutParams)bottomSheet.getLayoutParams();z.bottomMargin=bottomInset+dp(5);bottomSheet.setLayoutParams(z);if(historySheet!=null){FrameLayout.LayoutParams hz=(FrameLayout.LayoutParams)historySheet.getLayoutParams();hz.bottomMargin=bottomInset+dp(5);historySheet.setLayoutParams(hz);}return insets;});root.requestApplyInsets();
        setContentView(root);sheetState=Math.max(0,Math.min(2,getSharedPreferences("ui_prefs",MODE_PRIVATE).getInt("dashboard_sheet_state_r13",1)));setSheetState(sheetState,false);updateUi();
        mapView.getMapAsync(m->{map=m;map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new LatLng(-34.88,-56.08),11.8));map.setStyle(new Style.Builder().fromUri(STYLE_URL),s->{
            style=s;style.addImage(DRIVER_IMAGE,driverArrow());
            GeoJsonSource ds=new GeoJsonSource(DRIVER_SOURCE,pointGeoJson(-34.88,-56.08,0));style.addSource(ds);
            SymbolLayer dl=new SymbolLayer(DRIVER_LAYER,DRIVER_SOURCE).withProperties(PropertyFactory.iconImage(DRIVER_IMAGE),PropertyFactory.iconSize(0.86f),PropertyFactory.iconAllowOverlap(true),PropertyFactory.iconIgnorePlacement(true),PropertyFactory.iconRotate(Expression.get("bearing")),PropertyFactory.iconRotationAlignment("viewport"));style.addLayer(dl);
            GeoJsonSource rs=new GeoJsonSource(ROUTE_SOURCE,lineGeoJson(route));style.addSource(rs);
            LineLayer casing=new LineLayer(ROUTE_CASING_LAYER,ROUTE_SOURCE).withProperties(PropertyFactory.lineColor(BG),PropertyFactory.lineWidth(11f),PropertyFactory.lineOpacity(0.80f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));style.addLayerBelow(casing,DRIVER_LAYER);
            LineLayer glow=new LineLayer(ROUTE_GLOW_LAYER,ROUTE_SOURCE).withProperties(PropertyFactory.lineColor(ROUTE),PropertyFactory.lineWidth(8.8f),PropertyFactory.lineOpacity(0.28f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));style.addLayerBelow(glow,DRIVER_LAYER);
            LineLayer rl=new LineLayer(ROUTE_LAYER,ROUTE_SOURCE).withProperties(PropertyFactory.lineColor(ROUTE),PropertyFactory.lineWidth(5.8f),PropertyFactory.lineOpacity(0.99f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));style.addLayerBelow(rl,DRIVER_LAYER);
            GeoJsonSource ret=new GeoJsonSource(RETURN_SOURCE,emptyFeatureCollection());style.addSource(ret);
            LineLayer returnGlow=new LineLayer(RETURN_GLOW_LAYER,RETURN_SOURCE).withProperties(PropertyFactory.lineColor(RETURN_ROUTE),PropertyFactory.lineWidth(8.0f),PropertyFactory.lineOpacity(0.28f),PropertyFactory.lineOffset(2.2f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));style.addLayerBelow(returnGlow,DRIVER_LAYER);
            LineLayer returnLine=new LineLayer(RETURN_LAYER,RETURN_SOURCE).withProperties(PropertyFactory.lineColor(RETURN_ROUTE),PropertyFactory.lineWidth(4.8f),PropertyFactory.lineOpacity(0.98f),PropertyFactory.lineOffset(2.2f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));style.addLayerBelow(returnLine,DRIVER_LAYER);
            GeoJsonSource rps=new GeoJsonSource(ROUTE_POINTS_SOURCE,routePointsGeoJson(route));style.addSource(rps);
            CircleLayer crumbs=new CircleLayer(ROUTE_POINTS_LAYER,ROUTE_POINTS_SOURCE).withProperties(PropertyFactory.circleRadius(2.0f),PropertyFactory.circleColor(Color.rgb(180,241,250)),PropertyFactory.circleOpacity(0.72f),PropertyFactory.circleStrokeColor(ROUTE),PropertyFactory.circleStrokeWidth(0.7f));style.addLayerBelow(crumbs,DRIVER_LAYER);
            GeoJsonSource ss=new GeoJsonSource(START_SOURCE,emptyFeatureCollection());GeoJsonSource es=new GeoJsonSource(END_SOURCE,emptyFeatureCollection());style.addSource(ss);style.addSource(es);
            style.addLayer(new CircleLayer(START_HALO,START_SOURCE).withProperties(PropertyFactory.circleRadius(10f),PropertyFactory.circleColor(Color.WHITE),PropertyFactory.circleOpacity(0.88f)));
            style.addLayer(new CircleLayer(START_LAYER,START_SOURCE).withProperties(PropertyFactory.circleRadius(6.5f),PropertyFactory.circleColor(GREEN),PropertyFactory.circleStrokeColor(BG),PropertyFactory.circleStrokeWidth(1.3f)));
            style.addLayer(new CircleLayer(END_HALO,END_SOURCE).withProperties(PropertyFactory.circleRadius(10f),PropertyFactory.circleColor(Color.WHITE),PropertyFactory.circleOpacity(0.88f)));
            style.addLayer(new CircleLayer(END_LAYER,END_SOURCE).withProperties(PropertyFactory.circleRadius(6.5f),PropertyFactory.circleColor(RED),PropertyFactory.circleStrokeColor(BG),PropertyFactory.circleStrokeWidth(1.3f)));
            refreshRoute();if(!Double.isNaN(lastLat))updateDriver(lastLat,lastLon,lastBearing,false);if(!tripId.isEmpty())loadRoute(tripId);if(shiftActive)uiHandler.postDelayed(this::requestServiceState,180);
        });});
    }

    private int historyHeightDp(int state){return state<=0?HISTORY_PEEK_DP:(state==1?HISTORY_MID_DP:HISTORY_FULL_DP);}
    private void toggleHistoryPanel(){if(historyOpen)closeHistoryPanel();else openHistoryPanel();}
    private void openHistoryPanel(){
        if(historySheet==null)return;
        historyOpen=true;
        historySheet.reload();
        bottomSheet.setVisibility(View.GONE);
        historySheet.setVisibility(View.VISIBLE);
        historySheet.bringToFront();
        historyBtn.setBackground(headerButtonGradient(GOLD,Color.argb(76,224,193,111)));
        setHistorySheetState(1,false);
    }
    private void closeHistoryPanel(){
        if(historySheet==null)return;
        historyOpen=false;
        historySheet.setVisibility(View.GONE);
        bottomSheet.setVisibility(View.VISIBLE);
        historyBtn.setBackground(headerButtonGradient(Color.rgb(61,132,151),Color.argb(50,73,199,225)));
        setSheetState(sheetState,false);
    }
    private void setHistorySheetHeightPx(int h){
        if(historySheet==null)return;
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)historySheet.getLayoutParams();
        lp.height=Math.max(dp(HISTORY_PEEK_DP),Math.min(dp(HISTORY_FULL_DP),h));
        historySheet.setLayoutParams(lp);
    }
    private void setHistorySheetState(int state,boolean animate){
        historySheetState=Math.max(0,Math.min(2,state));
        int target=dp(historyHeightDp(historySheetState));
        if(!animate){setHistorySheetHeightPx(target);return;}
        int start=historySheet.getLayoutParams().height;
        android.animation.ValueAnimator a=android.animation.ValueAnimator.ofInt(start,target);
        a.setDuration(220);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator());
        a.addUpdateListener(v->setHistorySheetHeightPx((Integer)v.getAnimatedValue()));
        a.start();
    }
    private boolean onHistorySheetTouch(View v,MotionEvent e){
        if(historySheet==null)return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                historyDragging=true;historyDownY=e.getRawY();historyStartHeight=historySheet.getLayoutParams().height;
                v.getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(historyDragging){
                    int h=Math.round(historyStartHeight+(historyDownY-e.getRawY()));
                    setHistorySheetHeightPx(h);return true;
                }break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(historyDragging){
                    float delta=e.getRawY()-historyDownY;historyDragging=false;
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(delta)<dp(8)){
                        setHistorySheetState(historySheetState==1?2:1,true);return true;
                    }
                    int h=historySheet.getLayoutParams().height;
                    int p=dp(HISTORY_PEEK_DP),m=dp(HISTORY_MID_DP),f=dp(HISTORY_FULL_DP);
                    int target=Math.abs(h-f)<=Math.abs(h-m)&&Math.abs(h-f)<=Math.abs(h-p)?2:(Math.abs(h-m)<=Math.abs(h-p)?1:0);
                    if(target==0&&delta>dp(40)){closeHistoryPanel();return true;}
                    setHistorySheetState(target,true);v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);return true;
                }break;
        }
        return false;
    }

    private void performSliderAction(){
        if(!shiftActive){if(sendAction(TrackingService.ACTION_START_SHIFT,null,null,0))maybeOfferBubblePermission();return;}
        if(!tripActive){TripFlowDialogs.chooseTripType(this,type->sendAction(TrackingService.ACTION_START_TRIP,type,null,0));return;}
        if("to_pickup".equals(tripStage)){sendAction(TrackingService.ACTION_PICKUP_PASSENGER,null,null,0);return;}
        if(stopActive){sendAction(TrackingService.ACTION_END_STOP,null,null,0);return;}
        TripFlowDialogs.closeTrip(this,tripType,(status,amount)->sendAction(TrackingService.ACTION_STOP_TRIP,null,status,amount));
    }
    private void performMidpointAction(){if(tripActive&&!stopActive&&"onboard".equals(tripStage))sendAction(TrackingService.ACTION_START_STOP,null,null,0);}
    private boolean sendAction(String action,String type,String status,double amount){if((TrackingService.ACTION_START_SHIFT.equals(action)||TrackingService.ACTION_START_TRIP.equals(action))&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return false;}Intent i=new Intent(this,TrackingService.class).setAction(action);if(TrackingService.ACTION_START_SHIFT.equals(action))i.putExtra("ui_visible",true);if(type!=null)i.putExtra("trip_type",type);if(status!=null)i.putExtra("trip_status",status);if(TrackingService.ACTION_STOP_TRIP.equals(action))i.putExtra("amount_uyu",amount);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);return true;}

    private void applyState(Intent i){
        boolean oldTrip=tripActive;String oldTripId=tripId;
        shiftActive=i.getBooleanExtra("shift_active",false);tripActive=i.getBooleanExtra("trip_active",false);stopActive=i.getBooleanExtra("stop_active",false);shiftId=i.getStringExtra("shift_id");tripId=i.getStringExtra("trip_id");tripType=i.getStringExtra("trip_type");tripStage=i.getStringExtra("trip_stage");if(shiftId==null)shiftId="";if(tripId==null)tripId="";if(tripType==null||tripType.isEmpty())tripType="other";if(tripStage==null||tripStage.isEmpty())tripStage=tripActive?"onboard":"none";shiftStarted=i.getLongExtra("shift_started",0);tripStarted=i.getLongExtra("trip_started",0);pickupAt=i.getLongExtra("pickup_at",0);stopStarted=i.getLongExtra("stop_started",0);shiftDistance=i.getDoubleExtra("shift_distance",0);tripDistance=i.getDoubleExtra("trip_distance",0);shiftMoving=i.getLongExtra("shift_moving",0);shiftStopped=i.getLongExtra("shift_stopped",0);shiftTripMs=i.getLongExtra("shift_trip_ms",0);tripMoving=i.getLongExtra("trip_moving",0);tripStopped=i.getLongExtra("trip_stopped",0);tripMax=i.getDoubleExtra("trip_max",0);zone=i.getStringExtra("zone");if(zone==null||zone.isEmpty())zone="Buscando zona…";
        boolean justStarted=!oldTrip&&tripActive;
        if(justStarted){route.clear();loadedTripId="";routeEnded=false;lastRouteDbSyncAt=0;refreshRoute();}
        boolean hasLoc=i.getBooleanExtra("has_location",false);
        if(hasLoc){double lat=i.getDoubleExtra("lat",0),lon=i.getDoubleExtra("lon",0);float bearing=i.getFloatExtra("bearing",0),speed=i.getFloatExtra("speed",0),accuracy=i.getFloatExtra("accuracy",0);long age=i.getLongExtra("location_age_ms",0);if(age>SPEED_UI_STALE_MS||speed<3f)speed=0f;currentSpeedKmh=speed;speedGauge.setSpeed(speed);setGpsBadge(accuracy,age);updateDriver(lat,lon,bearing,true);if(tripActive)addRoutePoint(lat,lon);}
        if(!tripActive&&(oldTrip||!route.isEmpty()||routeEnded)){clearActiveRoute();if(oldTrip)Toast.makeText(this,"✓ Viaje guardado · mapa limpio",Toast.LENGTH_SHORT).show();}
        if(tripActive){
            if(!justStarted&&!tripId.equals(loadedTripId))loadRoute(tripId);
            long now=System.currentTimeMillis();if(now-lastRouteDbSyncAt>=3500L){lastRouteDbSyncAt=now;reconcileRouteFromDb(tripId);}
        }
        if(shiftActive)stopPreview();else startPreview();updateUi();
    }

    private void setGpsBadge(float accuracy,long ageMs){int border;if(ageMs>7000L)border=Color.rgb(210,88,76);else if(accuracy<=10f)border=GREEN;else if(accuracy<=25f)border=GOLD;else border=Color.rgb(218,132,67);String suffix=ageMs>7000L?" · demora":"";gpsText.setText(String.format(Locale.getDefault(),"●  GPS ±%.0f m%s",accuracy,suffix));gpsText.setBackground(statusPillGradient(border));}

    private void updateUi(){
        if(sheetState>maxSheetState())setSheetState(maxSheetState(),true);
        zoneText.setText(zone);if(bubbleBtn!=null)bubbleBtn.setBackground(headerButtonGradient(Settings.canDrawOverlays(this)?GREEN:GOLD,Color.argb(48,54,190,125)));long now=System.currentTimeMillis();long elapsed=shiftActive?Math.max(0,now-shiftStarted):0;long shownElapsed=tripActive?Math.max(0,now-tripStarted):elapsed;double shownDist=tripActive?tripDistance:shiftDistance;long mv=tripActive?tripMoving:shiftMoving;long st=tripActive?tripStopped:shiftStopped;long idle=shiftActive?Math.max(0,elapsed-shiftTripMs):0;
        distanceText.setText(String.format(Locale.getDefault(),"%.1f",shownDist/1000.0));elapsedText.setText(formatDuration(shownElapsed));movingText.setText(formatDuration(mv));stoppedText.setText(formatDuration(st));
        if(stopActive&&stopStarted>0)idleText.setText("Parada registrada  "+formatDuration(now-stopStarted)+"    ·    Vel. máx.  "+String.format(Locale.getDefault(),"%.0f km/h",tripMax));
        else if(tripActive&&pickupAt>0)idleText.setText("Con pasajero  "+formatDuration(now-pickupAt)+"    ·    Vel. máx.  "+String.format(Locale.getDefault(),"%.0f km/h",tripMax));
        else idleText.setText("Sin viaje  "+formatDuration(idle)+"    ·    Vel. máx.  "+String.format(Locale.getDefault(),"%.0f km/h",tripMax));

        if(!shiftActive){
            sheetMetaText.setText("Preparado · GPS y recorridos");modeText.setText("LISTO PARA JORNADA");slider.setMode(SlideActionView.MODE_SHIFT_START);slider.setLabel("DESLIZAR PARA INICIAR JORNADA");
        }else if(!tripActive){
            sheetMetaText.setText("Jornada en curso · listo para un nuevo servicio");modeText.setText("JORNADA ACTIVA · "+zone);slider.setMode(SlideActionView.MODE_TRIP_START);slider.setLabel("DESLIZAR PARA IR A RECOGER");endShiftSlider.setMode(SlideActionView.MODE_SHIFT_CLOSE);endShiftSlider.setLabel("← CERRAR JORNADA");
        }else if("to_pickup".equals(tripStage)){
            sheetMetaText.setText(typeLabel(tripType)+" · etapa 1/2 · trayecto hacia la recogida");modeText.setText("EN CAMINO A RECOGER · "+typeLabel(tripType));slider.setMode(SlideActionView.MODE_PICKUP);slider.setLabel("DESLIZAR · PASAJERO RECOGIDO");
        }else if(stopActive){
            sheetMetaText.setText("Parada registrada · "+formatDuration(Math.max(0,now-stopStarted))+" · deslizá para continuar");modeText.setText("PARADA EN CURSO · "+typeLabel(tripType));slider.setMode(SlideActionView.MODE_STOP_RESUME);slider.setLabel("DESLIZAR PARA CONTINUAR");
        }else{
            sheetMetaText.setText(typeLabel(tripType)+" · pasajero a bordo · mitad = parada");modeText.setText("VIAJE EN CURSO · "+typeLabel(tripType));slider.setMode(SlideActionView.MODE_TRIP_STOP);slider.setLabel("FINALIZAR VIAJE");
        }
        updateSheetContentVisibility();
    }

    private String typeLabel(String s){if("uber".equals(s))return "UBER";if("cabify".equals(s))return "CABIFY";if("personal".equals(s))return "PERSONAL";return "OTRO";}
    private void maybeOfferBubblePermission(){if(Settings.canDrawOverlays(this))return;SharedPreferences p=getSharedPreferences("ui_prefs",MODE_PRIVATE);if(p.getBoolean("bubble_offer_r6",false))return;p.edit().putBoolean("bubble_offer_r6",true).apply();new AlertDialog.Builder(this).setTitle("Acceso flotante").setMessage("Durante una jornada, Mapa Trayectos puede quedar como un globito encima de Uber, Cabify u otras apps. Tocándolo volvés al mapa en un instante.").setPositiveButton("ACTIVAR",(d,w)->openBubblePermission()).setNegativeButton("MÁS TARDE",null).show();}
    private void openBubblePermission(){if(Settings.canDrawOverlays(this)){Toast.makeText(this,"Burbuja activada: aparecerá al salir de la app durante una jornada.",Toast.LENGTH_LONG).show();return;}try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),REQ_OVERLAY);}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_OVERLAY){updateUi();Toast.makeText(this,Settings.canDrawOverlays(this)?"Burbuja flotante activada":"La burbuja sigue desactivada",Toast.LENGTH_SHORT).show();return;}
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_EXPORT_DB){performExport(uri);return;}
        if(requestCode==REQ_IMPORT_DB){inspectAndConfirmImport(uri);}
    }

    private boolean maintenanceBusy(){
        SharedPreferences s=getSharedPreferences("tracking_state",MODE_PRIVATE);
        return shiftActive||tripActive||s.getBoolean("shift_active",false)||s.getBoolean("trip_active",false);
    }

    private void showMaintenanceMenu(View anchor){
        LinearLayout menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(dp(10),dp(8),dp(10),dp(8));menu.setBackground(new TexturedDrawable(this,Color.rgb(8,55,63),Color.rgb(12,76,82),Color.argb(210,224,193,111),18f,1f,false));
        TextView title=text("MANTENIMIENTO",10.5f,GOLD,true);title.setPadding(dp(8),dp(3),dp(8),dp(6));menu.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView export=text("▣   Exportar base",15,TEXT,true);export.setGravity(Gravity.CENTER_VERTICAL);export.setPadding(dp(10),0,dp(10),0);menu.addView(export,new LinearLayout.LayoutParams(-1,dp(54)));
        View divider=new View(this);divider.setBackgroundColor(Color.argb(70,224,193,111));menu.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        TextView imp=text("⇧   Importar base",15,TEXT,true);imp.setGravity(Gravity.CENTER_VERTICAL);imp.setPadding(dp(10),0,dp(10),0);menu.addView(imp,new LinearLayout.LayoutParams(-1,dp(54)));

        TrackDb db=new TrackDb(this);JSONObject cnt=db.counts();db.close();
        long last=getSharedPreferences("maintenance_prefs",MODE_PRIVATE).getLong("last_backup_ms",0);
        String lastText=last>0?new java.text.SimpleDateFormat("dd/MM HH:mm",new Locale("es","UY")).format(new Date(last)):"sin copia";
        TextView status=text("Base OK · "+cnt.optInt("trips")+" viajes · Última copia "+lastText,10.5f,MUTED,false);status.setPadding(dp(8),dp(7),dp(8),dp(5));menu.addView(status,new LinearLayout.LayoutParams(-1,-2));

        PopupWindow popup=new PopupWindow(menu,dp(224),-2,true);popup.setOutsideTouchable(true);popup.setElevation(dp(10));popup.setBackgroundDrawable(new TexturedDrawable(this,Color.rgb(8,55,63),Color.rgb(12,76,82),Color.argb(210,224,193,111),18f,1f,false));
        export.setOnClickListener(v->{popup.dismiss();startExportBase();});
        imp.setOnClickListener(v->{popup.dismiss();startImportBase();});
        popup.showAsDropDown(anchor,-dp(184),dp(5));
    }

    private void startExportBase(){
        if(maintenanceBusy()){new AlertDialog.Builder(this).setTitle("Copia de seguridad").setMessage("Para crear una copia consistente, finalizá primero el viaje y cerrá la jornada activa.").setPositiveButton("ENTENDIDO",null).show();return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,DatabaseBackup.suggestedName());startActivityForResult(i,REQ_EXPORT_DB);
    }

    private void startImportBase(){
        if(maintenanceBusy()){new AlertDialog.Builder(this).setTitle("Importación bloqueada").setMessage("No se puede restaurar una base mientras haya una jornada o un viaje activo. Cerralo primero.").setPositiveButton("ENTENDIDO",null).show();return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");startActivityForResult(i,REQ_IMPORT_DB);
    }

    private void performExport(Uri uri){
        Toast.makeText(this,"Creando copia…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{
            DatabaseBackup.BackupInfo info=DatabaseBackup.exportBackup(getApplicationContext(),uri);
            getSharedPreferences("maintenance_prefs",MODE_PRIVATE).edit().putLong("last_backup_ms",System.currentTimeMillis()).apply();
            runOnUiThread(()->Toast.makeText(this,"✓ Copia creada · "+info.trips+" viajes",Toast.LENGTH_LONG).show());
        }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo exportar").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show());}},"db-export").start();
    }

    private void inspectAndConfirmImport(Uri uri){
        Toast.makeText(this,"Validando backup…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{
            DatabaseBackup.BackupInfo info=DatabaseBackup.inspectBackup(getApplicationContext(),uri);
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Restaurar base").setMessage(info.summary()+"\n\nSe creará una copia preventiva de la base actual. Si la restauración falla, se hará rollback automático.").setNegativeButton("CANCELAR",null).setPositiveButton("RESTAURAR",(d,w)->performImport(uri)).show());
        }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Backup inválido").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show());}},"db-inspect").start();
    }

    private void performImport(Uri uri){
        if(maintenanceBusy())return;
        Toast.makeText(this,"Restaurando base…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{
            DatabaseBackup.BackupInfo info=DatabaseBackup.importBackup(getApplicationContext(),uri);
            Api.syncPendingAsync();
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("✓ Base restaurada").setMessage("Jornadas: "+info.shifts+"\nViajes: "+info.trips+"\nPuntos GPS: "+info.points+"\n\nEl Historial ya puede abrirse con los datos restaurados.").setPositiveButton("ABRIR HISTORIAL",(d,w)->startActivity(new Intent(this,HistoryActivity.class))).setNegativeButton("CERRAR",null).show());
        }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo restaurar").setMessage((e.getMessage()==null?e.toString():e.getMessage())+"\n\nLa base anterior se conservó.").setPositiveButton("OK",null).show());}},"db-import").start();
    }
    private void sendUiSignal(String action){if(!shiftActive)return;try{startService(new Intent(this,TrackingService.class).setAction(action));}catch(Exception ignored){}}
    private void requestServiceState(){if(!shiftActive)return;try{Intent svc=new Intent(this,TrackingService.class).setAction(TrackingService.ACTION_REQUEST_STATE);if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);}catch(Exception ignored){}}

    private Drawable headerGradient(){return premiumGradient(Color.rgb(4,48,57),Color.rgb(7,75,81),Color.argb(220,226,190,91),22f,1f);}
    private Drawable headerButtonGradient(int border,int tint){int end=Color.rgb(9,66,72);if(border==GREEN)end=Color.rgb(7,92,66);else if(border==GOLD)end=Color.rgb(64,61,34);return premiumGradient(Color.rgb(5,42,49),end,Color.argb(225,Color.red(border),Color.green(border),Color.blue(border)),17f,1f);}
    private Drawable statusPillGradient(int border){return premiumGradient(Color.rgb(7,55,62),Color.rgb(10,78,83),Color.argb(225,Color.red(border),Color.green(border),Color.blue(border)),19f,1f);}
    private Drawable panelGradient(){return premiumGradient(Color.argb(247,5,52,60),Color.argb(247,8,75,80),Color.argb(210,224,193,111),28f,1f);}
    private Drawable premiumGradient(int c1,int c2,int stroke,float radius,float strokeDp){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{c1,c2});g.setCornerRadius(radius*getResources().getDisplayMetrics().density);if(strokeDp>0)g.setStroke(Math.max(1,Math.round(strokeDp*getResources().getDisplayMetrics().density)),stroke);return g;}
    private int sheetHeightDp(int state){return state<=0?SHEET_PEEK_DP:(state==1?SHEET_MID_DP:SHEET_FULL_DP);}
    private int maxSheetState(){return shiftActive?2:1;}
    private int maxSheetHeightDp(){return maxSheetState()>=2?SHEET_FULL_DP:SHEET_MID_DP;}
    private void setSheetHeightPx(int heightPx){if(bottomSheet==null)return;FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)bottomSheet.getLayoutParams();lp.height=Math.max(dp(SHEET_PEEK_DP),Math.min(dp(maxSheetHeightDp()),heightPx));bottomSheet.setLayoutParams(lp);}
    private void applySheetVisibility(int state){if(bottomSheet==null)return;View head=bottomSheet.findViewWithTag("sheet_head"),meta=bottomSheet.findViewWithTag("sheet_meta"),detail=bottomSheet.findViewWithTag("sheet_detail"),close=bottomSheet.findViewWithTag("sheet_shift_close"),metrics=bottomSheet.findViewWithTag("sheet_metrics"),action=bottomSheet.findViewWithTag("sheet_action");boolean peek=state<=0;if(head!=null)head.setVisibility(peek?View.GONE:View.VISIBLE);if(meta!=null)meta.setVisibility(peek?View.GONE:View.VISIBLE);if(detail!=null)detail.setVisibility(state>=2?View.VISIBLE:View.GONE);if(metrics!=null)metrics.setVisibility(peek?View.GONE:View.VISIBLE);if(action!=null)action.setVisibility(peek?View.GONE:View.VISIBLE);if(close!=null)close.setVisibility(state>=2&&shiftActive&&!tripActive?View.VISIBLE:View.GONE);if(sheetChevron!=null)sheetChevron.setText(state>=2?"⌄":"⌃");}
    private void updateSheetContentVisibility(){applySheetVisibility(sheetState);}
    private void previewSheetVisibilityForHeight(int heightPx){int low=(dp(SHEET_PEEK_DP)+dp(SHEET_MID_DP))/2;if(maxSheetState()<2){applySheetVisibility(heightPx<low?0:1);return;}int high=(dp(SHEET_MID_DP)+dp(SHEET_FULL_DP))/2;applySheetVisibility(heightPx<low?0:(heightPx<high?1:2));}
    private void animateSheetHeight(int targetPx){if(bottomSheet==null)return;int start=bottomSheet.getLayoutParams().height;if(start==targetPx){setSheetHeightPx(targetPx);return;}android.animation.ValueAnimator a=android.animation.ValueAnimator.ofInt(start,targetPx);a.setDuration(220);a.setInterpolator(new android.view.animation.DecelerateInterpolator());a.addUpdateListener(x->setSheetHeightPx((Integer)x.getAnimatedValue()));a.start();}
    private void setSheetState(int state,boolean animate){if(bottomSheet==null)return;sheetState=Math.max(0,Math.min(maxSheetState(),state));applySheetVisibility(sheetState);int target=dp(sheetHeightDp(sheetState));if(animate)animateSheetHeight(target);else setSheetHeightPx(target);getSharedPreferences("ui_prefs",MODE_PRIVATE).edit().putInt("dashboard_sheet_state_r13",sheetState).apply();}
    private void cycleSheetState(){int max=maxSheetState();setSheetState(sheetState>=max?0:sheetState+1,true);if(sheetHandle!=null)sheetHandle.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}
    private boolean onSheetTouch(View v,MotionEvent e){if(bottomSheet==null)return false;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:sheetDragging=true;sheetDownY=e.getRawY();sheetStartHeight=bottomSheet.getLayoutParams().height;v.getParent().requestDisallowInterceptTouchEvent(true);return true;case MotionEvent.ACTION_MOVE:if(sheetDragging){int h=Math.round(sheetStartHeight+(sheetDownY-e.getRawY()));h=Math.max(dp(SHEET_PEEK_DP),Math.min(dp(maxSheetHeightDp()),h));setSheetHeightPx(h);previewSheetVisibilityForHeight(h);return true;}break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(sheetDragging){float delta=e.getRawY()-sheetDownY;sheetDragging=false;v.getParent().requestDisallowInterceptTouchEvent(false);if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(delta)<dp(8)){cycleSheetState();return true;}int h=bottomSheet.getLayoutParams().height;int c0=dp(SHEET_PEEK_DP),c1=dp(SHEET_MID_DP);int target;if(maxSheetState()<2)target=Math.abs(h-c1)<=Math.abs(h-c0)?1:0;else{int c2=dp(SHEET_FULL_DP);target=Math.abs(h-c2)<=Math.abs(h-c1)&&Math.abs(h-c2)<=Math.abs(h-c0)?2:(Math.abs(h-c1)<=Math.abs(h-c0)?1:0);}setSheetState(target,true);v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);return true;}break;}return false;}

    private TextView metric(LinearLayout row,String value,String label,String icon){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(2),dp(4),dp(2),dp(3));
        box.setBackground(premiumGradient(Color.rgb(6,55,62),Color.rgb(7,68,73),Color.argb(205,224,193,111),15f,0.8f));
        TextView ic=text(icon,12.5f,Color.rgb(236,196,92),true);ic.setGravity(Gravity.CENTER);
        TextView v=text(value,19.5f,TEXT,true);v.setGravity(Gravity.CENTER);
        TextView l=text(label,9.6f,Color.rgb(234,198,101),true);l.setGravity(Gravity.CENTER);
        box.addView(ic,new LinearLayout.LayoutParams(-1,0,.58f));box.addView(v,new LinearLayout.LayoutParams(-1,0,1.05f));box.addView(l,new LinearLayout.LayoutParams(-1,0,.55f));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);bp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(box,bp);return v;
    }
    private float followZoom(){if(currentSpeedKmh<12f)return 13.60f;if(currentSpeedKmh<45f)return 13.43f;if(currentSpeedKmh<80f)return 13.15f;return 12.87f;}
    private float smoothBearing(float current,float target,float factor){float delta=((target-current+540f)%360f)-180f;return normalizeBearing(current+delta*factor);}
    private float normalizeBearing(float b){float x=b%360f;return x<0?x+360f:x;}
    private void updateDriver(double lat,double lon,float bearing,boolean maybeFollow){
        lastLat=lat;lastLon=lon;if(currentSpeedKmh>=4.5f)lastBearing=normalizeBearing(bearing);if(style==null||map==null)return;
        float markerBearing=follow?0f:lastBearing;GeoJsonSource s=style.getSourceAs(DRIVER_SOURCE);if(s!=null)s.setGeoJson(pointGeoJson(lat,lon,markerBearing));
        if(maybeFollow&&follow&&System.currentTimeMillis()-lastCameraAt>1300){lastCameraAt=System.currentTimeMillis();if(currentSpeedKmh>=4.5f)cameraBearing=smoothBearing(cameraBearing,lastBearing,0.34f);CameraPosition cp=new CameraPosition.Builder().target(new LatLng(lat,lon)).zoom(followZoom()).tilt(16).bearing(cameraBearing).build();map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp),480);}
        if(compassView!=null)compassView.setMapBearing(follow?cameraBearing:0f);
    }
    private void northUpFreeMode(){if(map==null)return;cameraBearing=0f;if(compassView!=null)compassView.setMapBearing(0f);if(!Double.isNaN(lastLat)){GeoJsonSource s=style==null?null:style.getSourceAs(DRIVER_SOURCE);if(s!=null)s.setGeoJson(pointGeoJson(lastLat,lastLon,lastBearing));}CameraPosition old=map.getCameraPosition();CameraPosition cp=new CameraPosition.Builder().target(old.target).zoom(old.zoom).tilt(0).bearing(0).build();map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp),420);}
    private void recenter(){if(map==null)return;follow=true;followBtn.setText("SEGUIR");cameraBearing=currentSpeedKmh>=4.5f?lastBearing:cameraBearing;if(!Double.isNaN(lastLat)){GeoJsonSource s=style==null?null:style.getSourceAs(DRIVER_SOURCE);if(s!=null)s.setGeoJson(pointGeoJson(lastLat,lastLon,0));lastCameraAt=System.currentTimeMillis();CameraPosition cp=new CameraPosition.Builder().target(new LatLng(lastLat,lastLon)).zoom(followZoom()).tilt(16).bearing(cameraBearing).build();map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp),450);}if(compassView!=null)compassView.setMapBearing(cameraBearing);}
    private void addRoutePoint(double lat,double lon){if(!route.isEmpty()){double[] p=route.get(route.size()-1);float[] out=new float[1];Location.distanceBetween(p[0],p[1],lat,lon,out);if(out[0]<1.5f)return;}route.add(new double[]{lat,lon});if(route.size()>6000)route.remove(0);refreshRoute();}
    private void clearActiveRoute(){route.clear();loadedTripId="";routeEnded=false;lastRouteDbSyncAt=0;refreshRoute();}
    private int returnSplitIndex(List<double[]> pts){if(pts==null||pts.size()<8)return -1;double[] start=pts.get(0);float maxDist=0f,endDist=0f;int maxIndex=-1;float[] out=new float[1];for(int i=1;i<pts.size();i++){double[] p=pts.get(i);Location.distanceBetween(start[0],start[1],p[0],p[1],out);float d=out[0];if(d>maxDist){maxDist=d;maxIndex=i;}if(i==pts.size()-1)endDist=d;}if(maxDist<300f||maxIndex<3||maxIndex>pts.size()-4)return -1;if(endDist>maxDist*0.78f)return -1;double returnMeters=0;for(int i=maxIndex+1;i<pts.size();i++){double[] a=pts.get(i-1),b=pts.get(i);Location.distanceBetween(a[0],a[1],b[0],b[1],out);returnMeters+=out[0];}if(returnMeters<Math.max(180.0,maxDist*0.22))return -1;return maxIndex;}
    private String returnSegmentGeoJson(List<double[]> pts){int split=returnSplitIndex(pts);return split<0?emptyFeatureCollection():lineGeoJsonRange(pts,split,pts.size()-1);}
    private void refreshRoute(){
        if(style==null)return;
        GeoJsonSource s=style.getSourceAs(ROUTE_SOURCE);if(s!=null)s.setGeoJson(lineGeoJson(route));
        GeoJsonSource ret=style.getSourceAs(RETURN_SOURCE);if(ret!=null)ret.setGeoJson(returnSegmentGeoJson(route));
        GeoJsonSource ps=style.getSourceAs(ROUTE_POINTS_SOURCE);if(ps!=null)ps.setGeoJson(routePointsGeoJson(route));
        GeoJsonSource ss=style.getSourceAs(START_SOURCE);if(ss!=null)ss.setGeoJson(route.isEmpty()?emptyFeatureCollection():simplePointGeoJson(route.get(0)[0],route.get(0)[1]));
        GeoJsonSource es=style.getSourceAs(END_SOURCE);if(es!=null)es.setGeoJson(routeEnded&&!route.isEmpty()?simplePointGeoJson(route.get(route.size()-1)[0],route.get(route.size()-1)[1]):emptyFeatureCollection());
    }
    private ArrayList<double[]> routeFromJson(JSONArray a){
        ArrayList<double[]> r=new ArrayList<>();for(int x=0;x<a.length();x++){JSONObject o=a.optJSONObject(x);if(o!=null)r.add(new double[]{o.optDouble("lat"),o.optDouble("lon")});}return r;
    }
    private void loadRoute(String id){
        if(id==null||id.isEmpty())return;loadedTripId=id;
        new Thread(()->{TrackDb db=new TrackDb(this);JSONArray a=TelemetryQuality.cleanRoute(db.getTripPoints(id));db.close();ArrayList<double[]> r=routeFromJson(a);runOnUiThread(()->{if(tripActive&&!id.equals(tripId))return;route.clear();route.addAll(r);routeEnded=!tripActive;refreshRoute();if(!Double.isNaN(lastLat))updateDriver(lastLat,lastLon,lastBearing,false);});},"load-route").start();
    }
    private void reconcileRouteFromDb(String id){
        if(id==null||id.isEmpty())return;
        new Thread(()->{TrackDb db=new TrackDb(this);JSONArray a=TelemetryQuality.cleanRoute(db.getTripPoints(id));db.close();ArrayList<double[]> r=routeFromJson(a);if(r.size()<2)return;runOnUiThread(()->{if(!tripActive||!id.equals(tripId))return;if(route.size()<2||r.size()>=route.size()){route.clear();route.addAll(r);routeEnded=false;refreshRoute();}});},"route-reconcile").start();
    }
    private String emptyFeatureCollection(){return "{\"type\":\"FeatureCollection\",\"features\":[]}";}
    private String simplePointGeoJson(double lat,double lon){return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":["+lon+","+lat+"]}}]}";}
    private String pointGeoJson(double lat,double lon,float bearing){return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"bearing\":"+bearing+"},\"geometry\":{\"type\":\"Point\",\"coordinates\":["+lon+","+lat+"]}}]}";}
    private String lineGeoJson(List<double[]> pts){return lineGeoJsonRange(pts,0,pts==null?-1:pts.size()-1);}
    private String lineGeoJsonRange(List<double[]> pts,int from,int to){
        if(pts==null||pts.size()<2||from<0||to<=from||to>=pts.size())return emptyFeatureCollection();
        StringBuilder sb=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        boolean first=true;for(int i=from;i<=to;i++){if(!first)sb.append(',');first=false;double[] p=pts.get(i);sb.append('[').append(p[1]).append(',').append(p[0]).append(']');}
        return sb.append("]}}]}").toString();
    }
    private String routePointsGeoJson(List<double[]> pts){
        if(pts==null||pts.size()<2)return emptyFeatureCollection();
        StringBuilder sb=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");boolean first=true;int lastAdded=-1;
        for(int i=0;i<pts.size();i+=4){double[] p=pts.get(i);if(!first)sb.append(',');first=false;sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[").append(p[1]).append(',').append(p[0]).append("]}}");lastAdded=i;}
        int last=pts.size()-1;if(lastAdded!=last){double[] p=pts.get(last);if(!first)sb.append(',');sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[").append(p[1]).append(',').append(p[0]).append("]}}");}
        return sb.append("]}").toString();
    }
    private Bitmap driverArrow(){int n=104;Bitmap b=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Path outer=new Path();outer.moveTo(52,2);outer.lineTo(91,95);outer.lineTo(52,78);outer.lineTo(13,95);outer.close();Paint halo=new Paint(Paint.ANTI_ALIAS_FLAG);halo.setColor(Color.WHITE);halo.setShadowLayer(6,0,2,Color.argb(120,0,0,0));c.drawPath(outer,halo);halo.clearShadowLayer();Path edge=new Path();edge.moveTo(52,7);edge.lineTo(84,86);edge.lineTo(52,72);edge.lineTo(20,86);edge.close();Paint dark=new Paint(Paint.ANTI_ALIAS_FLAG);dark.setColor(BG);c.drawPath(edge,dark);Path fill=new Path();fill.moveTo(52,11);fill.lineTo(78,80);fill.lineTo(52,68);fill.lineTo(26,80);fill.close();Paint gold=new Paint(Paint.ANTI_ALIAS_FLAG);gold.setColor(GOLD);c.drawPath(fill,gold);Path core=new Path();core.moveTo(52,18);core.lineTo(70,71);core.lineTo(52,62);core.lineTo(34,71);core.close();Paint light=new Paint(Paint.ANTI_ALIAS_FLAG);light.setColor(Color.rgb(255,249,224));c.drawPath(core,light);Paint center=new Paint(Paint.ANTI_ALIAS_FLAG);center.setColor(ROUTE);c.drawCircle(52,63,4.8f,center);return b;}

    private void requestNeededPermissions(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);else startPreview();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ_LOCATION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startPreview();}
    private void startPreview(){if(shiftActive||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;if(previewLm==null)previewLm=(LocationManager)getSystemService(LOCATION_SERVICE);if(previewLm==null)return;try{boolean gps=previewLm.isProviderEnabled(LocationManager.GPS_PROVIDER);Location l=previewLm.getLastKnownLocation(gps?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER);if(l!=null&&Math.abs(System.currentTimeMillis()-l.getTime())<120000L)previewListener.onLocationChanged(l);if(gps)previewLm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500L,1f,previewListener,Looper.getMainLooper());else if(previewLm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))previewLm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000L,12f,previewListener,Looper.getMainLooper());}catch(Exception ignored){}}
    private void stopPreview(){if(previewLm!=null)try{previewLm.removeUpdates(previewListener);}catch(Exception ignored){}previewLastLocation=null;previewLastTs=0L;previewLastSpeed=0f;previewMoving=false;previewMovingEvidence=previewStoppedEvidence=0;}

    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(10.2f);b.setTextColor(TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setPadding(dp(5),0,dp(5),0);b.setBackground(rounded(PANEL,16,1,Color.rgb(50,91,100)));b.setElevation(dp(2));return b;}
    private GradientDrawable rounded(int fill,int radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String formatDuration(long ms){long total=Math.max(0,ms)/1000,h=total/3600,m=(total%3600)/60;if(h>0)return String.format(Locale.getDefault(),"%d:%02d",h,m);return String.format(Locale.getDefault(),"%02d:%02d",m,total%60);}

    @Override protected void onResume(){super.onResume();if(mapView!=null)mapView.onResume();if(Build.VERSION.SDK_INT>=33)registerReceiver(stateReceiver,new IntentFilter(TrackingService.ACTION_STATE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(stateReceiver,new IntentFilter(TrackingService.ACTION_STATE));SharedPreferences s=getSharedPreferences("tracking_state",MODE_PRIVATE);shiftActive=s.getBoolean("shift_active",false);tripActive=s.getBoolean("trip_active",false);stopActive=s.getBoolean("stop_active",false);tripType=s.getString("trip_type","other");tripStage=s.getString("trip_stage",tripActive?"onboard":"none");pickupAt=s.getLong("pickup_at",0);stopStarted=s.getLong("stop_started",0);if(shiftActive){sendUiSignal(TrackingService.ACTION_UI_VISIBLE);requestServiceState();uiHandler.postDelayed(this::requestServiceState,350);uiHandler.postDelayed(this::requestServiceState,1100);}else startPreview();if(historyOpen&&historySheet!=null)historySheet.reload();updateUi();}
    @Override protected void onPause(){if(shiftActive)sendUiSignal(TrackingService.ACTION_UI_HIDDEN);try{unregisterReceiver(stateReceiver);}catch(Exception ignored){}stopPreview();if(mapView!=null)mapView.onPause();super.onPause();}
    @Override protected void onStart(){super.onStart();if(mapView!=null)mapView.onStart();}
    @Override protected void onStop(){if(mapView!=null)mapView.onStop();super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();if(mapView!=null)mapView.onLowMemory();}
    @Override protected void onDestroy(){uiHandler.removeCallbacksAndMessages(null);if(mapView!=null)mapView.onDestroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(mapView!=null)mapView.onSaveInstanceState(out);}
    @Override public void onBackPressed(){super.onBackPressed();}

    public static final class SpeedGaugeView extends View {
        private float speed=0f;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();
        public SpeedGaugeView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        public void setSpeed(float v){speed=v<3f?0f:Math.max(0f,Math.min(199f,v));setContentDescription(String.format(Locale.getDefault(),"Velocidad %.0f kilómetros por hora",speed));invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();r.set(1,1,w-1,h-1);p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,w,h,Color.argb(249,7,56,64),Color.argb(249,13,83,88),Shader.TileMode.CLAMP));c.drawRoundRect(r,dpv(23),dpv(23),p);p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(10,255,255,255));c.drawRoundRect(new RectF(dpv(12),dpv(7),w-dpv(12),dpv(9)),dpv(2),dpv(2),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(1.5f));p.setColor(Color.argb(225,232,192,84));c.drawRoundRect(r,dpv(23),dpv(23),p);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(185,238,196,84));c.drawRoundRect(new RectF(dpv(18),dpv(9),w-dpv(18),dpv(11)),dpv(2),dpv(2),p);drawCentered(c,"VELOCIDAD",10f,Color.rgb(194,205,207),h*.24f,false);drawCentered(c,String.format(Locale.getDefault(),"%.0f",speed),37f,Color.WHITE,h*.60f,true);drawCentered(c,"km/h",11.5f,GOLD,h*.84f,true);}
        private void drawCentered(Canvas c,String s,float sp,int color,float cy,boolean bold){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(bold?Typeface.create(Typeface.DEFAULT,Typeface.BOLD):Typeface.DEFAULT);p.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);Paint.FontMetrics fm=p.getFontMetrics();c.drawText(s,getWidth()/2f,cy-(fm.ascent+fm.descent)/2f,p);}
        private float dpv(float v){return v*getResources().getDisplayMetrics().density;}
    }

    public static final class SlideActionView extends View {
        public static final int MODE_SHIFT_START=0,MODE_TRIP_START=1,MODE_TRIP_STOP=2,MODE_SHIFT_CLOSE=3,MODE_PICKUP=4,MODE_STOP_RESUME=5;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF track=new RectF();
        private Runnable completed,midpointCompleted;private String label="";private int mode=MODE_SHIFT_START;
        private boolean dragging=false,thresholdBuzzed=false,midBuzzed=false;private float progress=0f;

        public SlideActionView(Context c){super(c);setClickable(true);setFocusable(true);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("Control deslizable");}
        public void setLabel(String s){if(Objects.equals(label,s))return;label=s;setContentDescription(s);invalidate();}
        public void setMode(int m){if(mode==m)return;mode=m;progress=isReverse()?1f:0f;invalidate();}
        public void setOnCompleted(Runnable r){completed=r;}
        public void setOnMidpointCompleted(Runnable r){midpointCompleted=r;}
        private boolean isReverse(){return mode==MODE_SHIFT_CLOSE;}
        private boolean isDual(){return mode==MODE_TRIP_STOP;}
        private int dark(){if(mode==MODE_TRIP_START)return Color.rgb(7,75,52);if(mode==MODE_PICKUP)return Color.rgb(6,72,78);if(mode==MODE_STOP_RESUME)return Color.rgb(7,82,59);if(mode==MODE_TRIP_STOP)return Color.rgb(91,24,29);return Color.rgb(91,62,9);}
        private int bright(){if(mode==MODE_TRIP_START)return Color.rgb(31,174,111);if(mode==MODE_PICKUP)return Color.rgb(22,139,145);if(mode==MODE_STOP_RESUME)return Color.rgb(42,179,116);if(mode==MODE_TRIP_STOP)return Color.rgb(221,49,59);return Color.rgb(224,178,58);}
        private int accent(){if(mode==MODE_TRIP_START||mode==MODE_STOP_RESUME)return Color.rgb(37,190,122);if(mode==MODE_PICKUP)return Color.rgb(70,190,194);if(mode==MODE_TRIP_STOP)return Color.rgb(229,69,75);return Color.rgb(235,193,79);}

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight(),pad=dpv(4);track.set(pad,pad,w-pad,h-pad);

            p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,w,0,dark(),bright(),Shader.TileMode.CLAMP));c.drawRoundRect(track,h/2,h/2,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(1));p.setColor(Color.argb(205,255,255,255));c.drawRoundRect(track,h/2,h/2,p);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(34,255,255,255));c.drawRoundRect(new RectF(track.left+dpv(22),track.top+dpv(6),track.right-dpv(22),track.top+dpv(8)),dpv(2),dpv(2),p);

            float radius=(h-pad*2)/2-dpv(4),minX=pad+dpv(4)+radius,maxX=w-pad-dpv(4)-radius;

            if(isDual()){
                float pillCx=minX+(maxX-minX)*0.52f,pillW=dpv(92),pillH=dpv(28),pillTop=h/2f-pillH/2f;
                RectF pill=new RectF(pillCx-pillW/2f,pillTop,pillCx+pillW/2f,pillTop+pillH);

                // Fine guide lines: visual landmark, not a second button track.
                p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(1.1f));p.setColor(Color.argb(210,240,184,59));
                float lineY=h/2f;
                c.drawLine(track.left+dpv(84),lineY,pill.left-dpv(10),lineY,p);
                c.drawLine(pill.right+dpv(10),lineY,track.right-dpv(130),lineY,p);

                // Orange horizontal "gragea" approved by user.
                p.setStyle(Paint.Style.FILL);p.setShadowLayer(dpv(6),0,dpv(2),Color.argb(105,74,28,0));
                p.setShader(new LinearGradient(pill.left,0,pill.right,0,Color.rgb(255,170,24),Color.rgb(246,190,59),Shader.TileMode.CLAMP));
                c.drawRoundRect(pill,pillH/2f,pillH/2f,p);p.clearShadowLayer();p.setShader(null);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(1));p.setColor(Color.argb(210,255,220,116));c.drawRoundRect(pill,pillH/2f,pillH/2f,p);
                p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
                p.setTextSize(9.6f*getResources().getDisplayMetrics().scaledDensity);p.setColor(Color.rgb(54,45,21));
                Paint.FontMetrics pf=p.getFontMetrics();c.drawText("1/2 PARADA",pillCx,h/2f-(pf.ascent+pf.descent)/2f,p);

                p.setTextSize(11.4f*getResources().getDisplayMetrics().scaledDensity);p.setColor(Color.WHITE);
                Paint.FontMetrics ff=p.getFontMetrics();c.drawText("FINALIZAR  →",track.right-dpv(73),h/2f-(ff.ascent+ff.descent)/2f,p);
            }

            float cx=minX+(maxX-minX)*progress,cy=h/2f;
            p.setStyle(Paint.Style.FILL);p.setShadowLayer(dpv(6),0,dpv(2),Color.argb(115,0,0,0));p.setColor(Color.rgb(250,249,244));c.drawCircle(cx,cy,radius,p);p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpv(2));p.setColor(accent());c.drawCircle(cx,cy,radius-dpv(2),p);
            p.setStyle(Paint.Style.FILL);p.setColor(dark());Path arrow=new Path();float a=dpv(6);
            if(isReverse()){arrow.moveTo(cx+a,cy-a);arrow.lineTo(cx-a,cy);arrow.lineTo(cx+a,cy+a);}else{arrow.moveTo(cx-a,cy-a);arrow.lineTo(cx+a,cy);arrow.lineTo(cx-a,cy+a);}arrow.close();c.drawPath(arrow,p);

            if(!isDual()){
                p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
                p.setTextSize((getHeight()<dpv(55)?9.4f:11.5f)*getResources().getDisplayMetrics().scaledDensity);p.setColor(Color.WHITE);
                Paint.FontMetrics fm=p.getFontMetrics();float offset=isReverse()?-dpv(8):dpv(10),textX=w/2f+offset,textY=h/2f-(fm.ascent+fm.descent)/2f;c.drawText(label,textX,textY,p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            float h=getHeight(),pad=dpv(4),radius=(h-pad*2)/2-dpv(4),minX=pad+dpv(4)+radius,maxX=getWidth()-pad-dpv(4)-radius;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    dragging=true;thresholdBuzzed=false;midBuzzed=false;getParent().requestDisallowInterceptTouchEvent(true);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);updateProgress(e.getX(),minX,maxX);return true;
                case MotionEvent.ACTION_MOVE:
                    if(dragging){
                        updateProgress(e.getX(),minX,maxX);
                        if(isDual()&&progress>=.47f&&!midBuzzed){midBuzzed=true;vibrateDualHalfStop();}
                        if(progress<.40f)midBuzzed=false;
                        boolean armed=isReverse()?progress<=.18f:progress>=.86f;
                        if(armed&&!thresholdBuzzed){thresholdBuzzed=true;if(isDual())vibrateDualFinish();else performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);}
                        else if(!armed&&progress<.80f)thresholdBuzzed=false;
                        return true;
                    }break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(dragging){
                        boolean up=e.getActionMasked()==MotionEvent.ACTION_UP;
                        boolean full=up&&(isReverse()?progress<=.16f:progress>=.88f);
                        boolean mid=up&&isDual()&&!full&&progress>=.46f&&progress<.80f;
                        dragging=false;getParent().requestDisallowInterceptTouchEvent(false);progress=isReverse()?1f:0f;invalidate();
                        if(full){performClick();performHapticFeedback(HapticFeedbackConstants.CONFIRM);if(completed!=null)completed.run();}
                        else if(mid){performClick();performHapticFeedback(HapticFeedbackConstants.CONFIRM);if(midpointCompleted!=null)midpointCompleted.run();}
                        return true;
                    }break;
            }
            return super.onTouchEvent(e);
        }

        private void vibrateDualHalfStop(){
            vibrateWave(new long[]{0,55,38,65},new int[]{0,165,0,215});
        }
        private void vibrateDualFinish(){
            vibrateWave(new long[]{0,90,48,140},new int[]{0,215,0,245});
        }
        private void vibrateWave(long[] timing,int[] amplitude){
            try{
                Vibrator v=(Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);if(v==null||!v.hasVibrator())return;
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)v.vibrate(VibrationEffect.createWaveform(timing,amplitude,-1));
                else v.vibrate(timing,-1);
            }catch(Exception ignored){}
        }
        private void updateProgress(float x,float min,float max){progress=Math.max(0f,Math.min(1f,(x-min)/Math.max(1f,max-min)));invalidate();}
        @Override public boolean performClick(){super.performClick();return true;}
        private float dpv(float v){return v*getResources().getDisplayMetrics().density;}
    }}
