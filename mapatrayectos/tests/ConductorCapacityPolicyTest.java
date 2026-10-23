import uy.com.mapatrayectos.conductorv0.ConductorCapacityPolicy;
public class ConductorCapacityPolicyTest {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] ignored){
        require(ConductorCapacityPolicy.countsAsUnclosedImmediate("ACEPTADA","now"),"accepted morning trip remains active later");
        require(ConductorCapacityPolicy.countsAsUnclosedImmediate("EN_VIAJE","now"),"in progress");
        require(ConductorCapacityPolicy.countsAsUnclosedImmediate("EN_VIAJE","schedule"),"scheduled in progress");
        require(ConductorCapacityPolicy.countsAsUnclosedImmediate("CONFIRMADA","now"),"legacy confirmed");
        require(!ConductorCapacityPolicy.countsAsUnclosedImmediate("FINALIZADA","now"),"finished no longer blocks");
        require(!ConductorCapacityPolicy.countsAsUnclosedImmediate("CANCELADA","now"),"cancelled no longer blocks");
        require(!ConductorCapacityPolicy.countsAsUnclosedImmediate("RECHAZADA","now"),"rejected no longer blocks");
        require(!ConductorCapacityPolicy.countsAsUnclosedImmediate("ACEPTADA","schedule"),"future scheduled handled by calendar");
        require(ConductorCapacityPolicy.mayAcceptImmediate(true,0),"available and fresh");
        require(!ConductorCapacityPolicy.mayAcceptImmediate(true,1),"one already accepted");
        require(!ConductorCapacityPolicy.mayAcceptImmediate(true,2),"two from screenshots");
        require(!ConductorCapacityPolicy.mayAcceptImmediate(false,0),"offline fails closed");
        require(ConductorCapacityPolicy.occupancyLabel(true,2).contains("2 servicios sin cerrar"),"honest occupied label");
        require(!ConductorCapacityPolicy.occupancyLabel(true,0).contains("Disponible"),"fresh booking snapshot is NOT proof of GPS/jornada availability");
        System.out.println("PASS: single-driver immediate capacity UI policy, no engine writes");
    }
}