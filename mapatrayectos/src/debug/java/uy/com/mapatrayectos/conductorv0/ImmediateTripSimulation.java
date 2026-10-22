package uy.com.mapatrayectos.conductorv0;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEBUG SOURCE SET ONLY. In-memory fake of driver_list_reservations_v3 and
 * driver_set_status_v2. No Android service, stored credentials or HTTP code.
 * This cannot enter release builds because its source set is src/debug.
 */
public final class ImmediateTripSimulation {
    public static final class Result {
        public final boolean ok;
        public final String reason;
        Result(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }
    }

    private final LinkedHashMap<String, ImmediateTripRequest> rows =
            new LinkedHashMap<>();
    private int mutationCount;
    private boolean failNextMutation;

    public void addWireRow(Map<String, ?> wire) {
        ImmediateTripRequest row = ImmediateTripRequest.fromWire(wire);
        if (row != null && !row.id.isEmpty()) rows.put(row.id, row);
    }
    public List<ImmediateTripRequest> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(rows.values()));
    }

    public ImmediateTripRequest firstPending() {
        for (ImmediateTripRequest row : rows.values()) {
            if (row.canDecide()) return row;
        }
        return null;
    }

    public int countNew() {return count(ImmediateTripRequest.PENDING);}
    public int countAccepted() {return count(ImmediateTripRequest.ACCEPTED);}
    public int countInJourney() {return count("EN_VIAJE");}

    private int count(String status) {
        int n = 0;
        for (ImmediateTripRequest row : rows.values()) {
            if (row.isDirectFixed() && status.equals(row.status)) n++;
        }
        return n;
    }

    /** Local fake mutation only: does not construct an RPC, PIN or request. */
    public Result decide(String id, String desiredStatus) {
        if (!ImmediateTripRequest.ACCEPTED.equals(desiredStatus)
                && !ImmediateTripRequest.REJECTED.equals(desiredStatus)) {
            return new Result(false, "UNSUPPORTED_STATUS");
        }
        ImmediateTripRequest current = rows.get(id);
        if (current == null) return new Result(false, "NOT_FOUND");
        if (!current.canDecide()) return new Result(false, "STALE_OR_NOT_IMMEDIATE");
        if (failNextMutation) {
            failNextMutation = false;
            return new Result(false, "SIMULATED_ERROR");
        }
        rows.put(id, current.withStatus(desiredStatus));
        mutationCount++;
        return new Result(true, "SIMULATED_ONLY");
    }

    public int mutationCount() {return mutationCount;}
    public void failNextMutation() {failNextMutation = true;}

    public static Map<String, Object> example(String id, String status,
            String pickupMode, String quoteStatus, double amount,
            String origin, String destination, double km, int minutes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("status", status);
        row.put("pickup_mode", pickupMode);
        row.put("quote_status", quoteStatus);
        row.put("quote_final_total", amount);
        row.put("origin_text", origin);
        row.put("destination_text", destination);
        row.put("route_distance_km", km);
        row.put("route_duration_min", minutes);
        return row;
    }

    /** Purely illustrative demo fixture; never loaded from a server. */
    public static ImmediateTripSimulation sample() {
        ImmediateTripSimulation fake = new ImmediateTripSimulation();
        fake.addWireRow(example("00000000-0000-0000-0000-000000000001",
                "PENDIENTE", "now", "PRECIO_FIJO", 840,
                "18 de Julio, Montevideo", "Aeropuerto de Carrasco", 21, 30));
        fake.addWireRow(example("00000000-0000-0000-0000-000000000002",
                "PENDIENTE", "20", "PRECIO_FIJO", 400,
                "Plaza Independencia", "Tres Cruces", 10, 18));
        fake.addWireRow(example("00000000-0000-0000-0000-000000000003",
                "ACEPTADA", "now", "PRECIO_FIJO", 300,
                "Centro", "Parque Rodó", 7, 14));
        // Programmed quote must NOT contaminate direct counters or decisions.
        fake.addWireRow(example("00000000-0000-0000-0000-000000000004",
                "PENDIENTE", "scheduled", "ENVIADO", 1200,
                "Canelones", "Colonia", 130, 110));
        return fake;
    }
}
