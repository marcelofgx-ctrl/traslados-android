package uy.com.mapatrayectos.conductorv0;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native Quick Trip driver surface. Only displays authorized server snapshots. */
public final class ConductorLiveView extends FrameLayout {
    private static final int BLACK=Color.BLACK, PANEL=Color.rgb(37,37,37);
    private static final int WHITE=Color.WHITE, MUTED=Color.rgb(175,175,175);
    private static final int GREEN=Color.rgb(0,166,89), RED=Color.rgb(218,62,67);
    private static final int ACCEPT=Color.rgb(0,120,89), REJECT=Color.rgb(47,47,47);
    public static final class Request {
        public final String id,code,origin,destination,distance,duration,amount,customer;
        public Request(String id,String code,String origin,String destination,String distance,
                       String duration,String amount,String customer) {
            this.id=s(id);this.code=s(code);this.origin=s(origin);this.destination=s(destination);
            this.distance=s(distance);this.duration=s(duration);this.amount=s(amount);this.customer=s(customer);
        }
    }
    public static final class Trip {
        public final String id,code,customer,phone,origin,destination,status,phase,time,amount;
        public final String pickupMode,fareMode,quoteStatus;
        public final double routeKm;
        public final int routeMinutes;
        public Trip(String id,String code,String customer,String phone,String origin,String destination,
                    String status,String phase,String time,String amount,String pickupMode,
                    String fareMode,String quoteStatus,double routeKm,int routeMinutes) {
            this.id=s(id);this.code=s(code);this.customer=s(customer);this.phone=s(phone);
            this.origin=s(origin);this.destination=s(destination);this.status=s(status);
            this.phase=s(phase);this.time=s(time);this.amount=s(amount);
            this.pickupMode=s(pickupMode);this.fareMode=s(fareMode);this.quoteStatus=s(quoteStatus);
            this.routeKm=routeKm;this.routeMinutes=routeMinutes;
        }
    }
    public interface Listener {
        void onAccept(String id);
        void onReject(String id);
        void onPrevious();
        void onNext();
        void onRefresh();
        void onCall(String phone);
        void onWhatsApp(String phone);
        void onRoute(String address);
        void onPickupPhase(String id,String phase);
        void onPrepareQuote(String id);
        void onStartTrip(String id);
        void onFinishTrip(String id);
    }
    private final TextView shiftBadge,countNew,countAccepted,countInJourney,connectionText,feedbackText;
    private final TextView historyToggle,activeToggle,scheduledToggle;
    private final LinearLayout requestBody,historyBody,activeBody,scheduledBody;
    private final List<String> history=new ArrayList<>();
    private final List<Trip> active=new ArrayList<>(),scheduled=new ArrayList<>();
    private boolean historyOpen=false,activeOpen=true,scheduledOpen=false,busy=false;
    private Listener listener;
    private Request request;
    private int index=0,total=0;
    private static String s(String v){return v==null?"":v.trim();}

