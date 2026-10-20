import uy.com.mapatrayectos.UnifiedDriverSwipePolicy;
public final class UnifiedDriverSwipePolicyTest {
  private static void pass(boolean b,String msg){if(!b)throw new AssertionError(msg);}
  public static void main(String[]args){
    float w=400f,h=840f,d=1f;
    pass(UnifiedDriverSwipePolicy.isPageSwipe(345,400,230,408,w,h,d,true),"left from right band opens driver");
    pass(UnifiedDriverSwipePolicy.isPageSwipe(56,400,160,405,w,h,d,false),"right from left band opens map");
    pass(!UnifiedDriverSwipePolicy.isPageSwipe(190,400,75,408,w,h,d,true),"center drag remains map pan");
    pass(!UnifiedDriverSwipePolicy.isPageSwipe(393,400,280,408,w,h,d,true),"system edge reserved");
    pass(!UnifiedDriverSwipePolicy.isPageSwipe(345,710,230,710,w,h,d,true),"bottom shift slider untouched");
    pass(!UnifiedDriverSwipePolicy.isPageSwipe(345,110,225,105,w,h,d,true),"header untouched");
    pass(!UnifiedDriverSwipePolicy.isPageSwipe(345,400,240,490,w,h,d,true),"vertical gesture untouched");
    pass(!UnifiedDriverSwipePolicy.isPageSwipe(345,400,310,400,w,h,d,true),"minor swipe untouched");
    System.out.println("OK: unified swipe preserves pan, controls, Android back edge");
  }
}
