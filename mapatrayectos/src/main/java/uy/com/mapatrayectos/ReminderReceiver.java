package uy.com.mapatrayectos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/** R22.6: foreground callouts anchor to ✦; exact-alarm audio is kept alive with goAsync(). */
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!ReminderStore.ACTION.equals(i.getAction()))return;
        int id=i.getIntExtra("id",-1);
        ReminderStore.Item item=ReminderStore.get(c,id);
        if(item==null||item.done||item.fired)return;
        long now=System.currentTimeMillis();
        if(item.time>now+30000L)return;
        if(!ReminderStore.alert(c,item)){
            ReminderStore.recordError(c,"Alarma recibida pero Android bloqueó la notificación");
            return;
        }
        ReminderStore.markFired(c,id);
        // The map displays a callout as a REAL child of its FrameLayout when visible.
        boolean inMap=c.getSharedPreferences("bubble_state",Context.MODE_PRIVATE).getBoolean("ui_visible",false);
        if(inMap){
            Intent display=new Intent(MainActivity.ACTION_REMINDER_POPUP).setPackage(c.getPackageName());
            display.putExtra("id",id);c.sendBroadcast(display);
        }else if(Settings.canDrawOverlays(c)){
            try{c.startForegroundService(new Intent(c,ReminderBubbleService.class).putExtra("id",id));}
            catch(Exception ex){Log.w("MapaReminders","External overlay unavailable; persistent notification is fallback",ex);}
        }

        final PendingResult pending=goAsync();
        final Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                ReminderSound.Result outcome=ReminderSound.playBlocking(app);
                if(!outcome.ok)Log.w("MapaReminderAudio",outcome.detail);
            }catch(Exception ex){Log.e("MapaReminderAudio","Cue error",ex);}
            finally{pending.finish();}
        },"mapa-reminder-sound").start();
    }
}