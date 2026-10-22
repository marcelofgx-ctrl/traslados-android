import uy.com.mapatrayectos.conductorv0.ImmediateTripRequest;
import uy.com.mapatrayectos.conductorv0.ImmediateTripSimulation;
import java.util.Map;

/** Pure Java regression suite: no Android runtime, PIN or real network calls. */
public final class ConductorV0ImmediateSimulationTest {
    private static int assertions;
    private static void check(boolean okay, String label) {
        assertions++;
        if (!okay) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        ImmediateTripSimulation fake = ImmediateTripSimulation.sample();
        check(fake.countNew() == 2, "two immediate requests");
        check(fake.countAccepted() == 1, "one previously accepted");
        check(fake.countInJourney() == 0, "zero in-journey");
        check(fake.snapshot().size() == 4, "programmed kept separate");
        ImmediateTripRequest first = fake.firstPending();
        check(first != null, "pending exists");
        check(first.canDecide(), "only valid direct");
        check(first.origin.equals("18 de Julio, Montevideo"), "wire origin");
        check(first.destination.equals("Aeropuerto de Carrasco"), "wire dest");
        check(first.distanceText().equals("21.0 km"), "wire distance");
        check(first.durationText().equals("30 min"), "wire duration");
        check(first.amountText().equals("$840"), "fixed price");
        check(ImmediateTripRequest.READ_CONTRACT.equals("driver_list_reservations_v3"), "list contract");
        check(ImmediateTripRequest.WRITE_CONTRACT.equals("driver_set_status_v2"), "status contract");

        String firstId = first.id;
        check(!fake.decide(firstId, "EN_VIAJE").ok, "no unauthorized stage");
        check(fake.mutationCount() == 0, "no unsupported mutation");
        fake.failNextMutation();
        check(!fake.decide(firstId, "ACEPTADA").ok, "simulated server failure");
        check(fake.countNew() == 2, "no optimistic state after failure");
        check(fake.mutationCount() == 0, "failure not written");
        check(fake.decide(firstId, "ACEPTADA").ok, "fake accept");
        check(fake.countNew() == 1, "accepted removed from pending");
        check(fake.countAccepted() == 2, "accepted counted");
        check(!fake.decide(firstId, "ACEPTADA").ok, "repeat cannot mutate");
        check(!fake.decide(firstId, "RECHAZADA").ok, "stale cannot reject");
        check(fake.mutationCount() == 1, "only one accepted mutation");
        String second = fake.firstPending().id;
        check(fake.decide(second, "RECHAZADA").ok, "fake reject");
        check(fake.countNew() == 0, "no new immediate requests");
        check(fake.countAccepted() == 2, "accepted count stable");
        check(fake.mutationCount() == 2, "exactly two fake changes");
        check(!fake.decide("missing", "RECHAZADA").ok, "missing id rejected");

        Map<String, Object> scheduled = ImmediateTripSimulation.example(
            "00000000-0000-0000-0000-000000000008", "PENDIENTE",
            "scheduled", "ENVIADO", 1500, "A", "B", 22, 40);
        fake.addWireRow(scheduled);
        check(!fake.decide(String.valueOf(scheduled.get("id")), "ACEPTADA").ok,
            "programmed excluded");
        check(fake.countNew() == 0, "scheduled never counted");
        Map<String, Object> missingPrice = ImmediateTripSimulation.example(
            "00000000-0000-0000-0000-000000000009", "PENDIENTE",
            "now", "PRECIO_FIJO", 0, "A", "B", 8, 10);
        fake.addWireRow(missingPrice);
        check(!fake.decide(String.valueOf(missingPrice.get("id")), "ACEPTADA").ok,
            "zero price not actionable");
        check(!ImmediateTripRequest.isImmediateMode("121"), "only max 120");
        check(ImmediateTripRequest.isImmediateMode("120"), "120 allowed");
        check(ImmediateTripRequest.isImmediateMode("now"), "now allowed");
        check(!ImmediateTripRequest.isImmediateMode("foo"), "unknown mode blocked");
        check(!ImmediateTripRequest.isImmediateMode("0"), "zero blocked");
        check(!ImmediateTripRequest.isImmediateMode("-1"), "negative blocked");
        System.out.println("PASS "+assertions+" tests: immediate mock-only contract.");
    }
}
