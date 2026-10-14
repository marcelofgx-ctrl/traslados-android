import uy.com.mapatrayectos.TripClosePolicy;

public final class TripClosePolicyTest {
    private static void check(boolean yes,String what){if(!yes)throw new AssertionError(what);}
    public static void main(String[] args){
        check(!TripClosePolicy.mayFinish("completed",0),"zero cannot complete");
        check(!TripClosePolicy.mayFinish("completed",-2),"negative cannot complete");
        check(!TripClosePolicy.mayFinish("completed",Double.NaN),"NaN cannot complete");
        check(!TripClosePolicy.mayFinish("completed",Double.POSITIVE_INFINITY),"infinite cannot complete");
        check(TripClosePolicy.mayFinish("completed",0.01),"positive can complete");
        check(TripClosePolicy.mayFinish("cancelled",0),"cancellation may have zero");
        check(!TripClosePolicy.mayFinish("active",150),"active cannot close");
        System.out.println("TripClosePolicy tests passed");
    }
}
