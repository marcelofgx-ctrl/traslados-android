package uy.com.mapatrayectos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native, extensible configuration center for the floating shortcut. */
public final class BubbleSettingsPanel {
    private BubbleSettingsPanel(){}

    public static void show(Activity host, Runnable targetedOverlay, Runnable ownAppInfo,
                            Runnable overlayList, Runnable test, Runnable diagnostics) {
        SharedPreferences state=host.getSharedPreferences("bubble_state",Context.MODE_PRIVATE);
        boolean overlay=Settings.canDrawOverlays(host);
        NotificationManager nm=(NotificationManager)host.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean notifications=nm!=null && nm.areNotificationsEnabled();
        long at=state.getLong("bubble_diag_at",0L);
        long trial=state.getLong("bubble_test_requested_at",0L);
        String stage=state.getString("bubble_diag_stage","Sin intento registrado");
        String error=state.getString("bubble_diag_error","");
        SimpleDateFormat clock=new SimpleDateFormat("HH:mm:ss",Locale.getDefault());

        ScrollView scroll=new ScrollView(host);
        scroll.setFillViewport(true);
        LinearLayout pane=new LinearLayout(host);
        pane.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(host,17);
        pane.setPadding(pad,pad,pad,pad);
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(7,36,44),Color.rgb(11,73,78)});
        background.setCornerRadius(dp(host,19));
        background.setStroke(dp(host,1),Color.rgb(194,162,93));
        pane.setBackground(background);
        scroll.addView(pane);

        addText(host,pane,"⚙  CONFIGURACIÓN",18,0xFFE7CA82,true,0);
        addText(host,pane,"Acceso flotante · permisos y pruebas",12,0xFFC0D3D4,false,6);
        addText(host,pane,overlay?"●  Aparecer encima: AUTORIZADO":
            "●  Aparecer encima: PENDIENTE",13,
            overlay?0xFF79DDAE:0xFFFFCC80,true,16);
        addText(host,pane,notifications?"●  Notificaciones: ACTIVADAS":
            "●  Notificaciones: DESACTIVADAS",12,
            notifications?0xFF79DDAE:0xFFFFCC80,true,6);
        addText(host,pane,"Probamos primero el acceso del permiso vinculado a Mapa Trayectos, "+
            "como en las primeras versiones. Android 11+ puede ignorar la selección y "+
            "mostrar la lista general; si ocurre, elegí Mapa Trayectos o entrá a sus ajustes.",
            12,0xFFF5F1E7,false,14);
        addText(host,pane,"El globito permanece oculto mientras Mapa está abierto. "+
            "Para probarlo se abre Inicio, sin activar jornada ni GPS.",
            11,0xFFC0D3D4,false,10);
        addText(host,pane,"ÚLTIMO DIAGNÓSTICO",11,0xFFE7CA82,true,16);
        String info=stage+(at>0?" · "+clock.format(new Date(at)):"")
            +(trial>0?"\nÚltima prueba: "+clock.format(new Date(trial)):"")
            +(error.isEmpty()?"":"\nError: "+error);
        addText(host,pane,info,11,0xFFC0D3D4,false,6);

        TextView directButton=addAction(host,pane,
            "01   IR AL PERMISO DE MAPA","Intentar abrir «Aparecer encima» con la app seleccionada");
        TextView appButton=addAction(host,pane,
            "02   AJUSTES DE MAPA TRAYECTOS","Ir directamente a la ficha de esta app");
        TextView permissionButton=addAction(host,pane,
            "03   LISTA «APARECER ENCIMA»","Alternativa oficial si no abre el permiso específico");
        TextView testButton=addAction(host,pane,
            "04   PROBAR GLOBITO",overlay?"Ir a Inicio y comprobar la burbuja real":
                "Primero hay que autorizar «Aparecer encima»");
        TextView refreshButton=addAction(host,pane,
            "05   ACTUALIZAR ESTADO","Volver a consultar permiso y servicio");
        TextView diagButton=addAction(host,pane,
            "06   DIAGNÓSTICO DETALLADO","Último evento, visibilidad y errores");

        AlertDialog dialog=new AlertDialog.Builder(host).setView(scroll)
            .setNegativeButton("CERRAR",null).create();
        directButton.setOnClickListener(v->{dialog.dismiss();targetedOverlay.run();});
        appButton.setOnClickListener(v->{dialog.dismiss();ownAppInfo.run();});
        permissionButton.setOnClickListener(v->{dialog.dismiss();overlayList.run();});
        testButton.setOnClickListener(v->{dialog.dismiss();test.run();});
        refreshButton.setOnClickListener(v->{dialog.dismiss();show(host,targetedOverlay,ownAppInfo,overlayList,test,diagnostics);});
        diagButton.setOnClickListener(v->{dialog.dismiss();diagnostics.run();});
        dialog.show();
    }

    private static void addText(Activity host,LinearLayout pane,String text,
                                int size,int color,boolean bold,int topMargin){
        TextView label=new TextView(host);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setTypeface(null,bold?1:0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(host,topMargin);
        pane.addView(label,lp);
    }

    private static TextView addAction(Activity host,LinearLayout pane,String title,String description){
        TextView button=new TextView(host);
        button.setText(title+"\n"+description);
        button.setTextSize(11.5f);
        button.setTextColor(Color.WHITE);
        button.setTypeface(null,1);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(host,12),dp(host,5),dp(host,10),dp(host,5));
        button.setContentDescription(title+". "+description);
        GradientDrawable plate=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(12,63,72),Color.rgb(15,87,90)});
        plate.setCornerRadius(dp(host,12));
        plate.setStroke(dp(host,1),0xAAE7CA82);
        button.setBackground(plate);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(host,60));
        lp.topMargin=dp(host,9);
        pane.addView(button,lp);
        return button;
    }
    private static int dp(Activity a,int n){
        return (int)(n*a.getResources().getDisplayMetrics().density+0.5f);
    }
}
