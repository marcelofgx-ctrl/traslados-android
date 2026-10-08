package uy.com.mapatrayectos;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null)return;
        int id=i.getIntExtra("id",-1);
        ReminderStore.Item item=ReminderStore.get(c,id);
        if(item==null||item.done||item.fired)return;
        if(System.currentTimeMillis()+30000L<item.time)return;
        ReminderStore.alert(c,item);
        ReminderStore.markFired(c,id);
    }
}
