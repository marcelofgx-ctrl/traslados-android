package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryActivity extends Activity {
    private static final int BG=Color.rgb(7,25,31),PANEL=Color.rgb(8,42,50),GOLD=Color.rgb(224,193,111),TEXT=Color.rgb(245,244,238),MUTED=Color.rgb(174,188,191);

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);render();Api.init(this);Api.syncPendingAsync();}

    private void render(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(30));sc.addView(root,new ScrollView.LayoutParams(-1,-2));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹");back.setTextSize(26);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(52),dp(48)));TextView title=text("HISTORIAL DE VIAJES",22,TEXT,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),0,0,0);head.addView(title,tp);root.addView(head,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView sub=text("Recorridos guardados en el teléfono y sincronizados con la base cuando hay conexión.",12,MUTED,false);root.addView(sub,lp(0,12));
        TrackDb db=new TrackDb(this);JSONArray a=db.listTrips();
        if(a.length()==0){TextView empty=text("Todavía no hay viajes registrados.\nIniciá una jornada y luego un viaje desde Mapa Trayectos.",16,MUTED,false);empty.setGravity(Gravity.CENTER);root.addView(empty,lp(40,0));}
        SimpleDateFormat df=new SimpleDateFormat("EEE d MMM · HH:mm",new Locale("es","UY"));
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("trip_id","");long start=o.optLong("started_at_ms",0),end=o.optLong("ended_at_ms",0),moving=o.optLong("moving_ms",0),stopped=o.optLong("stopped_ms",0);double km=o.optDouble("distance_m",0)/1000.0,avg=o.optDouble("avg_speed_kmh",0);double max=TelemetryQuality.confirmedMaxKmh(db.getTripPoints(id));if(max<=0)max=o.optDouble("max_speed_kmh",0);String from=o.optString("start_zone",""),to=o.optString("end_zone","");
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(15),dp(13),dp(15),dp(13));card.setBackground(rounded(PANEL,18,1,Color.rgb(43,82,91)));
            TextView date=text(start>0?df.format(new Date(start)):"Viaje",15,GOLD,true);card.addView(date);
            TextView route=text((from.isEmpty()?"Origen":from)+"  →  "+(to.isEmpty()?"Destino":to),18,TEXT,true);card.addView(route,lp(5,0));
            long total=end>start?end-start:moving+stopped;TextView summary=text(String.format(Locale.getDefault(),"%.1f km   ·   %s",km,duration(total)),13,TEXT,true);card.addView(summary,lp(7,0));
            TextView speeds=text(String.format(Locale.getDefault(),"Vel. prom. %.0f km/h   ·   Vel. máx. %.0f km/h",avg,max),12,GOLD,true);card.addView(speeds,lp(4,0));
            TextView detail=text("Movimiento "+duration(moving)+"   ·   Detenido "+duration(stopped),12,MUTED,false);card.addView(detail,lp(4,0));
            card.setOnClickListener(v->{Intent d=new Intent(this,TripDetailActivity.class);d.putExtra("trip_id",id);startActivity(d);});root.addView(card,lp(10,0));
        }
        db.close();setContentView(sc);
    }

    private LinearLayout.LayoutParams lp(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,dp(bottom));return p;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(TEXT);b.setAllCaps(false);b.setBackground(rounded(PANEL,15,1,Color.rgb(45,86,96)));return b;}
    private GradientDrawable rounded(int fill,int radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String duration(long ms){long t=Math.max(0,ms)/1000,h=t/3600,m=(t%3600)/60,s=t%60;return h>0?String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,s):String.format(Locale.getDefault(),"%02d:%02d",m,s);}
}
