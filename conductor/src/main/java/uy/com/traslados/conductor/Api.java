package uy.com.traslados.conductor;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import android.content.*;
import java.util.*;

public final class Api {
    public static final String BASE="https://zetaudvvutlouiqxopvg.supabase.co";
    public static final String KEY="sb_publishable_HnbMZW2dKpm6mBq-y5qkaA_Jlfx4BB9";
    private Api(){}
    private static volatile Context APP;
    private static final Object LOG_LOCK=new Object();
    private static volatile boolean flushingLogs=false;
    private static final String LOG_PREF="traslados_diag";
    private static final String LOG_KEY="queue";

    public static final class ApiException extends IOException {
        public final int httpStatus; public final String backendCode; public final String backendMessage;
        public ApiException(int status,String code,String message,String raw){super("HTTP "+status+": "+(raw==null?"":raw));httpStatus=status;backendCode=code==null?"":code;backendMessage=message==null?"":message;}
    }
    public static void init(Context context){if(context==null)return;APP=context.getApplicationContext();new Thread(Api::flushQueuedLogs,"traslados-diag-flush").start();}

    public static JSONObject setupState() throws Exception {
        String raw=postRpc("driver_setup_state_v2",new JSONObject());
        return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
    }

    public static JSONObject claimPin(String pin,String fullName) throws Exception {
        JSONObject b=new JSONObject(); b.put("p_pin",pin); b.put("p_full_name",fullName);
        return new JSONObject(postRpc("claim_driver_pin",b));
    }

    public static boolean pinValid(String pin) throws Exception {
        JSONObject b=new JSONObject(); b.put("p_pin",pin);
        String raw=postRpc("driver_pin_valid",b).trim();
        return "true".equalsIgnoreCase(raw)||"1".equals(raw);
    }

    public static JSONArray listActive(String pin) throws Exception {
        JSONObject b=new JSONObject(); b.put("p_pin",pin);
        String raw=postRpc("driver_list_reservations_v2",b);
        return raw==null||raw.trim().isEmpty()?new JSONArray():new JSONArray(raw);
    }

    public static JSONArray listHistory(String pin) throws Exception {
        JSONObject b=new JSONObject(); b.put("p_pin",pin);
        String raw=postRpc("driver_history_v2",b);
        return raw==null||raw.trim().isEmpty()?new JSONArray():new JSONArray(raw);
    }

    public static JSONObject setStatus(String pin,String id,String status) throws Exception {
        JSONObject b=new JSONObject(); b.put("p_pin",pin); b.put("p_reservation_id",id); b.put("p_status",status);
        return new JSONObject(postRpc("driver_set_status_v2",b));
    }

    public static JSONObject sendQuote(String pin,String id,double km,int duration,double rate,double minimum,double tolls,double waiting,double pickup,double other,double reference,double total,String includes) throws Exception {
        JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_reservation_id",id);b.put("p_route_distance_km",km);b.put("p_route_duration_min",duration>0?duration:JSONObject.NULL);
        b.put("p_price_per_km",rate);b.put("p_minimum",minimum);b.put("p_tolls",tolls);b.put("p_waiting",waiting);b.put("p_pickup_extra",pickup);b.put("p_other",other);b.put("p_reference_total",reference);b.put("p_final_total",total);b.put("p_includes",includes==null?"":includes);
        return new JSONObject(postRpc("driver_send_quote_v10",b));
    }

    public static JSONObject getQuoteSettings(String pin) throws Exception {
        JSONObject b=new JSONObject();b.put("p_pin",pin);return new JSONObject(postRpc("driver_get_quote_settings_v10_7",b));
    }

    public static JSONObject setDefaultToll(String pin,double value) throws Exception {
        JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_value",value);return new JSONObject(postRpc("driver_set_default_toll_v10_7",b));
    }

    public static JSONObject sendQuoteV107(String pin,String id,double km,int duration,double rate,double minimum,int tollCount,double tollUnit,double waiting,double pickup,double other,double reference,double total,String includes) throws Exception {
        JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_reservation_id",id);b.put("p_route_distance_km",km);b.put("p_route_duration_min",duration>0?duration:JSONObject.NULL);
        b.put("p_price_per_km",rate);b.put("p_minimum",minimum);b.put("p_toll_count",tollCount);b.put("p_toll_unit_value",tollUnit);b.put("p_waiting",waiting);b.put("p_pickup_extra",pickup);b.put("p_other",other);b.put("p_reference_total",reference);b.put("p_final_total",total);b.put("p_includes",includes==null?"":includes);
        return new JSONObject(postRpc("driver_send_quote_v10_7",b));
    }

    public static void logEvent(String event,String message,String details){
        try{JSONObject d;try{d=details==null?new JSONObject():new JSONObject(details);}catch(Exception e){d=new JSONObject();d.put("raw_details",String.valueOf(details));}enqueueLog(event,event!=null&&event.contains("error")?"error":"info",message,d,null);flushQueuedLogs();}catch(Exception ignored){}
    }

