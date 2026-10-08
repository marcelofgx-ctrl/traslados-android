package uy.com.mapatrayectos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

public final class QuickActionsMenu {
    private static final String PHONE="+598 97 228 175";
    private static final String BOOKING_URL="https://traslados-con-reserva.lovable.app/";
    private static final String DOWNLOAD_URL="https://traslados-con-reserva.lovable.app/descargas";
    private static final int BG=Color.rgb(8,49,57), GOLD=Color.rgb(231,202,130), WHITE=Color.rgb(247,246,241);
    private QuickActionsMenu(){}

    public static void show(Activity activity, View anchor) {
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12));
        content.setBackground(new TexturedDrawable(activity,Color.rgb(7,46,54),Color.rgb(10,72,78),Color.argb(210,231,202,130),20f,1f,false));
        TextView title=item(activity,"ACCIONES RÁPIDAS",12,GOLD);
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(activity,40)));
        PopupWindow popup=new PopupWindow(content,dp(activity,254),-2,true);
        popup.setBackgroundDrawable(new TexturedDrawable(activity,Color.rgb(7,46,54),Color.rgb(10,72,78),Color.argb(210,231,202,130),20f,1f,false));
        popup.setElevation(dp(activity,12));
        popup.setOutsideTouchable(true);
        add(activity,content,"✉   Enviar tarjeta por WhatsApp",popup,()->askPhone(activity));
        add(activity,content,"▣   Compartir contacto (.VCF)",popup,()->shareVcard(activity,null));
        add(activity,content,"⌁   Compartir reservas",popup,()->shareLink(activity,"Reservas de traslados",BOOKING_URL));
        add(activity,content,"⇩   Compartir descargas",popup,()->shareLink(activity,"Descargar aplicaciones de Traslados",DOWNLOAD_URL));
        int y=Math.max(dp(activity,100),(int)anchor.getY()-dp(activity,72));
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.RIGHT,dp(activity,12),y);
    }
    private static void add(Activity a,LinearLayout content,String label,PopupWindow p,Runnable action){
        TextView button=item(a,label,14,WHITE);button.setBackgroundColor(Color.argb(30,217,194,128));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,49));lp.bottomMargin=dp(a,5);content.addView(button,lp);
        button.setOnClickListener(v->{p.dismiss();action.run();});
    }
    private static TextView item(Activity a,String s,int size,int color){
        TextView v=new TextView(a);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(a,12),0,dp(a,8),0);
        return v;
    }
    private static void askPhone(Activity a){
        EditText input=new EditText(a);input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setSingleLine(true);input.setHint("Ej.: 099 123 456 / +598 99 123 456");
        LinearLayout wrap=new LinearLayout(a);wrap.setPadding(dp(a,22),dp(a,5),dp(a,22),0);wrap.addView(input);
        new AlertDialog.Builder(a).setTitle("Enviar mi tarjeta")
            .setMessage("Ingresá el teléfono del destinatario. WhatsApp abrirá el envío para que lo confirmes.")
            .setView(wrap).setNegativeButton("CANCELAR",null)
            .setNeutralButton("ENVIAR TEXTO",(d,w)->{
                String number=normalizePhone(input.getText().toString());
                if(number==null){Toast.makeText(a,"Número inválido",Toast.LENGTH_LONG).show();return;}
                String text="Marcelo Fernández\nTraslados con Reserva\nTel: "+PHONE+"\nReservas: "+BOOKING_URL;
                try{
                    Uri url=Uri.parse("https://wa.me/"+number+"?text="+URLEncoder.encode(text,"UTF-8"));
                    a.startActivity(new Intent(Intent.ACTION_VIEW,url));
                }catch(Exception e){Toast.makeText(a,"No se pudo abrir WhatsApp",Toast.LENGTH_LONG).show();}
            })
            .setPositiveButton("ENVIAR VCF",(d,w)->{
                String number=normalizePhone(input.getText().toString());
                if(number==null){Toast.makeText(a,"Número inválido",Toast.LENGTH_LONG).show();return;}
                shareVcard(a,number);
            }).show();
    }
    private static String normalizePhone(String source){
        String d=source.replaceAll("[^0-9]","");
        if(d.startsWith("0")&&d.length()==9)d="598"+d.substring(1);
        if(d.length()<9||d.length()>15)return null;
        return d;
    }
    private static void shareVcard(Activity a,String recipient){
        try{
            String vcard="BEGIN:VCARD\r\nVERSION:3.0\r\nN:Fernandez;Marcelo;;;\r\nFN:Marcelo Fernández\r\nORG:Traslados con Reserva\r\nTITLE:Traslados programados\r\nTEL;TYPE=CELL:+59897228175\r\nURL:"+BOOKING_URL+"\r\nNOTE:Aeropuerto, larga distancia y traslados programados. Montevideo, Ciudad de la Costa, Canelones.\r\nEND:VCARD\r\n";
            File dir=new File(a.getCacheDir(),"shared_contacts");
            if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("No se pudo crear el contacto");
            File vcf=new File(dir,"Marcelo_Fernandez_Contacto.vcf");
            try(FileOutputStream f=new FileOutputStream(vcf)){f.write(vcard.getBytes(StandardCharsets.UTF_8));}
            Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".share",vcf);
            Intent send=new Intent(Intent.ACTION_SEND).setType("text/x-vcard");
            send.putExtra(Intent.EXTRA_STREAM,uri);send.setClipData(ClipData.newUri(a.getContentResolver(),"Contacto de Marcelo",uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(recipient!=null){
                send.setPackage("com.whatsapp");
                // WhatsApp may not honor the recipient hint; user confirms the chat before sending.
                send.putExtra("jid",recipient+"@s.whatsapp.net");
                try{a.startActivity(send);return;}catch(Exception ignored){send.setPackage(null);}
            }
            a.startActivity(Intent.createChooser(send,"Compartir contacto VCF"));
        }catch(Exception e){Toast.makeText(a,"No se pudo compartir el contacto: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private static void shareLink(Activity a,String subject,String url){
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,subject);
        send.putExtra(Intent.EXTRA_TEXT,subject+"\n"+url);
        a.startActivity(Intent.createChooser(send,"Compartir enlace"));
    }
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
