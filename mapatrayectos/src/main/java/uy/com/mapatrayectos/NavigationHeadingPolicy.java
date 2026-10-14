package uy.com.mapatrayectos;

/**
 * R25.21 — single navigation course for arrow AND MapLibre bearing.
 * Speed accounting / odometer must never decide whether the map turns.
 * No compass sensor at a red light: keep the last reliable ground course.
 */
public final class NavigationHeadingPolicy {
    private NavigationHeadingPolicy() {}
    public static float normalize(float degrees) {
        if(!Float.isFinite(degrees))return 0f;
        float result=degrees%360f;
        return result<0?result+360f:result;
    }
    public static float signedDelta(float from,float to) {
        return ((normalize(to)-normalize(from)+540f)%360f)-180f;
    }
    /**
     * Location bearing from GPS when reliable; otherwise a sufficiently
     * long, accurately measured movement segment. NaN means HOLD previous
     * camera heading, not north-up.
     */
    public static float course(boolean hasGpsBearing,float gpsBearing,
        boolean hasGpsBearingAccuracy,float gpsBearingAccuracy,float speedKmh,
        float gpsAccuracy,float previousAccuracy,double displacementMeters,
        float movementBearing,double elapsedSeconds) {
        if(!Float.isFinite(speedKmh)||speedKmh<5.0f||
            !Float.isFinite(gpsAccuracy)||gpsAccuracy>30f)return Float.NaN;
        if(hasGpsBearing&&Float.isFinite(gpsBearing)&&
            (!hasGpsBearingAccuracy||(Float.isFinite(gpsBearingAccuracy)&&gpsBearingAccuracy<=45f)))
            return normalize(gpsBearing);
        double minMovement=Math.max(9d,Math.min(30d,
            (Math.max(0f,gpsAccuracy)+Math.max(0f,previousAccuracy))*.8d));
        if(elapsedSeconds>=.8d&&elapsedSeconds<=10d&&
            displacementMeters>=minMovement&&Double.isFinite(displacementMeters)&&
            Float.isFinite(movementBearing))return normalize(movementBearing);
        return Float.NaN;
    }
    /**
     * Make a new street heading-up immediately when the vehicle turns.
     * Small GPS oscillations are eased, but >=28 degree turns snap so that a
     * road cannot remain perpendicular after cornering.
     */
    public static float camera(float current,float target) {
        if(!Float.isFinite(target))return normalize(current);
        float delta=signedDelta(current,target);
        if(Math.abs(delta)>=28f)return normalize(target);
        return normalize(current+delta*.78f);
    }
}
