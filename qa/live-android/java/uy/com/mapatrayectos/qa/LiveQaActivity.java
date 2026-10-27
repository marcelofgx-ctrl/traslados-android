package uy.com.mapatrayectos.qa;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
public final class LiveQaActivity extends Activity {
 private EditText url,id,token;private TextView info;
 private EditText field(LinearLayout l,String hint,int kind){
  EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setInputType(kind);l.addView(e);return e;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);
  body.setOrientation(1);body.setPadding(24,20,24,24);scroll.addView(body);
  TextView title=new TextView(this);title.setTextSize(21);
  title.setText("Quick Trip Live · SOLO QA\nGPS de Mapa QT hacia pasajero");body.addView(title);
  TextView help=new TextView(this);
  help.setText("La reserva debe estar ACEPTADA en el Worker QA. Ningún token se guarda.\n"+
    "El GPS solo se envía con jornada y viaje activos, y consentimiento de esta prueba.");
  body.addView(help);
  url=field(body,"https://quicktrip-live-qa-demo.cuenta.workers.dev",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  id=field(body,"UUID de la solicitud QA",InputType.TYPE_CLASS_TEXT);
  token=field(body,"Token conductor QA",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  info=new TextView(this);info.setText("Apagado por defecto");body.addView(info);
  Button on=new Button(this);on.setText("AUTORIZAR Y COMPARTIR GPS QA");body.addView(on);
  on.setOnClickListener(v->{
   try{
    String base=LiveQaPolicy.endpoint(url.getText().toString());
    String requestId=LiveQaPolicy.requestId(id.getText().toString());
    String secret=token.getText().toString();
    if(secret.length()<32||secret.length()>240)throw new IllegalArgumentException("Token QA inválido");
    Intent i=new Intent(this,LiveQaService.class).setAction(LiveQaService.START);
    i.putExtra("endpoint",base);i.putExtra("request",requestId);i.putExtra("token",secret);
    if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    info.setText("Activación solicitada. Verificá la notificación de seguimiento QA.");
   }catch(Exception e){info.setText("No activado: "+e.getMessage());}
  });
  Button off=new Button(this);off.setText("DETENER Y REVOCAR");body.addView(off);
  off.setOnClickListener(v->{
   startService(new Intent(this,LiveQaService.class).setAction(LiveQaService.STOP));
   info.setText("Se solicitó detener; el servidor deja caducar cualquier GPS restante en 45 segundos.");
  });
  setContentView(scroll);
 }
}
