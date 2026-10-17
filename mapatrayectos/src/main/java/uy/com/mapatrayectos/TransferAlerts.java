package uy.com.mapatrayectos;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared reservation state bridging embedded conductor and the native map bubble. */
public final class TransferAlerts {
    public static final String ACTION_NEW="uy.com.mapatrayectos.NEW_TRANSFER_REQUEST";
    public static final String ACTION_COUNT="uy.com.mapatrayectos.TRANSFER_REQUEST_COUNT";
    private static final String PREF="integrated_transfer_alerts";
    private static final java.util.concurrent.atomic.AtomicBoolean QUICK_REFRESH=
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final long MONITOR_STALE_MS=60000L;
    private TransferAlerts(){}

    public static void ensureMonitor(Context c){
        String pin=c.getSharedPreferences("driver_session",Context.MODE_PRIVATE).getString("pin","");
        if(pin==null||pin.trim().isEmpty()){
            c.getSharedPreferences("driver_monitor_health",Context.MODE_PRIVATE).edit()
                .putString("last_error_type","driver_pin_required").apply();
            return;
        }
        try{
            Intent i=new Intent(c,uy.com.traslados.conductor.ReservationMonitorService.class);
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);
            else c.startService(i);
        }catch(Exception e){Log.w("MapaTransfers","Unable to start authenticated reservation monitor",e);}
    }

    /** Only restart a stalled monitor; do not repeatedly start it every polling tick. */
    public static boolean monitorNeedsRestart(Context c){
        String pin=c.getSharedPreferences("driver_session",Context.MODE_PRIVATE).getString("pin","");
        if(pin==null||pin.trim().isEmpty())return false;
        long last=c.getSharedPreferences("driver_monitor_health",Context.MODE_PRIVATE)
            .getLong("last_attempt_at",0L);
        long age=System.currentTimeMillis()-last;
        return last<=0L||age<0L||age>MONITOR_STALE_MS;
    }
    public static String receptionStatus(Context c){
        String pin=c.getSharedPreferences("driver_session",Context.MODE_PRIVATE).getString("pin","");
        if(pin==null||pin.trim().isEmpty())
            return "Sin acceso del conductor. Abrí TRASLADOS CONDUCTOR e ingresá con tu PIN.";
        SharedPreferences health=c.getSharedPreferences("driver_monitor_health",Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        long last=health.getLong("last_success_at",0L);
        long quick=lastSync(c);
        long newest=Math.max(last,quick);
        String error=syncError(c);
        if(newest>0&&now>=newest&&now-newest<=45000L)
            return "Conexión verificada hace "+Math.max(0,(now-newest)/1000)+" s · "+
                pendingCount(c)+" pendiente(s).";
        if(newest>0&&now>=newest)
            return "Sin actualización hace "+((now-newest)/60000L)+" min"+
                (error.isEmpty()?"":(" · "+error))+".";
        return error.isEmpty()?"Todavía no hay una consulta de reservas confirmada.":
            "No se pudieron consultar reservas · "+error+".";
    }

    /** Last authenticated server snapshot. All quick-panel views consume this same cache. */
    public static JSONArray snapshot(Context c){
        String text=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("snapshot","[]");
        try{return new JSONArray(text);}catch(Exception e){return new JSONArray();}
    }
    public static long lastSync(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong("synced_at",0L);
    }
    public static String syncError(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("sync_error","");
    }
    public static void recordSyncError(Context c,Exception e){
        String message=e==null?"Error de conexión":e.getClass().getSimpleName();
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("sync_error",message).apply();
        announce(c,ACTION_COUNT);
    }
    public static void refreshAsync(Context c){
        final Context app=c.getApplicationContext();
        final String pin=app.getSharedPreferences("driver_session",Context.MODE_PRIVATE)
            .getString("pin","");
        if(pin==null||pin.isEmpty()){
            app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString("sync_error","Se requiere iniciar sesión como conductor").apply();
            announce(app,ACTION_COUNT);
            return;
        }
        if(!QUICK_REFRESH.compareAndSet(false,true))return;
        new Thread(()->{
            try{
                JSONArray rows=uy.com.traslados.conductor.Api.listActive(pin);
                if(pin.equals(app.getSharedPreferences("driver_session",Context.MODE_PRIVATE)
                        .getString("pin","")))updatePending(app,rows);
            }catch(Exception e){recordSyncError(app,e);}
            finally{QUICK_REFRESH.set(false);}
        },"mapa-quick-reservations").start();
    }
    public static int pendingCount(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getInt("pending",0);
    }

    public static String lastText(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("last_text","Nueva solicitud de traslado");
    }

    public static boolean hasUnseen(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("unseen",false);
    }

    public static void markViewed(Context c){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("unseen",false).apply();
        announce(c,ACTION_COUNT);
    }

    public static void updatePending(Context c,JSONArray rows){
        if(rows==null)return;
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        // Handle status changes from the SAME authenticated reservation RPC.
        JSONArray prior=snapshot(c);
        int pending=0;
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r!=null&&QuickReservationLogic.pending(r.optString("status")))pending++;
        }
        String asText=rows.toString();
        boolean changed=!asText.equals(p.getString("snapshot","[]"));
        int oldCount=p.getInt("pending",0);
        p.edit().putString("snapshot",asText).putLong("synced_at",System.currentTimeMillis())
            .putInt("pending",pending).putString("sync_error","").apply();
        if(changed||oldCount!=pending)announce(c,ACTION_COUNT);
        // While Mapa itself is open, the service may have been killed by Android.
        // Recover recent NEW requests from the authenticated snapshot as well.
        // Foreground only: background Android alerts remain the monitor's job.
        if(c.getSharedPreferences("bubble_state",Context.MODE_PRIVATE).getBoolean("ui_visible",false)){
            JSONObject recent=null;
            long newestTime=0L;
            for(int i=0;i<rows.length();i++){
                JSONObject fresh=rows.optJSONObject(i);
                if(fresh==null)continue;
                String id=fresh.optString("id","");
                if(id.isEmpty()||
                    !uy.com.traslados.conductor.ReservationAlertPolicy.recentPending(
                        fresh.optString("status",""),fresh.optString("created_at",""),
                        System.currentTimeMillis()))continue;
                boolean alreadyPending=false;
                for(int j=0;j<prior.length();j++){
                    JSONObject old=prior.optJSONObject(j);
                    if(old!=null&&id.equals(old.optString("id",""))&&
                        QuickReservationLogic.pending(old.optString("status",""))){
                        alreadyPending=true;break;
                    }
                }
                if(!alreadyPending){
                    long when=0L;
                    try{when=java.time.Instant.parse(fresh.optString("created_at","")).toEpochMilli();}
                    catch(Exception ignored){}
                    if(recent==null||when>=newestTime){recent=fresh;newestTime=when;}
                }
            }
            if(recent!=null)reportNew(c,recent);
        }
        if(prior.length()>0){
            for(int i=0;i<rows.length();i++){
                JSONObject fresh=rows.optJSONObject(i);
                if(fresh==null)continue;
                String id=fresh.optString("id","");
                if(id.isEmpty())continue;
                for(int j=0;j<prior.length();j++){
                    JSONObject before=prior.optJSONObject(j);
                    if(before==null||!id.equals(before.optString("id")))continue;
                    String was=before.optString("status"),now=fresh.optString("status");
                    if(!was.equals(now)&&("CANCELADA".equals(now)||"RECHAZADA_CLIENTE".equals(now))){
                        announceChange(c,fresh,now);
                    }
                    break;
                }
            }
        }
    }

    private static void announceChange(Context c,JSONObject r,String status){
        String code=r.optString("code","Traslado");
        android.app.NotificationManager nm=
            (android.app.NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        final String channel="mapa_transfer_status_v1";
        if(Build.VERSION.SDK_INT>=26)
            nm.createNotificationChannel(new android.app.NotificationChannel(channel,
                "Cambios en reservas",android.app.NotificationManager.IMPORTANCE_HIGH));
        Intent view=new Intent(c,uy.com.traslados.conductor.MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open_requests",true).putExtra("reservation_id",r.optString("id",""));
        android.app.PendingIntent pi=android.app.PendingIntent.getActivity(c,
            Math.abs(code.hashCode())%50000,view,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification n=new android.app.Notification.Builder(c,channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Cambio de reserva · "+code)
            .setContentText("CANCELADA".equals(status)?"El pasajero canceló la solicitud":"La solicitud fue rechazada por el cliente")
            .setContentIntent(pi).setAutoCancel(true).build();
        nm.notify(17000+Math.abs(code.hashCode())%8000,n);
    }

    public static void reportNew(Context c,JSONObject r){
        if(r==null)return;
        // The monitor and foreground fallback can receive the same request.
        // Deduplicate the IN-APP cue without suppressing the Android notification.
        String id=r.optString("id","");
        synchronized(TransferAlerts.class){
            if(!id.isEmpty()){
                SharedPreferences seen=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
                long now=System.currentTimeMillis();
                long prior=seen.getLong("alert_"+id,0L);
                if(prior>0L&&now>=prior&&now-prior<2*60*60*1000L)return;
                seen.edit().putLong("alert_"+id,now).apply();
            }
        }
        String origin=r.optString("origin_text","Origen");
        String dest=r.optString("destination_text","Destino");
        String customer=r.optString("customer_name","");
        String text=customer+" · "+origin+" → "+dest;
        if(text.length()>180)text=text.substring(0,177)+"…";
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString("last_text",text)
            .putString("last_id",r.optString("id",""))
            .putLong("last_at",System.currentTimeMillis())
            .putBoolean("unseen",true)
            .apply();
        announce(c,ACTION_NEW);
        announce(c,ACTION_COUNT);
    }


    /** The authenticated reservation snapshot is the source of truth for the floating notice. */
    public static JSONObject actionableFixedById(Context context,String id){
        if(id==null||id.isEmpty())return null;
        JSONArray rows=snapshot(context);
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r!=null&&id.equals(r.optString("id"))&&isActionableFixed(r))return r;
        }
        return null;
    }
    public static JSONObject lastActionableFixed(Context c){
        String id=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("last_id","");
        return actionableFixedById(c,id);
    }
    private static boolean isActionableFixed(JSONObject r){
        return r!=null&&"fixed".equals(r.optString("fare_mode"))
            &&"PRECIO_FIJO".equals(r.optString("quote_status"))
            &&r.optDouble("quote_final_total",0)>0
            &&uy.com.traslados.conductor.ReservationAlertPolicy.recentPending(
                r.optString("status"),r.optString("created_at"),System.currentTimeMillis());
    }
    private static String shortStop(String value){
        if(value==null||value.trim().isEmpty())return "Dirección no disponible";
        String v=value.trim().replaceAll("\\s+"," ");
        return v.length()>36?v.substring(0,35)+"…":v;
    }
    public static String fixedSummary(JSONObject r){
        if(r==null)return "Nueva solicitud";
        double km=r.optDouble("route_distance_km",0);
        int minutes=r.optInt("route_duration_min",0);
        String distance=km>0?String.format(java.util.Locale.US,"%.1f km",km):"";
        if(minutes>0)distance+=(distance.isEmpty()?"":" · ")+minutes+" min";
        return "Origen: "+shortStop(r.optString("origin_text"))+
            "\nDestino: "+shortStop(r.optString("destination_text"))+
            "\n"+(distance.isEmpty()?"":distance+" · ")+"UYU "+Math.round(r.optDouble("quote_final_total",0));
    }
    public interface FixedDecisionCallback{void finished(boolean confirmed,String message);}
    private static final java.util.Set<String> IN_FLIGHT=
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    /** Perform a real backend decision. Never fake an acceptance after a network failure. */
    public static void decideFixed(Context context,String id,boolean accept,FixedDecisionCallback callback){
        Context app=context.getApplicationContext();
        android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
        if(actionableFixedById(app,id)==null){
            if(callback!=null)ui.post(()->callback.finished(false,"La solicitud ya no está disponible."));
            return;
        }
        if(!IN_FLIGHT.add(id)){
            if(callback!=null)ui.post(()->callback.finished(false,"Ya se está procesando esta solicitud."));
            return;
        }
        new Thread(()->{
            boolean ok=false;
            String message="No se pudo confirmar la operación. Revisá la conexión.";
            try{
                String pin=app.getSharedPreferences("driver_session",Context.MODE_PRIVATE).getString("pin","");
                if(pin.isEmpty())throw new IllegalStateException("Sesión de conductor no disponible");
                JSONArray current=uy.com.traslados.conductor.Api.listActive(pin);
                JSONObject live=null;
                for(int i=0;i<current.length();i++){
                    JSONObject r=current.optJSONObject(i);
                    if(r!=null&&id.equals(r.optString("id"))){live=r;break;}
                }
                if(!isActionableFixed(live))
                    throw new IllegalStateException("Solicitud cerrada, modificada o vencida");
                JSONObject result=uy.com.traslados.conductor.Api.decideFixedPickup(pin,id,accept);
                String status=result.optString("status","");
                if(!(accept&&"ACEPTADA".equals(status)||!accept&&"RECHAZADA".equals(status)))
                    throw new IllegalStateException("El servidor no confirmó la decisión");
                ok=true;
                message=accept?"Traslado aceptado":"Solicitud rechazada";
                refreshAsync(app);
            }catch(Exception ex){
                android.util.Log.w("MapaTransfers","Fixed decision was not confirmed: "+ex.getClass().getSimpleName());
                if(ex instanceof IllegalStateException)message=ex.getMessage();
            }
            final boolean confirmed=ok;
            final String result=message;
            ui.post(()->{
                IN_FLIGHT.remove(id);
                if(callback!=null)callback.finished(confirmed,result);
            });
        },"mapa-fixed-reservation-decision").start();
    }

    private static void announce(Context c,String action){
        Intent i=new Intent(action).setPackage(c.getPackageName());
        c.sendBroadcast(i);
    }
}
