package uy.com.mapatrayectos;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reglas de calidad para telemetria. La prioridad es no convertir un salto aislado
 * de GPS/red en distancia, velocidad maxima o un recorrido falso.
 */
public final class TelemetryQuality {
    private static final double MAX_ROUTE_ACCURACY_M = 45.0;
    private static final double MAX_SPEED_ACCURACY_M = 25.0;
    private static final double FALLBACK_SPEED_ACCURACY_M = 15.0;
    private static final double MAX_PLAUSIBLE_KMH = 160.0;
    private static final double MAX_ACCEL_MPS2 = 7.0;

    private TelemetryQuality() {}

    public static JSONArray cleanRoute(JSONArray input) {
        JSONArray out = new JSONArray();
        JSONObject previous = null;
        long previousTs = 0L;

        for (int i = 0; i < input.length(); i++) {
            JSONObject p = input.optJSONObject(i);
            if (p == null) continue;
            double lat = p.optDouble("lat", Double.NaN);
            double lon = p.optDouble("lon", Double.NaN);
            double accuracy = p.optDouble("accuracy_m", 999.0);
            long ts = p.optLong("recorded_at_ms", 0L);
            if (Double.isNaN(lat) || Double.isNaN(lon) || accuracy > MAX_ROUTE_ACCURACY_M || ts <= 0L) continue;

            if (previous != null) {
                long dt = ts - previousTs;
                if (dt <= 0L) continue;
                float[] d = new float[1];
                Location.distanceBetween(previous.optDouble("lat"), previous.optDouble("lon"), lat, lon, d);
                double derived = d[0] / (dt / 1000.0) * 3.6;
                if (derived > MAX_PLAUSIBLE_KMH) continue;
                if (dt < 800L && derived > 60.0) continue;
            }

            out.put(p);
            previous = p;
            previousTs = ts;
        }
        return out;
    }

    /**
     * Maxima confirmada: un pico debe ser coherente con al menos una lectura vecina
     * de buena precision y con una aceleracion fisicamente razonable.
     */
    public static double confirmedMaxKmh(JSONArray input) {
        JSONArray a = cleanRoute(input);
        double confirmed = 0.0;
        double bestVeryAccurate = 0.0;

        for (int i = 0; i < a.length(); i++) {
            JSONObject cur = a.optJSONObject(i);
            if (cur == null) continue;
            double speed = cur.optDouble("speed_kmh", 0.0);
            double accuracy = cur.optDouble("accuracy_m", 999.0);
            if (speed < 0.0 || speed > MAX_PLAUSIBLE_KMH) continue;
            if (accuracy <= FALLBACK_SPEED_ACCURACY_M) bestVeryAccurate = Math.max(bestVeryAccurate, speed);
            if (accuracy > MAX_SPEED_ACCURACY_M || speed < 1.0) continue;

            if (neighborConfirms(a, i, i - 1, speed) || neighborConfirms(a, i, i + 1, speed)) {
                confirmed = Math.max(confirmed, speed);
            }
        }
        return confirmed > 0.0 ? confirmed : bestVeryAccurate;
    }

    private static boolean neighborConfirms(JSONArray a, int i, int j, double speed) {
        if (j < 0 || j >= a.length()) return false;
        JSONObject cur = a.optJSONObject(i), n = a.optJSONObject(j);
        if (cur == null || n == null) return false;
        double ns = n.optDouble("speed_kmh", 0.0);
        double na = n.optDouble("accuracy_m", 999.0);
        if (na > MAX_SPEED_ACCURACY_M || ns < 0.0 || ns > MAX_PLAUSIBLE_KMH) return false;
        long dtMs = Math.abs(cur.optLong("recorded_at_ms", 0L) - n.optLong("recorded_at_ms", 0L));
        if (dtMs < 500L || dtMs > 8000L) return false;
        double tolerance = Math.max(8.0, Math.max(speed, ns) * 0.25);
        double delta = Math.abs(speed - ns);
        double accelMps2 = (delta / 3.6) / (dtMs / 1000.0);
        return delta <= tolerance && accelMps2 <= MAX_ACCEL_MPS2;
    }

    /**
     * Corrige historicos locales que quedaron con una maxima aislada falsa.
     * Solo baja una maxima cuando los puntos del propio viaje la contradicen;
     * nunca inventa una maxima superior a la guardada.
     */
    public static int repairHistoricalMaxima(Context context) {
        TrackDb db = new TrackDb(context);
        int repaired = 0;
        try {
            JSONArray trips = db.listTrips();
            for (int i = 0; i < trips.length(); i++) {
                JSONObject t = trips.optJSONObject(i);
                if (t == null || t.optLong("ended_at_ms", 0L) <= 0L) continue;
                String tripId = t.optString("trip_id", "");
                if (tripId.isEmpty()) continue;
                double stored = t.optDouble("max_speed_kmh", 0.0);
                double robust = confirmedMaxKmh(db.getTripPoints(tripId));
                if (robust <= 0.0) continue;
                if (stored <= 0.0 || stored - robust > 5.0) {
                    db.updateTrip(
                        tripId,
                        t.optLong("ended_at_ms", 0L),
                        t.optDouble("distance_m", 0.0),
                        t.optLong("moving_ms", 0L),
                        t.optLong("stopped_ms", 0L),
                        robust,
                        t.optDouble("avg_speed_kmh", 0.0),
                        t.optString("end_zone", "")
                    );
                    repaired++;
                }
            }
        } finally {
            db.close();
        }
        return repaired;
    }
}
