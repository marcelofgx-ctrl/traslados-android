import uy.com.traslados.conductor.DriverPanelSwipePolicy;
public final class DriverPanelSwipePolicyTest {
  private static void ok(boolean b){if(!b)throw new AssertionError("Back-page swipe policy");}
  public static void main(String[]args){
    ok(DriverPanelSwipePolicy.goBack(60,300,190,301,400,840,1));
    ok(!DriverPanelSwipePolicy.goBack(2,300,190,300,400,840,1));
    ok(!DriverPanelSwipePolicy.goBack(60,300,170,550,400,840,1));
    ok(!DriverPanelSwipePolicy.goBack(60,710,190,710,400,840,1));
    System.out.println("OK: Conductor swipe right returns to Mapa without list/bottom conflicts");
  }
}