    public static String publicMessage(String operation,Exception e){
        String technical=e==null?"":String.valueOf(e.getMessage());String backend=e instanceof ApiException?((ApiException)e).backendMessage:technical;String m=(backend+" "+technical).toLowerCase(Locale.ROOT);
        if(m.contains("pin incorrecto"))return "PIN incorrecto. Revisalo e intentá nuevamente.";
        if(m.contains("conductor ya está configurado"))return "El conductor ya está configurado en este sistema.";
        if(m.contains("sesión inválida")||m.contains("sesion invalida"))return "La sesión venció. Ingresá nuevamente.";
        if(m.contains("horario_ya_no_disponible")||m.contains("horario_no_disponible"))return "Ese horario ya no está disponible.";
        if(e instanceof java.net.SocketTimeoutException||m.contains("timed out")||m.contains("timeout"))return "La conexión está demorando demasiado. Revisá Internet e intentá nuevamente.";
        if(m.contains("unknownhost")||m.contains("failed to connect")||m.contains("network is unreachable"))return "No pudimos conectarnos. Revisá tu conexión a Internet e intentá nuevamente.";
        return "No pudimos completar la operación. Intentá nuevamente.";
    }

    public static String postRpc(String fn,JSONObject body) throws Exception {
        return request("POST",BASE+"/rest/v1/rpc/"+fn,body==null?"{}":body.toString());
    }

    private static String request(String method,String url,String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setRequestMethod(method);
        c.setRequestProperty("apikey",KEY);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("X-Client-Info","traslados-conductor-android/11.4-R10.4");
        if(body!=null){
            c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
            try(OutputStream os=c.getOutputStream()){os.write(body.getBytes(StandardCharsets.UTF_8));}
        }
        int code=c.getResponseCode();
        InputStream is=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        String txt=readAll(is);
        if(code<200||code>=300){
            String backendCode="",backendMessage="";try{JSONObject j=new JSONObject(txt);backendCode=j.optString("code","");backendMessage=j.optString("message","");}catch(Exception ignored){}
            ApiException ex=new ApiException(code,backendCode,backendMessage,txt);
            if(!url.endsWith("/log_app_event"))queueError(operationFromUrl(url),ex,publicMessage(operationFromUrl(url),ex),txt);
            throw ex;
        }
        if(!url.endsWith("/log_app_event"))flushQueuedLogs();
        return txt;
    }

