package uy.com.mapatrayectos;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public final class AddressResolver {
    private AddressResolver(){}

    public static String fallback(double lat,double lon,String zone){
        String z=zone==null?"":zone.trim();
        String coords=String.format(Locale.US,"%.5f, %.5f",lat,lon);
        return z.isEmpty()?coords:z+" · "+coords;
    }

    public static String resolve(Context context,double lat,double lon,String zone){
        try{
            if(!Geocoder.isPresent())return fallback(lat,lon,zone);
            Geocoder g=new Geocoder(context,new Locale("es","UY"));
            List<Address> rows=g.getFromLocation(lat,lon,1);
            if(rows==null||rows.isEmpty())return fallback(lat,lon,zone);
            Address a=rows.get(0);
            String line=a.getAddressLine(0);
            if(line!=null&&!line.trim().isEmpty())return clean(line);

            StringBuilder sb=new StringBuilder();
            String street=a.getThoroughfare(),number=a.getSubThoroughfare();
            if(street!=null&&!street.trim().isEmpty())sb.append(street.trim());
            if(number!=null&&!number.trim().isEmpty()){
                if(sb.length()>0)sb.append(' ');
                sb.append(number.trim());
            }
            String locality=firstNonEmpty(a.getSubLocality(),a.getLocality(),a.getSubAdminArea());
            if(locality!=null&&!locality.trim().isEmpty()&&!containsIgnoreCase(sb.toString(),locality)){
                if(sb.length()>0)sb.append(" · ");
                sb.append(locality.trim());
            }
            if(sb.length()>0)return clean(sb.toString());
        }catch(Exception ignored){}
        return fallback(lat,lon,zone);
    }

    public static void resolveTripStartAsync(Context context,String tripId,double lat,double lon,String zone){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String address=resolve(app,lat,lon,zone);
            TrackDb db=new TrackDb(app);
            db.setTripStartLocation(tripId,lat,lon,address);JSONObject t=db.getTrip(tripId);boolean ended=t!=null&&!t.isNull("ended_at_ms");db.close();if(ended)Api.syncTripAsync(tripId);
        },"address-start").start();
    }

    public static void resolveTripEndAsync(Context context,String tripId,double lat,double lon,String zone){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String address=resolve(app,lat,lon,zone);
            TrackDb db=new TrackDb(app);
            db.setTripEndLocation(tripId,lat,lon,address);db.close();Api.syncTripAsync(tripId);
        },"address-end").start();
    }

    public static void resolveTripPickupAsync(Context context,String tripId,double lat,double lon,String zone){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String address=resolve(app,lat,lon,zone);
            TrackDb db=new TrackDb(app);
            db.setTripPickupLocation(tripId,lat,lon,address);db.close();
        },"address-pickup").start();
    }

    public static void resolveTripStopAsync(Context context,String stopId,double lat,double lon,String zone){
        Context app=context.getApplicationContext();
        new Thread(()->{
            String address=resolve(app,lat,lon,zone);
            TrackDb db=new TrackDb(app);
            db.setStopLocation(stopId,lat,lon,address,zone);db.close();
        },"address-stop").start();
    }

    public static int backfillMissingTripLocations(Context context){
        Context app=context.getApplicationContext();
        TrackDb db=new TrackDb(app);
        int changed=db.backfillTripEndpointsFromPoints();
        JSONArray trips=db.listTrips();
        db.close();

        for(int i=0;i<trips.length();i++){
            JSONObject t=trips.optJSONObject(i);if(t==null)continue;
            String id=t.optString("trip_id","");
            if(id.isEmpty())continue;

            if(!t.isNull("start_lat")&&!t.isNull("start_lon")){
                String a=t.optString("start_address","");
                if(a.isEmpty()){
                    double lat=t.optDouble("start_lat"),lon=t.optDouble("start_lon");
                    String address=resolve(app,lat,lon,t.optString("start_zone",""));
                    TrackDb x=new TrackDb(app);x.setTripStartLocation(id,lat,lon,address);x.close();changed++;
                }
            }
            if(!t.isNull("end_lat")&&!t.isNull("end_lon")){
                String a=t.optString("end_address","");
                if(a.isEmpty()){
                    double lat=t.optDouble("end_lat"),lon=t.optDouble("end_lon");
                    String address=resolve(app,lat,lon,t.optString("end_zone",""));
                    TrackDb x=new TrackDb(app);x.setTripEndLocation(id,lat,lon,address);x.close();changed++;
                }
            }
        }
        return changed;
    }

    private static String firstNonEmpty(String... values){
        for(String v:values)if(v!=null&&!v.trim().isEmpty())return v;
        return null;
    }
    private static boolean containsIgnoreCase(String a,String b){
        return a!=null&&b!=null&&a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
    }
    private static String clean(String s){
        if(s==null)return "";
        return s.replace(" ,",",").replaceAll("\\s+"," ").trim();
    }
}
