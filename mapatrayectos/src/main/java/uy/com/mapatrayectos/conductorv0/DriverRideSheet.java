package uy.com.mapatrayectos.conductorv0;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import uy.com.mapatrayectos.TransferAlerts;

/**
 * C1 map-first driver card: presentation only, attached to the EXISTING Mapa
 * Activity. Never starts a new driver Activity, location service or RPC.
 *
 * It opens after server-confirmed acceptance; the card uses only a fresh,
 * authenticated TransferAlerts snapshot. No optimistic fake GPS, ETA, route,
 * passenger rating, payment method, or bonus.
 */
public final class DriverRideSheet extends LinearLayout {
    public interface Listener {
        void onVerifiedAction(String reservationId,DriverSheetPolicy.Action action);
        void onRoute(String address);
        void onDetails();
    }
    private static final int NIGHT=Color.rgb(3,34,41);
    private static final int GOLD=Color.rgb(237,198,112);
    private static final int WHITE=Color.rgb(247,249,247);
    private static final int MUTED=Color.rgb(181,205,209);
    private static final int GREEN=Color.rgb(88,213,165);
    private final Activity activity;
    private final Listener listener;
    private final TextView stateLabel,fareLabel,personLabel,originLabel,
        destinationLabel,journeyLabel,handleLabel;
    private final LinearLayout details;
    private final Button actionButton,routeButton;
    private String reservationId="";
    private DriverSheetPolicy.Action nextAction=DriverSheetPolicy.Action.NONE;
    private String navigationTarget="";
    private boolean expanded=true,verified=false;
    private int navigationInset=0;

