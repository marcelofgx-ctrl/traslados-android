package uy.com.traslados.conductor;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.HeatmapLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;

/**
 * Traslados Conductor v11.5 R1 - primera prueba del mapa de demanda.
 *
 * La capa naranja/roja usa datos SIMULADOS de Montevideo. La posición del
 * conductor sí proviene del GPS real del teléfono. Esta pantalla queda
 * deliberadamente aislada del flujo estable de solicitudes/reservas para
 * poder validarla sin tocar la lógica de v11.4 R10.4.
 */
public class DemandMapActivity extends Activity {
    private static final int REQ_LOCATION = 8801;
    private static final String STYLE_URL = "https://demotiles.maplibre.org/style.json";
    private static final String DEMAND_SOURCE = "traslados-demand-source";
    private static final String DEMAND_HEAT = "traslados-demand-heat";
    private static final String DEMAND_POINTS = "traslados-demand-points";
    private static final String DRIVER_SOURCE = "traslados-driver-source";
    private static final String DRIVER_HALO = "traslados-driver-halo";
    private static final String DRIVER_DOT = "traslados-driver-dot";

    // Centro de respaldo: eje Malvín / Carrasco. En cuanto hay GPS, se centra en el teléfono.
    private static final double FALLBACK_LAT = -34.8890;
    private static final double FALLBACK_LON = -56.0900;

    private MapView mapView;
    private MapLibreMap map;
    private Style style;
    private LocationManager locationManager;
    private Location lastLocation;
    private boolean firstGpsCenter = true;

