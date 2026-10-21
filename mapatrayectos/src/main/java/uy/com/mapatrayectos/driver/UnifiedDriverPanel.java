package uy.com.mapatrayectos.driver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import uy.com.mapatrayectos.Api;
import uy.com.mapatrayectos.R;

/** Brand-new native Conductor view INSIDE Mapa (no second app or login).
 * All UI and polling are independent of legacy Conductor MainActivity.
 * The backend remains the authoritative reservation state machine.
 */
public final class UnifiedDriverPanel extends FrameLayout {
    private final Activity host;
    private final Runnable onReturnToMapa;
    // Exact user-supplied design reference: #000000 background, #242424
    // container, black tiles, gray Reject and green Accept. No gold UI.
    private static final int PETROL=Color.rgb(0,0,0);
    private static final int CARD=Color.rgb(37,37,37);
    private static final int GOLD=Color.rgb(255,255,255);
    private static final int WHITE=Color.rgb(255,255,255);
    private static final int MUTED=Color.rgb(174,174,174);
    private static final int GREEN=Color.rgb(0,120,89);
    private static final int RED=Color.rgb(207,67,68);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean loading=new AtomicBoolean(false);
    private UnifiedDriverGateway gateway;
    private LinearLayout page,list;
    private ScrollView scroller;
    private TextView connection,newCount,acceptedCount,travelCount,heading;
    private LinearLayout navigation;
    private enum Screen { SOLICITUDES, AGENDA, HISTORIAL, RESUMEN }
    private enum Period { TODOS, HOY, SEMANA, MES }
    private Screen screen=Screen.SOLICITUDES;
    private Period period=Period.TODOS;
    private JSONArray activeRows=new JSONArray(), historyRows=new JSONArray();
    private boolean activeLoaded=false, historyLoaded=false, foreground=false, syncError=false;
    private final Set<String> openCards=new HashSet<>();
    private final Set<String> openDays=new HashSet<>();
    private boolean daysInitialized=false;
    private long lastSuccessfulFetch=0;
    private float downX,downY;
    private boolean swipeArmed=false;
    private final Runnable refreshTick=new Runnable(){
        @Override public void run(){
            if(!foreground)return;
            reload(false);
            ui.postDelayed(this,18000);
        }
    };

