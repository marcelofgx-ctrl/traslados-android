package uy.com.mapatrayectos.conductorv0;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Quick Trip premium Conductor presentation, isolated from business logic.
 * One FrameLayout -> one ScrollView -> one vertical root with independent cards.
 * No extra overlay, duplicated reservation source, or second driver activity.
 *
 * The public UI contract is preserved so R25.36 ConductorLiveController,
 * ConductorV0Navigator, TransferAlerts, driver RPCs, GPS and trip status are
 * completely untouched.
 */
public final class ConductorLiveView extends FrameLayout {
    private static final int CANVAS=Color.rgb(15,19,21);
    private static final int CARD=Color.rgb(35,40,43);
    private static final int CARD_RAISED=Color.rgb(40,46,49);
    private static final int CARD_DEEP=Color.rgb(26,32,34);
    private static final int STROKE=Color.rgb(66,79,80);
    private static final int WHITE=Color.rgb(244,246,246);
    private static final int MUTED=Color.rgb(166,178,178);
    private static final int GOLD=Color.rgb(224,195,132);
    private static final int GREEN=Color.rgb(81,202,154);
    private static final int GREEN_DEEP=Color.rgb(25,139,105);
    private static final int RED=Color.rgb(232,137,141);

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
        void onRejectScheduled(String id);
        void onStartTrip(String id);
        void onFinishTrip(String id);
    }

    private final TextView shiftBadge,countNew,countAccepted,countInJourney,connectionText,feedbackText;
    private final TextView historyToggle,activeToggle,scheduledToggle;
    private final LinearLayout requestBody,historyBody,activeBody,scheduledBody;
    private final List<String> history=new ArrayList<>();
    private final List<Trip> active=new ArrayList<>(),scheduled=new ArrayList<>();
    private boolean historyOpen=false,activeOpen=true,scheduledOpen=false,busy=false;
    private boolean connected=false;
    private Listener listener;
    private Request request;
    private int index=0,total=0;

    private static String s(String v){return v==null?"":v.trim();}

    public ConductorLiveView(Context context){
        super(context);
        setBackgroundColor(CANVAS);
        setClickable(true);
        setContentDescription("Quick Trip. Panel Conductor integrado en Mapa QT");
        setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                top=bars.top;bottom=bars.bottom;
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0,top,0,bottom);
            return insets;
        });

        // Single scroll hierarchy; no synthetic map-cover panels or negative margins.
        ScrollView scroll=new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout root=col();
        root.setPadding(dp(20),dp(23),dp(20),dp(30));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        LinearLayout brandRow=row();
        LinearLayout brand=col();
        TextView kicker=text("QUICK TRIP",11,GOLD,true);
        kicker.setLetterSpacing(.19f);
        brand.addView(kicker,mb(5));
        brand.addView(text("Conductor",27,WHITE,true));
        brandRow.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        shiftBadge=text("●  Jornada detenida",11,MUTED,true);
        shiftBadge.setGravity(Gravity.CENTER);
        shiftBadge.setMaxLines(2);
        shiftBadge.setPadding(dp(11),dp(8),dp(11),dp(8));
        shiftBadge.setBackground(shape(CARD_DEEP,50,STROKE));
        brandRow.addView(shiftBadge,new LinearLayout.LayoutParams(-2,-2));
        root.addView(brandRow,mb(21));

        // Three small stats, never fabricated earnings or fake trip counts.
        LinearLayout stats=row();
        countNew=stat(stats,"NUEVAS");
        countAccepted=stat(stats,"ACEPTADAS");
        countInJourney=stat(stats,"EN VIAJE");
        root.addView(stats,mb(16));

        LinearLayout monitor=row();
        TextView dot=text("●",13,GREEN,true);
        dot.setGravity(Gravity.CENTER_VERTICAL);
        monitor.addView(dot,new LinearLayout.LayoutParams(dp(17),dp(32)));
        connectionText=text("Consultando solicitudes…",11,MUTED,false);
        connectionText.setMaxLines(2);
        monitor.addView(connectionText,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=button("↻",Action.UTILITY);
        refresh.setTextSize(TypedValue.COMPLEX_UNIT_SP,21);
        refresh.setContentDescription("Actualizar solicitudes");
        refresh.setOnClickListener(v->{if(listener!=null)listener.onRefresh();});
        LinearLayout.LayoutParams refreshLp=new LinearLayout.LayoutParams(dp(44),dp(40));
        refreshLp.leftMargin=dp(8);
        monitor.addView(refresh,refreshLp);
        root.addView(monitor,mb(16));

        requestBody=col();
        requestBody.setPadding(dp(16),dp(16),dp(16),dp(16));
        requestBody.setBackground(shape(CARD,20,STROKE));
        root.addView(requestBody,mb(12));

        feedbackText=text("",12,WHITE,true);
        feedbackText.setGravity(Gravity.CENTER_VERTICAL);
        feedbackText.setPadding(dp(13),dp(11),dp(13),dp(11));
        feedbackText.setBackground(shape(CARD_DEEP,12,STROKE));
        feedbackText.setVisibility(View.GONE);
        root.addView(feedbackText,mb(11));

        activeToggle=section("Aceptadas y en curso",()->{
            activeOpen=!activeOpen;renderActive();
        });
        root.addView(activeToggle,mb(7));
        activeBody=col();
        root.addView(activeBody,mb(13));

        scheduledToggle=section("Programadas / presupuesto",()->{
            scheduledOpen=!scheduledOpen;renderScheduled();
        });
        root.addView(scheduledToggle,mb(7));
        scheduledBody=col();
        root.addView(scheduledBody,mb(13));

        historyToggle=section("Histórico de peticiones",()->{
            historyOpen=!historyOpen;renderHistory();
        });
        root.addView(historyToggle,mb(7));
        historyBody=col();
        root.addView(historyBody,mb(10));

        TextView gesture=text("Deslizá a la derecha para volver a Mapa",11,MUTED,false);
        gesture.setGravity(Gravity.CENTER);
        gesture.setPadding(0,dp(12),0,dp(12));
        root.addView(gesture);

        setShiftActive(false);
        setCounters(null,null,null);
        setPendingRequest(null,0,0);
        renderActive();renderScheduled();renderHistory();
    }

    // Controller-facing methods intentionally unchanged (argument and return types).
    public void setListener(Listener next){
        listener=next;renderRequest();renderActive();renderScheduled();
    }
    public void setBusy(boolean next){
        busy=next;renderRequest();renderActive();renderScheduled();
    }
    public void setShiftActive(boolean isActive){
        shiftBadge.setText(isActive?"●  Jornada activa":"○  Jornada detenida");
        shiftBadge.setTextColor(isActive?GREEN:MUTED);
        shiftBadge.setBackground(shape(isActive?Color.rgb(26,59,51):CARD_DEEP,50,
                isActive?Color.rgb(48,109,88):STROKE));
        shiftBadge.setContentDescription(isActive?"Jornada activa":"Jornada detenida");
    }
    public void setCounters(Integer fresh,Integer accepted,Integer inJourney){
        countNew.setText(number(fresh));
        countAccepted.setText(number(accepted));
        countInJourney.setText(number(inJourney));
    }
    public void setConnection(String status,boolean connected){
        this.connected=connected;
        String label=s(status);
        if(connected && label.startsWith("Solicitudes reales · sincronizado hace ")){
            label="Conectado · hace "+label.substring("Solicitudes reales · sincronizado hace ".length());
        }
        connectionText.setText(label);
        connectionText.setTextColor(connected?GREEN:MUTED);
        if(connected && "Actualizando solicitudes…".contentEquals(feedbackText.getText()))
            setFeedback("",false);
    }
    public void setFeedback(String message,boolean positive){
        String m=s(message);
        feedbackText.setText(m);
        feedbackText.setTextColor(positive?GREEN:MUTED);
        feedbackText.setVisibility(m.isEmpty()?View.GONE:View.VISIBLE);
    }
    public void setPendingRequest(Request next,int currentIndex,int totalCount){
        request=next;index=currentIndex;total=totalCount;renderRequest();
    }
    public void setActiveTrips(List<Trip> rows){
        active.clear();if(rows!=null)active.addAll(rows);renderActive();
    }
    public void setScheduledTrips(List<Trip> rows){
        scheduled.clear();if(rows!=null)scheduled.addAll(rows);renderScheduled();
    }
    public void setHistory(List<String> rows){
        history.clear();if(rows!=null)history.addAll(rows);renderHistory();
    }

    private void renderRequest(){
        requestBody.removeAllViews();
        if(request==null){
            LinearLayout heading=row();
            TextView icon=text("◉",18,GOLD,true);
            heading.addView(icon,new LinearLayout.LayoutParams(dp(30),-2));
            heading.addView(text("Todo al día",16,WHITE,true),
                    new LinearLayout.LayoutParams(0,-2,1));
            requestBody.addView(heading,mb(4));
            TextView note=text("Sin solicitudes inmediatas por atender.",12,MUTED,false);
            note.setPadding(dp(30),0,0,0);
            requestBody.addView(note);
            return;
        }
        final Request r=request;
        LinearLayout header=row();
        TextView label=text("Nueva solicitud",17,WHITE,true);
        header.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(pill(total>1?(index+1)+" DE "+total:"INMEDIATO",
                GREEN,Color.rgb(28,65,53)),new LinearLayout.LayoutParams(-2,-2));
        requestBody.addView(header,mb(8));
        if(!r.code.isEmpty())
            requestBody.addView(text(r.code,11,GOLD,true),mb(11));
        route(requestBody,"RECOGIDA",r.origin,GREEN,16);
        rail(requestBody);
        route(requestBody,"DESTINO",r.destination,GOLD,16);
        divider(requestBody,14,12);

        LinearLayout metrics=row();
        metric(metrics,"DISTANCIA",r.distance);
        metric(metrics,"TIEMPO",r.duration);
        metric(metrics,"PRECIO",r.amount);
        requestBody.addView(metrics,mb(13));
        if(!r.customer.isEmpty())
            requestBody.addView(text("Pasajero · "+r.customer,12,MUTED,false),mb(12));

        LinearLayout decisions=row();
        Button reject=button(busy?"Procesando…":"Rechazar",Action.OUTLINE);
        Button accept=button(busy?"Procesando…":"Aceptar",Action.PRIMARY);
        reject.setEnabled(listener!=null&&!busy);
        accept.setEnabled(listener!=null&&!busy);
        reject.setOnClickListener(v->{if(listener!=null)listener.onReject(r.id);});
        accept.setOnClickListener(v->{if(listener!=null)listener.onAccept(r.id);});
        equal(decisions,reject,52,0,6);equal(decisions,accept,52,6,0);
        requestBody.addView(decisions);

        if(total>1){
            LinearLayout paging=row();
            Button previous=button("‹ Anterior",Action.UTILITY);
            Button next=button("Siguiente ›",Action.UTILITY);
            previous.setEnabled(listener!=null&&!busy);
            next.setEnabled(listener!=null&&!busy);
            previous.setOnClickListener(v->{if(listener!=null)listener.onPrevious();});
            next.setOnClickListener(v->{if(listener!=null)listener.onNext();});
            equal(paging,previous,44,0,5);equal(paging,next,44,5,0);
            requestBody.addView(paging,top(11));
        }
    }

    private void renderActive(){
        updateSection(activeToggle,"Aceptadas y en curso",active.size(),activeOpen);
        activeBody.removeAllViews();
        activeBody.setVisibility(activeOpen?View.VISIBLE:View.GONE);
        if(!activeOpen||active.isEmpty())return; // No dead empty-state vertical area.
        for(Trip t:active){
            LinearLayout card=tripCard(t);

            LinearLayout actions=row();
            if(!t.phone.isEmpty()){
                Button call=button("☎  Llamar",Action.UTILITY);
                Button whatsapp=button("◌  WhatsApp",Action.UTILITY);
                call.setOnClickListener(v->{if(listener!=null)listener.onCall(t.phone);});
                whatsapp.setOnClickListener(v->{if(listener!=null)listener.onWhatsApp(t.phone);});
                equal(actions,call,46,0,4);equal(actions,whatsapp,46,4,4);
            }
            Button route=button("➤  Ruta",Action.UTILITY);
            route.setOnClickListener(v->{if(listener!=null)listener.onRoute(t.origin);});
            equal(actions,route,46,t.phone.isEmpty()?0:4,0);
            card.addView(actions,top(14));

            if("ACEPTADA".equals(t.status)){
                String nextPhase=("SIN_INICIAR".equals(t.phase)||t.phase.isEmpty())?
                        "EN_CAMINO":"EN_CAMINO".equals(t.phase)?"EN_PUNTO":"";
                if(!nextPhase.isEmpty()){
                    Button pickup=button("EN_CAMINO".equals(nextPhase)?
                            "Voy en camino  →":"Llegué al origen  ✓",Action.PRIMARY);
                    pickup.setEnabled(listener!=null&&!busy);
                    pickup.setOnClickListener(v->{if(listener!=null)
                        listener.onPickupPhase(t.id,nextPhase);});
                    card.addView(pickup,wide(49,12));
                }
            }
            if(ConductorOperationsPolicy.canStart(t.status)){
                Button begin=button("Iniciar viaje  →",Action.OUTLINE);
                begin.setEnabled(listener!=null&&!busy);
                begin.setOnClickListener(v->{if(listener!=null)listener.onStartTrip(t.id);});
                card.addView(begin,wide(49,9));
            }else if(ConductorOperationsPolicy.canFinish(t.status)){
                Button finish=button("Finalizar viaje",Action.DANGER);
                finish.setEnabled(listener!=null&&!busy);
                finish.setOnClickListener(v->{if(listener!=null)listener.onFinishTrip(t.id);});
                card.addView(finish,wide(49,9));
            }
            activeBody.addView(card,bottom(9));
        }
    }

    private void renderScheduled(){
        updateSection(scheduledToggle,"Programadas / presupuesto",scheduled.size(),scheduledOpen);
        scheduledBody.removeAllViews();
        scheduledBody.setVisibility(scheduledOpen?View.VISIBLE:View.GONE);
        if(!scheduledOpen||scheduled.isEmpty())return;
        for(Trip t:scheduled){
            LinearLayout card=tripCard(t);
            if(ConductorOperationsPolicy.canQuote(t.status,t.pickupMode,t.fareMode)){
                Button quote=button("ENVIADO".equals(t.quoteStatus)?
                        "Editar presupuesto":"Preparar presupuesto",Action.PRIMARY);
                quote.setEnabled(listener!=null&&!busy);
                quote.setOnClickListener(v->{if(listener!=null)listener.onPrepareQuote(t.id);});
                card.addView(quote,wide(49,12));
            }else if("PENDIENTE".equals(t.status)){
                card.addView(text("Esta solicitud requiere revisar su estado o precio.",
                        12,MUTED,false),top(10));
            }
            if("PENDIENTE".equals(t.status)&&"schedule".equals(t.pickupMode)){
                Button reject=button("Rechazar solicitud programada",Action.OUTLINE);
                reject.setEnabled(listener!=null&&!busy);
                reject.setOnClickListener(v->{if(listener!=null)listener.onRejectScheduled(t.id);});
                card.addView(reject,wide(47,9));
            }
            scheduledBody.addView(card,bottom(9));
        }
    }

    private void renderHistory(){
        updateSection(historyToggle,"Histórico de peticiones",history.size(),historyOpen);
        historyBody.removeAllViews();
        historyBody.setVisibility(historyOpen?View.VISIBLE:View.GONE);
        if(!historyOpen)return;
        if(history.isEmpty()){
            smallEmpty(historyBody,"Sin movimientos registrados por el servidor.");
            return;
        }
        for(int i=0;i<Math.min(40,history.size());i++){
            TextView entry=text(history.get(i),12,WHITE,false);
            entry.setLineSpacing(dp(3),1f);
            entry.setBackground(shape(CARD_DEEP,14,STROKE));
            entry.setPadding(dp(13),dp(13),dp(13),dp(13));
            historyBody.addView(entry,bottom(7));
        }
    }

    // The same card component presents accepted, active and scheduled trips.
    // All labels derive from controller-provided server snapshots.
    private LinearLayout tripCard(Trip t){
        LinearLayout card=col();
        card.setBackground(shape(CARD,20,STROKE));
        card.setPadding(dp(16),dp(15),dp(16),dp(16));

        LinearLayout top=row();
        LinearLayout identity=col();
        TextView code=text(t.code.isEmpty()?"Traslado":t.code,14,WHITE,true);
        code.setSingleLine(true);
        code.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(code);
        if(!t.time.isEmpty())identity.addView(text(displayWhen(t.time),11,MUTED,false),top(4));
        top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));

        int stateColor="EN_VIAJE".equals(t.status)?GOLD:
                ("PENDIENTE".equals(t.status)?GOLD:GREEN);
        TextView status=pill(prettyStatus(t.status),stateColor,
                "EN_VIAJE".equals(t.status)?Color.rgb(67,53,35):
                "PENDIENTE".equals(t.status)?Color.rgb(66,55,38):
                Color.rgb(30,71,54));
        LinearLayout.LayoutParams stateLp=new LinearLayout.LayoutParams(-2,-2);
        stateLp.leftMargin=dp(5);
        top.addView(status,stateLp);
        card.addView(top,bottom(12));

        if(!t.customer.isEmpty()){
            TextView passenger=text("◌   "+t.customer,12,WHITE,true);
            card.addView(passenger,bottom(12));
        }

        route(card,"RECOGIDA",t.origin,GREEN,14);
        rail(card);
        route(card,"DESTINO",t.destination,GOLD,14);

        boolean km=ConductorOperationsPolicy.goodDistance(t.routeKm);
        boolean duration=t.routeMinutes>0;
        if(km||duration){
            LinearLayout meta=row();
            if(km)meta.addView(smallChip("↗  "+String.format(Locale.US,"%.1f km",t.routeKm)));
            if(duration){
                LinearLayout.LayoutParams durationLp=new LinearLayout.LayoutParams(-2,-2);
                durationLp.leftMargin=dp(8);
                meta.addView(smallChip("◷  "+t.routeMinutes+" min"),durationLp);
            }
            card.addView(meta,top(11));
        }

        if(!t.amount.isEmpty()){
            divider(card,14,12);
            LinearLayout total=row();
            total.addView(text("IMPORTE",10,MUTED,true),
                    new LinearLayout.LayoutParams(0,-2,1));
            TextView price=text(t.amount,17,GOLD,true);
            price.setGravity(Gravity.END);
            total.addView(price,new LinearLayout.LayoutParams(-2,-2));
            card.addView(total);
        }
        if("ENVIADO".equals(t.quoteStatus)){
            card.addView(text("✓  Presupuesto enviado al pasajero",11,GREEN,false),top(10));
        }
        if("EN_VIAJE".equals(t.status)){
            card.addView(text("●  Seguimiento GPS del viaje en curso",11,GREEN,false),top(10));
        }
        return card;
    }

    private enum Action { PRIMARY, OUTLINE, UTILITY, DANGER }

    private TextView stat(LinearLayout row,String caption){
        LinearLayout tile=col();
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(shape(CARD,17,STROKE));
        TextView value=text("—",27,WHITE,true);
        value.setGravity(Gravity.CENTER);
        value.setIncludeFontPadding(false);
        tile.addView(value,bottom(9));
        TextView title=text(caption,10,MUTED,true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(.04f);
        tile.addView(title);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(83),1);
        p.setMargins(dp(4),0,dp(4),0);
        row.addView(tile,p);
        return value;
    }
    private TextView section(String title,Runnable click){
        TextView section=text(title,13,WHITE,true);
        section.setMinHeight(dp(52));
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(dp(15),dp(11),dp(15),dp(11));
        section.setBackground(shape(CARD_DEEP,15,STROKE));
        section.setClickable(true);
        section.setFocusable(true);
        section.setOnClickListener(v->click.run());
        return section;
    }
    private void updateSection(TextView section,String title,int count,boolean expanded){
        section.setText(title+"   ·   "+count+(expanded?"   ▴":"   ▾"));
        section.setContentDescription(title+". "+count+(count==1?" elemento. ":" elementos. ")+
                (expanded?"Contraer":"Desplegar"));
    }
    private void smallEmpty(LinearLayout parent,String caption){
        TextView empty=text(caption,12,MUTED,false);
        empty.setPadding(dp(14),dp(10),dp(14),dp(10));
        parent.addView(empty);
    }
    private TextView smallChip(String label){
        TextView badge=text(label,11,MUTED,true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10),dp(7),dp(10),dp(7));
        badge.setBackground(shape(CARD_DEEP,40,STROKE));
        return badge;
    }
    private TextView pill(String label,int ink,int background){
        TextView badge=text(label,10,ink,true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10),dp(7),dp(10),dp(7));
        badge.setBackground(shape(background,40,0));
        badge.setSingleLine(true);
        return badge;
    }

    // Pickup + destination clearly separated, with a subtle connecting rail.
    private void route(LinearLayout parent,String title,String address,int color,int fontSp){
        LinearLayout line=row();
        line.setGravity(Gravity.TOP);
        TextView dot=text("●",14,color,true);
        dot.setGravity(Gravity.TOP);
        line.addView(dot,new LinearLayout.LayoutParams(dp(25),-2));
        LinearLayout labels=col();
        TextView caption=text(title,10,color,true);
        caption.setLetterSpacing(.07f);
        labels.addView(caption,bottom(4));
        TextView place=text(address.isEmpty()?"Dirección pendiente":address,fontSp,WHITE,true);
        place.setLineSpacing(dp(2),1f);
        labels.addView(place);
        line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(line);
    }
    private void rail(LinearLayout card){
        TextView stem=text("│",14,MUTED,false);
        stem.setPadding(dp(3),dp(1),0,dp(1));
        card.addView(stem);
    }
    private void metric(LinearLayout metrics,String caption,String value){
        LinearLayout column=col();
        TextView label=text(caption,9,MUTED,true);
        label.setLetterSpacing(.05f);
        column.addView(label,bottom(4));
        TextView number=text(value.isEmpty()?"—":value,14,WHITE,true);
        number.setMaxLines(2);
        column.addView(number);
        metrics.addView(column,new LinearLayout.LayoutParams(0,-2,1));
    }
    private Button button(String label,Action type){
        Button b=new Button(getContext());
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,12);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(type==Action.DANGER?RED:WHITE);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(6),0,dp(6),0);
        if(type==Action.PRIMARY){
            GradientDrawable greenGradient=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(39,158,115),GREEN_DEEP});
            greenGradient.setCornerRadius(dp(14));
            b.setBackground(greenGradient);
        }else{
            b.setBackground(shape(type==Action.UTILITY?CARD_RAISED:CARD_DEEP,14,
                    type==Action.DANGER?Color.rgb(126,68,73):
                    type==Action.OUTLINE?Color.rgb(75,121,103):STROKE));
        }
        b.setElevation(0f);
        return b;
    }
    private String prettyStatus(String status){
        if("EN_VIAJE".equals(status))return "EN VIAJE";
        if("ACEPTADA".equals(status))return "ACEPTADA";
        if("CONFIRMADA".equals(status))return "CONFIRMADA";
        if("PENDIENTE".equals(status))return "PENDIENTE";
        return s(status).replace('_',' ');
    }
    private String displayWhen(String dateText){
        String clean=s(dateText);
        if(clean.length()>=16 && clean.charAt(4)=='-'&&clean.charAt(7)=='-'){
            String day=clean.substring(0,10),clock=clean.substring(11,16);
            SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            if(day.equals(format.format(new Date())))return "Hoy · "+clock;
            Calendar yesterday=Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR,-1);
            if(day.equals(format.format(yesterday.getTime())))return "Ayer · "+clock;
            return day.substring(8,10)+"/"+day.substring(5,7)+"/"+
                    day.substring(0,4)+" · "+clock;
        }
        return clean;
    }
    private static String number(Integer value){
        return value==null||value<0?"—":String.format(Locale.ROOT,"%02d",value);
    }

    private GradientDrawable shape(int fill,int radius,int border){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if(border!=0)drawable.setStroke(dp(1),border);
        return drawable;
    }
    private TextView text(String value,int fontSp,int color,boolean bold){
        TextView view=new TextView(getContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP,fontSp);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }
    private LinearLayout row(){
        LinearLayout layout=new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private LinearLayout col(){
        LinearLayout layout=new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout.LayoutParams bottom(int dpBottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(dpBottom);
        return p;
    }
    private LinearLayout.LayoutParams top(int dpTop){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(dpTop);
        return p;
    }
    private LinearLayout.LayoutParams wide(int height,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height));
        p.topMargin=dp(top);
        return p;
    }
    private void equal(LinearLayout row,Button button,int height,int start,int end){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1);
        p.setMarginStart(dp(start));p.setMarginEnd(dp(end));
        row.addView(button,p);
    }
    private void divider(LinearLayout root,int above,int below){
        View line=new View(getContext());
        line.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.topMargin=dp(above);
        p.bottomMargin=dp(below);
        root.addView(line,p);
    }
    private int dp(float value){
        return (int)(getResources().getDisplayMetrics().density*value+.5f);
    }
}
