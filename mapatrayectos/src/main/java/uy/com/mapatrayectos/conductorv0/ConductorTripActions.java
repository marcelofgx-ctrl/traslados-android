package uy.com.mapatrayectos.conductorv0;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import uy.com.mapatrayectos.DriverIdentity;
import uy.com.mapatrayectos.TransferAlerts;
import uy.com.traslados.conductor.Api;
import uy.com.traslados.conductor.TripTelemetryService;

/**
 * Reuses the installed driver telemetry service and existing authenticated RPC.
 * No TripTelemetryService, TrackingService, GPS store or reservation engine changes.
 */
public final class ConductorTripActions {
    public interface Callback {
        void working(boolean active);
        void finished(boolean success,String message);
    }
    private final Activity activity;
    private final Callback callback;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean working=false;
    public ConductorTripActions(Activity activity,Callback callback){
        this.activity=activity;this.callback=callback;
    }

    public void request(String id,boolean finish){
        if(working||!ConductorLivePolicy.safeId(id))return;
        String status=finish?"EN_VIAJE":"ACEPTADA";
        if(!snapshotContains(id,status)){
            callback.finished(false,"El estado del viaje cambió. Actualizá las solicitudes.");
            TransferAlerts.refreshAsync(activity);
            return;
        }
        if(!finish && !gpsReady())return;
        new AlertDialog.Builder(activity)
            .setTitle(finish?"Finalizar viaje":"Iniciar viaje")
            .setMessage(finish?
                "¿Terminaste el traslado? Se confirmará el cierre con el servidor y se detendrá el registro GPS de este viaje.":
                "¿El pasajero ya subió y vas a iniciar el recorrido? Se activará el GPS del viaje y se confirmará con el servidor.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton(finish?"Finalizar":"Iniciar",(d,w)->execute(id,finish))
            .show();
    }

    private boolean snapshotContains(String id,String status){
        long synced=TransferAlerts.lastSync(activity);
        long now=System.currentTimeMillis();
        if(synced<=0||now<synced||now-synced>45000L)return false;
        JSONArray a=TransferAlerts.snapshot(activity);
        for(int i=0;i<a.length();i++){
            JSONObject r=a.optJSONObject(i);
            if(r!=null&&id.equals(r.optString("id"))&&status.equals(r.optString("status")))return true;
        }
        return false;
    }
    private boolean gpsReady(){
        if(Build.VERSION.SDK_INT>=23 && activity.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            new AlertDialog.Builder(activity).setTitle("Ubicación precisa necesaria")
                .setMessage("Para iniciar un viaje necesitás habilitar la ubicación precisa. No se iniciará sin GPS.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Autorizar",(d,w)->{
                    activity.requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION},4412);
                }).show();
            return false;
        }
        try{
            LocationManager lm=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
            boolean enabled=lm!=null&&(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ||lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            activity.getPackageManager().getServiceInfo(
                new ComponentName(activity,TripTelemetryService.class),0);
            if(enabled)return true;
        }catch(Exception ignored){}
        new AlertDialog.Builder(activity).setTitle("GPS no disponible")
            .setMessage("Activá la ubicación y volvé a iniciar. No se enviará el cambio de estado.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Configurar GPS",(d,w)->{
                try{activity.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}
                catch(Exception ignored){}
            }).show();
        return false;
    }
    private void execute(String id,boolean finish){
        if(working)return;
        String credential=DriverIdentity.pin(activity);
        if(credential.isEmpty()){
            callback.finished(false,"El conductor ya no está vinculado.");return;
        }
        working=true;callback.working(true);
        new Thread(()->{
            JSONObject live=null;
            String error="";
            try{
                JSONArray current=Api.listActive(credential);
                for(int i=0;i<current.length();i++){
                    JSONObject candidate=current.optJSONObject(i);
                    if(candidate!=null&&id.equals(candidate.optString("id"))){
                        live=candidate;break;
                    }
                }
                if(live==null || (finish?!ConductorOperationsPolicy.canFinish(live.optString("status")):
                                     !ConductorOperationsPolicy.canStart(live.optString("status"))))
                    throw new IllegalStateException("El estado del viaje cambió en el servidor.");
            }catch(Exception ex){
                error=ex instanceof IllegalStateException?ex.getMessage():Api.publicMessage("trip",ex);
            }
            final JSONObject fresh=live;
            final String priorError=error;
            main.post(()->{
                if(activity.isFinishing()||activity.isDestroyed()){release();return;}
                if(!priorError.isEmpty()){
                    release();
                    callback.finished(false,priorError);
                    TransferAlerts.refreshAsync(activity);
                    return;
                }
                if(!finish&&!gpsReady()){
                    release();callback.finished(false,"No se inició: revisá la ubicación.");return;
                }
                // Original driver starts GPS BEFORE the status RPC and aborts on failure.
                if(!finish && !startGps(id,fresh.optString("code"))){
                    release();callback.finished(false,"Android no pudo iniciar GPS. El estado del servidor no cambió.");return;
                }
                sendStatus(id,credential,finish);
            });
        },"quicktrip-trip-preflight").start();
    }
    private void sendStatus(String id,String credential,boolean finish){
        new Thread(()->{
            String target=finish?"FINALIZADA":"EN_VIAJE";
            String previous=finish?"EN_VIAJE":"ACEPTADA";
            int outcome=0; // +1 server confirmed, -1 server confirmed unchanged, 0 uncertain
            String message="";
            try{
                if(!credential.equals(DriverIdentity.pin(activity))){
                    outcome=-1;
                    throw new IllegalStateException("La vinculación cambió durante la operación.");
                }
                JSONObject result=Api.setStatus(credential,id,target);
                if(id.equals(result.optString("id"))&&target.equals(result.optString("status")))
                    outcome=1;
                else message="El servidor no devolvió una confirmación inequívoca.";
            }catch(Exception ex){
                message=ex instanceof IllegalStateException?ex.getMessage():Api.publicMessage("trip",ex);
            }
            // A connection timeout may happen AFTER the server commits.
            // Read back the authoritative status before considering a GPS abort.
            if(outcome==0){
                try{
                    JSONArray active=Api.listActive(credential);
                    for(int i=0;i<active.length();i++){
                        JSONObject r=active.optJSONObject(i);
                        if(r==null||!id.equals(r.optString("id")))continue;
                        String status=r.optString("status");
                        if(target.equals(status))outcome=1;
                        else if(previous.equals(status))outcome=-1;
                        break;
                    }
                    if(finish&&outcome==0){
                        JSONArray historical=Api.listHistory(credential);
                        for(int i=0;i<historical.length();i++){
                            JSONObject r=historical.optJSONObject(i);
                            if(r!=null&&id.equals(r.optString("id"))&&target.equals(r.optString("status"))){
                                outcome=1;break;
                            }
                        }
                    }
                }catch(Exception ignored){ /* Unknown: preserve GPS trace. */ }
            }
            final int resultState=outcome;
            final String resultMessage=outcome==1?
                (finish?"Viaje finalizado y confirmado.":"Viaje iniciado y confirmado."):
                outcome==0?
                "Conexión interrumpida: no sabemos si el servidor confirmó. Se conserva el GPS. Actualizá la reserva antes de reintentar.":
                (message.isEmpty()?"No se pudo cambiar el estado del viaje.":message);
            main.post(()->{
                if(resultState==-1&&!finish)stopGps(id,TripTelemetryService.ACTION_ABORT);
                if(resultState==1&&finish)stopGps(id,TripTelemetryService.ACTION_STOP);
                release();
                if(!activity.isFinishing()&&!activity.isDestroyed()){
                    callback.finished(resultState==1,resultMessage);
                    if(resultState==1&&!finish)verifyGps(id,0);
                }
                TransferAlerts.refreshAsync(activity);
            });
        },"quicktrip-trip-status").start();
    }
    private boolean startGps(String id,String code){
        try{
            TripTelemetryService.prepareStart(activity,id,code);
            Intent intent=new Intent(activity,TripTelemetryService.class);
            intent.setAction(TripTelemetryService.ACTION_START);
            intent.putExtra("reservation_id",id);
            intent.putExtra("code",code);
            if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(intent);
            else activity.startService(intent);
            return true;
        }catch(Exception error){
            TripTelemetryService.markLaunchError(activity,id,"No se pudo iniciar el servicio GPS");
            stopGps(id,TripTelemetryService.ACTION_ABORT);
            return false;
        }
    }
    private void stopGps(String id,String action){
        try{
            Intent intent=new Intent(activity,TripTelemetryService.class);
            intent.setAction(action);intent.putExtra("reservation_id",id);
            activity.startService(intent);
        }catch(Exception ignored){
            if(TripTelemetryService.ACTION_STOP.equals(action))
                callback.finished(false,"El viaje finalizó, pero Android no pudo detener el GPS.");
        }
    }
    private void verifyGps(String id,int attempt){
        main.postDelayed(()->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            JSONObject summary=TripTelemetryService.summary(activity,id);
            if(TripTelemetryService.STATE_ACTIVE.equals(summary.optString("state")) &&
                summary.optBoolean("active",false))return;
            if(attempt<2 && TripTelemetryService.STATE_STARTING.equals(summary.optString("state"))){
                verifyGps(id,attempt+1);return;
            }
            new AlertDialog.Builder(activity)
                .setTitle("Viaje iniciado · comprobar GPS")
                .setMessage("El servidor confirmó el viaje, pero Android todavía no confirmó el registro GPS. Revisá la ubicación. No se borrarán puntos existentes.")
                .setNegativeButton("Cerrar",null)
                .setPositiveButton("Reintentar GPS",(d,w)->{
                    if(gpsReady())startGps(id,"");
                }).show();
        },1300L);
    }
    private void release(){working=false;callback.working(false);}
}