    private final LocationListener locationListener = location -> {
        lastLocation = location;
        updateDriverPoint(location, firstGpsCenter);
        firstGpsCenter = false;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        MapLibre.getInstance(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(235, 240, 246));

        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        root.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        addTopBar(root);
        addDemandBadge(root);
        addRecenterButton(root);
        addBottomStatus(root);

        setContentView(root);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(FALLBACK_LAT, FALLBACK_LON), 11.6));
            map.setStyle(new Style.Builder().fromUri(STYLE_URL), loadedStyle -> {
                style = loadedStyle;
                addDemandLayers();
                addDriverLayers(FALLBACK_LAT, FALLBACK_LON);
                startLocation();
            });
        });
    }

    private void addDemandLayers() {
        if (style == null) return;

        GeoJsonSource demand = new GeoJsonSource(DEMAND_SOURCE, demandGeoJson());
        style.addSource(demand);

        HeatmapLayer heat = new HeatmapLayer(DEMAND_HEAT, DEMAND_SOURCE).withProperties(
                PropertyFactory.heatmapWeight(Expression.get("weight")),
                PropertyFactory.heatmapIntensity(1.15f),
                PropertyFactory.heatmapRadius(46f),
                PropertyFactory.heatmapOpacity(0.82f),
                PropertyFactory.heatmapColor(
                        Expression.interpolate(
                                Expression.linear(),
                                Expression.heatmapDensity(),
                                Expression.stop(0.00, Expression.rgba(255, 255, 255, 0.0)),
                                Expression.stop(0.18, Expression.rgba(255, 221, 153, 0.28)),
                                Expression.stop(0.38, Expression.rgba(255, 170, 77, 0.52)),
                                Expression.stop(0.62, Expression.rgba(255, 111, 46, 0.68)),
                                Expression.stop(0.82, Expression.rgba(231, 67, 42, 0.78)),
                                Expression.stop(1.00, Expression.rgba(177, 28, 35, 0.88))
                        )
                )
        );
        heat.setMaxZoom(15f);
        style.addLayer(heat);

        CircleLayer points = new CircleLayer(DEMAND_POINTS, DEMAND_SOURCE).withProperties(
                PropertyFactory.circleRadius(5.5f),
                PropertyFactory.circleColor(Color.rgb(244, 151, 0)),
                PropertyFactory.circleOpacity(0.76f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(1.6f)
        );
        points.setMinZoom(11.2f);
        style.addLayer(points);
    }

    private void addDriverLayers(double lat, double lon) {
        if (style == null) return;

        GeoJsonSource driver = new GeoJsonSource(DRIVER_SOURCE, pointGeoJson(lat, lon));
        style.addSource(driver);

        CircleLayer halo = new CircleLayer(DRIVER_HALO, DRIVER_SOURCE).withProperties(
                PropertyFactory.circleRadius(16f),
                PropertyFactory.circleColor(Color.WHITE),
                PropertyFactory.circleOpacity(0.95f),
                PropertyFactory.circleStrokeColor(Color.BLACK),
                PropertyFactory.circleStrokeWidth(2.0f)
        );
        style.addLayer(halo);

        CircleLayer dot = new CircleLayer(DRIVER_DOT, DRIVER_SOURCE).withProperties(
                PropertyFactory.circleRadius(7.5f),
                PropertyFactory.circleColor(Color.BLACK),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(1.3f)
        );
        style.addLayer(dot);
    }

    private void updateDriverPoint(Location location, boolean center) {
        if (location == null || style == null || map == null) return;
        GeoJsonSource source = style.getSourceAs(DRIVER_SOURCE);
        if (source != null) {
            source.setGeoJson(pointGeoJson(location.getLatitude(), location.getLongitude()));
        }
        if (center) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), 12.4), 700);
        }
    }

    private void recenter() {
        if (map == null) return;
        if (lastLocation != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 12.6), 600);
        } else {
            startLocation();
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(FALLBACK_LAT, FALLBACK_LON), 11.6), 500);
            toast("Buscando ubicación GPS…");
        }
    }

    private void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }

        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        if (locationManager == null) return;

        Location best = null;
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null) best = gps;
            if (net != null && (best == null || net.getTime() > best.getTime())) best = net;
        } catch (SecurityException ignored) {
        }

        if (best != null) {
            lastLocation = best;
            updateDriverPoint(best, firstGpsCenter);
            firstGpsCenter = false;
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 2500L, 5f,
                        locationListener, Looper.getMainLooper());
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 3500L, 10f,
                        locationListener, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {
        } catch (Exception e) {
            toast("No se pudo iniciar GPS");
        }
    }

    private void stopLocation() {
        if (locationManager == null) return;
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocation();
            } else {
                toast("El mapa funciona sin GPS, pero no puede mostrar tu posición");
            }
        }
    }

    private void addTopBar(FrameLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);

        Button back = circleButton("‹");
        back.setTextSize(30);
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView earnings = new TextView(this);
        earnings.setText("UYU 0.00");
        earnings.setTextColor(Color.WHITE);
        earnings.setTextSize(24);
        earnings.setGravity(Gravity.CENTER);
        earnings.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        earnings.setBackground(rounded(Color.BLACK, 34, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(58), 1f);
        ep.setMargins(dp(18), 0, dp(18), 0);
        row.addView(earnings, ep);

        TextView gps = new TextView(this);
        gps.setText("GPS");
        gps.setTextColor(Color.rgb(25, 25, 25));
        gps.setTextSize(13);
        gps.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        gps.setGravity(Gravity.CENTER);
        gps.setBackground(rounded(Color.WHITE, 29, 1, Color.rgb(220, 224, 230)));
        row.addView(gps, new LinearLayout.LayoutParams(dp(58), dp(58)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(66), Gravity.TOP);
        lp.setMargins(0, dp(14), 0, 0);
        root.addView(row, lp);
    }

    private void addDemandBadge(FrameLayout root) {
        TextView badge = new TextView(this);
        badge.setText("  DEMANDA SIMULADA  ");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Color.rgb(235, 104, 27), 18, 0, Color.TRANSPARENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(150), dp(36), Gravity.TOP | Gravity.LEFT);
        lp.setMargins(dp(16), dp(96), 0, 0);
        root.addView(badge, lp);
    }

    private void addRecenterButton(FrameLayout root) {
        Button recenter = circleButton("⌖");
        recenter.setTextSize(28);
        recenter.setTextColor(Color.rgb(30, 88, 180));
        recenter.setOnClickListener(v -> recenter());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.RIGHT | Gravity.BOTTOM);
        lp.setMargins(0, 0, dp(18), dp(126));
        root.addView(recenter, lp);
    }

    private void addBottomStatus(FrameLayout root) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(22), dp(15), dp(22), dp(14));
        panel.setBackground(rounded(Color.WHITE, 24, 1, Color.rgb(232, 235, 240)));

        TextView title = new TextView(this);
        title.setText("Estás conectado");
        title.setTextColor(Color.rgb(18, 18, 20));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        TextView sub = new TextView(this);
        sub.setText("Mapa beta · GPS real · zonas de demanda de demostración");
        sub.setTextColor(Color.rgb(92, 99, 108));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        panel.addView(sub, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(96), Gravity.BOTTOM);
        lp.setMargins(0, 0, 0, 0);
        root.addView(panel, lp);
    }

    private Button circleButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(22);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(rounded(Color.WHITE, 30, 1, Color.rgb(220, 224, 230)));
        b.setElevation(dp(5));
        return b;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) gd.setStroke(dp(strokeDp), strokeColor);
        return gd;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String pointGeoJson(double lat, double lon) {
        return "{\"type\":\"FeatureCollection\",\"features\":[{" +
                "\"type\":\"Feature\",\"properties\":{},\"geometry\":{" +
                "\"type\":\"Point\",\"coordinates\":[" + lon + "," + lat + "]}}]}";
    }

    private String demandGeoJson() {
        return "{\"type\":\"FeatureCollection\",\"features\":[" +
                feature(-56.034, -34.859, 0.82) + "," + // Paso Carrasco
                feature(-56.055, -34.883, 1.00) + "," + // Carrasco
                feature(-56.073, -34.872, 0.92) + "," + // Cruz de Carrasco
                feature(-56.104, -34.893, 0.78) + "," + // Malvín
                feature(-56.126, -34.899, 0.68) + "," + // Buceo
                feature(-56.147, -34.909, 0.88) + "," + // Pocitos
                feature(-56.161, -34.922, 0.62) + "," + // Punta Carretas
                feature(-56.171, -34.895, 0.72) + "," + // Tres Cruces
                feature(-56.188, -34.906, 0.84) + "," + // Centro
                feature(-56.152, -34.884, 0.55) +
                "]}";
    }

    private String feature(double lon, double lat, double weight) {
        return "{\"type\":\"Feature\",\"properties\":{\"weight\":" + weight +
                "},\"geometry\":{\"type\":\"Point\",\"coordinates\":[" + lon + "," + lat + "]}}";
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); if (style != null) startLocation(); }
    @Override protected void onPause() { stopLocation(); mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onDestroy() { stopLocation(); mapView.onDestroy(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
}
