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

    /** Only an already server-priced immediate trip gets a one-tap answer.
     * Mirrors the original Conductor's isDirectFixedRequest contract:
     * quote_status=PRECIO_FIJO, finite positive quote_final_total and
     * pickup_mode=now or 1..120 minutes.
     * A scheduled or unpriced request must never reach direct Accept.
     */
    public static boolean isImmediateMode(String mode){
        if("now".equals(mode))return true;
        if(mode==null||!mode.matches("[1-9][0-9]{0,2}"))return false;
        try{return Integer.parseInt(mode)<=120;}
        catch(NumberFormatException e){return false;}
    }
    public static boolean isDirectFixedRequest(String status,String quoteStatus,
                                                double finalPrice,String pickupMode){
        return canAccept(status)&&"PRECIO_FIJO".equals(quoteStatus)
            &&Double.isFinite(finalPrice)&&finalPrice>0
            &&isImmediateMode(pickupMode);
    }
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
