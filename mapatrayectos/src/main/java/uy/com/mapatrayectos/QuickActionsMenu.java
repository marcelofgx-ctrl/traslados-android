package uy.com.mapatrayectos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import android.util.Base64;

public final class QuickActionsMenu {
    private static final String PHONE="+598 97 228 175";
    private static final String BOOKING_URL="https://traslados-con-reserva.lovable.app/";
    private static final String DOWNLOAD_URL="https://traslados-con-reserva.lovable.app/descargas";
    private static final int GOLD=Color.rgb(231,202,130),WHITE=Color.rgb(247,246,241),
            PETROL=Color.rgb(7,43,51),PETROL_LIGHT=Color.rgb(9,66,74);
    private static PopupWindow currentPopup;

    private QuickActionsMenu(){}

    public static void show(Activity activity, View anchor){
        if(currentPopup!=null&&currentPopup.isShowing()){
            dismiss(currentPopup,null);
            return;
        }
        final LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity,8),dp(activity,7),dp(activity,8),dp(activity,8));
        content.setBackground(new TexturedDrawable(activity,PETROL,PETROL_LIGHT,Color.argb(210,231,202,130),17f,0.8f,false));

        TextView header=text(activity,"ACCIONES",10,GOLD,true);
        header.setLetterSpacing(0.12f);
        header.setPadding(dp(activity,11),0,0,0);
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(activity,28)));

        PopupWindow popup=new PopupWindow(content,dp(activity,207),-2,true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(activity,9));
        popup.setOutsideTouchable(true);
        popup.setOnDismissListener(()->{if(currentPopup==popup)currentPopup=null;});

        add(activity,content,"↗", "Enviar tarjeta",popup,()->askPhone(activity));
        add(activity,content,"▣", "Contacto .VCF",popup,()->shareVcard(activity));
        add(activity,content,"⌁", "Link de reservas",popup,()->shareLink(activity,"Reservas de traslados",BOOKING_URL));
        add(activity,content,"⇩", "Link de descarga",popup,()->shareLink(activity,"Descargar aplicaciones de Traslados",DOWNLOAD_URL));

        currentPopup=popup;
        content.setAlpha(0f);
        content.setTranslationY(-dp(activity,11));
        try {
            // Align to the right edge of the small floating control; never anchor at screen center.
            popup.showAsDropDown(anchor,-dp(activity,163),dp(activity,8));
            content.animate().alpha(1f).translationY(0f).setDuration(200L)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } catch(Exception e){
            currentPopup=null;
            Toast.makeText(activity,"No se pudo abrir el menú",Toast.LENGTH_SHORT).show();
        }
    }

    private static void add(Activity a, LinearLayout group, String icon, String label,
                            PopupWindow popup, Runnable action){
        LinearLayout row=new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(itemBackground());
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(a,40));
        rp.bottomMargin=dp(a,3);
        group.addView(row,rp);
        TextView ic=text(a,icon,15,GOLD,true);
        ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(dp(a,35),-1));
        TextView name=text(a,label,13.1f,WHITE,false);
        row.addView(name,new LinearLayout.LayoutParams(0,-1,1));
        name.setGravity(Gravity.CENTER_VERTICAL);
        row.setContentDescription(label);
        row.setOnClickListener(v->dismiss(popup,action));
    }

    private static GradientDrawable itemBackground(){
        GradientDrawable b=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(50,22,93,100),Color.argb(17,10,57,63)});
        b.setCornerRadius(12f);
        b.setStroke(1,Color.argb(55,231,202,130));
        return b;
    }

    private static TextView text(Activity a,String value,float size,int color,boolean bold){
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }

    private static void dismiss(PopupWindow popup,Runnable callback){
        if(popup==null||!popup.isShowing()){
            if(callback!=null)callback.run();
            return;
        }
        View c=popup.getContentView();
        c.animate().cancel();
        c.animate().alpha(0f).translationY(-c.getResources().getDisplayMetrics().density*7f)
                .setInterpolator(new DecelerateInterpolator()).setDuration(115L)
                .withEndAction(()->{
                    try{popup.dismiss();}catch(Exception ignored){}
                    if(callback!=null)callback.run();
                }).start();
    }

    private static void askPhone(Activity a){
        EditText phone=new EditText(a);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setSingleLine(true);
        phone.setTextSize(14);
        phone.setHint("Número (opcional)");
        LinearLayout wrapper=new LinearLayout(a);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(a,18),dp(a,4),dp(a,18),0);

        Bitmap thumbnail=null;
        try{
            Bitmap full=BusinessCardImage.render(a);
            thumbnail=Bitmap.createScaledBitmap(full,720,405,true);
            full.recycle();
            ImageView image=new ImageView(a);
            image.setImageBitmap(thumbnail);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(-1,dp(a,176));
            imageLp.bottomMargin=dp(a,8);
            wrapper.addView(image,imageLp);
        }catch(Exception e){
            Toast.makeText(a,"La vista previa no está disponible",Toast.LENGTH_SHORT).show();
        }
        wrapper.addView(phone,new LinearLayout.LayoutParams(-1,dp(a,51)));
        final Bitmap toRecycle=thumbnail;
        AlertDialog dialog=new AlertDialog.Builder(a)
            .setTitle("Tarjeta de traslados")
            .setMessage("Compartí la imagen con QR desde WhatsApp. Allí elegís el destinatario y confirmás el envío. El número sirve para abrir un chat por separado.")
            .setView(wrapper)
            .setNegativeButton("CANCELAR",null)
            .setNeutralButton("ABRIR CHAT",null)
            .setPositiveButton("COMPARTIR IMAGEN",null)
            .create();
        dialog.setOnDismissListener(d->{if(toRecycle!=null&&!toRecycle.isRecycled())toRecycle.recycle();});
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                String normalized=normalizePhone(phone.getText().toString());
                if(normalized==null){phone.setError("Ingresá un teléfono válido");return;}
                if(openWhatsApp(a,normalized))dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(shareBusinessImage(a))dialog.dismiss();
            });
        });
        dialog.show();
    }

    /** WhatsApp attachment recipient selection must happen in WhatsApp itself. */
    private static boolean shareBusinessImage(Activity a){
        try{
            File image=BusinessCardImage.writeToCache(a);
            Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".share",image);
            Intent share=new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM,uri);
            share.putExtra(Intent.EXTRA_TEXT,
                "Marcelo Fernández · Traslados programados\n"+PHONE+
                "\nReservas por WhatsApp: "+BusinessCardImage.CONTACT_URL);
            share.setClipData(ClipData.newUri(a.getContentResolver(),"Tarjeta de Traslados",uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setPackage("com.whatsapp");
            try{a.startActivity(share);return true;}
            catch(Exception noPersonal){
                share.setPackage("com.whatsapp.w4b");
                try{a.startActivity(share);return true;}
                catch(Exception noBusiness){
                    share.setPackage(null);
                    a.startActivity(Intent.createChooser(share,"Compartir tarjeta visual"));
                    return true;
                }
            }
        }catch(Exception e){
            Toast.makeText(a,"No se pudo preparar la imagen: "+e.getMessage(),Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static boolean openWhatsApp(Activity a,String number){
        String message="*Marcelo Fernández*\n"
                +"Traslados programados\n"
                +"Tel.: "+PHONE+"\n"
                +"Guardá mi contacto y escribime cuando necesites un traslado.";
        try{
            Uri uri=Uri.parse("https://wa.me/"+number+"?text="+URLEncoder.encode(message,"UTF-8"));
            Intent direct=new Intent(Intent.ACTION_VIEW,uri);
            direct.setPackage("com.whatsapp");
            try{a.startActivity(direct);return true;}
            catch(Exception ignored){
                Intent business=new Intent(Intent.ACTION_VIEW,uri);
                business.setPackage("com.whatsapp.w4b");
                try{a.startActivity(business);return true;}
                catch(Exception ignoredBusiness){
                    a.startActivity(new Intent(Intent.ACTION_VIEW,uri));return true;
                }
            }
        }catch(Exception e){
            Toast.makeText(a,"No se pudo abrir WhatsApp. Revisá el teléfono y la instalación.",Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static String normalizePhone(String raw){
        String number=raw.replaceAll("[^0-9]","");
        if(number.startsWith("00"))number=number.substring(2);
        if(number.startsWith("0")&&number.length()==9)number="598"+number.substring(1);
        if(number.length()<9||number.length()>15)return null;
        return number;
    }

    private static void shareVcard(Activity a){
        try{
            StringBuilder vcard=new StringBuilder();
            vcard.append("BEGIN:VCARD\r\nVERSION:3.0\r\n");
            vcard.append("N:Fernández;Marcelo;;;\r\n");
            vcard.append("FN:Marcelo Fernández\r\n");
            vcard.append("ORG:Traslados programados\r\n");
            vcard.append("TEL;TYPE=CELL:+59897228175\r\n");
            vcard.append("URL:").append(BusinessCardImage.CONTACT_URL).append("\r\n");
            vcard.append("NOTE:Traslados programados. Montevideo, Ciudad de la Costa y Canelones.\r\n");
            // PHOTO is embedded (not an external link), so contact importers can retain the logo.
            byte[] photo;
            try(InputStream in=a.getResources().openRawResource(R.drawable.logo_c);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1)bytes.write(buf,0,n);
                photo=bytes.toByteArray();
            }
            if(photo.length<1024||photo.length>512000)throw new IllegalStateException("Fotografía de contacto inválida");
            String photoData=Base64.encodeToString(photo,Base64.NO_WRAP);
            String line="PHOTO;ENCODING=b;TYPE=JPEG:"+photoData;
            // vCard 3.0 logical line folding; every continuation begins with one space.
            int pos=0;while(pos<line.length()){
                int room=pos==0?74:73;
                int next=Math.min(pos+room,line.length());
                if(pos>0)vcard.append(" ");
                vcard.append(line,pos,next).append("\r\n");
                pos=next;
            }
            vcard.append("END:VCARD\r\n");
            File directory=new File(a.getCacheDir(),"shared_contacts");
            if(!directory.exists()&&!directory.mkdirs())throw new IllegalStateException("No se pudo crear el directorio");
            File file=new File(directory,"Marcelo_Fernandez_Contacto.vcf");
            try(FileOutputStream out=new FileOutputStream(file)){
                out.write(vcard.toString().getBytes(StandardCharsets.UTF_8));
            }
            Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".share",file);
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/x-vcard");
            send.putExtra(Intent.EXTRA_STREAM,uri);
            send.setClipData(ClipData.newUri(a.getContentResolver(),"Contacto Marcelo",uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(send,"Enviar contacto con logo C"));
        }catch(Exception e){
            Toast.makeText(a,"No se pudo compartir el archivo VCF: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private static void shareLink(Activity a,String subject,String url){
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,subject);
        send.putExtra(Intent.EXTRA_TEXT,subject+"\n"+url);
        try{a.startActivity(Intent.createChooser(send,"Compartir enlace"));}
        catch(Exception e){Toast.makeText(a,"No hay aplicaciones disponibles para compartir",Toast.LENGTH_SHORT).show();}
    }

    private static int dp(Activity a,int d){return Math.round(d*a.getResources().getDisplayMetrics().density);}
}
