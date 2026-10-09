package uy.com.mapatrayectos;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conversational, local, offline reminders. All calendar proposals are confirmed before saving. */
public final class ReminderActivity extends Activity {
    private final int PETROL=Color.rgb(7,45,53),GOLD=Color.rgb(226,189,113),CREAM=Color.rgb(248,247,240);
    private final SimpleDateFormat fmt=new SimpleDateFormat("EEE dd/MM/yyyy · HH:mm",new Locale("es","UY"));
    private LinearLayout body,history;private EditText prompt;
    private Calendar chosen;
    private boolean manuallySelectedTime=false;
    private TextView timeButton,diagnostics;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(4,25,30));
        getWindow().setNavigationBarColor(Color.rgb(4,25,30));
        chosen=Calendar.getInstance();chosen.add(Calendar.MINUTE,5);
        chosen.set(Calendar.SECOND,0);chosen.set(Calendar.MILLISECOND,0);
        ReminderStore.channel(this);
        build();refresh();
        int alertId=getIntent().getIntExtra("show_reminder_id",-1);
        if(alertId>0)history.post(()->showReminderDialog(alertId));
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7402);
    }
    @Override protected void onResume(){
        super.onResume();
        ReminderStore.channel(this);
        ReminderStore.rearm(this);
        refresh();updateDiagnostics();
    }
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(8,31,37));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(22),dp(18),dp(20));
        scroll.addView(body);setContentView(scroll);
        TextView title=label("◷   MIS RECORDATORIOS",20,GOLD,true);
        body.addView(title);TextView sub=label("Escribí algo que quieras recordar. La fecha siempre se confirma antes de guardar.",12,CREAM,false);
        sub.setPadding(0,dp(8),0,dp(16));body.addView(sub);
        TextView bot=bubble("¿Qué necesitás recordar?",false);
        body.addView(bot,margin(0,5));
        prompt=new EditText(this);
        prompt.setTextColor(CREAM);prompt.setHintTextColor(Color.rgb(154,180,182));
        prompt.setTextSize(16);
        prompt.setHint("Ej.: llamar a la contadora mañana 15:30");
        prompt.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        prompt.setSingleLine(false);prompt.setMinLines(2);prompt.setMaxLines(4);
        prompt.setPadding(dp(14),dp(8),dp(14),dp(8));
        prompt.setBackground(rounded(Color.rgb(14,63,71),GOLD,16));
        body.addView(prompt,margin(0,12));
        timeButton=label("FECHA Y HORA   "+fmt.format(chosen.getTime()),13,GOLD,true);
        timeButton.setGravity(Gravity.CENTER);timeButton.setPadding(dp(8),dp(12),dp(8),dp(12));
        timeButton.setBackground(rounded(Color.rgb(6,55,63),GOLD,12));
        body.addView(timeButton,margin(0,8));
        timeButton.setOnClickListener(v->pickDate());
        TextView save=label("PROGRAMAR RECORDATORIO",14,PETROL,true);save.setGravity(Gravity.CENTER);
        save.setPadding(dp(12),dp(15),dp(12),dp(15));save.setBackground(rounded(GOLD,GOLD,13));
        body.addView(save,margin(0,9));save.setOnClickListener(v->prepare());
        TextView help=label("Si escribís «hoy», «mañana» o «pasado mañana» junto a una hora, se propone esa fecha. Podés cambiarla antes de guardar.",11,Color.rgb(171,190,190),false);
        body.addView(help,margin(0,11));
        TextView test=label("PROBAR ALERTA EN 1 MINUTO",13,PETROL,true);
        test.setGravity(Gravity.CENTER);test.setPadding(dp(8),dp(15),dp(8),dp(15));
        test.setBackground(rounded(GOLD,GOLD,13));body.addView(test,margin(0,13));
        test.setOnClickListener(v->testAlarm());
        TextView preview=label("♫  PROBAR MP3 ORIGINAL DE BURBUJAS",12,GOLD,true);
        preview.setGravity(Gravity.CENTER);preview.setPadding(dp(7),dp(13),dp(7),dp(12));
        preview.setBackground(rounded(Color.rgb(6,55,63),GOLD,12));
        body.addView(preview,margin(0,6));
        preview.setOnClickListener(v->{
            Toast.makeText(this,"Probando tu MP3 original al volumen de notificaciones",Toast.LENGTH_SHORT).show();
            new Thread(()->{
                ReminderSound.Result result=ReminderSound.playBlocking(getApplicationContext());
                runOnUiThread(()->{
                    updateDiagnostics();
                    Toast.makeText(this,result.ok?"Audio iniciado: comprobá si lo escuchaste":result.detail,Toast.LENGTH_LONG).show();
                });
            },"mapa-preview-audio").start();
        });
        diagnostics=label("Cargando diagnóstico…",12,CREAM,false);
        diagnostics.setPadding(dp(7),dp(11),dp(7),dp(11));
        diagnostics.setBackground(rounded(Color.rgb(11,53,62),GOLD,12));
        body.addView(diagnostics,margin(0,9));
        TextView overlay=label("HABILITAR GLOBO DE DIÁLOGO",11,GOLD,true);
        overlay.setGravity(Gravity.CENTER);overlay.setPadding(0,dp(13),0,dp(7));
        body.addView(overlay);
        overlay.setOnClickListener(v->{
            if(android.provider.Settings.canDrawOverlays(this)){
                Toast.makeText(this,"Globos flotantes habilitados",Toast.LENGTH_SHORT).show();return;
            }
            new AlertDialog.Builder(this).setTitle("Globo de recordatorio")
                .setMessage("Permití que Mapa Trayectos se muestre sobre otras aplicaciones. Sin este permiso recibirás igualmente el aviso estándar de Android.")
                .setPositiveButton("ABRIR AJUSTES",(d,w)->{
                    try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
                    catch(Exception e){Toast.makeText(this,"No se pudieron abrir los ajustes de superposición",Toast.LENGTH_LONG).show();}
                }).setNegativeButton("CANCELAR",null).show();
        });
        TextView timing=label("REVISAR PRECISIÓN DE LAS ALERTAS",11,GOLD,true);
        timing.setGravity(Gravity.CENTER);timing.setPadding(dp(3),dp(10),dp(3),dp(10));
        body.addView(timing,margin(0,2));
        timing.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=31){
                AlarmManager alarms=(AlarmManager)getSystemService(ALARM_SERVICE);
                if(alarms!=null&&!alarms.canScheduleExactAlarms()){
                    new AlertDialog.Builder(this).setTitle("Alertas puntuales")
                        .setMessage("Android puede retrasar recordatorios si no habilitás las alarmas exactas. ¿Abrir la configuración del teléfono?")
                        .setPositiveButton("ABRIR AJUSTES",(d,w)->{
                            try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}
                            catch(Exception e){Toast.makeText(this,"Abrí Ajustes > Alarmas y recordatorios",Toast.LENGTH_LONG).show();}
                        }).setNegativeButton("CANCELAR",null).show();
                    return;
                }
            }
            Toast.makeText(this,"Alarmas puntuales habilitadas",Toast.LENGTH_SHORT).show();
        });
        TextView heading=label("PRÓXIMOS Y ANTERIORES",13,GOLD,true);body.addView(heading,margin(0,23));
        history=new LinearLayout(this);history.setOrientation(LinearLayout.VERTICAL);body.addView(history);
        TextView back=label("VOLVER AL MAPA",13,CREAM,true);back.setGravity(Gravity.CENTER);
        back.setPadding(0,dp(20),0,dp(14));body.addView(back);back.setOnClickListener(v->finish());
    }
    private void pickDate(){
        Calendar current=(Calendar)chosen.clone();
        DatePickerDialog pick=new DatePickerDialog(this,(v,year,month,day)->{
            chosen.set(Calendar.YEAR,year);chosen.set(Calendar.MONTH,month);chosen.set(Calendar.DAY_OF_MONTH,day);
            new TimePickerDialog(this,(tv,h,m)->{
                chosen.set(Calendar.HOUR_OF_DAY,h);chosen.set(Calendar.MINUTE,m);
                chosen.set(Calendar.SECOND,0);chosen.set(Calendar.MILLISECOND,0);
                manuallySelectedTime=true;
                timeButton.setText("FECHA Y HORA   "+fmt.format(chosen.getTime()));
            },chosen.get(Calendar.HOUR_OF_DAY),chosen.get(Calendar.MINUTE),true).show();
        },current.get(Calendar.YEAR),current.get(Calendar.MONTH),current.get(Calendar.DAY_OF_MONTH));
        pick.getDatePicker().setMinDate(System.currentTimeMillis()-3600000L);
        pick.show();
    }
    /** No 'hoy' needed when typing a time such as 'llamar 14:20'. */
    private Calendar interpretedTime(String text){
        Calendar proposal=(Calendar)chosen.clone();
        Matcher hour=Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").matcher(text);
        Matcher day=Pattern.compile("(?i)\\b(pasado\\s+mañana|mañana|hoy)\\b").matcher(text);
        boolean hasDay=day.find();
        boolean hasHour=hour.find();
        if(!hasHour&&!manuallySelectedTime)return null;
        if(hasDay){
            proposal=Calendar.getInstance();
            String word=day.group(1).toLowerCase(Locale.ROOT);
            proposal.add(Calendar.DATE,word.startsWith("pasado")?2:word.equals("mañana")?1:0);
        }else if(hasHour&&!manuallySelectedTime){
            proposal=Calendar.getInstance();
        }
        if(hasHour){
            proposal.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hour.group(1)));
            proposal.set(Calendar.MINUTE,Integer.parseInt(hour.group(2)));
            proposal.set(Calendar.SECOND,0);proposal.set(Calendar.MILLISECOND,0);
        }
        return proposal;
    }
    private void prepare(){
        String text=prompt.getText().toString().trim();
        if(text.isEmpty()){prompt.setError("Escribí qué querés recordar");return;}
        Calendar proposal=interpretedTime(text);
        if(proposal==null){
            new AlertDialog.Builder(this).setTitle("Falta la hora")
                .setMessage("Escribí una hora, por ejemplo «14:20», o elegí día y hora en el calendario.")
                .setPositiveButton("ELEGIR FECHA",(d,w)->pickDate())
                .setNegativeButton("CANCELAR",null).show();return;
        }
        long when=proposal.getTimeInMillis();
        if(when<=System.currentTimeMillis()){
            new AlertDialog.Builder(this).setTitle("Fecha u hora pasada")
                .setMessage("La hora indicada ya pasó: "+fmt.format(new Date(when))+
                    "\n\nElegí una fecha y hora futuras. No se programó nada.")
                .setPositiveButton("ELEGIR FECHA",(d,w)->pickDate())
                .setNegativeButton("CANCELAR",null).show();return;
        }
        final String message=text;
        final String warning=!ReminderStore.canNotify(this)?
            "\n\n⚠ Notificaciones DESACTIVADAS. Se guardará, pero necesitás habilitarlas para ver avisos.":
            !ReminderStore.canExact(this)?
            "\n\n⚠ Sin permiso de alarma exacta. Android puede retrasar este aviso.":
            "\n\nNotificación y alarma puntual habilitadas.";
        new AlertDialog.Builder(this).setTitle("Confirmar día y hora")
            .setMessage(message+"\n\n"+fmt.format(new Date(when))+warning)
            .setPositiveButton("PROGRAMAR",(d,w)->{
                try{
                    ReminderStore.create(this,message,when);
                    prompt.setText("");manuallySelectedTime=false;
                    Toast.makeText(this,"Guardado para "+fmt.format(new Date(when)),Toast.LENGTH_LONG).show();
                    refresh();updateDiagnostics();
                }catch(Exception ex){Toast.makeText(this,"No se guardó el recordatorio: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
            }).setNegativeButton("CANCELAR",null).show();
    }

    private void testAlarm(){
        if(!ReminderStore.canNotify(this)){
            new AlertDialog.Builder(this).setTitle("No se pueden mostrar alertas")
                .setMessage("Primero habilitá las notificaciones de Mapa Trayectos. De lo contrario Android no mostrará el aviso.")
                .setPositiveButton("AJUSTES",(d,w)->openNotificationSettings()).setNegativeButton("CANCELAR",null).show();
            return;
        }
        if(!ReminderStore.canExact(this)){
            new AlertDialog.Builder(this).setTitle("Activar alarma puntual")
                .setMessage("Para probar una alerta aproximadamente un minuto después necesitás habilitar «Alarmas y recordatorios» en Android.")
                .setPositiveButton("ACTIVAR",(d,w)->requestExactAlarm())
                .setNegativeButton("CANCELAR",null).show();
            return;
        }
        long when=System.currentTimeMillis()+65000L;
        new AlertDialog.Builder(this).setTitle("Prueba de recordatorio")
            .setMessage("En aproximadamente un minuto aparecerá el recordatorio de prueba.\n\nPodés salir de la aplicación y bloquear la pantalla.")
            .setPositiveButton("PROGRAMAR PRUEBA",(d,w)->{
                try{
                    ReminderStore.create(this,"Prueba de recordatorio de Mapa Trayectos",when);
                    refresh();updateDiagnostics();
                    Toast.makeText(this,"Prueba programada para "+fmt.format(new Date(when)),Toast.LENGTH_LONG).show();
                }catch(Exception e){Toast.makeText(this,"No se pudo programar: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            }).setNegativeButton("CANCELAR",null).show();
    }
    private void openNotificationSettings(){
        try{
            Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
            startActivity(i);
        }catch(Exception ex){
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        }
    }
    private void requestExactAlarm(){
        if(Build.VERSION.SDK_INT<31)return;
        try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}
        catch(Exception e){Toast.makeText(this,"Abrí Ajustes → Alarmas y recordatorios",Toast.LENGTH_LONG).show();}
    }
    private void updateDiagnostics(){
        if(diagnostics==null)return;
        boolean notify=ReminderStore.canNotify(this),exact=ReminderStore.canExact(this);
        boolean overlay=Settings.canDrawOverlays(this);
        int pending=0;long next=Long.MAX_VALUE;
        for(ReminderStore.Item i:ReminderStore.all(this))if(!i.done&&!i.fired){
            pending++;if(i.time<next)next=i.time;
        }
        long last=ReminderStore.lastAlert(this);
        String error=ReminderStore.lastError(this);
        diagnostics.setText("ESTADO DE ALERTAS\n"+
            "Notificaciones: "+(notify?"ACTIVADAS ✓":"BLOQUEADAS ✕")+"\n"+
            "Alarmas puntuales: "+(exact?"ACTIVADAS ✓":"SIN PERMISO ⚠")+"\n"+
            "Globo flotante: "+(overlay?"PERMITIDO ✓":"SIN PERMISO")+"\n"+
            "Pendientes: "+pending+"\n"+
            "Próximo aviso: "+(next==Long.MAX_VALUE?"ninguno":fmt.format(new Date(next)))+"\n"+
            "Último aviso entregado: "+(last==0?"ninguno registrado":fmt.format(new Date(last)))+
            (error==null||error.isEmpty()?"":"\nÚltimo problema: "+error)+
            "\nSonido: "+ReminderSound.diagnostics(this));
    }
    /** User tapping a notification sees the same illustrated style within the app. */
    private void showReminderDialog(int id){
        ReminderStore.Item item=ReminderStore.get(this,id);
        if(item==null||item.done)return;
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(12),dp(22),dp(13));
        TextView name=bubble("◈  ¡TE RECUERDO ALGO!",false);
        content.addView(name);
        TextView message=label("◣  "+item.text,18,PETROL,true);
        message.setPadding(dp(8),dp(15),dp(8),dp(18));content.addView(message);
        new AlertDialog.Builder(this).setTitle("✦ Tu recordatorio")
            .setView(content)
            .setPositiveButton("HECHO",(d,w)->{ReminderStore.change(this,id,true,false,-1);refresh();updateDiagnostics();})
            .setNeutralButton("+10 MIN",(d,w)->{ReminderStore.change(this,id,false,false,System.currentTimeMillis()+600000L);refresh();updateDiagnostics();})
            .setNegativeButton("CERRAR",null).show();
    }
    private void refresh(){
        if(history==null)return;history.removeAllViews();
        List<ReminderStore.Item> all=ReminderStore.all(this);
        if(all.isEmpty()){history.addView(bubble("Todavía no programaste recordatorios.",false));updateDiagnostics();return;}
        for(ReminderStore.Item item:all){
            LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setPadding(dp(13),dp(9),dp(13),dp(9));
            wrap.setBackground(rounded(item.done?Color.rgb(30,54,56):Color.rgb(13,76,83),Color.rgb(115,137,123),14));
            TextView content=label((item.done?"✓  ":"") +item.text,15,CREAM,!item.done);
            wrap.addView(content);
            String info=fmt.format(new Date(item.time))+(item.done?" · completado":item.fired?" · notificado":item.time<System.currentTimeMillis()?" · vencido":" · pendiente");
            TextView status=label(info,11,GOLD,false);wrap.addView(status,margin(0,4));
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView done=label(item.done?"REACTIVAR":"COMPLETAR",11,CREAM,true);
            done.setPadding(dp(6),dp(9),dp(14),dp(6));actions.addView(done);
            done.setOnClickListener(v->{ReminderStore.change(this,item.id,!item.done,false,-1);refresh();});
            TextView later=label("POSPONER",11,GOLD,true);later.setPadding(dp(7),dp(9),dp(14),dp(6));
            actions.addView(later);later.setOnClickListener(v->{
                Calendar c=Calendar.getInstance();c.setTimeInMillis(Math.max(System.currentTimeMillis(),item.time));
                new DatePickerDialog(this,(d,y,month,day)->{
                    c.set(y,month,day);
                    new TimePickerDialog(this,(t,h,min)->{
                        c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,min);c.set(Calendar.SECOND,0);
                        if(c.getTimeInMillis()<=System.currentTimeMillis()){Toast.makeText(this,"Elegí una hora futura",Toast.LENGTH_SHORT).show();return;}
                        ReminderStore.change(this,item.id,false,false,c.getTimeInMillis());refresh();
                    },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();
                },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
            });
            TextView del=label("ELIMINAR",11,Color.rgb(252,170,161),true);del.setPadding(dp(5),dp(9),0,dp(6));
            actions.addView(del);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Eliminar recordatorio")
                .setMessage(item.text).setPositiveButton("ELIMINAR",(d,w)->{ReminderStore.change(this,item.id,false,true,-1);refresh();})
                .setNegativeButton("CANCELAR",null).show());
            wrap.addView(actions);history.addView(wrap,margin(0,9));
        }
    }
    private TextView bubble(String value,boolean me){
        TextView view=label(value,14,CREAM,false);
        view.setPadding(dp(13),dp(12),dp(13),dp(12));
        view.setBackground(rounded(me?Color.rgb(15,84,89):Color.rgb(13,56,64),GOLD,15));
        return view;
    }
    private TextView label(String value,float size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;
    }
    private GradientDrawable rounded(int fill,int stroke,int r){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));
        d.setStroke(dp(1),stroke);return d;
    }
    private LinearLayout.LayoutParams margin(int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);return p;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
