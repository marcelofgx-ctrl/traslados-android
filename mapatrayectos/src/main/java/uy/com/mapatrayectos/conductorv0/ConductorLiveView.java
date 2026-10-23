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
 * Quick Trip visual surface, rebuilt as ONE clean hierarchy in Mapa QT.
 * This is presentation only. Authentication, reservation transitions, GPS,
 * prices, notifications, and server state remain owned by the existing controller.
 *
 * Public constructor, Request/Trip types, Listener, and setters intentionally
 * match R25.35 so the stable controller and navigation require NO modifications.
 */
public final class ConductorLiveView extends FrameLayout {
    private static final int BACKGROUND=Color.rgb(8,12,12);
    private static final int SHELL=Color.rgb(23,28,28);
    private static final int CARD=Color.rgb(10,15,15);
    private static final int UTILITY=Color.rgb(39,51,49);
    private static final int DIVIDER=Color.rgb(44,59,55);
    private static final int WHITE=Color.rgb(242,248,246);
    private static final int MUTED=Color.rgb(165,181,175);
    private static final int GOLD=Color.rgb(214,186,121);
    private static final int GREEN=Color.rgb(101,199,165);
    private static final int PRIMARY=Color.rgb(7,111,87);
    private static final int RED=Color.rgb(235,145,145);

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
    private Listener listener;
    private Request request;
    private int index=0,total=0;

    private static String s(String v){return v==null?"":v.trim();}

    public ConductorLiveView(Context c){
        super(c);
        setBackgroundColor(BACKGROUND);
        setClickable(true);
        setContentDescription("Quick Trip. Conductor integrado en Mapa QT");
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

        // Single hierarchy: FrameLayout -> ScrollView -> panel -> sections.
        // No absolute-positioned buttons, negative margins, or nested overlay.
        ScrollView scroll=new ScrollView(c);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(13),dp(12),dp(13),dp(24));
        addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout panel=col();
        panel.setPadding(dp(17),dp(20),dp(17),dp(19));
        panel.setBackground(surface(SHELL,26));
        scroll.addView(panel,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=row();
        LinearLayout branding=col();
        TextView brand=text("QUICK TRIP",12,GOLD,true);
        brand.setLetterSpacing(.13f);
        branding.addView(brand,mb(8));
        branding.addView(text("Conductor",27,WHITE,true));
        header.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        shiftBadge=text("○ Jornada detenida",11,MUTED,false);
        shiftBadge.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        shiftBadge.setMaxLines(2);
        shiftBadge.setPadding(dp(5),dp(8),0,0);
        header.addView(shiftBadge,new LinearLayout.LayoutParams(dp(132),dp(60)));
        panel.addView(header,mb(18));

        LinearLayout statistics=row();
        countNew=counter(statistics,"Nuevas");
        countAccepted=counter(statistics,"Aceptadas");
        countInJourney=counter(statistics,"En viaje");
        panel.addView(statistics,mb(12));

        LinearLayout connectivity=row();
        TextView onlineDot=text("●",11,GREEN,true);
        onlineDot.setContentDescription("Estado de conexión");
        connectivity.addView(onlineDot,new LinearLayout.LayoutParams(dp(17),-2));
        connectionText=text("Consultando solicitudes…",12,MUTED,false);
        connectionText.setMaxLines(2);
        connectivity.addView(connectionText,new LinearLayout.LayoutParams(0,-2,1));
        panel.addView(connectivity,mb(14));

        requestBody=col();
        requestBody.setPadding(dp(15),dp(15),dp(15),dp(15));
        requestBody.setBackground(surface(CARD,19));
        panel.addView(requestBody,mb(9));

        feedbackText=text("",12,MUTED,true);
        feedbackText.setGravity(Gravity.CENTER);
        feedbackText.setPadding(dp(12),dp(10),dp(12),dp(10));
        feedbackText.setBackground(surface(UTILITY,13));
        feedbackText.setVisibility(View.GONE);
        panel.addView(feedbackText,mb(9));

        Button refresh=button("↻  Actualizar solicitudes",Style.UTILITY);
        refresh.setOnClickListener(v->{if(listener!=null)listener.onRefresh();});
        panel.addView(refresh,new LinearLayout.LayoutParams(-1,dp(46)));
        gap(panel,14);

        activeToggle=toggle(()->{activeOpen=!activeOpen;renderActive();});
        panel.addView(activeToggle,mb(6));
        activeBody=col();
        panel.addView(activeBody,mb(12));

        scheduledToggle=toggle(()->{scheduledOpen=!scheduledOpen;renderScheduled();});
        panel.addView(scheduledToggle,mb(6));
        scheduledBody=col();
        panel.addView(scheduledBody,mb(12));

        historyToggle=toggle(()->{historyOpen=!historyOpen;renderHistory();});
        panel.addView(historyToggle,mb(6));
        historyBody=col();
        panel.addView(historyBody,mb(12));

        TextView gesture=text("Deslizá a la derecha para volver a Mapa",11,MUTED,false);
        gesture.setGravity(Gravity.CENTER);
        gesture.setPadding(0,dp(5),0,dp(5));
        panel.addView(gesture);

        setShiftActive(false);
        setCounters(null,null,null);
        setPendingRequest(null,0,0);
        renderActive();
        renderScheduled();
        renderHistory();
    }

