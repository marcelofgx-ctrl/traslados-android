package uy.com.mapatrayectos;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

public final class TexturedDrawable extends Drawable {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int c1,c2,stroke;
    private final float radius,strokePx;
    private final boolean light;

    public TexturedDrawable(Context context,int c1,int c2,int strokeColor,float radiusDp,float strokeDp,boolean light){
        this.c1=c1;this.c2=c2;this.stroke=strokeColor;this.light=light;
        float d=context.getResources().getDisplayMetrics().density;
        this.radius=radiusDp*d;
        this.strokePx=Math.max(1f,strokeDp*d);
    }

    @Override public void draw(Canvas canvas){
        Rect b=getBounds();if(b.width()<=0||b.height()<=0)return;
        RectF r=new RectF(b.left,b.top,b.right,b.bottom);

        canvas.save();
        Path clip=new Path();
        clip.addRoundRect(r,radius,radius,Path.Direction.CW);
        canvas.clipPath(clip);

        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,c1,c2,Shader.TileMode.CLAMP));
        canvas.drawRect(r,fill);
        fill.setShader(null);

        float step=Math.max(16f,r.width()/24f);
        line.setStrokeWidth(1f);
        line.setColor(light?Color.argb(14,21,83,87):Color.argb(16,255,255,255));
        for(float x=-r.height();x<r.width()+r.height();x+=step){
            canvas.drawLine(r.left+x,r.bottom,r.left+x+r.height(),r.top,line);
        }

        line.setColor(light?Color.argb(12,255,255,255):Color.argb(11,2,12,16));
        for(float y=r.top+10;y<r.bottom;y+=20f){
            canvas.drawLine(r.left+8,y,r.right-8,y,line);
        }

        if(light){
            fill.setShader(new RadialGradient(
                r.left+r.width()*0.78f,r.top+r.height()*0.12f,
                Math.max(r.width(),r.height())*0.72f,
                new int[]{Color.argb(28,255,255,255),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(r,fill);fill.setShader(null);
        }else{
            fill.setShader(new RadialGradient(
                r.left+r.width()*0.20f,r.top+r.height()*0.18f,
                Math.max(r.width(),r.height())*0.70f,
                new int[]{Color.argb(24,65,177,181),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(r,fill);fill.setShader(null);
        }

        canvas.restore();

        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(strokePx);
        fill.setColor(stroke);
        canvas.drawRoundRect(r,radius,radius,fill);
        fill.setStyle(Paint.Style.FILL);
    }

    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter colorFilter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
