package uy.com.mapatrayectos;

/**
 * Width-based sizing for time counters in equal-sized journey metric cards.
 * Pure Java for regression checks across Samsung font scales and narrow screens.
 */
public final class MetricTypographyPolicy {
    private MetricTypographyPolicy() {}

    /**
     * @param measuredAtMaxPx glyph width at maxSp, measured with actual font,
     *                        letter spacing, and horizontal scale
     * @param availablePx inner text width after card padding/safety margin
     */
    public static float fitSp(float measuredAtMaxPx,float availablePx,float maxSp,float minSp) {
        if(!Float.isFinite(measuredAtMaxPx)||!Float.isFinite(availablePx)||
                !Float.isFinite(maxSp)||!Float.isFinite(minSp)||
                maxSp<=0||minSp<=0||availablePx<=0)return minSp;
        if(measuredAtMaxPx<=availablePx)return maxSp;
        return Math.max(minSp,Math.min(maxSp,maxSp*availablePx/
            Math.max(1f,measuredAtMaxPx)*.97f));
    }
}