    // The controller-facing contract is preserved exactly.
    public void setListener(Listener next){
        listener=next;
        renderRequest();
        renderActive();
        renderScheduled();
    }
    public void setBusy(boolean next){
        busy=next;
        renderRequest();
        renderActive();
        renderScheduled();
    }
    public void setShiftActive(boolean isActive){
        shiftBadge.setText(isActive?"✓ Jornada activa":"○ Jornada detenida");
        shiftBadge.setTextColor(isActive?GREEN:MUTED);
    }
    public void setCounters(Integer fresh,Integer accepted,Integer inJourney){
        countNew.setText(number(fresh));
        countAccepted.setText(number(accepted));
        countInJourney.setText(number(inJourney));
    }
    public void setConnection(String status,boolean connected){
        String label=s(status);
        if(connected && label.startsWith("Solicitudes reales · sincronizado hace ")){
            label="Conectado · hace "+label.substring("Solicitudes reales · sincronizado hace ".length());
        }
        connectionText.setText(label);
        connectionText.setTextColor(connected?GREEN:MUTED);
        // A successful refresh clears ONLY the old transient refresh caption.
        // Errors and confirmations remain visible until the controller changes them.
        if(connected && "Actualizando solicitudes…".contentEquals(feedbackText.getText())){
            setFeedback("",false);
        }
    }
    public void setFeedback(String message,boolean positive){
        String shown=s(message);
        feedbackText.setText(shown);
        feedbackText.setTextColor(positive?GREEN:MUTED);
        feedbackText.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);
    }
    public void setPendingRequest(Request next,int currentIndex,int totalCount){
        request=next;
        index=currentIndex;
        total=totalCount;
        renderRequest();
    }
    public void setActiveTrips(List<Trip> rows){
        active.clear();
        if(rows!=null)active.addAll(rows);
        renderActive();
    }
    public void setScheduledTrips(List<Trip> rows){
        scheduled.clear();
        if(rows!=null)scheduled.addAll(rows);
        renderScheduled();
    }
    public void setHistory(List<String> rows){
        history.clear();
        if(rows!=null)history.addAll(rows);
        renderHistory();
    }

    private void renderRequest(){
        requestBody.removeAllViews();
        if(request==null){
            LinearLayout line=row();
            TextView symbol=text("◉",19,GOLD,true);
            line.addView(symbol,new LinearLayout.LayoutParams(dp(27),-2));
            line.addView(text("Sin solicitudes nuevas",16,WHITE,true),
                    new LinearLayout.LayoutParams(0,-2,1));
            requestBody.addView(line,mb(5));
            TextView note=text("Te avisaremos cuando llegue una petición.",12,MUTED,false);
            note.setPadding(dp(27),0,0,0);
            requestBody.addView(note);
            return;
        }
        final Request r=request;
        LinearLayout heading=row();
        TextView h=text("Nueva solicitud",19,WHITE,true);
        heading.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        TextView sequence=text(total>1?(index+1)+" de "+total:"Tu Viaje",11,GOLD,true);
        sequence.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        heading.addView(sequence,new LinearLayout.LayoutParams(-2,dp(32)));
        requestBody.addView(heading,mb(8));
        if(!r.code.isEmpty())requestBody.addView(text("Pedido "+r.code,11,MUTED,false),mb(12));
        routeLine(requestBody,"ORIGEN",r.origin,GREEN,16);
        connector(requestBody);
        routeLine(requestBody,"DESTINO",r.destination,RED,16);
        rule(requestBody,15,13);
        LinearLayout metrics=row();
        metric(metrics,"DISTANCIA",r.distance);
        metric(metrics,"DURACIÓN",r.duration);
        metric(metrics,"IMPORTE",r.amount);
        requestBody.addView(metrics,mb(14));
        if(!r.customer.isEmpty())requestBody.addView(text("Pasajero · "+r.customer,12,MUTED,false),mb(12));
        LinearLayout actions=row();
        Button decline=button(busy?"Procesando…":"Rechazar",Style.SECONDARY);
        Button accept=button(busy?"Procesando…":"Aceptar",Style.PRIMARY);
        decline.setEnabled(listener!=null&&!busy);
        accept.setEnabled(listener!=null&&!busy);
        decline.setOnClickListener(v->{if(listener!=null)listener.onReject(r.id);});
        accept.setOnClickListener(v->{if(listener!=null)listener.onAccept(r.id);});
        addEqual(actions,decline,56,0,5);
        addEqual(actions,accept,56,5,0);
        requestBody.addView(actions);
        if(total>1){
            LinearLayout pager=row();
            Button prev=button("‹ Anterior",Style.UTILITY);
            Button next=button("Siguiente ›",Style.UTILITY);
            prev.setEnabled(listener!=null&&!busy);
            next.setEnabled(listener!=null&&!busy);
            prev.setOnClickListener(v->{if(listener!=null)listener.onPrevious();});
            next.setOnClickListener(v->{if(listener!=null)listener.onNext();});
            addEqual(pager,prev,46,0,5);
            addEqual(pager,next,46,5,0);
            requestBody.addView(pager,mt(9));
        }
    }

    private void renderActive(){
        setToggle(activeToggle,"Aceptadas y en curso",active.size(),activeOpen);
        activeBody.removeAllViews();
        activeBody.setVisibility(activeOpen?View.VISIBLE:View.GONE);
        if(!activeOpen)return;
        if(active.isEmpty()){
            emptySection(activeBody,"Sin viajes aceptados ni en curso.");
            return;
        }
        for(Trip t:active){
            LinearLayout card=tripCard(t);
            LinearLayout utilities=row();
            if(!t.phone.isEmpty()){
                Button phone=button("☎  Llamar",Style.UTILITY);
                Button whatsapp=button("◌  WhatsApp",Style.UTILITY);
                phone.setOnClickListener(v->{if(listener!=null)listener.onCall(t.phone);});
                whatsapp.setOnClickListener(v->{if(listener!=null)listener.onWhatsApp(t.phone);});
                addEqual(utilities,phone,46,0,4);
                addEqual(utilities,whatsapp,46,4,4);
            }
            Button route=button("➤  Ruta",Style.UTILITY);
            route.setOnClickListener(v->{if(listener!=null)listener.onRoute(t.origin);});
            addEqual(utilities,route,46,t.phone.isEmpty()?0:4,0);
            card.addView(utilities,mt(13));

            if("ACEPTADA".equals(t.status)){
                String phase="SIN_INICIAR".equals(t.phase)||t.phase.isEmpty()?
                        "EN_CAMINO":"EN_CAMINO".equals(t.phase)?"EN_PUNTO":"";
                if(!phase.isEmpty()){
                    Button pickup=button("EN_CAMINO".equals(phase)?
                            "Voy en camino":"Llegué al origen",Style.PRIMARY);
                    pickup.setEnabled(listener!=null&&!busy);
                    pickup.setOnClickListener(v->{if(listener!=null)listener.onPickupPhase(t.id,phase);});
                    card.addView(pickup,fullButton(50,10));
                }
            }
            if(ConductorOperationsPolicy.canStart(t.status)){
                Button start=button("Iniciar viaje",Style.OUTLINE);
                start.setEnabled(listener!=null&&!busy);
                start.setOnClickListener(v->{if(listener!=null)listener.onStartTrip(t.id);});
                card.addView(start,fullButton(50,9));
            }else if(ConductorOperationsPolicy.canFinish(t.status)){
                Button finish=button("Finalizar viaje",Style.DANGER);
                finish.setEnabled(listener!=null&&!busy);
                finish.setOnClickListener(v->{if(listener!=null)listener.onFinishTrip(t.id);});
                card.addView(finish,fullButton(50,9));
            }
            activeBody.addView(card,mb(9));
        }
    }

    private void renderScheduled(){
        setToggle(scheduledToggle,"Programadas / presupuesto",scheduled.size(),scheduledOpen);
        scheduledBody.removeAllViews();
        scheduledBody.setVisibility(scheduledOpen?View.VISIBLE:View.GONE);
        if(!scheduledOpen)return;
        if(scheduled.isEmpty()){
            emptySection(scheduledBody,"Sin solicitudes programadas pendientes.");
            return;
        }
        TextView note=text("Presupuestos y agenda · flujo separado del viaje inmediato",11,MUTED,false);
        scheduledBody.addView(note,mb(8));
        for(Trip t:scheduled){
            LinearLayout card=tripCard(t);
            if(ConductorOperationsPolicy.canQuote(t.status,t.pickupMode,t.fareMode)){
                Button quote=button("ENVIADO".equals(t.quoteStatus)?
                        "Editar presupuesto":"Preparar presupuesto",Style.PRIMARY);
                quote.setEnabled(listener!=null&&!busy);
                quote.setOnClickListener(v->{if(listener!=null)listener.onPrepareQuote(t.id);});
                card.addView(quote,fullButton(50,12));
            }else if("PENDIENTE".equals(t.status)){
                card.addView(text("Esta solicitud requiere revisar su estado o precio.",12,MUTED,false),
                        mt(10));
            }
            if("PENDIENTE".equals(t.status) && "schedule".equals(t.pickupMode)){
                Button reject=button("Rechazar solicitud programada",Style.OUTLINE);
                reject.setEnabled(listener!=null&&!busy);
                reject.setOnClickListener(v->{if(listener!=null)listener.onRejectScheduled(t.id);});
                card.addView(reject,fullButton(48,9));
            }
            scheduledBody.addView(card,mb(9));
        }
    }

    private void renderHistory(){
        setToggle(historyToggle,"Histórico de peticiones",history.size(),historyOpen);
        historyBody.removeAllViews();
        historyBody.setVisibility(historyOpen?View.VISIBLE:View.GONE);
        if(!historyOpen)return;
        if(history.isEmpty()){
            emptySection(historyBody,"No hay movimientos registrados por el servidor.");
            return;
        }
        for(int i=0;i<Math.min(40,history.size());i++){
            TextView entry=text(history.get(i),12,WHITE,false);
            entry.setLineSpacing(dp(3),1f);
            entry.setBackground(surface(CARD,13));
            entry.setPadding(dp(13),dp(12),dp(13),dp(12));
            historyBody.addView(entry,mb(7));
        }
    }

    private LinearLayout tripCard(Trip t){
        LinearLayout card=col();
        card.setBackground(surface(CARD,19));
        card.setPadding(dp(15),dp(14),dp(15),dp(15));

        LinearLayout header=row();
        TextView code=text(t.code.isEmpty()?"Traslado":t.code,14,WHITE,true);
        code.setSingleLine(true);
        code.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(code,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text(prettyStatus(t.status),10,
                "EN_VIAJE".equals(t.status)?GOLD:GREEN,true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10),dp(6),dp(10),dp(6));
        status.setBackground(outlineSurface(UTILITY,13,0));
        header.addView(status,new LinearLayout.LayoutParams(-2,-2));
        card.addView(header,mb(5));

        if(!t.time.isEmpty())card.addView(text(displayWhen(t.time),11,MUTED,false),mb(12));
        if(!t.customer.isEmpty()){
            TextView customer=text(t.customer,13,WHITE,true);
            card.addView(customer,mb(12));
        }
        routeLine(card,"ORIGEN",t.origin,GREEN,14);
        connector(card);
        routeLine(card,"DESTINO",t.destination,RED,14);

        if(!t.amount.isEmpty()){
            rule(card,13,10);
            LinearLayout total=row();
            total.addView(text("IMPORTE",10,MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
            TextView amount=text(t.amount,17,GOLD,true);
            amount.setGravity(Gravity.END);
            total.addView(amount,new LinearLayout.LayoutParams(-2,-2));
            card.addView(total);
        }
        if("ENVIADO".equals(t.quoteStatus)){
            TextView sent=text("Presupuesto enviado al pasajero",11,GREEN,false);
            card.addView(sent,mt(9));
        }
        if("EN_VIAJE".equals(t.status)){
            TextView tracking=text("●  Seguimiento GPS del viaje en curso",11,GREEN,false);
            card.addView(tracking,mt(9));
        }
        return card;
    }

    private enum Style {PRIMARY, SECONDARY, UTILITY, OUTLINE, DANGER}

    private Button button(String label,Style style){
        Button b=new Button(getContext());
        b.setAllCaps(false);
        b.setText(label);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,13);
        b.setTextColor(style==Style.DANGER?RED:WHITE);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(7),0,dp(7),0);
        b.setBackground(style==Style.PRIMARY?
                outlineSurface(PRIMARY,14,0):
                style==Style.OUTLINE?outlineSurface(SHELL,14,Color.rgb(76,114,101)):
                style==Style.DANGER?outlineSurface(UTILITY,14,Color.rgb(122,70,72)):
                outlineSurface(UTILITY,14,0));
        b.setElevation(0f);
        return b;
    }

    private TextView counter(LinearLayout row,String label){
        LinearLayout tile=col();
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(surface(CARD,15));
        TextView value=text("—",27,WHITE,true);
        value.setGravity(Gravity.CENTER);
        value.setIncludeFontPadding(false);
        tile.addView(value,mb(8));
        TextView caption=text(label,11,MUTED,false);
        caption.setGravity(Gravity.CENTER);
        tile.addView(caption);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(86),1);
        p.setMargins(dp(3),0,dp(3),0);
        row.addView(tile,p);
        return value;
    }

    private TextView toggle(Runnable action){
        TextView t=text("",14,WHITE,true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(54));
        t.setPadding(dp(14),dp(9),dp(14),dp(9));
        t.setBackground(surface(CARD,15));
        t.setOnClickListener(v->action.run());
        t.setFocusable(true);
        return t;
    }
    private void setToggle(TextView t,String label,int count,boolean expanded){
        t.setText(label+"  ("+count+")  "+(expanded?"⌃":"⌄"));
        t.setContentDescription(label+", "+count+
                (count==1?" elemento":" elementos")+", "+(expanded?"contraer":"desplegar"));
    }
    private void emptySection(LinearLayout parent,String caption){
        TextView empty=text(caption,12,MUTED,false);
        empty.setPadding(dp(13),dp(11),dp(13),dp(11));
        parent.addView(empty);
    }
    private void routeLine(LinearLayout parent,String label,String address,int marker,int size){
        LinearLayout row=row();
        TextView dot=text("●",15,marker,true);
        dot.setGravity(Gravity.TOP);
        row.addView(dot,new LinearLayout.LayoutParams(dp(25),-2));
        LinearLayout info=col();
        TextView name=text(label,10,MUTED,true);
        name.setLetterSpacing(.06f);
        info.addView(name,mb(3));
        TextView place=text(address.isEmpty()?"Dirección pendiente":address,size,WHITE,true);
        place.setLineSpacing(dp(2),1f);
        info.addView(place);
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(row);
    }
    private void connector(LinearLayout parent){
        TextView stem=text("│",13,MUTED,false);
        stem.setPadding(dp(3),dp(1),0,dp(1));
        parent.addView(stem);
    }
    private void metric(LinearLayout row,String name,String value){
        LinearLayout box=col();
        TextView label=text(name,10,MUTED,true);
        box.addView(label,mb(5));
        TextView number=text(value.isEmpty()?"—":value,16,WHITE,true);
        number.setMaxLines(2);
        box.addView(number);
        row.addView(box,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void rule(LinearLayout parent,int above,int below){
        View line=new View(getContext());
        line.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(1));
        params.topMargin=dp(above);
        params.bottomMargin=dp(below);
        parent.addView(line,params);
    }
    private String prettyStatus(String value){
        if("ACEPTADA".equals(value))return "ACEPTADA";
        if("EN_VIAJE".equals(value))return "EN VIAJE";
        if("CONFIRMADA".equals(value))return "CONFIRMADA";
        if("PENDIENTE".equals(value))return "PENDIENTE";
        return s(value).replace('_',' ');
    }
    private String displayWhen(String value){
        String clean=s(value);
        if(clean.length()>=16 && clean.charAt(4)=='-' && clean.charAt(7)=='-'){
            String day=clean.substring(0,10),hour=clean.substring(11,16);
            SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            if(day.equals(iso.format(new Date())))return "Hoy · "+hour;
            Calendar yesterday=Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR,-1);
            if(day.equals(iso.format(yesterday.getTime())))return "Ayer · "+hour;
            return day.substring(8,10)+"/"+day.substring(5,7)+"/"+day.substring(0,4)+" · "+hour;
        }
        return clean;
    }
    private static String number(Integer n){
        return n==null||n<0?"—":String.format(Locale.ROOT,"%02d",n);
    }

    private GradientDrawable surface(int color,int radius){
        return outlineSurface(color,radius,0);
    }
    private GradientDrawable outlineSurface(int color,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);
        return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView text=new TextView(getContext());
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);
        if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return text;
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
    private void addEqual(LinearLayout parent,Button button,int height,int start,int end){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1);
        p.setMarginStart(dp(start));
        p.setMarginEnd(dp(end));
        parent.addView(button,p);
    }
    private LinearLayout.LayoutParams fullButton(int height,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height));
        p.topMargin=dp(top);
        return p;
    }
    private LinearLayout.LayoutParams mt(int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top);
        return p;
    }
    private LinearLayout.LayoutParams mb(int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(bottom);
        return p;
    }
    private void gap(LinearLayout parent,int height){
        parent.addView(new View(getContext()),new LinearLayout.LayoutParams(1,dp(height)));
    }
    private int dp(float n){
        return (int)(getResources().getDisplayMetrics().density*n+.5f);
    }
}
