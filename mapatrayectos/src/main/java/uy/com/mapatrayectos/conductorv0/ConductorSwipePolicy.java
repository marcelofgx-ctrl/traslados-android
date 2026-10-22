package uy.com.mapatrayectos.conductorv0;

/** Pure gesture contract; usable in JVM tests without Android or GPS. */
public final class ConductorSwipePolicy {
    private ConductorSwipePolicy(){}
    public static boolean isOpenGesture(float dx,float dy,float minimumPx){
        return valid(dx,dy,minimumPx) && dx<=-minimumPx
                && Math.abs(dx)>Math.abs(dy)*1.4f;
    }
    public static boolean isCloseGesture(float dx,float dy,float minimumPx){
        return valid(dx,dy,minimumPx) && dx>=minimumPx
                && Math.abs(dx)>Math.abs(dy)*1.4f;
    }
    private static boolean valid(float dx,float dy,float minimumPx) {
        return Float.isFinite(dx) && Float.isFinite(dy)
                && Float.isFinite(minimumPx) && minimumPx>0f;
    }
}