    private static String operationFromUrl(String url){int i=url==null?-1:url.lastIndexOf('/');return i>=0&&i+1<url.length()?url.substring(i+1):"request";}
    private static void queueError(String operation,Exception e,String userMessage,String raw){
        try{JSONObject d=new JSONObject();d.put("operation",operation==null?"":operation);d.put("user_message",userMessage==null?"":userMessage);if(e instanceof ApiException){ApiException a=(ApiException)e;d.put("http_status",a.httpStatus);d.put("error_code",a.backendCode);d.put("backend_message",a.backendMessage);}else d.put("error_code",e==null?"":e.getClass().getSimpleName());if(raw!=null&&!raw.isEmpty())d.put("backend_response",raw.length()>1200?raw.substring(0,1200):raw);enqueueLog("api_error","error",e==null?"Error sin detalle":String.valueOf(e.getMessage()),d,null);flushQueuedLogs();}catch(Exception ignored){}
    }
    private static void enqueueLog(String event,String level,String message,JSONObject details,String reservationCode){
        if(APP==null)return;synchronized(LOG_LOCK){try{SharedPreferences sp=APP.getSharedPreferences(LOG_PREF,Context.MODE_PRIVATE);JSONArray q;try{q=new JSONArray(sp.getString(LOG_KEY,"[]"));}catch(Exception e){q=new JSONArray();}JSONArray trimmed=new JSONArray();int start=Math.max(0,q.length()-79);for(int i=start;i<q.length();i++)trimmed.put(q.get(i));JSONObject item=new JSONObject();item.put("event_id",UUID.randomUUID().toString());item.put("occurred_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date()));item.put("event",event==null?"event":event);item.put("level",level==null?"info":level);item.put("message",message==null?JSONObject.NULL:message);item.put("details",details==null?new JSONObject():details);item.put("reservation_code",reservationCode==null?JSONObject.NULL:reservationCode);trimmed.put(item);sp.edit().putString(LOG_KEY,trimmed.toString()).apply();}catch(Exception ignored){}}
    }
    public static int queuedLogCount(){if(APP==null)return 0;try{SharedPreferences sp=APP.getSharedPreferences(LOG_PREF,Context.MODE_PRIVATE);return new JSONArray(sp.getString(LOG_KEY,"[]")).length();}catch(Exception e){return 0;}}

    public static void flushQueuedLogs(){
        if(APP==null||flushingLogs)return;synchronized(LOG_LOCK){if(flushingLogs)return;flushingLogs=true;}try{while(true){JSONObject item;SharedPreferences sp=APP.getSharedPreferences(LOG_PREF,Context.MODE_PRIVATE);synchronized(LOG_LOCK){JSONArray q;try{q=new JSONArray(sp.getString(LOG_KEY,"[]"));}catch(Exception e){q=new JSONArray();}if(q.length()==0)break;item=q.getJSONObject(0);}JSONObject d=item.optJSONObject("details");if(d==null)d=new JSONObject();d.put("event_id",item.optString("event_id",""));d.put("occurred_at",item.optString("occurred_at",""));JSONObject b=new JSONObject();b.put("p_app","conductor");b.put("p_level",item.optString("level","info"));b.put("p_event",item.optString("event","event"));b.put("p_message",item.has("message")?item.opt("message"):JSONObject.NULL);b.put("p_details",d);b.put("p_app_version","11.4-R10.4");b.put("p_device_info",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+" / Android "+android.os.Build.VERSION.RELEASE);b.put("p_reservation_code",item.has("reservation_code")?item.opt("reservation_code"):JSONObject.NULL);if(!sendLogDirect(b))break;synchronized(LOG_LOCK){JSONArray q;try{q=new JSONArray(sp.getString(LOG_KEY,"[]"));}catch(Exception e){q=new JSONArray();}JSONArray rest=new JSONArray();for(int i=1;i<q.length();i++)rest.put(q.get(i));sp.edit().putString(LOG_KEY,rest.toString()).apply();}}}catch(Exception ignored){}finally{flushingLogs=false;}
    }
    private static boolean sendLogDirect(JSONObject body){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(BASE+"/rest/v1/rpc/log_app_event").openConnection();c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");c.setRequestProperty("apikey",KEY);try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream is=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();readAll(is);return code>=200&&code<300;}catch(Exception ignored){return false;}finally{if(c!=null)c.disconnect();}}

    private static String readAll(InputStream is) throws Exception {
        if(is==null)return "";
        StringBuilder sb=new StringBuilder();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){
            String l;while((l=br.readLine())!=null)sb.append(l);
        }
        return sb.toString();
    }

    public static JSONObject routeEstimate(double originLat,double originLng,double destinationLat,double destinationLng) throws Exception {
        String u="https://router.project-osrm.org/route/v1/driving/"+originLng+","+originLat+";"+destinationLng+","+destinationLat+"?overview=false&alternatives=false&steps=false";
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(4500);c.setReadTimeout(6500);c.setRequestMethod("GET");
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","TrasladosConductor/11.4 (Uruguay)");
        int code=c.getResponseCode();String text=readAll((code>=200&&code<300)?c.getInputStream():c.getErrorStream());
        if(code<200||code>=300)throw new IOException("Ruta HTTP "+code);
        JSONObject j=new JSONObject(text);JSONArray routes=j.optJSONArray("routes");if(routes==null||routes.length()==0)throw new IOException("Sin ruta");
        JSONObject r=routes.getJSONObject(0),out=new JSONObject();out.put("distance_km",r.optDouble("distance",0)/1000.0);out.put("duration_min",Math.max(1,(int)Math.ceil(r.optDouble("duration",0)/60.0)));out.put("method","ROAD");return out;
    }

    public static JSONObject checkRouteFitV114(String pin,String reservationId) throws Exception {JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_reservation_id",reservationId);return new JSONObject(postRpc("driver_check_route_fit_v11_4",b));}
    public static JSONObject getAvailabilitySettings(String pin) throws Exception {JSONObject b=new JSONObject();b.put("p_pin",pin);return new JSONObject(postRpc("driver_get_availability_settings_v11_3",b));}
    public static JSONObject setAvailabilitySettings(JSONObject b) throws Exception {return new JSONObject(postRpc("driver_set_availability_settings_v11_3",b));}
    public static org.json.JSONArray listScheduleBlocks(String pin,String from,String to) throws Exception {JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_from",from);b.put("p_to",to);return new org.json.JSONArray(postRpc("driver_list_schedule_blocks_v11_3",b));}
    public static JSONObject addScheduleBlock(String pin,String date,String start,String end,String note) throws Exception {JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_date",date);b.put("p_start",start);b.put("p_end",end);b.put("p_note",note==null?"":note);return new JSONObject(postRpc("driver_add_schedule_block_v11_3",b));}
    public static JSONObject deleteScheduleBlock(String pin,String id) throws Exception {JSONObject b=new JSONObject();b.put("p_pin",pin);b.put("p_id",id);return new JSONObject(postRpc("driver_delete_schedule_block_v11_3",b));}
}
