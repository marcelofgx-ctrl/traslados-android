package uy.com.traslados.conductor;

import android.app.Activity;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared driver integration API. Standalone builds do not install a host. */
public final class DriverHostBridge {
    public interface Host {
        void onPendingSnapshot(Context context, JSONArray rows);
        void onNewRequest(Context context, JSONObject reservation);
        void onDriverVisible(Activity activity);
        void onDriverHidden(Activity activity);
    }
    private static volatile Host host;
    private DriverHostBridge() {}
    public static void install(Host value) {
        if (value == null) throw new IllegalArgumentException("Host required");
        host = value;
    }
    public static boolean isEmbedded() { return host != null; }
    public static void onPendingSnapshot(Context context, JSONArray rows) {
        Host h=host; if (h!=null) h.onPendingSnapshot(context,rows);
    }
    public static void onNewRequest(Context context, JSONObject reservation) {
        Host h=host; if (h!=null) h.onNewRequest(context,reservation);
    }
    public static void onDriverVisible(Activity activity) {
        Host h=host; if (h!=null) h.onDriverVisible(activity);
    }
    public static void onDriverHidden(Activity activity) {
        Host h=host; if (h!=null) h.onDriverHidden(activity);
    }
}
