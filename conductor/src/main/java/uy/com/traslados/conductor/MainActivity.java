package uy.com.traslados.conductor;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.CalendarContract;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    // v10.5: estado del globito del conductor.
    private boolean driverBubbleOpenRequested=false;
    private String driverBubbleReservationId="";
    // v10.7: cola visual de nuevas solicitudes; una sola caja, navegable y con acciones siempre visibles.
    private final ArrayList<JSONObject> driverAlertQueue=new ArrayList<>();
    private final HashSet<String> driverAlertQueuedIds=new HashSet<>();
    private final HashSet<String> driverStatusInFlight=new HashSet<>();
    private Dialog driverAlertDialog;
    private int driverAlertIndex=0;
    // v10.9: telemetría GPS real y exportación de la traza.
    private static final int REQ_EXPORT_GPX=7719;
    private String pendingGpx="";
    // v11.4 R5: recordatorio visual del próximo viaje confirmado.
    private String lastPulsedTripReminder="";

    private static final int GOLD=Color.rgb(224,193,111);
    private static final int GOLD_DARK=Color.rgb(184,145,55);
    private static final int BG=Color.rgb(5,20,25);
    private static final int PANEL=Color.rgb(6,43,52);
    private static final int PANEL_2=Color.rgb(7,52,63);
    private static final int LINE=Color.rgb(71,126,140);
    private static final int TEXT=Color.rgb(244,241,232);
    private static final int MUTED=Color.rgb(177,188,190);
    private static final int GREEN=Color.rgb(41,184,124);
    private static final int RED=Color.rgb(225,91,91);
    private static final int CYAN=Color.rgb(91,194,218);

    private final ExecutorService pool=Executors.newCachedThreadPool();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> currentRows=new ArrayList<>();
    private final Set<String> seenPending=new HashSet<>();
    // R10.2: acordeón estable; refinamiento visual de chevrons y badges.
    private final Set<String> collapsedHistoryDays=new HashSet<>();
    private final Set<String> collapsedActiveDays=new HashSet<>();
    private boolean historyDayGroupsInitialized=false;
    private boolean activeDayGroupsInitialized=false;
    private long lastSuccessfulSyncAt=0L;
    private boolean activeSyncInFlight=false;
    private String lastActiveSnapshot="";

    private String pin="";
    private String expandedId="";
    private boolean firstLoad=true;
    private boolean historyMode=false;
    private boolean dashboardVisible=false;

    private LinearLayout listBox,statsBox,agendaBox;
    private TextView screenTitle,screenSub,agendaLabel,availabilityStatus,gpsPreflightStatus;
    private TextView gpsStateChip,onlineStateChip,monitorStateChip;
    private Button refreshBtn,historyBtn,logoutBtn;
    private Button agendaAllBtn,agendaTodayBtn,agendaWeekBtn,agendaMonthBtn,agendaDateBtn;
    private String agendaMode="TODAS";
    private String agendaStatusMode="TODAS";
    private Calendar agendaDate=Calendar.getInstance();
    private boolean agendaCustomDate=false;

    private final Runnable periodic=new Runnable(){
        @Override public void run(){
            if(dashboardVisible&&!historyMode&&!pin.isEmpty())loadActive(false);
            handler.postDelayed(this,10000);
        }
    };

    @Override public void onCreate(Bundle b){
        captureDriverBubbleIntent(getIntent());

        super.onCreate(b);
        Api.init(getApplicationContext());
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},55);
        }
        pool.execute(()->Api.logEvent("app_start","Conductor v11.4 R10.3 iniciado","{}"));
        checkSetup();
    }

    @Override protected void onResume(){
        android.content.SharedPreferences dbp=getSharedPreferences("driver_bubble",MODE_PRIVATE);
        if(dbp.getBoolean("bubble_requested",false)&&android.provider.Settings.canDrawOverlays(this)){
  dbp.edit().putBoolean("bubble_requested",false).putBoolean("bubble_enabled",true).apply();
  toast("Globito del conductor activado");
        }
        dbp.edit().putBoolean("app_foreground",true).putInt("unread",0).apply();
        sendDriverBubbleAction("uy.com.traslados.conductor.HIDE_BUBBLE");

        super.onResume();
        handler.removeCallbacks(periodic);
        handler.postDelayed(periodic,10000);
        if(dashboardVisible)handler.postDelayed(this::actualizarGpsPreflightSilencioso,180);
    }

    @Override protected void onPause(){
        getSharedPreferences("driver_bubble",MODE_PRIVATE).edit().putBoolean("app_foreground",false).apply();
        if(pin!=null&&!pin.isEmpty())sendDriverBubbleAction("uy.com.traslados.conductor.SHOW_BUBBLE");

        super.onPause();
        handler.removeCallbacks(periodic);
    }

    private void checkSetup(){
        renderLoading("Preparando acceso…");
        pool.execute(()->{
            try{
                JSONObject s=Api.setupState();
                boolean configured=s.optBoolean("configured",false);
                String saved=getSharedPreferences("driver_session",MODE_PRIVATE).getString("pin","");
                if(configured&&!saved.isEmpty()){
                    try{
                        if(Api.pinValid(saved)){
                            pin=saved;
                            runOnUiThread(this::enterDashboard);
                            return;
                        }
                    }catch(Exception ignored){}
                }
                runOnUiThread(()->renderAccess(configured));
            }catch(Exception e){
                runOnUiThread(()->renderConnectionError(e));
            }
        });
    }

    private void renderLoading(String message){
        dashboardVisible=false;
        ScrollView sc=baseScreen(); LinearLayout box=contentOf(sc); addBrand(box);
        LinearLayout card=panelCard(); card.setGravity(Gravity.CENTER_HORIZONTAL);
        ProgressBar pb=new ProgressBar(this); card.addView(pb,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView t=body(message,16,MUTED);t.setGravity(Gravity.CENTER);card.addView(t,lpMatch(-2,12,4));
        box.addView(card,lpMatch(-2,18,18)); setContentView(sc);
    }

    private void renderConnectionError(Exception e){
        dashboardVisible=false;
        ScrollView sc=baseScreen(); LinearLayout box=contentOf(sc); addBrand(box);
        addIntro(box,"No pudimos conectar","El servidor respondió con un error. Tocá REINTENTAR.");
        LinearLayout card=panelCard(); card.addView(body(friendly(e),14,MUTED));
        Button retry=primaryButton("REINTENTAR");retry.setOnClickListener(v->checkSetup());card.addView(retry,lpMatch(dp(62),16,2));
        box.addView(card,lpMatch(-2,10,20));setContentView(sc);
    }

    private void renderAccess(boolean configured){
        dashboardVisible=false;
        ScrollView sc=baseScreen(); LinearLayout box=contentOf(sc); addBrand(box);
        if(!configured){
            addIntro(box,"Configuración inicial","Definí el nombre y un PIN privado. Este paso se realiza una sola vez.");
            LinearLayout card=panelCard();
            EditText name=addField(card,"person","NOMBRE DEL CONDUCTOR","Ej: Marcelo");
            EditText p=addField(card,"ticket","PIN PRIVADO","4 a 10 dígitos");
            p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            Button config=primaryButton("CONFIGURAR CONDUCTOR");
            config.setOnClickListener(v->configure(name,p,config));card.addView(config,lpMatch(dp(66),12,4));
            box.addView(card,lpMatch(-2,10,14));
            addNote(box,"El PIN queda asociado al conductor. Guardalo en un lugar seguro.");
        }else{
            addIntro(box,"Acceso conductor","Ingresá tu PIN para ver y gestionar las reservas.");
            LinearLayout card=panelCard();
            EditText p=addField(card,"ticket","PIN PRIVADO","••••••");
            p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            Button access=primaryButton("INGRESAR");access.setOnClickListener(v->login(p,access));card.addView(access,lpMatch(dp(66),12,4));
            box.addView(card,lpMatch(-2,10,14));
            addNote(box,"Cuando entre una solicitud, aparecerá automáticamente acá y recibirás un aviso sonoro.");
        }
        setContentView(sc);
    }

    private void configure(EditText name,EditText p,Button btn){
        String n=name.getText().toString().trim(),k=p.getText().toString().trim();
        if(n.length()<2||!k.matches("[0-9]{4,10}")){toast("Ingresá el nombre y un PIN de 4 a 10 dígitos");return;}
        btn.setEnabled(false);btn.setText("CONFIGURANDO…");
        pool.execute(()->{
            try{
                Api.claimPin(k,n); pin=k; savePin();
                Api.logEvent("driver_setup","Conductor configurado correctamente",null);
                runOnUiThread(()->{toast("Conductor configurado correctamente");enterDashboard();});
            }catch(Exception e){runOnUiThread(()->{toast("No se pudo configurar: "+friendly(e));btn.setEnabled(true);btn.setText("CONFIGURAR CONDUCTOR");});}
        });
    }

    private void login(EditText p,Button btn){
        String k=p.getText().toString().trim();
        if(!k.matches("[0-9]{4,10}")){toast("El PIN debe tener entre 4 y 10 dígitos");return;}
        btn.setEnabled(false);btn.setText("INGRESANDO…");
        pool.execute(()->{
            try{
                if(!Api.pinValid(k))throw new Exception("PIN incorrecto");
                pin=k;savePin();Api.logEvent("driver_login","Acceso conductor correcto",null);
                runOnUiThread(this::enterDashboard);
            }catch(Exception e){runOnUiThread(()->{toast("No se pudo ingresar: "+friendly(e));btn.setEnabled(true);btn.setText("INGRESAR");});}
        });
    }

    private void savePin(){getSharedPreferences("driver_session",MODE_PRIVATE).edit().putString("pin",pin).apply();}

    private void enterDashboard(){
        maybeAskDriverBubblePermission();
        if(driverBubbleOpenRequested){
  historyMode=false;
  if(!driverBubbleReservationId.isEmpty())expandedId=driverBubbleReservationId;
  driverBubbleOpenRequested=false;
        }

        dashboardVisible=true; historyMode=false; expandedId=""; firstLoad=true; seenPending.clear(); currentRows.clear();
        startMonitor(); buildDashboard(false); loadActive(true);
    }

    private void startMonitor(){
        Intent s=new Intent(this,ReservationMonitorService.class);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);
    }

    private void buildDashboard(boolean history){
        dashboardVisible=true;historyMode=history;
        ScrollView sc=baseScreen();LinearLayout box=contentOf(sc);addBrand(box);
        screenTitle=heading(history?"HISTORIAL":"SOLICITUDES",24);box.addView(screenTitle,lpMatch(-2,3,0));
        screenSub=body(history?"Viajes finalizados, cancelados y rechazados":"Monitor activo · esperando solicitudes",12,GOLD);box.addView(screenSub,lpMatch(-2,0,7));

        statsBox=new LinearLayout(this);statsBox.setOrientation(LinearLayout.HORIZONTAL);box.addView(statsBox,lpMatch(history?dp(50):dp(66),0,8));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        refreshBtn=secondaryButton("ACTUALIZAR");historyBtn=secondaryButton(history?"SOLICITUDES":"HISTORIAL");logoutBtn=secondaryButton("SALIR");
        applyActionIcon(refreshBtn,R.drawable.ic_refresh);applyActionIcon(historyBtn,R.drawable.ic_history);applyActionIcon(logoutBtn,R.drawable.ic_logout);
        actions.addView(refreshBtn,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(actions,6);
        actions.addView(historyBtn,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(actions,6);
        actions.addView(logoutBtn,new LinearLayout.LayoutParams(0,dp(50),1));
        box.addView(actions,lpMatch(dp(50),0,7));
        if(!history){
            LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);
            Button alertPrefs=secondaryButton("AVISOS"),pricePrefs=secondaryButton("PRESETS"),gpsPrefs=secondaryButton("GPS");
            applyActionIcon(alertPrefs,R.drawable.ic_bell);applyActionIcon(pricePrefs,R.drawable.ic_tune);applyActionIcon(gpsPrefs,R.drawable.ic_gps);
            for(Button b:new Button[]{alertPrefs,pricePrefs,gpsPrefs}){b.setTextSize(10);b.setPadding(dp(4),0,dp(4),0);}
            tools.addView(alertPrefs,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(tools,6);tools.addView(pricePrefs,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(tools,6);tools.addView(gpsPrefs,new LinearLayout.LayoutParams(0,dp(46),1));
            alertPrefs.setOnClickListener(v->showDriverAlertSettings());pricePrefs.setOnClickListener(v->showPricingPresetDialog());gpsPrefs.setOnClickListener(v->showTelemetrySettings());
            box.addView(tools,lpMatch(dp(46),0,7));
            LinearLayout tools2=new LinearLayout(this);tools2.setGravity(Gravity.CENTER_VERTICAL);
            Button diagBtn=secondaryButton("DIAGNÓSTICO"),testBtn=secondaryButton("MODO PRUEBA");diagBtn.setTextSize(10);testBtn.setTextSize(10);
            applyActionIcon(diagBtn,R.drawable.ic_diagnostics);applyActionIcon(testBtn,R.drawable.ic_lab);
            tools2.addView(diagBtn,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(tools2,6);tools2.addView(testBtn,new LinearLayout.LayoutParams(0,dp(46),1));
            diagBtn.setOnClickListener(v->showDiagnostics());testBtn.setOnClickListener(v->showTestMode());box.addView(tools2,lpMatch(dp(46),0,7));
            gpsPreflightStatus=body("● Comprobando GPS…",12,MUTED);gpsPreflightStatus.setPadding(dp(12),dp(10),dp(12),dp(10));gpsPreflightStatus.setBackground(rounded(PANEL_2,LINE,14));gpsPreflightStatus.setClickable(true);gpsPreflightStatus.setOnClickListener(v->corregirGpsPreflight());box.addView(gpsPreflightStatus,lpMatch(-2,0,8));handler.postDelayed(this::actualizarGpsPreflightSilencioso,120);
        }
        if(!history){
            agendaBox=new LinearLayout(this);agendaBox.setOrientation(LinearLayout.VERTICAL);

            LinearLayout agendaHead=new LinearLayout(this);agendaHead.setOrientation(LinearLayout.HORIZONTAL);agendaHead.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout agendaText=new LinearLayout(this);agendaText.setOrientation(LinearLayout.VERTICAL);
            TextView agendaTitle=body("AGENDA",18,TEXT);agendaTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            agendaLabel=body(agendaDescription(),12,GOLD);
            agendaText.addView(agendaTitle,lpMatch(-2,0,1));agendaText.addView(agendaLabel,lpMatch(-2,0,0));
            agendaHead.addView(agendaText,new LinearLayout.LayoutParams(0,-2,1));
            agendaDateBtn=secondaryButton("FECHA");agendaDateBtn.setTextSize(10);agendaDateBtn.setPadding(dp(6),0,dp(6),0);applyActionIcon(agendaDateBtn,R.drawable.ic_calendar);
            LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(dp(106),dp(42));dateLp.setMargins(dp(8),0,0,0);agendaHead.addView(agendaDateBtn,dateLp);
            agendaBox.addView(agendaHead,lpMatch(-2,10,8));

            LinearLayout filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);
            agendaAllBtn=secondaryButton("TODAS");agendaTodayBtn=secondaryButton("HOY");agendaWeekBtn=secondaryButton("SEMANA");agendaMonthBtn=secondaryButton("MES");
            for(Button b:new Button[]{agendaAllBtn,agendaTodayBtn,agendaWeekBtn,agendaMonthBtn}){b.setTextSize(10);b.setPadding(dp(3),0,dp(3),0);}
            filters.addView(agendaAllBtn,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(filters,4);
            filters.addView(agendaTodayBtn,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(filters,4);
            filters.addView(agendaWeekBtn,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(filters,4);
            filters.addView(agendaMonthBtn,new LinearLayout.LayoutParams(0,dp(44),1));
            agendaBox.addView(filters,lpMatch(dp(44),0,8));

            agendaAllBtn.setOnClickListener(v->{agendaMode="TODAS";agendaCustomDate=false;resetActiveDayAccordion();renderRows(false);});
            agendaTodayBtn.setOnClickListener(v->{agendaMode="DIA";agendaDate=Calendar.getInstance();agendaCustomDate=false;resetActiveDayAccordion();renderRows(false);});
            agendaWeekBtn.setOnClickListener(v->{agendaMode="SEMANA";agendaDate=Calendar.getInstance();agendaCustomDate=false;resetActiveDayAccordion();renderRows(false);});
            agendaMonthBtn.setOnClickListener(v->{agendaMode="MES";agendaDate=Calendar.getInstance();agendaCustomDate=false;resetActiveDayAccordion();renderRows(false);});
            agendaDateBtn.setOnClickListener(v->pickAgendaDate());
            LinearLayout availabilityActions=new LinearLayout(this);availabilityActions.setOrientation(LinearLayout.HORIZONTAL);
            Button availabilityBtn=secondaryButton("HORARIOS"),blockBtn=secondaryButton("BLOQUEAR"),blocksBtn=secondaryButton("BLOQUEOS");
            for(Button b:new Button[]{availabilityBtn,blockBtn,blocksBtn}){b.setTextSize(10);b.setPadding(dp(3),0,dp(3),0);}
            availabilityActions.addView(availabilityBtn,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(availabilityActions,5);availabilityActions.addView(blockBtn,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(availabilityActions,5);availabilityActions.addView(blocksBtn,new LinearLayout.LayoutParams(0,dp(46),1));agendaBox.addView(availabilityActions,lpMatch(dp(48),0,6));
            availabilityStatus=body("Consultando disponibilidad pública…",12,MUTED);availabilityStatus.setPadding(dp(10),dp(8),dp(10),dp(8));availabilityStatus.setBackground(rounded(PANEL_2,LINE,13));availabilityStatus.setClickable(true);agendaBox.addView(availabilityStatus,lpMatch(-2,0,9));
            availabilityBtn.setOnClickListener(v->showAvailabilitySettings());availabilityStatus.setOnClickListener(v->showAvailabilitySettings());blockBtn.setOnClickListener(v->showAddScheduleBlock());blocksBtn.setOnClickListener(v->showScheduleBlocks());
            refreshAgendaControls();loadAvailabilitySummary();
            box.addView(agendaBox,lpMatch(-2,0,12));
        }
        refreshBtn.setOnClickListener(v->{if(historyMode)loadHistory();else loadActive(true);});
        historyBtn.setOnClickListener(v->{expandedId="";currentRows.clear();historyDayGroupsInitialized=false;activeDayGroupsInitialized=false;collapsedHistoryDays.clear();collapsedActiveDays.clear();buildDashboard(!historyMode);if(historyMode)loadHistory();else loadActive(true);});
        logoutBtn.setOnClickListener(v->confirmLogout());

        listBox=new LinearLayout(this);listBox.setOrientation(LinearLayout.VERTICAL);box.addView(listBox,lpMatch(-2,0,20));
        listBox.addView(loadingCard(history?"Cargando historial…":"Actualizando reservas…"));
        setContentView(sc);
    }

    private void loadActive(boolean showLoading){
        if(pin.isEmpty()||activeSyncInFlight)return;
        activeSyncInFlight=true;
        if(showLoading&&screenSub!=null)screenSub.setText("Actualizando reservas…");
        pool.execute(()->{
            try{
                JSONArray a=Api.listActive(pin);ArrayList<JSONObject> rows=new ArrayList<>();Set<String> current=new HashSet<>();
                ArrayList<JSONObject> fresh=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject r=a.getJSONObject(i);rows.add(r);
                    if("PENDIENTE".equals(r.optString("status"))){
                        String id=r.optString("id");current.add(id);
                        if(!firstLoad&&!seenPending.contains(id))fresh.add(r);
                    }
                }
                String snapshot=rows.toString();
                seenPending.clear();seenPending.addAll(current);firstLoad=false;
                runOnUiThread(()->{
                    activeSyncInFlight=false;
                    if(historyMode)return;
                    boolean changed=!snapshot.equals(lastActiveSnapshot);lastActiveSnapshot=snapshot;
                    currentRows.clear();currentRows.addAll(rows);lastSuccessfulSyncAt=System.currentTimeMillis();ensureTelemetryForActiveTrip(rows);
                    if(changed||showLoading||listBox==null||listBox.getChildCount()==0)renderRows(false);else refreshPassiveDashboard();
                    for(JSONObject r:fresh)alertNew(r);
                });
            }catch(Exception e){runOnUiThread(()->{activeSyncInFlight=false;if(!historyMode)showLoadError(e,false);});}
        });
    }

    private void refreshPassiveDashboard(){
        if(screenSub!=null)screenSub.setText((isNetworkAvailable()?"Monitor activo":"Monitor sin conexión")+" · "+agendaFilteredRows().size()+" visible(s) de "+currentRows.size()+syncSuffix());
        renderStats(false);refreshAgendaControls();updateStatusStrip();
    }

    private void loadHistory(){
        if(pin.isEmpty())return;
        if(screenSub!=null)screenSub.setText("Cargando historial…");
        pool.execute(()->{
            try{
                JSONArray a=Api.listHistory(pin);ArrayList<JSONObject> rows=new ArrayList<>();
                for(int i=0;i<a.length();i++)rows.add(a.getJSONObject(i));
                runOnUiThread(()->{if(!historyMode)return;currentRows.clear();currentRows.addAll(rows);lastSuccessfulSyncAt=System.currentTimeMillis();renderRows(true);});
            }catch(Exception e){runOnUiThread(()->{if(historyMode)showLoadError(e,true);});}
        });
    }

    private void showLoadError(Exception e,boolean history){
        if(listBox==null)return;
        if(!currentRows.isEmpty()){
            renderRows(history);
            if(screenSub!=null)screenSub.setText((history?"Historial":"Reservas")+" · no pude actualizar · mostrando última información"+syncSuffix());
            LinearLayout warn=panelCard();warn.setBackground(rounded(Color.rgb(48,40,18),GOLD_DARK,16));
            warn.addView(body("⚠ No pude sincronizar ahora · la agenda visible es la última recibida.",12,GOLD));
            Button retry=secondaryButton("REINTENTAR");retry.setOnClickListener(v->{if(historyMode)loadHistory();else loadActive(true);});warn.addView(retry,lpMatch(dp(48),8,0));
            listBox.addView(warn,0,lpMatch(-2,2,8));
            return;
        }
        if(screenSub!=null)screenSub.setText(history?"Error al cargar historial":"Error al consultar reservas");
        listBox.removeAllViews();LinearLayout c=panelCard();c.addView(body("No se pudieron cargar las reservas.\n"+friendly(e),14,MUTED));Button retry=primaryButton("REINTENTAR");retry.setOnClickListener(v->{if(historyMode)loadHistory();else loadActive(true);});c.addView(retry,lpMatch(dp(56),14,2));listBox.addView(c,lpMatch(-2,8,8));
    }

    private void renderRows(boolean hist){
        ArrayList<JSONObject> shown=hist?new ArrayList<>(currentRows):agendaFilteredRows();
        if(!hist){refreshAgendaControls();updateStatusStrip();}
        if(screenSub!=null)screenSub.setText(hist?"Historial · "+shown.size()+" viaje(s)"+syncSuffix():(isNetworkAvailable()?"Monitor activo":"Monitor sin conexión")+" · "+shown.size()+" visible(s) de "+currentRows.size()+syncSuffix());
        renderStats(hist);
        listBox.removeAllViews();
        if(shown.isEmpty()){
            listBox.addView(emptyCard(hist?"Todavía no hay viajes finalizados · cuando cierres un viaje aparecerá acá":"Agenda libre para este período · no tenés traslados que requieran atención"),lpMatch(-2,8,8));return;
        }
        Collections.sort(shown,(a,b)->{String ad=a.optString("pickup_date","")+" "+a.optString("pickup_time","");String bd=b.optString("pickup_date","")+" "+b.optString("pickup_time","");return hist?bd.compareTo(ad):ad.compareTo(bd);});
        if(!hist){View next=nextTripBanner(shown);if(next!=null)listBox.addView(next,lpMatch(-2,2,8));}

        LinkedHashMap<String,ArrayList<JSONObject>> groups=new LinkedHashMap<>();
        for(JSONObject r:shown){String day=r.optString("pickup_date","");ArrayList<JSONObject> g=groups.get(day);if(g==null){g=new ArrayList<>();groups.put(day,g);}g.add(r);}
        Set<String> collapsed=hist?collapsedHistoryDays:collapsedActiveDays;
        boolean initialized=hist?historyDayGroupsInitialized:activeDayGroupsInitialized;
        if(!initialized){
            collapsed.clear();boolean first=true;
            for(String day:groups.keySet()){if(!first)collapsed.add(day);first=false;}
            if(hist)historyDayGroupsInitialized=true;else activeDayGroupsInitialized=true;
        }
        if(!expandedId.isEmpty())for(JSONObject r:shown)if(expandedId.equals(r.optString("id",""))){String openDay=r.optString("pickup_date","");collapseAllOtherDays(collapsed,openDay);break;}

        for(Map.Entry<String,ArrayList<JSONObject>> entry:groups.entrySet()){
            String day=entry.getKey();ArrayList<JSONObject> rows=entry.getValue();boolean closed=collapsed.contains(day);
            listBox.addView(dayGroupHeader(day,rows.size(),closed,hist),lpMatch(-2,7,5));
            if(!closed)for(JSONObject r:rows)listBox.addView(reservationCard(r,hist),lpMatch(-2,4,6));
        }
    }

    private View dayGroupHeader(String day,int count,boolean closed,boolean hist){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(13),dp(5),dp(6),dp(5));row.setBackground(rounded(PANEL_2,GOLD_DARK,16));row.setMinimumHeight(dp(56));
        TextView title=body(dayHeader(day),15,GOLD);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView chip=body(count+" "+(count==1?"viaje":"viajes"),11,TEXT);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(9),0,dp(9),0);chip.setBackground(rounded(Color.argb(35,224,193,111),GOLD_DARK,14));row.addView(chip,new LinearLayout.LayoutParams(-2,dp(32)));
        View arrow=chevronView(!closed,true);arrow.setContentDescription(closed?"Desplegar día":"Plegar día");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(46),dp(46));ap.setMargins(dp(7),0,0,0);row.addView(arrow,ap);
        row.setContentDescription(dayHeader(day)+" · "+count+" "+(count==1?"viaje":"viajes")+" · "+(closed?"plegado":"desplegado"));
        row.setOnClickListener(v->{Set<String> set=hist?collapsedHistoryDays:collapsedActiveDays;if(closed){collapseAllOtherDays(set,day);expandedId="";}else{set.add(day);for(JSONObject x:currentRows)if(day.equals(x.optString("pickup_date",""))&&expandedId.equals(x.optString("id",""))){expandedId="";break;}}hapticTick();renderRows(hist);});
        return row;
    }

    private void resetActiveDayAccordion(){activeDayGroupsInitialized=false;collapsedActiveDays.clear();expandedId="";}

    private void collapseAllOtherDays(Set<String> set,String openDay){set.clear();for(JSONObject x:currentRows){String d=x.optString("pickup_date","");if(!d.isEmpty()&&!d.equals(openDay))set.add(d);}set.remove(openDay);}

    // R10.2: indicador de expansión premium. No usa caracteres tipográficos:
    // badge sutil + chevron fino, con geometría diferenciada para día y reserva.
    private View chevronView(final boolean expanded,final boolean prominent){
        View v=new View(this){
            private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                float cx=getWidth()/2f,cy=getHeight()/2f;
                int badge=dp(prominent?36:32);
                float left=cx-badge/2f,top=cy-badge/2f,right=cx+badge/2f,bottom=cy+badge/2f;
                RectF r=new RectF(left,top,right,bottom);

                fill.setStyle(Paint.Style.FILL);
                fill.setColor(prominent?Color.argb(24,224,193,111):Color.argb(12,238,244,246));
                canvas.drawRoundRect(r,badge/2f,badge/2f,fill);

                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeCap(Paint.Cap.ROUND);
                stroke.setStrokeJoin(Paint.Join.ROUND);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(prominent?Color.argb(138,224,193,111):Color.argb(62,238,244,246));
                canvas.drawRoundRect(new RectF(left+.5f,top+.5f,right-.5f,bottom-.5f),badge/2f,badge/2f,stroke);

                stroke.setStrokeWidth(prominent?dp(2):dp(1));
                stroke.setColor(prominent?GOLD:Color.rgb(231,238,240));
                int dx=dp(prominent?6:5),dy=dp(prominent?4:3);
                Path path=new Path();
                if(expanded){
                    path.moveTo(cx-dx,cy-dy/2f);
                    path.lineTo(cx,cy+dy);
                    path.lineTo(cx+dx,cy-dy/2f);
                }else{
                    path.moveTo(cx-dy/2f,cy-dx);
                    path.lineTo(cx+dy,cy);
                    path.lineTo(cx-dy/2f,cy+dx);
                }
                canvas.drawPath(path,stroke);
            }
        };
        v.setAlpha(.98f);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return v;
    }

    private String syncSuffix(){if(lastSuccessfulSyncAt<=0)return "";return " · actualizado "+new java.text.SimpleDateFormat("HH:mm",Locale.US).format(new Date(lastSuccessfulSyncAt));}

    private void renderStats(boolean hist){
        statsBox.removeAllViews();
        if(!hist){
            int n=0,q=0,c=0;for(JSONObject r:currentRows){if(!matchesAgenda(r.optString("pickup_date","")))continue;String st=r.optString("status"),qs=r.optString("quote_status","SIN_PRESUPUESTO");if("PENDIENTE".equals(st)&&"ENVIADO".equals(qs))q++;else if("PENDIENTE".equals(st))n++;else if("ACEPTADA".equals(st)||"CONFIRMADA".equals(st)||"EN_VIAJE".equals(st))c++;}
            View nCard=statCard("NUEVAS",n,GOLD,"NUEVAS".equals(agendaStatusMode));
            View qCard=statCard("COTIZADAS",q,CYAN,"COTIZADAS".equals(agendaStatusMode));
            View cCard=statCard("CONFIRM.",c,GREEN,"CONFIRMADAS".equals(agendaStatusMode));
            nCard.setOnClickListener(v->setAgendaStatusMode("NUEVAS"));qCard.setOnClickListener(v->setAgendaStatusMode("COTIZADAS"));cCard.setOnClickListener(v->setAgendaStatusMode("CONFIRMADAS"));
            statsBox.addView(nCard,new LinearLayout.LayoutParams(0,-1,1));spacerH(statsBox,6);
            statsBox.addView(qCard,new LinearLayout.LayoutParams(0,-1,1));spacerH(statsBox,6);
            statsBox.addView(cCard,new LinearLayout.LayoutParams(0,-1,1));
        }else{
            int f=0,c=0,rj=0;for(JSONObject r:currentRows){String s=r.optString("status");if("FINALIZADA".equals(s))f++;else if("CANCELADA".equals(s))c++;else if("RECHAZADA".equals(s))rj++;}
            TextView summary=body(f+" finalizadas  ·  "+c+" canceladas  ·  "+rj+" rechazadas",13,TEXT);summary.setTypeface(Typeface.DEFAULT,Typeface.BOLD);summary.setGravity(Gravity.CENTER_VERTICAL);summary.setPadding(dp(14),0,dp(14),0);summary.setBackground(rounded(PANEL_2,LINE,15));statsBox.addView(summary,new LinearLayout.LayoutParams(-1,-1));
        }
    }

    private View statCard(String label,int count,int color,boolean active){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(4),dp(5),dp(4),dp(5));
        c.setBackground(rounded(active?Color.argb(58,Color.red(color),Color.green(color),Color.blue(color)):PANEL_2,active?color:LINE,15));c.setElevation(dp(active?4:1));c.setClickable(true);c.setFocusable(true);
        TextView n=body(String.valueOf(count),22,color);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);n.setGravity(Gravity.CENTER);c.addView(n);
        TextView l=body(label,10,active?TEXT:MUTED);l.setTypeface(Typeface.DEFAULT,Typeface.BOLD);l.setGravity(Gravity.CENTER);c.addView(l);return c;
    }

    private void setAgendaStatusMode(String mode){agendaStatusMode=mode.equals(agendaStatusMode)?"TODAS":mode;resetActiveDayAccordion();hapticTick();renderRows(false);}

    private View reservationCard(JSONObject r,boolean hist){
        String id=r.optString("id"),status=r.optString("status"),code=r.optString("code");
        boolean open=id.equals(expandedId);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);int cardAccent=statusColor(displayStatus(r));outer.setBackground(rounded(PANEL,cardAccent,18));outer.setElevation(dp(3));

        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(13),dp(9),dp(8),dp(9));head.setMinimumHeight(dp(88));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView codeV=body(code.isEmpty()?"RESERVA":code,13,GOLD);codeV.setTypeface(Typeface.DEFAULT,Typeface.BOLD);top.addView(codeV,new LinearLayout.LayoutParams(0,-2,1));
        double topKm=r.optDouble("route_distance_km",0);if(topKm>0){TextView kmTop=body("〰 "+formatDistance(topKm),11,GOLD);kmTop.setGravity(Gravity.CENTER);kmTop.setPadding(dp(8),dp(3),dp(8),dp(3));kmTop.setBackground(rounded(Color.argb(24,224,193,111),GOLD_DARK,13));LinearLayout.LayoutParams ktp=new LinearLayout.LayoutParams(-2,dp(30));ktp.setMargins(dp(7),0,dp(7),0);top.addView(kmTop,ktp);}
        top.addView(statusBadge(displayStatus(r)),new LinearLayout.LayoutParams(-2,dp(32)));
        View arrow=chevronView(open,false);arrow.setContentDescription(open?"Contraer reserva":"Expandir reserva");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(42),dp(42));ap.setMargins(dp(6),0,0,0);top.addView(arrow,ap);head.addView(top);

        String fullDt=prettyDate(r.optString("pickup_date"))+" · "+trimTime(r.optString("pickup_time"));
        String dt=hist?trimTime(r.optString("pickup_time")):fullDt;
        TextView date=body(dt,18,TEXT);date.setTypeface(Typeface.DEFAULT,Typeface.BOLD);head.addView(date,lpMatch(-2,2,1));
        TextView customer=body(r.optString("customer_name"),15,TEXT);head.addView(customer,lpMatch(-2,0,1));
        String route=shortAddress(r.optString("origin_text"))+"  →  "+shortAddress(r.optString("destination_text"));
        TextView routeText=body(route,14,MUTED);routeText.setMaxLines(2);routeText.setEllipsize(android.text.TextUtils.TruncateAt.END);head.addView(routeText,lpMatch(-2,0,0));
        outer.addView(head);

        LinearLayout bodyBox=new LinearLayout(this);bodyBox.setOrientation(LinearLayout.VERTICAL);bodyBox.setPadding(dp(13),0,dp(13),dp(13));bodyBox.setVisibility(open?View.VISIBLE:View.GONE);
        View div=new View(this);div.setBackgroundColor(Color.rgb(39,88,99));bodyBox.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));
        addDetail(bodyBox,"person","PASAJERO",r.optString("customer_name")+" · "+r.optString("customer_phone"));
        addDetail(bodyBox,"calendar","FECHA Y HORA",fullDt+" · "+r.optInt("passengers",1)+" pasajero(s)");
        addRouteBlock(bodyBox,"pin","ORIGEN",r.optString("origin_text"));
        addRouteBlock(bodyBox,"flag","DESTINO",r.optString("destination_text"));
        LinearLayout navRow=new LinearLayout(this);navRow.setOrientation(LinearLayout.HORIZONTAL);
        Button navOrigin=secondaryButton("IR AL ORIGEN"),navDest=secondaryButton("IR AL DESTINO");navOrigin.setTextSize(11);navDest.setTextSize(11);
        navRow.addView(navOrigin,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(navRow,7);navRow.addView(navDest,new LinearLayout.LayoutParams(0,dp(46),1));bodyBox.addView(navRow,lpMatch(dp(48),4,4));
        navOrigin.setOnClickListener(v->openNavigation(r.optString("origin_text","")));navDest.setOnClickListener(v->openNavigation(r.optString("destination_text","")));
        double km=r.optDouble("route_distance_km",0);int mins=r.optInt("route_duration_min",0);if(km>0)addDetail(bodyBox,"car","DISTANCIA",formatDistance(km)+(mins>0?" · aprox. "+mins+" min":""));
        JSONObject telemetry=TripTelemetryService.summary(this,id);String telemState=telemetry.optString("state","");
        if(telemetry.optLong("started_at",0)>0||"EN_VIAJE".equals(status)){
            String telemTitle=TripTelemetryService.STATE_ACTIVE.equals(telemState)?"● GPS REGISTRANDO":(TripTelemetryService.STATE_STARTING.equals(telemState)?"GPS INICIANDO":(TripTelemetryService.STATE_ERROR.equals(telemState)?"⚠ GPS SIN REGISTRO":"TELEMETRÍA GPS"));
            addDetail(bodyBox,"gps",telemTitle,telemetryLine(telemetry));
            Button trace=secondaryButton(TripTelemetryService.STATE_ACTIVE.equals(telemState)?"VER GPS EN VIVO":(TripTelemetryService.STATE_ERROR.equals(telemState)?"DIAGNÓSTICO GPS":"VER TRAZA GPS"));trace.setTextSize(11);trace.setOnClickListener(v->showTelemetryDialog(r));bodyBox.addView(trace,lpMatch(dp(46),2,4));
            if("EN_VIAJE".equals(status)&&!TripTelemetryService.STATE_ACTIVE.equals(telemState)){Button retryGps=secondaryButton("REINTENTAR GPS");retryGps.setTextSize(11);retryGps.setOnClickListener(v->{startTripTelemetry(r);handler.postDelayed(()->verifyTripTelemetryStart(r,0),700);});bodyBox.addView(retryGps,lpMatch(dp(46),2,4));}
        }
        String qs=r.optString("quote_status","SIN_PRESUPUESTO");
        if("ENVIADO".equals(qs)||"ACEPTADO".equals(qs)){addDetail(bodyBox,"ticket",qs.equals("ACEPTADO")?"PRESUPUESTO ACEPTADO":"PRESUPUESTO ENVIADO",formatMoney(r.optDouble("quote_final_total",0)));String inc=r.optString("quote_includes","").trim();if(!inc.isEmpty()&&!"null".equalsIgnoreCase(inc))addDetail(bodyBox,"info","INCLUYE",inc);double qkm=r.optDouble("route_distance_km",0),realKm=telemetry.optDouble("distance_km",0),pkm=driverPickupKm(r),op=(realKm>0?realKm:qkm)+pkm,total=r.optDouble("quote_final_total",0),rate=r.optDouble("quote_price_per_km",0),tolls=r.optDouble("quote_tolls",0);StringBuilder eco=new StringBuilder();if(qkm>0&&rate>0)eco.append(formatDistance(qkm)).append(" × ").append(formatMoney(rate)).append("/km");if(tolls>0)eco.append(eco.length()>0?" · ":"").append("peajes ").append(formatMoney(tolls));if(pkm>0)eco.append(eco.length()>0?" · ":"").append("+").append(formatDistance(pkm)).append(" hasta origen");if(total>0&&op>0)eco.append(" · ").append(formatMoney(total/op)).append(realKm>0?"/km operativo real":"/km operativo estimado");if(eco.length()>0)addDetail(bodyBox,"info","RESUMEN ECONÓMICO",eco.toString());}
        String com=r.optString("comments","");if(!com.isEmpty())addDetail(bodyBox,"comment","COMENTARIOS",com);

        LinearLayout contact=new LinearLayout(this);contact.setOrientation(LinearLayout.HORIZONTAL);
        Button call=smallAction("LLAMAR"),wa=smallAction("WHATSAPP"),map=smallAction("RUTA");
        contact.addView(call,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(contact,6);contact.addView(wa,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(contact,6);contact.addView(map,new LinearLayout.LayoutParams(0,dp(52),1));
        bodyBox.addView(contact,lpMatch(dp(52),10,6));
        call.setOnClickListener(v->openCall(r.optString("customer_phone")));wa.setOnClickListener(v->openWa(r.optString("customer_phone")));map.setOnClickListener(v->openRoute(r));
        LinearLayout utilities=new LinearLayout(this);utilities.setOrientation(LinearLayout.HORIZONTAL);Button share=smallAction("COMPARTIR"),cal=smallAction("CALENDARIO");utilities.addView(share,new LinearLayout.LayoutParams(0,dp(48),1));spacerH(utilities,6);utilities.addView(cal,new LinearLayout.LayoutParams(0,dp(48),1));bodyBox.addView(utilities,lpMatch(dp(50),0,6));share.setOnClickListener(v->shareDriverReservation(r));cal.setOnClickListener(v->addDriverReservationToCalendar(r));

        if(!hist){
            if("PENDIENTE".equals(status)){
                LinearLayout acts=new LinearLayout(this);acts.setOrientation(LinearLayout.HORIZONTAL);
                Button quote=primaryButton("ENVIADO".equals(qs)?"EDITAR PRESUPUESTO":"PREPARAR PRESUPUESTO"),reject=dangerButton("RECHAZAR");
                acts.addView(quote,new LinearLayout.LayoutParams(0,dp(58),2));spacerH(acts,7);acts.addView(reject,new LinearLayout.LayoutParams(0,dp(58),1));bodyBox.addView(acts,lpMatch(dp(58),4,0));
                quote.setOnClickListener(v->showQuoteDialog(r));reject.setOnClickListener(v->confirmReject(r));
            }else if("ACEPTADA".equals(status)){
                bodyBox.addView(tripSlideControl(r,false),lpMatch(dp(54),6,2));
            }else if("EN_VIAJE".equals(status)){
                bodyBox.addView(tripSlideControl(r,true),lpMatch(dp(54),6,2));
            }
        }
        outer.addView(bodyBox);
        head.setContentDescription((open?"Contraer ":"Expandir ")+(code.isEmpty()?"reserva":code));head.setOnClickListener(v->{expandedId=open?"":id;hapticTick();renderRows(hist);});
        return outer;
    }

    private void showQuoteDialog(JSONObject r){
        android.content.SharedPreferences pref=getSharedPreferences("pricing",MODE_PRIVATE);
        double cached=Double.longBitsToDouble(pref.getLong("toll_unit_bits",Double.doubleToLongBits(0)));
        toast("Comprobando agenda y reposicionamiento…");
        pool.execute(()->{
            double value=cached;JSONObject fit=null;Exception fitError=null;
            try{JSONObject cfg=Api.getQuoteSettings(pin);double remote=cfg.optDouble("default_toll_unit_value",value);if(remote>=0)value=remote;}catch(Exception ignored){}
            try{fit=Api.checkRouteFitV114(pin,r.optString("id"));}catch(Exception e){fitError=e;}
            final double def=value;final JSONObject f=fit;final Exception fe=fitError;
            pref.edit().putLong("toll_unit_bits",Double.doubleToRawLongBits(def)).apply();
            runOnUiThread(()->{
                if(f!=null&&!f.optBoolean("available",false)){showDriverInfo("Ese horario no es viable",routeFitDriverText(f));return;}
                if(fe!=null)toast("No pude comprobar el reposicionamiento; el servidor volverá a validar al confirmar");
                showQuoteDialogReady(r,def);
            });
        });
    }

    private String routeFitDriverText(JSONObject f){
        String reason=f.optString("reason","");String method=f.optString("reposition_method","ROAD");String suffix="ESTIMATED".equals(method)?"\n\nLa distancia de reposicionamiento fue estimada de forma conservadora.":"";
        if("OCCUPIED".equals(reason))return "La reserva se superpone con otro viaje o bloqueo de agenda."+suffix;
        if("TRAVEL_TIME".equals(reason)){
            int prev=f.optInt("previous_reposition_min",0),next=f.optInt("next_reposition_min",0);String earliest=f.optString("earliest_start","");
            if(prev>0)return "Después del viaje anterior necesitás aproximadamente "+prev+" min por carretera para llegar a este origen, además de tus márgenes operativos."+(earliest.isEmpty()?"":"\nHora viable aproximada desde: "+earliest.replace('T',' '))+suffix;
            if(next>0)return "Después de este traslado necesitás aproximadamente "+next+" min por carretera para llegar al origen del siguiente viaje, además de tus márgenes operativos."+suffix;
            return "No queda tiempo suficiente para reposicionarte entre los viajes."+suffix;
        }
        return "La agenda no permite ese horario."+suffix;
    }

    private void showQuoteDialogReady(JSONObject r,double defaultTollUnit){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.setClipToPadding(false);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(10),dp(18),dp(26));sc.addView(box);
        double km=r.optDouble("route_distance_km",0),oldRate=r.optDouble("quote_price_per_km",0);
        android.content.SharedPreferences pref=getSharedPreferences("pricing",MODE_PRIVATE);
        if(oldRate<=0)oldRate=Double.longBitsToDouble(pref.getLong("price_per_km_bits",Double.doubleToLongBits(0)));
        int oldCount=r.optInt("quote_toll_count",0);double oldUnit=r.optDouble("quote_toll_unit_value",0);double oldTolls=r.optDouble("quote_tolls",0);
        if(oldUnit<=0)oldUnit=defaultTollUnit;
        if(oldCount<=0&&oldTolls>0){oldCount=1;if(oldUnit<=0)oldUnit=oldTolls;}
        EditText kmF=quoteNumberField(box,"Distancia por carretera (km)",km>0?String.format(Locale.US,"%.1f",km):"");
        EditText rateF=quoteNumberField(box,"Referencia interna por km (UYU)",oldRate>0?String.format(Locale.US,"%.0f",oldRate):"");
        EditText minF=quoteNumberField(box,"Mínimo interno de referencia",valueOrPref(r,"quote_minimum",pref,"minimum_bits"));
        EditText tollCountF=quoteNumberField(box,"Cantidad de peajes",oldCount>0?String.valueOf(oldCount):"");
        tollCountF.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout tollQuick=new LinearLayout(this);for(int i=0;i<=3;i++){final int tc=i;Button b=secondaryButton(i+" PE"+(i==1?"AJE":"AJES"));b.setTextSize(9);b.setOnClickListener(v->tollCountF.setText(String.valueOf(tc)));tollQuick.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));if(i<3)spacerH(tollQuick,4);}box.addView(tollQuick,lpMatch(dp(44),2,4));
        EditText tollUnitF=quoteNumberField(box,"Valor por peaje (UYU)",oldUnit>0?String.format(Locale.US,"%.0f",oldUnit):"");
        TextView tollLine=body("Peajes: sin peajes",13,MUTED);box.addView(tollLine,lpMatch(-2,2,4));
        EditText waitF=quoteNumberField(box,"Espera",valueOrPref(r,"quote_waiting",pref,"waiting_bits"));
        EditText pickupKmF=quoteNumberField(box,"Km hasta el origen (editable)",driverPickupKmValue(r));
        LinearLayout gpsRow=new LinearLayout(this);TextView gpsHint=body("Podés escribir los km reales o estimarlos con la última ubicación GPS (distancia lineal).",11,MUTED);gpsRow.addView(gpsHint,new LinearLayout.LayoutParams(0,-2,1));Button gpsBtn=secondaryButton("GPS");gpsBtn.setTextSize(11);gpsRow.addView(gpsBtn,new LinearLayout.LayoutParams(dp(74),dp(42)));box.addView(gpsRow,lpMatch(-2,2,4));
        EditText pickupF=quoteNumberField(box,"Extra monetario hasta origen",valueOrPref(r,"quote_pickup_extra",pref,"pickup_extra_bits"));
        EditText otherF=quoteNumberField(box,"Otros extras",valueOrPref(r,"quote_other",pref,"other_bits"));
        TextView notePrice=body("El cálculo por km es solo una referencia interna. El cliente recibe únicamente el precio final que vos decidas.",12,MUTED);box.addView(notePrice,lpMatch(-2,7,3));
        TextView ref=body("Referencia interna: completá kilómetros y tarifa",14,GOLD);box.addView(ref,lpMatch(-2,6,2));
        TextView profitability=body("Operación: completá km hasta origen para ver km operativos",12,MUTED);box.addView(profitability,lpMatch(-2,2,4));
        EditText totalF=quoteNumberField(box,"PRECIO FINAL AL CLIENTE (UYU)",quoteValue(r,"quote_final_total"));
        EditText includes=addField(box,"info","Descripción opcional para el cliente","Ej: traslado reservado puerta a puerta");includes.setText(r.optString("quote_includes",""));
        Button calc=secondaryButton("USAR SUGERIDO");box.addView(calc,lpMatch(dp(50),8,4));
        LinearLayout roundRow=new LinearLayout(this);Button round50=secondaryButton("REDONDEAR 50 UYU"),round100=secondaryButton("REDONDEAR 100 UYU");round50.setTextSize(9);round100.setTextSize(9);roundRow.addView(round50,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(roundRow,6);roundRow.addView(round100,new LinearLayout.LayoutParams(0,dp(44),1));box.addView(roundRow,lpMatch(dp(46),0,6));

        final Runnable refresh=()->{int count=Math.max(0,(int)Math.round(num(tollCountF)));double unit=Math.max(0,num(tollUnitF));double tolls=count*unit;double reference=quoteReference(num(kmF),num(rateF),num(minF),tolls,num(waitF),num(pickupF),num(otherF));tollLine.setText(count<=0?"Peajes: sin peajes":"Peajes: "+count+" × "+formatMoney(unit)+" = "+formatMoney(tolls));ref.setText("Referencia interna: "+formatMoney(reference));double opKm=Math.max(0,num(kmF))+Math.max(0,num(pickupKmF));double income=num(totalF)>0?num(totalF):reference;profitability.setText(opKm>0?"Operación: "+formatDistance(opKm)+" operativos · "+(income>0?formatMoney(income/opKm)+"/km":"sin total definido"):"Operación: completá km hasta origen para ver km operativos");};
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence c,int st,int count,int after){}public void onTextChanged(CharSequence c,int st,int before,int count){}public void afterTextChanged(android.text.Editable e){refresh.run();}};
        for(EditText e:new EditText[]{kmF,rateF,minF,tollCountF,tollUnitF,waitF,pickupKmF,pickupF,otherF,totalF})e.addTextChangedListener(watcher);
        gpsBtn.setOnClickListener(v->{double d=estimatePickupDistanceKm(r);if(d>0){pickupKmF.setText(String.format(Locale.US,"%.1f",d));toast("Distancia GPS aproximada (lineal) cargada");}});
        calc.setOnClickListener(v->{refresh.run();int count=Math.max(0,(int)Math.round(num(tollCountF)));double reference=quoteReference(num(kmF),num(rateF),num(minF),count*Math.max(0,num(tollUnitF)),num(waitF),num(pickupF),num(otherF));totalF.setText(String.format(Locale.US,"%.0f",reference));hapticTick();});
        round50.setOnClickListener(v->{double x=num(totalF);if(x<=0){int count=Math.max(0,(int)Math.round(num(tollCountF)));x=quoteReference(num(kmF),num(rateF),num(minF),count*Math.max(0,num(tollUnitF)),num(waitF),num(pickupF),num(otherF));}if(x>0)totalF.setText(String.format(Locale.US,"%.0f",Math.ceil(x/50.0)*50.0));hapticTick();});
        round100.setOnClickListener(v->{double x=num(totalF);if(x<=0){int count=Math.max(0,(int)Math.round(num(tollCountF)));x=quoteReference(num(kmF),num(rateF),num(minF),count*Math.max(0,num(tollUnitF)),num(waitF),num(pickupF),num(otherF));}if(x>0)totalF.setText(String.format(Locale.US,"%.0f",Math.ceil(x/100.0)*100.0));hapticTick();});
        refresh.run();

        final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("PREPARAR PRESUPUESTO · "+r.optString("code"),"El cálculo es interno. El cliente recibe únicamente el precio final que decidas.");shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);Button cancel=secondaryButton("CANCELAR"),send=primaryButton("ENVIAR PRESUPUESTO");actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(54),1));spacerH(actions,7);actions.addView(send,new LinearLayout.LayoutParams(0,dp(54),2));shell.addView(actions,lpMatch(dp(58),8,0));cancel.setOnClickListener(v->d.dismiss());send.setOnClickListener(v->{double qkm=num(kmF),rate=num(rateF),minimum=num(minF),unit=Math.max(0,num(tollUnitF)),waiting=num(waitF),pickup=num(pickupF),other=num(otherF);int count=Math.max(0,(int)Math.round(num(tollCountF)));double tolls=count*unit;double reference=quoteReference(qkm,rate,minimum,tolls,waiting,pickup,other);double total=num(totalF);if(total<=0){toast("Definí el precio final que verá el cliente");return;}send.setEnabled(false);d.dismiss();sendQuote(r,qkm,r.optInt("route_duration_min",0),rate,minimum,count,unit,waiting,num(pickupKmF),pickup,other,reference,total,includes.getText().toString().trim());});showDriverDialog(d,shell,.96f,.92f);
    }

    private EditText quoteNumberField(LinearLayout box,String label,String value){EditText e=addField(box,"ticket",label,"0");e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);if(value!=null&&!value.isEmpty()&&!"0".equals(value))e.setText(value);return e;}
    private String quoteValue(JSONObject r,String key){double v=r.optDouble(key,0);return v>0?String.format(Locale.US,"%.0f",v):"";}
    private String valueOrPref(JSONObject r,String key,android.content.SharedPreferences p,String pref){double v=r.optDouble(key,0);if(v<=0)v=Double.longBitsToDouble(p.getLong(pref,Double.doubleToLongBits(0)));return v>0?String.format(Locale.US,"%.0f",v):"";}
    private double num(EditText e){try{return Double.parseDouble(e.getText().toString().trim().replace(',','.'));}catch(Exception x){return 0;}}
    private double quoteReference(double km,double rate,double minimum,double tolls,double waiting,double pickup,double other){double base=km*rate;if(minimum>base)base=minimum;return Math.max(0,base+tolls+waiting+pickup+other);}
    private void sendQuote(JSONObject r,double km,int duration,double rate,double minimum,int tollCount,double tollUnit,double waiting,double pickupKm,double pickup,double other,double reference,double total,String includes){
        getSharedPreferences("pricing",MODE_PRIVATE).edit().putLong("price_per_km_bits",Double.doubleToRawLongBits(rate)).putLong("minimum_bits",Double.doubleToRawLongBits(minimum)).putLong("toll_unit_bits",Double.doubleToRawLongBits(tollUnit)).putLong("waiting_bits",Double.doubleToRawLongBits(waiting)).putLong("pickup_extra_bits",Double.doubleToRawLongBits(pickup)).putLong("other_bits",Double.doubleToRawLongBits(other)).putLong("pickup_km_"+r.optString("id",r.optString("code","")),Double.doubleToRawLongBits(pickupKm)).apply();
        driverMarkSelfChange(r.optString("id"));
        toast("Enviando presupuesto…");
        pool.execute(()->{try{
            Api.sendQuoteV107(pin,r.optString("id"),km,duration,rate,minimum,tollCount,tollUnit,waiting,pickup,other,reference,total,includes);
            boolean saved=true;try{Api.setDefaultToll(pin,tollUnit);}catch(Exception ignored){saved=false;}
            Api.logEvent("quote_sent","Presupuesto enviado","{\"reservation\":\""+r.optString("code")+"\"}");
            final boolean configSaved=saved;
            runOnUiThread(()->{toast("Presupuesto enviado · "+formatMoney(total)+(configSaved?"":" · valor de peaje no pudo guardarse como predeterminado"));expandedId=r.optString("id");loadActive(true);});
        }catch(Exception e){runOnUiThread(()->toast("No se pudo enviar el presupuesto: "+friendly(e)));}});
    }

    private void driverMarkSelfChange(String id){if(id==null||id.isEmpty())return;getSharedPreferences("driver_bubble",MODE_PRIVATE).edit().putString("self_change_id",id).putLong("self_change_at",System.currentTimeMillis()).apply();}

    private String jsonTime(JSONObject j,String key,String fallback){String x=j.optString(key,fallback);return x==null||x.isEmpty()||"null".equalsIgnoreCase(x)?fallback:(x.length()>=5?x.substring(0,5):x);}
    private EditText availabilityField(LinearLayout box,String label,String value,boolean numeric){EditText e=addField(box,"ticket",label,value);e.setText(value);if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private int intValue(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return fallback;}}
    private void loadAvailabilitySummary(){if(pin==null||pin.isEmpty()||availabilityStatus==null)return;pool.execute(()->{try{JSONObject j=Api.getAvailabilitySettings(pin);getSharedPreferences("driver_availability_cache",MODE_PRIVATE).edit().putInt("before",j.optInt("buffer_before_min",15)).putInt("after",j.optInt("buffer_after_min",30)).putInt("interval",j.optInt("slot_interval_min",30)).apply();runOnUiThread(()->{if(availabilityStatus==null)return;if(j.optBoolean("enabled",true)){availabilityStatus.setText("● RESERVAS ONLINE · "+jsonTime(j,"day_start","08:00")+"–"+jsonTime(j,"day_end","22:00")+" · cada "+j.optInt("slot_interval_min",30)+" min · antes "+j.optInt("buffer_before_min",15)+" min · después "+j.optInt("buffer_after_min",30)+" min");availabilityStatus.setTextColor(GREEN);}else{availabilityStatus.setText("○ RESERVAS ONLINE DESACTIVADAS · tocá HORARIOS para configurar");availabilityStatus.setTextColor(MUTED);}});}catch(Exception e){runOnUiThread(()->{if(availabilityStatus!=null){availabilityStatus.setText("⚠ No pude leer la disponibilidad · tocá para reintentar");availabilityStatus.setTextColor(Color.rgb(235,170,80));}});}});}
    private void showAvailabilitySettings(){toast("Cargando disponibilidad…");pool.execute(()->{try{JSONObject j=Api.getAvailabilitySettings(pin);runOnUiThread(()->buildAvailabilitySettingsDialog(j));}catch(Exception e){runOnUiThread(()->toast("No pude cargar horarios: "+friendly(e)));}});}
    private void buildAvailabilitySettingsDialog(JSONObject j){
        final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=driverDialogShell("HORARIOS Y MÁRGENES","Configurá cuándo aceptar reservas y cuánto tiempo operativo querés dejar entre viajes.");
        ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(2),0,dp(8));sc.addView(box,new ScrollView.LayoutParams(-1,-2));shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        CheckBox enabled=new CheckBox(this);enabled.setText("Aceptar reservas online");enabled.setTextColor(TEXT);enabled.setChecked(j.optBoolean("enabled",true));box.addView(enabled,lpMatch(dp(50),0,7));
        EditText start=availabilityField(box,"Hora inicial",jsonTime(j,"day_start","08:00"),false),end=availabilityField(box,"Hora final",jsonTime(j,"day_end","22:00"),false);
        box.addView(body("DÍAS HABILITADOS",12,GOLD),lpMatch(-2,6,4));String[] labs={"D","L","M","X","J","V","S"};CheckBox[] days=new CheckBox[7];HashSet<Integer> active=new HashSet<>();JSONArray aa=j.optJSONArray("active_weekdays");if(aa!=null)for(int i=0;i<aa.length();i++)active.add(aa.optInt(i,-1));else for(int i=0;i<7;i++)active.add(i);
        LinearLayout day1=new LinearLayout(this),day2=new LinearLayout(this);for(int i=0;i<7;i++){CheckBox c=new CheckBox(this);days[i]=c;c.setText(labs[i]);c.setTextColor(TEXT);c.setGravity(Gravity.CENTER);c.setChecked(active.contains(i));(i<4?day1:day2).addView(c,new LinearLayout.LayoutParams(0,dp(46),1));}box.addView(day1);box.addView(day2);
        box.addView(body("PRESETS DE MARGEN",12,GOLD),lpMatch(-2,8,4));LinearLayout presets=new LinearLayout(this);Button tight=secondaryButton("AJUSTADO 5/5"),normal=secondaryButton("NORMAL 10/10"),safe=secondaryButton("CONSERV. 15/30");for(Button b:new Button[]{tight,normal,safe}){b.setTextSize(9);presets.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));if(b!=safe)spacerH(presets,4);}box.addView(presets,lpMatch(dp(46),0,6));
        EditText interval=availabilityField(box,"Intervalo de horarios ofrecidos (min)",String.valueOf(j.optInt("slot_interval_min",30)),true);EditText before=availabilityField(box,"Llegar antes al próximo origen (min)",String.valueOf(j.optInt("buffer_before_min",15)),true);EditText after=availabilityField(box,"Margen después de finalizar (min)",String.valueOf(j.optInt("buffer_after_min",30)),true);EditText duration=availabilityField(box,"Duración por defecto si no hay ruta (min)",String.valueOf(j.optInt("default_trip_min",60)),true);EditText hold=availabilityField(box,"Retener solicitud pendiente (min)",String.valueOf(j.optInt("pending_hold_min",30)),true);EditText lead=availabilityField(box,"Anticipación mínima para reservar (min)",String.valueOf(j.optInt("lead_time_min",30)),true);
        TextView example=body("",12,TEXT);example.setPadding(dp(10),dp(10),dp(10),dp(10));example.setBackground(rounded(PANEL,LINE,14));box.addView(example,lpMatch(-2,7,5));
        final Runnable updateExample=()->{int iv=Math.max(5,intValue(interval,30)),bf=Math.max(0,intValue(before,10)),af=Math.max(0,intValue(after,10));int raw=10*60+35+8+af+8+bf;int rounded=((raw+iv-1)/iv)*iv;example.setText("EJEMPLO · viaje 10:35–10:43 + "+af+" min después + 8 min hasta el próximo origen + "+bf+" min antes = "+String.format(Locale.US,"%02d:%02d",(raw/60)%24,raw%60)+" · próximo turno ofrecido "+String.format(Locale.US,"%02d:%02d",(rounded/60)%24,rounded%60));};
        tight.setOnClickListener(v->{before.setText("5");after.setText("5");updateExample.run();hapticTick();});normal.setOnClickListener(v->{before.setText("10");after.setText("10");updateExample.run();hapticTick();});safe.setOnClickListener(v->{before.setText("15");after.setText("30");updateExample.run();hapticTick();});
        android.text.TextWatcher tw=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence c,int st,int count,int after){}public void onTextChanged(CharSequence c,int st,int beforeCount,int count){}public void afterTextChanged(android.text.Editable e){updateExample.run();}};before.addTextChangedListener(tw);after.addTextChangedListener(tw);interval.addTextChangedListener(tw);updateExample.run();
        TextView note=body("El tiempo por carretera entre un destino y el siguiente origen se calcula aparte. El cliente solo ve horarios disponibles; nunca ve tu agenda ni otros pasajeros.",11,MUTED);box.addView(note,lpMatch(-2,4,4));
        LinearLayout actions=new LinearLayout(this);Button cancel=secondaryButton("CANCELAR"),save=primaryButton("GUARDAR");actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(actions,7);actions.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));shell.addView(actions,lpMatch(dp(54),8,0));cancel.setOnClickListener(v->d.dismiss());
        save.setOnClickListener(v->{String st=start.getText().toString().trim(),en=end.getText().toString().trim();if(!st.matches("[0-2][0-9]:[0-5][0-9]")||!en.matches("[0-2][0-9]:[0-5][0-9]")){toast("Usá formato HH:mm");return;}JSONArray wd=new JSONArray();for(int i=0;i<7;i++)if(days[i].isChecked())wd.put(i);if(wd.length()==0){toast("Elegí al menos un día");return;}try{JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_enabled",enabled.isChecked());b.put("p_day_start",st);b.put("p_day_end",en);b.put("p_active_weekdays",wd);b.put("p_slot_interval_min",intValue(interval,30));b.put("p_buffer_before_min",intValue(before,10));b.put("p_buffer_after_min",intValue(after,10));b.put("p_default_trip_min",intValue(duration,60));b.put("p_pending_hold_min",intValue(hold,30));b.put("p_lead_time_min",intValue(lead,30));save.setEnabled(false);pool.execute(()->{try{Api.setAvailabilitySettings(b);getSharedPreferences("driver_availability_cache",MODE_PRIVATE).edit().putInt("before",intValue(before,10)).putInt("after",intValue(after,10)).putInt("interval",intValue(interval,30)).apply();runOnUiThread(()->{toast("Disponibilidad guardada");d.dismiss();loadAvailabilitySummary();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);toast(Api.publicMessage("driver_set_availability_settings_v11_3",e));});}});}catch(Exception e){toast("Datos inválidos");}});
        showDriverDialog(d,shell,.94f,.90f);
    }
    private void showAddScheduleBlock(){
        Calendar c=Calendar.getInstance();final String[] date={new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime())};final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("BLOQUEAR HORARIO","Creá un bloqueo privado para impedir reservas online en ese tramo.");LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);Button dateBtn=secondaryButton("📅 "+prettyDate(date[0]));box.addView(dateBtn,lpMatch(dp(52),0,8));EditText start=availabilityField(box,"Desde (HH:mm)","09:00",false),end=availabilityField(box,"Hasta (HH:mm)","10:00",false),note=availabilityField(box,"Motivo privado (opcional)","",false);dateBtn.setOnClickListener(v->showPremiumDatePicker(c,picked->{date[0]=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(picked.getTime());dateBtn.setText("📅 "+prettyDate(date[0]));}));shell.addView(box,new LinearLayout.LayoutParams(-1,0,1));LinearLayout actions=new LinearLayout(this);Button cancel=secondaryButton("CANCELAR"),save=primaryButton("BLOQUEAR");actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(actions,7);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));shell.addView(actions,lpMatch(dp(56),8,0));cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->{String a=start.getText().toString().trim(),z=end.getText().toString().trim();if(!a.matches("[0-2][0-9]:[0-5][0-9]")||!z.matches("[0-2][0-9]:[0-5][0-9]")){toast("Usá formato HH:mm");return;}save.setEnabled(false);pool.execute(()->{try{Api.addScheduleBlock(pin,date[0],a,z,note.getText().toString().trim());runOnUiThread(()->{toast("Horario bloqueado");d.dismiss();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);toast("No pude bloquear: "+friendly(e));});}});});showDriverDialog(d,shell,.92f,.72f);
    }
    private void showScheduleBlocks(){
        Calendar a=Calendar.getInstance(),b=(Calendar)a.clone();b.add(Calendar.DAY_OF_YEAR,90);String from=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(a.getTime()),to=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(b.getTime());toast("Cargando bloqueos…");pool.execute(()->{try{JSONArray rows=Api.listScheduleBlocks(pin,from,to);runOnUiThread(()->{if(rows.length()==0){showDriverInfo("BLOQUEOS","No hay bloqueos manuales en los próximos 90 días.");return;}final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("BLOQUEOS","Tocá un bloqueo para eliminarlo.");ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;String n=r.optString("note","");String label=prettyDate(r.optString("date"))+" · "+trimTime(r.optString("start"))+"–"+trimTime(r.optString("end"))+(n.isEmpty()?"":"\n"+n);Button item=secondaryButton(label);item.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);item.setTextSize(12);item.setSingleLine(false);item.setPadding(dp(12),dp(5),dp(12),dp(5));item.setOnClickListener(v->showDriverConfirm("ELIMINAR BLOQUEO",label,"VOLVER","ELIMINAR",()->{d.dismiss();pool.execute(()->{try{Api.deleteScheduleBlock(pin,r.optString("id"));runOnUiThread(()->toast("Bloqueo eliminado"));}catch(Exception e){runOnUiThread(()->toast("No pude eliminar: "+friendly(e)));}});}));list.addView(item,lpMatch(dp(64),3,3));}shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));Button close=primaryButton("CERRAR");close.setOnClickListener(v->d.dismiss());shell.addView(close,lpMatch(dp(52),8,0));showDriverDialog(d,shell,.92f,.80f);});}catch(Exception e){runOnUiThread(()->toast("No pude cargar bloqueos: "+friendly(e)));}});
    }

    private ArrayList<JSONObject> agendaFilteredRows(){ArrayList<JSONObject> out=new ArrayList<>();for(JSONObject r:currentRows)if(matchesAgenda(r.optString("pickup_date",""))&&matchesAgendaStatus(r))out.add(r);return out;}
    private boolean matchesAgendaStatus(JSONObject r){if("TODAS".equals(agendaStatusMode))return true;String st=r.optString("status",""),qs=r.optString("quote_status","SIN_PRESUPUESTO");if("NUEVAS".equals(agendaStatusMode))return "PENDIENTE".equals(st)&&!"ENVIADO".equals(qs);if("COTIZADAS".equals(agendaStatusMode))return "PENDIENTE".equals(st)&&"ENVIADO".equals(qs);if("CONFIRMADAS".equals(agendaStatusMode))return "ACEPTADA".equals(st)||"CONFIRMADA".equals(st)||"EN_VIAJE".equals(st);return true;}
    private boolean matchesAgenda(String date){if("TODAS".equals(agendaMode)||date==null||date.length()<10)return true;try{Calendar d=Calendar.getInstance();d.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(date));Calendar a=(Calendar)agendaDate.clone();clearTime(d);clearTime(a);if("DIA".equals(agendaMode))return sameDay(d,a);if("SEMANA".equals(agendaMode)){Calendar end=(Calendar)a.clone();end.add(Calendar.DAY_OF_YEAR,6);return !d.before(a)&&!d.after(end);}if("MES".equals(agendaMode))return d.get(Calendar.YEAR)==a.get(Calendar.YEAR)&&d.get(Calendar.MONTH)==a.get(Calendar.MONTH);}catch(Exception ignored){}return true;}
    private void clearTime(Calendar c){c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}
    private boolean sameDay(Calendar a,Calendar b){return a.get(Calendar.YEAR)==b.get(Calendar.YEAR)&&a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);}
    private void pickAgendaDate(){showPremiumDatePicker(agendaDate,c->{agendaDate=c;agendaMode="DIA";agendaCustomDate=true;resetActiveDayAccordion();renderRows(false);});}
    private String agendaDescription(){String base;if(agendaCustomDate)base="reservas del "+new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.US).format(agendaDate.getTime());else if("TODAS".equals(agendaMode))base="todas las reservas activas";else if("DIA".equals(agendaMode))base="reservas de hoy";else if("SEMANA".equals(agendaMode))base="reservas de los próximos 7 días";else base="reservas del mes actual";if("NUEVAS".equals(agendaStatusMode))return base+" · nuevas";if("COTIZADAS".equals(agendaStatusMode))return base+" · cotizadas";if("CONFIRMADAS".equals(agendaStatusMode))return base+" · confirmadas";return base;}
    private void refreshAgendaControls(){if(agendaLabel!=null)agendaLabel.setText(agendaDescription());String picked=new java.text.SimpleDateFormat("dd/MM",Locale.US).format(agendaDate.getTime());if(agendaDateBtn!=null)agendaDateBtn.setText(agendaCustomDate?picked:"FECHA");setAgendaQuickState(agendaAllBtn,"TODAS",!agendaCustomDate&&"TODAS".equals(agendaMode));setAgendaQuickState(agendaTodayBtn,"HOY",!agendaCustomDate&&"DIA".equals(agendaMode));setAgendaQuickState(agendaWeekBtn,"SEMANA",!agendaCustomDate&&"SEMANA".equals(agendaMode));setAgendaQuickState(agendaMonthBtn,"MES",!agendaCustomDate&&"MES".equals(agendaMode));}
    private void setAgendaQuickState(Button b,String label,boolean active){if(b==null)return;b.setText(label);b.setAlpha(active?1.0f:0.84f);b.setTextColor(active?GOLD:TEXT);b.setBackground(rounded(active?Color.argb(52,224,193,111):PANEL_2,active?GOLD:LINE,16));}
    private String dayHeader(String d){try{Date x=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(d);return new java.text.SimpleDateFormat("EEEE dd/MM",new Locale("es","UY")).format(x).toUpperCase(new Locale("es","UY"));}catch(Exception e){return d;}}
    private String formatDistance(double km){return String.format(Locale.US,"%.1f km",km);}
    private String formatMoney(double v){return String.format(Locale.US,"%,.0f",v).replace(',', '.')+" UYU";}

    private void changeStatus(JSONObject r,String to){
        if("EN_VIAJE".equals(to)&&!asegurarGpsAntesDeViaje())return;
        String changeKey=r.optString("id",r.optString("code",""));if(driverStatusInFlight.contains(changeKey)){toast("Actualización en curso…");return;}driverStatusInFlight.add(changeKey);
        final boolean telemetryArmed="EN_VIAJE".equals(to);if(telemetryArmed){if(!startTripTelemetry(r)){driverStatusInFlight.remove(changeKey);return;}toast("Iniciando viaje · comprobando GPS…");}else toast("Actualizando estado…");
        String id=r.optString("id");driverMarkSelfChange(id);
        pool.execute(()->{
            try{
                Api.setStatus(pin,id,to);Api.logEvent("status_changed",to,"{\"reservation\":\""+r.optString("code")+"\"}");
                runOnUiThread(()->{driverStatusInFlight.remove(changeKey);if("FINALIZADA".equals(to)){stopTripTelemetry(r);playTripActionCue(true);toast("✓ Viaje finalizado");handler.postDelayed(()->showTripFinishSummary(r),900);}else if("EN_VIAJE".equals(to)){playTripActionCue(false);toast("✓ Viaje iniciado · verificando GPS");handler.postDelayed(()->verifyTripTelemetryStart(r,0),700);}else toast("Estado actualizado: "+prettyStatus(to));expandedId="";loadActive(true);});
            }catch(Exception e){getSharedPreferences("driver_bubble",MODE_PRIVATE).edit().remove("self_change_id").remove("self_change_at").apply();runOnUiThread(()->{driverStatusInFlight.remove(changeKey);if(telemetryArmed)abortTripTelemetry(r);toast("No se pudo cambiar el estado: "+friendly(e));});}
        });
    }

    private void confirmReject(JSONObject r){showDriverConfirm("Rechazar solicitud","¿Confirmás rechazar la reserva "+r.optString("code")+"?","VOLVER","RECHAZAR",()->changeStatus(r,"RECHAZADA"));}

    private void alertNew(JSONObject r){
        String incomingId=r.optString("id","");String incomingCode=r.optString("code","");boolean dashboardPresent=false;
        for(int i=0;i<currentRows.size();i++){JSONObject row=currentRows.get(i);boolean same=(!incomingId.isEmpty()&&incomingId.equals(row.optString("id","")))||(!incomingCode.isEmpty()&&incomingCode.equals(row.optString("code","")));if(same){currentRows.set(i,r);dashboardPresent=true;break;}}
        if(!dashboardPresent)currentRows.add(0,r);if(!historyMode)renderRows(false);
        String key=!incomingId.isEmpty()?incomingId:incomingCode;if(key.isEmpty())key=String.valueOf(System.currentTimeMillis());
        if(driverAlertQueuedIds.add(key)){driverAlertQueue.add(r);if(claimNewReservationCue(key))playWarmChime();}
        if(driverAlertDialog==null||!driverAlertDialog.isShowing()){driverAlertIndex=Math.max(0,driverAlertQueue.size()-1);showDriverAlertAt(driverAlertIndex);}else toast("Nueva solicitud · "+driverAlertQueue.size()+" aviso(s) pendiente(s)");
    }

    private void showDriverAlertAt(int requested){
        if(driverAlertQueue.isEmpty())return;driverAlertIndex=Math.max(0,Math.min(requested,driverAlertQueue.size()-1));JSONObject r=driverAlertQueue.get(driverAlertIndex);
        if(driverAlertDialog!=null&&driverAlertDialog.isShowing())driverAlertDialog.dismiss();
        final Dialog dialog=new Dialog(this);driverAlertDialog=dialog;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(16),dp(12),dp(16),dp(14));shell.setBackground(rounded(PANEL_2,GOLD_DARK,22));shell.setElevation(dp(12));

        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        Button prev=secondaryButton("‹"),next=secondaryButton("›");prev.setTextSize(22);next.setTextSize(22);
        TextView position=body("AVISO "+(driverAlertIndex+1)+" DE "+driverAlertQueue.size()+"  ·  "+currentRows.size()+" RESERVAS",10,GOLD);position.setTypeface(Typeface.DEFAULT,Typeface.BOLD);position.setGravity(Gravity.CENTER);position.setLetterSpacing(.06f);
        nav.addView(prev,new LinearLayout.LayoutParams(dp(46),dp(40)));nav.addView(position,new LinearLayout.LayoutParams(0,dp(40),1));nav.addView(next,new LinearLayout.LayoutParams(dp(46),dp(40)));shell.addView(nav);
        prev.setEnabled(driverAlertIndex>0);next.setEnabled(driverAlertIndex<driverAlertQueue.size()-1);
        prev.setAlpha(prev.isEnabled()?1f:.35f);next.setAlpha(next.isEnabled()?1f:.35f);
        prev.setOnClickListener(v->showDriverAlertAt(driverAlertIndex-1));next.setOnClickListener(v->showDriverAlertAt(driverAlertIndex+1));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(2),dp(6),dp(2),dp(18));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout labelRow=new LinearLayout(this);labelRow.setGravity(Gravity.CENTER_VERTICAL);TextView label=body("NUEVA SOLICITUD",11,GOLD);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);label.setLetterSpacing(.08f);label.setGravity(Gravity.CENTER);label.setPadding(dp(12),0,dp(12),0);label.setBackground(rounded(Color.argb(32,224,193,111),GOLD_DARK,16));labelRow.addView(label,new LinearLayout.LayoutParams(-2,dp(32)));TextView code=body(r.optString("code"),11,MUTED);code.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams codeLp=new LinearLayout.LayoutParams(0,dp(32),1);codeLp.setMargins(dp(10),0,0,0);labelRow.addView(code,codeLp);content.addView(labelRow);
        TextView customer=heading(r.optString("customer_name"),23);content.addView(customer,lpMatch(-2,10,2));String dt=prettyDate(r.optString("pickup_date"))+" · "+trimTime(r.optString("pickup_time"))+" · "+r.optInt("passengers",1)+" pasajero(s)";content.addView(body(dt,13,GOLD),lpMatch(-2,0,9));
        View div=new View(this);div.setBackgroundColor(Color.rgb(51,96,106));content.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));addRouteBlock(content,"pin","ORIGEN",r.optString("origin_text"));addRouteBlock(content,"flag","DESTINO",r.optString("destination_text"));LinearLayout popupNav=new LinearLayout(this);Button popupNavOrigin=secondaryButton("IR AL ORIGEN"),popupNavDest=secondaryButton("IR AL DESTINO");popupNavOrigin.setTextSize(10);popupNavDest.setTextSize(10);popupNav.addView(popupNavOrigin,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(popupNav,7);popupNav.addView(popupNavDest,new LinearLayout.LayoutParams(0,dp(44),1));content.addView(popupNav,lpMatch(dp(46),4,4));popupNavOrigin.setOnClickListener(v->openNavigation(r.optString("origin_text","")));popupNavDest.setOnClickListener(v->openNavigation(r.optString("destination_text","")));double km=r.optDouble("route_distance_km",0);int mins=r.optInt("route_duration_min",0);if(km>0)addDetail(content,"car","DISTANCIA",formatDistance(km)+(mins>0?" · aprox. "+mins+" min":""));String com=r.optString("comments","");if(!com.isEmpty())addDetail(content,"comment","COMENTARIOS",com);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(10),0,dp(4));Button list=secondaryButton("VER LISTA");Button quote=primaryButton("PREPARAR PRESUPUESTO");for(Button b:new Button[]{list,quote}){b.setGravity(Gravity.CENTER);b.setPadding(dp(10),0,dp(10),0);b.setMinHeight(0);b.setMinimumHeight(0);b.setIncludeFontPadding(false);}list.setSingleLine(true);quote.setSingleLine(false);quote.setMaxLines(2);quote.setLineSpacing(0f,0.92f);quote.setTextSize(12);quote.setBackground(rounded(GOLD,GOLD,20));quote.setTextColor(Color.rgb(30,34,31));LinearLayout.LayoutParams actionLp1=new LinearLayout.LayoutParams(0,dp(60),1),actionLp2=new LinearLayout.LayoutParams(0,dp(60),1);actions.addView(list,actionLp1);spacerH(actions,8);actions.addView(quote,actionLp2);shell.addView(actions,new LinearLayout.LayoutParams(-1,dp(76)));
        list.setOnClickListener(v->{ackDriverAlert(r);dialog.dismiss();expandedId=r.optString("id","");if(!historyMode)renderRows(false);showNextDriverAlertIfAny();});
        quote.setOnClickListener(v->{ackDriverAlert(r);dialog.dismiss();expandedId=r.optString("id","");showQuoteDialog(r);showNextDriverAlertIfAny();});
        dialog.setOnDismissListener(x->{if(driverAlertDialog==dialog)driverAlertDialog=null;});dialog.setContentView(shell);
        dialog.setOnShowListener(d->{Window dw=dialog.getWindow();if(dw!=null){dw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dw.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=dw.getAttributes();a.dimAmount=.68f;dw.setAttributes(a);int width=(int)(getResources().getDisplayMetrics().widthPixels*.92f);int height=(int)(getResources().getDisplayMetrics().heightPixels*.74f);dw.setLayout(width,height);dw.setGravity(Gravity.CENTER);}});dialog.show();
    }

    private void ackDriverAlert(JSONObject r){String id=r.optString("id",r.optString("code",""));for(int i=driverAlertQueue.size()-1;i>=0;i--){JSONObject x=driverAlertQueue.get(i);String k=x.optString("id",x.optString("code",""));if(id.equals(k)){driverAlertQueue.remove(i);driverAlertQueuedIds.remove(k);if(i<driverAlertIndex)driverAlertIndex--;break;}}if(driverAlertIndex>=driverAlertQueue.size())driverAlertIndex=Math.max(0,driverAlertQueue.size()-1);}
    private void showNextDriverAlertIfAny(){if(driverAlertQueue.isEmpty())return;handler.postDelayed(()->showDriverAlertAt(driverAlertIndex),140);}

    private boolean claimNewReservationCue(String id){if(id==null||id.isEmpty())return true;synchronized(Api.class){android.content.SharedPreferences p=getSharedPreferences("driver_alert_delivery",MODE_PRIVATE);String key="new_"+id;long now=System.currentTimeMillis(),at=p.getLong(key,0);if(at>0&&now-at<6*60*60*1000L)return false;p.edit().putLong(key,now).putString("last_id",id).putLong("last_at",now).apply();return true;}}

    private void playWarmChime(){
        android.content.SharedPreferences ap=getSharedPreferences("driver_alert_settings",MODE_PRIVATE);
        if(ap.getBoolean("new_sound",true)){try{android.media.MediaPlayer mp=android.media.MediaPlayer.create(this,R.raw.reserva_calida);if(mp!=null){mp.setVolume(.7f,.7f);mp.setOnCompletionListener(android.media.MediaPlayer::release);mp.start();}}catch(Exception ignored){}}
        if(ap.getBoolean("vibrate",true)){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(android.os.VibrationEffect.createOneShot(170,android.os.VibrationEffect.DEFAULT_AMPLITUDE));else v.vibrate(170);}}catch(Exception ignored){}}
        getSharedPreferences("driver_alert_delivery",MODE_PRIVATE).edit().putBoolean("last_sound",ap.getBoolean("new_sound",true)).putBoolean("last_vibrate",ap.getBoolean("vibrate",true)).putString("last_source","APP").apply();
    }

    private void confirmLogout(){showDriverConfirm("Salir","¿Querés cerrar el acceso del conductor en este teléfono?","VOLVER","SÍ, SALIR",this::logout);}

    private void logout(){
        stopService(new Intent(this,ReservationMonitorService.class));
        getSharedPreferences("driver_session",MODE_PRIVATE).edit().clear().apply();pin="";expandedId="";dashboardVisible=false;currentRows.clear();
        toast("Sesión cerrada");renderAccess(true);
    }

    private void openCall(String phone){
        try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+phone.replace(" ",""))));}catch(Exception e){toast("No se pudo abrir el teléfono");}
    }
    private void openWa(String phone){
        String p=phone.replaceAll("[^0-9]","");if(p.startsWith("0"))p="598"+p.substring(1);
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+p)));}catch(Exception e){toast("No se pudo abrir WhatsApp");}
    }
    private void openRoute(JSONObject r){
        String o,d;
        if(r.has("origin_lat")&&!r.isNull("origin_lat"))o=r.optDouble("origin_lat")+","+r.optDouble("origin_lng");else o=Uri.encode(r.optString("origin_text"));
        if(r.has("destination_lat")&&!r.isNull("destination_lat"))d=r.optDouble("destination_lat")+","+r.optDouble("destination_lng");else d=Uri.encode(r.optString("destination_text"));
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&origin="+o+"&destination="+d+"&travelmode=driving")));}catch(Exception e){toast("No se pudo abrir Maps");}
    }

    private void instalarAyudaTecladoConductor(EditText e){
        e.setImeOptions(e.getImeOptions()|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus)programarCampoVisibleConductor(e);});
        e.setOnClickListener(v->programarCampoVisibleConductor(e));
    }
    private void programarCampoVisibleConductor(View field){if(field==null)return;field.postDelayed(()->asegurarCampoVisibleConductor(field),90);field.postDelayed(()->asegurarCampoVisibleConductor(field),310);}
    private ScrollView scrollPadreConductor(View field){ViewParent p=field==null?null:field.getParent();while(p instanceof View){if(p instanceof ScrollView)return (ScrollView)p;p=p.getParent();}return null;}
    private void asegurarCampoVisibleConductor(View field){if(field==null||!field.isShown())return;ScrollView sc=scrollPadreConductor(field);if(sc==null){field.requestRectangleOnScreen(new Rect(0,0,Math.max(1,field.getWidth()),field.getHeight()+dp(55)),true);return;}Rect vis=new Rect();getWindow().getDecorView().getWindowVisibleDisplayFrame(vis);int[] pos=new int[2];field.getLocationInWindow(pos);int top=pos[1],bottom=top+field.getHeight(),safeBottom=vis.bottom-dp(78),safeTop=vis.top+dp(58);if(bottom>safeBottom)sc.smoothScrollBy(0,bottom-safeBottom);else if(top<safeTop)sc.smoothScrollBy(0,top-safeTop);}

    private ScrollView baseScreen(){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(true);sc.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        sc.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());int dyn=Math.max(0,ime.bottom-safe.bottom);view.setPadding(safe.left,safe.top,safe.right,safe.bottom+dyn);if(dyn>0){View f=getCurrentFocus();if(f instanceof EditText)programarCampoVisibleConductor(f);}}else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        try{BitmapDrawable bg=(BitmapDrawable)getDrawable(R.drawable.bg_texture);bg=(BitmapDrawable)bg.mutate();bg.setTileModeX(Shader.TileMode.REPEAT);bg.setTileModeY(Shader.TileMode.REPEAT);sc.setBackground(bg);}catch(Exception e){sc.setBackgroundColor(BG);}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(14),dp(14),dp(24));sc.addView(box,new ScrollView.LayoutParams(-1,-2));return sc;
    }
    private LinearLayout contentOf(ScrollView sc){return (LinearLayout)sc.getChildAt(0);}

    private void addBrand(LinearLayout box){
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(12),dp(10),dp(12),dp(10));
        hero.setBackground(rounded(Color.rgb(5,35,43),Color.rgb(55,100,111),20));
        hero.setElevation(dp(4));

        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.app_icon);logo.setScaleType(ImageView.ScaleType.CENTER_CROP);logo.setBackground(rounded(PANEL_2,GOLD_DARK,16));logo.setClipToOutline(true);row.addView(logo,new LinearLayout.LayoutParams(dp(64),dp(64)));

        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(11),0,0,0);row.addView(col,cp);
        TextView brand=heading("Traslados Conductor",22);col.addView(brand);
        TextView privateLine=body("Acceso privado del conductor",11,MUTED);col.addView(privateLine,lpMatch(-2,1,0));
        TextView serviceLine=body("Aeropuerto · Programados · Larga distancia",10,GOLD);col.addView(serviceLine);
        TextView versionLine=body("v11.4 · R10.3 · build 121",9,MUTED);col.addView(versionLine,lpMatch(-2,2,0));
        hero.addView(row);

        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(8),0,0);
        gpsStateChip=miniChip("GPS · …",MUTED);onlineStateChip=miniChip(isNetworkAvailable()?"ONLINE":"SIN RED",isNetworkAvailable()?GREEN:RED);monitorStateChip=miniChip("MONITOR",dashboardVisible?GREEN:MUTED);
        meta.addView(gpsStateChip,new LinearLayout.LayoutParams(0,dp(28),1));spacerH(meta,5);meta.addView(onlineStateChip,new LinearLayout.LayoutParams(0,dp(28),1));spacerH(meta,5);meta.addView(monitorStateChip,new LinearLayout.LayoutParams(0,dp(28),1));hero.addView(meta);
        box.addView(hero,lpMatch(-2,0,4));
    }

    private TextView miniChip(String text,int color){
        TextView t=body(text,9,color);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER);t.setBackground(rounded(Color.argb(28,Color.red(color),Color.green(color),Color.blue(color)),color,14));return t;
    }
    private void setMiniChipState(TextView chip,String text,int color){if(chip==null)return;chip.setText(text);chip.setTextColor(color);chip.setBackground(rounded(Color.argb(28,Color.red(color),Color.green(color),Color.blue(color)),color,14));}
    private void updateStatusStrip(){boolean net=isNetworkAvailable();setMiniChipState(onlineStateChip,net?"ONLINE":"SIN RED",net?GREEN:RED);setMiniChipState(monitorStateChip,dashboardVisible?"MONITOR":"PAUSA",dashboardVisible?GREEN:MUTED);}
    private void applyActionIcon(Button b,int res){if(b==null)return;try{b.setCompoundDrawablesRelativeWithIntrinsicBounds(res,0,0,0);b.setCompoundDrawablePadding(dp(5));}catch(Exception ignored){}}

    private void addIntro(LinearLayout box,String title,String sub){TextView t=heading(title,27);box.addView(t,lpMatch(-2,14,0));TextView s=body(sub,14,GOLD);box.addView(s,lpMatch(-2,0,10));}
    private void addNote(LinearLayout box,String text){LinearLayout note=horizontalCard(PANEL_2,LINE,16);note.setPadding(dp(12),dp(12),dp(12),dp(12));note.addView(icon("info",GOLD),new LinearLayout.LayoutParams(dp(34),dp(34)));TextView t=body(text,13,MUTED);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(8),0,0,0);note.addView(t,tp);box.addView(note,lpMatch(-2,0,20));}

    private EditText addField(LinearLayout box,String iconType,String label,String hint){
        LinearLayout card=horizontalCard(PANEL_2,LINE,18);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.addView(icon(iconType,GOLD),new LinearLayout.LayoutParams(dp(40),dp(44)));
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.addView(body(label,12,MUTED));EditText e=new EditText(this);e.setTextColor(TEXT);e.setHintTextColor(Color.rgb(135,151,155));e.setHint(hint);e.setTextSize(17);e.setSingleLine(true);e.setBackgroundColor(Color.TRANSPARENT);e.setPadding(0,0,0,0);instalarAyudaTecladoConductor(e);col.addView(e,new LinearLayout.LayoutParams(-1,-2));card.addView(col,new LinearLayout.LayoutParams(0,-2,1));box.addView(card,lpMatch(-2,0,8));return e;
    }

    private LinearLayout panelCard(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(rounded(PANEL,LINE,18));c.setElevation(dp(3));return c;}
    private LinearLayout horizontalCard(int fill,int stroke,int radius){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setBackground(rounded(fill,stroke,radius));return c;}
    private View loadingCard(String s){LinearLayout c=panelCard();ProgressBar p=new ProgressBar(this);c.addView(p,new LinearLayout.LayoutParams(dp(38),dp(38)));c.addView(body(s,14,MUTED),lpMatch(-2,8,2));return c;}
    private View emptyCard(String s){LinearLayout c=panelCard();TextView t=body(s,16,MUTED);t.setGravity(Gravity.CENTER);c.addView(t,lpMatch(dp(60),4,4));return c;}

    private void addDetail(LinearLayout card,String iconType,String label,String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(9),0,dp(9));row.addView(icon(iconType,GOLD),new LinearLayout.LayoutParams(dp(36),dp(36)));LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.addView(body(label,11,MUTED));col.addView(body(value,15,TEXT));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(6),0,0,0);row.addView(col,cp);card.addView(row);
    }
    private void addRouteBlock(LinearLayout card,String iconType,String label,String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);row.setPadding(0,dp(7),0,dp(7));row.addView(icon(iconType,GOLD),new LinearLayout.LayoutParams(dp(36),dp(36)));LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);TextView l=body(label,11,GOLD);l.setTypeface(Typeface.DEFAULT,Typeface.BOLD);col.addView(l);col.addView(body(value,15,TEXT));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(6),0,0,0);row.addView(col,cp);card.addView(row);
    }

    private TextView statusBadge(String s){String label=prettyStatus(s);TextView t=body(label,11,statusColor(s));t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER);int c=statusColor(s);t.setBackground(rounded(Color.argb(42,Color.red(c),Color.green(c),Color.blue(c)),c,16));t.setPadding(dp(12),0,dp(12),0);return t;}
    private int statusColor(String s){if("ACEPTADA".equals(s)||"FINALIZADA".equals(s)||"CONFIRMADA".equals(s))return GREEN;if("EN_VIAJE".equals(s)||"PRESUPUESTO_ENVIADO".equals(s))return CYAN;if("RECHAZADA".equals(s)||"CANCELADA".equals(s))return RED;return GOLD;}
    private String prettyStatus(String s){if("PENDIENTE".equals(s))return "NUEVA";if("EN_VIAJE".equals(s))return "EN VIAJE";if("PRESUPUESTO_ENVIADO".equals(s))return "PRESUPUESTO ENVIADO";if("CONFIRMADA".equals(s))return "CONFIRMADO";return s.replace('_',' ');}
    private String displayStatus(JSONObject r){String st=r.optString("status","PENDIENTE"),qs=r.optString("quote_status","SIN_PRESUPUESTO");if("PENDIENTE".equals(st)&&"ENVIADO".equals(qs))return "PRESUPUESTO_ENVIADO";if("ACEPTADA".equals(st)&&"ACEPTADO".equals(qs))return "CONFIRMADA";return st;}

    private Button primaryButton(String text){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(15);b.setTextColor(Color.rgb(15,25,25));b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable gd=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(248,216,128),Color.rgb(213,167,64)});gd.setCornerRadius(dp(18));b.setBackground(gd);b.setPadding(dp(12),0,dp(12),0);return b;}
    private Button secondaryButton(String text){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(12);b.setTextColor(TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(rounded(PANEL_2,GOLD_DARK,16));b.setPadding(dp(5),0,dp(5),0);return b;}
    private Button smallAction(String text){Button b=secondaryButton(text);b.setTextSize(11);return b;}
    private Button dangerButton(String text){Button b=secondaryButton(text);b.setTextColor(RED);b.setBackground(rounded(PANEL_2,RED,16));return b;}

    private TextView heading(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TEXT);t.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));return t;}
    private TextView body(String s,int z,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(color);t.setLineSpacing(0,1.08f);return t;}
    private IconView icon(String type,int color){return new IconView(this,type,color);}
    private GradientDrawable rounded(int fill,int stroke,float radiusDp){GradientDrawable gd=new GradientDrawable();gd.setColor(fill);gd.setCornerRadius(dp((int)radiusDp));if(stroke!=0)gd.setStroke(dp(1),stroke);return gd;}
    private LinearLayout.LayoutParams lpMatch(int height,int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,height);lp.setMargins(0,dp(top),0,dp(bottom));return lp;}
    private void spacerH(LinearLayout row,int v){Space s=new Space(this);row.addView(s,new LinearLayout.LayoutParams(dp(v),1));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String friendly(Exception e){return Api.publicMessage("",e);}
    private String trimTime(String s){return s!=null&&s.length()>=5?s.substring(0,5):s;}
    private String prettyDate(String s){try{if(s!=null&&s.length()>=10)return s.substring(8,10)+"/"+s.substring(5,7)+"/"+s.substring(0,4);}catch(Exception ignored){}return s==null?"":s;}
    private String shortAddress(String s){if(s==null)return "";String a=s.trim();int comma=a.indexOf(',');if(comma>10)a=a.substring(0,comma);return a.length()>46?a.substring(0,43)+"…":a;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}

    @Override public void onBackPressed(){if(historyMode){expandedId="";currentRows.clear();buildDashboard(false);loadActive(true);}else super.onBackPressed();}

    public static class IconView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String type;private final int color;
        public IconView(Context c,String type,int color){super(c);this.type=type;this.color=color;p.setColor(color);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,r=Math.min(w,h)*0.30f;p.setColor(color);p.setStrokeWidth(Math.max(2,Math.min(w,h)*0.075f));p.setStyle(Paint.Style.STROKE);
            if(type.equals("person")){c.drawCircle(cx,cy-r*.55f,r*.38f,p);c.drawArc(cx-r*.65f,cy,cx+r*.65f,cy+r*1.1f,200,140,false,p);}
            else if(type.equals("ticket")){c.drawRoundRect(cx-r,cy-r*.55f,cx+r,cy+r*.55f,r*.08f,r*.08f,p);c.drawLine(cx,cy-r*.55f,cx,cy+r*.55f,p);}
            else if(type.equals("calendar")){c.drawRoundRect(cx-r,cy-r*.75f,cx+r,cy+r*.8f,r*.12f,r*.12f,p);c.drawLine(cx-r*.5f,cy-r,cx-r*.5f,cy-r*.55f,p);c.drawLine(cx+r*.5f,cy-r,cx+r*.5f,cy-r*.55f,p);c.drawLine(cx-r,cy-r*.25f,cx+r,cy-r*.25f,p);}
            else if(type.equals("comment")){c.drawRoundRect(cx-r,cy-r*.7f,cx+r,cy+r*.55f,r*.12f,r*.12f,p);c.drawLine(cx-r*.55f,cy-r*.2f,cx+r*.55f,cy-r*.2f,p);c.drawLine(cx-r*.55f,cy+r*.15f,cx+r*.25f,cy+r*.15f,p);}
            else if(type.equals("pin")){c.drawCircle(cx,cy-r*.2f,r*.45f,p);Path q=new Path();q.moveTo(cx-r*.45f,cy);q.quadTo(cx,cy+r*1.2f,cx+r*.45f,cy);c.drawPath(q,p);c.drawCircle(cx,cy-r*.2f,r*.12f,p);}
            else if(type.equals("flag")){c.drawLine(cx-r*.6f,cy-r*.8f,cx-r*.6f,cy+r*.9f,p);Path q=new Path();q.moveTo(cx-r*.55f,cy-r*.75f);q.lineTo(cx+r*.75f,cy-r*.45f);q.lineTo(cx-r*.55f,cy-.05f);q.close();c.drawPath(q,p);}
            else if(type.equals("info")){c.drawCircle(cx,cy,r,p);c.drawLine(cx,cy-r*.12f,cx,cy+r*.55f,p);p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy-r*.55f,r*.09f,p);}
            else if(type.equals("gps")){c.drawCircle(cx,cy,r*.78f,p);c.drawCircle(cx,cy,r*.23f,p);c.drawLine(cx-r*1.05f,cy,cx-r*.65f,cy,p);c.drawLine(cx+r*.65f,cy,cx+r*1.05f,cy,p);c.drawLine(cx,cy-r*1.05f,cx,cy-r*.65f,p);c.drawLine(cx,cy+r*.65f,cx,cy+r*1.05f,p);}
            else c.drawCircle(cx,cy,r*.55f,p);
        }
    }


    // v10.5: recibe el toque del globito incluso si la Activity ya estaba viva.
    @Override protected void onNewIntent(android.content.Intent intent){
        super.onNewIntent(intent);setIntent(intent);captureDriverBubbleIntent(intent);
        if(driverBubbleOpenRequested&&!pin.isEmpty()){
  historyMode=false;
  if(!driverBubbleReservationId.isEmpty())expandedId=driverBubbleReservationId;
  driverBubbleOpenRequested=false;
  loadActive(true);
        }
    }

    private void captureDriverBubbleIntent(android.content.Intent intent){
        if(intent==null)return;
        if(intent.getBooleanExtra("open_requests",false)){
  driverBubbleOpenRequested=true;
  driverBubbleReservationId=intent.getStringExtra("reservation_id")==null?"":intent.getStringExtra("reservation_id");
        }
    }

    private void maybeAskDriverBubblePermission(){
        android.content.SharedPreferences p=getSharedPreferences("driver_bubble",MODE_PRIVATE);
        if(android.provider.Settings.canDrawOverlays(this)){
  if(!p.getBoolean("bubble_enabled",false))p.edit().putBoolean("bubble_enabled",true).apply();
  return;
        }
        if(p.getBoolean("permission_prompted",false))return;
        p.edit().putBoolean("permission_prompted",true).apply();
        showDriverConfirm("Globito del conductor","Para seguir viendo solicitudes y novedades cuando minimices la app, activá ‘Mostrar sobre otras aplicaciones’. El globito es opcional y podés moverlo por la pantalla.","AHORA NO","ACTIVAR",()->{p.edit().putBoolean("bubble_requested",true).apply();android.content.Intent i=new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName()));startActivity(i);});
    }

    private void sendDriverBubbleAction(String action){
        if(pin==null||pin.isEmpty())return;
        if(!android.provider.Settings.canDrawOverlays(this))return;
        android.content.SharedPreferences p=getSharedPreferences("driver_bubble",MODE_PRIVATE);
        if(!p.getBoolean("bubble_enabled",false))return;
        android.content.Intent i=new android.content.Intent(this,ReservationMonitorService.class);i.setAction(action);
        try{if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception ignored){}
    }


    // v10.6: control compacto y deliberado para iniciar/finalizar el viaje.
    // Inspirado en el gesto de deslizamiento de apps de movilidad, pero ajustado
    // a la escala visual de nuestras tarjetas.
    private android.view.View tripSlideControl(org.json.JSONObject reservation,boolean finishMode){
        TripSlideControl c=new TripSlideControl(this,reservation,finishMode);
        c.setContentDescription(finishMode?"Deslizar para finalizar viaje":"Deslizar para iniciar viaje");
        return c;
    }

    private class TripSlideControl extends android.view.View{
        private final org.json.JSONObject reservation;
        private final boolean finishMode;
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint stroke=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float knobLeft=-1f,downX,startLeft;
        private boolean dragging=false,locked=false;

        TripSlideControl(android.content.Context context,org.json.JSONObject r,boolean finish){
            super(context);reservation=r;finishMode=finish;
            setClickable(true);setFocusable(true);
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);
        }

        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            if(knobLeft<0)knobLeft=slidePad();
        }

        private float slidePad(){return dp(4);}
        private float knobWidth(){return dp(48);}
        private float maxLeft(){return Math.max(slidePad(),getWidth()-slidePad()-knobWidth());}

        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight(),radius=dp(17);
            android.graphics.RectF track=new android.graphics.RectF(0,0,w,h);
            int c1=finishMode?android.graphics.Color.rgb(225,86,96):android.graphics.Color.rgb(57,196,153);
            int c2=finishMode?android.graphics.Color.rgb(176,54,65):android.graphics.Color.rgb(29,151,118);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setShader(new android.graphics.LinearGradient(0,0,w,0,c1,c2,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(track,radius,radius,paint);paint.setShader(null);

            // Suave brillo superior: da volumen sin convertirlo en un control pesado.
            paint.setColor(android.graphics.Color.argb(finishMode?24:32,255,255,255));
            canvas.drawRoundRect(new android.graphics.RectF(dp(2),dp(2),w-dp(2),h*.49f),radius,radius,paint);

            stroke.setStyle(android.graphics.Paint.Style.STROKE);stroke.setStrokeWidth(dp(1));
            stroke.setColor(android.graphics.Color.argb(105,255,255,255));
            canvas.drawRoundRect(new android.graphics.RectF(dp(1),dp(1),w-dp(1),h-dp(1)),radius,radius,stroke);

            // Texto central, contenido y limpio.
            paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD));
            paint.setTextSize(14f*getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setColor(finishMode?android.graphics.Color.WHITE:android.graphics.Color.rgb(8,49,42));
            String label=finishMode?"Deslizar para finalizar":"Deslizar para iniciar";
            android.graphics.Paint.FontMetrics fm=paint.getFontMetrics();
            float ty=h/2f-(fm.ascent+fm.descent)/2f;
            canvas.drawText(label,w/2f,ty,paint);

            // Tirador oscuro, pequeño y redondeado como pieza separada.
            float top=slidePad(),bottom=h-slidePad();
            android.graphics.RectF knob=new android.graphics.RectF(knobLeft,top,knobLeft+knobWidth(),bottom);
            paint.setColor(finishMode?android.graphics.Color.rgb(83,31,38):android.graphics.Color.rgb(5,66,57));
            canvas.drawRoundRect(knob,dp(14),dp(14),paint);
            stroke.setColor(android.graphics.Color.argb(80,255,255,255));stroke.setStrokeWidth(dp(1));
            canvas.drawRoundRect(knob,dp(14),dp(14),stroke);

            // Flecha de avance.
            float cx=knob.centerX(),cy=knob.centerY();
            stroke.setStyle(android.graphics.Paint.Style.STROKE);stroke.setStrokeCap(android.graphics.Paint.Cap.ROUND);stroke.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            stroke.setStrokeWidth(dp(3));stroke.setColor(finishMode?android.graphics.Color.rgb(255,214,216):android.graphics.Color.rgb(88,218,181));
            android.graphics.Path arrow=new android.graphics.Path();
            arrow.moveTo(cx-dp(9),cy);arrow.lineTo(cx+dp(8),cy);
            arrow.moveTo(cx+dp(2),cy-dp(6));arrow.lineTo(cx+dp(9),cy);arrow.lineTo(cx+dp(2),cy+dp(6));
            canvas.drawPath(arrow,stroke);stroke.setStrokeCap(android.graphics.Paint.Cap.BUTT);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(locked)return true;
            switch(e.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN:
                    if(e.getX()>knobLeft+knobWidth()+dp(14))return false;
                    dragging=true;downX=e.getX();startLeft=knobLeft;getParent().requestDisallowInterceptTouchEvent(true);return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if(!dragging)return false;
                    knobLeft=Math.max(slidePad(),Math.min(maxLeft(),startLeft+(e.getX()-downX)));invalidate();return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    dragging=false;animateKnob(slidePad(),false);getParent().requestDisallowInterceptTouchEvent(false);return true;
                case android.view.MotionEvent.ACTION_UP:
                    if(!dragging)return false;dragging=false;getParent().requestDisallowInterceptTouchEvent(false);
                    float travel=Math.max(1f,maxLeft()-slidePad());
                    boolean commit=(knobLeft-slidePad())/travel>=.72f;
                    animateKnob(commit?maxLeft():slidePad(),commit);return true;
            }
            return super.onTouchEvent(e);
        }

        private void animateKnob(float target,boolean commit){
            android.animation.ValueAnimator va=android.animation.ValueAnimator.ofFloat(knobLeft,target);
            va.setDuration(commit?135:180);va.setInterpolator(new android.view.animation.DecelerateInterpolator());
            va.addUpdateListener(a->{knobLeft=(Float)a.getAnimatedValue();invalidate();});
            if(commit)va.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){completeSlide();}});
            va.start();
        }

        private void completeSlide(){
            if(locked)return;locked=true;
            try{performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);}catch(Exception ignored){}
            changeStatus(reservation,finishMode?"FINALIZADA":"EN_VIAJE");
            // Si la red falla, el control vuelve a quedar utilizable para reintentar.
            postDelayed(()->{if(isAttachedToWindow()){locked=false;knobLeft=slidePad();invalidate();}},2200);
        }
    }


    private boolean isNetworkAvailable(){try{android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return true;android.net.Network n=cm.getActiveNetwork();if(n==null)return false;android.net.NetworkCapabilities c=cm.getNetworkCapabilities(n);return c!=null&&(c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)||c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)||c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR));}catch(Exception e){return true;}}

    private void openNavigation(String address){if(address==null||address.trim().isEmpty()){toast("No hay dirección disponible");return;}try{android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("geo:0,0?q="+android.net.Uri.encode(address)));i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception e){toast("No encontré una app de navegación");}}

    private long pickupMillis(JSONObject r){try{String d=r.optString("pickup_date","")+" "+trimTime(r.optString("pickup_time",""));java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);f.setLenient(false);java.util.Date x=f.parse(d);return x==null?0:x.getTime();}catch(Exception e){return 0;}}
    private String relativePickup(JSONObject r){long t=pickupMillis(r),now=System.currentTimeMillis();if(t<=0)return trimTime(r.optString("pickup_time",""));long m=Math.round((t-now)/60000.0);if(m>=0&&m<60)return "en "+m+" min";if(m>=60&&m<1440)return "en "+(m/60)+" h "+(m%60)+" min";if(m>=1440&&m<2880)return "mañana · "+trimTime(r.optString("pickup_time",""));return prettyDate(r.optString("pickup_date"))+" · "+trimTime(r.optString("pickup_time",""));}
    private boolean isConfirmedTrip(JSONObject r){String st=r.optString("status","");return "ACEPTADA".equals(st)||"CONFIRMADA".equals(st);}
    private View nextTripBanner(ArrayList<JSONObject> rows){
        JSONObject best=null;long bestAt=Long.MAX_VALUE,now=System.currentTimeMillis()-30*60000L;
        for(JSONObject r:rows){if(!isConfirmedTrip(r)||"EN_VIAJE".equals(r.optString("status")))continue;long at=pickupMillis(r);if(at>=now&&at<bestAt){bestAt=at;best=r;}}
        if(best==null)return null;final JSONObject chosen=best;String id=chosen.optString("id","");
        android.content.SharedPreferences rp=getSharedPreferences("driver_trip_reminder",MODE_PRIVATE);String rid=rp.getString("reservation_id","");String level=rp.getString("level","");long rat=rp.getLong("at",0);boolean reminder=id.equals(rid)&&System.currentTimeMillis()-rat<90*60000L;
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(10),dp(14),dp(10));c.setBackground(rounded(Color.argb(reminder?72:42,224,193,111),reminder?GOLD:GOLD_DARK,18));
        String top=reminder&&"LEAVE".equals(level)?"HORA DE SALIR 🔔":(reminder?"PRÓXIMO VIAJE 🔔":"PRÓXIMO VIAJE");
        TextView a=body(top+" · "+relativePickup(chosen),11,GOLD);a.setTypeface(Typeface.DEFAULT,Typeface.BOLD);a.setTag("next_trip_title");c.addView(a);
        TextView b=body(trimTime(chosen.optString("pickup_time",""))+" · "+shortAddress(chosen.optString("destination_text","")),15,TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(b,lpMatch(-2,2,0));
        if(reminder){int travel=rp.getInt("travel_min",0),leaveIn=rp.getInt("leave_in_min",0);String info=travel>0?"Llegar al origen: aprox. "+travel+" min · ":"";info+=("LEAVE".equals(level)?"salí ahora":(leaveIn<=1?"conviene salir ahora":"conviene salir en "+leaveIn+" min"));TextView hint=body(info,11,"LEAVE".equals(level)?Color.rgb(255,218,128):GOLD);hint.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(hint,lpMatch(-2,4,0));}
        c.setOnClickListener(v->{expandedId=chosen.optString("id","");renderRows(false);});
        if(reminder){String pulseKey=id+":"+level+":"+rat;if(!pulseKey.equals(lastPulsedTripReminder)){lastPulsedTripReminder=pulseKey;c.postDelayed(()->{c.requestRectangleOnScreen(new Rect(0,0,Math.max(1,c.getWidth()),c.getHeight()+dp(28)),true);pulseNextTripBanner(c,0);},180);}}
        return c;
    }
    private void pulseNextTripBanner(View v,int step){if(v==null||step>=6)return;v.animate().alpha(step%2==0?.58f:1f).setDuration(230).withEndAction(()->pulseNextTripBanner(v,step+1)).start();}

    private double driverPickupKm(JSONObject r){String key=r.optString("id",r.optString("code",""));return Double.longBitsToDouble(getSharedPreferences("pricing",MODE_PRIVATE).getLong("pickup_km_"+key,Double.doubleToLongBits(0)));}
    private String driverPickupKmValue(JSONObject r){double v=driverPickupKm(r);return v>0?String.format(Locale.US,"%.1f",v):"";}
    private double estimatePickupDistanceKm(JSONObject r){double lat=r.optDouble("origin_lat",Double.NaN),lng=r.optDouble("origin_lng",Double.NaN);if(Double.isNaN(lat)||Double.isNaN(lng)){toast("La reserva no tiene coordenadas de origen");return -1;}if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},4411);toast("Autorizá ubicación y volvé a tocar GPS");return -1;}try{android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);android.location.Location best=null;for(String provider:lm.getProviders(true)){try{android.location.Location l=lm.getLastKnownLocation(provider);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}catch(SecurityException ignored){}}if(best==null){toast("Todavía no hay una ubicación GPS disponible");return -1;}float[] out=new float[1];android.location.Location.distanceBetween(best.getLatitude(),best.getLongitude(),lat,lng,out);return Math.max(0,out[0]/1000.0);}catch(Exception e){toast("No pude estimar la distancia al origen");return -1;}}

    private void playTripActionCue(boolean finish){android.content.SharedPreferences ap=getSharedPreferences("driver_alert_settings",MODE_PRIVATE);if(ap.getBoolean("trip_sound",true)){try{final android.media.ToneGenerator tg=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,68);tg.startTone(finish?android.media.ToneGenerator.TONE_PROP_ACK:android.media.ToneGenerator.TONE_PROP_BEEP2,finish?210:150);handler.postDelayed(tg::release,380);}catch(Exception ignored){}}if(ap.getBoolean("vibrate",true)){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){long ms=finish?170:110;if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(android.os.VibrationEffect.createOneShot(ms,android.os.VibrationEffect.DEFAULT_AMPLITUDE));else v.vibrate(ms);}}catch(Exception ignored){}}}

    private void showTripFinishSummary(JSONObject r){
        JSONObject t=TripTelemetryService.summary(this,r.optString("id",""));double realKm=t.optDouble("distance_km",0),routeKm=r.optDouble("route_distance_km",0),pickupKm=driverPickupKm(r),opKm=(realKm>0?realKm:routeKm)+pickupKm,total=r.optDouble("quote_final_total",0),tolls=r.optDouble("quote_tolls",0);
        StringBuilder b=new StringBuilder();if(t.optLong("started_at",0)>0){b.append("GPS real: ").append(formatDistance(realKm));b.append("\nDuración: ").append(TripTelemetryService.fmtDuration(t.optLong("elapsed_ms",0)));b.append("\nEn movimiento: ").append(TripTelemetryService.fmtDuration(t.optLong("moving_ms",0)));b.append("\nDetenido: ").append(TripTelemetryService.fmtDuration(t.optLong("stopped_ms",0)));long gap=t.optLong("signal_gap_ms",0);if(gap>15000)b.append("\nSin señal/intervalos no medidos: ").append(TripTelemetryService.fmtDuration(gap));b.append("\nVelocidad media en movimiento: ").append(String.format(Locale.US,"%.1f km/h",t.optDouble("avg_moving_speed_kmh",0)));b.append("\nVelocidad máxima GPS: ").append(String.format(Locale.US,"%.1f km/h",t.optDouble("max_speed_kmh",0)));b.append("\nPuntos GPS: ").append(t.optInt("points",0));if(t.optInt("radar_alerts",0)>0)b.append("\nAvisos de cámaras OSM: ").append(t.optInt("radar_alerts",0));}
        else if(routeKm>0)b.append("Viaje estimado: ").append(formatDistance(routeKm));
        if(pickupKm>0)b.append("\nHasta origen: ").append(formatDistance(pickupKm)).append(" aprox.");if(opKm>0)b.append("\nKm operativos: ").append(formatDistance(opKm));if(total>0)b.append("\nImporte: ").append(formatMoney(total));if(tolls>0)b.append("\nPeajes: ").append(formatMoney(tolls));if(total>0&&opKm>0)b.append("\nIngreso por km operativo: ").append(formatMoney(total/opKm));long elapsed=t.optLong("elapsed_ms",0);if(total>0&&elapsed>60000)b.append("\nIngreso por hora de viaje: ").append(formatMoney(total/(elapsed/3600000.0))).append("/h");
        final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("✓ VIAJE FINALIZADO",b.length()>0?b.toString():"La reserva pasó al historial.");if(t.optBoolean("trace_exists",false)){Button trace=secondaryButton("VER TRAZA GPS");trace.setOnClickListener(v->{d.dismiss();showTelemetryDialog(r);});shell.addView(trace,lpMatch(dp(50),5,3));}Button ok=primaryButton("LISTO");ok.setOnClickListener(v->d.dismiss());shell.addView(ok,lpMatch(dp(52),7,0));showDriverDialog(d,shell,.92f,.74f);
    }

    static class GpsPreflight{boolean permission,locationEnabled,serviceDeclared,backgroundRestricted;boolean ready(){return permission&&locationEnabled&&serviceDeclared;}}
    private GpsPreflight leerGpsPreflight(){GpsPreflight g=new GpsPreflight();g.permission=Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;try{android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);g.locationEnabled=lm!=null&&(Build.VERSION.SDK_INT>=28?lm.isLocationEnabled():(lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)||lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)));}catch(Exception e){g.locationEnabled=false;}try{getPackageManager().getServiceInfo(new ComponentName(this,TripTelemetryService.class),0);g.serviceDeclared=true;}catch(Exception e){g.serviceDeclared=false;}try{if(Build.VERSION.SDK_INT>=28){android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);g.backgroundRestricted=am!=null&&am.isBackgroundRestricted();}}catch(Exception ignored){}return g;}
    private void actualizarGpsPreflightSilencioso(){GpsPreflight g=leerGpsPreflight();String detail,chip;int color;if(!g.permission){detail="⚠ PERMISO DE UBICACIÓN NECESARIO · CORREGIR";chip="GPS · PERMISO";color=Color.rgb(235,170,80);}else if(!g.locationEnabled){detail="⚠ GPS DESACTIVADO · ACTIVAR";chip="GPS · OFF";color=Color.rgb(235,170,80);}else if(!g.serviceDeclared){detail="⚠ SERVICIO GPS NO DISPONIBLE";chip="GPS · ERROR";color=RED;}else if(g.backgroundRestricted){detail="● GPS LISTO · Android restringe segundo plano";chip="GPS · LIMITADO";color=Color.rgb(235,170,80);}else{detail="● GPS LISTO";chip="GPS · LISTO";color=GREEN;}if(gpsPreflightStatus!=null){gpsPreflightStatus.setText(detail);gpsPreflightStatus.setTextColor(color);}setMiniChipState(gpsStateChip,chip,color);updateStatusStrip();}
    private void corregirGpsPreflight(){GpsPreflight g=leerGpsPreflight();if(!g.permission){if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},4412);return;}if(!g.locationEnabled){showDriverConfirm("ACTIVAR UBICACIÓN","Para registrar kilómetros y recorrido, Android debe tener Ubicación/GPS encendido.","CANCELAR","ABRIR AJUSTES",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception e){}});return;}if(g.backgroundRestricted){showDriverConfirm("GPS LISTO CON ADVERTENCIA","La ubicación está disponible, pero Android marca esta app como restringida en segundo plano. El servicio seguirá registrando como servicio en primer plano.","CERRAR","AJUSTES DE APP",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){}});return;}toast("● GPS LISTO");}
    private boolean asegurarGpsAntesDeViaje(){GpsPreflight g=leerGpsPreflight();actualizarGpsPreflightSilencioso();if(g.ready())return true;if(!g.permission){if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},4412);toast("Autorizá ubicación precisa y volvé a deslizar para iniciar");return false;}if(!g.locationEnabled){showDriverConfirm("GPS DESACTIVADO","No voy a iniciar el viaje sin telemetría. Activá Ubicación/GPS y luego volvé a deslizar.","CANCELAR","ACTIVAR GPS",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception e){}});return false;}showDriverInfo("GPS NO DISPONIBLE","Android no encuentra el servicio de telemetría. El viaje no se iniciará sin registro GPS.");return false;}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==4412)handler.postDelayed(this::actualizarGpsPreflightSilencioso,180);}

    private boolean hasTripLocationPermission(){return android.os.Build.VERSION.SDK_INT<23||checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private boolean startTripTelemetry(JSONObject r){if(!hasTripLocationPermission()){toast("Falta permiso de ubicación para registrar el viaje");return false;}String id=r.optString("id",""),code=r.optString("code","");if(id.isEmpty()){toast("No pude identificar la reserva para el GPS");return false;}TripTelemetryService.prepareStart(this,id,code);android.content.Intent i=new android.content.Intent(this,TripTelemetryService.class);i.setAction(TripTelemetryService.ACTION_START);i.putExtra("reservation_id",id);i.putExtra("code",code);try{if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);return true;}catch(Exception e){TripTelemetryService.markLaunchError(this,id,"Android rechazó el servicio GPS: "+friendly(e));toast("No pude iniciar el registro GPS: "+friendly(e));return false;}}
    private void stopTripTelemetry(JSONObject r){android.content.Intent i=new android.content.Intent(this,TripTelemetryService.class);i.setAction(TripTelemetryService.ACTION_STOP);i.putExtra("reservation_id",r.optString("id",""));try{startService(i);}catch(Exception e){toast("El viaje cerró, pero no pude cerrar el GPS: "+friendly(e));}}
    private void abortTripTelemetry(JSONObject r){android.content.Intent i=new android.content.Intent(this,TripTelemetryService.class);i.setAction(TripTelemetryService.ACTION_ABORT);i.putExtra("reservation_id",r.optString("id",""));try{startService(i);}catch(Exception ignored){}}
    private void verifyTripTelemetryStart(JSONObject r,int attempt){JSONObject t=TripTelemetryService.summary(this,r.optString("id",""));String state=t.optString("state","");if(TripTelemetryService.STATE_ACTIVE.equals(state)&&t.optBoolean("active",false)){toast("● GPS REGISTRANDO · "+t.optInt("points",0)+" puntos · "+formatDistance(t.optDouble("distance_km",0)));return;}if((TripTelemetryService.STATE_STARTING.equals(state)||state.isEmpty())&&attempt<2){handler.postDelayed(()->verifyTripTelemetryStart(r,attempt+1),1200);return;}String err=t.optString("last_error","");if(err.isEmpty())err="El servicio GPS no confirmó el inicio";final String msg=err;showDriverConfirm("⚠ GPS NO ESTÁ REGISTRANDO",msg+"\n\nEl viaje quedó iniciado, pero no voy a ocultar el problema. Podés reintentar el GPS ahora.","CERRAR","REINTENTAR",()->{startTripTelemetry(r);handler.postDelayed(()->verifyTripTelemetryStart(r,0),700);});}
    private void ensureTelemetryForActiveTrip(ArrayList<JSONObject> rows){if(!hasTripLocationPermission())return;for(JSONObject r:rows)if("EN_VIAJE".equals(r.optString("status"))){JSONObject t=TripTelemetryService.summary(this,r.optString("id",""));String state=t.optString("state","");long age=t.optLong("heartbeat_age_ms",Long.MAX_VALUE);if(!t.optBoolean("active",false)||!TripTelemetryService.STATE_ACTIVE.equals(state)||age>20000)startTripTelemetry(r);return;}}
    private String telemetryLine(JSONObject t){String state=t.optString("state","");if(TripTelemetryService.STATE_ERROR.equals(state)){String e=t.optString("last_error","GPS sin registro");return e.length()>70?e.substring(0,67)+"…":e;}StringBuilder x=new StringBuilder();x.append(t.optInt("points",0)).append(" puntos · ").append(formatDistance(t.optDouble("distance_km",0))).append(" · ").append(TripTelemetryService.fmtDuration(t.optLong("elapsed_ms",0)));if(TripTelemetryService.STATE_ACTIVE.equals(state))x.append(" · ").append(String.format(Locale.US,"%.0f km/h",t.optDouble("current_speed_kmh",0)));x.append(" · detenido ").append(TripTelemetryService.fmtDuration(t.optLong("stopped_ms",0)));return x.toString();}
    private void showTelemetryDialog(JSONObject r){String id=r.optString("id","");JSONObject t=TripTelemetryService.summary(this,id);if(t.optLong("started_at",0)<=0){toast("Todavía no hay telemetría para esta reserva");return;}String state=t.optString("state","");StringBuilder b=new StringBuilder();b.append("Estado: ").append(state.isEmpty()?"SIN DIAGNÓSTICO":state);if(t.optInt("providers",0)>0)b.append(" · ").append(t.optInt("providers",0)).append(" proveedor(es)");String err=t.optString("last_error","");if(!err.isEmpty())b.append("\nDiagnóstico: ").append(err);long hbAge=t.optLong("heartbeat_age_ms",Long.MAX_VALUE);if(hbAge<Long.MAX_VALUE/2)b.append("\nÚltima señal del servicio: hace ").append(TripTelemetryService.fmtDuration(hbAge));b.append("\n\nDistancia real: ").append(formatDistance(t.optDouble("distance_km",0)));b.append("\nTiempo total: ").append(TripTelemetryService.fmtDuration(t.optLong("elapsed_ms",0)));b.append("\nEn movimiento: ").append(TripTelemetryService.fmtDuration(t.optLong("moving_ms",0)));b.append("\nDetenido: ").append(TripTelemetryService.fmtDuration(t.optLong("stopped_ms",0)));long gap=t.optLong("signal_gap_ms",0);if(gap>15000)b.append("\nSin señal / no medido: ").append(TripTelemetryService.fmtDuration(gap));b.append("\nVelocidad actual: ").append(String.format(Locale.US,"%.1f km/h",t.optDouble("current_speed_kmh",0)));b.append("\nVelocidad media (mov.): ").append(String.format(Locale.US,"%.1f km/h",t.optDouble("avg_moving_speed_kmh",0)));b.append("\nVelocidad máxima: ").append(String.format(Locale.US,"%.1f km/h",t.optDouble("max_speed_kmh",0)));b.append("\nPuntos registrados: ").append(t.optInt("points",0));b.append("\nAvisos de cámaras OSM: ").append(t.optInt("radar_alerts",0));final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("TELEMETRÍA GPS · "+r.optString("code",""),b.toString());LinearLayout actions=new LinearLayout(this);Button export=secondaryButton("EXPORTAR GPX"),map=secondaryButton("VER RECORRIDO");export.setEnabled(t.optInt("points",0)>0);map.setEnabled(t.optInt("points",0)>1);actions.addView(export,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(actions,6);actions.addView(map,new LinearLayout.LayoutParams(0,dp(50),1));shell.addView(actions,lpMatch(dp(54),7,4));Button close=primaryButton("CERRAR");shell.addView(close,lpMatch(dp(52),3,0));export.setOnClickListener(v->{d.dismiss();exportGpx(r);});map.setOnClickListener(v->{d.dismiss();openTelemetryMap(r);});close.setOnClickListener(v->d.dismiss());showDriverDialog(d,shell,.94f,.82f);}
    private void openTelemetryMap(JSONObject r){String u=TripTelemetryService.mapsUrl(this,r.optString("id",""));if(u==null){toast("La traza todavía no tiene suficientes puntos");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){toast("No pude abrir el recorrido");}}
    private void exportGpx(JSONObject r){pendingGpx=TripTelemetryService.buildGpx(this,r.optString("id",""),r.optString("code","Viaje"));if(pendingGpx==null||pendingGpx.isEmpty()){toast("No hay puntos GPS para exportar");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/gpx+xml");i.putExtra(Intent.EXTRA_TITLE,"Traslados_"+r.optString("code","viaje")+".gpx");try{startActivityForResult(i,REQ_EXPORT_GPX);}catch(Exception e){toast("No pude abrir el selector de archivos");}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_EXPORT_GPX&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&!pendingGpx.isEmpty()){try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(pendingGpx.getBytes("UTF-8"));toast("Traza GPX guardada");}catch(Exception e){toast("No pude guardar el GPX");}finally{pendingGpx="";}}}
    private void showTelemetrySettings(){android.content.SharedPreferences p=getSharedPreferences("telemetry_settings",MODE_PRIVATE);final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("GPS Y TELEMETRÍA","Durante EN VIAJE se registra la traza completa en este teléfono, incluso con la app minimizada.");LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);android.widget.CheckBox radar=new android.widget.CheckBox(this);radar.setText("Alertas audibles de cámaras OSM (experimental)");radar.setTextColor(TEXT);radar.setChecked(p.getBoolean("radar_osm",true));box.addView(radar);box.addView(body("Las cámaras se consultan en OpenStreetMap/Overpass y pueden estar incompletas o desactualizadas. El aviso es complementario; respetá siempre la señalización vial.",11,MUTED),lpMatch(-2,4,0));shell.addView(box,new LinearLayout.LayoutParams(-1,0,1));LinearLayout actions=new LinearLayout(this);Button cancel=secondaryButton("CANCELAR"),save=primaryButton("GUARDAR");actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(actions,7);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));shell.addView(actions,lpMatch(dp(56),8,0));cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->{p.edit().putBoolean("radar_osm",radar.isChecked()).apply();toast("Preferencias GPS guardadas");d.dismiss();});showDriverDialog(d,shell,.92f,.64f);}

    private void showDriverAlertSettings(){android.content.SharedPreferences p=getSharedPreferences("driver_alert_settings",MODE_PRIVATE);final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("AVISOS","Elegí qué señales querés recibir. Cada evento importante suena/vibra una sola vez.");ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);sc.addView(box);shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));android.widget.CheckBox n=new android.widget.CheckBox(this);n.setText("Sonido de nueva solicitud");n.setTextColor(TEXT);n.setChecked(p.getBoolean("new_sound",true));android.widget.CheckBox c=new android.widget.CheckBox(this);c.setText("Sonido de cambios del cliente");c.setTextColor(TEXT);c.setChecked(p.getBoolean("change_sound",true));android.widget.CheckBox t=new android.widget.CheckBox(this);t.setText("Sonido al iniciar / finalizar");t.setTextColor(TEXT);t.setChecked(p.getBoolean("trip_sound",true));android.widget.CheckBox r=new android.widget.CheckBox(this);r.setText("Aviso inteligente de próximo viaje");r.setTextColor(TEXT);r.setChecked(p.getBoolean("trip_reminder",true));android.widget.CheckBox v=new android.widget.CheckBox(this);v.setText("Vibración");v.setTextColor(TEXT);v.setChecked(p.getBoolean("vibrate",true));for(CheckBox x:new CheckBox[]{n,c,t,r,v})box.addView(x);EditText prep=availabilityField(box,"Avisarme antes de la hora recomendada de salida (min)",String.valueOf(p.getInt("trip_reminder_lead_min",10)),true);TextView explain=body("Ejemplo: si para llegar con margen deberías salir 09:30 y elegís 10 min, recibirás un aviso de preparación 09:20 y otro HORA DE SALIR a las 09:30.",11,MUTED);box.addView(explain,lpMatch(-2,4,6));Button test=secondaryButton("🧪 PROBAR AVISOS");test.setOnClickListener(x->showTestMode());box.addView(test,lpMatch(dp(48),4,5));LinearLayout actions=new LinearLayout(this);Button cancel=secondaryButton("CANCELAR"),save=primaryButton("GUARDAR");actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(actions,7);actions.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));shell.addView(actions,lpMatch(dp(54),8,0));cancel.setOnClickListener(x->d.dismiss());save.setOnClickListener(x->{p.edit().putBoolean("new_sound",n.isChecked()).putBoolean("change_sound",c.isChecked()).putBoolean("trip_sound",t.isChecked()).putBoolean("trip_reminder",r.isChecked()).putBoolean("vibrate",v.isChecked()).putInt("trip_reminder_lead_min",Math.max(0,Math.min(60,intValue(prep,10)))).apply();toast("Preferencias de avisos guardadas");d.dismiss();});showDriverDialog(d,shell,.92f,.82f);}

    private void showPricingPresetDialog(){android.content.SharedPreferences p=getSharedPreferences("pricing",MODE_PRIVATE);final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("PRESET DE PRESUPUESTO","Valores internos predeterminados. El cliente nunca ve este desglose.");ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);sc.addView(box);EditText rate=quoteNumberField(box,"Referencia habitual por km",bitsValue(p,"price_per_km_bits"));EditText min=quoteNumberField(box,"Mínimo habitual",bitsValue(p,"minimum_bits"));EditText toll=quoteNumberField(box,"Valor habitual por peaje",bitsValue(p,"toll_unit_bits"));EditText wait=quoteNumberField(box,"Espera habitual",bitsValue(p,"waiting_bits"));EditText pickup=quoteNumberField(box,"Extra habitual hasta origen",bitsValue(p,"pickup_extra_bits"));EditText other=quoteNumberField(box,"Otros extras habituales",bitsValue(p,"other_bits"));shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout actions=new LinearLayout(this);Button cancel=secondaryButton("CANCELAR"),save=primaryButton("GUARDAR");actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(actions,7);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));shell.addView(actions,lpMatch(dp(56),8,0));cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->{p.edit().putLong("price_per_km_bits",Double.doubleToRawLongBits(num(rate))).putLong("minimum_bits",Double.doubleToRawLongBits(num(min))).putLong("toll_unit_bits",Double.doubleToRawLongBits(num(toll))).putLong("waiting_bits",Double.doubleToRawLongBits(num(wait))).putLong("pickup_extra_bits",Double.doubleToRawLongBits(num(pickup))).putLong("other_bits",Double.doubleToRawLongBits(num(other))).apply();toast("Preset guardado");d.dismiss();});showDriverDialog(d,shell,.94f,.86f);}
    private String bitsValue(android.content.SharedPreferences p,String key){double v=Double.longBitsToDouble(p.getLong(key,Double.doubleToLongBits(0)));return v>0?String.format(Locale.US,"%.0f",v):"";}


    private LinearLayout driverDialogShell(String title,String subtitle){LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(16),dp(14),dp(16),dp(14));shell.setBackground(rounded(PANEL_2,GOLD_DARK,22));TextView t=body(title,20,GOLD);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);shell.addView(t,lpMatch(-2,0,2));if(subtitle!=null&&!subtitle.isEmpty())shell.addView(body(subtitle,12,MUTED),lpMatch(-2,0,8));return shell;}
    private void showDriverDialog(Dialog d,View content,float widthFrac,float heightFrac){d.setContentView(content);d.setOnShowListener(x->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*widthFrac),heightFrac>0?(int)(getResources().getDisplayMetrics().heightPixels*heightFrac):-2);}});d.show();}
    private void showDriverInfo(String title,String message){final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell(title,message);Button ok=primaryButton("LISTO");ok.setOnClickListener(v->d.dismiss());shell.addView(ok,lpMatch(dp(50),10,0));showDriverDialog(d,shell,.90f,.46f);}
    private void showDriverConfirm(String title,String message,String negative,String positive,Runnable action){final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell(title,message);LinearLayout row=new LinearLayout(this);Button no=secondaryButton(negative),yes=primaryButton(positive);row.addView(no,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(row,7);row.addView(yes,new LinearLayout.LayoutParams(0,dp(50),1));shell.addView(row,lpMatch(dp(54),10,0));no.setOnClickListener(v->d.dismiss());yes.setOnClickListener(v->{d.dismiss();if(action!=null)action.run();});showDriverDialog(d,shell,.90f,.48f);}
    private void hapticTick(){try{Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(32,80));else v.vibrate(32);}}catch(Exception ignored){}}
    private void shareDriverReservation(JSONObject r){try{String text="Traslado "+r.optString("code","")+"\n"+prettyDate(r.optString("pickup_date",""))+" · "+trimTime(r.optString("pickup_time",""))+"\n"+r.optString("origin_text","")+" → "+r.optString("destination_text","");double total=r.optDouble("quote_final_total",0);if(total>0)text+="\nPrecio acordado: "+formatMoney(total);Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"Compartir traslado"));}catch(Exception e){toast("No pude compartir la reserva");}}
    private void addDriverReservationToCalendar(JSONObject r){try{Calendar c=Calendar.getInstance();java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);c.setTime(f.parse(r.optString("pickup_date","")+" "+trimTime(r.optString("pickup_time",""))));long start=c.getTimeInMillis();int mins=Math.max(30,r.optInt("route_duration_min",60));Intent i=new Intent(Intent.ACTION_INSERT);i.setData(CalendarContract.Events.CONTENT_URI);i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start);i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,start+mins*60000L);i.putExtra(CalendarContract.Events.TITLE,"Traslado · "+r.optString("customer_name","Cliente"));i.putExtra(CalendarContract.Events.DESCRIPTION,r.optString("origin_text","")+" → "+r.optString("destination_text","")+"\nReserva "+r.optString("code",""));i.putExtra(CalendarContract.Events.EVENT_LOCATION,r.optString("origin_text",""));startActivity(i);}catch(Exception e){toast("No pude abrir el calendario");}}
    private void showDiagnostics(){final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("DIAGNÓSTICO","Estado técnico de esta instalación. No muestra PIN ni credenciales.");TextView status=body(driverDiagnosticsText("COMPROBANDO"),12,TEXT);status.setPadding(dp(10),dp(10),dp(10),dp(10));status.setBackground(rounded(PANEL,LINE,14));shell.addView(status,new LinearLayout.LayoutParams(-1,0,1));LinearLayout actions=new LinearLayout(this);Button copy=secondaryButton("COPIAR"),logs=secondaryButton("ENVIAR LOGS"),close=primaryButton("CERRAR");actions.addView(copy,new LinearLayout.LayoutParams(0,dp(48),1));spacerH(actions,5);actions.addView(logs,new LinearLayout.LayoutParams(0,dp(48),1));spacerH(actions,5);actions.addView(close,new LinearLayout.LayoutParams(0,dp(48),1));shell.addView(actions,lpMatch(dp(52),8,0));copy.setOnClickListener(v->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico Traslados",status.getText()));toast("Diagnóstico copiado");});logs.setOnClickListener(v->{Api.flushQueuedLogs();toast("Reintentando envío de diagnósticos");handler.postDelayed(()->status.setText(driverDiagnosticsText("REINTENTADO")),900);});close.setOnClickListener(v->d.dismiss());showDriverDialog(d,shell,.94f,.72f);pool.execute(()->{String back="OK";try{Api.getAvailabilitySettings(pin);}catch(Exception e){back="ERROR · "+Api.publicMessage("diagnostic",e);}final String b=back;runOnUiThread(()->status.setText(driverDiagnosticsText(b)));});}
    private String driverDiagnosticsText(String backend){boolean fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;boolean enabled=false;try{android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);enabled=lm!=null&&(lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)||lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));}catch(Exception ignored){}boolean overlay=android.provider.Settings.canDrawOverlays(this);android.content.SharedPreferences ad=getSharedPreferences("driver_alert_delivery",MODE_PRIVATE);long at=ad.getLong("last_at",0);String last=at>0?new java.text.SimpleDateFormat("HH:mm:ss",new Locale("es","UY")).format(new Date(at))+" · sonido "+(ad.getBoolean("last_sound",false)?"✓":"—")+" · vibración "+(ad.getBoolean("last_vibrate",false)?"✓":"—")+" · "+ad.getString("last_source",""):"sin prueba todavía";return "Traslados Conductor · v11.4 R10\nBackend: Supabase propio · "+backend+"\nInternet: "+(isNetworkAvailable()?"OK":"SIN CONEXIÓN")+"\nGPS preciso: "+(fine?"PERMITIDO":"FALTA PERMISO")+" · ubicación: "+(enabled?"ACTIVA":"DESACTIVADA")+"\nGlobito: "+(overlay?"PERMITIDO":"SIN PERMISO")+"\nÚltimo aviso: "+last+"\nLogs pendientes: "+Api.queuedLogCount()+"\nRecordatorio inteligente: "+(getSharedPreferences("driver_alert_settings",MODE_PRIVATE).getBoolean("trip_reminder",true)?"ACTIVO":"DESACTIVADO");}
    private void showTestMode(){final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell("MODO PRUEBA","Simula señales visuales y sonoras sin crear ni modificar reservas reales.");String[] names={"NUEVA SOLICITUD","PRÓXIMO VIAJE","HORA DE SALIR","GPS SIN SEÑAL"};for(String n:names){Button b=secondaryButton(n);b.setTextSize(11);shell.addView(b,lpMatch(dp(48),3,3));b.setOnClickListener(v->{if("PRÓXIMO VIAJE".equals(n)||"HORA DE SALIR".equals(n)){triggerTripReminderTest("HORA DE SALIR".equals(n));}else if("NUEVA SOLICITUD".equals(n)){playDriverTestCue(false);showDriverPreview("NUEVA SOLICITUD","Cliente de prueba · Ciudad de la Costa → Aeropuerto");}else showDriverPreview("GPS SIN SEÑAL","El viaje seguiría visible y ofrecería REINTENTAR GPS. Esto es solo una simulación.");});}Button close=primaryButton("CERRAR");close.setOnClickListener(v->d.dismiss());shell.addView(close,lpMatch(dp(50),8,0));showDriverDialog(d,shell,.90f,.72f);}
    private void playDriverTestCue(boolean urgent){try{MediaPlayer mp=MediaPlayer.create(this,urgent?R.raw.viaje_proximo:R.raw.reserva_calida);if(mp!=null){mp.setOnCompletionListener(MediaPlayer::release);mp.start();}}catch(Exception ignored){}hapticTick();}
    private void showDriverPreview(String title,String message){final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=driverDialogShell(title,message);Button ok=primaryButton("LISTO");ok.setOnClickListener(v->d.dismiss());shell.addView(ok,lpMatch(dp(50),10,0));showDriverDialog(d,shell,.88f,.42f);}
    private void triggerTripReminderTest(boolean leave){playDriverTestCue(true);Intent i=new Intent(this,ReservationMonitorService.class);i.setAction("uy.com.traslados.conductor.TEST_TRIP_REMINDER");i.putExtra("leave",leave);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);toast(leave?"Simulando HORA DE SALIR":"Simulando PRÓXIMO VIAJE");}

    private interface PremiumDateCallback{void picked(Calendar c);}
    private void showPremiumDatePicker(Calendar initial,PremiumDateCallback cb){final Calendar shown=(Calendar)initial.clone(),selected=(Calendar)initial.clone();final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(16),dp(14),dp(16),dp(14));shell.setBackground(rounded(PANEL_2,GOLD_DARK,22));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);Button prev=secondaryButton("‹"),next=secondaryButton("›");TextView title=body("",18,GOLD);title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);head.addView(prev,new LinearLayout.LayoutParams(dp(46),dp(42)));head.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));head.addView(next,new LinearLayout.LayoutParams(dp(46),dp(42)));shell.addView(head);android.widget.GridLayout grid=new android.widget.GridLayout(this);grid.setColumnCount(7);shell.addView(grid,new LinearLayout.LayoutParams(-1,-2));final Runnable[] render=new Runnable[1];render[0]=()->{title.setText(new java.text.SimpleDateFormat("MMMM yyyy",new Locale("es","UY")).format(shown.getTime()).toUpperCase(new Locale("es","UY")));grid.removeAllViews();String[] wd={"LU","MA","MI","JU","VI","SA","DO"};for(String x:wd){TextView tv=body(x,10,MUTED);tv.setGravity(Gravity.CENTER);grid.addView(tv,new android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f)));}Calendar first=(Calendar)shown.clone();first.set(Calendar.DAY_OF_MONTH,1);int blank=(first.get(Calendar.DAY_OF_WEEK)+5)%7;for(int i=0;i<blank;i++){TextView e=body("",12,MUTED);grid.addView(e,new android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f)));}int max=shown.getActualMaximum(Calendar.DAY_OF_MONTH);for(int day=1;day<=max;day++){final int dd=day;TextView tv=body(String.valueOf(day),13,TEXT);tv.setGravity(Gravity.CENTER);tv.setPadding(0,dp(8),0,dp(8));boolean sel=selected.get(Calendar.YEAR)==shown.get(Calendar.YEAR)&&selected.get(Calendar.MONTH)==shown.get(Calendar.MONTH)&&selected.get(Calendar.DAY_OF_MONTH)==day;Calendar now=Calendar.getInstance();boolean today=now.get(Calendar.YEAR)==shown.get(Calendar.YEAR)&&now.get(Calendar.MONTH)==shown.get(Calendar.MONTH)&&now.get(Calendar.DAY_OF_MONTH)==day;if(sel){tv.setTextColor(Color.rgb(20,38,39));tv.setBackground(rounded(GOLD,GOLD,18));}else if(today)tv.setBackground(rounded(Color.TRANSPARENT,GOLD_DARK,18));tv.setOnClickListener(v->{selected.set(shown.get(Calendar.YEAR),shown.get(Calendar.MONTH),dd);render[0].run();});grid.addView(tv,new android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f)));}};prev.setOnClickListener(v->{shown.add(Calendar.MONTH,-1);render[0].run();});next.setOnClickListener(v->{shown.add(Calendar.MONTH,1);render[0].run();});render[0].run();LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.RIGHT);Button cancel=secondaryButton("CANCELAR"),ok=primaryButton("ACEPTAR");actions.addView(cancel,new LinearLayout.LayoutParams(dp(120),dp(48)));spacerH(actions,8);actions.addView(ok,new LinearLayout.LayoutParams(dp(120),dp(48)));shell.addView(actions,lpMatch(dp(54),10,0));cancel.setOnClickListener(v->d.dismiss());ok.setOnClickListener(v->{cb.picked((Calendar)selected.clone());d.dismiss();});d.setContentView(shell);d.setOnShowListener(x->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92f),-2);}});d.show();}
}
