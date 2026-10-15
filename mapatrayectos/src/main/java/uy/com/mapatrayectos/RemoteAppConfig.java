package uy.com.mapatrayectos;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small read-only public configuration snapshot. GPS, trips and native UI NEVER
 * await this fetch. No PIN, device ID, coordinates or driver token are sent.
 */
public final class RemoteAppConfig {
    private static final String PREFS="mapa_public_config_v1";
    private static final long TTL_MS=30L*60L*1000L;
    private static final String ENDPOINT=Api.BASE+"/rest/v1/rpc/public_app_config_v1";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private RemoteAppConfig(){}

    public static void refreshIfStale(Context context){
        Context app=context.getApplicationContext();
        SharedPreferences prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        long attempted=prefs.getLong("last_attempt",0);
        if(attempted>0 && now>=attempted && now-attempted<TTL_MS)return;
        if(!BUSY.compareAndSet(false,true))return;
        prefs.edit().putLong("last_attempt",now).apply();
        new Thread(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
                try{
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(3500);
                    c.setReadTimeout(5000);
                    c.setDoOutput(true);
                    c.setRequestProperty("apikey",Api.KEY);
                    c.setRequestProperty("Accept","application/json");
                    c.setRequestProperty("Content-Type","application/json");
                    try(OutputStream out=c.getOutputStream()){
                        out.write("{}".getBytes(StandardCharsets.UTF_8));
                    }
                    if(c.getResponseCode()!=200)return;
                    String payload=readBounded(c.getInputStream());
                    JSONObject object=new JSONObject(payload);
                    if(!validSnapshot(object))return;
                    int revision=object.getInt("revision");
                    int stored=prefs.getInt("revision",0);
                    if(revision<stored)return; // Rollback requires a newer revision.
                    prefs.edit().putString("snapshot",payload)
                        .putInt("revision",revision)
                        .putLong("last_success",System.currentTimeMillis()).apply();
                }finally{c.disconnect();}
            }catch(Exception ex){
                // Last known good snapshot stays available offline.
                Log.d("MapaConfig","Public config unreachable; using cached links");
            }finally{BUSY.set(false);}
        },"mapa-public-config").start();
    }

    public static String officialLink(Context context,String key){
        String fallback=defaultLink(key);
        if(fallback.isEmpty())return "";
        try{
            SharedPreferences prefs=context.getApplicationContext()
                .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            JSONObject object=new JSONObject(prefs.getString("snapshot","{}"));
            if(!validSnapshot(object))return fallback;
            String url=object.getJSONObject("links").optString(key,"");
            return validLink(key,url)?url:fallback;
        }catch(Exception ex){return fallback;}
    }

    private static String defaultLink(String key){
        if("booking_url".equals(key))return ShareLinkPolicy.BOOKING_URL;
        if("client_apk_url".equals(key))return ShareLinkPolicy.CLIENT_APK_URL;
        return "";
    }

    private static boolean validSnapshot(JSONObject object){
        try{
            if(object.getInt("schema_version")!=1 || object.getInt("revision")<1)return false;
            JSONObject links=object.getJSONObject("links");
            return validLink("booking_url",links.getString("booking_url"))
                &&validLink("client_apk_url",links.getString("client_apk_url"));
        }catch(Exception ex){return false;}
    }

    private static boolean validLink(String key,String raw){
        if(raw==null || raw.length()>512)return false;
        try{
            URL url=new URL(raw);
            if(!"https".equals(url.getProtocol())
                ||!"marcelofgx-ctrl.github.io".equals(url.getHost())
                ||url.getPort()!=-1 ||url.getUserInfo()!=null
                ||url.getQuery()!=null ||url.getRef()!=null)return false;
            String base="/traslados-android/web-pasajero/";
            if("booking_url".equals(key))return base.equals(url.getPath());
            return "client_apk_url".equals(key)
                &&url.getPath().startsWith(base+"downloads/traslados-cliente-premium-v")
                &&url.getPath().endsWith(".apk")
                &&url.getPath().length()<base.length()+90;
        }catch(Exception ex){return false;}
    }

    private static String readBounded(InputStream stream)throws Exception{
        try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];
            int n;
            while((n=in.read(buffer))!=-1){
                if(out.size()+n>16384)throw new IllegalArgumentException("config too large");
                out.write(buffer,0,n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
