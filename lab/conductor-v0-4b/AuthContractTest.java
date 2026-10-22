package lab.quicktrip;

import java.util.UUID;

public final class AuthContractTest {
    private static int assertions=0;
    private static void equal(Object actual, Object expected, String label) {
        assertions++;
        if (!actual.equals(expected))
            throw new AssertionError(label+" expected="+expected+" got="+actual);
    }
    private static void yes(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        long now=1900000000L;
        UUID a=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID b=UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AuthContract.Session valid=new AuthContract.Session(a,true,false,false,now+600);
        equal(AuthContract.canDecide(valid,a,true,a,now),AuthContract.Reason.ALLOWED,"valid");
        equal(AuthContract.canDecide(null,a,true,a,now),AuthContract.Reason.NO_SESSION,"absent");
        equal(AuthContract.canDecide(new AuthContract.Session(null,true,false,false,now+600),
            a,true,a,now),AuthContract.Reason.NO_SESSION,"empty user");
        equal(AuthContract.canDecide(new AuthContract.Session(a,false,false,false,now+600),
            a,true,a,now),AuthContract.Reason.UNVERIFIED,"invalid signature flag");
        equal(AuthContract.canDecide(new AuthContract.Session(a,true,false,false,now-1),
            a,true,a,now),AuthContract.Reason.EXPIRED,"expired");
        equal(AuthContract.canDecide(new AuthContract.Session(a,true,false,false,now),
            a,true,a,now),AuthContract.Reason.EXPIRED,"expiry boundary");
        equal(AuthContract.canDecide(new AuthContract.Session(a,true,true,false,now+600),
            a,true,a,now),AuthContract.Reason.REVOKED,"revoked");
        equal(AuthContract.canDecide(new AuthContract.Session(a,true,false,true,now+600),
            a,true,a,now),AuthContract.Reason.ANONYMOUS,"anonymous auth");
        equal(AuthContract.canDecide(valid,a,false,a,now),AuthContract.Reason.INACTIVE_DRIVER,"inactive");
        equal(AuthContract.canDecide(valid,b,true,a,now),AuthContract.Reason.INACTIVE_DRIVER,"other principal");
        equal(AuthContract.canDecide(valid,a,true,b,now),AuthContract.Reason.OTHER_DRIVER,"other trip");
        equal(AuthContract.canDecide(valid,a,true,null,now),AuthContract.Reason.INVALID_RESERVATION,"no driver");
        yes(AuthContract.isImmediateFixed("now","fixed","PRECIO_FIJO","PENDIENTE",840,21),"now");
        yes(AuthContract.isImmediateFixed("1","fixed","PRECIO_FIJO","PENDIENTE",40,1),"one min");
        yes(AuthContract.isImmediateFixed("120","fixed","PRECIO_FIJO","PENDIENTE",40,1),"max min");
        yes(!AuthContract.isImmediateFixed("121","fixed","PRECIO_FIJO","PENDIENTE",840,21),"over max");
        yes(!AuthContract.isImmediateFixed("schedule","fixed","PRECIO_FIJO","PENDIENTE",840,21),"scheduled");
        yes(!AuthContract.isImmediateFixed("now","quote","PRECIO_FIJO","PENDIENTE",840,21),"quoted");
        yes(!AuthContract.isImmediateFixed("now","fixed","ENVIADO","PENDIENTE",840,21),"unpriced");
        yes(!AuthContract.isImmediateFixed("now","fixed","PRECIO_FIJO","ACEPTADA",840,21),"stale");
        yes(!AuthContract.isImmediateFixed("now","fixed","PRECIO_FIJO","PENDIENTE",0,21),"free zero");
        yes(!AuthContract.isImmediateFixed("now","fixed","PRECIO_FIJO","PENDIENTE",Double.NaN,21),"nan");
        yes(!AuthContract.isImmediateFixed("now","fixed","PRECIO_FIJO","PENDIENTE",840,0),"no route");
        yes(!AuthContract.isImmediateFixed(null,"fixed","PRECIO_FIJO","PENDIENTE",840,21),"no mode");
        System.out.println("PASS "+assertions+" Java contract assertions; synthetic identity ONLY.");
    }
}
