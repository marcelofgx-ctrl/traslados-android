package uy.com.mapatrayectos.conductorv0;

/** C1: display-only action selection; existing controller performs all RPC validation. */
public final class DriverSheetPolicy {
    public enum Action { NONE, GO_TO_PICKUP, ARRIVED, START_TRIP, FINISH_TRIP }
    private DriverSheetPolicy() {}

    /** Recover only one real active immediate journey from an authenticated snapshot. */
    public static boolean recoverable(String status){
        return "ACEPTADA".equals(status) || "EN_VIAJE".equals(status);
    }
    public static String uniqueRecoveryId(java.util.List<String> ids,java.util.List<String> states){
        if(ids==null||states==null||ids.size()!=states.size())return "";
        String match="";
        for(int i=0;i<ids.size();i++){
            if(!recoverable(states.get(i)))continue;
            String id=ids.get(i);
            if(id==null||id.isEmpty()||!match.isEmpty())return "";
            match=id;
        }
        return match;
    }
    public static boolean visible(String status) {
        return "ACEPTADA".equals(status) || "ACEPTADA_CLIENTE".equals(status)
            || "CONFIRMADA".equals(status) || "EN_VIAJE".equals(status);
    }
    public static Action next(String status,String phase,boolean snapshotFresh) {
        if(!snapshotFresh || !visible(status))return Action.NONE;
        if("EN_VIAJE".equals(status))return Action.FINISH_TRIP;
        // The existing pickup RPC explicitly validates status ACEPTADA.
        // Do not show an action it cannot perform for other accepted variants.
        if(!"ACEPTADA".equals(status))return Action.NONE;
        if("EN_PUNTO".equals(phase))return Action.START_TRIP;
        if("EN_CAMINO".equals(phase))return Action.ARRIVED;
        if(phase==null || phase.isEmpty() || "SIN_INICIAR".equals(phase))
            return Action.GO_TO_PICKUP;
        return Action.NONE;
    }
    public static String label(Action action) {
        switch(action) {
            case GO_TO_PICKUP:return "Ir a recoger";
            case ARRIVED:return "Llegué al origen";
            case START_TRIP:return "Iniciar viaje";
            case FINISH_TRIP:return "Finalizar viaje";
            default:return "Esperando estado verificado";
        }
    }
    public static String statusLabel(String status,String phase) {
        if("EN_VIAJE".equals(status))return "Viaje en curso";
        if("EN_PUNTO".equals(phase))return "En el punto de recogida";
        if("EN_CAMINO".equals(phase))return "Conductor en camino";
        return "Viaje aceptado";
    }
}
