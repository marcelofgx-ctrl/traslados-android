package uy.com.mapatrayectos;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
    private static final int BG=Color.rgb(7,25,31),GOLD=Color.rgb(224,193,111),TEXT=Color.rgb(245,244,238),MUTED=Color.rgb(174,188,191),GREEN=Color.rgb(54,190,125),RED=Color.rgb(225,78,84),ROUTE=Color.rgb(73,199,225);
    private MapView mapView;

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);MapLibre.getInstance(this);String tripId=getIntent().getStringExtra("trip_id");if(tripId==null||tripId.isEmpty()){Toast.makeText(this,"No se pudo identificar el viaje.",Toast.LENGTH_LONG).show();finish();return;}TrackDb db=new TrackDb(this);JSONObject trip=db.getTrip(tripId);JSONArray points=TelemetryQuality.cleanRoute(db.getTripPoints(tripId));db.close();if(trip==null){Toast.makeText(this,"El viaje ya no está disponible en el historial local.",Toast.LENGTH_LONG).show();finish();return;}build(b,trip,points);}

    private void build(Bundle b,JSONObject trip,JSONArray points){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);mapView=new MapView(this);mapView.onCreate(b);root.addView(mapView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),0,dp(12),0);top.setBackground(rounded(Color.argb(240,7,25,31),18,0,0));Button back=new Button(this);back.setText("‹");back.setTextSize(28);back.setTextColor(TEXT);back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(55),dp(55)));TextView t=text("RECORRIDO DEL VIAJE",18,TEXT,true);top.addView(t,new LinearLayout.LayoutParams(0,-2,1));FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP);tlp.setMargins(dp(8),dp(8),dp(8),0);root.addView(top,tlp);

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_HORIZONTAL);info.setPadding(dp(16),dp(12),dp(16),dp(12));info.setBackground(rounded(Color.argb(247,7,25,31),24,1,Color.rgb(42,82,91)));
        String from=trip==null?"":trip.optString("start_zone",""),to=trip==null?"":trip.optString("end_zone","");String type=trip==null?"other":trip.optString("trip_type","other"),status=trip==null?"completed":trip.optString("trip_status","completed");double amount=trip==null||trip.isNull("amount_uyu")?0:trip.optDouble("amount_uyu",0);
        TextView tag=text(typeLabel(type)+("cancelled".equals(status)?" · CANCELADO":" · COMPLETADO"),12,"cancelled".equals(status)?RED:GREEN,true);tag.setGravity(Gravity.CENTER);info.addView(tag);
        TextView route=text((from.isEmpty()?"Origen":from)+"  →  "+(to.isEmpty()?"Destino":to),18,GOLD,true);route.setGravity(Gravity.CENTER);info.addView(route,lp(5,0));
        double km=trip==null?0:trip.optDouble("distance_m",0)/1000.0,avg=trip==null?0:trip.optDouble("avg_speed_kmh",0),storedMax=trip==null?0:trip.optDouble("max_speed_kmh",0);double robustMax=TelemetryQuality.confirmedMaxKmh(points);double max=robustMax>0?robustMax:storedMax;long moving=trip==null?0:trip.optLong("moving_ms",0),stopped=trip==null?0:trip.optLong("stopped_ms",0),start=trip==null?0:trip.optLong("started_at_ms",0),end=trip==null?0:trip.optLong("ended_at_ms",0);
        TextView mainStats=text(String.format(Locale.getDefault(),"%.1f km   ·   %s",km,duration(end>start?end-start:moving+stopped)),14,TEXT,true);mainStats.setGravity(Gravity.CENTER);info.addView(mainStats,lp(6,0));
        TextView speeds=text(String.format(Locale.getDefault(),"Vel. prom. %.0f km/h   ·   Vel. máx. %.0f km/h",avg,max),13,GOLD,true);speeds.setGravity(Gravity.CENTER);info.addView(speeds,lp(5,0));
        if(amount>0){TextView fare=text(String.format(Locale.getDefault(),"Importe: $ %.0f UYU",amount),14,GREEN,true);fare.setGravity(Gravity.CENTER);info.addView(fare,lp(5,0));}
        TextView ms=text("Movimiento "+duration(moving)+"   ·   Detenido "+duration(stopped),12,MUTED,false);ms.setGravity(Gravity.CENTER);info.addView(ms,lp(5,0));
        if(start>0){TextView when=text(new SimpleDateFormat("EEEE d 'de' MMMM · HH:mm",new Locale("es","UY")).format(new Date(start)),11,MUTED,false);when.setGravity(Gravity.CENTER);info.addView(when,lp(5,0));}
        FrameLayout.LayoutParams ilp=new FrameLayout.LayoutParams(-1,dp(amount>0?196:178),Gravity.BOTTOM);ilp.setMargins(dp(8),0,dp(8),dp(8));root.addView(info,ilp);

        root.setOnApplyWindowInsetsListener((v,insets)->{int topInset=insets.getSystemWindowInsetTop(),bottomInset=insets.getSystemWindowInsetBottom();FrameLayout.LayoutParams a=(FrameLayout.LayoutParams)top.getLayoutParams();a.topMargin=topInset+dp(6);top.setLayoutParams(a);FrameLayout.LayoutParams z=(FrameLayout.LayoutParams)info.getLayoutParams();z.bottomMargin=bottomInset+dp(8);info.setLayoutParams(z);return insets;});root.requestApplyInsets();
        setContentView(root);

        final String routeJson=lineGeoJson(points);final double[] camera=cameraFor(points);
        mapView.getMapAsync(map->{map.setStyle(new Style.Builder().fromUri(STYLE_URL),style->{
            GeoJsonSource src=new GeoJsonSource("history-route",routeJson);style.addSource(src);
            style.addLayer(new LineLayer("history-casing","history-route").withProperties(PropertyFactory.lineColor(BG),PropertyFactory.lineWidth(11f),PropertyFactory.lineOpacity(0.80f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));style.addLayer(new LineLayer("history-glow","history-route").withProperties(PropertyFactory.lineColor(ROUTE),PropertyFactory.lineWidth(8.6f),PropertyFactory.lineOpacity(0.28f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));style.addLayer(new LineLayer("history-line","history-route").withProperties(PropertyFactory.lineColor(ROUTE),PropertyFactory.lineWidth(5.8f),PropertyFactory.lineOpacity(0.99f),PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));if(points.length()>0){JSONObject first=points.optJSONObject(0),last=points.optJSONObject(points.length()-1);GeoJsonSource s1=new GeoJsonSource("start",point(first));GeoJsonSource s2=new GeoJsonSource("end",point(last));style.addSource(s1);style.addSource(s2);style.addLayer(new CircleLayer("start-halo","start").withProperties(PropertyFactory.circleRadius(10f),PropertyFactory.circleColor(Color.WHITE),PropertyFactory.circleOpacity(0.88f)));style.addLayer(new CircleLayer("start-dot","start").withProperties(PropertyFactory.circleRadius(6.5f),PropertyFactory.circleColor(GREEN),PropertyFactory.circleStrokeColor(BG),PropertyFactory.circleStrokeWidth(1.3f)));style.addLayer(new CircleLayer("end-halo","end").withProperties(PropertyFactory.circleRadius(10f),PropertyFactory.circleColor(Color.WHITE),PropertyFactory.circleOpacity(0.88f)));style.addLayer(new CircleLayer("end-dot","end").withProperties(PropertyFactory.circleRadius(6.5f),PropertyFactory.circleColor(RED),PropertyFactory.circleStrokeColor(BG),PropertyFactory.circleStrokeWidth(1.3f)));}
            CameraPosition cp=new CameraPosition.Builder().target(new LatLng(camera[0],camera[1])).zoom(camera[2]).tilt(0).bearing(0).build();map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cp));
        });});
    }

    private String typeLabel(String s){if("uber".equals(s))return "UBER";if("cabify".equals(s))return "CABIFY";if("personal".equals(s))return "PERSONAL";return "OTRO";}
    private String point(JSONObject o){if(o==null)return "{\"type\":\"FeatureCollection\",\"features\":[]}";return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":["+o.optDouble("lon")+","+o.optDouble("lat")+"]}}]}";}
    private String lineGeoJson(JSONArray a){if(a==null||a.length()<2)return "{\"type\":\"FeatureCollection\",\"features\":[]}";StringBuilder sb=new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");boolean first=true;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;if(!first)sb.append(',');first=false;sb.append('[').append(o.optDouble("lon")).append(',').append(o.optDouble("lat")).append(']');}return sb.append("]}}]}").toString();}
    private double[] cameraFor(JSONArray a){if(a.length()==0)return new double[]{-34.88,-56.08,11.6};double minLat=90,maxLat=-90,minLon=180,maxLon=-180;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;double lat=o.optDouble("lat"),lon=o.optDouble("lon");minLat=Math.min(minLat,lat);maxLat=Math.max(maxLat,lat);minLon=Math.min(minLon,lon);maxLon=Math.max(maxLon,lon);}double span=Math.max(maxLat-minLat,maxLon-minLon);double z=span<.002?16.0:span<.006?14.8:span<.015?13.8:span<.035?12.8:span<.08?11.8:10.8;return new double[]{(minLat+maxLat)/2,(minLon+maxLon)/2,z};}
    private LinearLayout.LayoutParams lp(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,dp(bottom));return p;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable rounded(int fill,int radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String duration(long ms){long x=Math.max(0,ms)/1000,h=x/3600,m=(x%3600)/60,s=x%60;return h>0?String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,s):String.format(Locale.getDefault(),"%02d:%02d",m,s);}
    @Override protected void onStart(){super.onStart();mapView.onStart();}@Override protected void onResume(){super.onResume();mapView.onResume();}@Override protected void onPause(){mapView.onPause();super.onPause();}@Override protected void onStop(){mapView.onStop();super.onStop();}@Override protected void onDestroy(){mapView.onDestroy();super.onDestroy();}@Override public void onLowMemory(){super.onLowMemory();mapView.onLowMemory();}@Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);mapView.onSaveInstanceState(out);}
}
