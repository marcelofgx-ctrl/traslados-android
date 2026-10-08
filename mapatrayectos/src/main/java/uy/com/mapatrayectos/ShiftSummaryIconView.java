package uy.com.mapatrayectos;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Faithful R22.6 card icons from the approved screenshot: sun, roadway and car in champagne gold. */
public final class ShiftSummaryIconView extends View {
    public static final int SUN=0,ROAD=1,CAR=2;
    private final int kind;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int GOLD=Color.rgb(238,191,76);
    public ShiftSummaryIconView(Context c,int type){super(c);kind=type;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();float d=getResources().getDisplayMetrics().density;
        canvas.save();
        canvas.translate(w/2f,h/2f);
        float unit=Math.min(w,h)/52f;canvas.scale(unit,unit);
        p.setShader(null);p.setColor(GOLD);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(kind==SUN)drawSun(canvas);
        else if(kind==ROAD)drawRoad(canvas);
        else drawCar(canvas);
        canvas.restore();
    }
    private void drawSun(Canvas c){
        p.setStyle(Paint.Style.FILL);c.drawCircle(0,0,8.2f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.2f);
        for(int a=0;a<360;a+=45){
            double rad=Math.toRadians(a);
            float x=(float)Math.cos(rad),y=(float)Math.sin(rad);
            c.drawLine(x*13f,y*13f,x*19f,y*19f,p);
        }
    }
    private void drawRoad(Canvas c){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.6f);
        Path line=new Path();line.moveTo(-7,-19);line.cubicTo(-10,-5,-14,10,-17,19);c.drawPath(line,p);
        line.reset();line.moveTo(7,-19);line.cubicTo(10,-5,14,10,17,19);c.drawPath(line,p);
        p.setStrokeWidth(2.2f);p.setStrokeCap(Paint.Cap.SQUARE);
        c.drawLine(0,-19,0,-11,p);c.drawLine(0,-4,0,3,p);c.drawLine(0,11,0,19,p);
    }
    private void drawCar(Canvas c){
        p.setStyle(Paint.Style.FILL);
        Path car=new Path();car.moveTo(-14,-5);car.lineTo(-10,-14);car.quadTo(-9,-17,-5,-17);
        car.lineTo(5,-17);car.quadTo(9,-17,10,-14);car.lineTo(14,-5);
        car.quadTo(19,-3,19,1);car.lineTo(19,11);car.quadTo(19,13,17,13);
        car.lineTo(-17,13);car.quadTo(-19,13,-19,11);car.lineTo(-19,1);car.quadTo(-19,-3,-14,-5);
        car.close();c.drawPath(car,p);
        p.setColor(Color.rgb(6,50,58));
        Path glass=new Path();glass.moveTo(-10,-5);glass.lineTo(-7,-13);glass.lineTo(7,-13);glass.lineTo(10,-5);glass.close();c.drawPath(glass,p);
        p.setStyle(Paint.Style.FILL);c.drawRoundRect(-12,2,-6,7,1,1,p);c.drawRoundRect(6,2,12,7,1,1,p);
        p.setColor(GOLD);c.drawRoundRect(-15,12,-9,18,1.5f,1.5f,p);c.drawRoundRect(9,12,15,18,1.5f,1.5f,p);
    }
}
