package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryActivity extends Activity {
    // R17 visual refinement: cleaner surfaces, stronger contrast and almost no visual noise.
    private static final int BG1=Color.rgb(249,247,239),BG2=Color.rgb(242,248,244);
    private static final int INK=Color.rgb(5,52,63),INK_SOFT=Color.rgb(79,101,105);
    private static final int TEAL=Color.rgb(18,105,113),TEAL_DARK=Color.rgb(7,63,72);
    private static final int GOLD=Color.rgb(220,181,74),GOLD_DEEP=Color.rgb(164,112,15);
    private static final int GREEN=Color.rgb(18,145,96),RED=Color.rgb(205,55,63),ROUTE=Color.rgb(20,137,164);
    private static final int PANEL=Color.rgb(255,253,247),PANEL_ALT=Color.rgb(247,250,246);
    private static final int BORDER_GOLD=Color.rgb(224,191,102),BORDER_TEAL=Color.rgb(119,165,168);
    private static final int WHITE=Color.rgb(253,252,248);

    private JSONArray allTrips=new JSONArray();
    private final HashMap<String,JSONObject> shiftsById=new HashMap<>();
    private LinearLayout summaryHost,listHost;
    private boolean endpointBackfillStarted=false;
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
        sc.setBackground(roundedGradient(BG1,BG2,Color.TRANSPARENT,0f,0f));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int baseLeft=dp(12),baseTop=dp(10),baseRight=dp(12),baseBottom=dp(34);
        root.setPadding(baseLeft,baseTop,baseRight,baseBottom);
        if(Build.VERSION.SDK_INT>=23){
            root.setOnApplyWindowInsetsListener((v,insets)->{
                int topInset,bottomInset;
                if(Build.VERSION.SDK_INT>=30){
                    topInset=insets.getInsets(WindowInsets.Type.statusBars()).top;
                    bottomInset=insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                }else{
                    topInset=insets.getSystemWindowInsetTop();
                    bottomInset=insets.getSystemWindowInsetBottom();
                }
                v.setPadding(baseLeft,baseTop+topInset,baseRight,baseBottom+bottomInset);
                return insets;
            });
            root.requestApplyInsets();
        }
        sc.addView(root,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10),dp(8),dp(10),dp(8));
        header.setBackground(roundedGradient(Color.rgb(5,49,58),Color.rgb(10,78,84),Color.argb(210,213,177,78),22f,1f));
        header.setElevation(dp(5));

        TextView back=text("‹",28,INK,true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Volver al mapa");
        back.setBackground(roundedGradient(Color.rgb(255,244,205),Color.rgb(239,219,158),Color.argb(210,214,174,67),18f,1f));
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
        badge.setBackground(roundedGradient(Color.rgb(13,86,92),Color.rgb(18,105,109),Color.argb(190,214,174,67),16f,1f));
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
        shiftsById.clear();
        JSONArray shifts=db.listShifts();
        for(int i=0;i<shifts.length();i++){JSONObject s=shifts.optJSONObject(i);if(s!=null)shiftsById.put(s.optString("shift_id",""),s);}
        db.close();
        expandedDay=null;
        renderFiltered();
        if(!endpointBackfillStarted){
            endpointBackfillStarted=true;
            new Thread(()->{
                int changed=AddressResolver.backfillMissingTripLocations(getApplicationContext());
                if(changed>0){Api.syncPendingAsync();runOnUiThread(()->{TrackDb x=new TrackDb(this);allTrips=x.listTrips();x.close();renderFiltered();});}
            },"history-address-backfill").start();
        }
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
        int completed=0,cancelled=0,uber=0,cabify=0,personal=0,other=0;double income=0,km=0;long totalMs=0,moving=0,stopped=0;
        for(JSONObject o:trips){
            km+=o.optDouble("distance_m",0)/1000.0;
            String st=o.optString("trip_status","completed");if("cancelled".equals(st))cancelled++;else completed++;
            String tp=o.optString("trip_type","other");if("uber".equals(tp))uber++;else if("cabify".equals(tp))cabify++;else if("personal".equals(tp))personal++;else other++;
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
        TextView icon=text("▥",20,GOLD_DEEP,true);icon.setGravity(Gravity.CENTER);icon.setBackground(roundedGradient(Color.rgb(255,243,199),Color.rgb(243,222,157),Color.argb(180,214,174,67),16f,1f));
        titleRow.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(9),0,0,0);
        copy.addView(text("RESUMEN DEL PERÍODO",13.5f,INK,true));
        copy.addView(text(periodLabel(),10.5f,INK_SOFT,false));
        titleRow.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        dash.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(summaryMetric(String.valueOf(trips.size()),"VIAJES","╱╲"),new LinearLayout.LayoutParams(0,dp(70),1));
        metrics.addView(summaryMetric(String.format(locale,"%.1f",km),"KM","⌖"),new LinearLayout.LayoutParams(0,dp(70),1));
        metrics.addView(summaryMetric(duration(totalMs),"TIEMPO","◷"),new LinearLayout.LayoutParams(0,dp(70),1));
        metrics.addView(summaryMetric("$ "+String.format(locale,"%.0f",income),"INGRESOS","●"),new LinearLayout.LayoutParams(0,dp(70),1));
        dash.addView(metrics,lp(3,5));

        TextView line=text("Completados "+completed+"   ·   Cancelados "+cancelled+"   ·   Movimiento "+duration(moving)+"   ·   Detenido "+duration(stopped),10.7f,INK_SOFT,true);
        line.setGravity(Gravity.CENTER);
        dash.addView(line,lp(3,0));
        TextView platforms=text("Uber "+uber+"   ·   Cabify "+cabify+"   ·   Personal "+personal+"   ·   Otro "+other,10.4f,TEAL_DARK,true);
        platforms.setGravity(Gravity.CENTER);dash.addView(platforms,lp(5,0));

        if(km>0&&income>0){
            double perHour=totalMs>0?income/(totalMs/3600000.0):0;TextView eff=text(String.format(locale,"Rendimiento  $ %.0f/km   ·   $ %.0f/h",income/km,perHour),10.5f,GOLD_DEEP,true);
            eff.setGravity(Gravity.CENTER);dash.addView(eff,lp(5,0));
        }
        summaryHost.addView(dash);
    }

    private View summaryMetric(String value,String label,String iconText){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(3),dp(4),dp(3),dp(2));
        box.setBackground(roundedGradient(Color.rgb(255,254,249),Color.rgb(247,250,246),Color.argb(115,17,111,118),14f,0.8f));
        TextView icon=text(iconText,15,GOLD_DEEP,true);icon.setGravity(Gravity.CENTER);
        TextView v=text(value,18.2f,INK,true);v.setGravity(Gravity.CENTER);v.setSingleLine(true);
        TextView l=text(label,9.0f,INK_SOFT,true);l.setGravity(Gravity.CENTER);l.setSingleLine(true);
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
            dateBadge.setBackground(roundedGradient(Color.rgb(255,244,199),Color.rgb(244,222,157),Color.argb(200,214,174,67),14f,1f));
            TextView dow=text(dowFmt.format(new Date(ts)).toUpperCase(locale),9.2f,INK,true);dow.setGravity(Gravity.CENTER);
            TextView dn=text(dayFmt.format(new Date(ts)),19,INK,true);dn.setGravity(Gravity.CENTER);
            dateBadge.addView(dow);dateBadge.addView(dn);head.addView(dateBadge,new LinearLayout.LayoutParams(dp(57),dp(56)));

            LinearLayout middle=new LinearLayout(this);middle.setOrientation(LinearLayout.VERTICAL);middle.setPadding(dp(10),0,dp(6),0);
            TextView date=text(cap(titleFmt.format(new Date(ts))),15.2f,INK,true);date.setSingleLine(true);
            String summary=dayTrips.size()+" viajes   ·   "+String.format(locale,"%.1f km",dkm)+"   ·   "+duration(dtime)+(din>0?"   ·   $ "+String.format(locale,"%.0f",din):"");
            TextView daySum=text(summary,10.3f,TEAL_DARK,true);daySum.setSingleLine(true);
            middle.addView(date);middle.addView(daySum,lp(3,0));head.addView(middle,new LinearLayout.LayoutParams(0,-2,1));

            TextView chevron=text(expanded?"⌃":"⌄",23,INK,true);chevron.setGravity(Gravity.CENTER);
            chevron.setBackground(roundedGradient(Color.rgb(255,255,252),Color.rgb(242,248,246),Color.argb(145,9,73,82),21f,1f));
            head.addView(chevron,new LinearLayout.LayoutParams(dp(42),dp(42)));

            View.OnClickListener toggle=v->{expandedDay=expanded?null:dayKey;renderDays(trips);};
            head.setOnClickListener(toggle);chevron.setOnClickListener(toggle);
            group.addView(head,new LinearLayout.LayoutParams(-1,dp(74)));

            if(expanded){
                View div=divider();group.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));
                LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),dp(3),dp(8),dp(9));
                LinkedHashMap<String,ArrayList<JSONObject>> byShift=new LinkedHashMap<>();
                for(JSONObject trip:dayTrips){String sid=trip.optString("shift_id","");byShift.computeIfAbsent(sid,k->new ArrayList<>()).add(trip);}
                int shiftNo=1;
                for(Map.Entry<String,ArrayList<JSONObject>> se:byShift.entrySet()){
                    ArrayList<JSONObject> shiftTrips=se.getValue();
                    if(byShift.size()>1)body.addView(shiftHeader(se.getKey(),shiftTrips,shiftNo++),lp(7,1));
                    for(JSONObject o:shiftTrips)body.addView(tripCard(o),lp(6,0));
                }
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
        card.setBackground(roundedGradient(Color.rgb(255,255,252),Color.rgb(248,251,248),Color.argb(130,17,111,118),15f,1f));
        card.setElevation(dp(1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=text(tf.format(new Date(start))+(end>start?"–"+tf.format(new Date(end)):""),11.5f,GOLD_DEEP,true);top.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        TextView tag=text(typeLabel(type)+("cancelled".equals(status)?" · CANCELADO":""),9.5f,"cancelled".equals(status)?RED:GREEN,true);tag.setGravity(Gravity.RIGHT);top.addView(tag);
        card.addView(top);

        double startLat=o.isNull("start_lat")?Double.NaN:o.optDouble("start_lat"),startLon=o.isNull("start_lon")?Double.NaN:o.optDouble("start_lon");
        double endLat=o.isNull("end_lat")?Double.NaN:o.optDouble("end_lat"),endLon=o.isNull("end_lon")?Double.NaN:o.optDouble("end_lon");
        String startAddress=endpointText(o,true),endAddress=endpointText(o,false);
        card.addView(endpointRow("ORIGEN",startAddress,startLat,startLon,GREEN),lp(5,0));
        card.addView(endpointRow("DESTINO",endAddress,endLat,endLon,RED),lp(3,0));
        long total=end>start?end-start:moving+stopped;
        TextView details=text(String.format(locale,"%.1f km · %s · prom. %.0f · máx. %.0f km/h",km,duration(total),avg,max),10.4f,INK_SOFT,true);card.addView(details,lp(5,0));
        String extra="Movimiento "+duration(moving)+" · Detenido "+duration(stopped)+(amount>0?" · $ "+String.format(locale,"%.0f",amount):"");
        TextView bottom=text(extra,10.2f,amount>0?GOLD_DEEP:INK_SOFT,amount>0);card.addView(bottom,lp(3,0));
        TextView open=text("VER RECORRIDO  ›",10.2f,ROUTE,true);open.setGravity(Gravity.RIGHT);card.addView(open,lp(6,0));
        card.setOnClickListener(v->{Intent d=new Intent(this,TripDetailActivity.class);d.putExtra("trip_id",id);startActivity(d);});
        return card;
    }

    private View shiftHeader(String shiftId,List<JSONObject> trips,int number){
        long start=Long.MAX_VALUE,end=0;double km=0;for(JSONObject t:trips){start=Math.min(start,t.optLong("started_at_ms",Long.MAX_VALUE));end=Math.max(end,t.optLong("ended_at_ms",0));km+=t.optDouble("distance_m",0)/1000.0;}
        JSONObject shift=shiftsById.get(shiftId);if(shift!=null){start=shift.optLong("started_at_ms",start);end=shift.optLong("ended_at_ms",end);}
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm",locale);
        String times=start<Long.MAX_VALUE?tf.format(new Date(start))+(end>start?"–"+tf.format(new Date(end)):""):"";
        TextView v=text("JORNADA "+number+"   ·   "+times+"   ·   "+trips.size()+" viajes   ·   "+String.format(locale,"%.1f km",km),9.8f,GOLD_DEEP,true);
        v.setPadding(dp(8),dp(5),dp(8),dp(5));
        v.setBackground(roundedGradient(Color.rgb(255,244,204),Color.rgb(245,225,169),Color.argb(145,214,174,67),12f,0.8f));
        return v;
    }

    private String endpointText(JSONObject o,boolean start){
        String prefix=start?"start_":"end_";String address=o.optString(prefix+"address","");
        if(!address.isEmpty())return address;
        String zone=o.optString(prefix+"zone","");
        if(!o.isNull(prefix+"lat")&&!o.isNull(prefix+"lon"))return AddressResolver.fallback(o.optDouble(prefix+"lat"),o.optDouble(prefix+"lon"),zone);
        return zone.isEmpty()?(start?"Origen sin dirección":"Destino sin dirección"):zone;
    }

    private View endpointRow(String label,String address,double lat,double lon,int accent){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot=text("●",10,accent,true);dot.setGravity(Gravity.CENTER);row.addView(dot,new LinearLayout.LayoutParams(dp(20),dp(32)));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);
        TextView l=text(label,8.7f,GOLD_DEEP,true);TextView a=text(address,11.6f,INK,true);a.setSingleLine(true);a.setEllipsize(TextUtils.TruncateAt.END);txt.addView(l);txt.addView(a);
        row.addView(txt,new LinearLayout.LayoutParams(0,dp(38),1));
        if(!Double.isNaN(lat)&&!Double.isNaN(lon)){
            TextView nav=text("↗",18,TEAL_DARK,true);nav.setGravity(Gravity.CENTER);nav.setContentDescription("Navegar a "+label.toLowerCase(locale));
            nav.setBackground(roundedGradient(Color.rgb(255,255,251),Color.rgb(239,248,245),Color.argb(155,17,111,118),17f,1f));
            nav.setOnClickListener(v->openNavigation(lat,lon,address));
            row.addView(nav,new LinearLayout.LayoutParams(dp(38),dp(36)));
        }
        return row;
    }

    private void openNavigation(double lat,double lon,String label){
        try{
            Uri u=Uri.parse("google.navigation:q="+lat+","+lon+"&mode=d");Intent i=new Intent(Intent.ACTION_VIEW,u);i.setPackage("com.google.android.apps.maps");startActivity(i);
        }catch(Exception e){
            Uri u=Uri.parse("geo:"+lat+","+lon+"?q="+lat+","+lon+"("+Uri.encode(label)+")");startActivity(new Intent(Intent.ACTION_VIEW,u));
        }
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
            ?roundedGradient(Color.rgb(255,226,139),Color.rgb(239,199,91),Color.rgb(181,133,26),17f,1f)
            :roundedGradient(Color.rgb(255,255,251),Color.rgb(245,249,247),Color.argb(175,17,111,118),17f,1f));
        return v;
    }

    private View filterLabel(String label,String icon){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView i=text(icon,16,GOLD_DEEP,true);i.setGravity(Gravity.CENTER);row.addView(i,new LinearLayout.LayoutParams(dp(30),dp(28)));
        TextView l=text(label,10.5f,INK,true);row.addView(l,new LinearLayout.LayoutParams(0,dp(28),1));return row;
    }

    private View divider(){View v=new View(this);v.setBackgroundColor(Color.argb(48,10,47,57));return v;}
    private GradientDrawable lightPanel(float radius){return roundedGradient(PANEL,PANEL_ALT,Color.argb(190,224,191,102),radius,1f);}
    private GradientDrawable roundedGradient(int c1,int c2,int strokeColor,float radiusDp,float strokeDp){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{c1,c2});
        g.setCornerRadius(dpF(radiusDp));
        if(strokeDp>0f&&Color.alpha(strokeColor)>0)g.setStroke(Math.max(1,Math.round(dpF(strokeDp))),strokeColor);
        return g;
    }
    private float dpF(float v){return v*getResources().getDisplayMetrics().density;}
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
