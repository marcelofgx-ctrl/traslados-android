package uy.com.mapatrayectos;

/**
 * Pixel-space positioning shared by the in-map callout and the system overlay.
 * The speech-tail tip must touch the ACTIVE anchor. No clamped detached fallbacks.
 * Independent of Activity / WindowManager for deterministic geometry tests.
 */
public final class ReminderAnchorGeometry {
    private ReminderAnchorGeometry(){}

    public static final class Placement {
        public final int x, y, tailCenterY;
        public final boolean tailOnLeft;
        private Placement(int x,int y,int center,boolean left){
            this.x=x;this.y=y;tailCenterY=center;tailOnLeft=left;
        }
    }

    public static Placement place(int anchorLeft,int anchorTop,int anchorSize,
                                  int containerWidth,int containerHeight,
                                  int calloutWidth,int calloutHeight,
                                  int defaultTailCenter,int tailHalfHeight,int margin){
        if(anchorSize<=0||containerWidth<=0||containerHeight<=0
                ||calloutWidth<=0||calloutHeight<=0)return null;
        final int rightTipX=anchorLeft+1; // right-pointing tail touches anchor's left border
        final int leftTipX=anchorLeft+anchorSize-1; // left-pointing tail touches right border
        final int beforeX=rightTipX-calloutWidth;
        final int afterX=leftTipX;
        final boolean before=beforeX>=margin&&rightTipX<=containerWidth-margin;
        final boolean after=afterX>=margin&&afterX+calloutWidth<=containerWidth-margin;
        if(!before&&!after)return null;

        // Prefer to face toward the middle of the display, reversing on left-edge bubbles.
        boolean onLeft;
        if(before&&after)onLeft=anchorLeft+anchorSize/2<containerWidth/2;
        else onLeft=after;
        int x=onLeft?afterX:beforeX;

        int centerY=anchorTop+anchorSize/2;
        int lastTop=containerHeight-calloutHeight-margin;
        if(lastTop<margin)return null;
        int y=Math.max(margin,Math.min(centerY-defaultTailCenter,lastTop));
        int tailY=centerY-y;
        int tailSafe=Math.max(tailHalfHeight+margin,tailHalfHeight+2);
        if(tailY<tailSafe||tailY>calloutHeight-tailSafe)return null;
        // The tail can slide along the card while the whole card stays inside screen.
        return new Placement(x,y,tailY,onLeft);
    }
}
