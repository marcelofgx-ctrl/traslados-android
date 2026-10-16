package uy.com.mapatrayectos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.Locale;

/**
 * R25.28: real, authenticated request data in a compact/expanded external bubble card.
 * Does not synthesize fare, position, ETA or route distance. Acceptance is handled
 * by the same versioned RPC as the embedded Conductor, after a second confirmation.
 */
public final class TripRequestCard extends ScrollView {
    public interface Decision { void onConfirm(boolean accept); }

    private static final int BG=Color.rgb(7,37,45);
    private static final int GOLD=Color.rgb(239,209,148);
    private static final int WHITE=Color.rgb(252,250,243);
    private static final int MUTED=Color.rgb(191,206,207);
    private static final int GREEN=Color.rgb(116,213,174);
    private final Context context;
    private final JSONObject request;
    private final Runnable dismiss, openDriver, openHistory, onResize;
    private final Decision decision;
    private boolean expanded=false, busy=false, acceptUnavailable=false;
    private String issueMessage="";
    private Boolean confirming=null;
    private final LinearLayout content;
    private final boolean fixed;
    private final boolean pending;
    private final double price;

    public TripRequestCard(Context context,JSONObject request,Runnable dismiss,
                           Runnable openDriver,Runnable openHistory,Runnable onResize,Decision decision){
        super(context);
        this.context=context;
        this.request=request;
        this.dismiss=dismiss;
        this.openDriver=openDriver;
        this.openHistory=openHistory;
        this.onResize=onResize;
        this.decision=decision;
        price=request.optDouble("quote_final_total",0);
        fixed="fixed".equals(request.optString("fare_mode"))
                &&"PRECIO_FIJO".equals(request.optString("quote_status"))
                &&Double.isFinite(price)&&price>0;
        pending="PENDIENTE".equals(request.optString("status"));
        setFillViewport(false);
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);
        setElevation(dp(12));
        setBackground(rounded(BG,GOLD,24));
        content=new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15),dp(9),dp(15),dp(13));
        addView(content,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        render();
    }

    public int heightPx(){
        int display=context.getResources().getDisplayMetrics().heightPixels;
        int width=context.getResources().getDisplayMetrics().widthPixels-dp(24);
        content.measure(View.MeasureSpec.makeMeasureSpec(width-dp(2),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int desired=content.getMeasuredHeight()+dp(4);
        return Math.max(dp(155),Math.min(desired,display-dp(132)));
    }

    public String reservationId(){return request.optString("id","");}
    public boolean hasFixedFare(){return fixed && pending && !reservationId().isEmpty();}
    public void done(boolean success,String message){
        busy=false;confirming=null;
        if(success){dismiss.run();return;}
        issueMessage=message==null?"No se pudo confirmar. Consultá Conductor.":message;
        acceptUnavailable=issueMessage.contains("horario")||issueMessage.contains("agenda");
        render();
        if(onResize!=null)onResize.run();
    }

    private void render(){
        content.removeAllViews();
        LinearLayout heading=new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView type=chip("TRASLADOS",GOLD,BG);
        heading.addView(type);
        TextView state=chip(acceptUnavailable?"● REVISAR AGENDA":pending?"● SOLICITUD":"● "+request.optString("status","VIAJE"),acceptUnavailable?GOLD:GREEN,BG);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(0,dp(32),1);
        st.leftMargin=dp(8);heading.addView(state,st);
        TextView close=text("×",29,WHITE,true);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Minimizar tarjeta y volver al globito");
        heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        close.setOnClickListener(v->dismiss.run());
        content.addView(heading);

        String money=fixed?String.format(new Locale("es","UY"),"UYU %,.0f",price):
            "Precio por confirmar";
        TextView fare=text(money,fixed?29:20,fixed?GOLD:WHITE,true);
        fare.setSingleLine(true);fare.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(fare,space(0,1));
        String when=request.optString("pickup_time","");
        if(when.length()>5)when=when.substring(0,5);
        String date=request.optString("pickup_date","");
        TextView dateLine=text((date.isEmpty()?"":"◷  "+date)+(when.isEmpty()?"":"  ·  "+when),11,MUTED,false);
        if(!date.isEmpty()||!when.isEmpty())content.addView(dateLine,space(1,7));

        // A pickup ETA is only displayed if both true road-routing values
        // exist in the authenticated response. Never label straight-line as road.
        double pickupKm=request.optDouble("pickup_route_distance_km",0);
        int pickupMin=request.optInt("pickup_route_duration_min",0);
        String pickup=(pickupKm>0&&pickupMin>0)
            ?String.format(Locale.US,"Recogida  %d min  ·  %.1f km",pickupMin,pickupKm)
            :"Recogida  ·  distancia y llegada por confirmar";
        content.addView(text(pickup,12,MUTED,false),space(3,8));
        divider();
        field("●  ORIGEN",request.optString("origin_text","Origen no disponible"));
        field("■  DESTINO",request.optString("destination_text","Destino no disponible"));
        double km=request.optDouble("route_distance_km",0);
        int mins=request.optInt("route_duration_min",0);
        String trip="Viaje";
        if(mins>0)trip+="  "+mins+" min";
        if(km>0)trip+=String.format(Locale.US,"  ·  %.1f km",km);
        if(mins<=0&&km<=0)trip+="  ·  recorrido sin estimación";
        content.addView(text(trip,12,GOLD,true),space(7,9));
        if(!issueMessage.isEmpty()){
            TextView issue=text("⚠ "+issueMessage,12,GOLD,true);
            issue.setPadding(dp(7),dp(7),dp(7),dp(7));
            issue.setBackground(rounded(Color.rgb(38,40,30),GOLD,12));
            content.addView(issue,space(3,6));
        }
        if(expanded){
            divider();
            String customer=request.optString("customer_name","");
            if(!customer.isEmpty())field("PASAJERO",customer);
            String code=request.optString("code","");
            if(!code.isEmpty())field("RESERVA",code);
            String comments=request.optString("comments","").trim();
            if(!comments.isEmpty()&&!"null".equalsIgnoreCase(comments))
                field("OBSERVACIONES",comments);
            TextView more=text("◈  Ruta, chat, llamada y estados en Conductor",11,MUTED,false);
            content.addView(more,space(4,5));
            TextView history=text("▦  VER HISTORIAL DEL CONDUCTOR  ↗",12,GOLD,true);
            history.setMinHeight(dp(42));history.setGravity(Gravity.CENTER);
            history.setContentDescription("Abrir historial del Conductor");
            content.addView(history,space(2,5));
            history.setOnClickListener(v->openHistory.run());
        }
        TextView expand=text(expanded?"⌃  MENOS DETALLES":"⌄  VER DETALLES",11,GOLD,true);
        expand.setGravity(Gravity.CENTER);
        expand.setMinHeight(dp(38));
        content.addView(expand);
        expand.setOnClickListener(v->{
            expanded=!expanded;confirming=null;render();
            if(onResize!=null)onResize.run();
        });
        if(confirming!=null){
            TextView ask=text(Boolean.TRUE.equals(confirming)
                ?"¿Confirmás la aceptación por "+money+"?"
                :"¿Confirmás que querés rechazar este viaje?",13,GOLD,true);
            content.addView(ask,space(3,8));
        }
        LinearLayout buttons=new LinearLayout(context);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        boolean canAct=hasFixedFare()&&!busy;
        String mainLabel=busy?"PROCESANDO…":confirming!=null?
            (confirming?"CONFIRMAR ACEPTACIÓN":"CONFIRMAR RECHAZO"):
            acceptUnavailable?"REVISAR AGENDA":canAct?"ACEPTAR":"ABRIR CONDUCTOR";
        Button main=button(mainLabel,true);
        buttons.addView(main,new LinearLayout.LayoutParams(0,dp(53),2));
        Button secondary=button(confirming!=null?"VOLVER":"RECHAZAR",false);
        LinearLayout.LayoutParams sec=new LinearLayout.LayoutParams(0,dp(53),1);
        sec.leftMargin=dp(8);buttons.addView(secondary,sec);
        content.addView(buttons,space(1,0));
        main.setEnabled(!busy);secondary.setEnabled(!busy);
        main.setOnClickListener(v->{
            if(!canAct||acceptUnavailable&&confirming==null){openDriver.run();return;}
            if(confirming!=null){
                busy=true;boolean accept=confirming;render();
                if(onResize!=null)onResize.run();
                decision.onConfirm(accept);
            }else{
                confirming=true;render();if(onResize!=null)onResize.run();
            }
        });
        secondary.setOnClickListener(v->{
            if(confirming!=null){confirming=null;render();if(onResize!=null)onResize.run();return;}
            if(canAct){confirming=false;render();if(onResize!=null)onResize.run();}
            else dismiss.run();
        });
        setContentDescription("Solicitud de traslado. "+money+". "+pickup+". "
                +request.optString("origin_text")+" a "+request.optString("destination_text"));
    }

    private void field(String key,String value){
        TextView label=text(key,10,GOLD,true);
        content.addView(label,space(2,0));
        TextView val=text(value,14,WHITE,true);
        val.setMaxLines(expanded?4:2);
        val.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(val,space(0,7));
    }
    private void divider(){
        View line=new View(context);line.setBackgroundColor(Color.rgb(47,86,92));
        content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private TextView chip(String value,int color,int bg){
        TextView v=text(value,10,color,true);
        v.setGravity(Gravity.CENTER);v.setPadding(dp(9),0,dp(9),0);
        v.setBackground(rounded(bg,color,14));
        v.setMinHeight(dp(32));return v;
    }
    private TextView text(String value,float size,int color,boolean bold){
        TextView v=new TextView(context);v.setText(value);v.setTextSize(size);
        v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private Button button(String label,boolean primary){
        Button v=new Button(context);v.setText(label);v.setTextSize(11.5f);
        v.setAllCaps(false);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setTextColor(primary?BG:WHITE);
        v.setPadding(dp(4),0,dp(4),0);
        v.setBackground(rounded(primary?GOLD:Color.rgb(18,44,52),GOLD,15));
        v.setContentDescription(label);
        return v;
    }
    private LinearLayout.LayoutParams space(int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top);p.bottomMargin=dp(bottom);return p;
    }
    private GradientDrawable rounded(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);
        return d;
    }
    private int dp(float v){return (int)(v*context.getResources().getDisplayMetrics().density+0.5f);}
}
