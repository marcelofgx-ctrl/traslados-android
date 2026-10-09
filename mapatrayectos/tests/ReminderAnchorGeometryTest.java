import uy.com.mapatrayectos.ReminderAnchorGeometry;

/** Headless deterministic checks: every visible tail touches the matching anchor. */
public final class ReminderAnchorGeometryTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static ReminderAnchorGeometry.Placement place(int x,int y,int size,int w,int h){
        return ReminderAnchorGeometry.place(x,y,size,w,h,203,150,22,10,4);
    }
    private static void verifyTip(ReminderAnchorGeometry.Placement p,int x,int y,int size){
        check(p!=null,"expected attached placement");
        int actualTipX=p.tailOnLeft?p.x:p.x+203;
        int expectedTipX=p.tailOnLeft?x+size-1:x+1;
        check(actualTipX==expectedTipX,"detached horizontal tip "+actualTipX+" vs "+expectedTipX);
        check(p.y+p.tailCenterY==y+size/2,"detached vertical tip");
        check(p.x>=0&&p.y>=0,"offscreen negative");
    }
    public static void main(String[] args){
        ReminderAnchorGeometry.Placement right=place(335,232,44,393,800);
        verifyTip(right,335,232,44);check(!right.tailOnLeft,"right-side star must point right");
        ReminderAnchorGeometry.Placement externalLeft=place(0,220,52,393,800);
        verifyTip(externalLeft,0,220,52);check(externalLeft.tailOnLeft,"left-side bubble must point left");
        ReminderAnchorGeometry.Placement moved=place(341,380,52,393,800);
        verifyTip(moved,341,380,52);check(!moved.tailOnLeft,"moved right-side bubble must flip");
        ReminderAnchorGeometry.Placement clamp=place(330,680,52,393,800);
        verifyTip(clamp,330,680,52);
        check(place(100,200,44,220,800)==null,"narrow display must refuse orphan");
        check(place(335,772,44,393,800)==null,"offscreen bottom tip must refuse orphan");
        check(place(335,0,44,393,800)!=null,"top star may connect within safe margin");
        System.out.println("R23.0 geometry PASS: foreground, left/right external, drag, vertical clamp, no-orphan fallback");
    }
}
