package uy.com.mapatrayectos;

/** R24.1: never quit the map's GPS/journey service while a journey or trip remains open. */
public final class BubbleClosePolicy {
    private BubbleClosePolicy(){}
    public static boolean mayClose(boolean liveShift,boolean liveTrip,boolean savedShift,boolean savedTrip){
        return !(liveShift||liveTrip||savedShift||savedTrip);
    }
}
