package uy.com.mapatrayectos;

/**
 * Canonical public share links for APK Mapa Trayectos.
 * Only links distributed by previous versions are migrated: user-edited
 * destinations must never be silently replaced.
 */
public final class ShareLinkPolicy {
    private ShareLinkPolicy() {}

    public static final String BOOKING_URL =
        "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/";
    public static final String CLIENT_APK_URL =
        "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v16.apk";

    public static String updatedDestination(String key, String current) {
        if("booking_url".equals(key) &&
           ("https://traslados-con-reserva.lovable.app/".equals(current) ||
            "https://traslados-web.marcelof-gx.workers.dev/".equals(current)))
            return BOOKING_URL;
        if("download_url".equals(key) &&
           ("https://traslados-con-reserva.lovable.app/descargas".equals(current) ||
            "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v15.apk".equals(current)))
            return CLIENT_APK_URL;
        return current; // preserve genuine custom links and already current ones
    }

    public static boolean needsMigration(String key,String current) {
        return current!=null && !updatedDestination(key,current).equals(current);
    }
}
