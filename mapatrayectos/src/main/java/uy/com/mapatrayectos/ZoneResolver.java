package uy.com.mapatrayectos;

import java.util.Locale;

public final class ZoneResolver {
    private ZoneResolver() {}

    private static final Object[][] ZONES = new Object[][]{
            {"Centro", -34.9057, -56.1916},
            {"Ciudad Vieja", -34.9060, -56.2063},
            {"Cordón", -34.9013, -56.1786},
            {"Tres Cruces", -34.8935, -56.1668},
            {"Parque Batlle", -34.8949, -56.1530},
            {"La Blanqueada", -34.8870, -56.1550},
            {"Pocitos", -34.9095, -56.1515},
            {"Punta Carretas", -34.9220, -56.1608},
            {"Buceo", -34.9000, -56.1330},
            {"Malvín", -34.8932, -56.1060},
            {"Punta Gorda", -34.8950, -56.0830},
            {"Carrasco", -34.8857, -56.0578},
            {"La Cruz de Carrasco", -34.8723, -56.0730},
            {"Maroñas", -34.8538, -56.1250},
            {"Prado", -34.8610, -56.2050},
            {"Aguada", -34.8893, -56.1904},
            {"Paso Carrasco", -34.8596, -56.0345},
            {"Colonia Nicolich", -34.8155, -56.0330},
            {"Shangrilá", -34.8530, -55.9965},
            {"San José de Carrasco", -34.8445, -55.9745},
            {"Lagomar", -34.8375, -55.9505},
            {"Solymar", -34.8235, -55.9280},
            {"Lomas de Solymar", -34.8130, -55.9040},
            {"El Pinar", -34.7978, -55.8880},
            {"Neptunia", -34.7840, -55.8680},
            {"Pinamar", -34.7810, -55.8550},
            {"Salinas", -34.7740, -55.8400},
            {"Atlántida", -34.7715, -55.7580},
            {"Barros Blancos", -34.7520, -56.0020},
            {"Pando", -34.7170, -55.9580},
            {"Aeropuerto", -34.8356, -56.0308}
    };

    public static String resolve(double lat, double lon) {
        String best = "";
        double bestKm = Double.MAX_VALUE;
        for (Object[] z : ZONES) {
            double km = distanceKm(lat, lon, (double) z[1], (double) z[2]);
            if (km < bestKm) {
                bestKm = km;
                best = (String) z[0];
            }
        }
        if (bestKm <= 7.5) return best;
        return String.format(Locale.US, "%.4f, %.4f", lat, lon);
    }

    private static double distanceKm(double aLat, double aLon, double bLat, double bLon) {
        double r = 6371.0;
        double dLat = Math.toRadians(bLat - aLat);
        double dLon = Math.toRadians(bLon - aLon);
        double s1 = Math.sin(dLat / 2.0);
        double s2 = Math.sin(dLon / 2.0);
        double q = s1 * s1 + Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) * s2 * s2;
        return 2.0 * r * Math.atan2(Math.sqrt(q), Math.sqrt(1.0 - q));
    }
}
