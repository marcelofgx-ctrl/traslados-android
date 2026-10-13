import uy.com.mapatrayectos.BubbleReadinessPolicy;
public final class BubbleReadinessPolicyTest {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] a){
        check(!BubbleReadinessPolicy.confirmed(0,500),"No test");
        check(!BubbleReadinessPolicy.confirmed(900,700),"Old draw");
        check(BubbleReadinessPolicy.confirmed(900,905),"New draw");
        check(BubbleReadinessPolicy.confirmed(900,900),"Same time");
        check(BubbleReadinessPolicy.pending(900,800,910),"Pending");
        check(!BubbleReadinessPolicy.pending(900,905,910),"Not pending after draw");
        check(!BubbleReadinessPolicy.pending(900,800,18000),"Expired pending");
        check(!BubbleReadinessPolicy.pending(900,800,800),"Future request");
        check(!BubbleReadinessPolicy.pending(0,0,1000),"No request");
        System.out.println("R25.11 bubble evidence PASS");
    }
}
