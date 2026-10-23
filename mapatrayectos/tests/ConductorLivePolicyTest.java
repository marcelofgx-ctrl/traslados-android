import uy.com.mapatrayectos.conductorv0.ConductorLivePolicy;
public final class ConductorLivePolicyTest {
    static void ok(boolean value){if(!value)throw new AssertionError("live policy");}
    public static void main(String[] a) {
        long now=java.time.Instant.parse("2026-10-22T22:00:00Z").toEpochMilli();
        String fresh="2026-10-22T21:45:00+00:00",old="2026-10-21T18:00:00Z";
        ok(ConductorLivePolicy.actionableFixed("PENDIENTE","fixed","PRECIO_FIJO",840,21,fresh,now));
        ok(!ConductorLivePolicy.actionableFixed("ACEPTADA","fixed","PRECIO_FIJO",840,21,fresh,now));
        ok(!ConductorLivePolicy.actionableFixed("PENDIENTE","quote","PRECIO_FIJO",840,21,fresh,now));
        ok(!ConductorLivePolicy.actionableFixed("PENDIENTE","fixed","PRECIO_FIJO",0,21,fresh,now));
        ok(!ConductorLivePolicy.actionableFixed("PENDIENTE","fixed","PRECIO_FIJO",840,21,old,now));
        ok(!ConductorLivePolicy.actionableFixed("PENDIENTE","fixed","PRECIO_FIJO",840,21,"invalid",now));
        ok(ConductorLivePolicy.accepted("ACEPTADA") && !ConductorLivePolicy.accepted("PENDIENTE"));
        ok(ConductorLivePolicy.inJourney("EN_VIAJE") && !ConductorLivePolicy.inJourney("ACEPTADA"));
        ok(ConductorLivePolicy.safeId("4b4c4d4e-1111-2222-aaaa-efefa0ab2323"));
        ok(!ConductorLivePolicy.safeId("sample-only")&&!ConductorLivePolicy.safeId("3"));
        System.out.println("PASS real fixed request policy; stale, malformed, fake and quote-only rejected");
    }
}
