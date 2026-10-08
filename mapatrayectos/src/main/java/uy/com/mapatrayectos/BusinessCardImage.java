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

    /** Refined two-tone business card, with ample negative space and a large readable QR. */
    public static Bitmap render(Context context) throws Exception {
        Bitmap bmp=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(IVORY);c.drawColor(IVORY);

        RectF card=new RectF(21,21,WIDTH-21,HEIGHT-21);
        Path clipping=new Path();
        clipping.addRoundRect(card,42,42,Path.Direction.CW);
        c.save();
        c.clipPath(clipping);
        p.setShader(new LinearGradient(21,21,820,645,NAVY,SEA,Shader.TileMode.CLAMP));
        c.drawRect(21,21,1259,699,p);
        p.setShader(null);
        p.setColor(Color.rgb(252,249,241));
        c.drawRect(808,21,1259,699,p);
        c.restore();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(Color.rgb(197,167,105));
        c.drawRoundRect(card,42,42,p);
        p.setStyle(Paint.Style.FILL);

        // The selected C emblem is the visual identity. Circular crop avoids square JPEG edges.
        Bitmap emblem=BitmapFactory.decodeResource(context.getResources(),R.drawable.logo_c);
        if(emblem!=null){
            c.save();
            Path emblemClip=new Path();
            emblemClip.addCircle(197,184,105,Path.Direction.CW);
            c.clipPath(emblemClip);
            Paint ep=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(emblem,null,new RectF(90,77,304,291),ep);
            c.restore();
            emblem.recycle();
            p.setColor(GOLD);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
            c.drawCircle(197,184,107,p);
            p.setStyle(Paint.Style.FILL);
        }

        // Left-hand content, aligned to emblem, with breathable hierarchy.
        label(c,p,"TRASLADOS PROGRAMADOS",333,151,26,Color.rgb(240,207,141),true);
        p.setColor(Color.argb(130,240,208,153));
        c.drawRect(334,179,732,182,p);
        label(c,p,"VIAJES CON RESERVA",334,230,23,IVORY,false);
        label(c,p,"Marcelo",88,405,86,Color.WHITE,true);
        label(c,p,"Fernández",88,501,86,Color.WHITE,true);
        label(c,p,PHONE,90,599,54,Color.rgb(242,213,153),true);
        p.setColor(Color.argb(115,237,221,174));
        c.drawRect(90,632,730,634,p);
        label(c,p,"MONTEVIDEO  ·  CIUDAD DE LA COSTA  ·  CANELONES",90,666,18,Color.rgb(228,233,223),false);

        // High-contrast QR: direct WhatsApp link, never an unverified website.
        label(c,p,"GUARDÁ MI CONTACTO",852,131,25,INK,true);
        p.setColor(Color.rgb(255,255,255));
        c.drawRoundRect(new RectF(859,172,1206,529),28,28,p);
        p.setColor(Color.argb(45,7,55,64));p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);c.drawRoundRect(new RectF(859,172,1206,529),28,28,p);
        p.setStyle(Paint.Style.FILL);
        drawQr(c,p,CONTACT_URL,895,201,279);
        label(c,p,"ESCANEÁ EL QR",902,575,26,SEA,true);
        label(c,p,"Y ESCRIBIME",934,616,22,INK,false);
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
