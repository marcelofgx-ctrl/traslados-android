package uy.com.mapatrayectos.conductorv0;

/**
 * Pure, conservative presentation policy for a ONE-driver private transfer app.
 * This is only an Android-side guard; the final single-driver invariant must
 * also be enforced atomically by all relevant Supabase acceptance RPCs.
 */
public final class ConductorCapacityPolicy {
    private ConductorCapacityPolicy() {}

    /** A historical estimated end time must NEVER close a real accepted trip. */
    public static boolean countsAsUnclosedImmediate(String status,String pickupMode) {
        if ("EN_VIAJE".equals(status)) return true;
        if (!"now".equals(pickupMode)) return false;
        return "ACEPTADA".equals(status)
            || "CONFIRMADA".equals(status)
            || "ACEPTADA_CLIENTE".equals(status);
    }
    public static boolean mayAcceptImmediate(boolean freshSnapshot,int openImmediateCount) {
        return freshSnapshot && openImmediateCount == 0;
    }
    public static String occupancyLabel(boolean freshSnapshot,int openImmediateCount) {
        if(!freshSnapshot) return "Estado del conductor por confirmar";
        if(openImmediateCount > 0)
            return "Conductor ocupado · "+openImmediateCount+
                (openImmediateCount==1?" servicio sin cerrar":" servicios sin cerrar");
        return "Disponible para una recogida inmediata";
    }
}