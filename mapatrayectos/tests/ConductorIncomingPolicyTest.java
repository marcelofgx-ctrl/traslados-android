import uy.com.mapatrayectos.conductorv0.ConductorIncomingPolicy;
import java.util.Arrays;
public final class ConductorIncomingPolicyTest {
    private static void check(boolean ok,String detail){
        if(!ok)throw new AssertionError(detail);
    }
    public static void main(String[] ignored){
        check(ConductorIncomingPolicy.openForegroundPanel(true,true,true),"foreground request");
        check(!ConductorIncomingPolicy.openForegroundPanel(false,true,true),"minimized stays bubble");
        check(!ConductorIncomingPolicy.openForegroundPanel(true,false,true),"device not linked");
        check(!ConductorIncomingPolicy.openForegroundPanel(true,true,false),"scheduled not immediate");
        check(ConductorIncomingPolicy.returnToMap(true),"confirmed decision");
        check(!ConductorIncomingPolicy.returnToMap(false),"server failure must not close");
        check(Arrays.equals(ConductorIncomingPolicy.incomingPattern(),
            new long[]{0,360,180,360,180,360}),"exactly three long separated haptics");
        System.out.println("PASS Conductor incoming foreground policy");
    }
}
