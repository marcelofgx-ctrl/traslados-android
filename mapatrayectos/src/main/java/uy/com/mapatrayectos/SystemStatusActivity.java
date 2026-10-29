package uy.com.mapatrayectos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Read-only, driver-authenticated system status. Never fetches individual
 * customer records, raw GPS positions or secrets; refresh is user-initiated.
 */
public final class SystemStatusActivity extends Activity {
    private static final int BG=Color.rgb(7,25,31), GOLD=Color.rgb(224,193,111);
    private static final int TEXT=Color.rgb(247,246,241), MUTED=Color.rgb(181,200,201);
    private static final int GREEN=Color.rgb(88,211,154), AMBER=Color.rgb(245,184,93);
    private static final int RED=Color.rgb(232,109,109);
    private TextView cloudValue,cloudNote,cloudState,cloudUpdated,cloudRows,localRows,connectivity,versionLine,privateGpsLine;
    private ProgressBar capacityBar;
    private Button reload;
    private String sessionPin="";
    private boolean loading=false;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        Api.init(getApplicationContext());
        sessionPin=DriverIdentity.pin(this);
        buildPage();
        refresh();
    }

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private TextView text(String value,float size,int color,boolean strong){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(strong)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        t.setIncludeFontPadding(false);return t;
    }

    private GradientDrawable rounded(int bg,int border,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(bg);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),border);return g;
    }

    private LinearLayout section(LinearLayout parent,String eyebrow,String heading,String hint){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17),dp(16),dp(17),dp(17));
        card.setBackground(new TexturedDrawable(this,Color.rgb(7,43,51),
            Color.rgb(12,67,72),Color.argb(152,224,193,111),18f,1f,false));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.bottomMargin=dp(13);parent.addView(card,cp);
        TextView eye=text(eyebrow,10,GOLD,true);
        eye.setLetterSpacing(0.11f);
        card.addView(eye);
        TextView name=text(heading,18.5f,TEXT,true);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.topMargin=dp(6);card.addView(name,np);
        if(hint!=null&&!hint.isEmpty()){
            TextView info=text(hint,11.5f,MUTED,false);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);
            ip.topMargin=dp(7);card.addView(info,ip);
        }
        return card;
    }

    private void buildPage(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17),dp(18),dp(17),dp(26));
        scroll.addView(content);
        setContentView(scroll);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(top,new LinearLayout.LayoutParams(-1,-2));
        Button back=button("‹",false);back.setTextSize(25);
        top.addView(back,new LinearLayout.LayoutParams(dp(47),dp(45)));back.setOnClickListener(v->finish());
        LinearLayout title=new LinearLayout(this);title.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(11);
        top.addView(title,tp);
        title.addView(text("MAPA TRAYECTOS",11,GOLD,true));
        title.addView(text("Estado del sistema",23,TEXT,true));
        TextView desc=text("Diagnóstico del teléfono y la nube · datos privados del conductor",11.4f,MUTED,false);
        LinearLayout.LayoutParams descp=new LinearLayout.LayoutParams(-1,-2);
        descp.topMargin=dp(9);descp.bottomMargin=dp(18);content.addView(desc,descp);

        LinearLayout cloud=section(content,"01 · SUPABASE","Almacenamiento en la nube",
            "Uso global aproximado del proyecto PostgreSQL · Plan Free");
        cloudState=text("●  Comprobando acceso…",12,GOLD,true);
        cloud.addView(cloudState,new LinearLayout.LayoutParams(-1,dp(26)));
        cloudValue=text("—",31,TEXT,true);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(7);
        cloud.addView(cloudValue,vp);
        cloudNote=text("Esperando información del servidor",11.6f,MUTED,false);
        LinearLayout.LayoutParams note=new LinearLayout.LayoutParams(-1,-2);note.topMargin=dp(3);
        cloud.addView(cloudNote,note);
        capacityBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        capacityBar.setIndeterminate(false);capacityBar.setMax(1000);capacityBar.setProgress(0);
        capacityBar.setProgressTintList(ColorStateList.valueOf(GREEN));
        capacityBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(32,75,79)));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(10));
        bp.topMargin=dp(14);bp.bottomMargin=dp(14);cloud.addView(capacityBar,bp);
        cloudRows=text("Aguardando consulta con PIN del conductor",12.3f,TEXT,false);
        cloudRows.setLineSpacing(dp(5),1f);cloud.addView(cloudRows);
        cloudUpdated=text("",10.4f,MUTED,false);
        LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-1,-2);up.topMargin=dp(12);
        cloud.addView(cloudUpdated,up);

        LinearLayout local=section(content,"02 · MAPA EN EL TELÉFONO","Base local y GPS",
            "Se muestra lo que está guardado en este dispositivo; puede diferir de Supabase.");
        localRows=text("Leyendo la base local…",12.5f,TEXT,false);
        localRows.setLineSpacing(dp(6),1f);local.addView(localRows);

        LinearLayout status=section(content,"03 · OPERACIÓN","Conectividad y jornada",
            "La disponibilidad real para pasajeros también depende del GPS y la agenda.");
        connectivity=text("Comprobando…",12.5f,TEXT,false);
        connectivity.setLineSpacing(dp(6),1f);status.addView(connectivity);

        LinearLayout privateGps=section(content,"04 · QA GPS PRIVADO","Resultado de publicación",
            "Solo códigos locales de esta ejecución; sin coordenadas, tokens, reservas ni conexión adicional.");
        privateGpsLine=text(PrivateGpsDiagnostic.message(Api.pickupPresenceEnabled()),
            12.3f,TEXT,false);
        privateGpsLine.setLineSpacing(dp(5),1f);
        privateGps.addView(privateGpsLine);

        LinearLayout app=section(content,"05 · APLICACIÓN","Versión y mantenimiento",
            "Solo lectura: no modifica reservas, jornadas, GPS ni configuraciones.");
        versionLine=text("Mapa Trayectos",12.5f,TEXT,false);app.addView(versionLine);

        reload=button("↻  ACTUALIZAR DIAGNÓSTICO",true);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));
        rp.topMargin=dp(2);content.addView(reload,rp);
        reload.setOnClickListener(v->{
            if(sessionPin==null||sessionPin.isEmpty())
                sessionPin=DriverIdentity.pin(this);
            if(sessionPin.isEmpty())promptPin();else refresh();
        });
        TextView privacy=text("Información privada · la identidad vinculada se verifica en Supabase. Este panel no publica tu ubicación ni muestra datos personales de clientes.",10.4f,MUTED,false);
        privacy.setGravity(Gravity.CENTER);privacy.setPadding(dp(3),dp(15),dp(3),0);
        content.addView(privacy);
    }

    private Button button(String name,boolean gold){
        Button b=new Button(this);b.setText(name);b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(gold?BG:GOLD);
        b.setBackground(rounded(gold?GOLD:Color.rgb(12,58,65),GOLD,13));
        return b;
    }

    private void promptPin(){
        EditText pin=new EditText(this);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint("PIN del conductor");pin.setSingleLine(true);
        pin.setPadding(dp(18),dp(9),dp(18),dp(9));
        new AlertDialog.Builder(this).setTitle("Acceso privado a Supabase")
            .setMessage("Para consultar el espacio ocupado y el estado global, ingresá tu PIN de Conductor. No se guarda desde este panel.")
            .setView(pin).setNegativeButton("Cancelar",null)
            .setPositiveButton("CONSULTAR",(d,w)->{
                String entered=pin.getText().toString().trim();pin.setText("");
                if(!entered.matches("[0-9]{4,10}")){
                    cloudState.setText("●  PIN inválido");cloudState.setTextColor(RED);
                    return;
                }
                sessionPin=entered;refresh();
            }).show();
    }

    private void refresh(){
        if(loading)return;
        loading=true;reload.setEnabled(false);reload.setText("Actualizando…");
        cloudState.setText("●  Consultando servidor…");cloudState.setTextColor(GOLD);
        final String pin=sessionPin==null?"":sessionPin;
        new Thread(()->{
            final String localReport=localSummary();
            final String connectionReport=connectionSummary();
            final String appReport=appSummary();
            JSONObject remote=null;String error=null;
            if(pin.isEmpty())error="Ingresá el PIN del conductor para ver las métricas de Supabase.";
            else try{remote=Api.getSystemStatus(pin);}
                catch(Exception e){error="No pudimos validar el acceso o consultar Supabase. Revisá tu PIN y la conexión.";}
            final JSONObject answer=remote;final String issue=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                localRows.setText(localReport);
                connectivity.setText(connectionReport);
                privateGpsLine.setText(PrivateGpsDiagnostic.message(Api.pickupPresenceEnabled()));
                versionLine.setText(appReport);
                if(answer!=null)showCloud(answer);else{
                    cloudValue.setText("Sin datos");
                    cloudNote.setText("La base remota no está confirmada");
                    cloudRows.setText(issue);
                    cloudUpdated.setText("Datos de nube no disponibles · no se muestran cifras antiguas");
                    cloudState.setText("●  Consulta pendiente");cloudState.setTextColor(AMBER);
                    capacityBar.setProgress(0);
                    sessionPin=DriverIdentity.pin(this);
                    reload.setText(sessionPin.isEmpty()?"VINCULAR / REINTENTAR":"REINTENTAR CONEXIÓN");
                }
                loading=false;reload.setEnabled(true);
                if(answer!=null)reload.setText("↻  ACTUALIZAR DIAGNÓSTICO");
            });
        },"mapa-system-health").start();
    }

    private void showCloud(JSONObject result){
        long used=result.optLong("database_bytes",-1),limit=result.optLong("database_quota_bytes",-1);
        int pct=SystemStatusPolicy.percent(used,limit);
        if(pct<0){
            cloudValue.setText("Sin medición");cloudNote.setText("La cuota no pudo calcularse");
            cloudState.setText("●  No disponible");cloudState.setTextColor(AMBER);
            capacityBar.setProgress(0);return;
        }
        String level=SystemStatusPolicy.level(used,limit);
        int color="CRITICAL".equals(level)?RED:"WATCH".equals(level)?AMBER:GREEN;
        cloudState.setText("●  "+("CRITICAL".equals(level)?"Espacio crítico":
            "WATCH".equals(level)?"Vigilar crecimiento":"Base operativa · espacio suficiente"));
        cloudState.setTextColor(color);
        cloudValue.setText(SystemStatusPolicy.mb(used)+" / "+SystemStatusPolicy.mb(limit));
        cloudNote.setText(pct+" % ocupado   ·   "+SystemStatusPolicy.mb(SystemStatusPolicy.free(used,limit))+" libres");
        capacityBar.setProgress(Math.round(1000f*used/limit));
        capacityBar.setProgressTintList(ColorStateList.valueOf(color));
        cloudRows.setText(
            "Reservas: "+SystemStatusPolicy.count(result.optLong("bookings",-1))+
            "   ·   Abiertas: "+SystemStatusPolicy.count(result.optLong("bookings_active",-1))+
            "\nTrayectos: "+SystemStatusPolicy.count(result.optLong("trips",-1))+
            "   ·   Jornadas: "+SystemStatusPolicy.count(result.optLong("shifts",-1))+
            "\nPuntos GPS: "+SystemStatusPolicy.count(result.optLong("gps_points",-1))+
            "   ·   Dispositivos: "+SystemStatusPolicy.count(result.optLong("devices",-1))+
            "\nJornadas sin cierre: "+SystemStatusPolicy.count(result.optLong("open_shifts",-1))+
            "\nÚltima muestra GPS en nube: "+date(result.optLong("last_gps_ms",0))
        );
        String checked=result.optString("checked_at","");
        String when=checked;
        try{when=date(java.time.OffsetDateTime.parse(checked).toInstant().toEpochMilli());}
        catch(Exception ignored){}
        cloudUpdated.setText("Actualizado: "+when+
            "\nCuota Free de 500 MB: referencia del plan, no medición del disco físico.");
    }

    private String localSummary(){
        try{
            TrackDb db=new TrackDb(getApplicationContext());
            JSONObject counts=db.counts();
            int pendingTrips=db.unsyncedEndedTrips().length();
            int pendingShifts=db.unsyncedEndedShifts().length();
            db.close();
            File f=getDatabasePath(TrackDb.DB_NAME);
            long bytes=(f.exists()?f.length():0)+new File(f.getPath()+"-wal").length();
            return "Trayectos locales: "+SystemStatusPolicy.count(counts.optLong("trips",0))+
                "\nJornadas locales: "+SystemStatusPolicy.count(counts.optLong("shifts",0))+
                "\nPuntos GPS locales: "+SystemStatusPolicy.count(counts.optLong("points",0))+
                "\nPendientes de enviar: "+pendingTrips+" viajes · "+pendingShifts+" jornadas"+
                "\nSQLite + cola WAL: "+SystemStatusPolicy.mb(bytes);
        }catch(Exception e){return "No se pudo leer el historial local. No se modificó nada.";}
    }

    private String connectionSummary(){
        String network="Sin conexión validada";
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n=cm==null?null:cm.getActiveNetwork();
            NetworkCapabilities c=n==null?null:cm.getNetworkCapabilities(n);
            if(c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                network="● Conexión a Internet validada";
        }catch(Exception ignored){}
        SharedPreferences s=getSharedPreferences("tracking_state",MODE_PRIVATE);
        boolean active=s.getBoolean("shift_active",false);
        boolean trip=s.getBoolean("trip_active",false);
        boolean paused=s.getBoolean("shift_paused",false);
        String shift=active?(paused?"Pausada":"En curso"):"Sin jornada iniciada";
        return network+
            "\nJornada: "+shift+"   ·   Viaje: "+(trip?"Activo":"Sin viaje")+
            "\nRecogida pública: "+(Api.pickupPresenceEnabled()?
                (Api.pickupManuallyBusy()?"Pausada manualmente":"Habilitada (sujeta a GPS y agenda)"):"Desactivada");
    }

    private String appSummary(){
        String version="No identificada";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){}
        return "Mapa Trayectos · "+version+
            "\nAndroid "+android.os.Build.VERSION.RELEASE+
            "\nLos datos de cliente nunca se incluyen en este diagnóstico.";
    }

    private String date(long ms){
        if(ms<=0)return "Sin muestras";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("es","UY")).format(new Date(ms));
    }
}
