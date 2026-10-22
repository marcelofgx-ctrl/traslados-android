package uy.com.mapatrayectos.conductorv0;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/**
 * CONDUCTOR V0 — brand-new UI, no legacy imports, no PIN, no network.
 *
 * An inert, native presentation component that belongs to Mapa's Activity.
 * Play/Stop, GPS, authorization, journeys and navigation remain in Mapa.
 * The caller may wire real request actions only in a reviewed future phase.
 *
 * Exact visual contract: docs/CONDUCTOR_V0_REINICIO_LIMPIO_20261021.md
 */
public final class ConductorV0View extends FrameLayout {
    private static final int BLACK=Color.rgb(0,0,0);
    private static final int CHARCOAL=Color.rgb(37,37,37);
    private static final int REJECT=Color.rgb(47,47,47);
    private static final int ACCEPT=Color.rgb(0,120,89);
    private static final int WHITE=Color.rgb(255,255,255);
    private static final int MUTED=Color.rgb(175,175,175);
    private static final int GREEN=Color.rgb(0,166,89);
    private static final int RED=Color.rgb(218,62,67);

    /** Immutable values from an authorized booking; never invented in this UI. */
    public static final class Request {
        public final String id, origin, destination, distance, duration, amount;
        public Request(String id,String origin,String destination,
                       String distance,String duration,String amount) {
            this.id=nonempty(id);
            this.origin=nonempty(origin);
            this.destination=nonempty(destination);
            this.distance=nonempty(distance);
            this.duration=nonempty(duration);
            this.amount=nonempty(amount);
        }
        private static String nonempty(String text) {
            return text==null?"":text.trim();
        }
        public boolean complete() {
            return !id.isEmpty()&&!origin.isEmpty()&&!destination.isEmpty();
        }
    }

    public interface Listener {
        void onAccept(String requestId);
        void onReject(String requestId);
    }

    private final LinearLayout cardBody;
    private final TextView shiftBadge, countNew, countAccepted, countInJourney;
    private Listener listener;
    private Request pending;

    public ConductorV0View(Context context) {
        super(context);
        setBackgroundColor(BLACK);
        setClickable(true);
        setContentDescription("Quick Trip. Panel Conductor dentro de Mapa.");

        ScrollView viewport=new ScrollView(context);
        viewport.setFillViewport(true);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.setClipToPadding(false);
        viewport.setPadding(dp(16),dp(12),dp(16),dp(28));
        addView(viewport,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout panel=vertical(context);
        panel.setPadding(dp(14),dp(20),dp(14),dp(20));
        panel.setBackground(shape(CHARCOAL,24));
        viewport.addView(panel,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=horizontal(context);
        LinearLayout titles=vertical(context);
        titles.addView(label(context,"QUICK TRIP",13,MUTED,false));
        gap(context,titles,7);
        titles.addView(label(context,"Conductor",29,WHITE,true));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));

        shiftBadge=label(context,"Jornada detenida",12,MUTED,false);
        shiftBadge.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        shiftBadge.setPadding(dp(4),dp(8),0,0);
        header.addView(shiftBadge,new LinearLayout.LayoutParams(dp(120),dp(64)));
        panel.addView(header,marginBottom(17));

        LinearLayout counters=horizontal(context);
        countNew=counter(context,counters,"Nuevas");
        countAccepted=counter(context,counters,"Aceptadas");
        countInJourney=counter(context,counters,"En viaje");
        panel.addView(counters,marginBottom(15));

        cardBody=vertical(context);
        cardBody.setPadding(dp(14),dp(20),dp(14),dp(22));
        cardBody.setBackground(shape(BLACK,20));
        cardBody.setMinimumHeight(dp(295));
        panel.addView(cardBody,marginBottom(14));

        TextView swipe=label(context,
            "➜  Deslizar a la derecha para volver a Mapa",12,MUTED,false);
        swipe.setGravity(Gravity.CENTER);
        swipe.setPadding(dp(1),dp(8),dp(1),dp(8));
        panel.addView(swipe,new LinearLayout.LayoutParams(-1,-2));

        setShiftActive(false);
        setCounters(null,null,null);
        setPendingRequest(null);
    }

    public void setListener(Listener next) {
        listener=next;
        renderCard();
    }

    /** UI only. This never starts/stops a journey or publishes any GPS. */
    public void setShiftActive(boolean active) {
        shiftBadge.setText(active?"● Jornada activa":"○ Jornada detenida");
        shiftBadge.setTextColor(active?GREEN:MUTED);
    }

    /** null means unknown: display a dash, never claim zero requests. */
    public void setCounters(Integer fresh,Integer accepted,Integer inJourney) {
        countNew.setText(value(fresh));
        countAccepted.setText(value(accepted));
        countInJourney.setText(value(inJourney));
    }

    public void setPendingRequest(Request request) {
        pending=request!=null&&request.complete()?request:null;
        renderCard();
    }

