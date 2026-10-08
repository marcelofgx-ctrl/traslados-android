package uy.com.mapatrayectos;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class ReminderBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        String action=intent==null?"":intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||
           Intent.ACTION_TIMEZONE_CHANGED.equals(action)||"android.intent.action.MY_PACKAGE_REPLACED".equals(action)||
           "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)){
            ReminderStore.rearm(c);
        }
    }
}
