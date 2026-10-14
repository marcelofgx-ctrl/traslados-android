import uy.com.mapatrayectos.ShiftEndPolicy;

public final class ShiftEndPolicyTest {
    private static void verify(boolean actual,boolean expected,String name){
        if(actual!=expected)throw new AssertionError(name+": expected="+expected+", actual="+actual);
    }
    public static void main(String[] args){
        verify(ShiftEndPolicy.canEndShift(false,false),false,"inactive shift");
        verify(ShiftEndPolicy.canEndShift(false,true),false,"invalid trip without shift");
        verify(ShiftEndPolicy.canEndShift(true,true),false,"trip in progress");
        verify(ShiftEndPolicy.canEndShift(true,false),true,"active shift without trip");
        System.out.println("ShiftEndPolicy tests OK");
    }
}
