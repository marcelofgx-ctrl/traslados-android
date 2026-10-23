import uy.com.mapatrayectos.conductorv0.ConductorOperationsPolicy;
public final class ConductorOperationsPolicyTest {
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] a){
        check(ConductorOperationsPolicy.canQuote("PENDIENTE","schedule","quote"),"scheduled quote");
        check(!ConductorOperationsPolicy.canQuote("PENDIENTE","now","quote"),"immediate no quote");
        check(!ConductorOperationsPolicy.canQuote("PENDIENTE","schedule","fixed"),"fixed no quote");
        check(!ConductorOperationsPolicy.canQuote("ACEPTADA","schedule","quote"),"accepted no quote");
        check(ConductorOperationsPolicy.canStart("ACEPTADA"),"start");
        check(!ConductorOperationsPolicy.canStart("EN_VIAJE"),"start twice");
        check(!ConductorOperationsPolicy.canStart("PENDIENTE"),"start without accepting");
        check(ConductorOperationsPolicy.canFinish("EN_VIAJE"),"finish");
        check(!ConductorOperationsPolicy.canFinish("ACEPTADA"),"finish before start");
        check(ConductorOperationsPolicy.goodDistance(21.2)&&!ConductorOperationsPolicy.goodDistance(Double.NaN)&&!ConductorOperationsPolicy.goodDistance(-2),"distance");
        check(ConductorOperationsPolicy.goodPrice(840)&&!ConductorOperationsPolicy.goodPrice(0)&&!ConductorOperationsPolicy.goodPrice(Double.POSITIVE_INFINITY),"price");
        check(ConductorOperationsPolicy.nonnegative(0)&&!ConductorOperationsPolicy.nonnegative(-1),"extra");
        check(ConductorOperationsPolicy.quoteConfirmed("PENDIENTE","ENVIADO",true),"server confirmed");
        check(!ConductorOperationsPolicy.quoteConfirmed("PENDIENTE","SIN_PRESUPUESTO",true),"server not confirmed");
        System.out.println("PASS Conductor Operations — quote/start/finish and input constraints");
    }
}
