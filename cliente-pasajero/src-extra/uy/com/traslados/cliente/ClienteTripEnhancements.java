package uy.com.traslados.cliente;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.IntConsumer;

/** Customer booking additions. Source-controlled separately from the recovered R9.1 baseline. */
public final class ClienteTripEnhancements {
    private static final String[] DEPARTMENTS={
        "Montevideo","Canelones","Artigas","Cerro Largo","Colonia","Durazno",
        "Flores","Florida","Lavalleja","Maldonado","Paysandú","Río Negro",
        "Rivera","Rocha","Salto","San José","Soriano","Tacuarembó","Treinta y Tres"
    };
    private final Activity activity;
    private final ExecutorService worker;
    private final ArrayList<Stop> stops=new ArrayList<>();
    private String originDepartment="Canelones",destinationDepartment="Montevideo";
    private LinearLayout stopList;
    private Button fromButton,toButton;
    private final int gold=Color.rgb(224,193,111),white=Color.rgb(244,241,232),petrol=Color.rgb(6,43,52);
    private static class Stop {
        final String text,department;
        final double lat,lng;
        Stop(String text,String dep,double lat,double lng){this.text=text;department=dep;this.lat=lat;this.lng=lng;}
    }
    public ClienteTripEnhancements(Activity a,ExecutorService w){activity=a;worker=w;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView caption(String txt){
        TextView t=new TextView(activity);t.setText(txt);t.setTextSize(12);t.setTextColor(gold);
        t.setPadding(dp(6),dp(9),dp(4),dp(6));return t;
    }
    private Button button(String txt){
        Button b=new Button(activity);b.setText(txt);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(white);
        GradientDrawable bg=new GradientDrawable();bg.setColor(petrol);bg.setCornerRadius(dp(13));
        bg.setStroke(dp(1),gold);b.setBackground(bg);
        b.setMinHeight(dp(45));return b;
    }
    private void addBox(LinearLayout parent,View view,int margin){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(margin);parent.addView(view,lp);
    }
    private void addQuick(LinearLayout parent,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(49),1);
        p.setMargins(dp(2),dp(2),dp(2),dp(2));parent.addView(b,p);
    }
    public void attachForm(LinearLayout trip,LinearLayout origin,LinearLayout destination,LinearLayout extra,IntConsumer chooseTime){
        trip.addView(caption("¿CUÁNDO QUERÉS VIAJAR?"),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout times=new LinearLayout(activity);times.setGravity(Gravity.CENTER);
        Button now=button("AHORA"),ten=button("+10 MIN"),later=button("PROGRAMAR");
        addQuick(times,now);addQuick(times,ten);addQuick(times,later);
        addBox(trip,times,1);
        now.setOnClickListener(v->chooseTime.accept(1));
        ten.setOnClickListener(v->chooseTime.accept(10));
        later.setOnClickListener(v->chooseTime.accept(-1));
        addBox(trip,caption("Los horarios inmediatos están sujetos a disponibilidad real."),2);

        fromButton=button("DEPARTAMENTO · "+originDepartment);
        toButton=button("DEPARTAMENTO · "+destinationDepartment);
        addBox(origin,caption("ORIGEN · DEPARTAMENTO"),4);
        addBox(origin,fromButton,0);
        addBox(destination,caption("DESTINO · DEPARTAMENTO"),4);
        addBox(destination,toButton,0);
        fromButton.setOnClickListener(v->selectDepartment(true));
        toButton.setOnClickListener(v->selectDepartment(false));

        addBox(extra,caption("PARADAS INTERMEDIAS (opcional, máximo 8)"),7);
        stopList=new LinearLayout(activity);stopList.setOrientation(LinearLayout.VERTICAL);
        addBox(extra,stopList,2);
        Button add=button("+ AGREGAR PARADA");
        add.setOnClickListener(v->askNewStop());addBox(extra,add,4);
        redrawStops();
    }
    private void selectDepartment(boolean from){
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Seleccioná el departamento")
            .setItems(DEPARTMENTS,(d,index)->{
                if(from){originDepartment=DEPARTMENTS[index];fromButton.setText("DEPARTAMENTO · "+originDepartment);}
                else{destinationDepartment=DEPARTMENTS[index];toButton.setText("DEPARTAMENTO · "+destinationDepartment);}
            }).setNegativeButton("CANCELAR",null).create();
        dialog.setOnShowListener(v->{
            if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });dialog.show();
    }
    private void askNewStop(){
        if(stops.size()>=8){toast("Máximo 8 paradas intermedias");return;}
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(4),dp(18),dp(8));
        TextView helper=caption("Escribí dirección, número y localidad. La búsqueda es por acción tuya.");
        box.addView(helper);
        EditText input=new EditText(activity);
        input.setSingleLine(false);input.setMinLines(1);input.setMaxLines(3);
        input.setTextColor(Color.BLACK);input.setHint("Ej.: Av. Italia 1234, Montevideo");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        Spinner dep=new Spinner(activity);
        ArrayAdapter<String> aa=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,DEPARTMENTS);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dep.setAdapter(aa);dep.setSelection(1);
        box.addView(dep,new LinearLayout.LayoutParams(-1,dp(54)));
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Nueva parada").setView(box)
            .setNegativeButton("CANCELAR",null).setPositiveButton("BUSCAR",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
                final String addr=input.getText().toString().trim();
                final String dept=dep.getSelectedItem().toString();
                if(addr.length()<5){input.setError("Dirección demasiado corta");return;}
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                worker.execute(()->{
                    try{
                        Geocoder geocoder=new Geocoder(activity,new Locale("es","UY"));
                        List<Address> matches=geocoder.getFromLocationName(addr+", "+dept+", Uruguay",4);
                        Address match=null;
                        if(matches!=null)for(Address item:matches){
                            if(item==null||!item.hasLatitude()||!item.hasLongitude())continue;
                            double lat=item.getLatitude(),lng=item.getLongitude();
                            if(lat>=-35.3&&lat<=-30&&lng>=-58.65&&lng<=-53){match=item;break;}
                        }
                        if(match==null)throw new IllegalStateException("No se pudo identificar la dirección. Probá con calle, número y localidad.");
                        final double lat=match.getLatitude(),lng=match.getLongitude();
                        activity.runOnUiThread(()->{
                            stops.add(new Stop(addr+", "+dept,dept,lat,lng));
                            dialog.dismiss();redrawStops();
                            toast("Parada agregada; verificá el punto antes de confirmar");
                        });
                    }catch(Exception e){
                        activity.runOnUiThread(()->{
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            toast(e.getMessage()==null?"No se pudo ubicar la parada":e.getMessage());
                        });
                    }
                });
            });
        });dialog.show();
    }
    private void toast(String txt){Toast.makeText(activity,txt,Toast.LENGTH_LONG).show();}
    private void redrawStops(){
        if(stopList==null)return;stopList.removeAllViews();
        if(stops.isEmpty()){
            TextView t=caption("Sin paradas. Podés agregar hasta ocho.");stopList.addView(t);return;
        }
        for(int i=0;i<stops.size();i++){
            final int index=i;Stop stop=stops.get(i);
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=caption((i+1)+". "+stop.text);
            title.setTextColor(white);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            Button remove=button("×");remove.setContentDescription("Quitar parada "+(i+1));
            row.addView(remove,new LinearLayout.LayoutParams(dp(52),dp(44)));
            remove.setOnClickListener(v->{stops.remove(index);redrawStops();});
            addBox(stopList,row,3);
        }
    }
    public String originDepartment(){return originDepartment;}
    public String destinationDepartment(){return destinationDepartment;}
    public JSONArray stopsJson(){
        JSONArray a=new JSONArray();
        for(int i=0;i<stops.size();i++){
            Stop s=stops.get(i);JSONObject o=new JSONObject();
            try{
                o.put("position",i+1);o.put("address_text",s.text);o.put("lat",s.lat);
                o.put("lng",s.lng);o.put("department",s.department);a.put(o);
            }catch(JSONException ignored){}
        }return a;
    }
    public void review(LinearLayout container){
        TextView depart=caption("Departamentos: "+originDepartment+" → "+destinationDepartment);
        container.addView(depart);
        if(!stops.isEmpty()){
            container.addView(caption("PARADAS INTERMEDIAS: "+stops.size()));
            for(int i=0;i<stops.size();i++)container.addView(caption((i+1)+". "+stops.get(i).text));
            container.addView(caption("La distancia lineal origen-destino no incluye desvíos de las paradas."));
        }
    }
}
