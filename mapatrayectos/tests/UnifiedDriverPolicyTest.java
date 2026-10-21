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
        // Direct "Tu viaje": answer immediately and never create a quote flow.
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",840,"now"),true,"direct now");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",840,"10"),true,"direct in 10m");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",840,"120"),true,"direct in 120m");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",840,"121"),false,"over 120m is not direct");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",840,"scheduled"),false,"scheduled does not use direct accept");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","SIN_PRESUPUESTO",840,"now"),false,"unpriced cannot accept direct");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","ENVIADO",840,"now"),false,"submitted quote is different");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",0,"now"),false,"missing price cannot accept");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",Double.NaN,"now"),false,"invalid price");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",Double.POSITIVE_INFINITY,"now"),false,"infinite price");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("ACEPTADA","PRECIO_FIJO",840,"now"),false,"already accepted cannot accept twice");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",840,""),false,"missing pickup mode");
        eq(UnifiedDriverPolicy.isDirectFixedRequest("PENDIENTE","PRECIO_FIJO",840,"01"),false,"bad time encoding");
        System.out.println("PASS: new Conductor state machine and direct-vs-scheduled guard");
    }
}