    public ConductorLiveView(Context c){
        super(c);
        setBackgroundColor(BLACK);
        setClickable(true);
        setContentDescription("Quick Trip. Conductor integrado en Mapa QT");
        setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                top=bars.top;bottom=bars.bottom;
            }else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(0,top,0,bottom);return insets;
        });
        ScrollView scroll=new ScrollView(c);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(16),dp(12),dp(16),dp(26));
        addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=col();
        panel.setPadding(dp(14),dp(20),dp(14),dp(20));
        panel.setBackground(bg(PANEL,24));
        scroll.addView(panel,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=row();
        LinearLayout title=col();
        title.addView(text("QUICK TRIP",13,MUTED,false));
        gap(title,7);
        title.addView(text("Conductor",29,WHITE,true));
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        shiftBadge=text("Jornada detenida",12,MUTED,false);
        shiftBadge.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        shiftBadge.setPadding(dp(4),dp(8),0,0);
        header.addView(shiftBadge,new LinearLayout.LayoutParams(dp(120),dp(64)));
        panel.addView(header,mb(16));

        LinearLayout counters=row();
        countNew=counter(counters,"Nuevas");
        countAccepted=counter(counters,"Aceptadas");
        countInJourney=counter(counters,"En viaje");
        panel.addView(counters,mb(10));

        connectionText=text("Consultando solicitudes…",12,MUTED,false);
        connectionText.setGravity(Gravity.CENTER);
        connectionText.setPadding(dp(5),dp(7),dp(5),dp(7));
        panel.addView(connectionText,mb(12));

        requestBody=col();
        requestBody.setPadding(dp(14),dp(19),dp(14),dp(20));
        requestBody.setMinimumHeight(dp(250));
        requestBody.setBackground(bg(BLACK,20));
        panel.addView(requestBody,mb(12));

        feedbackText=text("",14,WHITE,true);
        feedbackText.setGravity(Gravity.CENTER);
        feedbackText.setVisibility(View.GONE);
        panel.addView(feedbackText,mb(10));

        Button refresh=button("↻  Actualizar solicitudes",REJECT);
        refresh.setTextSize(13);
        refresh.setOnClickListener(v->{if(listener!=null)listener.onRefresh();});
        panel.addView(refresh,new LinearLayout.LayoutParams(-1,dp(46)));
        gap(panel,12);

        activeToggle=toggle("Aceptadas y en curso",activeOpen,()->{activeOpen=!activeOpen;renderActive();});
        panel.addView(activeToggle,mb(5));
        activeBody=col();
        panel.addView(activeBody,mb(12));

        scheduledToggle=toggle("Programadas / presupuesto",scheduledOpen,()->{scheduledOpen=!scheduledOpen;renderScheduled();});
        panel.addView(scheduledToggle,mb(5));
        scheduledBody=col();
        panel.addView(scheduledBody,mb(12));

        historyToggle=toggle("Histórico",historyOpen,()->{historyOpen=!historyOpen;renderHistory();});
        panel.addView(historyToggle,mb(5));
        historyBody=col();
        panel.addView(historyBody,mb(14));
        TextView hint=text("Deslizá a la derecha para volver a Mapa",12,MUTED,false);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint);

        setShiftActive(false);
        setCounters(null,null,null);
        setPendingRequest(null,0,0);
        renderActive();renderScheduled();renderHistory();
    }
    public void setListener(Listener next){listener=next;renderRequest();renderActive();}
    public void setBusy(boolean next){busy=next;renderRequest();renderActive();}
    public void setShiftActive(boolean active){
        shiftBadge.setText(active?"✓ Jornada activa":"○ Jornada detenida");
        shiftBadge.setTextColor(active?GREEN:MUTED);
    }
    public void setCounters(Integer fresh,Integer accepted,Integer inJourney){
        countNew.setText(number(fresh));countAccepted.setText(number(accepted));
        countInJourney.setText(number(inJourney));
    }
    public void setConnection(String status,boolean connected){
        connectionText.setText(status);
        connectionText.setTextColor(connected?GREEN:MUTED);
    }
    public void setFeedback(String message,boolean positive){
        feedbackText.setText(s(message));
        feedbackText.setTextColor(positive?GREEN:MUTED);
        feedbackText.setVisibility(s(message).isEmpty()?View.GONE:View.VISIBLE);
    }
    public void setPendingRequest(Request next,int currentIndex,int totalCount){
        request=next;index=currentIndex;total=totalCount;renderRequest();
    }
    public void setActiveTrips(List<Trip> rows){active.clear();if(rows!=null)active.addAll(rows);renderActive();}
    public void setScheduledTrips(List<Trip> rows){scheduled.clear();if(rows!=null)scheduled.addAll(rows);renderScheduled();}
    public void setHistory(List<String> rows){history.clear();if(rows!=null)history.addAll(rows);renderHistory();}

    private void renderRequest(){
        requestBody.removeAllViews();
        if(request==null){
            requestBody.addView(text("Sin solicitudes inmediatas",21,WHITE,true),mb(15));
            requestBody.addView(text("Las peticiones de Tu Viaje aparecerán acá cuando estén pendientes. Los viajes programados figuran en su sección.",14,MUTED,false));
            return;
        }
        final Request r=request;
        LinearLayout top=row();
        top.addView(text("Nueva solicitud",22,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView sequence=text(total>1?(index+1)+" / "+total:"Tu Viaje",12,MUTED,false);
        sequence.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        top.addView(sequence,new LinearLayout.LayoutParams(-2,-1));
        requestBody.addView(top,mb(15));
        if(!r.code.isEmpty())requestBody.addView(text("Pedido "+r.code,12,MUTED,false),mb(12));
        routeLine(requestBody,"ORIGEN",r.origin,GREEN);
        requestBody.addView(text("│",17,MUTED,false),mb(1));
        routeLine(requestBody,"DESTINO",r.destination,RED);
        View rule=new View(getContext());rule.setBackgroundColor(Color.rgb(48,48,48));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(1));
        rp.setMargins(0,dp(17),0,dp(17));requestBody.addView(rule,rp);
        LinearLayout totals=row();
        metric(totals,"DISTANCIA",r.distance);
        metric(totals,"DURACIÓN",r.duration);
        metric(totals,"IMPORTE",r.amount);
        requestBody.addView(totals,mb(17));
        if(!r.customer.isEmpty())requestBody.addView(text("Pasajero: "+r.customer,12,MUTED,false),mb(10));
        LinearLayout buttons=row();
        Button reject=button(busy?"Procesando…":"Rechazar",REJECT);
        Button accept=button(busy?"Procesando…":"Aceptar",ACCEPT);
        boolean enabled=listener!=null&&!busy;
        reject.setEnabled(enabled);accept.setEnabled(enabled);
        reject.setOnClickListener(v->{if(listener!=null)listener.onReject(r.id);});
        accept.setOnClickListener(v->{if(listener!=null)listener.onAccept(r.id);});
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(55),1);
        left.rightMargin=dp(4);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(55),1);
        right.leftMargin=dp(4);
        buttons.addView(reject,left);buttons.addView(accept,right);
        requestBody.addView(buttons);
        if(total>1){
            LinearLayout nav=row();
            Button previous=button("‹ Anterior",REJECT),next=button("Siguiente ›",REJECT);
            previous.setTextSize(13);next.setTextSize(13);
            previous.setOnClickListener(v->{if(listener!=null)listener.onPrevious();});
            next.setOnClickListener(v->{if(listener!=null)listener.onNext();});
            nav.addView(previous,new LinearLayout.LayoutParams(0,dp(42),1));
            nav.addView(next,new LinearLayout.LayoutParams(0,dp(42),1));
            requestBody.addView(nav,mt(9));
        }
    }

    private void renderActive(){
        activeToggle.setText("Aceptadas y en curso ("+active.size()+") "+(activeOpen?"▴":"▾"));
        activeBody.removeAllViews();
        activeBody.setVisibility(activeOpen?View.VISIBLE:View.GONE);
        if(!activeOpen)return;
        if(active.isEmpty()){activeBody.addView(text("Sin viajes aceptados o en curso.",13,MUTED,false));return;}
        for(Trip t:active){
            LinearLayout card=tripCard(t);
            LinearLayout quick=row();
            if(!t.phone.isEmpty()){
                Button call=button("Llamar",REJECT),wa=button("WhatsApp",REJECT);
                call.setTextSize(12);wa.setTextSize(12);
                call.setOnClickListener(v->{if(listener!=null)listener.onCall(t.phone);});
                wa.setOnClickListener(v->{if(listener!=null)listener.onWhatsApp(t.phone);});
                quick.addView(call,new LinearLayout.LayoutParams(0,dp(42),1));
                quick.addView(wa,new LinearLayout.LayoutParams(0,dp(42),1));
            }
            Button route=button("Ruta",REJECT);route.setTextSize(12);
            route.setOnClickListener(v->{if(listener!=null)listener.onRoute(t.origin);});
            quick.addView(route,new LinearLayout.LayoutParams(0,dp(42),1));
            card.addView(quick,mt(8));
            if("ACEPTADA".equals(t.status)){
                String nextPhase="SIN_INICIAR".equals(t.phase)||t.phase.isEmpty()?"EN_CAMINO":
                    "EN_CAMINO".equals(t.phase)?"EN_PUNTO":"";
                if(!nextPhase.isEmpty()){
                    Button state=button("EN_CAMINO".equals(nextPhase)?"Voy en camino":"Llegué al origen",ACCEPT);
                    state.setTextSize(13);state.setEnabled(!busy);
                    state.setOnClickListener(v->{if(listener!=null)listener.onPickupPhase(t.id,nextPhase);});
                    card.addView(state,new LinearLayout.LayoutParams(-1,dp(47)));
                }
            }
            if(ConductorOperationsPolicy.canStart(t.status)) {
                Button start=button("Iniciar viaje",ACCEPT);
                start.setTextSize(13);
                start.setEnabled(!busy);
                start.setOnClickListener(v->{if(listener!=null)listener.onStartTrip(t.id);});
                // The controller requires explicit confirmation plus GPS preflight.
                card.addView(start,new LinearLayout.LayoutParams(-1,dp(48)));
            }else if(ConductorOperationsPolicy.canFinish(t.status)){
                Button finish=button("Finalizar viaje",REJECT);
                finish.setTextSize(13);
                finish.setEnabled(!busy);
                finish.setOnClickListener(v->{if(listener!=null)listener.onFinishTrip(t.id);});
                card.addView(finish,new LinearLayout.LayoutParams(-1,dp(48)));
            }
            activeBody.addView(card,mb(7));
        }
    }
    private void renderScheduled(){
        scheduledToggle.setText("Programadas / presupuesto ("+scheduled.size()+") "+(scheduledOpen?"▴":"▾"));
        scheduledBody.removeAllViews();
        scheduledBody.setVisibility(scheduledOpen?View.VISIBLE:View.GONE);
        if(!scheduledOpen)return;
        if(scheduled.isEmpty()){scheduledBody.addView(text("Sin solicitudes programadas pendientes.",13,MUTED,false));return;}
        scheduledBody.addView(text("Requieren el proceso de presupuesto y agenda. No se aceptan como Tu Viaje inmediato.",12,MUTED,false),mb(8));
        for(Trip t:scheduled) {
            LinearLayout card=tripCard(t);
            if(ConductorOperationsPolicy.canQuote(t.status,t.pickupMode,t.fareMode)) {
                Button quote=button("ENVIADO".equals(t.quoteStatus)?
                        "Editar presupuesto":"Preparar presupuesto",ACCEPT);
                quote.setTextSize(13);quote.setEnabled(!busy);
                quote.setOnClickListener(v->{if(listener!=null)listener.onPrepareQuote(t.id);});
                card.addView(quote,new LinearLayout.LayoutParams(-1,dp(49)));
            }else if("PENDIENTE".equals(t.status)){
                card.addView(text("Esta solicitud requiere revisión del estado o precio.",12,MUTED,false));
            }
            scheduledBody.addView(card,mb(7));
        }
    }
    private void renderHistory(){
        historyToggle.setText("Histórico de peticiones ("+history.size()+") "+(historyOpen?"▴":"▾"));
        historyBody.removeAllViews();
        historyBody.setVisibility(historyOpen?View.VISIBLE:View.GONE);
        if(!historyOpen)return;
        if(history.isEmpty()){historyBody.addView(text("Sin movimientos registrados por el servidor.",13,MUTED,false));return;}
        for(int i=0;i<Math.min(history.size(),40);i++){
            TextView item=text(history.get(i),13,WHITE,false);
            item.setBackground(bg(BLACK,12));
            item.setPadding(dp(12),dp(12),dp(12),dp(12));
            historyBody.addView(item,mb(5));
        }
    }
    private LinearLayout tripCard(Trip t){
        LinearLayout box=col();
        box.setBackground(bg(BLACK,16));
        box.setPadding(dp(12),dp(12),dp(12),dp(12));
        box.addView(text(t.code.isEmpty()?t.status:t.code+"  ·  "+t.status,15,WHITE,true),mb(7));
        if(!t.time.isEmpty())box.addView(text(t.time,12,MUTED,false),mb(6));
        if(!t.customer.isEmpty())box.addView(text(t.customer,13,WHITE,false),mb(5));
        box.addView(text(t.origin+" → "+t.destination,13,MUTED,false),mb(7));
        if(!t.amount.isEmpty())box.addView(text(t.amount,14,GREEN,true));
        if("ENVIADO".equals(t.quoteStatus))box.addView(text("Presupuesto enviado al pasajero",12,MUTED,false));
        if("EN_VIAJE".equals(t.status))box.addView(text("Seguimiento GPS del viaje en curso",12,GREEN,false));
        return box;
    }
    private TextView toggle(String value,boolean open,Runnable click){
        TextView t=text(value+(open?" ▴":" ▾"),14,WHITE,true);
        t.setBackground(bg(BLACK,13));
        t.setPadding(dp(12),dp(13),dp(12),dp(13));
        t.setOnClickListener(v->click.run());return t;
    }
    private TextView counter(LinearLayout strip,String name){
        LinearLayout tile=col();
        tile.setGravity(Gravity.CENTER);tile.setBackground(bg(BLACK,15));
        TextView value=text("—",30,WHITE,true);value.setGravity(Gravity.CENTER);tile.addView(value);
        TextView title=text(name,13,MUTED,false);title.setGravity(Gravity.CENTER);tile.addView(title);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(91),1);
        p.setMargins(dp(3),0,dp(3),0);strip.addView(tile,p);return value;
    }
    private void routeLine(LinearLayout body,String title,String address,int color){
        LinearLayout r=row();TextView dot=text("●",18,color,true);
        r.addView(dot,new LinearLayout.LayoutParams(dp(26),-2));
        LinearLayout details=col();details.addView(text(title,12,MUTED,false),mb(4));
        details.addView(text(address.isEmpty()?"Dirección pendiente":address,18,WHITE,true));
        r.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(r);
    }
    private void metric(LinearLayout parent,String title,String value){
        LinearLayout block=col();block.addView(text(title,11,MUTED,false),mb(6));
        TextView v=text(value.isEmpty()?"—":value,18,WHITE,true);v.setMaxLines(2);
        block.addView(v);parent.addView(block,new LinearLayout.LayoutParams(0,-2,1));
    }
    private Button button(String title,int color){
        Button b=new Button(getContext());b.setText(title);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setTextSize(TypedValue.COMPLEX_UNIT_SP,16);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(bg(color,15));b.setMinHeight(0);b.setMinimumHeight(0);
        return b;
    }
    private static String number(Integer n){return n==null||n<0?"—":String.format(Locale.ROOT,"%02d",n);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(getContext());
        t.setText(value);t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private LinearLayout col(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable bg(int fill,int radius){
        GradientDrawable b=new GradientDrawable();b.setColor(fill);b.setCornerRadius(dp(radius));return b;
    }
    private LinearLayout.LayoutParams mb(int d){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(d);return p;}
    private LinearLayout.LayoutParams mt(int d){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(d);return p;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(getContext()),new LinearLayout.LayoutParams(1,dp(height)));}
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
}
