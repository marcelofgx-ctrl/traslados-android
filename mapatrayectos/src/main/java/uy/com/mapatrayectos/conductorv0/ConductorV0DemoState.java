package uy.com.mapatrayectos.conductorv0;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure JVM state machine for the SINGLE illustrative request in Conductor V0.
 * This cannot accept/reject a real trip or alter reservations, GPS, or Supabase.
 * A future authenticated backend implementation must not use this demo state.
 */
public final class ConductorV0DemoState {
    public static final String SAMPLE_ID = "sample-only";
    public enum Decision { ACCEPTED, REJECTED }

    private boolean pending = true;
    private final List<Decision> history = new ArrayList<>();

    public boolean hasPending() { return pending; }

    /** Idempotent: only a currently pending example may be decided. */
    public boolean decide(String requestId, Decision decision) {
        if (!pending || !SAMPLE_ID.equals(requestId) || decision == null) return false;
        history.add(0, decision);
        pending = false;
        return true;
    }

    /** Resets ONLY the mock request, not real booking or journey state. */
    public void resetExample() { pending = true; }

    public int newCount() { return pending ? 1 : 0; }

    public int acceptedCount() {
        int n = 0;
        for (Decision d : history) if (d == Decision.ACCEPTED) n++;
        return n;
    }

    public int inJourneyCount() { return 0; }

    public List<String> demoHistory() {
        List<String> rows = new ArrayList<>();
        for (Decision d : history) {
            rows.add(d == Decision.ACCEPTED
                    ? "Solicitud de ejemplo · Aceptada (simulación)"
                    : "Solicitud de ejemplo · Rechazada (simulación)");
        }
        return Collections.unmodifiableList(rows);
    }
}
