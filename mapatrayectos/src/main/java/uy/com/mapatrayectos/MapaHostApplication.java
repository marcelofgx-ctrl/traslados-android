package uy.com.mapatrayectos;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import uy.com.traslados.conductor.DriverHostBridge;

/**
 * Mapa owns the single floating shortcut and TransferAlerts, independently
 * of how the shared driver implementation is compiled. Installed before
 * any embedded driver Activity or Service can be created.
 */
public final class MapaHostApplication extends Application implements DriverHostBridge.Host {
    @Override public void onCreate() {
        super.onCreate();
        DriverHostBridge.install(this);
    }

    @Override public void onPendingSnapshot(Context context, JSONArray rows) {
        TransferAlerts.updatePending(context, rows);
    }

    @Override public void onNewRequest(Context context, JSONObject reservation) {
        TransferAlerts.reportNew(context, reservation);
    }

    @Override public void onDriverVisible(Activity activity) {
        TransferAlerts.markViewed(activity);
        activity.getSharedPreferences("bubble_state", MODE_PRIVATE)
            .edit().putBoolean("ui_visible", true).apply();
        try {
            boolean active=activity.getSharedPreferences("tracking_state",MODE_PRIVATE)
                .getBoolean("shift_active",false);
            boolean allowed=Settings.canDrawOverlays(activity);
            Intent svc=new Intent(activity,TrackingService.class)
                .setAction(active ? TrackingService.ACTION_UI_VISIBLE
                                  : TrackingService.ACTION_PREPARE_IDLE_BUBBLE);
            if(!active&&!allowed)activity.stopService(new Intent(activity,TrackingService.class));
            else if(!active&&Build.VERSION.SDK_INT>=26)activity.startForegroundService(svc);
            else activity.startService(svc);
        } catch(Exception e) {
            Log.w("MapaBubble","Prepare embedded driver bubble",e);
        }
    }

    @Override public void onDriverHidden(Activity activity) {
        activity.getSharedPreferences("bubble_state",MODE_PRIVATE)
            .edit().putBoolean("ui_visible",false).apply();
        try {
            Intent svc=new Intent(activity,TrackingService.class)
                .setAction(TrackingService.ACTION_UI_HIDDEN);
            boolean active=activity.getSharedPreferences("tracking_state",MODE_PRIVATE)
                .getBoolean("shift_active",false);
            if(active)activity.startService(svc);
            else if(Settings.canDrawOverlays(activity)) {
                if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(svc);
                else activity.startService(svc);
            }
        } catch(Exception e) {
            Log.w("MapaBubble","Restore embedded driver overlay",e);
        }
    }
}
