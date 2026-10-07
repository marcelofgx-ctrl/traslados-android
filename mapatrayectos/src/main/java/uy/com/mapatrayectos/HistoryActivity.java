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
    private static final int BG=Color.rgb(7,25,31),PANEL=Color.rgb(8,42,50),PANEL_2=Color.rgb(10,50,59),GOLD=Color.rgb(224,193,111),TEXT=Color.rgb(245,244,238),MUTED=Color.rgb(174,188,191),GREEN=Color.rgb(54,190,125),RED=Color.rgb(225,78,84),ROUTE=Color.rgb(73,199,225);

    private JSONArray allTrips=new JSONArray();
    private LinearLayout summaryHost,listHost;
    private int periodDays=30;
    private String typeFilter="all",statusFilter="all";
    private final LinkedHashSet<String> expandedDays=new LinkedHashSet<>();
    private boolean didInitialAutoExpand=false;
    private final Locale locale=new Locale("es","UY");

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        periodDays=getSharedPreferences("history_prefs",MODE_PRIVATE).getInt("period_days",30);
        typeFilter=getSharedPreferences("history_prefs",MODE_PRIVATE).getString("type_filter","all");
        statusFilter=getSharedPreferences("history_prefs",MODE_PRIVATE).getString("status_filter","all");
        buildUi();loadData();Api.init(this);Api.syncPendingAsync();
    }

    private void buildUi(){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(34));sc.addView(root,new ScrollView.LayoutParams(-1,-2));

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",false);back.setTextSize(27);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(46)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        TextView title=text("HISTORIAL",22,TEXT,true);TextView kicker=text("Jornadas y viajes · filtros y detalle",11.5f,GOLD,true);titles.addView(title);titles.addView(kicker);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(10),0,0,0);head.addView(titles,tp);root.addView(head,new LinearLayout.LayoutParams(-1,dp(60)));

        TextView sub=text("Agrupado por día. Tocá una fecha para desplegar u ocultar sus viajes.",12,MUTED,false);root.addView(sub,lp(2,10));

        summaryHost=new LinearLayout(this);summaryHost.setOrientation(LinearLayout.VERTICAL);root.addView(summaryHost,lp(0,10));

        TextView pLabel=text("PERÍODO",10.5f,GOLD,true);root.addView(pLabel,lp(2,5));
        root.addView(filterRow(new FilterItem("Hoy",1),new FilterItem("7 días",7),new FilterItem("30 días",30),new FilterItem("Todo",0)),lp(0,9));

        TextView tLabel=text("TIPO DE VIAJE",10.5f,GOLD,true);root.addView(tLabel,lp(1,5));
        root.addView(typeFilterRow(),lp(0,9));

        TextView sLabel=text("ESTADO",10.5f,GOLD,true);root.addView(sLabel,lp(1,5));
        root.addView(statusFilterRow(),lp(0,12));

        listHost=new LinearLayout(this);listHost.setOrientation(LinearLayout.VERTICAL);root.addView(listHost,new LinearLayout.LayoutParams(-1,-2));
        setContentView(sc);
    }

    private void loadData(){
        TrackDb db=new TrackDb(this);allTrips=db.listTrips();db.close();renderFiltered();
    }

    private void renderFiltered(){
        ArrayList<JSONObject> filtered=new ArrayList<>();
        long now=System.currentTimeMillis(),cutoff=periodDays<=0?Long.MIN_VALUE:startOfToday(now)-(long)(periodDays-1)*86400000L;
        for(int i=0;i<allTrips.length();i++){
            JSONObject o=allTrips.optJSONObject(i);if(o==null)continue;
            long start=o.optLong("started_at_ms",0);if(start<cutoff)continue;
            String type=o.optString("trip_type","other"),status=o.optString("trip_status","completed");
            if(!"all".equals(typeFilter)&&!typeFilter.equals(type))continue;
            if(!"all".equals(statusFilter)&&!statusFilter.equals(status))continue;
            filtered.add(o);
        }
        renderSummary(filtered);renderDays(filtered);
    }

    private void renderSummary(List<JSONObject> trips){
        summaryHost.removeAllViews();
        int completed=0,cancelled=0;double income=0,km=0;long totalMs=0,moving=0,stopped=0;
        for(JSONObject o:trips){
            km+=o.optDouble("distance_m",0)/1000.0;String st=o.optString("trip_status","completed");if("cancelled".equals(st))cancelled++;else completed++;
            if(!o.isNull("amount_uyu"))income+=Math.max(0,o.optDouble("amount_uyu",0));
            long start=o.optLong("started_at_ms",0),end=o.optLong("ended_at_ms",0);totalMs+=end>start?end-start:o.optLong("moving_ms",0)+o.optLong("stopped_ms",0);
            moving+=o.optLong("moving_ms",0);stopped+=o.optLong("stopped_ms",0);
        }
        LinearLayout dash=new LinearLayout(this);dash.setOrientation(LinearLayout.VERTICAL);dash.setPadding(dp(14),dp(13),dp(14),dp(13));dash.setBackground(panelGradient());
        LinearLayout first=new LinearLayout(this);first.setOrientation(LinearLayout.HORIZONTAL);
        first.addView(summaryMetric(String.valueOf(trips.size()),"VIAJES"),new LinearLayout.LayoutParams(0,dp(58),1));
        first.addView(summaryMetric(String.format(locale,"%.1f",km),"KM"),new LinearLayout.LayoutParams(0,dp(58),1));
        first.addView(summaryMetric("$ "+String.format(locale,"%.0f",income),"INGRESOS"),new LinearLayout.LayoutParams(0,dp(58),1));
        dash.addView(first);
        TextView line=text("Completados "+completed+" · Cancelados "+cancelled+" · Tiempo "+duration(totalMs),12,TEXT,true);dash.addView(line,lp(8,0));
        String efficiency=km>0&&income>0?String.format(locale," · $ %.0f/km",income/km):"";
        TextView detail=text("Movimiento "+duration(moving)+" · Detenido "+duration(stopped)+efficiency,11.5f,MUTED,false);dash.addView(detail,lp(3,0));
        summaryHost.addView(dash);
    }

    private void renderDays(List<JSONObject> trips){
        listHost.removeAllViews();
        if(trips.isEmpty()){
            TextView empty=text("No hay viajes para estos filtros.",15,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(36),0,dp(36));listHost.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;
        }
        LinkedHashMap<String,ArrayList<JSONObject>> days=new LinkedHashMap<>();
        SimpleDateFormat keyFmt=new SimpleDateFormat("yyyy-MM-dd",locale);
        for(JSONObject o:trips){String k=keyFmt.format(new Date(o.optLong("started_at_ms",0)));days.computeIfAbsent(k,x->new ArrayList<>()).add(o);}
        if(!didInitialAutoExpand&&!days.isEmpty()){expandedDays.add(days.keySet().iterator().next());didInitialAutoExpand=true;}

        SimpleDateFormat titleFmt=new SimpleDateFormat("EEEE d 'de' MMMM",locale);
        for(Map.Entry<String,ArrayList<JSONObject>> e:days.entrySet()){
            String day=e.getKey();ArrayList<JSONObject> dayTrips=e.getValue();boolean expanded=expandedDays.contains(day);
            LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);group.setBackground(rounded(Color.rgb(7,35,42),20,1,Color.rgb(47,91,101)));

            LinearLayout dh=new LinearLayout(this);dh.setGravity(Gravity.CENTER_VERTICAL);dh.setPadding(dp(14),dp(10),dp(10),dp(10));
            long ts=dayTrips.get(0).optLong("started_at_ms",0);LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);
            TextView date=text(cap(titleFmt.format(new Date(ts))),16,TEXT,true);left.addView(date);
            double dkm=0,din=0;for(JSONObject o:dayTrips){dkm+=o.optDouble("distance_m",0)/1000.0;if(!o.isNull("amount_uyu"))din+=Math.max(0,o.optDouble("amount_uyu",0));}
            TextView daySum=text(dayTrips.size()+" viajes · "+String.format(locale,"%.1f km",dkm)+(din>0?" · $ "+String.format(locale,"%.0f",din):""),11.5f,GOLD,true);left.addView(daySum,lp(2,0));
            dh.addView(left,new LinearLayout.LayoutParams(0,-2,1));TextView chevron=text(expanded?"⌃":"⌄",24,GOLD,true);chevron.setGravity(Gravity.CENTER);dh.addView(chevron,new LinearLayout.LayoutParams(dp(42),dp(42)));

            LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(10),0,dp(10),dp(10));body.setVisibility(expanded?View.VISIBLE:View.GONE);
            for(JSONObject o:dayTrips)body.addView(tripCard(o),lp(6,0));
            View.OnClickListener toggle=v->{if(expandedDays.contains(day))expandedDays.remove(day);else expandedDays.add(day);renderDays(trips);};
            dh.setOnClickListener(toggle);chevron.setOnClickListener(toggle);group.addView(dh);group.addView(body);listHost.addView(group,lp(7,0));
        }
    }

    private View tripCard(JSONObject o){
        String id=o.optString("trip_id","");long start=o.optLong("started_at_ms",0),end=o.optLong("ended_at_ms",0),moving=o.optLong("moving_ms",0),stopped=o.optLong("stopped_ms",0);
        double km=o.optDouble("distance_m",0)/1000.0,avg=o.optDouble("avg_speed_kmh",0),max=o.optDouble("max_speed_kmh",0);String from=o.optString("start_zone",""),to=o.optString("end_zone",""),type=o.optString("trip_type","other"),status=o.optString("trip_status","completed");double amount=o.isNull("amount_uyu")?0:o.optDouble("amount_uyu",0);
        SimpleDateFormat timeFmt=new SimpleDateFormat("HH:mm",locale);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(13),dp(11),dp(13),dp(11));card.setBackground(rounded(PANEL_2,16,1,Color.rgb(49,98,108)));
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);TextView time=text(timeFmt.format(new Date(start))+(end>start?"–"+timeFmt.format(new Date(end)):""),13,GOLD,true);line.addView(time,new LinearLayout.LayoutParams(0,-2,1));TextView tag=text(typeLabel(type)+("cancelled".equals(status)?" · CANCELADO":""),10.5f,"cancelled".equals(status)?RED:GREEN,true);tag.setGravity(Gravity.RIGHT);line.addView(tag);card.addView(line);
        TextView route=text((from.isEmpty()?"Origen":from)+"  →  "+(to.isEmpty()?"Destino":to),16,TEXT,true);card.addView(route,lp(5,0));
        long total=end>start?end-start:moving+stopped;TextView summary=text(String.format(locale,"%.1f km · %s · prom. %.0f · máx. %.0f km/h",km,duration(total),avg,max),11.7f,TEXT,true);card.addView(summary,lp(6,0));
        String extra="Movimiento "+duration(moving)+" · Detenido "+duration(stopped);if(amount>0)extra+=" · $ "+String.format(locale,"%.0f",amount);TextView detail=text(extra,11.3f,amount>0?GOLD:MUTED,amount>0);card.addView(detail,lp(4,0));
        TextView open=text("VER RECORRIDO  ›",10.5f,ROUTE,true);open.setGravity(Gravity.RIGHT);card.addView(open,lp(7,0));
        card.setOnClickListener(v->{Intent d=new Intent(this,TripDetailActivity.class);d.putExtra("trip_id",id);startActivity(d);});return card;
    }

    private View filterRow(FilterItem... items){
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);sc.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        for(FilterItem x:items){boolean selected=periodDays==x.days;Button b=chip(x.label,selected);b.setOnClickListener(v->{periodDays=x.days;expandedDays.clear();didInitialAutoExpand=false;savePrefs();buildUi();loadData();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(38));p.setMargins(0,0,dp(7),0);row.addView(b,p);}return sc;
    }

    private View typeFilterRow(){
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);sc.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        String[][] values={{"Todos","all"},{"Uber","uber"},{"Cabify","cabify"},{"Personal","personal"},{"Otro","other"}};
        for(String[] x:values){Button b=chip(x[0],typeFilter.equals(x[1]));b.setOnClickListener(v->{typeFilter=x[1];expandedDays.clear();didInitialAutoExpand=false;savePrefs();buildUi();loadData();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(38));p.setMargins(0,0,dp(7),0);row.addView(b,p);}return sc;
    }

    private View statusFilterRow(){
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);sc.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        String[][] values={{"Todos","all"},{"Completados","completed"},{"Cancelados","cancelled"}};
        for(String[] x:values){Button b=chip(x[0],statusFilter.equals(x[1]));b.setOnClickListener(v->{statusFilter=x[1];expandedDays.clear();didInitialAutoExpand=false;savePrefs();buildUi();loadData();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(38));p.setMargins(0,0,dp(7),0);row.addView(b,p);}return sc;
    }

    private void savePrefs(){getSharedPreferences("history_prefs",MODE_PRIVATE).edit().putInt("period_days",periodDays).putString("type_filter",typeFilter).putString("status_filter",statusFilter).apply();}
    private void signalTrackingUi(boolean visible){SharedPreferences s=getSharedPreferences("tracking_state",MODE_PRIVATE);if(!s.getBoolean("shift_active",false))return;try{startService(new Intent(this,TrackingService.class).setAction(visible?TrackingService.ACTION_UI_VISIBLE:TrackingService.ACTION_UI_HIDDEN));}catch(Exception ignored){}}
    private long startOfToday(long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private String cap(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private String typeLabel(String s){if("uber".equals(s))return "UBER";if("cabify".equals(s))return "CABIFY";if("personal".equals(s))return "PERSONAL";return "OTRO";}

    private LinearLayout summaryMetric(String value,String label){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);TextView v=text(value,18,TEXT,true);v.setGravity(Gravity.CENTER);TextView l=text(label,9.5f,GOLD,true);l.setGravity(Gravity.CENTER);box.addView(v);box.addView(l,lp(2,0));return box;}
    private Button chip(String s,boolean selected){Button b=button(s,selected);b.setTextSize(11);b.setPadding(dp(13),0,dp(13),0);return b;}
    private Button button(String s,boolean selected){Button b=new Button(this);b.setText(s);b.setTextColor(selected?BG:TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(rounded(selected?GOLD:PANEL,15,1,selected?GOLD:Color.rgb(45,86,96)));return b;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable panelGradient(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(5,29,36),Color.rgb(9,52,61),Color.rgb(6,33,40)});g.setCornerRadius(dp(20));g.setStroke(dp(1),Color.argb(160,224,193,111));return g;}
    private GradientDrawable rounded(int fill,int radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}
    private LinearLayout.LayoutParams lp(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,dp(bottom));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String duration(long ms){long t=Math.max(0,ms)/1000,h=t/3600,m=(t%3600)/60,s=t%60;return h>0?String.format(locale,"%d:%02d:%02d",h,m,s):String.format(locale,"%02d:%02d",m,s);}
    private static final class FilterItem{final String label;final int days;FilterItem(String label,int days){this.label=label;this.days=days;}}
    @Override protected void onResume(){super.onResume();signalTrackingUi(true);}
    @Override protected void onPause(){signalTrackingUi(false);super.onPause();}
}
