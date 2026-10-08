package uy.com.mapatrayectos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.Map;

/** Standalone branded PNG card produced locally, no website or external image download. */
public final class BusinessCardImage {
    public static final String CONTACT_URL="https://wa.me/59897228175";
    public static final String PHONE="+598 97 228 175";
    public static final int WIDTH=1280, HEIGHT=720;

    private static final int NAVY=Color.rgb(5,55,64);
    private static final int SEA=Color.rgb(13,106,113);
    private static final int IVORY=Color.rgb(247,242,231);
    private static final int GOLD=Color.rgb(227,181,89);
    private static final int INK=Color.rgb(12,53,62);
    private BusinessCardImage(){}

    public static Bitmap render(Context context) throws Exception {
        Bitmap bmp=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,WIDTH,HEIGHT,Color.rgb(255,251,242),
                IVORY,Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,p);
        p.setShader(null);

        p.setShader(new LinearGradient(0,0,830,385,NAVY,SEA,Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,374,p);
        p.setShader(null);

        // A subtle architectural silhouette evokes a metropolitan skyline without
        // presenting an unrelated city's photograph as an authentic local location.
        final int[] top={231,208,268,196,179,239,191,252,163,222,205,246,183};
        final int[] widths={65,48,55,77,58,53,39,66,62,51,77,44,73};
        int x=0;
        p.setColor(Color.argb(65,109,184,181));
        for(int i=0;i<top.length;i++){
            c.drawRect(x,top[i],x+widths[i],374,p);
            if(i%3==0){
                p.setColor(Color.argb(80,213,200,148));
                c.drawRect(x+widths[i]/2,top[i]-22,x+widths[i]/2+3,top[i],p);
                p.setColor(Color.argb(65,109,184,181));
            }
            x+=widths[i]+6;
        }
        p.setColor(Color.argb(55,255,255,255));
        for(int row=0;row<3;row++){
            for(int col=0;col<11;col++){
                int wx=20+col*77,wy=284+row*24;
                c.drawRoundRect(new RectF(wx,wy,wx+7,wy+4),2,2,p);
            }
        }

        p.setColor(GOLD);c.drawRect(0,372,WIDTH,380,p);
        p.setColor(Color.argb(18,23,94,101));
        for(int k=0;k<26;k++){int a=k*53;c.drawLine(a,380,a+170,720,p);}

        // Refined mark and business name.
        p.setColor(GOLD);
        c.drawCircle(82,72,20,p);
        p.setColor(NAVY);
        c.drawCircle(82,72,11,p);
        p.setColor(GOLD);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
        c.drawLine(82,47,82,97,p);c.drawLine(57,72,107,72,p);
        p.setStyle(Paint.Style.FILL);
        label(c,p,"TRASLADOS PROGRAMADOS",128,82,28,Color.rgb(255,228,166),true);

        label(c,p,"Marcelo",74,199,86,Color.WHITE,true);
        label(c,p,"Fernández",74,301,86,Color.WHITE,true);
        // Approved Option C mark: car, navigation star and route. Contact-photo resource.
        Bitmap emblem=BitmapFactory.decodeResource(context.getResources(),R.drawable.logo_c);
        if(emblem!=null){
            Paint ep=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(emblem,null,new RectF(600,112,831,343),ep);
            emblem.recycle();
        }

        label(c,p,"VIAJES CON RESERVA",75,452,32,SEA,true);
        p.setColor(GOLD);c.drawRoundRect(new RectF(75,478,327,485),4,4,p);
        label(c,p,PHONE,74,573,61,INK,true);
        label(c,p,"MONTEVIDEO  ·  CIUDAD DE LA COSTA  ·  CANELONES",76,636,23,INK,false);

        // Minimal road motif near lower edge.
        p.setColor(Color.argb(50,15,117,124));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);
        Path route=new Path();route.moveTo(75,675);route.cubicTo(210,652,430,701,770,664);c.drawPath(route,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(GOLD);c.drawCircle(75,675,8,p);c.drawCircle(770,664,8,p);

        // Dedicated white QR card ensures contrast when delivered as an image.
        p.setShadowLayer(18,0,9,Color.argb(55,0,0,0));p.setColor(Color.WHITE);
        c.drawRoundRect(new RectF(856,120,1219,618),30,30,p);
        p.clearShadowLayer();
        label(c,p,"CONTACTO DIRECTO",901,185,24,INK,true);
        drawQr(c,p,CONTACT_URL,900,211,270);
        label(c,p,"ESCANEÁ EL QR",914,536,24,SEA,true);
        label(c,p,"Y ESCRIBIME",931,574,20,INK,false);
        return bmp;
    }

    private static void drawQr(Canvas c,Paint p,String value,int x,int y,int size) throws Exception {
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN,2);
        hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
        BitMatrix qr=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size,hints);
        p.setColor(Color.WHITE);c.drawRect(x-12,y-12,x+size+12,y+size+12,p);
        p.setColor(INK);
        for(int j=0;j<size;j++)for(int i=0;i<size;i++){
            if(qr.get(i,j))c.drawRect(x+i,y+j,x+i+1,y+j+1,p);
        }
    }
    private static void label(Canvas c,Paint p,String s,float x,float y,float font,int color,boolean bold){
        p.setColor(color);p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        p.setTextSize(font);
        c.drawText(s,x,y,p);
    }

    public static File writeToCache(Context context) throws Exception {
        File dir=new File(context.getCacheDir(),"shared_contacts");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Directorio inaccesible");
        File file=new File(dir,"Marcelo_Fernandez_Traslados.png");
        Bitmap image=render(context);
        try(FileOutputStream out=new FileOutputStream(file)){
            if(!image.compress(Bitmap.CompressFormat.PNG,100,out))
                throw new IllegalStateException("No se pudo codificar la tarjeta");
        }finally{image.recycle();}
        return file;
    }
}
