package uy.com.mapatrayectos.conductorv0;

/** Operational transitions: never infer a server status from GPS or UI clicks. */
public final class ConductorOperationsPolicy {
    private ConductorOperationsPolicy(){}
    public static boolean canQuote(String status,String pickupMode,String fareMode) {
        return "PENDIENTE".equals(status) && "schedule".equals(pickupMode)
            && !"fixed".equals(fareMode);
    }
    public static boolean canStart(String status) {
        return "ACEPTADA".equals(status);
    }
    public static boolean canFinish(String status) {
        return "EN_VIAJE".equals(status);
    }
    public static boolean goodPrice(double amount) {
        return Double.isFinite(amount) && amount>0 && amount<=1000000;
    }
    public static boolean goodDistance(double km) {
        return Double.isFinite(km) && km>0 && km<=5000;
    }
    public static boolean nonnegative(double amount) {
        return Double.isFinite(amount) && amount>=0 && amount<=1000000;
    }
    public static boolean quoteConfirmed(String status,String quoteStatus,boolean ok) {
        return ok && "PENDIENTE".equals(status) && "ENVIADO".equals(quoteStatus);
    }
}
