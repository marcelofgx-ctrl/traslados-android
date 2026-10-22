package uy.com.mapatrayectos.conductorv0;

import java.util.Locale;
import java.util.Map;

/**
 * Read-only projection of existing driver_list_reservations_v3 fields.
 * No access to PINs, sessions, RPCs, location or Android services.
 */
public final class ImmediateTripRequest {
    public static final String READ_CONTRACT = "driver_list_reservations_v3";
    public static final String WRITE_CONTRACT = "driver_set_status_v2";
    public static final String ACCEPTED = "ACEPTADA";
    public static final String REJECTED = "RECHAZADA";
    public static final String PENDING = "PENDIENTE";

    public final String id, status, quoteStatus, pickupMode;
    public final String origin, destination;
    public final double amountUyu, routeKm;
    public final int durationMinutes;

    private ImmediateTripRequest(String id, String status, String quoteStatus,
            String pickupMode, String origin, String destination,
            double amountUyu, double routeKm, int durationMinutes) {
        this.id = clean(id);
        this.status = clean(status);
        this.quoteStatus = clean(quoteStatus);
        this.pickupMode = clean(pickupMode);
        this.origin = clean(origin);
        this.destination = clean(destination);
        this.amountUyu = amountUyu;
        this.routeKm = routeKm;
        this.durationMinutes = durationMinutes;
    }

    /** Same wire names and eligibility as existing Conductor direct fixed-price rule. */
    public static ImmediateTripRequest fromWire(Map<String, ?> wire) {
        if (wire == null) return null;
        return new ImmediateTripRequest(
            string(wire.get("id")), string(wire.get("status")),
            string(wire.get("quote_status")), string(wire.get("pickup_mode")),
            string(wire.get("origin_text")), string(wire.get("destination_text")),
            number(wire.get("quote_final_total")),
            number(wire.get("route_distance_km")),
            integer(wire.get("route_duration_min")));
    }

    /** Distinguish direct fixed-price trips from programmed/quoted trips. */
    public boolean isDirectFixed() {
        return !id.isEmpty() && !origin.isEmpty() && !destination.isEmpty()
                && "PRECIO_FIJO".equals(quoteStatus)
                && Double.isFinite(amountUyu) && amountUyu > 0
                && isImmediateMode(pickupMode);
    }

    public boolean canDecide() {
        return isDirectFixed() && PENDING.equals(status);
    }

    public ImmediateTripRequest withStatus(String replacement) {
        return new ImmediateTripRequest(id, replacement, quoteStatus, pickupMode,
                origin, destination, amountUyu, routeKm, durationMinutes);
    }

    public String distanceText() {
        return Double.isFinite(routeKm) && routeKm > 0
                ? String.format(Locale.ROOT, "%.1f km", routeKm) : "—";
    }
    public String durationText() {
        return durationMinutes > 0 ? durationMinutes + " min" : "—";
    }
    public String amountText() {
        return String.format(Locale.ROOT, "$%.0f", amountUyu);
    }

    public static boolean isImmediateMode(String raw) {
        String mode = clean(raw);
        if ("now".equals(mode)) return true;
        if (!mode.matches("^[1-9][0-9]{0,2}$")) return false;
        try {
            int minutes = Integer.parseInt(mode);
            return minutes >= 1 && minutes <= 120;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String clean(String x) {
        return x == null ? "" : x.trim();
    }
    private static String string(Object x) {
        return x == null ? "" : String.valueOf(x).trim();
    }
    private static double number(Object value) {
        if (value instanceof Number) return ((Number)value).doubleValue();
        if (value == null) return Double.NaN;
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ex) { return Double.NaN; }
    }
    private static int integer(Object value) {
        double d = number(value);
        return Double.isFinite(d) && d > 0 && d <= Integer.MAX_VALUE ? (int)d : 0;
    }
}
