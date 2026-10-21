package uy.com.mapatrayectos.driver;

/** Pure journey transitions. The server remains the source of truth. */
public final class UnifiedDriverPolicy {
    private UnifiedDriverPolicy(){}
    public static boolean allowedStatus(String s){
        return "ACEPTADA".equals(s)||"RECHAZADA".equals(s)
            ||"EN_VIAJE".equals(s)||"FINALIZADA".equals(s);
    }
    public static boolean allowedPickupPhase(String s){
        return "EN_CAMINO".equals(s)||"EN_PUNTO".equals(s);
    }
    public static boolean active(String s){
        return "PENDIENTE".equals(s)||"ACEPTADA".equals(s)
            ||"EN_VIAJE".equals(s);
    }
    public static boolean canAccept(String s){return "PENDIENTE".equals(s);}
    public static boolean canStart(String s){return "ACEPTADA".equals(s);}
    public static boolean canFinish(String s){return "EN_VIAJE".equals(s);}
    public static boolean canSetPickup(String status,String current,String next){
        if(!canStart(status))return false;
        if("EN_CAMINO".equals(next))return current==null||current.isEmpty();
        if("EN_PUNTO".equals(next))return "EN_CAMINO".equals(current);
        return false;
    }
    public static String label(String status,String phase){
        if("PENDIENTE".equals(status))return "NUEVA SOLICITUD";
        if("ACEPTADA".equals(status)){
            if("EN_PUNTO".equals(phase))return "EN PUNTO DE RECOGIDA";
            if("EN_CAMINO".equals(phase))return "EN CAMINO A RECOGIDA";
            return "ACEPTADA";
        }
        if("EN_VIAJE".equals(status))return "EN VIAJE";
        if("FINALIZADA".equals(status))return "FINALIZADA";
        if("RECHAZADA".equals(status))return "RECHAZADA";
        return status==null?"SIN ESTADO":status;
    }
}
