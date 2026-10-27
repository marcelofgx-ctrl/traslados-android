package uy.com.mapatrayectos.qa;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** QA-only driver console. Does not reference any original Mapa/Conductor operational activity. */
public final class LiveQaActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText endpointField,requestField,tokenField;
    private TextView status;
    private String verifiedEndpoint="",verifiedToken="",verifiedId="";
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30,28,30,28);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);
        setContentView(scroll);
        TextView title=new TextView(this);title.setText("Mapa Testing");title.setTextSize(25);
        root.addView(title);
        TextView warning=new TextView(this);
        warning.setText("Aplicación de pruebas de Quick Trip. No modifica Mapa QT estable ni realiza reservas comerciales. La conexión GPS requiere un servidor de pruebas configurado.");
        warning.setPadding(0,12,0,16);root.addView(warning);
        endpointField=field(root,"URL Worker QA HTTPS",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        requestField=field(root,"UUID solicitud de prueba",InputType.TYPE_CLASS_TEXT);
        tokenField=field(root,"Token CONDUCTOR QA (solo memoria)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        status=new TextView(this);status.setText("Sin conexión. Nada se envía.");
        status.setPadding(0,16,0,20);root.addView(status);
        action(root,"1 · Consultar solicitud",()->perform("check",null));
        action(root,"2 · Aceptar solicitud QA",()->perform("transition","ACEPTADA"));
        action(root,"3 · En camino a recogida",()->perform("transition","EN_CAMINO"));
        action(root,"4 · En punto de recogida",()->perform("transition","EN_PUNTO"));
        action(root,"5 · En viaje",()->perform("transition","EN_VIAJE"));
        action(root,"6 · Activar compartir GPS",this::startLive);
        action(root,"7 · Finalizar viaje QA",()->perform("transition","FINALIZADA"));
        action(root,"Detener y revocar GPS",this::stopLive);
    }
    private EditText field(LinearLayout box,String hint,int type){
        EditText edit=new EditText(this);edit.setHint(hint);edit.setSingleLine(true);
        edit.setInputType(type);box.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;
    }
    private void action(LinearLayout root,String name,Runnable action){
        Button b=new Button(this);b.setText(name);root.addView(b,new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v->action.run());
    }
    private void message(String text){runOnUiThread(()->status.setText(text));}
    private boolean read(){
        try {
            verifiedEndpoint=LiveQaNet.base(endpointField.getText().toString());
            verifiedId=LiveQaNet.id(requestField.getText().toString());
            verifiedToken=LiveQaNet.token(tokenField.getText().toString());
            return true;
        }catch(Exception e){message(e.getMessage());return false;}
    }
    private JSONObject readTrip() throws Exception {
        JSONObject result=LiveQaNet.call(verifiedEndpoint,verifiedToken,"GET",
            "/v1/requests/"+verifiedId,null);
        return result.getJSONObject("request");
    }
    private void perform(String mode,String desired){
        if(!read())return;
        message("Consultando exclusivamente Worker QA...");
        worker.execute(()->{
            try {
                JSONObject trip=readTrip();
                String state=trip.getString("state");
                int version=trip.getInt("version");
                if(desired==null){
                    message("Solicitud QA: "+state+" · versión "+version+"\nListo para avanzar.");return;
                }
                JSONObject body=new JSONObject().put("to",desired).put("expectedVersion",version);
                JSONObject result=LiveQaNet.call(verifiedEndpoint,verifiedToken,"POST",
                    "/v1/requests/"+verifiedId+"/transition",body);
                String next=result.getJSONObject("request").getString("state");
                if("FINALIZADA".equals(next))stopService(new Intent(this,LiveQaTrackingService.class));
                message("Estado QA actualizado: "+next);
            }catch(Exception e){message("Sin transición: "+e.getMessage());}
        });
    }
    private void startLive(){
        if(!read())return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION},41);
            message("Concedé ubicación PRECISA y pulsá nuevamente Activar.");return;
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);
        }
        message("Verificando estado y consentimiento...");
        worker.execute(()->{
            try{
                JSONObject trip=readTrip();
                String state=trip.getString("state");
                if(!("ACEPTADA".equals(state)||"EN_CAMINO".equals(state)||
                     "EN_PUNTO".equals(state)||"EN_VIAJE".equals(state))){
                    message("Debe aceptar primero una solicitud QA.");return;
                }
                JSONObject consent=new JSONObject().put("enabled",true).put("expectedVersion",trip.getInt("version"));
                JSONObject result=LiveQaNet.call(verifiedEndpoint,verifiedToken,"POST",
                    "/v1/requests/"+verifiedId+"/tracking-consent",consent);
                if(!result.optBoolean("trackingEnabled",false))throw new Exception("Consentimiento no confirmado");
                runOnUiThread(()->{
                    Intent i=new Intent(this,LiveQaTrackingService.class);
                    i.putExtra("endpoint",verifiedEndpoint);
                    i.putExtra("requestId",verifiedId);
                    i.putExtra("driverToken",verifiedToken);
                    try{
                        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                        message("GPS QA activado. La notificación permanecerá visible.");
                    }catch(Exception e){message("Android no permitió iniciar GPS QA: "+e.getClass().getSimpleName());}
                });
            }catch(Exception e){message("GPS no activado: "+e.getMessage());}
        });
    }
    private void stopLive(){
        stopService(new Intent(this,LiveQaTrackingService.class));
        if(!read()){message("GPS local detenido; verificar revocación de servidor cuando vuelva la conexión.");return;}
        message("GPS local detenido; revocando consentimiento en QA...");
        worker.execute(()->{
            try{
                JSONObject trip=readTrip();
                String state=trip.getString("state");
                if("CANCELADA".equals(state)||"FINALIZADA".equals(state)||"RECHAZADA".equals(state)){
                    message("GPS detenido: viaje QA cerrado.");return;
                }
                JSONObject consent=new JSONObject().put("enabled",false).put("expectedVersion",trip.getInt("version"));
                LiveQaNet.call(verifiedEndpoint,verifiedToken,"POST",
                    "/v1/requests/"+verifiedId+"/tracking-consent",consent);
                message("GPS QA detenido y acceso revocado.");
            }catch(Exception e){message("GPS local detenido; sin conexión para revocar. El GPS publicado caduca a los 45 s.");}
        });
    }
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();verifiedToken="";}
}
