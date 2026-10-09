package uy.com.mapatrayectos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * R24.2: reservation accordion in the already approved premium ✦ shortcut.
 * Shares the actual Conductor RPC snapshot: no dummy reservations or duplicate DB.
 * Network is never called on the UI thread; business transitions stay in Conductor.
 */
public final class TransferQuickPanel {
    private static final int GOLD=Color.rgb(231,202,130);
    private static final int WHITE=Color.rgb(247,246,241);
    private static final int MUTED=Color.rgb(185,205,206);
    private static final int PETROL=Color.rgb(7,43,51);
    private final Activity a;
    private final PopupWindow popup;
    private final float initialSpeedKmh;
    private final LinearLayout root;
    private LinearLayout detailArea;
    private LinearLayout extraActions;
    private TextView head,preview,updateLabel;
    private boolean opened=false,otherOpened=false,disposed=false;
    private String expandedId="",expandedDay="hoy";
    private String lastRenderKey="";
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final BroadcastReceiver updates=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(disposed)return;
            String action=i==null?"":i.getAction();
            if(TransferAlerts.ACTION_COUNT.equals(action))render();
        }
    };

    public TransferQuickPanel(Activity activity,PopupWindow owner,float speedKmh){
        a=activity;popup=owner;initialSpeedKmh=speedKmh;
        root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0,0,0,dp(3));
        buildShell();
    }
    public View view(){return root;}

    public void installOtherActions(LinearLayout extra){
        extraActions=extra;
        extraActions.setVisibility(View.GONE);
        LinearLayout title=new LinearLayout(a);title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8),dp(4),dp(5),0);
        TextView name=label("OTRAS ACCIONES",10,GOLD,true);
        title.addView(name,new LinearLayout.LayoutParams(0,dp(35),1));
        TextView arrow=label("⌄",20,GOLD,true);title.addView(arrow);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(38)));
        root.addView(extraActions);
        title.setOnClickListener(v->{
            otherOpened=!otherOpened;
            extraActions.setVisibility(otherOpened?View.VISIBLE:View.GONE);
            arrow.setText(otherOpened?"⌃":"⌄");
            requestResize();
        });
    }

    public void start(){
        IntentFilter f=new IntentFilter(TransferAlerts.ACTION_COUNT);
        if(Build.VERSION.SDK_INT>=33)a.registerReceiver(updates,f,Context.RECEIVER_NOT_EXPORTED);
        else a.registerReceiver(updates,f);
        render(); // Last authenticated snapshot appears immediately.
        TransferAlerts.refreshAsync(a); // One real server refresh, off the UI thread.
    }
    public void dispose(){
        if(disposed)return;
        disposed=true;
        try{a.unregisterReceiver(updates);}catch(Exception ignored){}
        handler.removeCallbacksAndMessages(null);
    }

    private void buildShell(){
        LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),dp(2),dp(6),dp(1));
        head=label("🚘  RESERVAS",12,WHITE,true);
        bar.addView(head,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView arrow=label("⌄",19,GOLD,true);
        arrow.setGravity(Gravity.CENTER);bar.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(40)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(42)));
        bar.setBackground(subtle());
        bar.setOnClickListener(v->{
            opened=!opened;detailArea.setVisibility(opened?View.VISIBLE:View.GONE);
            arrow.setText(opened?"⌃":"⌄");
            if(opened)render();
            requestResize();
        });

        preview=label("Consultando solicitudes…",11,MUTED,false);
        preview.setPadding(dp(9),dp(6),dp(8),dp(7));
        preview.setMaxLines(2);preview.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        detailArea=new LinearLayout(a);detailArea.setOrientation(LinearLayout.VERTICAL);
        detailArea.setVisibility(View.GONE);root.addView(detailArea);
        updateLabel=label("",9.2f,MUTED,false);
        updateLabel.setPadding(dp(8),dp(4),0,dp(3));
        root.addView(updateLabel);
    }

    private void render(){
        if(disposed)return;
        String pin=a.getSharedPreferences("driver_session",Context.MODE_PRIVATE).getString("pin","");
        if(pin==null||pin.isEmpty()){
            head.setText("🚘  RESERVAS · CONECTAR");
            preview.setText("Ingresá a Traslados Conductor para iniciar sesión.");
            updateLabel.setText("Sin sesión de conductor");
            if(opened){
                detailArea.removeAllViews();
                button(detailArea,"ABRIR TRASLADOS CONDUCTOR",()->openConductor(""));
            }
            requestResize();return;
        }
        JSONArray raw=TransferAlerts.snapshot(a);
        ArrayList<JSONObject> list=new ArrayList<>();
        for(int i=0;i<raw.length();i++){
            JSONObject r=raw.optJSONObject(i);
            if(r!=null&&QuickReservationLogic.active(r.optString("status")))list.add(r);
        }
        Collections.sort(list,(r1,r2)->{
            int p1="EN_VIAJE".equals(r1.optString("status"))?0:QuickReservationLogic.pending(r1.optString("status"))?1:2;
            int p2="EN_VIAJE".equals(r2.optString("status"))?0:QuickReservationLogic.pending(r2.optString("status"))?1:2;
            if(p1!=p2)return Integer.compare(p1,p2);
            return Long.compare(sortTime(r1),sortTime(r2));
        });
        int fresh=0,quoted=0,confirmed=0,inTrip=0;
        for(JSONObject r:list){
            String status=r.optString("status"),quote=r.optString("quote_status","");
            if(QuickReservationLogic.pending(status)){
                if("ENVIADO".equals(quote))quoted++;else fresh++;
            }else if("EN_VIAJE".equals(status))inTrip++;
            else if(QuickReservationLogic.confirmed(status))confirmed++;
        }
        head.setText("🚘  RESERVAS  ·  "+(fresh+quoted+confirmed+inTrip));
        JSONObject next=nextTrip(list);
        if(next!=null){
            long t=sortTime(next);
            long left=(t-System.currentTimeMillis())/60000L;
            String msg="Próximo: "+time(next)+" · "+shortText(next.optString("destination_text"),31);
            if(left>0&&left<24L*60L)msg+=" · faltan "+left+" min";
            preview.setText(msg);
        }else preview.setText((fresh+quoted)>0?(fresh+quoted)+" solicitud(es) por atender":"Sin traslados confirmados próximos");

        long synced=TransferAlerts.lastSync(a);
        if(synced<=0){
            updateLabel.setText("Sin sincronización · tocá actualizar");
        }else{
            String at=new SimpleDateFormat("HH:mm:ss",new Locale("es","UY")).format(new Date(synced));
            boolean stale=QuickReservationLogic.stale(synced,System.currentTimeMillis());
            String err=TransferAlerts.syncError(a);
            updateLabel.setText((stale?"⚠ DATOS SIN ACTUALIZAR · ":"● Actualizado ")+at+(err.isEmpty()?"":" · sin conexión"));
            updateLabel.setTextColor(stale?GOLD:MUTED);
        }
        if(!opened){requestResize();return;}
        detailArea.removeAllViews();
        LinearLayout counters=new LinearLayout(a);
        counters.setGravity(Gravity.CENTER_VERTICAL);
        counter(counters,"NUEVAS",fresh);
        counter(counters,"COTIZADAS",quoted);
        counter(counters,"ACEPTADAS",confirmed+inTrip);
        detailArea.addView(counters,new LinearLayout.LayoutParams(-1,dp(53)));

        if(next!=null){
            TextView first=label("◷  PRÓXIMO: "+dateTime(next)+" · "+shortText(next.optString("destination_text"),22),
                10,GOLD,true);
            first.setPadding(dp(8),dp(9),dp(5),dp(7));detailArea.addView(first);
            first.setOnClickListener(v->{expandedId=next.optString("id");expandedDay=groupFor(next);render();});
        }
        if(list.isEmpty()){
            TextView empty=label("No hay reservas activas para mostrar.",11,MUTED,false);
            empty.setPadding(dp(9),dp(12),dp(8),dp(12));detailArea.addView(empty);
        }else{
            // Group by day; show up to three cards across all days to stay compact.
            int displayed=0;
            for(String group:new String[]{"hoy","mañana","próximas"}){
                ArrayList<JSONObject> bucket=new ArrayList<>();
                for(JSONObject r:list)if(group.equals(groupFor(r)))bucket.add(r);
                if(bucket.isEmpty())continue;
                final String g=group;
                boolean expanded=expandedDay.equals(group);
                TextView title=label((expanded?"⌄  ":"›  ")+group.toUpperCase(new Locale("es","UY"))
                    +"  ("+bucket.size()+")",10.5f,WHITE,true);
                title.setPadding(dp(10),dp(10),dp(4),dp(8));
                detailArea.addView(title);
                title.setOnClickListener(v->{expandedDay=expanded?"":g;expandedId="";render();});
                if(!expanded)continue;
                for(JSONObject r:bucket){
                    if(displayed>=3)break;
                    displayed++;
                    addReservationCard(r,list);
                }
            }
            if(list.size()>3){
                TextView more=label("VER TODAS LAS RESERVAS  ↗",10.5f,GOLD,true);
                more.setGravity(Gravity.CENTER);more.setPadding(0,dp(10),0,dp(9));
                detailArea.addView(more);more.setOnClickListener(v->openConductor(""));
            }
        }
        button(detailArea,"ACTUALIZAR RESERVAS   ↻",()->{
            TransferAlerts.refreshAsync(a);
            updateLabel.setText("Consultando Supabase…");
        });
        button(detailArea,"ABRIR GESTIÓN COMPLETA   ↗",()->openConductor(""));
        requestResize();
    }

    private void addReservationCard(JSONObject r,ArrayList<JSONObject> list){
        final String id=r.optString("id","");
        final boolean open=id.equals(expandedId);
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9),dp(8),dp(9),dp(8));card.setBackground(subtle());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.bottomMargin=dp(5);detailArea.addView(card,cp);
        TextView top=label((open?"⌄  ":"›  ")+time(r)+" · "+shortText(r.optString("customer_name"),18),11,WHITE,true);
        card.addView(top);
        String status=r.optString("status","PENDIENTE");
        TextView sub=label(statusLabel(status)+" · "+shortText(r.optString("origin_text"),23)+
            " → "+shortText(r.optString("destination_text"),24),9.8f,MUTED,false);
        sub.setMaxLines(2);card.addView(sub);
        top.setOnClickListener(v->{expandedId=open?"":id;render();});
        sub.setOnClickListener(v->{expandedId=open?"":id;render();});
        if(!open)return;
        line(card);
        detail(card,"CÓDIGO",r.optString("code",""));
        detail(card,"FECHA",dateTime(r));
        detail(card,"PASAJERO",r.optString("customer_name",""));
        detail(card,"ORIGEN",r.optString("origin_text",""));
        detail(card,"DESTINO",r.optString("destination_text",""));
        double km=r.optDouble("route_distance_km",0);
        if(km>0)detail(card,"RECORRIDO",String.format(Locale.US,"%.1f km",km));
        int mins=r.optInt("route_duration_min",0);
        if(mins>0)detail(card,"TIEMPO ESTIMADO",mins+" min");
        double total=r.optDouble("quote_final_total",0);
        if(total>0)detail(card,"PRESUPUESTO",String.format(Locale.US,"%.0f UYU",total));
        String comment=r.optString("comments","");
        if(!comment.isEmpty())detail(card,"OBSERVACIONES",comment);
        if(QuickReservationLogic.pending(status)&&conflicts(r,list)){
            TextView warn=label("⚠ POSIBLE CONFLICTO DE AGENDA · confirmar con Conductor",10,GOLD,true);
            warn.setPadding(dp(2),dp(8),dp(2),dp(8));card.addView(warn);
        }
        LinearLayout nav=new LinearLayout(a);
        smallButton(nav,"IR AL ORIGEN",()->navigate(r,true));
        smallButton(nav,"VER RUTA",()->navigate(r,false));
        card.addView(nav,new LinearLayout.LayoutParams(-1,dp(44)));
        String phone=r.optString("customer_phone","");
        if(!phone.isEmpty()){
            LinearLayout contacts=new LinearLayout(a);
            smallButton(contacts,"LLAMAR",()->dial(phone));
            smallButton(contacts,"WHATSAPP",()->whatsapp(phone,null));
            card.addView(contacts,new LinearLayout.LayoutParams(-1,dp(44)));
            button(card,"MENSAJE: ESTOY EN CAMINO",()->whatsapp(phone,"Hola, estoy en camino al punto de recogida de tu traslado."));
            button(card,"MENSAJE: YA LLEGUÉ",()->whatsapp(phone,"Hola, ya llegué al punto de encuentro de tu traslado."));
        }
        String idFinal=id;
        button(card,"GESTIONAR / PRESUPUESTAR   ↗",()->{
            if(isDriving()){
                new AlertDialog.Builder(a).setTitle("Modo conducción")
                    .setMessage("Para modificar o confirmar reservas, detené el vehículo en un lugar seguro. La navegación sigue disponible.")
                    .setPositiveButton("ENTENDIDO",null).show();return;
            }
            openConductor(idFinal);
        });
    }

    private boolean conflicts(JSONObject target,List<JSONObject> list){
        long from=sortTime(target);
        int dur=target.optInt("route_duration_min",60);
        for(JSONObject r:list){
            if(r==target||!QuickReservationLogic.confirmed(r.optString("status")))continue;
            if(QuickReservationLogic.conflicts(from,dur,sortTime(r),r.optInt("route_duration_min",60)))return true;
        }
        return false;
    }
    private JSONObject nextTrip(List<JSONObject> rows){
        JSONObject best=null;long ts=Long.MAX_VALUE,now=System.currentTimeMillis();
        for(JSONObject r:rows){
            if(!QuickReservationLogic.confirmed(r.optString("status")))continue;
            long t=sortTime(r);
            if(t>=now-90L*60000&&t<ts){best=r;ts=t;}
        }
        return best;
    }
    private String groupFor(JSONObject r){
        String day=r.optString("pickup_date","");
        Calendar c=Calendar.getInstance();
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        String today=f.format(c.getTime());
        c.add(Calendar.DAY_OF_YEAR,1);
        String tomorrow=f.format(c.getTime());
        return QuickReservationLogic.bucket(day,today,tomorrow)==0?"hoy":
            QuickReservationLogic.bucket(day,today,tomorrow)==1?"mañana":"próximas";
    }
    private long sortTime(JSONObject r){
        long v=QuickReservationLogic.atMillis(r.optString("pickup_date"),r.optString("pickup_time"));
        return v<0?Long.MAX_VALUE:v;
    }
    private String time(JSONObject r){String x=r.optString("pickup_time","");return x.length()>=5?x.substring(0,5):"--:--";}
    private String dateTime(JSONObject r){return r.optString("pickup_date","Sin fecha")+" · "+time(r);}
    private static String statusLabel(String s){
        if("PENDIENTE".equals(s))return "PENDIENTE";
        if("EN_VIAJE".equals(s))return "EN VIAJE";
        if("ACEPTADA".equals(s)||"CONFIRMADA".equals(s)||"ACEPTADA_CLIENTE".equals(s))return "CONFIRMADA";
        return s.replace('_',' ');
    }
    private static String shortText(String s,int n){
        if(s==null)return "";return s.length()>n?s.substring(0,n-1)+"…":s;
    }
    private boolean isDriving(){
        boolean moving=a.getSharedPreferences("tracking_state",Context.MODE_PRIVATE).getBoolean("vehicle_moving",false);
        return initialSpeedKmh>=5f||moving;
    }
    private void navigate(JSONObject r,boolean pickup){
        String name=r.optString(pickup?"origin_text":"destination_text","");
        String lat=pickup?"origin_lat":"destination_lat",lng=pickup?"origin_lng":"destination_lng";
        if(name.isEmpty())return;
        String coordinates=r.has(lat)&&!r.isNull(lat)&&r.has(lng)&&!r.isNull(lng)?
            r.optDouble(lat)+","+r.optDouble(lng):name;
        new AlertDialog.Builder(a).setTitle(pickup?"Navegar al origen":"Ver ruta")
            .setItems(new String[]{"Waze","Google Maps","Abrir gestión completa"},(d,choice)->{
                if(choice==2){openConductor(r.optString("id",""));return;}
                String url=choice==0?
                    "https://waze.com/ul?q="+Uri.encode(coordinates)+"&navigate=yes":
                    "https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(coordinates)+"&travelmode=driving";
                openUrl(url);
            }).show();
    }
    private void dial(String phone){
        String cleaned=phone.replaceAll("[^0-9+]","");
        if(cleaned.isEmpty())return;
        try{a.startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(cleaned))));}
        catch(Exception e){Toast.makeText(a,"No se pudo abrir el teléfono",Toast.LENGTH_SHORT).show();}
    }
    private void whatsapp(String phone,String text){
        String digits=phone.replaceAll("[^0-9]","");
        if(digits.length()<8)return;
        String url="https://wa.me/"+digits;
        if(text!=null)url+="?text="+Uri.encode(text);
        openUrl(url);
    }
    private void openUrl(String url){
        try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){Toast.makeText(a,"No se pudo abrir el enlace",Toast.LENGTH_SHORT).show();}
    }
    private void openConductor(String id){
        popup.dismiss();
        Intent i=new Intent(a,uy.com.traslados.conductor.MainActivity.class);
        if(id!=null&&!id.isEmpty()){
            i.putExtra("open_requests",true).putExtra("reservation_id",id);
        }
        try{a.startActivity(i);}catch(Exception e){
            Toast.makeText(a,"No se pudo abrir Traslados Conductor",Toast.LENGTH_LONG).show();
        }
    }
    private void counter(LinearLayout row,String heading,int n){
        LinearLayout box=new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2),dp(2),dp(2),dp(2));box.setBackground(subtle());
        TextView value=label(""+n,19,n>0?GOLD:WHITE,true);value.setGravity(Gravity.CENTER);
        TextView title=label(heading,8.1f,MUTED,true);title.setGravity(Gravity.CENTER);
        box.addView(value);box.addView(title);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(dp(2),dp(3),dp(2),dp(2));row.addView(box,lp);
    }
    private void detail(LinearLayout host,String heading,String value){
        if(value==null||value.trim().isEmpty())return;
        TextView label=label(heading,8.2f,GOLD,true);
        label.setPadding(dp(1),dp(7),0,0);host.addView(label);
        TextView v=label(value,10.5f,WHITE,false);v.setMaxLines(3);
        host.addView(v);
    }
    private void smallButton(LinearLayout row,String title,Runnable action){
        TextView v=label(title,9,GOLD,true);
        v.setGravity(Gravity.CENTER);v.setBackground(subtle());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
        p.setMargins(dp(2),dp(3),dp(2),dp(2));row.addView(v,p);
        v.setOnClickListener(w->action.run());
    }
    private void button(LinearLayout host,String title,Runnable action){
        TextView v=label(title,10,GOLD,true);
        v.setGravity(Gravity.CENTER);v.setBackground(subtle());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(37));
        p.topMargin=dp(4);host.addView(v,p);
        v.setOnClickListener(w->action.run());
    }
    private void line(LinearLayout card){
        View l=new View(a);l.setBackgroundColor(Color.argb(80,231,202,130));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.topMargin=dp(8);card.addView(l,p);
    }
    private GradientDrawable subtle(){
        GradientDrawable b=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.rgb(10,62,70),Color.rgb(8,47,55)});
        b.setCornerRadius(dp(12));b.setStroke(dp(1),Color.argb(110,231,202,130));
        return b;
    }
    private TextView label(String s,float size,int color,boolean bold){
        TextView v=new TextView(a);v.setText(s);v.setTextColor(color);v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private int dp(int px){return Math.round(px*a.getResources().getDisplayMetrics().density);}
    private void requestResize(){
        if(popup.isShowing()){
            handler.post(()->{
                if(!disposed&&popup.isShowing()){
                    try{popup.update();}catch(Exception ignored){}
                }
            });
        }
    }
}
