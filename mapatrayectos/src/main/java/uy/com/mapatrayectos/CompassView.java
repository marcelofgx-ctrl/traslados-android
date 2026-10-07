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
        p.setShadowLayer(dp(6),0,dp(2),Color.argb(105,0,0,0));
        p.setShader(new RadialGradient(cx,cy,r,Color.rgb(12,56,64),Color.rgb(5,23,29),Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,r,p);p.clearShadowLayer();p.setShader(null);

        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.6f));p.setColor(Color.rgb(232,192,84));c.drawCircle(cx,cy,r-dp(1),p);
        p.setStrokeWidth(dp(.8f));p.setColor(Color.argb(145,222,237,238));
        for(int i=0;i<12;i++){double a=Math.toRadians(i*30-90);float r1=r-dp(i%3==0?7:5),r2=r-dp(2);c.drawLine(cx+(float)Math.cos(a)*r1,cy+(float)Math.sin(a)*r1,cx+(float)Math.cos(a)*r2,cy+(float)Math.sin(a)*r2,p);}

        c.save();c.rotate(-mapBearing,cx,cy);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(244,191,67));
        Path north=new Path();north.moveTo(cx,cy-r+dp(10));north.lineTo(cx-dp(5),cy);north.lineTo(cx+dp(5),cy);north.close();c.drawPath(north,p);
        p.setColor(Color.rgb(112,210,236));Path south=new Path();south.moveTo(cx,cy+r-dp(10));south.lineTo(cx-dp(4.5f),cy);south.lineTo(cx+dp(4.5f),cy);south.close();c.drawPath(south,p);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(dp(9.5f));p.setColor(Color.rgb(238,196,84));c.drawText("N",cx,cy-r+dp(17),p);p.setTextSize(dp(8));p.setColor(Color.rgb(183,203,207));c.drawText("S",cx,cy+r-dp(8),p);c.restore();

        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(73,199,225));c.drawCircle(cx,cy,dp(2.5f),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.WHITE);c.drawCircle(cx,cy,dp(3.8f),p);
    }

    private float normalize(float v){float x=v%360f;return x<0?x+360f:x;}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
