package uy.com.mapatrayectos;

import java.util.Locale;

/** Platform chosen for the NEXT service. Does not alter an already-recorded trip. */
public final class TripPlatformChoice {
    private TripPlatformChoice(){}
    public static String canonical(String value){
        if(value==null)return "";
        String v=value.trim().toLowerCase(Locale.ROOT);
        if("uber".equals(v)||"cabify".equals(v)||"personal".equals(v)||"other".equals(v))return v;
        return "";
    }
    public static boolean shouldPrompt(String value){
        return canonical(value).isEmpty();
    }
    public static String display(String value){
        String v=canonical(value);
        if("uber".equals(v))return "UBER";
        if("cabify".equals(v))return "CABIFY";
        if("personal".equals(v))return "PERSONAL";
        if("other".equals(v))return "OTRO";
        return "ELEGIR";
    }
    public static String forBadge(String planned, boolean tripActive, String recordedTripType){
        return display(tripActive?recordedTripType:planned);
    }
}
