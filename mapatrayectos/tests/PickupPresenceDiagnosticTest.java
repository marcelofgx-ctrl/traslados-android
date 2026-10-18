import uy.com.mapatrayectos.PickupPresenceDiagnostic;
import java.net.SocketTimeoutException;
import java.io.IOException;

public final class PickupPresenceDiagnosticTest {
    private static void eq(String expected,String actual){
        if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);
    }
    private static String result(boolean ok,String reason,boolean eligible,boolean shift,
            boolean paused,boolean inTrip,boolean busy,boolean gps){
        return PickupPresenceDiagnostic.response(ok,reason,eligible,shift,paused,inTrip,busy,gps);
    }
    public static void main(String[] args){
        eq("published",result(true,"",true,true,false,false,false,true));
        eq("not_authorized",result(false,"not_authorized",false,true,false,false,false,true));
        eq("server_rejected",result(false,"other",false,true,false,false,false,true));
        eq("shift_inactive",result(true,"",false,false,false,false,false,true));
        eq("shift_paused",result(true,"",false,true,true,false,false,true));
        eq("trip_active",result(true,"",false,true,false,true,false,true));
        eq("manually_busy",result(true,"",false,true,false,false,true,true));
        eq("gps_not_usable",result(true,"",false,true,false,false,false,false));
        eq("server_not_eligible",result(true,"",false,true,false,false,false,true));
        eq("timeout",PickupPresenceDiagnostic.failure(new SocketTimeoutException("private")));
        eq("http_authorization",PickupPresenceDiagnostic.failure(new IOException("HTTP 403 secret")));
        eq("http_server",PickupPresenceDiagnostic.failure(new IOException("HTTP 500 secret")));
        eq("connection_error",PickupPresenceDiagnostic.failure(new IOException("GPS SECRET 123")));
        if(!PickupPresenceDiagnostic.display("not_authorized").contains("reautorizar"))
            throw new AssertionError("Missing recovery instruction");
        if(PickupPresenceDiagnostic.display("connection_error").contains("SECRET"))
            throw new AssertionError("Leaked exception");
        System.out.println("PASS PRESENCE-001: finite states, authorization and private errors");
    }
}
