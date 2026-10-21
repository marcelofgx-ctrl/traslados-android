import uy.com.mapatrayectos.driver.UnifiedDriverPolicy;
public final class UnifiedDriverPolicyTest {
    static void eq(boolean actual,boolean wanted,String context){
        if(actual!=wanted)throw new AssertionError(context);
    }
    public static void main(String[]args){
        eq(UnifiedDriverPolicy.canAccept("PENDIENTE"),true,"accept pending");
        eq(UnifiedDriverPolicy.canAccept("ACEPTADA"),false,"no duplicate acceptance");
        eq(UnifiedDriverPolicy.canStart("PENDIENTE"),false,"cannot start pending");
        eq(UnifiedDriverPolicy.canStart("ACEPTADA"),true,"start accepted");
        eq(UnifiedDriverPolicy.canSetPickup("ACEPTADA","","EN_CAMINO"),true,"pickup start");
        eq(UnifiedDriverPolicy.canSetPickup("ACEPTADA","","EN_PUNTO"),false,"no jump");
        eq(UnifiedDriverPolicy.canSetPickup("ACEPTADA","EN_CAMINO","EN_PUNTO"),true,"arrived");
        eq(UnifiedDriverPolicy.canSetPickup("EN_VIAJE","EN_CAMINO","EN_PUNTO"),false,"no pickup in travel");
        eq(UnifiedDriverPolicy.canFinish("EN_VIAJE"),true,"finish");
        eq(UnifiedDriverPolicy.canFinish("PENDIENTE"),false,"no premature finish");
        eq(UnifiedDriverPolicy.allowedStatus("PENDIENTE"),false,"no reset");
        eq(UnifiedDriverPolicy.allowedStatus("CANCELADA"),false,"no cancellation");
        eq(UnifiedDriverPolicy.allowedPickupPhase("EN_PUNTO"),true,"known pickup phase");
        eq(UnifiedDriverPolicy.allowedPickupPhase("LISTO"),false,"unknown pickup");
        System.out.println("PASS: new Conductor state machine");
    }
}
