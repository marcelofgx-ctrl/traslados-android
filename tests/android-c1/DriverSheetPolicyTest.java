package uy.com.mapatrayectos.conductorv0;

public final class DriverSheetPolicyTest {
    private static int count;
    static void check(boolean value,String label) {
        if(!value)throw new AssertionError(label);
        count++;System.out.println("PASS "+label);
    }
    static void expect(String status,String phase,boolean fresh,DriverSheetPolicy.Action action){
        check(DriverSheetPolicy.next(status,phase,fresh)==action,
              status+"/"+phase+"/"+fresh+" -> "+action);
    }
    public static void main(String[] args) {
        expect("ACEPTADA","SIN_INICIAR",true,DriverSheetPolicy.Action.GO_TO_PICKUP);
        expect("ACEPTADA","",true,DriverSheetPolicy.Action.GO_TO_PICKUP);
        expect("ACEPTADA","EN_CAMINO",true,DriverSheetPolicy.Action.ARRIVED);
        expect("ACEPTADA","EN_PUNTO",true,DriverSheetPolicy.Action.START_TRIP);
        expect("EN_VIAJE","EN_PUNTO",true,DriverSheetPolicy.Action.FINISH_TRIP);
        expect("EN_VIAJE","",true,DriverSheetPolicy.Action.FINISH_TRIP);
        expect("PENDIENTE","",true,DriverSheetPolicy.Action.NONE);
        expect("FINALIZADA","",true,DriverSheetPolicy.Action.NONE);
        expect("CANCELADA","",true,DriverSheetPolicy.Action.NONE);
        expect("ACEPTADA","EN_CAMINO",false,DriverSheetPolicy.Action.NONE);
        expect("EN_VIAJE","",false,DriverSheetPolicy.Action.NONE);
        expect("ACEPTADA_CLIENTE","EN_CAMINO",true,DriverSheetPolicy.Action.NONE);
        expect("CONFIRMADA","EN_PUNTO",true,DriverSheetPolicy.Action.NONE);
        expect("ACEPTADA","OTRA_ETAPA",true,DriverSheetPolicy.Action.NONE);
        check(DriverSheetPolicy.visible("ACEPTADA"),"accepted visible");
        check(DriverSheetPolicy.visible("EN_VIAJE"),"onboard visible");
        check(!DriverSheetPolicy.visible("PENDIENTE"),"pending hidden");
        check(!DriverSheetPolicy.visible("RECHAZADA"),"rejected hidden");
        check("Ir a recoger".equals(DriverSheetPolicy.label(DriverSheetPolicy.Action.GO_TO_PICKUP)),"CTA pickup");
        check("Finalizar viaje".equals(DriverSheetPolicy.label(DriverSheetPolicy.Action.FINISH_TRIP)),"CTA finish");
        System.out.println("C1 POLICY "+count+" PASS / 0 FAIL");
    }
}
