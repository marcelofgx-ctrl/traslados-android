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
public final class UnifiedDriverActivity extends Activity {
    private static final int PETROL=Color.rgb(5,34,42);
    private static final int CARD=Color.rgb(14,56,64);
    private static final int GOLD=Color.rgb(240,207,147);
    private static final int WHITE=Color.rgb(241,246,243);
    private static final int MUTED=Color.rgb(174,200,196);
    private static final int GREEN=Color.rgb(77,193,146);
    private static final int RED=Color.rgb(228,150,134);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean loading=new AtomicBoolean(false);
    private UnifiedDriverGateway gateway;
    private LinearLayout page,list;
    private ScrollView scroller;
    private TextView connection,metrics;
    private Button tripsButton,historyButton;
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

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Api.init(getApplicationContext());
        gateway=new UnifiedDriverGateway(this);
        getWindow().setStatusBarColor(PETROL);
        getWindow().setNavigationBarColor(PETROL);
        build();
    }
    @Override protected void onResume(){
        super.onResume();
        foreground=true;ui.removeCallbacks(refreshTick);
        if(gateway!=null)reload(false);
        ui.postDelayed(refreshTick,18000);
    }
    @Override protected void onPause(){
        foreground=false;ui.removeCallbacks(refreshTick);
        super.onPause();
    }
    @Override protected void onDestroy(){
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }
    @Override public void onBackPressed(){
        if(history){history=false;toggle();reload(true);}else backToMap();
    }
    private void backToMap(){
        finish();
        overridePendingTransition(R.anim.qt_from_driver_in,R.anim.qt_from_driver_out);
    }
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
                    &&downY>=160*density&&downY<=getWindow().getDecorView().getHeight()-160*density
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
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private TextView text(String value,int size,int color,boolean strong){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setLineSpacing(dp(2),1f);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button btn(String title,boolean primary){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(primary?PETROL:WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setPadding(dp(8),0,dp(8),0);
        b.setBackground(bg(primary?GOLD:CARD,primary?GOLD:0xff477379,12));
        return b;
    }
    private void gap(LinearLayout target,int h){
        View spacer=new View(this);
        target.addView(spacer,new LinearLayout.LayoutParams(1,dp(h)));
    }
    private void build(){
        page=vertical();page.setBackgroundColor(PETROL);
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(14),dp(14),dp(14),dp(12));
        Button map=btn("‹ MAPA",false);
        map.setOnClickListener(v->backToMap());
        top.addView(map,new LinearLayout.LayoutParams(dp(93),dp(48)));
        LinearLayout heading=vertical();heading.setPadding(dp(12),0,0,0);
        heading.addView(text("QUICK TRIP",12,GOLD,true));
        heading.addView(text("CONDUCTOR",22,WHITE,true));
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(top);

        connection=text("Revisando conexión y jornada…",12,MUTED,false);
        connection.setPadding(dp(16),dp(10),dp(14),dp(10));
        page.addView(connection);
        metrics=text("Nuevas —  ·  Aceptadas —  ·  En viaje —",13,GOLD,true);
        metrics.setPadding(dp(16),dp(10),dp(14),dp(13));
        page.addView(metrics);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(dp(14),0,dp(14),dp(8));
        tripsButton=btn("SOLICITUDES",true);historyButton=btn("HISTORIAL",false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(45),1);
        lp.setMargins(0,0,dp(5),0);tabs.addView(tripsButton,lp);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(45),1);
        hp.setMargins(dp(5),0,0,0);tabs.addView(historyButton,hp);
        tripsButton.setOnClickListener(v->{history=false;toggle();reload(true);});
        historyButton.setOnClickListener(v->{history=true;toggle();reload(true);});
        page.addView(tabs);
        scroller=new ScrollView(this);scroller.setFillViewport(true);
        list=vertical();list.setPadding(dp(14),dp(4),dp(14),dp(25));
        scroller.addView(list);
        page.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(page);
        toggle();render();
    }
    private void toggle(){
        tripsButton.setBackground(bg(history?CARD:GOLD,history?0xff477379:GOLD,12));
        tripsButton.setTextColor(history?WHITE:PETROL);
        historyButton.setBackground(bg(history?GOLD:CARD,history?GOLD:0xff477379,12));
        historyButton.setTextColor(history?PETROL:WHITE);
    }
    private void reload(boolean moveTop){
        if(!gateway.linked()){render();return;}
        if(!loading.compareAndSet(false,true))return;
        final boolean requestedHistory=history;
        io.execute(()->{
            try{
                final JSONArray answer=requestedHistory?gateway.history():gateway.requests();
                runOnUiThread(()->{
                    loading.set(false);
                    if(isFinishing()||isDestroyed())return;
                    if(history!=requestedHistory){reload(true);return;}
                    rows=answer;lastSuccessfulFetch=System.currentTimeMillis();
                    render();
                    if(moveTop)scroller.smoothScrollTo(0,0);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    loading.set(false);
                    if(isFinishing()||isDestroyed())return;
                    connection.setText("Sin sincronización. Verificá Internet y la vinculación en Mapa.");
                    if(lastSuccessfulFetch==0){rows=new JSONArray();render();}
                });
            }
        });
    }
    private void render(){
        boolean started=getSharedPreferences("tracking_state",MODE_PRIVATE)
            .getBoolean("shift_active",false);
        boolean enabled=Api.pickupPresenceEnabled();
        if(!gateway.linked()){
            connection.setText("SIN VINCULAR · Volvé a Mapa → Conductor y recogidas");
            metrics.setText("La vinculación se hace únicamente en Mapa");
            list.removeAllViews();
            cardMessage("Activar desde Mapa",
                "No hay acceso independiente aquí. Volvé a Mapa y autorizá esta instalación una sola vez.");
            Button home=btn("VOLVER A MAPA",true);
            home.setOnClickListener(v->backToMap());
            list.addView(home,new LinearLayout.LayoutParams(-1,dp(52)));
            return;
        }
        connection.setText(started?
            (enabled?"● Jornada activa en Mapa · disponibilidad sujeta a GPS y agenda":
                "● Jornada activa · recogidas todavía sin vincular"):
            "● Jornada detenida en Mapa · nuevas solicitudes visibles");
        int pending=0,accepted=0,traveling=0;
        ArrayList<JSONObject> items=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONObject o=rows.optJSONObject(i);if(o==null)continue;
            if(!history&&!UnifiedDriverPolicy.active(o.optString("status")))continue;
            items.add(o);
            String status=o.optString("status");
            if("PENDIENTE".equals(status))pending++;
            if("ACEPTADA".equals(status))accepted++;
            if("EN_VIAJE".equals(status))traveling++;
        }
        metrics.setText(history?"Historial: "+items.size()+" registros":
            "Nuevas "+pending+"    ·    Aceptadas "+accepted+"    ·    En viaje "+traveling);
        int oldY=scroller==null?0:scroller.getScrollY();
        list.removeAllViews();
        if(items.isEmpty())cardMessage(history?"Sin historial":"Sin solicitudes nuevas",
            history?"No hay viajes finalizados ni rechazados en el historial autorizado.":
            "La bandeja está vacía. Cuando llegue una solicitud aparecerá aquí para aceptar o rechazar.");
        // Do not display fictitious fares. Each card uses server-provided totals.
        for(JSONObject item:items)showReservation(item);
        if(!history){
            gap(list,12);
            Button update=btn("ACTUALIZAR AHORA",false);
            update.setOnClickListener(v->reload(false));
            list.addView(update,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        if(oldY>0)scroller.post(()->scroller.scrollTo(0,oldY));
    }
    private void cardMessage(String heading,String detail){
        LinearLayout card=vertical();card.setPadding(dp(17),dp(17),dp(17),dp(17));
        card.setBackground(bg(CARD,0xff436f70,17));
        card.addView(text(heading,18,GOLD,true));gap(card,7);
        card.addView(text(detail,13,WHITE,false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,dp(9));
        list.addView(card,p);
    }
    private String plain(String s){return s==null||s.trim().isEmpty()?"No informado":s.trim();}
    private String amount(JSONObject o){
        double amt=o.optDouble("quote_final_total",Double.NaN);
        if(Double.isNaN(amt)||amt<=0)amt=o.optDouble("quote_reference_total",Double.NaN);
        return Double.isFinite(amt)&&amt>0?"$ "+String.format(Locale.forLanguageTag("es-UY"),"%.0f",amt):
            "Precio no confirmado";
    }
    private void showReservation(JSONObject item){
        String id=item.optString("id","");
        String status=item.optString("status","");
        String phase=item.optString("pickup_phase","");
        if(id.isEmpty())return;
        LinearLayout card=vertical();card.setPadding(dp(14),dp(13),dp(14),dp(15));
        card.setBackground(bg(CARD,0xff477379,16));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,dp(7),0,dp(9));
        list.addView(card,cp);
        card.addView(text(UnifiedDriverPolicy.label(status,phase),12,
            "PENDIENTE".equals(status)?GOLD:GREEN,true));
        gap(card,6);
        String when=plain(item.optString("pickup_date"))+"  "+plain(item.optString("pickup_time"));
        card.addView(text(when+"   ·   "+plain(item.optString("code")),12,MUTED,false));gap(card,7);
        card.addView(text("ORIGEN  "+plain(item.optString("origin_text")),14,WHITE,true));gap(card,5);
        card.addView(text("DESTINO  "+plain(item.optString("destination_text")),14,WHITE,true));
        gap(card,10);
        double km=item.optDouble("route_distance_km",Double.NaN);
        String distance=Double.isFinite(km)&&km>0?
            String.format(Locale.forLanguageTag("es-UY"),"%.1f km",km):
            "Distancia sin confirmar";
        card.addView(text(distance+"    ·    "+amount(item),16,GOLD,true));
        gap(card,5);
        card.addView(text("Pasajero: "+plain(item.optString("customer_name")),12,MUTED,false));
        gap(card,11);
        if(!history){
            if(UnifiedDriverPolicy.canAccept(status)){
                buttons(card,"ACEPTAR",true,()->performChange(item,"ACEPTADA",false),
                    "RECHAZAR",false,()->performChange(item,"RECHAZADA",true));
            }else if(UnifiedDriverPolicy.canStart(status)){
                if(UnifiedDriverPolicy.canSetPickup(status,phase,"EN_CAMINO"))
                    button(card,"INICIAR RECOGIDA",true,
                        ()->performPickup(item,"EN_CAMINO"));
                else if(UnifiedDriverPolicy.canSetPickup(status,phase,"EN_PUNTO"))
                    button(card,"LLEGUÉ AL ORIGEN",true,
                        ()->performPickup(item,"EN_PUNTO"));
                button(card,"INICIAR VIAJE",false,
                    ()->performChange(item,"EN_VIAJE",true));
            }else if(UnifiedDriverPolicy.canFinish(status)){
                button(card,"FINALIZAR VIAJE",true,
                    ()->performChange(item,"FINALIZADA",true));
            }
        }
        buttons(card,"RUTA",false,()->openRoute(item),
            "LLAMAR",false,()->dial(item));
        if(!history)buttons(card,"WHATSAPP",false,()->whatsapp(item),
            "MENSAJES",false,()->showMessages(item));
    }
    private void button(LinearLayout card,String name,boolean primary,Runnable action){
        Button b=btn(name,primary);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(47));
        p.setMargins(0,dp(7),0,0);card.addView(b,p);
    }
    private void buttons(LinearLayout card,String left,boolean primaryLeft,Runnable leftAction,
                         String right,boolean primaryRight,Runnable rightAction){
        LinearLayout row=new LinearLayout(this);
        Button one=btn(left,primaryLeft),two=btn(right,primaryRight);
        one.setOnClickListener(v->leftAction.run());
        two.setOnClickListener(v->rightAction.run());
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1);
        a.setMargins(0,0,dp(4),0);row.addView(one,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(48),1);
        b.setMargins(dp(4),0,0,0);row.addView(two,b);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48));
        rp.setMargins(0,dp(7),0,0);card.addView(row,rp);
    }
    private void performChange(JSONObject row,String status,boolean confirm){
        String id=row.optString("id","");
        if(id.isEmpty())return;
        Runnable command=()->submitChange(id,status,null);
        if(confirm)new AlertDialog.Builder(this)
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
                runOnUiThread(()->{
                    loading.set(false);
                    if(isFinishing()||isDestroyed())return;
                    reload(false);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    loading.set(false);
                    if(isFinishing()||isDestroyed())return;
                    new AlertDialog.Builder(this).setTitle("No se guardó")
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
        try{startActivity(intent);}
        catch(Exception e){new AlertDialog.Builder(this).setMessage(
            "No se encontró una aplicación para abrir esta acción.")
            .setPositiveButton("CERRAR",null).show();}
    }
    private void showMessages(JSONObject row){
        final String id=row.optString("id","");
        if(id.isEmpty())return;
        LinearLayout dialogue=vertical();dialogue.setPadding(dp(14),dp(8),dp(14),dp(10));
        TextView historyView=text("Cargando mensajes…",12,PETROL,false);
        ScrollView chatScroll=new ScrollView(this);
        chatScroll.addView(historyView);
        dialogue.addView(chatScroll,new LinearLayout.LayoutParams(-1,dp(195)));
        EditText input=new EditText(this);
        input.setSingleLine(false);input.setMinLines(2);
        input.setHint("Escribir mensaje para el pasajero");
        dialogue.addView(input,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this)
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
                        .append(": ").append(body).append("\\n\\n");
                }
                final String shown=list.length()==0?"Todavía no hay mensajes.":list.toString().trim();
                runOnUiThread(()->{
                    if(dialog.isShowing())historyView.setText(shown);
                });
            }catch(Exception e){
                runOnUiThread(()->{
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
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed())
                        new AlertDialog.Builder(this).setMessage(
                            "No se pudo enviar el mensaje. Comprobá la conexión.")
                            .setPositiveButton("CERRAR",null).show();
                });
            }
        });
    }
}
