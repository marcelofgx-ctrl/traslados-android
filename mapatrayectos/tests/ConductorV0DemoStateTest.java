import uy.com.mapatrayectos.conductorv0.ConductorV0DemoState;

public final class ConductorV0DemoStateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ConductorV0DemoState m = new ConductorV0DemoState();
        check(m.hasPending() && m.newCount() == 1 && m.acceptedCount() == 0, "initial demo");
        check(!m.decide("real-booking-id", ConductorV0DemoState.Decision.ACCEPTED), "reject real ids");
        check(!m.decide(null, ConductorV0DemoState.Decision.ACCEPTED), "reject null ids");
        check(m.decide(ConductorV0DemoState.SAMPLE_ID, ConductorV0DemoState.Decision.ACCEPTED), "accept");
        check(!m.hasPending() && m.newCount() == 0 && m.acceptedCount() == 1, "accepted counters");
        check(!m.decide(ConductorV0DemoState.SAMPLE_ID, ConductorV0DemoState.Decision.REJECTED), "no double action");
        check(m.demoHistory().size() == 1 && m.demoHistory().get(0).contains("Aceptada"), "accepted history");
        m.resetExample();
        check(m.hasPending() && m.newCount() == 1, "reset");
        check(m.decide(ConductorV0DemoState.SAMPLE_ID, ConductorV0DemoState.Decision.REJECTED), "reject");
        check(m.acceptedCount() == 1 && m.inJourneyCount() == 0, "no fake journey");
        check(m.demoHistory().size() == 2 && m.demoHistory().get(0).contains("Rechazada"), "rejection history");
        try {
            m.demoHistory().add("mutate");
            throw new AssertionError("history should be read-only");
        } catch (UnsupportedOperationException expected) { }
        System.out.println("PASS Conductor V0 local demo decisions: no backend effects");
    }
}
