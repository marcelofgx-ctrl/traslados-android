package uy.com.mapatrayectos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Driver-facing control center. Show the next useful action first;
 * keep platform workarounds and diagnostics behind a deliberate expansion.
 */
public final class BubbleSettingsPanel {
    private static final int GOLD=0xFFE7CA82, WHITE=0xFFF5F2EA,
        MUTED=0xFFC3D5D5, GREEN=0xFF80E2B1, AMBER=0xFFFFCE88;
    private BubbleSettingsPanel(){}

    public static void show(Activity host, Runnable targetedOverlay, Runnable ownAppInfo,
                            Runnable overlayList, Runnable notificationSettings,
                            Runnable test, Runnable diagnostics) {
        SharedPreferences state=host.getSharedPreferences("bubble_state",Context.MODE_PRIVATE);
        boolean overlay=Settings.canDrawOverlays(host);
        NotificationManager manager=(NotificationManager)
            host.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean notifications=manager!=null&&manager.areNotificationsEnabled();
        String stage=state.getString("bubble_diag_stage","");
        long at=state.getLong("bubble_diag_at",0L);
        long trial=state.getLong("bubble_test_requested_at",0L);
        // Actual drawing is independent from granting Android permission.
        long lastDrawn=state.getLong("floating_seen_ms",0L);
        boolean confirmed=BubbleReadinessPolicy.confirmed(trial,lastDrawn);
        boolean pending=BubbleReadinessPolicy.pending(trial,lastDrawn,System.currentTimeMillis());
        String clockText=at>0?new SimpleDateFormat("HH:mm",Locale.getDefault())
            .format(new Date(at)):"sin registro";

        ScrollView scroll=new ScrollView(host);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout pane=new LinearLayout(host);
        pane.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(host,18);
        pane.setPadding(pad,pad,pad,dp(host,14));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF071F28,0xFF0D4950});
        bg.setCornerRadius(dp(host,22));
        bg.setStroke(dp(host,1),0xBFE7CA82);
        pane.setBackground(bg);
        scroll.addView(pane);

        addText(host,pane,"⚙  CENTRO DE CONTROL",18,GOLD,true,0);
        addText(host,pane,"Mapa Trayectos  ·  Permisos y herramientas",12,MUTED,false,3);
        addText(host,pane,"ESTADO DEL TELÉFONO",11,GOLD,true,18);
        statusRow(host,pane,"◎",overlay?"Acceso flotante autorizado":"Acceso flotante pendiente",
            overlay?"El globito puede aparecer al salir del mapa":
                "Necesita tu autorización en Android",overlay);
        statusRow(host,pane,"◉",notifications?"Avisos habilitados":"Avisos desactivados",
            notifications?"Notificaciones de la app permitidas":
                "Podés perder avisos si están desactivados",notifications);
        if(overlay){
            statusRow(host,pane,confirmed?"✓":"◌",
                confirmed?"Globito comprobado":pending?"Prueba en curso":
                    trial>0?"Prueba sin confirmar":"Todavía sin probar",
                confirmed?"Se dibujó después de la última prueba en Inicio":
                    pending?"Volvé desde Inicio y actualizá el estado":
                    trial>0?"No hay registro de dibujo en la última prueba":
                    "Probalo desde esta pantalla, sin iniciar jornada",confirmed);
        }
        addText(host,pane,!overlay
            ?"PRÓXIMO PASO · Autorizá el acceso flotante para continuar.":
            confirmed?"COMPROBADO · Se oculta al abrir Mapa y aparece al salir.":
            pending?"COMPROBANDO · Esperá en Inicio para ver el globito.":
            "PRÓXIMO PASO · Probá el globito en Inicio de Android.",
            12,overlay&&confirmed?GREEN:AMBER,true,14);

        // Show only one primary next action; keep alternate settings below.
        TextView directButton=null;
        TextView testButton=null;
        if(!overlay)
            directButton=addAction(host,pane,"01   ACTIVAR ACCESO FLOTANTE",
                "Ir al permiso de Mapa Trayectos en Android",true);
        else
            testButton=addAction(host,pane,
                confirmed?"01   REPETIR PRUEBA":"01   PROBAR GLOBITO",
                "Abrir Inicio y comprobar la burbuja real",true);
        TextView notificationButton=null;
        if(!notifications)
            notificationButton=addAction(host,pane,"03   ACTIVAR NOTIFICACIONES",
                "Abrir avisos de Mapa Trayectos, sin buscar la app",true);

        addText(host,pane,"ÚLTIMA ACTIVIDAD",11,GOLD,true,18);
        addText(host,pane,(at>0?clockText+"  ·  ":"")+"Evento: "+
            ((stage==null||stage.trim().isEmpty())?"Sin diagnóstico todavía":stage),
            12,MUTED,false,6);
        if(trial>0)addText(host,pane,"Última prueba: "+
            new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date(trial)),
            11,MUTED,false,4);
        TextView refreshButton=addAction(host,pane,"↻   ACTUALIZAR ESTADO",
            "Consultar de nuevo permisos y último evento",false);

        addText(host,pane,"HERRAMIENTAS",11,GOLD,true,18);
        TextView more=addAction(host,pane,"⌄   MÁS OPCIONES",
            "Ajustes de la app, lista de permisos y diagnóstico",false);
        LinearLayout advanced=new LinearLayout(host);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        pane.addView(advanced,new LinearLayout.LayoutParams(-1,-2));
        TextView reviewButton=addAction(host,advanced,"REVISAR ACCESO FLOTANTE",
            "Abrir el permiso aunque ya esté autorizado",false);
        TextView appButton=addAction(host,advanced,"AJUSTES DE MAPA TRAYECTOS",
            "Abrir la ficha de esta aplicación",false);
        TextView permissionButton=addAction(host,advanced,"LISTA «APARECER ENCIMA»",
            "Alternativa si Samsung no selecciona la aplicación",false);
        TextView diagButton=addAction(host,advanced,"DIAGNÓSTICO DETALLADO",
            "Servicio, visibilidad y posibles errores",false);
        TextView copyButton=addAction(host,advanced,"⧉   COPIAR INFORME",
            "Estado técnico para compartir, sin PIN ni ubicación",false);
        addText(host,advanced,"Android puede ignorar la selección automática "+
            "del permiso. Si ocurre, elegí Mapa Trayectos en la lista.",
            11,MUTED,false,10);

        AlertDialog dialog=new AlertDialog.Builder(host).setView(scroll)
            .setNegativeButton("CERRAR",null).create();
        if(directButton!=null)
            directButton.setOnClickListener(v->{dialog.dismiss();targetedOverlay.run();});
        if(testButton!=null)
            testButton.setOnClickListener(v->{dialog.dismiss();test.run();});
        if(notificationButton!=null)
            notificationButton.setOnClickListener(v->{dialog.dismiss();notificationSettings.run();});
        refreshButton.setOnClickListener(v->{dialog.dismiss();show(host,targetedOverlay,
            ownAppInfo,overlayList,notificationSettings,test,diagnostics);});
        more.setOnClickListener(v->{
            boolean expanded=advanced.getVisibility()!=View.VISIBLE;
            advanced.setVisibility(expanded?View.VISIBLE:View.GONE);
            more.setText(expanded?"⌃   MENOS OPCIONES\nOcultar las herramientas avanzadas":
                "⌄   MÁS OPCIONES\nAjustes de la app, lista de permisos y diagnóstico");
        });
        reviewButton.setOnClickListener(v->{dialog.dismiss();targetedOverlay.run();});
        appButton.setOnClickListener(v->{dialog.dismiss();ownAppInfo.run();});
        permissionButton.setOnClickListener(v->{dialog.dismiss();overlayList.run();});
        diagButton.setOnClickListener(v->{dialog.dismiss();diagnostics.run();});
        copyButton.setOnClickListener(v->{
            ClipboardManager clipboard=(ClipboardManager)
                host.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null){
                clipboard.setPrimaryClip(ClipData.newPlainText("Estado de Mapa Trayectos",
                    diagnosticReport(host)));
                Toast.makeText(host,"Estado copiado · sin PIN ni ubicación",
                    Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(host,"No se pudo copiar el estado",Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
        Window win=dialog.getWindow();
        if(win!=null){
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int max=host.getResources().getDisplayMetrics().widthPixels-dp(host,26);
            win.setLayout(Math.min(dp(host,430),max),-2);
        }
    }

    /** Snapshot of only non-sensitive diagnostics. Never include coordinates or customer data. */
    private static String diagnosticReport(Activity host){
        SharedPreferences state=host.getSharedPreferences("bubble_state",Context.MODE_PRIVATE);
        NotificationManager nm=(NotificationManager)
            host.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean notifications=nm!=null&&nm.areNotificationsEnabled();
        long at=state.getLong("bubble_diag_at",0L);
        long trial=state.getLong("bubble_test_requested_at",0L);
        long drawn=state.getLong("floating_seen_ms",0L);
        return "Mapa Trayectos · estado del globito"
            +"\nAndroid: "+android.os.Build.VERSION.RELEASE
            +"\nAcceso flotante: "+(Settings.canDrawOverlays(host)?"Autorizado":"Pendiente")
            +"\nNotificaciones: "+(notifications?"Activadas":"Desactivadas")
            +"\nÚltimo estado reportado: "+state.getString("bubble_diag_stage","Sin registro")
            +"\nÚltima visibilidad reportada: "+
                (state.getBoolean("floating_visible",false)?"Visible":"No visible")
            +"\nÚltima prueba: "+(trial>0?"Registrada":"Sin registro")
            +"\nDibujo tras la prueba: "+
                (BubbleReadinessPolicy.confirmed(trial,drawn)?"Confirmado":"Sin confirmar")
            +"\nÚltimo evento: "+(at>0?
                new SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault()).format(new Date(at)):
                "Sin registro")
            +"\nNota: Mapa oculta el globito mientras está en primer plano.";
    }

    private static void statusRow(Activity host,LinearLayout parent,
                                  String icon,String title,String subtitle,boolean okay){
        LinearLayout row=new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(host,12),dp(host,9),dp(host,12),dp(host,9));
        GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xFF11434B,0xFF103940});
        surface.setCornerRadius(dp(host,13));
        surface.setStroke(dp(host,1),okay?0x8879DDAE:0xAAFFD08A);
        row.setBackground(surface);
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);
        rowLp.topMargin=dp(host,8);
        parent.addView(row,rowLp);
        TextView mark=new TextView(host);
        mark.setText(icon);
        mark.setTextSize(22);
        mark.setTextColor(okay?GREEN:AMBER);
        mark.setGravity(Gravity.CENTER);
        row.addView(mark,new LinearLayout.LayoutParams(dp(host,40),dp(host,42)));
        LinearLayout labels=new LinearLayout(host);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView main=new TextView(host);
        main.setText(title);
        main.setTextSize(13.5f);
        main.setTextColor(WHITE);
        main.setTypeface(null,1);
        labels.addView(main);
        TextView hint=new TextView(host);
        hint.setText(subtitle);
        hint.setTextSize(11.5f);
        hint.setTextColor(MUTED);
        labels.addView(hint);
        LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(0,-2,1);
        labelLp.leftMargin=dp(host,5);
        row.addView(labels,labelLp);
        TextView indicator=new TextView(host);
        indicator.setText(okay?"●":"!");
        indicator.setTextSize(16);
        indicator.setTextColor(okay?GREEN:AMBER);
        indicator.setGravity(Gravity.CENTER);
        row.addView(indicator,new LinearLayout.LayoutParams(dp(host,21),dp(host,36)));
        row.setContentDescription(title+". "+subtitle);
    }

    private static void addText(Activity host,LinearLayout pane,String value,
                                float size,int color,boolean bold,int marginTop){
        TextView view=new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(null,bold?1:0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(host,marginTop);
        pane.addView(view,lp);
    }

    private static TextView addAction(Activity host,LinearLayout pane,
                                      String title,String description,boolean primary){
        TextView button=new TextView(host);
        button.setText(title+"\n"+description);
        button.setTextSize(12.8f);
        button.setLineSpacing(dp(host,3),1f);
        button.setTextColor(primary?0xFF0A343C:WHITE);
        button.setTypeface(null,1);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(host,14),dp(host,10),dp(host,12),dp(host,10));
        button.setContentDescription(title+". "+description);
        button.setMinHeight(dp(host,62));
        button.setClickable(true);
        button.setFocusable(true);
        GradientDrawable plate=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            primary?new int[]{0xFFEFD79A,0xFFDBB56D}:new int[]{0xFF125661,0xFF10414B});
        plate.setCornerRadius(dp(host,13));
        if(!primary)plate.setStroke(dp(host,1),0x99E7CA82);
        button.setBackground(new RippleDrawable(
            ColorStateList.valueOf(primary?0x55FFFFFF:0x66FFFFFF),plate,null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(host,9);
        pane.addView(button,lp);
        return button;
    }

    private static int dp(Activity host,int value){
        return (int)(value*host.getResources().getDisplayMetrics().density+0.5f);
    }
}
