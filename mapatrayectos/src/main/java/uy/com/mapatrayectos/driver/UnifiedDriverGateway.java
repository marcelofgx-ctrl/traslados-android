package uy.com.mapatrayectos.driver;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import uy.com.mapatrayectos.Api;

/**
 * Fresh gateway for the Mapa-owned Conductor page.
 *
 * The UI never asks for a PIN and never stores credentials itself. For the
 * transition phase, calls use the existing authenticated backend contract
 * from the one-time Mapa pairing. No new public/anonymous RPC is introduced.
 * The PIN-to-device-token backend migration is a separate audited change.
 */
public final class UnifiedDriverGateway {
    private final Context app;
    public UnifiedDriverGateway(Context context){app=context.getApplicationContext();}
    public boolean linked(){
        return !app.getSharedPreferences("driver_session",Context.MODE_PRIVATE)
            .getString("pin","").isEmpty();
    }
    private String access(){
        SharedPreferences p=app.getSharedPreferences("driver_session",Context.MODE_PRIVATE);
        String pin=p.getString("pin","");
        if(pin==null||pin.isEmpty())throw new IllegalStateException(
            "Vinculá este dispositivo desde Mapa → Conductor y recogidas");
        return pin;
    }
    private String post(String endpoint,JSONObject body)throws Exception{
        if(!endpoint.matches("driver_[a-z0-9_]+"))throw new IllegalArgumentException("RPC no permitido");
        HttpURLConnection c=(HttpURLConnection)new URL(Api.BASE+"/rest/v1/rpc/"+endpoint).openConnection();
        c.setConnectTimeout(9000);c.setReadTimeout(15000);
        c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("apikey",Api.KEY);
        c.setRequestProperty("Content-Type","application/json");
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("X-Client-Info","mapa-integrated-driver-new/1");
        try{
            try(OutputStream os=c.getOutputStream()){
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code=c.getResponseCode();
            InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            StringBuilder result=new StringBuilder();
            if(stream!=null)try(BufferedReader br=new BufferedReader(
                new InputStreamReader(stream,StandardCharsets.UTF_8))){
                String line;while((line=br.readLine())!=null)result.append(line);
            }
            if(code<200||code>=300){
                // Keep credentials/server internals out of on-screen failures.
                if(code==401||code==403)throw new IllegalStateException(
                    "La vinculación del conductor no está autorizada");
                throw new IllegalStateException("No se pudo completar la operación ("+code+")");
            }
            return result.toString();
        }finally{c.disconnect();}
    }
    private JSONObject auth()throws Exception{return new JSONObject().put("p_pin",access());}
    private JSONObject withId(String id)throws Exception{
        return auth().put("p_reservation_id",id);
    }
    public JSONArray requests()throws Exception{return new JSONArray(
        post("driver_list_reservations_v3",auth()));}
    public JSONArray history()throws Exception{return new JSONArray(
        post("driver_history_v2",auth()));}
    public JSONObject changeStatus(String id,String next)throws Exception{
        if(!UnifiedDriverPolicy.allowedStatus(next))throw new IllegalArgumentException("Estado desconocido");
        return new JSONObject(post("driver_set_status_v2",
            withId(id).put("p_status",next)));
    }
    public JSONObject changePickupPhase(String id,String phase)throws Exception{
        if(!UnifiedDriverPolicy.allowedPickupPhase(phase))
            throw new IllegalArgumentException("Etapa de recogida inválida");
        return new JSONObject(post("driver_set_pickup_phase_v1",
            withId(id).put("p_phase",phase)));
    }
    public JSONArray messages(String id)throws Exception{
        return new JSONArray(post("driver_trip_chat_v1",
            withId(id).put("p_after_id",0)));
    }
    public JSONObject sendMessage(String id,String message)throws Exception{
        if(message==null||message.trim().isEmpty()||message.length()>500)
            throw new IllegalArgumentException("Mensaje vacío o demasiado largo");
        return new JSONObject(post("driver_trip_chat_send_v1",
            withId(id).put("p_body",message.trim())
                .put("p_request_id",UUID.randomUUID().toString())));
    }
}
