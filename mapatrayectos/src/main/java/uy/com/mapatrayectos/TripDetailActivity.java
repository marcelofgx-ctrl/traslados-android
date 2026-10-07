package uy.com.mapatrayectos;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.*;
import org.maplibre.android.style.layers.*;
import org.maplibre.android.style.sources.GeoJsonSource;
import java.text.SimpleDateFormat;
import java.util.*;

public class TripDetailActivity extends Activity {
    private static final String STYLE_URL="https://tiles.openfreemap.org/styles/liberty";
    private static final int BG=Color.rgb(7,25,31),GOLD=Color.rgb(224,193,111),TEXT=Color.rgb(245,244,238),MUTED=Color.rgb(174,188,191),GREEN=Color.rgb(54,190,125),RED=Color.rgb(225,78,84),ROUTE=Color.rgb(73,199,225),RETURN_ROUTE=Color.rgb(235,166,65);
    private static final int INK=Color.rgb(10,47,57),INK_SOFT=Color.rgb(73,97,101),GOLD_DEEP=Color.rgb(171,128,35),TEAL=Color.rgb(17,111,118);
    private MapView mapView;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);MapLibre.getInstance(this);
        String tripId=getIntent().getStringExtra("trip_id");
        if(tripId==null||tripId.isEmpty()){Toast.makeText(this,"No se pudo identificar el viaje.",Toast.LENGTH_LONG).show();finish();return;}
        TrackDb db=new TrackDb(this);db.backfillTripEndpointsFromPoints();JSONObject trip=db.getTrip(tripId);JSONArray points=TelemetryQuality.cleanRoute(db.getTripPoints(tripId));db.close();
        if(trip==null){Toast.makeText(this,"El viaje ya no está disponible en el historial local.",Toast.LENGTH_LONG).show();finish();return;}
        build(b,trip,points);
        if((trip.optString("start_address","").isEmpty()||trip.optString("end_address","").isEmpty())&&points.length()>0){
            new Thread(()->AddressResolver.backfillMissingTripLocations(getApplicationContext()),"detail-address-backfill").start();
        }
    }

    private void build(Bundle b,JSONObject trip,JSONArray points){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);mapView=new MapView(this);mapView.onCreate(b);root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(10),dp(6));
        top.setBackground(new TexturedDrawable(this,Color.rgb(5,47,56),Color.rgb(10,78,84),Color.argb(210,224,193,111),20f,1f,false));top.setElevation(dp(6));
        TextView back=text("‹",28,TEXT,true);back.setGravity(Gravity.CENTER);back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.addView(text("RECORRIDO DEL VIAJE",17,TEXT,true));
        titles.addView(text("Origen, destino y traza GPS",10.4f,Color.rgb(232,211,145),true));
        top.addView(titles,new LinearLayout.LayoutParams(0,-1,1));
        FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP);tlp.setMargins(dp(8),dp(8),dp(8),0);root.addView(top,tlp);

        int split=returnSplitIndex(points);

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),dp(10),dp(12),dp(10));
        info.setBackground(new TexturedDrawable(this,Color.argb(248,253,251,239),Color.argb(248,228,243,238),Color.argb(190,214,174,67),24f,1f,true));info.setElevation(dp(8));

        String type=trip.optString("trip_type","other"),status=trip.optString("trip_status","completed");double amount=trip.isNull("amount_uyu")?0:trip.optDouble("amount_uyu",0);
        LinearLayout tagRow=new LinearLayout(this);tagRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag=text(typeLabel(type)+("cancelled".equals(status)?" · CANCELADO":" · COMPLETADO"),10.7f,"cancelled".equals(status)?RED:GREEN,true);tagRow.addView(tag,new LinearLayout.LayoutParams(0,-2,1));
        if(split>=0){
            TextView legend=text("● IDA   ● VUELTA",9.5f,INK_SOFT,true);legend.setGravity(Gravity.RIGHT);legend.setContentDescription("Ida en turquesa, vuelta en dorado");tagRow.addView(legend,new LinearLayout.LayoutParams(0,-2,1));
        }
        info.addView(tagRow);

        double startLat=trip.isNull("start_lat")?pointLat(points,0):trip.optDouble("start_lat");
        double startLon=trip.isNull("start_lon")?pointLon(points,0):trip.optDouble("start_lon");
        double endLat=trip.isNull("end_lat")?pointLat(points,points.length()-1):trip.optDouble("end_lat");
        double endLon=trip.isNull("end_lon")?pointLon(points,points.length()-1):trip.optDouble("end_lon");
        String startAddress=endpointText(trip,true,startLat,startLon),endAddress=endpointText(trip,false,endLat,endLon);

        info.addView(endpointRow("ORIGEN",startAddress,startLat,startLon,GREEN),lp(5,0));
        info.addView(endpointRow("DESTINO",endAddress,endLat,endLon,RED),lp(2,0));

        double km=trip.optDouble("distance_m",0)/1000.0,avg=trip.optDouble("avg_speed_kmh",0),storedMax=trip.optDouble("max_speed_kmh",0);double robustMax=TelemetryQuality.confirmedMaxKmh(points);double max=robustMax>0?robustMax:storedMax;
        long moving=trip.optLong("moving_ms",0),stopped=trip.optLong("stopped_ms",0),start=trip.optLong("started_at_ms",0),end=trip.optLong("ended_at_ms",0);
        TextView mainStats=text(String.format(Locale.getDefault(),"%.1f km   ·   %s",km,duration(end>start?end-start:moving+stopped)),13.5f,INK,true);mainStats.setGravity(Gravity.CENTER);info.addView(mainStats,lp(6,0));
        TextView speeds=text(String.format(Locale.getDefault(),"Vel. prom. %.0f km/h   ·   Vel. máx. %.0f km/h",avg,max),11.8f,GOLD_DEEP,true);speeds.setGravity(Gravity.CENTER);info.addView(speeds,lp(4,0));
        if(amount>0){TextView fare=text(String.format(Locale.getDefault(),"Importe: $ %.0f UYU",amount),12.5f,GREEN,true);fare.setGravity(Gravity.CENTER);info.addView(fare,lp(4,0));}
        TextView ms=text("Movimiento "+duration(moving)+"   ·   Detenido "+duration(stopped),10.6f,INK_SOFT,false);ms.setGravity(Gravity.CENTER);info.addView(ms,lp(4,0));
        if(start>0){TextView when=text(new SimpleDateFormat("EEEE d 'de' MMMM · HH:mm",new Locale("es","UY")).format(new Date(start)),10.1f,INK_SOFT,false);when.setGravity(Gravity.CENTER);info.addView(when,lp(4,0));}

        int panelHeight=amount>0?286:268;FrameLayout.LayoutParams ilp=new FrameLayout.LayoutParams(-1,dp(panelHeight),Gravity.BOTTOM);ilp.setMargins(dp(8),0,dp(8),dp(8));root.addView(info,ilp);

        root.setOnApplyWindowInsetsListener((v,insets)->{int topInset=insets.getSystemWindowInsetTop(),bottomInset=insets.getSystemWindowInsetBottom();FrameLayout.LayoutParams a=(FrameLayout.LayoutParams)top.getLayoutParams();a.topMargin=topInset+dp(6);top.setLayoutParams(a);FrameLayout.LayoutParams z=(FrameLayout.LayoutParams)info.getLayoutParams();z.bottomMargin=bottomInset+dp(8);info.setLayoutParams(z);return insets;});root.requestApplyInsets();
        setContentView(root);

        final String routeJson=lineGeoJson(points),returnJson=split>=0?lineGeoJsonRange(points,split,points.length()-1):emptyGeoJson();final double[] camera=cameraFor(points);
        mapView.getMapAsync(map->{map.setStyle(new Style.Builder().fromUri(STYLE_URL),style->{
            GeoJsonSource src=new GeoJsonSource("history-route",routeJson);style.addSource(src);
            style.addLayer(new LineLayer("history-casing","history-route").withProperties(PropertyFactory.lineColor(BG),PropertyFactory.lineWidth(11f),PropertyFactory.lineOpacity(0.80f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));
            style.addLayer(new LineLayer("history-glow","history-route").withProperties(PropertyFactory.lineColor(ROUTE),PropertyFactory.lineWidth(8.6f),PropertyFactory.lineOpacity(0.28f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));
            style.addLayer(new LineLayer("history-line","history-route").withProperties(PropertyFactory.lineColor(ROUTE),PropertyFactory.lineWidth(5.8f),PropertyFactory.lineOpacity(0.99f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));
            GeoJsonSource ret=new GeoJsonSource("history-return",returnJson);style.addSource(ret);
            style.addLayer(new LineLayer("history-return-glow","history-return").withProperties(PropertyFactory.lineColor(RETURN_ROUTE),PropertyFactory.lineWidth(8.0f),PropertyFactory.lineOpacity(0.30f),PropertyFactory.lineOffset(2.2f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));
            style.addLayer(new LineLayer("history-return-line","history-return").withProperties(PropertyFactory.lineColor(RETURN_ROUTE),PropertyFactory.lineWidth(4.8f),PropertyFactory.lineOpacity(0.99f),PropertyFactory.lineOffset(2.2f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));

            if(points.length()>0){JSONObject first=points.optJSONObject(0),last=points.optJSONObject(points.length()-1);GeoJsonSource s1=new GeoJsonSource("start",point(first));GeoJsonSource s2=new GeoJsonSource("end",point(last));style.addSource(s1);style.addSource(s2);style.addLayer(new CircleLayer("start-halo","start").withProperties(PropertyFactory.circleRadius(10f),PropertyFactory.circleColor(Color.WHITE),PropertyFactory.circleOpacity(0.88f)));style.addLayer(new CircleLayer("start-dot","start").withProperties(PropertyFactory.circleRadius(6.5f),PropertyFactory.circleColor(GREEN),PropertyFactory.circleStrokeColor(BG),PropertyFactory.circleStrokeWidth(1.3f)));style.addLayer(new CircleLayer("end-halo","end").withProperties(PropertyFactory.circleRadius(10f),PropertyFactory.circleColor(Color.WHITE),PropertyFactory.circleOpacity(0.88f)));style.addLayer(new CircleLayer("end-dot","end").withProperties(PropertyFactory.circleRadius(6.5f),PropertyFactory.circleColor(RED),PropertyFactory.circleStrokeColor(BG),PropertyFactory.circleStrokeWidth(1.3f)));}
            CameraPosition cp=new CameraPosition.Builder().target(new LatLng(camera[0],camera[1])).zoom(camera[2]).tilt(0).bearing(0).build();map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp));
        });});
    }

    private View endpointRow(String label,String address,double lat,double lon,int accent){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot=text("●",10,accent,true);dot.setGravity(Gravity.CENTER);row.addView(dot,new LinearLayout.LayoutParams(dp(20),dp(38)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);
        TextView l=text(label,8.4f,GOLD_DEEP,true);TextView a=text(address,11.4f,INK,true);a.setSingleLine(true);a.setEllipsize(TextUtils.TruncateAt.END);tx.addView(l);tx.addView(a);row.addView(tx,new LinearLayout.LayoutParams(0,dp(42),1));
        if(!Double.isNaN(lat)&&!Double.isNaN(lon)){
            TextView nav=text("↗",18,TEAL,true);nav.setGravity(Gravity.CENTER);nav.setContentDescription("Navegar a "+label.toLowerCase(Locale.ROOT));
            nav.setBackground(new TexturedDrawable(this,Color.argb(225,250,251,246),Color.argb(225,224,241,237),Color.argb(145,17,111,118),17f,1f,true));
            nav.setOnClickListener(v->openNavigation(lat,lon,address));row.addView(nav,new LinearLayout.LayoutParams(dp(38),dp(36)));
        }
        return row;
    }

    private String endpointText(JSONObject trip,boolean start,double lat,double lon){
        String p=start?"start_":"end_";String address=trip.optString(p+"address","");if(!address.isEmpty())return address;
        String zone=trip.optString(p+"zone","");if(!Double.isNaN(lat)&&!Double.isNaN(lon))return AddressResolver.fallback(lat,lon,zone);
        return zone.isEmpty()?(start?"Origen sin dirección":"Destino sin dirección"):zone;
    }

    private void openNavigation(double lat,double lon,String label){
        try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+lat+","+lon+"&mode=d"));i.setPackage("com.google.android.apps.maps");startActivity(i);}
        catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+lat+","+lon+"?q="+lat+","+lon+"("+Uri.encode(label)+")")));}
    }

    private int returnSplitIndex(JSONArray a){
        if(a==null||a.length()<8)return -1;JSONObject first=a.optJSONObject(0);if(first==null)return -1;double slat=first.optDouble("lat"),slon=first.optDouble("lon");float maxDist=0,endDist=0;int maxIndex=-1;float[] out=new float[1];
        for(int i=1;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;android.location.Location.distanceBetween(slat,slon,o.optDouble("lat"),o.optDouble("lon"),out);float d=out[0];if(d>maxDist){maxDist=d;maxIndex=i;}if(i==a.length()-1)endDist=d;}
        if(maxDist<300f||maxIndex<3||maxIndex>a.length()-4||endDist>maxDist*0.78f)return -1;
        double returnMeters=0;JSONObject prev=a.optJSONObject(maxIndex);
        for(int i=maxIndex+1;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||prev==null)continue;android.location.Location.distanceBetween(prev.optDouble("lat"),prev.optDouble("lon"),o.optDouble("lat"),o.optDouble("lon"),out);returnMeters+=out[0];prev=o;}
        return returnMeters>=Math.max(180.0,maxDist*0.22)?maxIndex:-1;
    }

    private String typeLabel(String s){if("uber".equals(s))return "UBER";if("cabify".equals(s))return "CABIFY";if("personal".equals(s))return "PERSONAL";return "OTRO";}
    private String emptyGeoJson(){return "{\"type\":\"FeatureCollection\",\"features\":[]}";}
    private String point(JSONObject o){if(o==null)return emptyGeoJson();return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":["+o.optDouble("lon")+","+o.optDouble("lat")+"]}}]}";}
    private String lineGeoJson(JSONArray a){return lineGeoJsonRange(a,0,a==null?-1:a.length()-1);}
    private String lineGeoJsonRange(JSONArray a,int from,int to){if(a==null||a.length()<2||from<0||to<=from||to>=a.length())return emptyGeoJson();StringBuilder sb=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");boolean first=true;for(int i=from;i<=to;i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;if(!first)sb.append(',');first=false;sb.append('[').append(o.optDouble("lon")).append(',').append(o.optDouble("lat")).append(']');}return sb.append("]}}]}").toString();}
    private double pointLat(JSONArray a,int i){JSONObject o=(a!=null&&i>=0&&i<a.length())?a.optJSONObject(i):null;return o==null?Double.NaN:o.optDouble("lat",Double.NaN);}
    private double pointLon(JSONArray a,int i){JSONObject o=(a!=null&&i>=0&&i<a.length())?a.optJSONObject(i):null;return o==null?Double.NaN:o.optDouble("lon",Double.NaN);}
    private double[] cameraFor(JSONArray a){if(a.length()==0)return new double[]{-34.88,-56.08,11.6};double minLat=90,maxLat=-90,minLon=180,maxLon=-180;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;double lat=o.optDouble("lat"),lon=o.optDouble("lon");minLat=Math.min(minLat,lat);maxLat=Math.max(maxLat,lat);minLon=Math.min(minLon,lon);maxLon=Math.max(maxLon,lon);}double span=Math.max(maxLat-minLat,maxLon-minLon);double z=span<.002?16.0:span<.006?14.8:span<.015?13.8:span<.035?12.8:span<.08?11.8:10.8;return new double[]{(minLat+maxLat)/2,(minLon+maxLon)/2,z};}
    private LinearLayout.LayoutParams lp(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,dp(bottom));return p;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable rounded(int fill,int radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String duration(long ms){long x=Math.max(0,ms)/1000,h=x/3600,m=(x%3600)/60,s=x%60;return h>0?String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,s):String.format(Locale.getDefault(),"%02d:%02d",m,s);}
    private void signalTrackingUi(boolean visible){android.content.SharedPreferences s=getSharedPreferences("tracking_state",MODE_PRIVATE);if(!s.getBoolean("shift_active",false))return;try{startService(new android.content.Intent(this,TrackingService.class).setAction(visible?TrackingService.ACTION_UI_VISIBLE:TrackingService.ACTION_UI_HIDDEN));}catch(Exception ignored){}}
    @Override protected void onStart(){super.onStart();if(mapView!=null)mapView.onStart();}
    @Override protected void onResume(){super.onResume();if(mapView!=null)mapView.onResume();signalTrackingUi(true);}
    @Override protected void onPause(){signalTrackingUi(false);if(mapView!=null)mapView.onPause();super.onPause();}
    @Override protected void onStop(){if(mapView!=null)mapView.onStop();super.onStop();}
    @Override protected void onDestroy(){if(mapView!=null)mapView.onDestroy();super.onDestroy();}
    @Override public void onLowMemory(){super.onLowMemory();if(mapView!=null)mapView.onLowMemory();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(mapView!=null)mapView.onSaveInstanceState(out);}
}