    public UnifiedDriverPanel(Activity activity,Runnable returnToMapa){
        super(activity);
        host=activity;
        onReturnToMapa=returnToMapa;
        setBackgroundColor(PETROL);
        Api.init(host.getApplicationContext());
        gateway=new UnifiedDriverGateway(host);
        build();
    }
    /** Keep Maps running while the adjacent native page is visible. */
    public void showPage(){
        foreground=true;
        ui.removeCallbacks(refreshTick);
        render();
        reload(false);
        ui.postDelayed(refreshTick,18000);
    }
    public void hidePage(){
        foreground=false;
        ui.removeCallbacks(refreshTick);
    }
    public void dispose(){
        hidePage();
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
    }
    public void handleBack(){
        if(screen!=Screen.SOLICITUDES)openScreen(Screen.SOLICITUDES);
        else backToMap();
    }
    private void backToMap(){onReturnToMapa.run();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=event.getX();downY=event.getY();swipeArmed=true;break;
            case MotionEvent.ACTION_POINTER_DOWN:swipeArmed=false;break;
            case MotionEvent.ACTION_CANCEL:swipeArmed=false;break;
            case MotionEvent.ACTION_UP:
                float density=getResources().getDisplayMetrics().density;
                float dx=event.getX()-downX,dy=event.getY()-downY;
                boolean valid=swipeArmed&&downX>=24*density&&downX<=105*density
                    &&downY>=160*density&&downY<=host.getWindow().getDecorView().getHeight()-160*density
                    &&dx>=78*density&&dx>Math.abs(dy)*1.65f;
                swipeArmed=false;
                if(valid){
                    MotionEvent cancel=MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);cancel.recycle();
                    backToMap();return true;
                }
        }
        return super.dispatchTouchEvent(event);
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int fill,int edge,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),edge);
        return g;
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(host);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private TextView text(String value,int size,int color,boolean strong){
        TextView t=new TextView(host);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setLineSpacing(dp(2),1f);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button btn(String title,boolean primary){
        Button b=new Button(host);b.setText(title);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(primary?PETROL:WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setPadding(dp(8),0,dp(8),0);
        b.setBackground(bg(primary?GOLD:CARD,primary?GOLD:0xff477379,12));
        return b;
    }
    private void gap(LinearLayout target,int h){
        View spacer=new View(host);
        target.addView(spacer,new LinearLayout.LayoutParams(1,dp(h)));
    }
    private LinearLayout row(){
        LinearLayout v=new LinearLayout(host);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private LinearLayout.LayoutParams match(int bottomMarginDp){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(bottomMarginDp);
        return lp;
    }
    private TextView center(String value,int sp,int color,boolean strong){
        TextView t=text(value,sp,color,strong);
        t.setGravity(Gravity.CENTER);
        return t;
    }
    private TextView metric(LinearLayout strip,String category){
        LinearLayout tile=vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(bg(Color.BLACK,Color.BLACK,16));
        tile.setPadding(dp(4),dp(9),dp(4),dp(9));
        TextView count=center("—",31,WHITE,true);
        tile.addView(count,new LinearLayout.LayoutParams(-1,0,1));
        tile.addView(center(category,13,MUTED,false));
        LinearLayout.LayoutParams tileLp=new LinearLayout.LayoutParams(0,dp(91),1);
        tileLp.setMargins(dp(4),0,dp(4),0);
        strip.addView(tile,tileLp);
        return count;
    }

    private void openScreen(Screen next){
        if(screen==next)return;
        screen=next;
        period=Period.TODOS;
        openDays.clear();
        daysInitialized=false;
        render();
        scroller.scrollTo(0,0);
        reload(true);
    }
    private void addTab(LinearLayout bar,Screen view,String label){
        TextView tab=center(label,11,screen==view?WHITE:MUTED,screen==view);
        tab.setSingleLine(true);
        tab.setGravity(Gravity.CENTER);
        tab.setBackground(bg(screen==view?Color.BLACK:CARD,
            screen==view?GREEN:CARD,12));
        tab.setContentDescription("Ver "+label);
        tab.setOnClickListener(v->openScreen(view));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1);
        p.setMargins(dp(2),0,dp(2),0);
        bar.addView(tab,p);
    }
    private void buildNavigation(){
        navigation.removeAllViews();
        addTab(navigation,Screen.SOLICITUDES,"Solicitudes");
        addTab(navigation,Screen.AGENDA,"Agenda");
        addTab(navigation,Screen.HISTORIAL,"Historial");
        addTab(navigation,Screen.RESUMEN,"Resumen");
    }
    private void build(){
        setBackgroundColor(PETROL);
        ScrollView viewport=new ScrollView(host);
        // Reference layout: the charcoal surface fills the usable device
        // height, but real reservation cards still scroll when needed.
        viewport.setFillViewport(true);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.setClipToPadding(false);
        viewport.setBackgroundColor(PETROL);
        int inset=dp(25);
        int statusId=getResources().getIdentifier("status_bar_height","dimen","android");
        if(statusId>0)inset=Math.max(inset,getResources().getDimensionPixelSize(statusId));
        int navInset=dp(29);
        int navId=getResources().getIdentifier("navigation_bar_height","dimen","android");
        if(navId>0)navInset=Math.max(navInset,getResources().getDimensionPixelSize(navId));
        viewport.setPadding(dp(16),inset+dp(7),dp(16),navInset+dp(22));
        addView(viewport,new FrameLayout.LayoutParams(-1,-1));

        page=vertical();
        page.setBackground(bg(CARD,CARD,24));
        page.setPadding(dp(13),dp(15),dp(13),dp(15));
        viewport.addView(page,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=row();
        LinearLayout titleGroup=vertical();
        titleGroup.addView(text("QUICK TRIP",13,MUTED,false));
        gap(titleGroup,9);
        heading=text("Conductor",28,WHITE,true);
        titleGroup.addView(heading);
        header.addView(titleGroup,new LinearLayout.LayoutParams(0,-2,1));
        connection=text("○ Jornada detenida",12,MUTED,false);
        connection.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        header.addView(connection,new LinearLayout.LayoutParams(-2,dp(54)));
        page.addView(header,match(16));

        LinearLayout strip=row();
        newCount=metric(strip,"Nuevas");
        acceptedCount=metric(strip,"Aceptadas");
        travelCount=metric(strip,"En viaje");
        page.addView(strip,match(14));

        // All four destinations remain inside Conductor; Mapa's view is untouched.
        navigation=row();
        page.addView(navigation,match(13));

        // One outer ScrollView prevents the weighted nested container from stretching.
        scroller=viewport;
        list=vertical();
        page.addView(list,match(12));
        // Flexible breathing room belongs INSIDE the charcoal panel.
        // This absorbs spare height when idle and collapses when reservations
        // need vertical space; do not overlay any element on Mapa.
        View stretch=new View(host);
        page.addView(stretch,new LinearLayout.LayoutParams(1,0,1));

        TextView swipe=center("➜   Deslizar a la derecha para volver a Mapa",12,MUTED,false);
        swipe.setContentDescription("Deslizá desde el borde izquierdo hacia la derecha para volver a Mapa");
        swipe.setPadding(0,dp(5),0,dp(4));
        page.addView(swipe,match(0));
        render();
    }

    private void reload(boolean moveTop){
        if(!gateway.linked()){render();return;}
        if(!loading.compareAndSet(false,true))return;
        final Screen requestedScreen=screen;
        final boolean loadArchive=screen==Screen.HISTORIAL||screen==Screen.RESUMEN;
        io.execute(()->{
            try{
                final JSONArray answer=loadArchive?gateway.history():gateway.requests();
                host.runOnUiThread(()->{
                    loading.set(false);
                    if(host.isFinishing()||host.isDestroyed())return;
                    if(loadArchive){historyRows=answer;historyLoaded=true;}
                    else{activeRows=answer;activeLoaded=true;}
                    lastSuccessfulFetch=System.currentTimeMillis();
                    syncError=false;
                    if(screen!=requestedScreen){reload(true);return;}
                    render();
                    if(moveTop)scroller.smoothScrollTo(0,0);
                });
            }catch(Exception e){
                host.runOnUiThread(()->{
                    loading.set(false);
                    if(host.isFinishing()||host.isDestroyed())return;
                    syncError=true;
                    render();
                });
            }
        });
    }
    private boolean isArchive(){return screen==Screen.HISTORIAL||screen==Screen.RESUMEN;}
    private void render(){
        boolean started=host.getSharedPreferences("tracking_state",Context.MODE_PRIVATE)
            .getBoolean("shift_active",false);
        connection.setText((started?"✓ Jornada activa":"○ Jornada detenida")
            +(syncError?" · Sin sincronizar":""));
        connection.setTextColor(started?GREEN:MUTED);
        buildNavigation();
        int pending=0,accepted=0,traveling=0;
        for(int i=0;i<activeRows.length();i++){
            JSONObject r=activeRows.optJSONObject(i);if(r==null)continue;
            String status=r.optString("status");
            if("PENDIENTE".equals(status))pending++;
            if("ACEPTADA".equals(status)||"CONFIRMADA".equals(status))accepted++;
            if("EN_VIAJE".equals(status))traveling++;
        }
        if(gateway.linked()&&activeLoaded){
            newCount.setText(String.format(Locale.ROOT,"%02d",pending));
            acceptedCount.setText(String.format(Locale.ROOT,"%02d",accepted));
            travelCount.setText(String.format(Locale.ROOT,"%02d",traveling));
        }else{
            newCount.setText("—");acceptedCount.setText("—");travelCount.setText("—");
        }

        int oldY=scroller.getScrollY();
        list.removeAllViews();
        if(!gateway.linked()){
            emptyCard("Conductor sin vincular",
                "Para ver solicitudes, agenda e histórico, autorizá las reservas desde Mapa → Conductor y recogidas. La jornada y el GPS siguen en Mapa.");
            // Footer already contains the single return gesture.
            // No duplicate "Volver" button and no PIN form here.
            return;
        }
        if(syncError){
            TextView error=text("Sin conexión actual. Se conserva la última información recibida.",12,MUTED,false);
            error.setPadding(dp(8),dp(4),dp(8),dp(10));
            list.addView(error,match(0));
        }
        boolean loaded=isArchive()?historyLoaded:activeLoaded;
        if(!loaded){
            emptyCard(syncError?"No se pudieron cargar los datos":"Cargando "+screenTitle().toLowerCase(Locale.ROOT)+"…",
                syncError?"Revisá Internet y volvé a intentar.":"Esperando respuesta de las reservas autorizadas.");
            secondary(list,"REINTENTAR",()->reload(false));
            return;
        }

        if(screen!=Screen.SOLICITUDES)showPeriodFilters();
        if(screen==Screen.RESUMEN){renderSummary();return;}

        JSONArray source=isArchive()?historyRows:activeRows;
        ArrayList<JSONObject> items=new ArrayList<>();
        for(int i=0;i<source.length();i++){
            JSONObject item=source.optJSONObject(i);
            if(item==null)continue;
            String status=item.optString("status");
            if(screen==Screen.SOLICITUDES&&!UnifiedDriverPolicy.active(status))continue;
            if(screen!=Screen.SOLICITUDES&&!matchesPeriod(item))continue;
            items.add(item);
        }
        Collections.sort(items,(a,b)->{
            if(screen==Screen.SOLICITUDES){
                int pa=reservationPriority(a),pb=reservationPriority(b);
                if(pa!=pb)return Integer.compare(pa,pb);
            }
            String left=a.optString("pickup_date","")+" "+a.optString("pickup_time","");
            String right=b.optString("pickup_date","")+" "+b.optString("pickup_time","");
            return screen==Screen.HISTORIAL?right.compareTo(left):left.compareTo(right);
        });
        if(items.isEmpty()){
            emptyCard(screen==Screen.HISTORIAL?"Sin viajes registrados":
                    screen==Screen.AGENDA?"Agenda sin viajes":"Sin solicitudes pendientes",
                screen==Screen.HISTORIAL?"Cuando finalices un viaje aparecerá aquí. Mientras tanto, podés consultar la agenda.":
                    screen==Screen.AGENDA?"No hay reservas para este período. Probá con otras fechas.":
                    "Al recibir una petición aparecerán origen, destino, kilómetros, importe y los botones de aceptar y rechazar.");
            if(screen==Screen.SOLICITUDES){
                secondary(list,"VER AGENDA  →",()->openScreen(Screen.AGENDA));
                secondary(list,"VER HISTORIAL  →",()->openScreen(Screen.HISTORIAL));
            }else if(screen==Screen.HISTORIAL)
                secondary(list,"VER AGENDA  →",()->openScreen(Screen.AGENDA));
            else secondary(list,"VER SOLICITUDES  →",()->openScreen(Screen.SOLICITUDES));
        }else if(screen==Screen.SOLICITUDES){
            for(JSONObject item:items)showReservation(item);
        }else renderDateGroups(items);
        secondary(list,screen==Screen.HISTORIAL?"ACTUALIZAR HISTORIAL":
            screen==Screen.AGENDA?"ACTUALIZAR AGENDA":"ACTUALIZAR SOLICITUDES",
            ()->reload(false));
        if(oldY>0)scroller.post(()->scroller.scrollTo(0,oldY));
    }
    /** Live journey and direct fixed-price decisions appear before scheduled work. */
    private int reservationPriority(JSONObject item){
        String status=item.optString("status","");
        if("EN_VIAJE".equals(status))return 0;
        if(isDirectFixedRequest(item))return 1;
        if("ACEPTADA".equals(status))return 2;
        if("PENDIENTE".equals(status))return 3;
        return 4;
    }
    private boolean isDirectFixedRequest(JSONObject item){
        return item!=null&&UnifiedDriverPolicy.isDirectFixedRequest(
            item.optString("status",""),
            item.optString("quote_status",""),
            item.optDouble("quote_final_total",Double.NaN),
            item.optString("pickup_mode",""));
    }
    private String screenTitle(){
        if(screen==Screen.AGENDA)return "Agenda";
        if(screen==Screen.HISTORIAL)return "Historial";
        if(screen==Screen.RESUMEN)return "Resumen";
        return "Solicitudes";
    }
    private void showPeriodFilters(){
        LinearLayout strip=row();
        for(Period option:Period.values()){
            final String label=option==Period.TODOS?"Todos":option==Period.HOY?"Hoy":
                option==Period.SEMANA?"Semana":"Mes";
            TextView chip=center(label,12,period==option?WHITE:MUTED,period==option);
            chip.setSingleLine(true);
            chip.setBackground(bg(period==option?GREEN:Color.BLACK,
                period==option?GREEN:Color.BLACK,11));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(39),1);
            lp.setMargins(dp(3),0,dp(3),0);
            chip.setOnClickListener(v->{
                period=option;
                openDays.clear();
                daysInitialized=false;
                render();
                scroller.scrollTo(0,0);
            });
            strip.addView(chip,lp);
        }
        list.addView(strip,match(11));
    }
    private boolean matchesPeriod(JSONObject row){
        if(period==Period.TODOS)return true;
        String raw=row.optString("pickup_date","");
        if(raw.length()<10)return false;
        try{
            SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            fmt.setLenient(false);
            Date when=fmt.parse(raw.substring(0,10));
            if(when==null)return false;
            java.util.Calendar threshold=java.util.Calendar.getInstance();
            threshold.set(java.util.Calendar.HOUR_OF_DAY,0);
            threshold.set(java.util.Calendar.MINUTE,0);
            threshold.set(java.util.Calendar.SECOND,0);
            threshold.set(java.util.Calendar.MILLISECOND,0);
            if(period==Period.SEMANA){
                while(threshold.get(java.util.Calendar.DAY_OF_WEEK)!=java.util.Calendar.MONDAY)
                    threshold.add(java.util.Calendar.DATE,-1);
            }else if(period==Period.MES)
                threshold.set(java.util.Calendar.DAY_OF_MONTH,1);
            java.util.Calendar end=java.util.Calendar.getInstance();
            end.set(java.util.Calendar.HOUR_OF_DAY,0);
            end.set(java.util.Calendar.MINUTE,0);
            end.set(java.util.Calendar.SECOND,0);
            end.set(java.util.Calendar.MILLISECOND,0);
            if(screen==Screen.AGENDA&&period==Period.SEMANA){
                while(end.get(java.util.Calendar.DAY_OF_WEEK)!=java.util.Calendar.MONDAY)
                    end.add(java.util.Calendar.DATE,-1);
                end.add(java.util.Calendar.DATE,7);
            }else if(screen==Screen.AGENDA&&period==Period.MES){
                end.set(java.util.Calendar.DAY_OF_MONTH,1);
                end.add(java.util.Calendar.MONTH,1);
            }else end.add(java.util.Calendar.DATE,1);
            return !when.before(threshold.getTime())&&when.before(end.getTime());
        }catch(Exception e){return false;}
    }
    /** The date header is compact; expanding one date contracts the others. */
    private void renderDateGroups(ArrayList<JSONObject> items){
        LinkedHashMap<String,ArrayList<JSONObject>> groups=new LinkedHashMap<>();
        for(JSONObject item:items){
            String day=item.optString("pickup_date","");
            if(!groups.containsKey(day))groups.put(day,new ArrayList<>());
            groups.get(day).add(item);
        }
        if(!daysInitialized){
            openDays.clear();
            if(!groups.isEmpty())openDays.add(groups.keySet().iterator().next());
            daysInitialized=true;
        }
        for(Map.Entry<String,ArrayList<JSONObject>> entry:groups.entrySet()){
            String day=entry.getKey();
            int count=entry.getValue().size();
            boolean expanded=openDays.contains(day);
            LinearLayout row=row();
            row.setPadding(dp(14),dp(13),dp(13),dp(13));
            row.setBackground(bg(Color.BLACK,Color.BLACK,17));
            TextView title=text(prettyDay(day),16,WHITE,true);
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(text(count+(count==1?" viaje":" viajes")+
                (expanded?"   −":"   +"),12,MUTED,false));
            row.setContentDescription(prettyDay(day)+", "+count+" viajes, "+
                (expanded?"contraer":"desplegar"));
            row.setOnClickListener(v->{
                if(openDays.contains(day))openDays.remove(day);
                else {openDays.clear();openDays.add(day);}
                render();
            });
            list.addView(row,match(expanded?8:10));
            if(expanded)for(JSONObject item:entry.getValue())showReservation(item);
        }
    }
    private String prettyDay(String date){
        if(date.length()>=10)return date.substring(8,10)+"/"+date.substring(5,7)+"/"+date.substring(0,4);
        return "Sin fecha";
    }
    private void summaryValue(LinearLayout container,String value,String label){
        LinearLayout tile=vertical();
        tile.setPadding(dp(13),dp(18),dp(10),dp(17));
        tile.setBackground(bg(Color.BLACK,Color.BLACK,18));
        TextView number=text(value,24,WHITE,true);
        number.setSingleLine(true);
        number.setAutoSizeTextTypeUniformWithConfiguration(14,24,1,
            android.util.TypedValue.COMPLEX_UNIT_SP);
        tile.addView(number,match(9));
        tile.addView(text(label,12,MUTED,false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(104),1);
        lp.setMargins(dp(4),0,dp(4),dp(8));
        container.addView(tile,lp);
    }
    private void renderSummary(){
        int finished=0,canceled=0,rejected=0,priced=0,records=0;
        double km=0,quoted=0;
        for(int i=0;i<historyRows.length();i++){
            JSONObject item=historyRows.optJSONObject(i);
            if(item==null||!matchesPeriod(item))continue;
            records++;
            String status=item.optString("status","");
            if("FINALIZADA".equals(status)){
                finished++;
                double distance=item.optDouble("route_distance_km",Double.NaN);
                if(Double.isFinite(distance)&&distance>0)km+=distance;
                double amount=item.optDouble("quote_final_total",Double.NaN);
                if(!Double.isFinite(amount)||amount<=0)
                    amount=item.optDouble("quote_reference_total",Double.NaN);
                if(Double.isFinite(amount)&&amount>0){quoted+=amount;priced++;}
            }else if("CANCELADA".equals(status))canceled++;
            else if("RECHAZADA".equals(status))rejected++;
        }
        if(records==0){
            emptyCard("Sin actividad registrada en este período",
                "Los viajes realizados aparecerán aquí con kilómetros estimados e importes informados, sin confundir cotizaciones con cobros.");
            secondary(list,"VER HISTORIAL  →",()->openScreen(Screen.HISTORIAL));
            secondary(list,"VER SOLICITUDES  →",()->openScreen(Screen.SOLICITUDES));
            return;
        }
        list.addView(text("Resumen de viajes",20,WHITE,true),match(14));
        LinearLayout first=row(),second=row();
        summaryValue(first,String.valueOf(finished),"Finalizados");
        summaryValue(first,String.valueOf(canceled),"Cancelados");
        summaryValue(second,String.format(Locale.forLanguageTag("es-UY"),"%.1f km",km),"Km estimados de ruta");
        summaryValue(second,"$ "+String.format(Locale.forLanguageTag("es-UY"),"%.0f",quoted),"Importes cotizados");
        list.addView(first,match(0));
        list.addView(second,match(3));
        TextView disclaimer=text(rejected+" rechazados · Importes informados en "+
            priced+" viaje(s) finalizados. No representa recaudación efectivamente cobrada.",12,MUTED,false);
        disclaimer.setPadding(dp(7),dp(8),dp(7),dp(14));
        list.addView(disclaimer,match(0));
        secondary(list,"VER HISTORIAL DE VIAJES",()->openScreen(Screen.HISTORIAL));
        secondary(list,"ACTUALIZAR RESUMEN",()->reload(false));
    }
    private void emptyCard(String title,String description){
        LinearLayout card=vertical();
        card.setBackground(bg(Color.BLACK,Color.BLACK,22));
        card.setPadding(dp(15),dp(17),dp(15),dp(18));
        TextView headline=text(title,21,WHITE,true);
        card.addView(headline,match(11));
        TextView detail=text(description,14,MUTED,false);
        card.addView(detail,match(2));
        list.addView(card,match(0));
    }
    private String plain(String s){return s==null||s.trim().isEmpty()?"No informado":s.trim();}
    private String amount(JSONObject o){
        double amt=o.optDouble("quote_final_total",Double.NaN);
        if(Double.isNaN(amt)||amt<=0)amt=o.optDouble("quote_reference_total",Double.NaN);
        return Double.isFinite(amt)&&amt>0?"$ "+String.format(Locale.forLanguageTag("es-UY"),"%.0f",amt):
            "Precio no confirmado";
    }
    private void routeBlock(LinearLayout card,String label,String address,int markerColor){
        LinearLayout r=row();
        TextView marker=center("◉",22,markerColor,true);
        r.addView(marker,new LinearLayout.LayoutParams(dp(25),dp(51)));
        LinearLayout content=vertical();
        content.setPadding(dp(9),0,0,0);
        content.addView(text(label,12,MUTED,false));
        gap(content,5);
        content.addView(text(address,18,WHITE,true));
        r.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(r,match(0));
    }
    private void rule(LinearLayout parent){
        View line=new View(host);
        line.setBackgroundColor(Color.rgb(33,33,33));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.setMargins(0,dp(17),0,dp(17));
        parent.addView(line,p);
    }
    private void metricDetail(LinearLayout parent,String label,String value){
        LinearLayout stack=vertical();
        stack.setPadding(dp(3),0,dp(3),0);
        stack.addView(text(label,10,MUTED,false));
        gap(stack,9);
        TextView val=text(value,19,WHITE,true);
        val.setSingleLine(true);
        val.setAutoSizeTextTypeUniformWithConfiguration(11,19,1,
            android.util.TypedValue.COMPLEX_UNIT_SP);
        stack.addView(val);
        parent.addView(stack,new LinearLayout.LayoutParams(0,-2,1));
    }
    private Button action(String title,boolean accept){
        Button b=new Button(host);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(WHITE);
        b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(6),0,dp(6),0);
        int fill=accept?GREEN:Color.rgb(47,47,47);
        b.setBackground(bg(fill,fill,16));
        return b;
    }
    private void actions(LinearLayout parent,String left,Runnable leftAction,
                         String right,Runnable rightAction){
        LinearLayout actionRow=row();
        Button reject=action(left,false),accept=action(right,true);
        reject.setOnClickListener(v->leftAction.run());
        accept.setOnClickListener(v->rightAction.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(55),1);
        lp.rightMargin=dp(4);
        actionRow.addView(reject,lp);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(55),1);
        rp.leftMargin=dp(4);
        actionRow.addView(accept,rp);
        parent.addView(actionRow,match(0));
    }
    private void secondary(LinearLayout parent,String name,Runnable run){
        TextView t=center(name,12,MUTED,false);
        t.setPadding(dp(12),dp(12),dp(12),dp(9));
        t.setOnClickListener(v->run.run());
        parent.addView(t,match(0));
    }
    /**
     * Incoming petitions keep the user's original large card, with actionable price,
     * route and Accept/Reject buttons visible immediately. Other records collapse.
     * No changes to the reservation RPCs or Mapa's GPS/journey implementation.
     */
    private void showReservation(JSONObject item){
        String id=item.optString("id","");
        String status=item.optString("status","");
        String phase=item.optString("pickup_phase","");
        if(id.isEmpty())return;
        final boolean direct=isDirectFixedRequest(item);
        String pickupMode=item.optString("pickup_mode","");
        final boolean scheduled="PENDIENTE".equals(status)&&
            !pickupMode.isEmpty()&&!UnifiedDriverPolicy.isImmediateMode(pickupMode);
        boolean urgent=screen==Screen.SOLICITUDES&&
            (direct||"EN_VIAJE".equals(status));
        boolean expanded=urgent||openCards.contains(id);

        LinearLayout card=vertical();
        card.setPadding(dp(13),dp(14),dp(13),dp(14));
        card.setBackground(bg(Color.BLACK,Color.BLACK,22));
        list.addView(card,match(10));

        String title="PENDIENTE".equals(status)?
            (direct?"Nueva solicitud · Tu viaje":
                scheduled?"Viaje programado":"Petición por revisar"):
            UnifiedDriverPolicy.label(status,phase);
        String code=item.optString("code","");
        String customer=item.optString("customer_name","");
        String date=item.optString("pickup_date","");
        String time=item.optString("pickup_time","");
        String when=(date.isEmpty()?"Sin fecha":prettyDay(date))+
            (time.isEmpty()?"":" · "+time);
        String details=(code.isEmpty()?"":code+" · ")+when+
            (customer.trim().isEmpty()?"":" · "+customer.trim());

        LinearLayout header=row();
        LinearLayout titleGroup=vertical();
        titleGroup.addView(text(title,urgent?22:17,WHITE,true),match(6));
        titleGroup.addView(text(details,12,MUTED,false));
        header.addView(titleGroup,new LinearLayout.LayoutParams(0,-2,1));
        if(!urgent){
            TextView toggle=center(expanded?"−":"+",23,MUTED,false);
            header.addView(toggle,new LinearLayout.LayoutParams(dp(38),dp(40)));
            header.setContentDescription((expanded?"Contraer ":"Desplegar ")+title);
            header.setOnClickListener(v->{
                if(openCards.contains(id))openCards.remove(id);
                else openCards.add(id);
                render();
            });
        }
        card.addView(header,match(expanded?17:8));

        if(!expanded){
            String origin=item.optString("origin_text","");
            String destination=item.optString("destination_text","");
            if(!origin.trim().isEmpty()||!destination.trim().isEmpty()){
                TextView route=text(plain(origin)+"  →  "+plain(destination),13,MUTED,false);
                route.setMaxLines(2);
                route.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(route,match(4));
            }
            if(screen==Screen.SOLICITUDES&&"ACEPTADA".equals(status)){
                TextView hint=text("Tocá para ver recogida y acciones",12,GREEN,false);
                card.addView(hint,match(0));
            }
            return;
        }

        routeBlock(card,"ORIGEN",plain(item.optString("origin_text")),
            Color.rgb(0,163,82));
        TextView dotted=text("│",17,Color.rgb(85,85,85),false);
        dotted.setPadding(dp(7),0,0,0);
        card.addView(dotted,match(0));
        routeBlock(card,"DESTINO",plain(item.optString("destination_text")),
            Color.rgb(231,61,63));
        rule(card);
        LinearLayout three=row();
        double km=item.optDouble("route_distance_km",Double.NaN);
        String distance=Double.isFinite(km)&&km>0?
            String.format(Locale.forLanguageTag("es-UY"),"%.1f km",km):"—";
        double minutes=item.optDouble("route_duration_min",Double.NaN);
        String duration=Double.isFinite(minutes)&&minutes>0?
            String.format(Locale.ROOT,"%.0f min",minutes):"—";
        metricDetail(three,"DISTANCIA",distance);
        metricDetail(three,"DURACIÓN",duration);
        String value=amount(item);
        if("PENDIENTE".equals(status)&&!direct){
            String quote=item.optString("quote_status","");
            if("ENVIADO".equals(quote))value="Cotización enviada";
            else if(!"ACEPTADO".equals(quote)&&!"PRECIO_FIJO".equals(quote))
                value="A cotizar";
        }
        metricDetail(three,direct?"PRECIO FIJO":"IMPORTE",value);
        card.addView(three,match(17));

        if(screen!=Screen.HISTORIAL){
            if(UnifiedDriverPolicy.canAccept(status)){
                if(direct){
                    // EXACTLY TWO one-tap decisions. The server confirms the state change.
                    // No quote dialog, confirmation sheet or editable price for Tu viaje.
                    actions(card,"Rechazar",()->performChange(item,"RECHAZADA",false),
                        "Aceptar",()->performChange(item,"ACEPTADA",false));
                }else{
                    TextView explanation=text(scheduled?
                        "Reserva programada: gestionar fecha, disponibilidad y presupuesto desde Agenda. No es una solicitud directa.":
                        "Falta confirmar modalidad o precio. Actualizá la reserva antes de ofrecer aceptación directa.",
                        12,MUTED,false);
                    explanation.setPadding(dp(6),dp(2),dp(6),dp(5));
                    card.addView(explanation,match(3));
                    if(scheduled)
                        secondary(card,"VER EN AGENDA",()->openScreen(Screen.AGENDA));
                    else secondary(card,"ACTUALIZAR DATOS",()->reload(false));
                    secondary(card,"RECHAZAR RESERVA",
                        ()->performChange(item,"RECHAZADA",true));
                }
            }else if(UnifiedDriverPolicy.canStart(status)){
                if(UnifiedDriverPolicy.canSetPickup(status,phase,"EN_CAMINO"))
                    actions(card,"Ruta",()->openRoute(item),
                        "Iniciar recogida",()->performPickup(item,"EN_CAMINO"));
                else if(UnifiedDriverPolicy.canSetPickup(status,phase,"EN_PUNTO"))
                    actions(card,"Ruta",()->openRoute(item),
                        "Llegué",()->performPickup(item,"EN_PUNTO"));
                else
                    actions(card,"Ruta",()->openRoute(item),
                        "Iniciar viaje",()->performChange(item,"EN_VIAJE",true));
                secondary(card,"INICIAR VIAJE",()->performChange(item,"EN_VIAJE",true));
            }else if(UnifiedDriverPolicy.canFinish(status))
                actions(card,"Ruta",()->openRoute(item),
                    "Finalizar",()->performChange(item,"FINALIZADA",true));
        }
        if(screen!=Screen.HISTORIAL&&!UnifiedDriverPolicy.canAccept(status)){
            LinearLayout extras=row();
            extras.addView(extra("Llamar",()->dial(item)),
                new LinearLayout.LayoutParams(0,dp(43),1));
            extras.addView(extra("WhatsApp",()->whatsapp(item)),
                new LinearLayout.LayoutParams(0,dp(43),1));
            extras.addView(extra("Mensajes",()->showMessages(item)),
                new LinearLayout.LayoutParams(0,dp(43),1));
            card.addView(extras,match(0));
        }
    }
    private TextView extra(String value,Runnable action){
        TextView t=center(value,12,MUTED,false);
        t.setOnClickListener(v->action.run());
        return t;
    }
    private void performChange(JSONObject row,String status,boolean confirm){
        String id=row.optString("id","");
        if(id.isEmpty())return;
        Runnable command=()->submitChange(id,status,null);
        if(confirm)new AlertDialog.Builder(host)
            .setTitle(status.equals("RECHAZADA")?"Rechazar solicitud":
                status.equals("FINALIZADA")?"Finalizar viaje":"Iniciar viaje")
            .setMessage("¿Confirmás la operación? Quedará registrada en la reserva.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CONFIRMAR",(d,w)->command.run()).show();
        else command.run();
    }
    private void performPickup(JSONObject row,String phase){
        String id=row.optString("id","");
        if(!id.isEmpty())submitChange(id,null,phase);
    }
    private void submitChange(String id,String status,String phase){
        if(!loading.compareAndSet(false,true))return;
        connection.setText("Guardando operación…");
        io.execute(()->{
            try{
                if(status!=null)gateway.changeStatus(id,status);
                else gateway.changePickupPhase(id,phase);
                host.runOnUiThread(()->{
                    loading.set(false);
                    if(host.isFinishing()||host.isDestroyed())return;
                    if("ACEPTADA".equals(status))openCards.add(id);
                    reload(false);
                });
            }catch(Exception e){
                host.runOnUiThread(()->{
                    loading.set(false);
                    if(host.isFinishing()||host.isDestroyed())return;
                    new AlertDialog.Builder(host).setTitle("No se guardó")
                        .setMessage("El servidor no confirmó la operación. Actualizá y comprobá el estado.")
                        .setPositiveButton("ENTENDIDO",null).show();
                    reload(false);
                });
            }
        });
    }
    private void openRoute(JSONObject row){
        String dest=row.optString("origin_text","");
        if("EN_VIAJE".equals(row.optString("status")))
            dest=row.optString("destination_text","");
        if(dest.isEmpty())return;
        open(new Intent(Intent.ACTION_VIEW,Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(dest))));
    }
    private void dial(JSONObject row){
        String phone=row.optString("customer_phone",row.optString("passenger_phone",""));
        if(!phone.trim().isEmpty())open(new Intent(Intent.ACTION_DIAL,
            Uri.parse("tel:"+Uri.encode(phone))));
    }
    private void whatsapp(JSONObject row){
        String digits=row.optString("customer_phone",
            row.optString("passenger_phone","")).replaceAll("[^0-9]","");
        if(digits.startsWith("0"))digits="598"+digits.substring(1);
        if(!digits.isEmpty())open(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/"+digits)));
    }
    private void open(Intent intent){
        try{host.startActivity(intent);}
        catch(Exception e){new AlertDialog.Builder(host).setMessage(
            "No se encontró una aplicación para abrir esta acción.")
            .setPositiveButton("CERRAR",null).show();}
    }
    private void showMessages(JSONObject row){
        final String id=row.optString("id","");
        if(id.isEmpty())return;
        LinearLayout dialogue=vertical();dialogue.setPadding(dp(14),dp(8),dp(14),dp(10));
        TextView historyView=text("Cargando mensajes…",12,PETROL,false);
        ScrollView chatScroll=new ScrollView(host);
        chatScroll.addView(historyView);
        dialogue.addView(chatScroll,new LinearLayout.LayoutParams(-1,dp(195)));
        EditText input=new EditText(host);
        input.setSingleLine(false);input.setMinLines(2);
        input.setHint("Escribir mensaje para el pasajero");
        dialogue.addView(input,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(host)
            .setTitle("MENSAJES · "+row.optString("code"))
            .setView(dialogue).setNegativeButton("CERRAR",null)
            .setNeutralButton("LLEGO EN 5 MIN",(d,w)->sendMessage(id,"Llego en 5 minutos"))
            .setPositiveButton("ENVIAR",(d,w)->sendMessage(id,input.getText().toString()))
            .create();
        dialog.show();
        io.execute(()->{
            try{
                JSONArray messages=gateway.messages(id);
                StringBuilder list=new StringBuilder();
                for(int i=0;i<messages.length();i++){
                    JSONObject msg=messages.optJSONObject(i);if(msg==null)continue;
                    String sender=msg.optString("sender_role","cliente");
                    String body=msg.optString("body","");
                    if(body.isEmpty())continue;
                    list.append("driver".equalsIgnoreCase(sender)?"CONDUCTOR":"PASAJERO")
                        .append(": ").append(body).append("\n\n");
                }
                final String shown=list.length()==0?"Todavía no hay mensajes.":list.toString().trim();
                host.runOnUiThread(()->{
                    if(dialog.isShowing())historyView.setText(shown);
                });
            }catch(Exception e){
                host.runOnUiThread(()->{
                    if(dialog.isShowing())historyView.setText(
                        "No se pudieron cargar los mensajes. Intentá nuevamente.");
                });
            }
        });
    }
    private void sendMessage(String id,String value){
        if(value==null||value.trim().isEmpty())return;
        io.execute(()->{
            try{gateway.sendMessage(id,value);}
            catch(Exception e){
                host.runOnUiThread(()->{
                    if(!host.isFinishing()&&!host.isDestroyed())
                        new AlertDialog.Builder(host).setMessage(
                            "No se pudo enviar el mensaje. Comprobá la conexión.")
                            .setPositiveButton("CERRAR",null).show();
                });
            }
        });
    }
}
