package uy.com.mapatrayectos;

/** Horizontal page navigation without intercepting map panning or shift sliders.
 * Only accept swipes beginning in an interior edge band, away from Android's
 * system-back gesture and outside the header/bottom journey controls.
 */
public final class UnifiedDriverSwipePolicy {
    private UnifiedDriverSwipePolicy(){}
    public static boolean isPageSwipe(float x0,float y0,float x1,float y1,
         float screenWidth,float screenHeight,float density,boolean toDriver){
        if(density<=0||screenWidth<=0||screenHeight<=0)return false;
        final float edgeInset=24*density,edgeBand=105*density;
        if(y0<185*density||y0>screenHeight-190*density)return false;
        if(toDriver){
            if(x0<screenWidth-edgeBand||x0>screenWidth-edgeInset)return false;
        }else{
            if(x0<edgeInset||x0>edgeBand)return false;
        }
        float dx=x1-x0,dy=y1-y0;
        return (toDriver?dx<0:dx>0)
             &&Math.abs(dx)>=78*density
             &&Math.abs(dx)>Math.abs(dy)*1.65f;
    }
}
