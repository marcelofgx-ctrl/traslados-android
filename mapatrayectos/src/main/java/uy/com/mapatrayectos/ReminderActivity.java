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
    private TextView timeButton;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(4,25,30));
        getWindow().setNavigationBarColor(Color.rgb(4,25,30));
        chosen=Calendar.getInstance();chosen.add(Calendar.DATE,1);chosen.set(Calendar.HOUR_OF_DAY,10);chosen.set(Calendar.MINUTE,0);
        chosen.set(Calendar.SECOND,0);chosen.set(Calendar.MILLISECOND,0);
        build();refresh();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7402);
    }
    @Override protected void onResume(){super.onResume();ReminderStore.rearm(this);}
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
                timeButton.setText("FECHA Y HORA   "+fmt.format(chosen.getTime()));
            },chosen.get(Calendar.HOUR_OF_DAY),chosen.get(Calendar.MINUTE),true).show();
        },current.get(Calendar.YEAR),current.get(Calendar.MONTH),current.get(Calendar.DAY_OF_MONTH));
        pick.getDatePicker().setMinDate(System.currentTimeMillis()-3600000L);
        pick.show();
    }
    private void prepare(){
        String text=prompt.getText().toString().trim();
        if(text.isEmpty()){prompt.setError("Escribí qué querés recordar");return;}
        Calendar proposal=(Calendar)chosen.clone();
        // Pattern only recognizes explicit Spanish relative days + time. Other sentences use the picker.
        Matcher m=Pattern.compile("(?i)\\b(hoy|mañana|pasado\\s+mañana)\\b.{0,36}?\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").matcher(text);
        if(m.find()){
            proposal=Calendar.getInstance();
            String day=m.group(1).toLowerCase(Locale.ROOT);
            int offset=day.startsWith("pasado")?2:day.equals("mañana")?1:0;
            proposal.add(Calendar.DAY_OF_MONTH,offset);
            proposal.set(Calendar.HOUR_OF_DAY,Integer.parseInt(m.group(2)));
            proposal.set(Calendar.MINUTE,Integer.parseInt(m.group(3)));
            proposal.set(Calendar.SECOND,0);proposal.set(Calendar.MILLISECOND,0);
        }
        long selected=proposal.getTimeInMillis();
        if(selected<=System.currentTimeMillis()){
            new AlertDialog.Builder(this).setTitle("Fecha pasada")
                .setMessage("Elegí una fecha y hora futuras para recibir el recordatorio.")
                .setPositiveButton("ELEGIR FECHA",(d,w)->pickDate()).setNegativeButton("CANCELAR",null).show();
            return;
        }
        final String note=text;
        new AlertDialog.Builder(this).setTitle("Confirmar recordatorio")
            .setMessage(note+"\n\n"+fmt.format(new Date(selected))+"\n\nSe guardará en este teléfono.")
            .setPositiveButton("PROGRAMAR",(d,w)->{
                try{ReminderStore.create(this,note,selected);prompt.setText("");
                    Toast.makeText(this,"Recordatorio guardado",Toast.LENGTH_SHORT).show();refresh();}
                catch(Exception e){Toast.makeText(this,"No se pudo guardar el recordatorio",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("CANCELAR",null).show();
    }
    private void refresh(){
        if(history==null)return;history.removeAllViews();
        List<ReminderStore.Item> all=ReminderStore.all(this);
        if(all.isEmpty()){history.addView(bubble("Todavía no programaste recordatorios.",false));return;}
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
