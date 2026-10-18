package uy.com.mapatrayectos;

/** Play/Stop semantics for commercial presence. The identity is obtained
 * from the device's existing secure link, never entered per shift. */
public final class PlayStopPresencePolicy {
    private PlayStopPresencePolicy(){}
    public static boolean canEnable(boolean shiftActive,boolean linked){
        return shiftActive&&linked;
    }
    public static boolean shouldRetry(boolean shiftActive,boolean requested,
            boolean enabled,boolean linked){
        return canEnable(shiftActive,linked)&&requested&&!enabled;
    }
    public static boolean mustRevoke(boolean shiftStopped){return shiftStopped;}
}
