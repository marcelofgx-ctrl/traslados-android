package uy.com.mapatrayectos;

/** Network-only pickup heartbeat policy. GPS sampling remains unchanged.
 * Faster uploads require BOTH confirmed recent movement and real passenger ETA
 * demand acknowledged by our credential-checked Supabase RPC.
 */
public final class PickupPresenceCadence {
    public static final long BASE_MS=25000L;
    public static final long MOVING_DEMAND_MS=15000L;
    public static final long STATIONARY_DEMAND_MS=30000L;
    private PickupPresenceCadence(){}
    public static long intervalMs(boolean recentlyMoving,boolean passengerDemand){
        if(!passengerDemand)return BASE_MS;
        return recentlyMoving?MOVING_DEMAND_MS:STATIONARY_DEMAND_MS;
    }
    public static boolean recentlyMoving(boolean moving,long acceptedAt,long now){
        return moving&&acceptedAt>0&&now>=acceptedAt&&now-acceptedAt<=10000L;
    }
}