    private void renderCard() {
        cardBody.removeAllViews();
        if(pending==null) {
            cardBody.addView(label(getContext(),"Sin solicitudes nuevas",21,WHITE,true),
                marginBottom(18));
            TextView empty=label(getContext(),
                "Las solicitudes aparecerán aquí cuando haya alguna pendiente.",
                14,MUTED,false);
            empty.setPadding(0,0,0,dp(16));
            cardBody.addView(empty,marginBottom(16));
            return;
        }
        final Request current=pending;
        cardBody.addView(label(getContext(),"Nueva solicitud",22,WHITE,true),
            marginBottom(19));
        routeRow("ORIGEN",current.origin,GREEN);
        TextView connector=label(getContext(),"│",18,MUTED,false);
        connector.setPadding(dp(4),0,0,0);
        cardBody.addView(connector);
        routeRow("DESTINO",current.destination,RED);
        View line=new View(getContext());
        line.setBackgroundColor(Color.rgb(44,44,44));
        LinearLayout.LayoutParams lineP=new LinearLayout.LayoutParams(-1,dp(1));
        lineP.setMargins(0,dp(18),0,dp(18));
        cardBody.addView(line,lineP);

        LinearLayout totals=horizontal(getContext());
        total(totals,"DISTANCIA",current.distance);
        total(totals,"DURACIÓN",current.duration);
        total(totals,"IMPORTE",current.amount);
        cardBody.addView(totals,marginBottom(17));

        LinearLayout actions=horizontal(getContext());
        Button reject=action("Rechazar",REJECT),accept=action("Aceptar",ACCEPT);
        // No backend callbacks are available in phase 1; never pretend success.
        boolean wired=listener!=null;
        reject.setEnabled(wired);
        accept.setEnabled(wired);
        reject.setAlpha(wired?1f:.55f);
        accept.setAlpha(wired?1f:.55f);
        reject.setOnClickListener(v->{if(listener!=null)listener.onReject(current.id);});
        accept.setOnClickListener(v->{if(listener!=null)listener.onAccept(current.id);});
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(56),1);
        left.rightMargin=dp(4);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(56),1);
        right.leftMargin=dp(4);
        actions.addView(reject,left);
        actions.addView(accept,right);
        cardBody.addView(actions);
    }

    private void routeRow(String heading,String address,int color) {
        LinearLayout line=horizontal(getContext());
        TextView circle=label(getContext(),"●",19,color,true);
        circle.setGravity(Gravity.TOP);
        circle.setPadding(0,dp(6),0,0);
        line.addView(circle,new LinearLayout.LayoutParams(dp(26),-2));
        LinearLayout detail=vertical(getContext());
        detail.addView(label(getContext(),heading,12,MUTED,false));
        gap(getContext(),detail,4);
        detail.addView(label(getContext(),address,18,WHITE,true));
        line.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
        cardBody.addView(line);
    }

    private void total(LinearLayout target,String title,String value) {
        LinearLayout part=vertical(getContext());
        part.addView(label(getContext(),title,11,MUTED,false));
        gap(getContext(),part,7);
        TextView result=label(getContext(),value.isEmpty()?"—":value,18,WHITE,true);
        result.setMaxLines(2);
        part.addView(result);
        target.addView(part,new LinearLayout.LayoutParams(0,-2,1));
    }

    private TextView counter(Context context,LinearLayout strip,String title) {
        LinearLayout tile=vertical(context);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(shape(BLACK,15));
        tile.setPadding(dp(4),dp(10),dp(4),dp(10));
        TextView number=label(context,"—",30,WHITE,true);
        number.setGravity(Gravity.CENTER);
        tile.addView(number);
        TextView caption=label(context,title,13,MUTED,false);
        caption.setGravity(Gravity.CENTER);
        tile.addView(caption);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(96),1);
        p.setMargins(dp(3),0,dp(3),0);
        strip.addView(tile,p);
        return number;
    }

    private Button action(String value,int fill) {
        Button b=new Button(getContext());
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(shape(fill,15));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private String value(Integer n) {
        return n==null||n<0?"—":String.format(Locale.ROOT,"%02d",n);
    }

    private int dp(float n) {
        return (int)(getResources().getDisplayMetrics().density*n+.5f);
    }

    private GradientDrawable shape(int fill,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private static LinearLayout vertical(Context c) {
        LinearLayout v=new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private static LinearLayout horizontal(Context c) {
        LinearLayout v=new LinearLayout(c);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private static TextView label(Context c,String s,int size,int color,boolean bold) {
        TextView v=new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);
        v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private LinearLayout.LayoutParams marginBottom(int bottomDp) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(bottomDp);
        return p;
    }
    private static void gap(Context c,LinearLayout parent,int dp) {
        View spacer=new View(c);
        parent.addView(spacer,new LinearLayout.LayoutParams(1,
            Math.round(c.getResources().getDisplayMetrics().density*dp)));
    }
}
