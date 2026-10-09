import uy.com.mapatrayectos.BubbleClosePolicy;
public class BubbleClosePolicyTest {
    private static void yes(boolean x,String why){if(!x)throw new AssertionError(why);}
    public static void main(String[] args){
        yes(BubbleClosePolicy.mayClose(false,false,false,false),"idle may close");
        yes(!BubbleClosePolicy.mayClose(true,false,false,false),"active live shift must block");
        yes(!BubbleClosePolicy.mayClose(false,true,false,false),"active live trip must block");
        yes(!BubbleClosePolicy.mayClose(false,false,true,false),"stored shift must block");
        yes(!BubbleClosePolicy.mayClose(false,false,false,true),"stored trip must block");
        yes(!BubbleClosePolicy.mayClose(true,true,true,true),"active everything must block");
        System.out.println("R24.1 floating close safety PASS: idle and all active states");
    }
}
