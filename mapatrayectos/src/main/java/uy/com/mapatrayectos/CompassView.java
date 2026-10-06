package uy.com.mapatrayectos;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

/** Compact navigation compass used while the map is in course-up mode. */
public final class CompassView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mapBearing = 0f;

    public CompassView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Brújula");
    }

    public void setMapBearing(float bearing) {
        mapBearing = normalize(bearing);
        setContentDescription(String.format(Locale.getDefault(), "Brújula, norte a %.0f grados", normalize(360f - mapBearing)));
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight(), cx=w/2f, cy=h/2f;
        float r=Math.min(w,h)/2f-dp(2);

        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(dp(5),0,dp(2),Color.argb(90,0,0,0));
        p.setColor(Color.argb(238,7,25,31));
        c.drawCircle(cx,cy,r,p);
        p.clearShadowLayer();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1.2f));
        p.setColor(Color.argb(190,224,193,111));
        c.drawCircle(cx,cy,r-dp(1),p);

        c.save();
        c.rotate(-mapBearing,cx,cy);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(245,244,238));
        Path north=new Path();
        north.moveTo(cx,cy-r+dp(7));
        north.lineTo(cx-dp(4.5f),cy-dp(2));
        north.lineTo(cx+dp(4.5f),cy-dp(2));
        north.close();
        c.drawPath(north,p);

        p.setColor(Color.rgb(174,188,191));
        Path south=new Path();
        south.moveTo(cx,cy+r-dp(7));
        south.lineTo(cx-dp(3.5f),cy+dp(2));
        south.lineTo(cx+dp(3.5f),cy+dp(2));
        south.close();
        c.drawPath(south,p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(dp(10));
        p.setColor(Color.rgb(224,193,111));
        c.drawText("N",cx,cy-r+dp(17),p);
        c.restore();

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(73,199,225));
        c.drawCircle(cx,cy,dp(2.3f),p);
    }

    private float normalize(float v){float x=v%360f;return x<0?x+360f:x;}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