    public DriverRideSheet(Activity a,Listener callbacks){
        super(a);
        activity=a;listener=callbacks;
        setOrientation(VERTICAL);
        setPadding(dp(17),dp(7),dp(17),dp(15));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(4,48,57),NIGHT});
        bg.setCornerRadii(new float[]{dp(26),dp(26),dp(26),dp(26),dp(21),dp(21),dp(21),dp(21)});
        bg.setStroke(dp(1),Color.rgb(67,114,118));
        setBackground(bg);
        setElevation(dp(20));
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Quick Trip. Servicio confirmado sobre el mapa.");

        handleLabel=t("━━━━",13,MUTED,true);
        handleLabel.setGravity(Gravity.CENTER);
        handleLabel.setContentDescription("Mostrar u ocultar detalles del viaje");
        addView(handleLabel,new LayoutParams(-1,dp(21)));
        handleLabel.setOnClickListener(v->toggle());
        handleLabel.setOnTouchListener(new View.OnTouchListener(){
            private float down;
            @Override public boolean onTouch(View v,MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){down=e.getY();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP){
                    float delta=e.getY()-down;
                    if(Math.abs(delta)>dp(22))setExpanded(delta>0?false:true);
                    else toggle();
                    return true;
                }
                return true;
            }
        });

        LinearLayout title=new LinearLayout(a);
        title.setOrientation(HORIZONTAL);title.setGravity(Gravity.CENTER_VERTICAL);
        stateLabel=t("Sincronizando servicio",13,GREEN,true);
        stateLabel.setPadding(dp(11),dp(7),dp(11),dp(7));
        stateLabel.setBackground(rounded(Color.rgb(13,83,74),18));
        title.addView(stateLabel,new LayoutParams(0,-2,1));
        TextView more=t("Detalles  ⌃",12,GOLD,true);
        more.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        more.setPadding(dp(8),dp(8),0,dp(8));
        title.addView(more,new LayoutParams(-2,-1));
        more.setOnClickListener(v->toggle());
        addView(title,new LayoutParams(-1,-2));

        fareLabel=t("Sincronizando…",29,WHITE,true);
        LayoutParams fareLp=new LayoutParams(-1,-2);
        fareLp.topMargin=dp(9);
        addView(fareLabel,fareLp);

        personLabel=t("Verificando pasajero",15,WHITE,true);
        personLabel.setMaxLines(1);
        personLabel.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams personLp=new LayoutParams(-1,-2);
        personLp.topMargin=dp(2);
        addView(personLabel,personLp);

        details=new LinearLayout(a);
        details.setOrientation(VERTICAL);
        LayoutParams detailsLp=new LayoutParams(-1,-2);
        detailsLp.topMargin=dp(11);
        addView(details,detailsLp);
        View divider=new View(a);divider.setBackgroundColor(Color.rgb(53,93,100));
        details.addView(divider,new LayoutParams(-1,dp(1)));
        originLabel=t("Recogida por confirmar",13,WHITE,false);
        destinationLabel=t("Destino por confirmar",13,WHITE,false);
        journeyLabel=t("",12,MUTED,false);
        addDetail("●  RECOGIDA",originLabel,GREEN);
        addDetail("◆  DESTINO",destinationLabel,GOLD);
        LayoutParams jl=new LayoutParams(-1,-2);jl.topMargin=dp(8);
        details.addView(journeyLabel,jl);

        LinearLayout actions=new LinearLayout(a);
        actions.setOrientation(HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams al=new LayoutParams(-1,dp(55));al.topMargin=dp(15);
        addView(actions,al);
        actionButton=new Button(a);
        actionButton.setAllCaps(false);actionButton.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);
        actionButton.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        actionButton.setTextColor(Color.rgb(8,32,38));
        actionButton.setBackground(rounded(GOLD,18));
        actionButton.setText("Esperando estado verificado");
        actionButton.setEnabled(false);
        actions.addView(actionButton,new LayoutParams(0,-1,1));
        actionButton.setOnClickListener(v->{
            if(verified && nextAction!=DriverSheetPolicy.Action.NONE &&
               ConductorLivePolicy.safeId(reservationId)){
                // Freeze controls until the RPC response and snapshot arrive.
                verified=false;actionButton.setEnabled(false);
                listener.onVerifiedAction(reservationId,nextAction);
            }
        });
        routeButton=new Button(a);
        routeButton.setAllCaps(false);routeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP,13);
        routeButton.setTextColor(WHITE);routeButton.setText("➤ Ruta");
        routeButton.setBackground(rounded(Color.rgb(25,72,82),15));
        LayoutParams rl=new LayoutParams(dp(92),-1);rl.leftMargin=dp(9);
        actions.addView(routeButton,rl);
        routeButton.setOnClickListener(v->{
            if(verified && !navigationTarget.isEmpty())listener.onRoute(navigationTarget);
        });
        setExpanded(true);
    }

    public void focusConfirmed(String id){
        if(!ConductorLivePolicy.safeId(id))return;
        reservationId=id;
        setExpanded(true);
        setVisibility(VISIBLE);
        showWaiting();
        refreshFromSnapshot();
    }
    public void refreshFromSnapshot(){
        if(reservationId.isEmpty())return;
        long now=System.currentTimeMillis(),synced=TransferAlerts.lastSync(activity);
        boolean fresh=synced>0 && now>=synced && now-synced<=45000L;
        if(!fresh){showWaiting();return;}
        JSONArray rows=TransferAlerts.snapshot(activity);
        JSONObject matching=null;
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r!=null&&reservationId.equals(r.optString("id",""))){
                matching=r;break;
            }
        }
        if(matching==null){showWaiting();return;}
        String status=matching.optString("status","");
        if(!DriverSheetPolicy.visible(status)){
            if("PENDIENTE".equals(status)){showWaiting();return;}
            hideAndClear();return; // canceled, rejected, finished or unknown
        }
        String phase=matching.optString("pickup_phase","");
        DriverSheetPolicy.Action action=DriverSheetPolicy.next(status,phase,fresh);
        nextAction=action;
        verified=action!=DriverSheetPolicy.Action.NONE;
        stateLabel.setText("●  "+DriverSheetPolicy.statusLabel(status,phase));
        double amount=matching.optDouble("quote_final_total",0d);
        fareLabel.setText(Double.isFinite(amount)&&amount>0&&amount<=1000000
            ?String.format(Locale.US,"UYU %,.0f",amount).replace(',','.')
            :"Precio no confirmado");
        String name=matching.optString("customer_name","").trim();
        personLabel.setText(name.isEmpty()?"Pasajero por confirmar":name);
        originLabel.setText(safe(matching.optString("origin_text"),"Origen pendiente"));
        destinationLabel.setText(safe(matching.optString("destination_text"),"Destino pendiente"));
        double km=matching.optDouble("route_distance_km",0);
        int mins=matching.optInt("route_duration_min",0);
        journeyLabel.setText(km>0&&mins>0
            ?String.format(Locale.US,"Viaje · %.1f km · %d min",km,mins)
            :"Distancia y duración pendientes");
        navigationTarget="EN_VIAJE".equals(status)?
            matching.optString("destination_text","").trim():
            matching.optString("origin_text","").trim();
        actionButton.setText(DriverSheetPolicy.label(action));
        actionButton.setEnabled(verified);
        routeButton.setEnabled(verified&&!navigationTarget.isEmpty());
        setContentDescription("Quick Trip, "+DriverSheetPolicy.statusLabel(status,phase)+
            ". "+personLabel.getText()+". "+fareLabel.getText());
    }
    public void hideAndClear(){
        reservationId="";verified=false;
        nextAction=DriverSheetPolicy.Action.NONE;
        navigationTarget="";
        setVisibility(GONE);
    }
    public void updateInsets(int bottomPx){
        navigationInset=Math.max(0,bottomPx);
        if(getLayoutParams() instanceof FrameLayout.LayoutParams){
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)getLayoutParams();
            p.bottomMargin=navigationInset+dp(5);setLayoutParams(p);
        }
    }
    public boolean isShowingRide(){return getVisibility()==VISIBLE && !reservationId.isEmpty();}
    private void showWaiting(){
        verified=false;nextAction=DriverSheetPolicy.Action.NONE;
        navigationTarget="";
        stateLabel.setText("●  Sincronizando servicio");
        fareLabel.setText("Verificando…");
        personLabel.setText("Esperando estado confirmado");
        originLabel.setText("Origen por confirmar");
        destinationLabel.setText("Destino por confirmar");
        journeyLabel.setText("Sin mostrar una ruta sin verificar");
        actionButton.setText("Esperando estado verificado");
        actionButton.setEnabled(false);routeButton.setEnabled(false);
    }
    private void toggle(){setExpanded(!expanded);}
    private void setExpanded(boolean value){
        expanded=value;
        details.setVisibility(value?VISIBLE:GONE);
        handleLabel.setContentDescription(value?"Contraer detalles del servicio":"Expandir detalles del servicio");
    }
    private void addDetail(String label,TextView address,int accent){
        TextView heading=t(label,10,accent,true);
        LayoutParams hl=new LayoutParams(-1,-2);hl.topMargin=dp(10);
        details.addView(heading,hl);
        address.setMaxLines(2);address.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams vl=new LayoutParams(-1,-2);vl.topMargin=dp(3);
        details.addView(address,vl);
    }
    private static String safe(String raw,String fallback){
        if(raw==null||raw.trim().isEmpty())return fallback;
        return raw.trim();
    }
    private TextView t(String s,float sp,int color,boolean bold){
        TextView view=new TextView(activity);
        view.setText(s);view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        view.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        view.setIncludeFontPadding(true);
        return view;
    }
    private GradientDrawable rounded(int fill,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);
        d.setCornerRadius(dp(radius));return d;
    }
    private int dp(float n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
}
