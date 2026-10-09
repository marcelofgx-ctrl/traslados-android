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
    private TransferAlerts(){}

    public static void ensureMonitor(Context c){
        String pin=c.getSharedPreferences("driver_session",Context.MODE_PRIVATE).getString("pin","");
        if(pin==null||pin.trim().isEmpty())return;
        try{
            Intent i=new Intent(c,uy.com.traslados.conductor.ReservationMonitorService.class);
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);
            else c.startService(i);
        }catch(Exception e){Log.w("MapaTransfers","Unable to start authenticated reservation monitor",e);}
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
        if(pin==null||pin.isEmpty())return;
        new Thread(()->{
            try{
                JSONArray rows=uy.com.traslados.conductor.Api.listActive(pin);
                if(pin.equals(app.getSharedPreferences("driver_session",Context.MODE_PRIVATE)
                        .getString("pin","")))updatePending(app,rows);
            }catch(Exception e){recordSyncError(app,e);}
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

    private static void announce(Context c,String action){
        Intent i=new Intent(action).setPackage(c.getPackageName());
        c.sendBroadcast(i);
    }
}
