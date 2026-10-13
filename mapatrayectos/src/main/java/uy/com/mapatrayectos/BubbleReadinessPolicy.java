package uy.com.mapatrayectos;
/** Android overlay permission is not proof of a real rendered floating shortcut. */
public final class BubbleReadinessPolicy {
    private static final long SETTLE_MS=15000L;
    private BubbleReadinessPolicy(){}
    public static boolean confirmed(long testAt,long drawnAt){
        return testAt>0&&drawnAt>=testAt;
    }
    public static boolean pending(long testAt,long drawnAt,long now){
        return testAt>0&&drawnAt<testAt&&now>=testAt&&now-testAt<SETTLE_MS;
    }
}
