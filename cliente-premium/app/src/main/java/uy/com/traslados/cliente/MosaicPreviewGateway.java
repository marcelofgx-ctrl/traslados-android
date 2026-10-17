package uy.com.traslados.cliente;

import android.app.Activity;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One audited web-resource gateway for the Android passenger mosaic.
 *
 * Production v16 never calls this gateway; offline PR builds call it only
 * when BuildConfig.MOSAIC_PREVIEW_MODE is true.
 * Browser route and booking modules continue to own UX. No JS bridge, server
 * secrets or arbitrary cross-origin WebView access.
 */
public final class MosaicPreviewGateway {
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String APP_PATH = "/traslados-android/web-pasajero/";
    private static final String API_HOST = "zetaudvvutlouiqxopvg.supabase.co";
    private static final String PUBLIC_ROUTE_HOST = "traslados-web.marcelof-gx.workers.dev";
    private static final String IDE_HOST = "direcciones.ide.uy";
    private static final String PUB_KEY = "sb_publishable_HnbMZW2dKpm6mBq-y5qkaA_Jlfx4BB9";
    private static final Set<String> IDE_PATHS = immutableSet(
        "/api/v1/geocode/candidates", "/api/v1/geocode/direcUnica", "/api/v1/geocode/reverse");

    // These RPCs are already used by the passenger app; actual identity and
    // ownership checks remain on Supabase, not inside the WebView.
    private static final Set<String> OPERATIONAL_RPC = immutableSet(
        "customer_request_pickup_v1", "customer_request_pickup_fixed_v1",
        "customer_register", "customer_login", "customer_logout",
        "customer_claim_reservation", "customer_reservation_vehicle_v1",
        "customer_check_availability_v11_4", "customer_create_reservation_v12",
        "create_reservation", "customer_trip_chat_v1", "customer_trip_chat_send_v1",
        "cancel_reservation_by_token", "customer_cancel_reservation",
        "customer_quote_decision_v13", "customer_list_reservations_v15",
        "customer_list_reservations_v16", "get_reservation_by_token_v4",
        "get_reservation_by_token_v5", "public_app_config_v1",
        "public_driver_availability_v1", "public_cached_route_preview_v1",
        "public_reference_quote_v1");

    private static Set<String> immutableSet(String... names) {
        return Collections.unmodifiableSet(new java.util.HashSet<>(Arrays.asList(names)));
    }
    private MosaicPreviewGateway() {}

    public static WebResourceResponse intercept(Activity activity, WebResourceRequest request) {
        if (!BuildConfig.MOSAIC_PREVIEW_MODE) return null;
        Uri uri=request.getUrl();
        if(uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo()!=null || uri.getPort()!=-1) return blocked();
        final String host=uri.getHost(),path=uri.getPath(),method=request.getMethod();

        if(APP_HOST.equalsIgnoreCase(host) && path!=null
                && (path.equals(APP_PATH.substring(0,APP_PATH.length()-1))
                    || path.startsWith(APP_PATH)) && "GET".equals(method)) {
            if ((APP_PATH+"_public/pickup-eta").equals(path)) {
                try {
                    if(uri.getQueryParameterNames().size()!=2) return blocked();
                    double lat=Double.parseDouble(uri.getQueryParameter("lat"));
                    double lng=Double.parseDouble(uri.getQueryParameter("lng"));
                    if(!Double.isFinite(lat)||!Double.isFinite(lng)
                            ||lat < -35.3 || lat > -30 || lng < -58.8 || lng > -52.9)
                        return blocked();
                    JSONObject body=new JSONObject();
                    body.put("originLat",lat);body.put("originLng",lng);
                    return approvedPublicJson("https://"+API_HOST+"/functions/v1/pickup-eta",body.toString());
                }catch(Exception invalid){return blocked();}
            }
            String relative=path.length()<APP_PATH.length() ? "" : path.substring(APP_PATH.length());
            if(relative.isEmpty())relative="index.html";
            if(relative.contains("..")||relative.contains("\\")||relative.startsWith("/"))
                return blocked();
            try{
                String mime=URLConnection.guessContentTypeFromName(relative);
                if(relative.endsWith(".js"))mime="application/javascript";
                else if(relative.endsWith(".css"))mime="text/css";
                else if(relative.endsWith(".svg"))mime="image/svg+xml";
                else if(relative.endsWith(".webp"))mime="image/webp";
                else if(mime==null)mime="application/octet-stream";
                Map<String,String> headers=new HashMap<>();
                headers.put("Cache-Control","no-store");
                headers.put("X-Content-Type-Options","nosniff");
                return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,
                    activity.getAssets().open("premium-preview/"+relative));
            }catch(IOException notPackaged){return blocked();}
        }

