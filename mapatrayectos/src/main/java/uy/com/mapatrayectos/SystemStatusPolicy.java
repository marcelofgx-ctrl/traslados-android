package uy.com.mapatrayectos;

import java.util.Locale;

/** Pure formatting and thresholds for the driver-only read-only status dashboard. */
public final class SystemStatusPolicy {
    private SystemStatusPolicy(){}

    public static int percent(long used,long limit) {
        if(used<0 || limit<=0)return -1;
        return (int)Math.max(0,Math.min(100,Math.round(100.0*used/limit)));
    }

    public static String level(long used,long limit) {
        int pct=percent(used,limit);
        if(pct<0)return "NO_DATA";
        if(pct>=85)return "CRITICAL";
        if(pct>=65)return "WATCH";
        return "HEALTHY";
    }

    public static long free(long used,long limit) {
        return Math.max(0,limit-Math.max(0,used));
    }

    public static String mb(long bytes) {
        if(bytes<0)return "Sin datos";
        return String.format(new Locale("es","UY"),"%.1f MB",bytes/1000000.0);
    }

    public static String count(long value) {
        if(value<0)return "—";
        return String.format(new Locale("es","UY"),"%,d",value);
    }
}
