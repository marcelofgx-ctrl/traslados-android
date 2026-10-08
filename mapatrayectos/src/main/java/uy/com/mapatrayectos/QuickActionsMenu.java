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
import android.provider.ContactsContract;
import android.text.TextUtils;
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

        add(activity,content,"▣", "Tarjeta visual",popup,()->showCard(activity));
        add(activity,content,"↗", "WhatsApp a número",popup,()->showWhatsAppNumber(activity));
        add(activity,content,"+", "Guardar pasajero",popup,()->savePassenger(activity));
        add(activity,content,"◷", "Recordatorios",popup,()->activity.startActivity(new Intent(activity,ReminderActivity.class)));
        add(activity,content,"⌁", "Enlaces y contacto",popup,()->showShareHub(activity));

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
        row.setOnClickListener(v->{FeedbackReceiver.playUiCue(a);dismiss(popup,action);});
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

    /** Main transfer action: a real image, not a text message advertised as a card. */
    private static void showCard(Activity a){
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,16),dp(a,8),dp(a,16),dp(a,9));
        Bitmap thumb=null;
        try{
            Bitmap full=BusinessCardImage.render(a);
            thumb=Bitmap.createScaledBitmap(full,900,506,true);
            full.recycle();
            ImageView preview=new ImageView(a);
            preview.setImageBitmap(thumb);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(-1,dp(a,205));
            imageLp.bottomMargin=dp(a,6);
            content.addView(preview,imageLp);
        }catch(Exception e){
            Toast.makeText(a,"No se pudo obtener la vista previa",Toast.LENGTH_SHORT).show();
        }
        TextView hint=text(a,
            "Se enviará esta imagen con el logo C y el QR. WhatsApp te permitirá elegir el destinatario y confirmar.",
            12.5f,Color.rgb(48,74,79),false);
        hint.setPadding(dp(a,6),dp(a,4),dp(a,6),dp(a,11));
        content.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        final Bitmap toRecycle=thumb;
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Mi tarjeta de traslados")
            .setView(content).create();
        addDialogAction(a,content,"▣  COMPARTIR POR WHATSAPP",true,()->{
            if(shareBusinessImage(a))dialog.dismiss();
        });
        addDialogAction(a,content,"▤  CONTACTO PARA GUARDAR (.VCF)",false,()->{
            if(shareVcard(a))dialog.dismiss();
        });
        addDialogAction(a,content,"CANCELAR",false,dialog::dismiss);
        dialog.setOnDismissListener(d->{
            if(toRecycle!=null&&!toRecycle.isRecycled())toRecycle.recycle();
        });
        dialog.show();
    }

    /** Click-to-chat works for a number even when not saved to Contacts. No image attachment. */
    private static void showWhatsAppNumber(Activity a){
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,18),dp(a,7),dp(a,18),dp(a,10));
        TextView hint=text(a,"Abrí una conversación sin agendar al pasajero. Se prepara un saludo breve; confirmás el envío dentro de WhatsApp.",12.6f,Color.rgb(45,70,75),false);
        hint.setPadding(0,0,0,dp(a,11));
        content.addView(hint);
        EditText number=formField(a,"Número: 099 123 456 o +598…",InputType.TYPE_CLASS_PHONE);
        content.addView(number,new LinearLayout.LayoutParams(-1,dp(a,54)));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("WhatsApp a un número")
            .setView(content).create();
        addDialogAction(a,content,"↗  ABRIR WHATSAPP",true,()->{
            String normalized=normalizePhone(number.getText().toString());
            if(normalized==null){
                number.setError("Verificá el número; para otros países incluí el prefijo");
                number.requestFocus();return;
            }
            if(openWhatsApp(a,normalized))dialog.dismiss();
        });
        addDialogAction(a,content,"CANCELAR",false,dialog::dismiss);
        dialog.show();
    }

    /** Contacts app reviews the proposed passenger. This app never writes contacts silently. */
    private static void savePassenger(Activity a){
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,18),dp(a,8),dp(a,18),dp(a,10));
        TextView hint=text(a,"Completá los datos y confirmá el guardado en la agenda del teléfono.",12.6f,Color.rgb(49,73,76),false);
        hint.setPadding(0,0,0,dp(a,6));content.addView(hint);
        TextView nameLabel=text(a,"NOMBRE DEL PASAJERO",11.2f,PETROL,true);
        content.addView(nameLabel);
        EditText name=formField(a,"Nombre y apellido",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        content.addView(name,new LinearLayout.LayoutParams(-1,dp(a,52)));
        TextView phoneLabel=text(a,"TELÉFONO",11.2f,PETROL,true);
        phoneLabel.setPadding(0,dp(a,8),0,0);content.addView(phoneLabel);
        EditText phone=formField(a,"099 123 456 o +598 99…",InputType.TYPE_CLASS_PHONE);
        content.addView(phone,new LinearLayout.LayoutParams(-1,dp(a,52)));
        TextView companyLabel=text(a,"EMPRESA (OPCIONAL)",11.2f,PETROL,true);
        companyLabel.setPadding(0,dp(a,8),0,0);content.addView(companyLabel);
        EditText company=formField(a,"Empresa",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        content.addView(company,new LinearLayout.LayoutParams(-1,dp(a,52)));

        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Guardar pasajero")
            .setView(content).create();
        addDialogAction(a,content,"+  ABRIR AGENDA Y GUARDAR",true,()->{
            String fullName=name.getText().toString().trim();
            if(TextUtils.isEmpty(fullName)){name.setError("Ingresá el nombre");name.requestFocus();return;}
            String normalized=normalizePhone(phone.getText().toString());
            if(normalized==null){phone.setError("Número inválido");phone.requestFocus();return;}
            Intent insert=new Intent(Intent.ACTION_INSERT);
            insert.setType(ContactsContract.Contacts.CONTENT_TYPE);
            insert.putExtra(ContactsContract.Intents.Insert.NAME,fullName);
            insert.putExtra(ContactsContract.Intents.Insert.PHONE,"+"+normalized);
            String org=company.getText().toString().trim();
            if(!org.isEmpty())insert.putExtra(ContactsContract.Intents.Insert.COMPANY,org);
            try{a.startActivity(insert);dialog.dismiss();}
            catch(Exception error){Toast.makeText(a,"No se pudo abrir la agenda de contactos",Toast.LENGTH_LONG).show();}
        });
        addDialogAction(a,content,"CANCELAR",false,dialog::dismiss);
        dialog.getWindow();
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private static EditText formField(Activity a,String hint,int type){
        EditText input=new EditText(a);
        input.setInputType(type);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(Color.rgb(15,51,59));
        input.setHintTextColor(Color.rgb(112,124,125));
        input.setHint(hint);
        return input;
    }

    private static void addDialogAction(Activity a,LinearLayout content,String label,
                                        boolean primary,Runnable callback){
        TextView button=dialogAction(a,label,primary);
        content.addView(button,dialogButtonLp(a));
        button.setOnClickListener(v->callback.run());
    }

    /** Keeps the quick menu small while preserving all previous sharing functions. */
    private static void showShareHub(Activity a){
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,18),dp(a,9),dp(a,18),dp(a,12));
        TextView hint=text(a,"Compartí tu contacto o los enlaces del emprendimiento.",12.7f,Color.rgb(43,72,78),false);
        hint.setPadding(0,0,0,dp(a,8));content.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Enlaces y contacto")
            .setView(content).create();
        addDialogAction(a,content,"▤  CONTACTO .VCF",true,()->{
            if(shareVcard(a))dialog.dismiss();
        });
        addDialogAction(a,content,"⌁  WEB DE RESERVAS",false,()->{
            dialog.dismiss();shareEditableLink(a,"booking_url","Reservas de traslados",BOOKING_URL);
        });
        addDialogAction(a,content,"⇩  DESCARGAR LA APP",false,()->{
            dialog.dismiss();shareEditableLink(a,"download_url","Enlace de descarga de la aplicación",DOWNLOAD_URL);
        });
        addDialogAction(a,content,"CANCELAR",false,dialog::dismiss);
        dialog.show();
    }

    /** Project URL was never verified; require human confirmation before first share. */
    private static void shareEditableLink(Activity a,String key,String title,String proposed){
        android.content.SharedPreferences prefs=a.getSharedPreferences("share_links",Activity.MODE_PRIVATE);
        String current=prefs.getString(key,proposed);
        if(prefs.getBoolean(key+"_confirmed",false)&&validUrl(current)){
            shareLink(a,title,current);return;
        }
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,18),dp(a,7),dp(a,18),dp(a,9));
        TextView notice=text(a,
            "Este enlace todavía no está confirmado. Revisalo o reemplazalo por la dirección real antes de compartir.",
            12.5f,Color.rgb(50,76,80),false);
        content.addView(notice);
        EditText url=formField(a,"https://…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(current);
        content.addView(url,new LinearLayout.LayoutParams(-1,dp(a,60)));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title)
            .setView(content).create();
        addDialogAction(a,content,"COMPARTIR Y RECORDAR ENLACE",true,()->{
            String value=url.getText().toString().trim();
            if(!validUrl(value)){url.setError("Ingresá una dirección HTTPS válida");return;}
            prefs.edit().putString(key,value).putBoolean(key+"_confirmed",true).apply();
            dialog.dismiss();shareLink(a,title,value);
        });
        addDialogAction(a,content,"CANCELAR",false,dialog::dismiss);
        dialog.show();
    }

    private static boolean validUrl(String value){
        try{
            Uri uri=Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                &&uri.getHost()!=null&&uri.getHost().contains(".");
        }catch(Exception e){return false;}
    }

    private static LinearLayout.LayoutParams dialogButtonLp(Activity a){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,46));
        lp.bottomMargin=dp(a,7);
        return lp;
    }

    private static TextView dialogAction(Activity a,String label,boolean primary){
        TextView button=text(a,label,13.2f,
            primary?Color.rgb(7,43,51):Color.rgb(247,246,241),true);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            primary?new int[]{Color.rgb(238,206,134),Color.rgb(218,174,85)}
                   :new int[]{Color.rgb(9,70,78),Color.rgb(6,47,56)});
        bg.setCornerRadius(dp(a,13));
        bg.setStroke(dp(a,1),Color.rgb(189,159,96));
        button.setBackground(bg);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
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
        String message="Hola, soy Marcelo Fernández. Te dejo mi contacto para cuando necesites un traslado: "+PHONE+". ¡Saludos!";
        try{
            String encoded=URLEncoder.encode(message,"UTF-8");
            // Official click-to-chat link works for unlisted numbers. WhatsApp still requires
            // the user to confirm the outgoing message; attachments cannot be injected here.
            Uri first=Uri.parse("https://api.whatsapp.com/send?phone="+number+"&text="+encoded);
            Intent direct=new Intent(Intent.ACTION_VIEW,first);
            try{
                a.startActivity(direct);
                return true;
            }catch(Exception firstError){
                Uri fallback=Uri.parse("https://wa.me/"+number+"?text="+encoded);
                a.startActivity(new Intent(Intent.ACTION_VIEW,fallback));
                return true;
            }
        }catch(Exception e){
            Toast.makeText(a,"No se pudo abrir WhatsApp. Verificá el número o la aplicación.",Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static String normalizePhone(String raw){
        String number=raw.replaceAll("[^0-9]","");
        if(number.startsWith("00"))number=number.substring(2);
        if(number.startsWith("0")&&number.length()==9)number="598"+number.substring(1);
        else if(number.length()==8&&number.startsWith("9"))number="598"+number;
        if(number.length()<9||number.length()>15)return null;
        return number;
    }

    private static boolean shareVcard(Activity a){
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
            return true;
        }catch(Exception e){
            Toast.makeText(a,"No se pudo compartir el archivo VCF: "+e.getMessage(),Toast.LENGTH_LONG).show();
            return false;
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
