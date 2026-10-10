#!/usr/bin/env python3
"""Deterministic patch from verified Cliente v11.4 R9.1 snapshot to R11 native release."""
from pathlib import Path
import re

root=Path("cliente")
java=root/"src/main/java/uy/com/traslados/cliente/MainActivity.java"
gradle=root/"build.gradle"

def replace_once(source,old,new,label):
    n=source.count(old)
    if n!=1:
        raise SystemExit(f"Cliente R11: expected one {label}, found {n}")
    return source.replace(old,new)

s=java.read_text(encoding="utf-8")
s=replace_once(s,'    private String draftName="", draftPhone="", draftComments="", draftOrigin="", draftDestination="";',
'''    private String draftName="", draftPhone="", draftComments="", draftOrigin="", draftDestination="";
    private ClienteTripEnhancements tripEnhancements;''',"booking state")
s=replace_once(s,"        Api.init(getApplicationContext());",
'''        Api.init(getApplicationContext());
        tripEnhancements=new ClienteTripEnhancements(this,pool);''',"initialize booking additions")
s=replace_once(s,'        comentarios=addField(secComments.body,"comment","Comentarios (opcional)","Ej. Vuelo, equipaje, alguna indicación...");',
'''        comentarios=addField(secComments.body,"comment","Comentarios (opcional)","Ej. Vuelo, equipaje, alguna indicación...");
        tripEnhancements.attachForm(secTrip.body,secOrigin.body,secDestination.body,secComments.body,
            minutes->{
                if(minutes<0){elegirFecha();return;}
                if(minutes<=10){
                    tripEnhancements.showPickupEta(sessionToken,origenP.set,origenP.lat,origenP.lng,
                        minutes,origen==null?"":origen.getText().toString(),
                        destino==null?"":destino.getText().toString());
                    return;
                }
                Calendar when=Calendar.getInstance(TimeZone.getTimeZone("America/Montevideo"));
                when.add(Calendar.MINUTE,minutes);
                dateFmt.setTimeZone(TimeZone.getTimeZone("America/Montevideo"));
                timeFmt.setTimeZone(TimeZone.getTimeZone("America/Montevideo"));
                fecha=dateFmt.format(when.getTime());hora=timeFmt.format(when.getTime());
                fechaBtn.setText("FECHA\\n"+fecha);
                horaBtn.setText("HORA\\n"+hora.substring(0,5));
                if(secTrip!=null)secTrip.summary.setText(tripSummary());
                toast(minutes<=1?"Pedido para ahora, sujeto a disponibilidad":
                    "Pedido para dentro de 10 minutos, sujeto a disponibilidad");
            });''',"attach booking options")
s=replace_once(s,'        addDetail(card,"flag","Destino",draftDestination);',
'''        addDetail(card,"flag","Destino",draftDestination);
        tripEnhancements.review(card);''',"review including stops")
s=replace_once(s,'                if(routeEstimated){b.put("p_route_distance_km",routeDistanceKm);b.put("p_route_duration_min",routeDurationMin);}else{b.put("p_route_distance_km",JSONObject.NULL);b.put("p_route_duration_min",JSONObject.NULL);}',
'''                b.put("p_origin_department",tripEnhancements.originDepartment());
                b.put("p_destination_department",tripEnhancements.destinationDepartment());
                b.put("p_stops",tripEnhancements.stopsJson());
                b.put("p_passenger_name",JSONObject.NULL);b.put("p_passenger_phone",JSONObject.NULL);
                // customer_create_reservation_v12 accepts verified stops/departments,
                // but intentionally does not accept computed route-distance arguments.''',"v12 fields")
s=replace_once(s,'new JSONObject(Api.postRpc("customer_create_reservation_v11_4",b))',
'new JSONObject(Api.postRpc("customer_create_reservation_v12",b))',"upgrade booking RPC")
s=s.replace("Cliente v11.4 R9.1 iniciado","Cliente v11.5 R10 iniciado")
java.write_text(s,encoding="utf-8")

g=gradle.read_text(encoding="utf-8")
g=replace_once(g,"versionCode 118","versionCode 121","versionCode")
g=replace_once(g,"versionName '11.4-R9.1'","versionName '11.5-R11'","versionName")
head="""def releaseKeystorePath=System.getenv('ANDROID_KEYSTORE_PATH')
def releaseStorePassword=System.getenv('ANDROID_KEYSTORE_PASSWORD')
def releaseKeyAlias=System.getenv('ANDROID_KEY_ALIAS') ?: 'traslados-conductor'
def releaseKeyPassword=System.getenv('ANDROID_KEY_PASSWORD') ?: releaseStorePassword
def hasReleaseSigning=releaseKeystorePath && releaseStorePassword

"""
g=replace_once(g,"android {",head+"android {","signing constants")
g=replace_once(g,"    compileOptions {",
"""    signingConfigs {
        release {
            if (hasReleaseSigning) {
                storeFile file(releaseKeystorePath)
                storePassword releaseStorePassword
                keyAlias releaseKeyAlias
                keyPassword releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            minifyEnabled false
            shrinkResources false
            if (hasReleaseSigning) signingConfig signingConfigs.release
        }
    }
    compileOptions {""","release signing")
gradle.write_text(g,encoding="utf-8")
print("Cliente v11.5 R11 ready: departments, stops, quick times, v12 RPC, stable release signing")
