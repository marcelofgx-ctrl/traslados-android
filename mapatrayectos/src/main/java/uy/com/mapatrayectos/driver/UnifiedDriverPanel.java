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
import java.util.Locale;
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
    private TextView connection,newCount,acceptedCount,travelCount,heading,historyLink;
    private LinearLayout contentCard;
    private JSONArray rows=new JSONArray();
    private boolean history=false,foreground=false;
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
        if(history){history=false;toggle();reload(true);}
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
    private void build(){
        setBackgroundColor(PETROL);
        ScrollView viewport=new ScrollView(host);
        viewport.setFillViewport(true);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.setClipToPadding(false);
        viewport.setBackgroundColor(PETROL);
        // Device safe area: the reference's black margin starts below
        // Android's status bar and above the gesture navigation bar.
        int inset=dp(25);
        int statusId=getResources().getIdentifier("status_bar_height","dimen","android");
        if(statusId>0)inset=Math.max(inset,getResources().getDimensionPixelSize(statusId));
        viewport.setPadding(dp(16),inset+dp(7),dp(16),dp(20));
        addView(viewport,new FrameLayout.LayoutParams(-1,-1));

        page=vertical();
        page.setBackground(bg(CARD,CARD,24));
        page.setPadding(dp(13),dp(15),dp(13),dp(15));
        viewport.addView(page,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=row();
        LinearLayout titleGroup=vertical();
        TextView brand=text("QUICK TRIP",13,MUTED,false);
        titleGroup.addView(brand);
        gap(titleGroup,9);
        heading=text("Conductor",28,WHITE,true);
        titleGroup.addView(heading);
        titleGroup.setOnClickListener(v->{
            history=!history;
            heading.setText(history?"Historial":"Conductor");
            render();
            reload(true);
        });
        titleGroup.setContentDescription("Conductor. Tocá para alternar entre solicitudes e historial");
        header.addView(titleGroup,new LinearLayout.LayoutParams(0,-2,1));
        connection=text("● Jornada detenida",12,MUTED,false);
        connection.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        header.addView(connection,new LinearLayout.LayoutParams(-2,dp(54)));
        page.addView(header,match(16));

        LinearLayout strip=row();
        newCount=metric(strip,"Nuevas");
        acceptedCount=metric(strip,"Aceptadas");
        travelCount=metric(strip,"En viaje");
        page.addView(strip,match(13));

        scroller=new ScrollView(host);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        list=vertical();
        list.setPadding(0,0,0,0);
        scroller.addView(list,new ScrollView.LayoutParams(-1,-2));
        page.addView(scroller,match(14));

        TextView swipe=center("➜   Deslizar a la derecha para volver a Mapa",12,MUTED,false);
        swipe.setContentDescription("Deslizá desde el borde izquierdo hacia la derecha para volver a Mapa");
        swipe.setPadding(0,dp(5),0,dp(4));
        page.addView(swipe,match(0));
        render();
    }
    private void toggle(){
        heading.setText(history?"Historial":"Conductor");
    }
    private void reload(boolean moveTop){
        if(!gateway.linked()){render();return;}
        if(!loading.compareAndSet(false,true))return;
        final boolean requestedHistory=history;
        io.execute(()->{
            try{
                final JSONArray answer=requestedHistory?gateway.history():gateway.requests();
                host.runOnUiThread(()->{
                    loading.set(false);
                    if(host.isFinishing()||host.isDestroyed())return;
                    if(history!=requestedHistory){reload(true);return;}
                    rows=answer;lastSuccessfulFetch=System.currentTimeMillis();
                    render();
                    if(moveTop)scroller.smoothScrollTo(0,0);
                });
            }catch(Exception e){
                host.runOnUiThread(()->{
                    loading.set(false);
                    if(host.isFinishing()||host.isDestroyed())return;
                    connection.setText("Sin sincronización. Verificá Internet y la vinculación en Mapa.");
                    if(lastSuccessfulFetch==0){rows=new JSONArray();render();}
                });
            }
        });
    }
    private void render(){
        boolean started=host.getSharedPreferences("tracking_state",Context.MODE_PRIVATE)
            .getBoolean("shift_active",false);
        connection.setText(started?"✓ Jornada activa":"○ Jornada detenida");
        connection.setTextColor(started?GREEN:MUTED);

        ArrayList<JSONObject> items=new ArrayList<>();
        int pending=0,accepted=0,traveling=0;
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            String status=r.optString("status");
            if("PENDIENTE".equals(status))pending++;
            if("ACEPTADA".equals(status))accepted++;
            if("EN_VIAJE".equals(status))traveling++;
            if(history||UnifiedDriverPolicy.active(status))items.add(r);
        }
        if(gateway.linked()){
            newCount.setText(String.format(Locale.ROOT,"%02d",pending));
            acceptedCount.setText(String.format(Locale.ROOT,"%02d",accepted));
            travelCount.setText(String.format(Locale.ROOT,"%02d",traveling));
        }else{
            newCount.setText("—");acceptedCount.setText("—");travelCount.setText("—");
        }
        int oldY=scroller.getScrollY();
        list.removeAllViews();

        if(!gateway.linked()){
            emptyCard("Solicitudes no vinculadas",
                "Mapa mantiene la jornada y el GPS. Todavía falta autorizar la conexión de reservas desde la configuración de Mapa.");
        }else if(items.isEmpty()){
            emptyCard(history?"Sin viajes en el historial":"Sin solicitudes nuevas",
                history?"Tus viajes registrados aparecerán aquí.":
                "Quedamos atentos a una nueva solicitud. No hay viajes para mostrar.");
        }else{
            for(JSONObject item:items)showReservation(item);
        }
        if(!history&&gateway.linked()){
            TextView refresh=center("Actualizar solicitudes",12,MUTED,false);
            refresh.setPadding(dp(5),dp(8),dp(5),dp(8));
            refresh.setOnClickListener(v->reload(false));
            list.addView(refresh,match(0));
        }
        if(oldY>0)scroller.post(()->scroller.scrollTo(0,oldY));
    }
    private void emptyCard(String title,String description){
        LinearLayout card=vertical();
        card.setBackground(bg(Color.BLACK,Color.BLACK,22));
        card.setPadding(dp(15),dp(21),dp(15),dp(26));
        TextView headline=text(title,21,WHITE,true);
        card.addView(headline,match(18));
        TextView detail=text(description,14,MUTED,false);
        detail.setMinHeight(dp(110));
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
    private void showReservation(JSONObject item){
        String id=item.optString("id","");
        String status=item.optString("status","");
        String phase=item.optString("pickup_phase","");
        if(id.isEmpty())return;
        LinearLayout card=vertical();
        card.setPadding(dp(13),dp(16),dp(13),dp(15));
        card.setBackground(bg(Color.BLACK,Color.BLACK,22));
        list.addView(card,match(11));

        String title="PENDIENTE".equals(status)?"Nueva solicitud":
            UnifiedDriverPolicy.label(status,phase);
        card.addView(text(title,22,WHITE,true),match(20));
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
        metricDetail(three,"IMPORTE",amount(item));
        card.addView(three,match(17));

        if(!history){
            if(UnifiedDriverPolicy.canAccept(status))
                actions(card,"Rechazar",()->performChange(item,"RECHAZADA",true),
                    "Aceptar",()->performChange(item,"ACEPTADA",false));
            else if(UnifiedDriverPolicy.canStart(status)){
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
        if(!history&&!UnifiedDriverPolicy.canAccept(status)){
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
