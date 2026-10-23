package uy.com.mapatrayectos.conductorv0;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Pure read-only client gating; backend RPC revalidates under row lock. */
public final class ConductorLivePolicy {
    private ConductorLivePolicy() {}
    public static boolean actionableFixed(String status,String mode,String quote,
                                          double amount,double km,String createdAt,long now) {
        return "PENDIENTE".equals(status) && "fixed".equals(mode)
            && "PRECIO_FIJO".equals(quote) && amount>0 && km>0
            && recent(createdAt,now);
    }
    public static boolean recent(String timestamp,long now) {
        if(timestamp==null||timestamp.isEmpty())return false;
        long created;
        try{created=OffsetDateTime.parse(timestamp).toInstant().toEpochMilli();}
        catch(Exception first) {
            try{created=Instant.parse(timestamp).toEpochMilli();}
            catch(Exception second){return false;}
        }
        long age=now-created;
        return age>=-60000L && age<=90L*60000L;
    }
    public static boolean accepted(String status) {
        return "ACEPTADA".equals(status)||"CONFIRMADA".equals(status)
            ||"ACEPTADA_CLIENTE".equals(status);
    }
    public static boolean inJourney(String status){return "EN_VIAJE".equals(status);}
    public static boolean pending(String status){return "PENDIENTE".equals(status);}
    public static boolean safeId(String id) {
        return id!=null && id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
