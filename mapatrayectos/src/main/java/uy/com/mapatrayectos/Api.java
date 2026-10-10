package uy.com.mapatrayectos;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Api {
    public static final String BASE="https://zetaudvvutlouiqxopvg.supabase.co";
    public static final String KEY="sb_publishable_HnbMZW2dKpm6mBq-y5qkaA_Jlfx4BB9";
    private static volatile Context APP;
    private static final Object SYNC_LOCK=new Object();
    private static volatile boolean syncing=false;

    private static final java.util.concurrent.atomic.AtomicBoolean PRESENCE_BUSY=
        new java.util.concurrent.atomic.AtomicBoolean(false);
    public static boolean pickupPresenceEnabled(){
        return APP!=null&&APP.getSharedPreferences("pickup_presence",Context.MODE_PRIVATE)
            .getBoolean("enabled",false);
    }
    public static boolean pickupManuallyBusy(){
        return APP!=null&&APP.getSharedPreferences("pickup_presence",Context.MODE_PRIVATE)
            .getBoolean("manually_busy",false);
    }
    /** User PIN is used only for one-time device linking, never persisted. */
    public static boolean setPickupConsent(String pin,boolean enabled)throws Exception{
        // Privacy first: OFF must stop future GPS uploads even if offline.
        // Remote presence will expire after 90 s if the revocation request fails.
        if(!enabled&&APP!=null)APP.getSharedPreferences("pickup_presence",Context.MODE_PRIVATE)
            .edit().putBoolean("enabled",false).apply();
        registerDevice();
        String[] ids=credentials();JSONObject body=new JSONObject();
        body.put("p_device_id",ids[0]);body.put("p_device_secret",ids[1]);
        body.put("p_pin",enabled?pin:"");body.put("p_enabled",enabled);
        JSONObject result=new JSONObject(postRpc("mapa_presence_consent_v1",body));
        if(!result.optBoolean("ok",false))return false;
        APP.getSharedPreferences("pickup_presence",Context.MODE_PRIVATE).edit()
            .putBoolean("enabled",enabled).apply();
        return true;
    }
    /** Asynchronous, bounded heartbeat; never blocks GPS or writes secret/PIN to logs. */
    public static void publishPickupPresenceAsync(boolean active,boolean paused,
        boolean inTrip,double lat,double lng,float accuracy,long gpsTimeMs,long lastMovingAtMs){
        if(APP==null||!pickupPresenceEnabled()||!PRESENCE_BUSY.compareAndSet(false,true))return;
        final boolean busy=pickupManuallyBusy();
        new Thread(()->{
            try{
                String[] ids=credentials();
                JSONObject body=new JSONObject();
                body.put("p_device_id",ids[0]);body.put("p_device_secret",ids[1]);
                body.put("p_shift_active",active);body.put("p_shift_paused",paused);
                body.put("p_trip_active",inTrip);body.put("p_manually_busy",busy);
                body.put("p_lat",lat);body.put("p_lng",lng);
                body.put("p_accuracy_m",accuracy);body.put("p_gps_at_ms",gpsTimeMs);
                body.put("p_last_move_at_ms",lastMovingAtMs);
                JSONObject result=new JSONObject(postRpc("mapa_presence_ping_v1",body));
                // Revoke local permission if a different device has been linked.
                if(!result.optBoolean("ok",false)&&"not_authorized".equals(result.optString("reason"))){
                    APP.getSharedPreferences("pickup_presence",Context.MODE_PRIVATE)
                        .edit().putBoolean("enabled",false).apply();
                }
            }catch(Exception ignored){
                // Server expires location after 90 s; stale position is never published.
            }finally{PRESENCE_BUSY.set(false);}
        },"mapa-pickup-presence").start();
    }
    private Api(){}
    public static void init(Context context){APP=context.getApplicationContext();credentials();}

    private static String[] credentials(){
        if(APP==null)throw new IllegalStateException("Api not initialized");
        SharedPreferences sp=APP.getSharedPreferences("mapa_device",Context.MODE_PRIVATE);String id=sp.getString("device_id","");String secret=sp.getString("device_secret","");
        if(id.isEmpty()||secret.length()<24){id=UUID.randomUUID().toString();secret=(UUID.randomUUID().toString()+UUID.randomUUID().toString()).replace("-","");sp.edit().putString("device_id",id).putString("device_secret",secret).apply();}
        return new String[]{id,secret};
    }

    public static void syncPendingAsync(){if(APP==null||syncing)return;new Thread(()->{ synchronized(SYNC_LOCK){ if(syncing)return; syncing=true; }try{ syncPending(); }catch(Exception ignored){} finally{syncing=false;} },"mapa-sync").start();}
    public static void syncPending() throws Exception { registerDevice();TrackDb db=new TrackDb(APP);JSONArray shifts=db.unsyncedEndedShifts();for(int i=0;i<shifts.length();i++)syncShift(db,shifts.getJSONObject(i));JSONArray trips=db.unsyncedEndedTrips();for(int i=0;i<trips.length();i++)syncTrip(db,trips.getJSONObject(i));shifts=db.unsyncedEndedShifts();for(int i=0;i<shifts.length();i++)syncShift(db,shifts.getJSONObject(i));db.close(); }
    public static void syncTripAsync(String tripId){if(APP==null||tripId==null||tripId.isEmpty())return;new Thread(()->{try{registerDevice();TrackDb db=new TrackDb(APP);JSONObject t=db.getTrip(tripId);if(t!=null)syncTrip(db,t);db.close();}catch(Exception ignored){}},"mapa-sync-trip").start();}
    public static void syncShiftAsync(String shiftId){if(APP==null||shiftId==null||shiftId.isEmpty())return;new Thread(()->{try{registerDevice();TrackDb db=new TrackDb(APP);JSONObject s=db.getShift(shiftId);if(s!=null)syncShift(db,s);db.close();}catch(Exception ignored){}},"mapa-sync-shift").start();}

    private static void registerDevice() throws Exception {String[] c=credentials();JSONObject b=new JSONObject();b.put("p_device_id",c[0]);b.put("p_device_secret",c[1]);postRpc("mapa_register_device",b);}

    private static void syncShift(TrackDb db,JSONObject shift) throws Exception {
        String[] c=credentials();JSONObject payload=cleanShift(shift);JSONObject b=new JSONObject();b.put("p_device_id",c[0]);b.put("p_device_secret",c[1]);b.put("p_payload",payload);postRpc("mapa_sync_shift",b);String shiftId=shift.optString("shift_id","");JSONArray idle=db.unsyncedIdlePoints(shiftId);for(int from=0;from<idle.length();from+=250){JSONArray batch=slicePoints(idle,from,Math.min(idle.length(),from+250));JSONObject ib=new JSONObject();ib.put("p_device_id",c[0]);ib.put("p_device_secret",c[1]);ib.put("p_shift_id",shiftId);ib.put("p_points",batch);postRpc("mapa_sync_idle_points",ib);}db.markShiftSynced(shiftId,true);
    }

    private static void syncTrip(TrackDb db,JSONObject trip) throws Exception {
        String shiftId=trip.optString("shift_id","");JSONObject shift=db.getShift(shiftId);if(shift!=null){String[] c=credentials();JSONObject sb=new JSONObject();sb.put("p_device_id",c[0]);sb.put("p_device_secret",c[1]);sb.put("p_payload",cleanShift(shift));postRpc("mapa_sync_shift",sb);}String[] c=credentials();String tripId=trip.optString("trip_id","");JSONArray points=db.unsyncedTripPoints(tripId);if(points.length()==0){JSONObject b=new JSONObject();b.put("p_device_id",c[0]);b.put("p_device_secret",c[1]);b.put("p_trip",cleanTrip(trip));b.put("p_points",new JSONArray());postRpc("mapa_sync_trip",b);}else{for(int from=0;from<points.length();from+=250){JSONArray batch=slicePoints(points,from,Math.min(points.length(),from+250));JSONObject b=new JSONObject();b.put("p_device_id",c[0]);b.put("p_device_secret",c[1]);b.put("p_trip",cleanTrip(trip));b.put("p_points",batch);postRpc("mapa_sync_trip",b);}}db.markTripSynced(tripId);
    }

    private static JSONObject cleanShift(JSONObject s) throws JSONException {JSONObject o=new JSONObject();String[] keys={"shift_id","started_at_ms","ended_at_ms","distance_m","moving_ms","stopped_ms","trip_ms","start_zone","end_zone"};for(String k:keys)if(s.has(k)&&!s.isNull(k))o.put(k,s.get(k));else o.put(k,"");return o;}
    private static JSONObject cleanTrip(JSONObject s) throws JSONException {JSONObject o=new JSONObject();String[] keys={"trip_id","shift_id","started_at_ms","ended_at_ms","distance_m","moving_ms","stopped_ms","max_speed_kmh","avg_speed_kmh","start_zone","end_zone","start_lat","start_lon","end_lat","end_lon","start_address","end_address","trip_type","trip_status","amount_uyu"};for(String k:keys)if(s.has(k)&&!s.isNull(k))o.put(k,s.get(k));else o.put(k,"");return o;}
    private static JSONArray slicePoints(JSONArray src,int from,int to) throws JSONException {JSONArray out=new JSONArray();for(int i=from;i<to;i++){JSONObject p=src.getJSONObject(i);JSONObject o=new JSONObject();String[] keys={"point_id","recorded_at_ms","lat","lon","accuracy_m","speed_kmh","bearing_deg","zone"};for(String k:keys)if(p.has(k)&&!p.isNull(k))o.put(k,p.get(k));else o.put(k,"");out.put(o);}return out;}

    private static String postRpc(String fn,JSONObject body) throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(BASE+"/rest/v1/rpc/"+fn).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("apikey",KEY);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-Client-Info","mapa-trayectos-android/0.1-R6");try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=readAll(is);c.disconnect();if(code<200||code>=300)throw new IOException("HTTP "+code+" "+text);return text;}
    private static String readAll(InputStream is) throws Exception {if(is==null)return "";StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){String l;while((l=br.readLine())!=null)sb.append(l);}return sb.toString();}
}
