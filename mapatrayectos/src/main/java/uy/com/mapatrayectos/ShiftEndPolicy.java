package uy.com.mapatrayectos;

/** Prevents closing an active shift while a service is still in progress. */
public final class ShiftEndPolicy {
    private ShiftEndPolicy(){}
    public static boolean canEndShift(boolean shiftActive, boolean tripActive){
        return shiftActive && !tripActive;
    }
}
