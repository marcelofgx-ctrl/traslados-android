package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public final class TripFlowDialogs {
    private static final int BG=Color.rgb(7,25,31),PANEL=Color.rgb(8,42,50),GOLD=Color.rgb(224,193,111),TEXT=Color.rgb(245,244,238),MUTED=Color.rgb(174,188,191),GREEN=Color.rgb(42,176,111),RED=Color.rgb(215,68,75);
    private TripFlowDialogs(){}

    public interface StartCallback { void onSelected(String type); }
    public interface CloseCallback { void onClosed(String status,double amountUyu); }

    public static void chooseTripType(Activity a,StartCallback cb){
        LinearLayout box=baseBox(a);
        TextView title=text(a,"¿QUÉ TIPO DE VIAJE ES?",19,TEXT,true);box.addView(title);
        TextView sub=text(a,"La categoría queda guardada en el histórico y en la base.",12,MUTED,false);box.addView(sub,lp(a,5,12));
        AlertDialog dlg=new AlertDialog.Builder(a).setView(box).create();
        addChoice(a,box,"UBER","Viaje de Uber",Color.rgb(28,122,82),()->{dlg.dismiss();cb.onSelected("uber");});
        addChoice(a,box,"CABIFY","Viaje de Cabify",Color.rgb(88,54,126),()->{dlg.dismiss();cb.onSelected("cabify");});
        addChoice(a,box,"PERSONAL","Traslado personal",Color.rgb(41,86,105),()->{dlg.dismiss();cb.onSelected("personal");});
        addChoice(a,box,"OTRO","Otro tipo de traslado",Color.rgb(92,78,44),()->{dlg.dismiss();cb.onSelected("other");});
        Button cancel=button(a,"VOLVER",PANEL);cancel.setOnClickListener(v->dlg.dismiss());box.addView(cancel,lp(a,10,0));
        dlg.setOnShowListener(x->{Window w=dlg.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(a.getResources().getDisplayMetrics().widthPixels*.90f),WindowManager.LayoutParams.WRAP_CONTENT);}});dlg.show();
    }

    public static void closeTrip(Activity a,String type,CloseCallback cb){
        if(!"uber".equals(type)&&!"cabify".equals(type)){cb.onClosed("completed",0);return;}
        LinearLayout box=baseBox(a);String label="uber".equals(type)?"UBER":"CABIFY";
        TextView title=text(a,"CERRAR VIAJE "+label,19,TEXT,true);box.addView(title);
        TextView sub=text(a,"Si se completó, podés registrar ahora el importe cobrado.",12,MUTED,false);box.addView(sub,lp(a,5,10));
        EditText amount=new EditText(a);amount.setTextColor(TEXT);amount.setHintTextColor(MUTED);amount.setHint("Importe en UYU  ·  ej. 380");amount.setSingleLine(true);amount.setTextSize(18);amount.setPadding(dp(a,14),0,dp(a,14),0);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setBackground(rounded(a,Color.rgb(11,49,57),16,1,Color.rgb(55,98,107)));box.addView(amount,new LinearLayout.LayoutParams(-1,dp(a,54)));
        AlertDialog dlg=new AlertDialog.Builder(a).setView(box).create();
        Button completed=button(a,"✓  COMPLETADO",GREEN);completed.setOnClickListener(v->{double value=0;try{String s=amount.getText().toString().trim().replace(',','.');if(!s.isEmpty())value=Double.parseDouble(s);}catch(Exception ignored){}dlg.dismiss();cb.onClosed("completed",Math.max(0,value));});box.addView(completed,lp(a,12,0));
        Button cancelled=button(a,"×  VIAJE CANCELADO",RED);cancelled.setOnClickListener(v->{dlg.dismiss();cb.onClosed("cancelled",0);});box.addView(cancelled,lp(a,8,0));
        Button back=button(a,"VOLVER AL VIAJE",PANEL);back.setOnClickListener(v->dlg.dismiss());box.addView(back,lp(a,8,0));
        dlg.setOnShowListener(x->{Window w=dlg.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(a.getResources().getDisplayMetrics().widthPixels*.90f),WindowManager.LayoutParams.WRAP_CONTENT);}});dlg.show();
    }

    private static void addChoice(Activity a,LinearLayout box,String title,String sub,int color,Runnable run){Button b=button(a,title+"\n"+sub,color);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setTextSize(13);b.setPadding(dp(a,16),0,dp(a,12),0);b.setOnClickListener(v->run.run());box.addView(b,lp(a,7,0));}
    private static LinearLayout baseBox(Activity a){LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(a,20),dp(a,18),dp(a,20),dp(a,18));box.setBackground(rounded(a,BG,24,1,Color.rgb(44,84,93)));return box;}
    private static Button button(Activity a,String s,int color){Button b=new Button(a);b.setText(s);b.setTextColor(TEXT);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(rounded(a,color,17,1,Color.argb(110,255,255,255)));return b;}
    private static TextView text(Activity a,String s,float size,int color,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private static LinearLayout.LayoutParams lp(Activity a,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(a,top),0,dp(a,bottom));return p;}
    private static GradientDrawable rounded(Activity a,int fill,int radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(a,radius));if(stroke>0)g.setStroke(dp(a,stroke),strokeColor);return g;}
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
