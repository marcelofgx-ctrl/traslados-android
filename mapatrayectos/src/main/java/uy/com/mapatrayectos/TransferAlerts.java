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
        int count=0;
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r!=null&&"PENDIENTE".equals(r.optString("status")))count++;
        }
        int old=pendingCount(c);
        if(old!=count){
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt("pending",count).apply();
            announce(c,ACTION_COUNT);
        }
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