        if(API_HOST.equalsIgnoreCase(host) && path!=null) {
            if("/rest/v1/rpc/public_driver_availability_v1".equals(path)
                    && uri.getQuery()==null && ("GET".equals(method)||"OPTIONS".equals(method)))
                return null;
            if("/rest/v1/rpc/public_reference_quote_v1".equals(path)
                    && ("GET".equals(method)||"OPTIONS".equals(method))
                    && uri.getQueryParameterNames().size()==1
                    && uri.getQueryParameter("p_distance_km")!=null)
                return null;
            if(BuildConfig.MOSAIC_PREVIEW_OPERATIONAL && !request.isForMainFrame()
                    && uri.getQuery()==null
                    && ("POST".equals(method)||"OPTIONS".equals(method))
                    && path.startsWith("/rest/v1/rpc/")
                    && OPERATIONAL_RPC.contains(path.substring("/rest/v1/rpc/".length())))
                return null;
        }

        if(PUBLIC_ROUTE_HOST.equalsIgnoreCase(host)
                && "/api/public/route-estimate".equals(path) && "GET".equals(method))
            return approvedPublicJson(uri.toString(),null);
        if(IDE_HOST.equalsIgnoreCase(host)&&IDE_PATHS.contains(path)&&"GET".equals(method))
            return approvedPublicJson(uri.toString(),null);
        if("tile.openstreetmap.org".equalsIgnoreCase(host) && "GET".equals(method)
                && path!=null && path.matches("/[0-9]+/[0-9]+/[0-9]+\\.png"))
            return null;
        return blocked();
    }

    private static WebResourceResponse approvedPublicJson(String target,String body){
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(target).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(14000);
            connection.setRequestProperty("Accept","application/json");
            if(body!=null){
                connection.setRequestMethod("POST");connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json");
                connection.setRequestProperty("Origin","https://marcelofgx-ctrl.github.io");
                connection.setRequestProperty("apikey",PUB_KEY);
                try(java.io.OutputStream out=connection.getOutputStream()){
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code=connection.getResponseCode();
            if(code<200||code>=600||(code>=300&&code<400))return blocked();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=code>=400?connection.getErrorStream():connection.getInputStream()){
                if(in==null)return blocked();
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1){
                    if(bytes.size()+n>524288)return blocked();
                    bytes.write(buf,0,n);
                }
            }
            Map<String,String> headers=new HashMap<>();
            headers.put("Cache-Control","no-store");
            headers.put("Access-Control-Allow-Origin","https://appassets.androidplatform.net");
            headers.put("X-Content-Type-Options","nosniff");
            return new WebResourceResponse("application/json","UTF-8",code,"Public service",headers,
                new ByteArrayInputStream(bytes.toByteArray()));
        }catch(Exception offline){return blocked();}
        finally{if(connection!=null)connection.disconnect();}
    }

    private static WebResourceResponse blocked(){
        return new WebResourceResponse("text/plain","UTF-8",503,"Preview isolated",
            Collections.singletonMap("Cache-Control","no-store"),
            new ByteArrayInputStream("Versión de prueba: recurso bloqueado"
                .getBytes(StandardCharsets.UTF_8)));
    }
}
