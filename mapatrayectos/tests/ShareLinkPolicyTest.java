import uy.com.mapatrayectos.ShareLinkPolicy;
public final class ShareLinkPolicyTest {
    static void is(String expected,String actual,String why){
        if(!expected.equals(actual))throw new AssertionError(why+" expected="+expected+" actual="+actual);
    }
    static void truth(boolean check,String why){if(!check)throw new AssertionError(why);}
    public static void main(String[] args){
        String url=ShareLinkPolicy.CLIENT_APK_URL;
        truth(url.endsWith("/traslados-cliente-premium-v16.apk"),"new APK must be v16");
        is(url,ShareLinkPolicy.updatedDestination("download_url",
            "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v15.apk"),
            "previously confirmed official v15 must migrate to v16");
        truth(ShareLinkPolicy.needsMigration("download_url",
            "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v15.apk"),"v15 official must force review");
        is(url,ShareLinkPolicy.updatedDestination("download_url",
            "https://traslados-con-reserva.lovable.app/descargas"),"old Lovable default");
        is(ShareLinkPolicy.BOOKING_URL,ShareLinkPolicy.updatedDestination("booking_url",
            "https://traslados-web.marcelof-gx.workers.dev/"),"old Worker should never be passenger UI");
        is("https://example.org/custom.apk",ShareLinkPolicy.updatedDestination("download_url",
            "https://example.org/custom.apk"),"do not overwrite user custom download URL");
        truth(!ShareLinkPolicy.needsMigration("download_url",url),"up to date should not migrate");
        truth(!ShareLinkPolicy.needsMigration("booking_url",ShareLinkPolicy.BOOKING_URL),"web current");
        
        // New server-published Client v17 must reach existing R25.24 installs
        // without rebuilding Mapa, while private URLs remain unchanged.
        String v17="https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v17.apk";
        is(v17,ShareLinkPolicy.resolve("download_url",url,v17,url),
            "confirmed previous central official default follows new release");
        is(v17,ShareLinkPolicy.resolve("download_url",url,v17,""),
            "legacy current official v16 migrates even without marker");
        is(v17,ShareLinkPolicy.resolve("download_url",
            "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v15.apk",v17,""),
            "pre-v16 official still migrates to newest central release");
        is("https://example.org/custom.apk",
            ShareLinkPolicy.resolve("download_url","https://example.org/custom.apk",v17,url),
            "custom owner link never changes");
        is(v17,ShareLinkPolicy.resolve("download_url",v17,v17,url),
            "already current official does not change");
        is(ShareLinkPolicy.BOOKING_URL,ShareLinkPolicy.resolve("booking_url",
            ShareLinkPolicy.BOOKING_URL,ShareLinkPolicy.BOOKING_URL,""),
            "booking URL remains consistent");
        System.out.println("PASS R25.23: official Client Premium v16 and safe migration of confirmed v15 link");
    }
}
