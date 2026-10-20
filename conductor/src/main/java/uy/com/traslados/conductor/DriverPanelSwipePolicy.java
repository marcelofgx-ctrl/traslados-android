package uy.com.traslados.conductor;
/** Only interior left-edge swipes navigate back; vertical reservation lists
    and Android's system edge keep their original touch behavior. */
public final class DriverPanelSwipePolicy {
  private DriverPanelSwipePolicy(){}
  public static boolean goBack(float x0,float y0,float x1,float y1,
      float width,float height,float density){
    if(width<=0||height<=0||density<=0)return false;
    if(y0<185*density||y0>height-190*density)return false;
    if(x0<24*density||x0>105*density)return false;
    float dx=x1-x0,dy=y1-y0;
    return dx>=78*density&&dx>Math.abs(dy)*1.65f;
  }
}
