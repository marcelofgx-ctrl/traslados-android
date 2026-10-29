package uy.com.mapatrayectos;

/** Java 17 offline QA: no Android runtime, no network, no private GPS data. */
public final class PrivateGpsDiagnosticTest {
    private static int passed=0;
    private static void expect(String label, PrivateGpsDiagnostic.Code actual,
                               PrivateGpsDiagnostic.Code expected) {
        if(actual!=expected)throw new AssertionError(label+": "+actual+" != "+expected);
        passed++;
        System.out.println("PASS "+label);
    }
    private static void rpc(String label, boolean ok, String raw,
                            PrivateGpsDiagnostic.Code code) {
        PrivateGpsDiagnostic.recordRpcResult(ok,raw);
        expect(label,PrivateGpsDiagnostic.lastCode(),code);
    }
    public static void main(String[] args) {
        expect("initial in memory",PrivateGpsDiagnostic.lastCode(),
            PrivateGpsDiagnostic.Code.NO_ATTEMPT);
        rpc("server acknowledged",true,"",PrivateGpsDiagnostic.Code.ACKNOWLEDGED);
        rpc("ambiguous active reservations",false,"no_unique_active_reservation",
            PrivateGpsDiagnostic.Code.NO_UNIQUE_RESERVATION);
        rpc("device unauthorized",false,"unauthorized",
            PrivateGpsDiagnostic.Code.UNAUTHORIZED);
        rpc("other unauthorized",false,"not_authorized",
            PrivateGpsDiagnostic.Code.UNAUTHORIZED);
        rpc("GPS unfit",false,"gps_unavailable",
            PrivateGpsDiagnostic.Code.GPS_UNAVAILABLE);
        rpc("stale GPS",false,"stale_gps",PrivateGpsDiagnostic.Code.STALE_GPS);
        rpc("unknown server response never echoed",false,
            "secret=do-not-echo; lat=-99; device=not-real",
            PrivateGpsDiagnostic.Code.SERVER_REJECTED);
        if(PrivateGpsDiagnostic.message(true).contains("do-not-echo"))
            throw new AssertionError("Raw server reason leaked");
        passed++;
        PrivateGpsDiagnostic.privateTransportError();
        expect("private network failure",PrivateGpsDiagnostic.lastCode(),
            PrivateGpsDiagnostic.Code.PRIVATE_TRANSPORT_ERROR);
        PrivateGpsDiagnostic.publicTransportError();
        expect("public network failure",PrivateGpsDiagnostic.lastCode(),
            PrivateGpsDiagnostic.Code.PUBLIC_TRANSPORT_ERROR);
        PrivateGpsDiagnostic.publicRejected();
        expect("public rpc rejected",PrivateGpsDiagnostic.lastCode(),
            PrivateGpsDiagnostic.Code.PUBLIC_REJECTED);
        PrivateGpsDiagnostic.skippedConditions();
        expect("GPS or shift condition skipped",PrivateGpsDiagnostic.lastCode(),
            PrivateGpsDiagnostic.Code.CONDITIONS_NOT_MET);
        if(!PrivateGpsDiagnostic.message(false).contains("desactivada"))
            throw new AssertionError("Consent disable must override previous codes");
        passed++;
        if(PrivateGpsDiagnostic.message(true).contains("token") ||
           PrivateGpsDiagnostic.message(true).contains("-34.") ||
           PrivateGpsDiagnostic.message(true).contains("device_id"))
            throw new AssertionError("Sensitive data leaked");
        passed++;
        rpc("success clears prior error",true,"no_unique_active_reservation",
            PrivateGpsDiagnostic.Code.ACKNOWLEDGED);
        System.out.println("QA P0.1 SUMMARY "+passed+" PASS / 0 FAIL (Java standalone)");
    }
}
