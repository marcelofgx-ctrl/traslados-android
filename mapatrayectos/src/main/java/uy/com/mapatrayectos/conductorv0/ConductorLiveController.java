package uy.com.mapatrayectos.conductorv0;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import uy.com.mapatrayectos.DriverIdentity;
import uy.com.mapatrayectos.TransferAlerts;

/**
 * Uses the existing authenticated Conductor RPC pipeline, never raw table access.
 * The encrypted device credential is read without displaying a PIN screen.
 * UI decisions only sound successful after positive server confirmation.
 */
public final class ConductorLiveController implements ConductorLiveView.Listener {
    private final Activity activity;
    private final ConductorLiveView view;
    private final ConductorTripActions trips;
    private final ConductorQuoteDialog quotes;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> pending=new ArrayList<>();
    private final HashSet<String> locallyConfirmed=new HashSet<>();
    private boolean open=false,hostResumed=false,receiverRegistered=false,busy=false,historyLoading=false;
    private String selection="",lastKey="";
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!open||!hostResumed)return;
            renderFromCache(false);
            main.postDelayed(this,3500L);
        }
    };
    private final BroadcastReceiver updates=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(open && i!=null)renderFromCache(false);
        }
    };
    public ConductorLiveController(Activity a,ConductorLiveView v){
        activity=a;view=v;view.setListener(this);
        trips=new ConductorTripActions(a,new ConductorTripActions.Callback(){
            @Override public void working(boolean active){
                busy=active;
                view.setBusy(active);
            }
            @Override public void finished(boolean success,String message){
                view.setFeedback(message,success);
                if(success) {
                    playTone(true);
                    TransferAlerts.refreshAsync(activity);
                    loadHistory();
                }
            }
        });
        quotes=new ConductorQuoteDialog(a,(success,message)->{
            if(message!=null&&!message.isEmpty())view.setFeedback(message,success);
            if(success){playTone(true);TransferAlerts.refreshAsync(activity);}
        });
    }
    public void open(boolean shiftActive){
        open=true;
        view.setShiftActive(shiftActive);
        renderFromCache(true);
        if(!DriverIdentity.pin(activity).isEmpty()){
            TransferAlerts.ensureMonitor(activity);
            TransferAlerts.refreshAsync(activity);
            loadHistory();
        }
        if(hostResumed)startWatching();
    }
    public void close(){
        open=false;stopWatching();
    }
    public void onHostResume(){
        hostResumed=true;
        if(open){startWatching();renderFromCache(true);}
    }
    public void onHostPause(){hostResumed=false;stopWatching();}
    public void dispose(){close();main.removeCallbacksAndMessages(null);}

    private void startWatching(){
        if(!receiverRegistered){
            IntentFilter filter=new IntentFilter();
            filter.addAction(TransferAlerts.ACTION_NEW);
            filter.addAction(TransferAlerts.ACTION_COUNT);
            if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(updates,filter,Context.RECEIVER_NOT_EXPORTED);
            else activity.registerReceiver(updates,filter);
            receiverRegistered=true;
        }
        main.removeCallbacks(tick);
        main.post(tick);
    }
    private void stopWatching(){
        main.removeCallbacks(tick);
        if(receiverRegistered){
            try{activity.unregisterReceiver(updates);}catch(Exception ignored){}
            receiverRegistered=false;
        }
    }
    private void renderFromCache(boolean force){
        if(!open)return;
        String pin=DriverIdentity.pin(activity);
        if(pin.isEmpty()){
            view.setConnection("Conductor sin vincular. Usá Vincular conductor en el menú de Mapa.",false);
            view.setCounters(null,null,null);
            view.setPendingRequest(null,0,0);
            view.setActiveTrips(new ArrayList<>());
            view.setScheduledTrips(new ArrayList<>());
            view.setHistory(new ArrayList<>());
            lastKey="";
            return;
        }
        long synced=TransferAlerts.lastSync(activity), now=System.currentTimeMillis();
        String error=TransferAlerts.syncError(activity);
        boolean fresh=synced>0 && now>=synced && now-synced<=45000L;
        if(!fresh){
            view.setConnection(synced<=0?"Conectando con solicitudes reales…":
                "Sin actualización reciente · revisá la conexión"+(error.isEmpty()?"":" ("+error+")"),false);
            view.setCounters(null,null,null);
            view.setPendingRequest(null,0,0);
            view.setActiveTrips(new ArrayList<>());
            view.setScheduledTrips(new ArrayList<>());
            lastKey="";
            return;
        }
        JSONArray snapshot=TransferAlerts.snapshot(activity);
        String key=snapshot.toString()+":"+synced+":"+locallyConfirmed.toString();
        if(!force && key.equals(lastKey))return;
        lastKey=key;
        view.setConnection("Solicitudes reales · sincronizado hace "+Math.max(0,(now-synced)/1000)+" s",true);
        ArrayList<JSONObject> nextPending=new ArrayList<>();
        ArrayList<ConductorLiveView.Trip> accepted=new ArrayList<>(),scheduled=new ArrayList<>();
        int activeCount=0,inJourney=0;
        for(int i=0;i<snapshot.length();i++){
            JSONObject r=snapshot.optJSONObject(i);if(r==null)continue;
            String id=r.optString("id",""),status=r.optString("status","");
            if(!ConductorLivePolicy.safeId(id))continue;
            if(ConductorLivePolicy.pending(status)){
                if(locallyConfirmed.contains(id))continue; // avoid a stale optimistic reclick after confirmed RPC
                boolean fixed=ConductorLivePolicy.actionableFixed(status,
                    r.optString("fare_mode"),r.optString("quote_status"),
                    r.optDouble("quote_final_total",0),r.optDouble("route_distance_km",0),
                    r.optString("created_at"),now);
                if(fixed)nextPending.add(r);
                else scheduled.add(trip(r));
            }else if(ConductorLivePolicy.accepted(status)){
                accepted.add(trip(r));activeCount++;
            }else if(ConductorLivePolicy.inJourney(status)){
                accepted.add(trip(r));inJourney++;
            }
        }
        // A new server state clears locally-confirmed suppression once it no longer appears pending.
        HashSet<String> stillPending=new HashSet<>();
        for(int i=0;i<snapshot.length();i++){
            JSONObject r=snapshot.optJSONObject(i);
            if(r!=null&&ConductorLivePolicy.pending(r.optString("status")))stillPending.add(r.optString("id"));
        }
        locallyConfirmed.retainAll(stillPending);
        pending.clear();pending.addAll(nextPending);
        if(!pending.isEmpty()){
            boolean found=false;
            for(JSONObject r:pending)if(selection.equals(r.optString("id")))found=true;
            if(!found)selection=pending.get(0).optString("id");
            int index=0;
            for(int i=0;i<pending.size();i++)
                if(selection.equals(pending.get(i).optString("id")))index=i;
            view.setPendingRequest(request(pending.get(index)),index,pending.size());
        }else{selection="";view.setPendingRequest(null,0,0);}
        view.setCounters(pending.size(),activeCount,inJourney);
        view.setActiveTrips(accepted);
        view.setScheduledTrips(scheduled);
    }
    private static ConductorLiveView.Request request(JSONObject r){
        double km=r.optDouble("route_distance_km",0);
        int mins=r.optInt("route_duration_min",0);
        double amount=r.optDouble("quote_final_total",0);
        return new ConductorLiveView.Request(
            r.optString("id"),r.optString("code"),r.optString("origin_text"),
            r.optString("destination_text"),
            km>0?String.format(Locale.US,"%.1f km",km):"",
            mins>0?mins+" min":"",
            amount>0?String.format(Locale.US,"$ %.0f UYU",amount):"",
            r.optString("customer_name"));
    }
    private static ConductorLiveView.Trip trip(JSONObject r){
        double amount=r.optDouble("quote_final_total",0);
        return new ConductorLiveView.Trip(r.optString("id"),r.optString("code"),
            r.optString("customer_name"),r.optString("customer_phone"),
            r.optString("origin_text"),r.optString("destination_text"),
            r.optString("status"),r.optString("pickup_phase"),
            r.optString("pickup_date")+" "+r.optString("pickup_time"),
            amount>0?String.format(Locale.US,"$ %.0f UYU",amount):"",
            r.optString("pickup_mode"),r.optString("fare_mode"),
            r.optString("quote_status"),r.optDouble("route_distance_km",0),
            r.optInt("route_duration_min",0));
    }
    private void loadHistory(){
        if(historyLoading||!open)return;
        String credential=DriverIdentity.pin(activity);
        if(credential.isEmpty())return;
        historyLoading=true;
        new Thread(()->{
            List<String> rows=new ArrayList<>();
            boolean succeeded=false;
            try{
                JSONArray result=uy.com.traslados.conductor.Api.listHistory(credential);
                for(int i=0;i<Math.min(result.length(),200);i++){
                    JSONObject r=result.optJSONObject(i);
                    if(r==null)continue;
                    String code=r.optString("code"),status=r.optString("status");
                    String day=r.optString("pickup_date"),origin=r.optString("origin_text");
                    String destination=r.optString("destination_text");
                    rows.add(code+" · "+status+" · "+day+"\n"+origin+" → "+destination);
                }
                succeeded=true;
            }catch(Exception error){Log.w("QuickTripConductor","History read failed: "+error.getClass().getSimpleName());}
            final boolean ok=succeeded;
            main.post(()->{
                historyLoading=false;
                if(open && credential.equals(DriverIdentity.pin(activity))){
                    if(ok)view.setHistory(rows);
                    else view.setFeedback("No se pudo actualizar el histórico; verificá Internet.",false);
                }
            });
        },"quicktrip-live-history").start();
    }
    @Override public void onAccept(String id){decide(id,true);}
    @Override public void onReject(String id){decide(id,false);}
    private void decide(String id,boolean accepted){
        if(busy||!ConductorLivePolicy.safeId(id)||DriverIdentity.pin(activity).isEmpty())return;
        boolean present=false;
        for(JSONObject r:pending)if(id.equals(r.optString("id")))present=true;
        if(!present)return;
        busy=true;view.setBusy(true);
        view.setFeedback(accepted?"Confirmando aceptación con servidor…":"Confirmando rechazo con servidor…",false);
        TransferAlerts.decideFixed(activity,id,accepted,(confirmed,message)->{
            busy=false;view.setBusy(false);
            view.setFeedback(message,confirmed);
            if(confirmed){
                locallyConfirmed.add(id);
                playTone(accepted);
                renderFromCache(true);
                loadHistory();
                TransferAlerts.refreshAsync(activity);
            }else{TransferAlerts.refreshAsync(activity);}
        });
    }
    @Override public void onPrevious(){shift(-1);}
    @Override public void onNext(){shift(1);}
    private void shift(int delta){
        if(pending.isEmpty()||busy)return;
        int i=0;for(int n=0;n<pending.size();n++)if(selection.equals(pending.get(n).optString("id")))i=n;
        i=(i+delta+pending.size())%pending.size();
        selection=pending.get(i).optString("id");
        view.setPendingRequest(request(pending.get(i)),i,pending.size());
    }
    @Override public void onRefresh(){
        if(DriverIdentity.pin(activity).isEmpty()){
            view.setFeedback("Primero vinculá este teléfono desde el menú de Mapa.",false);return;
        }
        view.setFeedback("Actualizando solicitudes…",false);
        TransferAlerts.refreshAsync(activity);
        loadHistory();
    }
    @Override public void onPickupPhase(String id,String phase){
        if(busy||!ConductorLivePolicy.safeId(id)||(!"EN_CAMINO".equals(phase)&&!"EN_PUNTO".equals(phase)))return;
        String credential=DriverIdentity.pin(activity);
        if(credential.isEmpty())return;
        JSONObject found=null;
        JSONArray rows=TransferAlerts.snapshot(activity);
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r!=null&&id.equals(r.optString("id"))&&"ACEPTADA".equals(r.optString("status")))found=r;
        }
        if(found==null){view.setFeedback("Ya no está disponible la reserva para actualizar.",false);return;}
        busy=true;view.setBusy(true);view.setFeedback("Comunicando estado al pasajero…",false);
        new Thread(()->{
            boolean success=false;
            try{
                JSONObject result=uy.com.traslados.conductor.Api.setPickupPhase(credential,id,phase);
                success=phase.equals(result.optString("pickup_phase"));
            }catch(Exception e){Log.w("QuickTripConductor","Pickup state error: "+e.getClass().getSimpleName());}
            final boolean done=success;
            main.post(()->{
                busy=false;view.setBusy(false);
                view.setFeedback(done?("EN_CAMINO".equals(phase)?"En camino confirmado":"Llegada confirmada"):
                    "No se confirmó el cambio; verificá conexión o estado.",done);
                if(done){playTone(true);TransferAlerts.refreshAsync(activity);}
            });
        },"quicktrip-live-pickup").start();
    }
    @Override public void onPrepareQuote(String id){
        if(busy)return;
        quotes.show(id);
    }
    @Override public void onStartTrip(String id){
        if(busy)return;
        trips.request(id,false);
    }
    @Override public void onFinishTrip(String id){
        if(busy)return;
        trips.request(id,true);
    }
    @Override public void onCall(String phone){
        if(phone==null||phone.isEmpty())return;
        try{activity.startActivity(new Intent(Intent.ACTION_DIAL,
            Uri.parse("tel:"+phone.replaceAll("[^0-9+]",""))));}
        catch(Exception e){view.setFeedback("No se pudo abrir la llamada.",false);}
    }
    @Override public void onWhatsApp(String phone){
        if(phone==null||phone.isEmpty())return;
        String digits=phone.replaceAll("[^0-9]","");
        if(digits.startsWith("0"))digits="598"+digits.substring(1);
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+digits)));}
        catch(Exception e){view.setFeedback("No se pudo abrir WhatsApp.",false);}
    }
    @Override public void onRoute(String destination){
        if(destination==null||destination.trim().isEmpty())return;
        try{
            String url="https://www.google.com/maps/dir/?api=1&destination="+
                Uri.encode(destination)+"&travelmode=driving";
            activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        }catch(Exception e){view.setFeedback("No se pudo abrir la ruta.",false);}
    }
    private void playTone(boolean accepted){
        try{
            AudioManager audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
            if(audio==null||audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL
                ||audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)==0)return;
            ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,60);
            if(!tone.startTone(accepted?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_NACK,200)){
                tone.release();return;
            }
            main.postDelayed(tone::release,310L);
        }catch(RuntimeException ignored){}
    }
}
