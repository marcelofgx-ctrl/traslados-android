package uy.com.mapatrayectos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/** Alarm receiver: do not consume reminders when notification permissions block delivery. */
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!ReminderStore.ACTION.equals(i.getAction()))return;
        int id=i.getIntExtra("id",-1);
        ReminderStore.Item item=ReminderStore.get(c,id);
        if(item==null||item.done||item.fired)return;
        long now=System.currentTimeMillis();
        if(item.time>now+30000L)return;
        if(ReminderStore.alert(c,item)){
            ReminderStore.markFired(c,id);
            // Best-effort cartoon speech bubble only if the user granted overlay permission.
            // A standard high-priority notification remains available if Android disallows popups.
            if(Settings.canDrawOverlays(c)){
                try{
                    Intent popup=new Intent(c,ReminderBubbleService.class).putExtra("id",id);
                    c.startForegroundService(popup);
                }catch(Exception ex){
                    Log.w("MapaReminders","Overlay service unavailable; heads-up notification remains",ex);
                }
            }
        }else{
            ReminderStore.recordError(c,"Alarma recibida pero Android bloqueó la notificación");
        }
    }
}