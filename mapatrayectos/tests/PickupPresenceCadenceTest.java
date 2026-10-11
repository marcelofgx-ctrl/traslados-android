import uy.com.mapatrayectos.PickupPresenceCadence;
public final class PickupPresenceCadenceTest {
  private static void eq(long actual,long expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
  public static void main(String[] args){
    eq(PickupPresenceCadence.intervalMs(false,false),25000L);
    eq(PickupPresenceCadence.intervalMs(true,false),25000L);
    eq(PickupPresenceCadence.intervalMs(true,true),15000L);
    eq(PickupPresenceCadence.intervalMs(false,true),30000L);
    if(!PickupPresenceCadence.recentlyMoving(true,100000L,108000L))throw new AssertionError("Fresh motion");
    if(PickupPresenceCadence.recentlyMoving(true,100000L,111000L))throw new AssertionError("Stale motion");
    if(PickupPresenceCadence.recentlyMoving(false,100000L,100000L))throw new AssertionError("Stopped vehicle");
    if(PickupPresenceCadence.recentlyMoving(true,200000L,100000L))throw new AssertionError("Future GPS");
    System.out.println("PASS PickupPresenceCadence: demand-aware 15s/30s, idle 25s, stale GPS protection");
  }
}
