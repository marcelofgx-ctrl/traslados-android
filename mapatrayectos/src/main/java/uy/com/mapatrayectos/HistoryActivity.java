package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryActivity extends Activity {
    private static final int BG1=Color.rgb(244,243,234),BG2=Color.rgb(229,241,236);
    private static final int INK=Color.rgb(10,47,57),INK_SOFT=Color.rgb(73,97,101);
    private static final int TEAL=Color.rgb(17,111,118),TEAL_DARK=Color.rgb(7,72,80);
    private static final int GOLD=Color.rgb(214,174,67),GOLD_DEEP=Color.rgb(171,128,35);
    private static final int GREEN=Color.rgb(24,142,101),RED=Color.rgb(196,67,72),ROUTE=Color.rgb(32,143,168);
    private static final int WHITE=Color.rgb(251,250,244);

    private JSONArray allTrips=new JSONArray();
    private LinearLayout summaryHost,listHost;
    private int periodDays=30;
    private String typeFilter="all",statusFilter="all";
    private String expandedDay=null;
    private final Locale locale=new Locale("es","UY");

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG1);
        getWindow().setNavigationBarColor(BG1);
        if(Build.VERSION.SDK_INT>=26)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        periodDays=getSharedPreferences("history_prefs",MODE_PRIVATE).getInt("period_days",30);
        typeFilter=getSharedPreferences("history_prefs",MODE_PRIVATE).getString("type_filter","all");
        statusFilter=getSharedPreferences("history_prefs",MODE_PRIVATE).getString("status_filter","all");
        buildUi();
        loadData();
        Api.init(this);
        Api.syncPendingAsync();
    }

    private void buildUi(){
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        sc.setBackground(new TexturedDrawable(this,BG1,BG2,Color.argb(0,0,0,0),0f,0f,true));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(34));
        sc.addView(root,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10),dp(8),dp(10),dp(8));
        header.setBackground(new TexturedDrawable(this,Color.rgb(6,52,61),Color.rgb(11,82,88),Color.argb(220,214,174,67),22f,1f,false));
        header.setElevation(dp(5));

        TextView back=text("‹",28,INK,true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Volver al mapa");
        back.setBackground(new TexturedDrawable(this,Color.argb(245,252,244,211),Color.argb(245,236,219,158),Color.argb(200,214,174,67),18f,1f,true));
        back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));

        LinearLayout titleBox=new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("HISTORIAL DE TRAYECTOS",18,Color.WHITE,true);
        TextView subtitle=text("Consulta, filtros y recorridos",10.8f,Color.rgb(232,211,145),true);
        titleBox.addView(title);titleBox.addView(subtitle);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-1,1);titleLp.setMargins(dp(10),0,0,0);header.addView(titleBox,titleLp);

        TextView badge=text("30D",10.5f,Color.rgb(252,245,216),true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(new TexturedDrawable(this,Color.argb(230,15,87,93),Color.argb(230,20,108,111),Color.argb(180,214,174,67),16f,1f,false));
        badge.setText(periodDays==1?"HOY":periodDays==7?"7D":periodDays==30?"30D":"TODO");
        header.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(36)));
        root.addView(header,lp(0,10));

        summaryHost=new LinearLayout(this);
        summaryHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(summaryHost,lp(0,10));

        LinearLayout filterCard=new LinearLayout(this);
        filterCard.setOrientation(LinearLayout.VERTICAL);
        filterCard.setPadding(dp(12),dp(11),dp(12),dp(10));
        filterCard.setBackground(lightPanel(22));
        filterCard.setElevation(dp(2));

        filterCard.addView(filterLabel("PERÍODO","▣"));
        filterCard.addView(periodFilterRow(),lp(5,10));
        filterCard.addView(divider(),new LinearLayout.LayoutParams(-1,dp(1)));
        filterCard.addView(filterLabel("TIPO DE VIAJE","◈"),lp(9,0));
        filterCard.addView(typeFilterRow(),lp(5,10));
        filterCard.addView(divider(),new LinearLayout.LayoutParams(-1,dp(1)));
        filterCard.addView(filterLabel("ESTADO","≡"),lp(9,0));
        filterCard.addView(statusFilterRow(),lp(5,0));
        root.addView(filterCard,lp(0,12));

        LinearLayout listTitle=new LinearLayout(this);listTitle.setGravity(Gravity.CENTER_VERTICAL);
        TextView l1=text("AGRUPADO POR DÍA",11,GOLD_DEEP,true);
        TextView l2=text("Todos contraídos · tocá un día para abrirlo",10.3f,INK_SOFT,false);l2.setGravity(Gravity.RIGHT);
        listTitle.addView(l1,new LinearLayout.LayoutParams(0,-2,1));listTitle.addView(l2,new LinearLayout.LayoutParams(0,-2,1.8f));
        root.addView(listTitle,lp(0,5));

        listHost=new LinearLayout(this);
        listHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(listHost,new LinearLayout.LayoutParams(-1,-2));

        setContentView(sc);
    }

    private void loadData(){
        TrackDb db=new TrackDb(this);
        allTrips=db.listTrips();
        db.close();
        expandedDay=null;
        renderFiltered();
    }

    private ArrayList<JSONObject> filteredTrips(){
        ArrayList<JSONObject> filtered=new ArrayList<>();
        long now=System.currentTimeMillis();
        long cutoff=periodDays<=0?Long.MIN_VALUE:startOfToday(now)-(long)(periodDays-1)*86400000L;
        for(int i=0;i<allTrips.length();i++){
            JSONObject o=allTrips.optJSONObject(i);if(o==null)continue;
            if(o.optLong("started_at_ms",0)<cutoff)continue;
            String type=o.optString("trip_type","other"),status=o.optString("trip_status","completed");
            if(!"all".equals(typeFilter)&&!typeFilter.equals(type))continue;
            if(!"all".equals(statusFilter)&&!statusFilter.equals(status))continue;
            filtered.add(o);
        }
        return filtered;
    }

    private void renderFiltered(){
        ArrayList<JSONObject> filtered=filteredTrips();
        renderSummary(filtered);
        renderDays(filtered);
    }

    private void renderSummary(List<JSONObject> trips){
        summaryHost.removeAllViews();
        int completed=0,cancelled=0;double income=0,km=0;long totalMs=0,moving=0,stopped=0;
        for(JSONObject o:trips){
            km+=o.optDouble("distance_m",0)/1000.0;
            String st=o.optString("trip_status","completed");if("cancelled".equals(st))cancelled++;else completed++;
            if(!o.isNull("amount_uyu"))income+=Math.max(0,o.optDouble("amount_uyu",0));
            long start=o.optLong("started_at_ms",0),end=o.optLong("ended_at_ms",0);
            totalMs+=end>start?end-start:o.optLong("moving_ms",0)+o.optLong("stopped_ms",0);
            moving+=o.optLong("moving_ms",0);stopped+=o.optLong("stopped_ms",0);
        }

        LinearLayout dash=new LinearLayout(this);
        dash.setOrientation(LinearLayout.VERTICAL);
        dash.setPadding(dp(10),dp(10),dp(10),dp(10));
        dash.setBackground(lightPanel(22));
        dash.setElevation(dp(3));

        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text("▥",20,GOLD_DEEP,true);icon.setGravity(Gravity.CENTER);icon.setBackground(new TexturedDrawable(this,Color.argb(235,252,239,186),Color.argb(235,239,217,143),Color.argb(160,214,174,67),16f,1f,true));
        titleRow.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(9),0,0,0);
        copy.addView(text("RESUMEN DEL PERÍODO",13.5f,INK,true));
        copy.addView(text(periodLabel(),10.5f,INK_SOFT,false));
        titleRow.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        dash.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(summaryMetric(String.valueOf(trips.size()),"VIAJES","╱╲"),new LinearLayout.LayoutParams(0,dp(66),1));
        metrics.addView(summaryMetric(String.format(locale,"%.1f",km),"KM","⌖"),new LinearLayout.LayoutParams(0,dp(66),1));
        metrics.addView(summaryMetric(duration(totalMs),"TIEMPO","◷"),new LinearLayout.LayoutParams(0,dp(66),1));
        metrics.addView(summaryMetric("$ "+String.format(locale,"%.0f",income),"INGRESOS","●"),new LinearLayout.LayoutParams(0,dp(66),1));
        dash.addView(metrics,lp(3,5));

        TextView line=text("Completados "+completed+"   ·   Cancelados "+cancelled+"   ·   Movimiento "+duration(moving)+"   ·   Detenido "+duration(stopped),10.7f,INK_SOFT,true);
        line.setGravity(Gravity.CENTER);
        dash.addView(line,lp(3,0));

        if(km>0&&income>0){
            TextView eff=text(String.format(locale,"Rendimiento  $ %.0f/km",income/km),10.5f,GOLD_DEEP,true);
            eff.setGravity(Gravity.CENTER);dash.addView(eff,lp(5,0));
        }
        summaryHost.addView(dash);
    }

    private View summaryMetric(String value,String label,String iconText){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(3),dp(4),dp(3),dp(2));
        box.setBackground(new TexturedDrawable(this,Color.argb(210,253,251,239),Color.argb(210,231,244,239),Color.argb(120,17,111,118),14f,1f,true));
        TextView icon=text(iconText,15,GOLD_DEEP,true);icon.setGravity(Gravity.CENTER);
        TextView v=text(value,17,INK,true);v.setGravity(Gravity.CENTER);v.setSingleLine(true);
        TextView l=text(label,8.8f,INK_SOFT,true);l.setGravity(Gravity.CENTER);l.setSingleLine(true);
        box.addView(icon,new LinearLayout.LayoutParams(-1,0,.8f));box.addView(v,new LinearLayout.LayoutParams(-1,0,1.1f));box.addView(l,new LinearLayout.LayoutParams(-1,0,.7f));
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(3),0,dp(3),0);wrap.addView(box,new LinearLayout.LayoutParams(-1,-1));return wrap;
    }

    private void renderDays(List<JSONObject> trips){
        listHost.removeAllViews();
        if(trips.isEmpty()){
            TextView empty=text("No hay viajes para estos filtros.",14,INK_SOFT,false);empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,dp(38),0,dp(38));listHost.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;
        }

        LinkedHashMap<String,ArrayList<JSONObject>> days=new LinkedHashMap<>();
        SimpleDateFormat keyFmt=new SimpleDateFormat("yyyy-MM-dd",locale);
        for(JSONObject o:trips){
            String key=keyFmt.format(new Date(o.optLong("started_at_ms",0)));
            days.computeIfAbsent(key,k->new ArrayList<>()).add(o);
        }

        SimpleDateFormat titleFmt=new SimpleDateFormat("EEEE d 'de' MMMM",locale);
        SimpleDateFormat monthFmt=new SimpleDateFormat("MMMM yyyy",locale);
        SimpleDateFormat dowFmt=new SimpleDateFormat("EEE",locale);
        SimpleDateFormat dayFmt=new SimpleDateFormat("d",locale);
        String lastMonth="";

        for(Map.Entry<String,ArrayList<JSONObject>> e:days.entrySet()){
            String dayKey=e.getKey();ArrayList<JSONObject> dayTrips=e.getValue();boolean expanded=dayKey.equals(expandedDay);
            long ts=dayTrips.get(0).optLong("started_at_ms",0);
            String month=cap(monthFmt.format(new Date(ts)));
            if(!month.equals(lastMonth)){
                TextView monthTitle=text(month.toUpperCase(locale),10.5f,GOLD_DEEP,true);
                monthTitle.setPadding(dp(5),dp(lastMonth.isEmpty()?3:11),0,dp(2));
                listHost.addView(monthTitle,new LinearLayout.LayoutParams(-1,-2));
                lastMonth=month;
            }

            double dkm=0,din=0;long dtime=0;
            for(JSONObject o:dayTrips){
                dkm+=o.optDouble("distance_m",0)/1000.0;
                if(!o.isNull("amount_uyu"))din+=Math.max(0,o.optDouble("amount_uyu",0));
                long s=o.optLong("started_at_ms",0),en=o.optLong("ended_at_ms",0);dtime+=en>s?en-s:o.optLong("moving_ms",0)+o.optLong("stopped_ms",0);
            }

            LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);group.setBackground(lightPanel(20));group.setElevation(dp(2));
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(9),dp(8),dp(8),dp(8));

            LinearLayout dateBadge=new LinearLayout(this);dateBadge.setOrientation(LinearLayout.VERTICAL);dateBadge.setGravity(Gravity.CENTER);
            dateBadge.setBackground(new TexturedDrawable(this,Color.argb(245,251,237,183),Color.argb(245,235,213,141),Color.argb(190,214,174,67),14f,1f,true));
            TextView dow=text(dowFmt.format(new Date(ts)).toUpperCase(locale),9.2f,INK,true);dow.setGravity(Gravity.CENTER);
            TextView dn=text(dayFmt.format(new Date(ts)),19,INK,true);dn.setGravity(Gravity.CENTER);
            dateBadge.addView(dow);dateBadge.addView(dn);head.addView(dateBadge,new LinearLayout.LayoutParams(dp(57),dp(56)));

            LinearLayout middle=new LinearLayout(this);middle.setOrientation(LinearLayout.VERTICAL);middle.setPadding(dp(10),0,dp(6),0);
            TextView date=text(cap(titleFmt.format(new Date(ts))),15.2f,INK,true);date.setSingleLine(true);
            String summary=dayTrips.size()+" viajes   ·   "+String.format(locale,"%.1f km",dkm)+"   ·   "+duration(dtime)+(din>0?"   ·   $ "+String.format(locale,"%.0f",din):"");
            TextView daySum=text(summary,10.3f,TEAL_DARK,true);daySum.setSingleLine(true);
            middle.addView(date);middle.addView(daySum,lp(3,0));head.addView(middle,new LinearLayout.LayoutParams(0,-2,1));

            TextView chevron=text(expanded?"⌃":"⌄",23,INK,true);chevron.setGravity(Gravity.CENTER);
            chevron.setBackground(new TexturedDrawable(this,Color.argb(225,255,255,250),Color.argb(225,234,244,240),Color.argb(120,9,47,57),21f,1f,true));
            head.addView(chevron,new LinearLayout.LayoutParams(dp(42),dp(42)));

            View.OnClickListener toggle=v->{expandedDay=expanded?null:dayKey;renderDays(trips);};
            head.setOnClickListener(toggle);chevron.setOnClickListener(toggle);
            group.addView(head,new LinearLayout.LayoutParams(-1,dp(74)));

            if(expanded){
                View div=divider();group.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));
                LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),dp(3),dp(8),dp(9));
                for(JSONObject o:dayTrips)body.addView(tripCard(o),lp(6,0));
                group.addView(body,new LinearLayout.LayoutParams(-1,-2));
            }
            listHost.addView(group,lp(6,0));
        }
    }

    private View tripCard(JSONObject o){
        String id=o.optString("trip_id","");
        long start=o.optLong("started_at_ms",0),end=o.optLong("ended_at_ms",0),moving=o.optLong("moving_ms",0),stopped=o.optLong("stopped_ms",0);
        double km=o.optDouble("distance_m",0)/1000.0,avg=o.optDouble("avg_speed_kmh",0),max=o.optDouble("max_speed_kmh",0);
        String from=o.optString("start_zone",""),to=o.optString("end_zone",""),type=o.optString("trip_type","other"),status=o.optString("trip_status","completed");
        double amount=o.isNull("amount_uyu")?0:o.optDouble("amount_uyu",0);
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm",locale);

        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(11),dp(9),dp(11),dp(9));
        card.setBackground(new TexturedDrawable(this,Color.argb(230,255,255,250),Color.argb(230,235,247,243),Color.argb(120,17,111,118),15f,1f,true));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=text(tf.format(new Date(start))+(end>start?"–"+tf.format(new Date(end)):""),11.5f,GOLD_DEEP,true);top.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        TextView tag=text(typeLabel(type)+("cancelled".equals(status)?" · CANCELADO":""),9.5f,"cancelled".equals(status)?RED:GREEN,true);tag.setGravity(Gravity.RIGHT);top.addView(tag);
        card.addView(top);

        TextView route=text((from.isEmpty()?"Origen":from)+"  →  "+(to.isEmpty()?"Destino":to),14.2f,INK,true);card.addView(route,lp(4,0));
        long total=end>start?end-start:moving+stopped;
        TextView details=text(String.format(locale,"%.1f km · %s · prom. %.0f · máx. %.0f km/h",km,duration(total),avg,max),10.4f,INK_SOFT,true);card.addView(details,lp(5,0));
        String extra="Movimiento "+duration(moving)+" · Detenido "+duration(stopped)+(amount>0?" · $ "+String.format(locale,"%.0f",amount):"");
        TextView bottom=text(extra,10.2f,amount>0?GOLD_DEEP:INK_SOFT,amount>0);card.addView(bottom,lp(3,0));
        TextView open=text("VER RECORRIDO  ›",10.2f,ROUTE,true);open.setGravity(Gravity.RIGHT);card.addView(open,lp(6,0));
        card.setOnClickListener(v->{Intent d=new Intent(this,TripDetailActivity.class);d.putExtra("trip_id",id);startActivity(d);});
        return card;
    }

    private View periodFilterRow(){
        String[][] values={{"Hoy","1"},{"7 días","7"},{"30 días","30"},{"Todo","0"}};
        LinearLayout row=chipRow();
        for(String[] x:values){int days=Integer.parseInt(x[1]);TextView chip=chip(x[0],periodDays==days);chip.setOnClickListener(v->{periodDays=days;expandedDay=null;savePrefs();buildUi();loadData();});addChip(row,chip);}
        return horizontal(row);
    }

    private View typeFilterRow(){
        String[][] values={{"Todos","all"},{"Uber","uber"},{"Cabify","cabify"},{"Personal","personal"},{"Otro","other"}};
        LinearLayout row=chipRow();
        for(String[] x:values){TextView chip=chip(x[0],typeFilter.equals(x[1]));chip.setOnClickListener(v->{typeFilter=x[1];expandedDay=null;savePrefs();buildUi();loadData();});addChip(row,chip);}
        return horizontal(row);
    }

    private View statusFilterRow(){
        String[][] values={{"Todos","all"},{"Completados","completed"},{"Cancelados","cancelled"}};
        LinearLayout row=chipRow();
        for(String[] x:values){TextView chip=chip(x[0],statusFilter.equals(x[1]));chip.setOnClickListener(v->{statusFilter=x[1];expandedDay=null;savePrefs();buildUi();loadData();});addChip(row,chip);}
        return horizontal(row);
    }

    private LinearLayout chipRow(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private HorizontalScrollView horizontal(LinearLayout row){HorizontalScrollView h=new HorizontalScrollView(this);h.setHorizontalScrollBarEnabled(false);h.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));return h;}
    private void addChip(LinearLayout row,TextView chip){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(36));p.setMargins(0,0,dp(7),0);row.addView(chip,p);}
    private TextView chip(String s,boolean selected){
        TextView v=text(s,10.8f,INK,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(14),0,dp(14),0);
        v.setBackground(selected
            ?new TexturedDrawable(this,Color.rgb(249,224,147),Color.rgb(230,191,84),Color.rgb(184,139,34),17f,1f,true)
            :new TexturedDrawable(this,Color.argb(225,250,251,246),Color.argb(225,226,241,237),Color.argb(145,17,111,118),17f,1f,true));
        return v;
    }

    private View filterLabel(String label,String icon){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView i=text(icon,16,GOLD_DEEP,true);i.setGravity(Gravity.CENTER);row.addView(i,new LinearLayout.LayoutParams(dp(30),dp(28)));
        TextView l=text(label,10.5f,INK,true);row.addView(l,new LinearLayout.LayoutParams(0,dp(28),1));return row;
    }

    private View divider(){View v=new View(this);v.setBackgroundColor(Color.argb(55,10,47,57));return v;}
    private TexturedDrawable lightPanel(float radius){return new TexturedDrawable(this,Color.argb(245,253,251,239),Color.argb(245,228,243,238),Color.argb(165,214,174,67),radius,1f,true);}
    private String periodLabel(){return periodDays==1?"Hoy":periodDays==7?"Últimos 7 días":periodDays==30?"Últimos 30 días":"Todo el historial";}
    private void savePrefs(){getSharedPreferences("history_prefs",MODE_PRIVATE).edit().putInt("period_days",periodDays).putString("type_filter",typeFilter).putString("status_filter",statusFilter).apply();}
    private void signalTrackingUi(boolean visible){SharedPreferences s=getSharedPreferences("tracking_state",MODE_PRIVATE);if(!s.getBoolean("shift_active",false))return;try{startService(new Intent(this,TrackingService.class).setAction(visible?TrackingService.ACTION_UI_VISIBLE:TrackingService.ACTION_UI_HIDDEN));}catch(Exception ignored){}}
    private long startOfToday(long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private String cap(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private String typeLabel(String s){if("uber".equals(s))return "UBER";if("cabify".equals(s))return "CABIFY";if("personal".equals(s))return "PERSONAL";return "OTRO";}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams lp(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,dp(bottom));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String duration(long ms){long t=Math.max(0,ms)/1000,h=t/3600,m=(t%3600)/60,s=t%60;return h>0?String.format(locale,"%d:%02d:%02d",h,m,s):String.format(locale,"%02d:%02d",m,s);}

    @Override protected void onResume(){super.onResume();signalTrackingUi(true);}
    @Override protected void onPause(){signalTrackingUi(false);super.onPause();}
}
