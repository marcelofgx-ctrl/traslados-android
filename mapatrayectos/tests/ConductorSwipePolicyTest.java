import uy.com.mapatrayectos.conductorv0.ConductorSwipePolicy;

/** Phase-2 navigation contract, pure JVM, no APK and no Android runtime. */
public final class ConductorSwipePolicyTest {
    private static void check(boolean ok,String what) {
        if(!ok)throw new AssertionError(what);
    }
    public static void main(String[] args) {
        check(ConductorSwipePolicy.isOpenGesture(-80,5,55),"left swipe opens");
        check(!ConductorSwipePolicy.isCloseGesture(-80,5,55),"left not close");
        check(ConductorSwipePolicy.isCloseGesture(80,5,55),"right swipe closes");
        check(!ConductorSwipePolicy.isOpenGesture(80,5,55),"right not open");
        check(!ConductorSwipePolicy.isOpenGesture(-30,0,55),"small swipe ignored");
        check(!ConductorSwipePolicy.isCloseGesture(30,0,55),"small right ignored");
        check(!ConductorSwipePolicy.isOpenGesture(-100,150,55),"vertical scroll preserved");
        check(!ConductorSwipePolicy.isCloseGesture(100,150,55),"vertical scroll unchanged");
        check(!ConductorSwipePolicy.isOpenGesture(Float.NaN,0,55),"invalid input");
        check(!ConductorSwipePolicy.isCloseGesture(200,0,-1),"invalid threshold");
        System.out.println("Conductor V0 phase 2: ten gesture contract checks PASS");
    }
}
