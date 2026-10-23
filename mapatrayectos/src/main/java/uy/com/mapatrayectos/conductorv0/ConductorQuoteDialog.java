package uy.com.mapatrayectos.conductorv0;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import uy.com.mapatrayectos.DriverIdentity;
import uy.com.mapatrayectos.TransferAlerts;
import uy.com.traslados.conductor.Api;

/** Quote UI for scheduled trips. The existing RPC owns business validation. */
public final class ConductorQuoteDialog {
    public interface Completion { void finished(boolean success,String message); }
    private final Activity activity;
    private final Completion callback;
    private boolean opening=false,sending=false;
    public ConductorQuoteDialog(Activity activity,Completion callback){
        this.activity=activity;this.callback=callback;
    }
    public void show(String id){
        if(opening||sending||!ConductorLivePolicy.safeId(id))return;
        String credential=DriverIdentity.pin(activity);
        if(credential.isEmpty()){callback.finished(false,"Vinculá el teléfono como conductor.");return;}
        opening=true;
        new Thread(()->{
            JSONObject booking=null,fit=null,settings=null;
            String failure="";
            try {
                JSONArray active=Api.listActive(credential);
                for(int i=0;i<active.length();i++){
                    JSONObject r=active.optJSONObject(i);
                    if(r!=null&&id.equals(r.optString("id"))){booking=r;break;}
                }
                if(booking==null||!ConductorOperationsPolicy.canQuote(
                    booking.optString("status"),booking.optString("pickup_mode"),
                    booking.optString("fare_mode")))
                    throw new IllegalStateException("Esta solicitud programada ya no admite presupuesto.");
                fit=Api.checkRouteFitV114(credential,id);
                if(fit==null||!fit.optBoolean("available",false))
                    throw new IllegalStateException("Ese horario no está disponible. Revisá la agenda.");
                try{settings=Api.getQuoteSettings(credential);}catch(Exception ignored){}
            }catch(Exception e){
                failure=e instanceof IllegalStateException?e.getMessage():Api.publicMessage("quote",e);
            }
            JSONObject b=booking,cfg=settings;
            String error=failure;
            activity.runOnUiThread(()->{
                opening=false;
                if(activity.isFinishing()||activity.isDestroyed())return;
                if(!error.isEmpty()){callback.finished(false,error);return;}
                renderDialog(id,credential,b,cfg);
            });
        },"quicktrip-quote-check").start();
    }
    private void renderDialog(String id,String credential,JSONObject b,JSONObject cfg){
        final LinearLayout form=new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16),dp(12),dp(16),dp(16));
        form.setBackgroundColor(Color.rgb(29,32,32));
        TextView note=new TextView(activity);
        note.setText("El cliente recibe solo el precio final que confirmes. Los importes de referencia son internos.");
        note.setTextColor(Color.WHITE);note.setTextSize(13);form.addView(note,mb(12));
        double km=b.optDouble("route_distance_km",0),rate=b.optDouble("quote_price_per_km",40);
        if(!ConductorOperationsPolicy.goodPrice(rate))rate=40;
        final EditText kmF=number(form,"Distancia de la ruta (km)",km>0?String.format(Locale.US,"%.1f",km):"");
        final EditText rateF=number(form,"Tarifa interna por km (UYU)",String.format(Locale.US,"%.0f",rate));
        final EditText minF=number(form,"Mínimo (UYU)",value(b,"quote_minimum"));
        int count=b.optInt("quote_toll_count",0);
        final EditText tollCount=number(form,"Cantidad de peajes",count>0?String.valueOf(count):"0");
        tollCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText tollUnit=number(form,"Costo por peaje (UYU)",
            b.optDouble("quote_toll_unit_value",0)>0?value(b,"quote_toll_unit_value"):
            cfg!=null&&cfg.optDouble("default_toll_unit_value",0)>0?
                String.format(Locale.US,"%.0f",cfg.optDouble("default_toll_unit_value")):"0");
        final EditText waitingF=number(form,"Espera (UYU)",value(b,"quote_waiting"));
        final EditText pickupF=number(form,"Extra hasta recogida (UYU)",value(b,"quote_pickup_extra"));
        final EditText otherF=number(form,"Otros extras (UYU)",value(b,"quote_other"));
        final TextView reference=new TextView(activity);
        reference.setTextSize(14);reference.setTextColor(Color.rgb(210,191,132));
        form.addView(reference,mb(12));
        double initial=b.optDouble("quote_final_total",0);
        final EditText finalF=number(form,"PRECIO FINAL AL PASAJERO (UYU)",
            initial>0?String.format(Locale.US,"%.0f",initial):"");
        final EditText includes=new EditText(activity);
        includes.setSingleLine(false);includes.setMinLines(2);includes.setTextSize(14);
        includes.setTextColor(Color.WHITE);
        includes.setHintTextColor(Color.LTGRAY);
        includes.setHint("Descripción opcional para el pasajero");
        includes.setText(b.optString("quote_includes",""));
        form.addView(includes,mb(12));
        Runnable calculate=()->{
            double base=num(kmF)*num(rateF);
            double ref=Math.max(base,num(minF))+
                Math.max(0,Math.round(num(tollCount)))*num(tollUnit)+
                num(waitingF)+num(pickupF)+num(otherF);
            reference.setText("Referencia interna: "+
                (ConductorOperationsPolicy.goodPrice(ref)?
                  String.format(Locale.US,"%.0f UYU",ref):"completá los importes"));
        };
        for(EditText field:new EditText[]{kmF,rateF,minF,tollCount,tollUnit,waitingF,pickupF,otherF})
            field.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int st,int count,int after){}
                public void onTextChanged(CharSequence s,int st,int before,int count){}
                public void afterTextChanged(android.text.Editable e){calculate.run();}
            });
        TextView suggested=new TextView(activity);
        suggested.setText("USAR REFERENCIA COMO PRECIO FINAL");
        suggested.setTextSize(12);suggested.setTextColor(Color.rgb(160,222,193));
        suggested.setGravity(Gravity.CENTER);
        suggested.setPadding(dp(5),dp(13),dp(5),dp(13));
        suggested.setOnClickListener(v->{
            double total=Math.max(num(kmF)*num(rateF),num(minF))+
                Math.max(0,Math.round(num(tollCount)))*num(tollUnit)+
                num(waitingF)+num(pickupF)+num(otherF);
            if(ConductorOperationsPolicy.goodPrice(total))
                finalF.setText(String.format(Locale.US,"%.0f",total));
        });
        form.addView(suggested,mb(8));
        calculate.run();
        ScrollView sc=new ScrollView(activity);
        sc.setFillViewport(false);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Presupuesto · "+b.optString("code"))
            .setView(sc)
            .setNegativeButton("Cancelar",(d,w)->{})
            .setPositiveButton("Enviar presupuesto",(d,w)->{})
            .create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            double distance=num(kmF),tariff=num(rateF),minimum=num(minF),tollPrice=num(tollUnit),
                waiting=num(waitingF),pickup=num(pickupF),other=num(otherF),total=num(finalF);
            int countFinal=(int)Math.round(num(tollCount));
            double referenceTotal=Math.max(distance*tariff,minimum)+
                countFinal*tollPrice+waiting+pickup+other;
            if(!ConductorOperationsPolicy.goodDistance(distance)||
                !ConductorOperationsPolicy.goodPrice(tariff)||
                !ConductorOperationsPolicy.goodPrice(total)||
                !ConductorOperationsPolicy.nonnegative(minimum)||
                !ConductorOperationsPolicy.nonnegative(tollPrice)||
                !ConductorOperationsPolicy.nonnegative(waiting)||
                !ConductorOperationsPolicy.nonnegative(pickup)||
                !ConductorOperationsPolicy.nonnegative(other)||
                !ConductorOperationsPolicy.nonnegative(referenceTotal)||
                countFinal<0||countFinal>20){
                callback.finished(false,"Revisá distancia, tarifa e importes. No se envió nada.");return;
            }
            new AlertDialog.Builder(activity)
                .setTitle("Confirmar presupuesto")
                .setMessage("Enviar al pasajero el precio final de "+
                    String.format(Locale.US,"%.0f UYU",total)+" por la reserva "+
                    b.optString("code")+"?")
                .setNegativeButton("Volver",null)
                .setPositiveButton("Enviar",(d,w)->{
                    if(sending)return;
                    sending=true;
                    final String quoteDescription=includes.getText().toString().trim();
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                    dialog.setCancelable(false);
                    new Thread(()->{
                        boolean ok=false;String message="No se confirmó el presupuesto.";
                        try{
                            if(!credential.equals(DriverIdentity.pin(activity)))
                                throw new IllegalStateException("Cambió el conductor vinculado.");
                            JSONArray rows=Api.listActive(credential);JSONObject current=null;
                            for(int i=0;i<rows.length();i++){
                                JSONObject row=rows.optJSONObject(i);
                                if(row!=null&&id.equals(row.optString("id"))){current=row;break;}
                            }
                            if(current==null||!ConductorOperationsPolicy.canQuote(current.optString("status"),
                                current.optString("pickup_mode"),current.optString("fare_mode")))
                                throw new IllegalStateException("La reserva ya no admite presupuesto.");
                            JSONObject fit=Api.checkRouteFitV114(credential,id);
                            if(!fit.optBoolean("available",false))
                                throw new IllegalStateException("Ese horario ya no está disponible.");
                            JSONObject sent=Api.sendQuoteV107(credential,id,distance,
                                current.optInt("route_duration_min",0),tariff,minimum,countFinal,
                                tollPrice,waiting,pickup,other,referenceTotal,total,
                                quoteDescription);
                            ok=ConductorOperationsPolicy.quoteConfirmed(sent.optString("status"),
                                sent.optString("quote_status"),sent.optBoolean("ok",false))
                                && id.equals(sent.optString("id"));
                            if(ok){message="Presupuesto enviado y confirmado por el servidor.";}
                            else message="El servidor no confirmó el estado enviado.";
                        }catch(Exception e){
                            message=e instanceof IllegalStateException?e.getMessage():Api.publicMessage("quote",e);
                        }
                        final boolean confirmed=ok;
                        final String outcome=message;
                        activity.runOnUiThread(()->{
                            sending=false;
                            if(activity.isFinishing()||activity.isDestroyed())return;
                            if(confirmed){dialog.dismiss();TransferAlerts.refreshAsync(activity);}
                            else{
                                dialog.setCancelable(true);
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            }
                            callback.finished(confirmed,outcome);
                        });
                    },"quicktrip-quote-send").start();
                }).show();
        }));
        dialog.show();
    }
    private EditText number(LinearLayout form,String label,String value){
        TextView title=new TextView(activity);title.setText(label);
        title.setTextColor(Color.LTGRAY);title.setTextSize(12);form.addView(title);
        EditText field=new EditText(activity);
        field.setSingleLine(true);
        field.setTextColor(Color.WHITE);field.setTextSize(16);
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(value);form.addView(field,mb(10));return field;
    }
    private static double num(EditText f){
        try{
            double v=Double.parseDouble(f.getText().toString().replace(',','.').trim());
            return Double.isFinite(v)?v:Double.NaN;
        }catch(Exception e){return Double.NaN;}
    }
    private static String value(JSONObject r,String key){
        double v=r.optDouble(key,0);
        return v>0?String.format(Locale.US,"%.0f",v):"0";
    }
    private LinearLayout.LayoutParams mb(int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(bottom);return p;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
