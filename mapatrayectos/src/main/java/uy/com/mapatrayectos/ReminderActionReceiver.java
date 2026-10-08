package uy.com.mapatrayectos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Safe notification shade actions, no activity launch required. */
public final class ReminderActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null)return;
        int id=intent.getIntExtra("id",-1);
        ReminderStore.Item item=ReminderStore.get(c,id);
        if(item==null)return;
        String a=intent.getAction();
        if("uy.com.mapatrayectos.REMINDER_DONE".equals(a)){
            ReminderStore.change(c,id,true,false,-1L);
        }else if("uy.com.mapatrayectos.REMINDER_SNOOZE".equals(a)){
            ReminderStore.change(c,id,false,false,System.currentTimeMillis()+10L*60L*1000L);
        }
    }
}