import uy.com.mapatrayectos.NavigationHeadingPolicy;

/** R25.21. Simulate GPS routes: NE, 90-degree turns, noisy stop, no-bearing. */
public final class NavigationHeadingPolicyTest {
    private static void close(float a,float b,String scenario){
        float error=Math.abs(NavigationHeadingPolicy.signedDelta(a,b));
        if(error>1.5f)throw new AssertionError(scenario+": expected "+b+" but got "+a+" error "+error);
    }
    public static void main(String[] args) {
        float b=NavigationHeadingPolicy.course(true,90f,false,0,32f,5f,5f,20,90f,1.5);
        close(b,90f,"eastward driving");
        close(NavigationHeadingPolicy.camera(0f,b),90f,"east turn must rotate map, NOT stay north");
        close(NavigationHeadingPolicy.camera(90f,185f),185f,"southward 90 degree bend immediate");
        close(NavigationHeadingPolicy.camera(359f,2f),1.34f,"north crossover smooth");
        close(NavigationHeadingPolicy.camera(2f,359f),359.66f,"reverse north crossover smooth");
        close(NavigationHeadingPolicy.camera(90f,Float.NaN),90f,"stationary stop keeps map angle");
        if(Float.isFinite(NavigationHeadingPolicy.course(true,240f,false,0,0f,3f,3f,1,240f,2)))
            throw new AssertionError("at red light raw bearing must be ignored");
        if(Float.isFinite(NavigationHeadingPolicy.course(true,270f,true,80f,30f,6f,6f,2,270f,1.5)))
            throw new AssertionError("poor heading accuracy and short drift are not route direction");
        b=NavigationHeadingPolicy.course(false,0f,false,0f,27f,4f,4f,34,265f,2);
        close(b,265f,"movement geometry fallback when device omits bearing");
        if(Float.isFinite(NavigationHeadingPolicy.course(false,0f,false,0f,23f,13f,13f,5,265f,2)))
            throw new AssertionError("noise at GPS accuracy threshold must not rotate map");
        if(Float.isFinite(NavigationHeadingPolicy.course(true,90f,false,0f,32f,80f,80f,50,90f,2)))
            throw new AssertionError("low precision location must not rotate map");
        close(NavigationHeadingPolicy.normalize(-90f),270f,"normalize negative degrees");
        close(NavigationHeadingPolicy.camera(180f,95f),95f,"hard right turn");
        System.out.println("PASS R25.21: heading-up turns, course fallback, stationary GPS noise and north crossing");
    }
}
