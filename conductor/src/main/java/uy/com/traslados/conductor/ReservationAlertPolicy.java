package uy.com.traslados.conductor;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Pure policy shared by service and UI: recent PENDING bookings must alert
 * even on the FIRST successful poll after Android kills/restarts the monitor.
 * Long-expired requests are never advertised as newly actionable.
 * Android API 26+ supports java.time; no location/session/PII is stored here. */
public final class ReservationAlertPolicy {
    private ReservationAlertPolicy(){}
    public static final long RECENT_MS=90L*60L*1000L;
    public static final long DELIVERED_TTL_MS=24L*60L*60L*1000L;

    public static long createdAtMillis(String utcIso){
        if(utcIso==null||utcIso.trim().isEmpty())return -1L;
        try{return OffsetDateTime.parse(utcIso.trim()).toInstant().toEpochMilli();}
        catch(Exception ignored){
            try{return Instant.parse(utcIso.trim()).toEpochMilli();}
            catch(Exception ignored2){return -1L;}
        }
    }

    public static boolean recentPending(String status,String createdAt,long nowMillis){
        if(!"PENDIENTE".equals(status))return false;
        long stamp=createdAtMillis(createdAt);
        long age=nowMillis-stamp;
        return stamp>0L&&age>=-60000L&&age<=RECENT_MS;
    }

    public static boolean undelivered(long lastDeliveredAt,long nowMillis){
        return lastDeliveredAt<=0L||lastDeliveredAt>nowMillis+60000L
            ||nowMillis-lastDeliveredAt>DELIVERED_TTL_MS;
    }
}
