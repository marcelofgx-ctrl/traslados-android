package uy.com.mapatrayectos.conductorv0;

/**
 * Foreground-only presentation rules. No GPS, Supabase, trips or permissions.
 * The background floating callout is owned by the existing TrackingService.
 */
public final class ConductorIncomingPolicy {
    private ConductorIncomingPolicy() {}
    public static boolean openForegroundPanel(boolean uiVisible,boolean driverLinked,
                                              boolean immediateFixedRequest) {
        return uiVisible && driverLinked && immediateFixedRequest;
    }
    /** Three prolonged vibration pulses separated by pauses, no repetition. */
    public static long[] incomingPattern(){
        return new long[]{0,360,180,360,180,360};
    }
    public static boolean returnToMap(boolean serverConfirmed){
        return serverConfirmed;
    }
}
