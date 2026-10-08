package uy.com.mapatrayectos;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Durable personal reminders, intentionally separate from trips SQLite. */
public final class ReminderStore {
    private static final String PREF="personal_reminders_v1",KEY="items";
    public static final String CHANNEL="mapa_personal_reminders_v2";
    public static final String ACTION="uy.com.mapatrayectos.PERSONAL_REMINDER";
    private ReminderStore(){}
    public static final class Item{
        public int id;public long time;public String text;public boolean done,fired;
        Item(int id,long time,String text,boolean done,boolean fired){
            this.id=id;this.time=time;this.text=text;this.done=done;this.fired=fired;
        }
    }
    public static synchronized List<Item> all(Context c){
        List<Item> result=new ArrayList<>();
        try{
            JSONArray arr=new JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]"));
            for(int i=0;i<arr.length();i++){
                JSONObject x=arr.getJSONObject(i);
                result.add(new Item(x.optInt("id"),x.optLong("time"),x.optString("text"),x.optBoolean("done"),x.optBoolean("fired")));
            }
        }catch(Exception e){Log.e("MapaReminders","Error reading reminders",e);}
        result.sort((a,b)->Long.compare(a.time,b.time));return result;
    }
    private static synchronized void write(Context c,List<Item> items){
        JSONArray arr=new JSONArray();
        for(Item i:items)try{
            JSONObject x=new JSONObject();
            x.put("id",i.id);x.put("time",i.time);x.put("text",i.text);
            x.put("done",i.done);x.put("fired",i.fired);arr.put(x);
        }catch(Exception ignored){}
        if(!c.getSharedPreferences(PREF,0).edit().putString(KEY,arr.toString()).commit())
            throw new IllegalStateException("No se pudo guardar la lista");
    }
    public static boolean canNotify(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null||!nm.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=nm.getNotificationChannel(CHANNEL);
            if(ch!=null&&ch.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        }
        return true;
    }
    public static boolean canExact(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return am!=null&&(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms());
    }
    public static void channel(Context c){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Alertas personales · Mapa Trayectos",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos puntuales y acciones para tus recordatorios");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }
    public static synchronized void create(Context c,String text,long when){
        if(text==null||text.trim().isEmpty()||when<=System.currentTimeMillis())
            throw new IllegalArgumentException("Texto y fecha futura requeridos");
        List<Item> items=all(c);SharedPreferences p=c.getSharedPreferences(PREF,0);
        int id=p.getInt("next_id",100);
        if(!p.edit().putInt("next_id",id+1).commit())throw new IllegalStateException("Error guardando identificador");
        Item item=new Item(id,when,text.trim(),false,false);
        items.add(item);write(c,items);schedule(c,item);
    }
    public static synchronized void change(Context c,int id,boolean done,boolean delete,long newTime){
        List<Item> items=all(c);Item found=null;
        for(Item x:items)if(x.id==id){found=x;break;}
        if(found==null)return;cancel(c,id);
        if(delete)items.remove(found);
        else{found.done=done;if(newTime>0){found.time=newTime;found.done=false;}found.fired=false;}
        write(c,items);
        if(!delete&&!found.done)schedule(c,found);
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.cancel(24000+id);
    }
    public static synchronized Item get(Context c,int id){
        for(Item i:all(c))if(i.id==id)return i;return null;
    }
    /** Only mark delivered after Android actually accepted a notification. */
    public static synchronized void markFired(Context c,int id){
        List<Item> items=all(c);
        for(Item i:items)if(i.id==id&&!i.done){
            i.fired=true;write(c,items);
            c.getSharedPreferences(PREF,0).edit().putLong("last_alert_at",System.currentTimeMillis())
                .putInt("last_alert_id",id).putString("last_error","").apply();
            return;
        }
    }
    public static void recordError(Context c,String message){
        c.getSharedPreferences(PREF,0).edit().putString("last_error",message).putLong("last_error_at",System.currentTimeMillis()).apply();
        Log.w("MapaReminders",message);
    }
    public static String lastError(Context c){return c.getSharedPreferences(PREF,0).getString("last_error","");}
    public static long lastAlert(Context c){return c.getSharedPreferences(PREF,0).getLong("last_alert_at",0);}
    private static PendingIntent alarm(Context c,int id){
        Intent i=new Intent(c,ReminderReceiver.class).setAction(ACTION).setData(Uri.parse("mapareminder://alarm/"+id));
        i.putExtra("id",id);
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    public static void cancel(Context c,int id){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)am.cancel(alarm(c,id));
    }
    /** The device can defer inexact alarms; exact permission is explicitly surfaced in UI. */
    public static void schedule(Context c,Item item){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)throw new IllegalStateException("AlarmManager no disponible");
        PendingIntent pi=alarm(c,item.id);
        try{
            if(canExact(c))am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,item.time,pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,item.time,pi);
        }catch(SecurityException e){
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,item.time,pi);
            recordError(c,"No se habilitaron alarmas exactas: horario aproximado");
        }
    }
    /** Rebuild alarms after reboot, install, time/zone changes and when permissions are restored. */
    public static void rearm(Context c){
        long now=System.currentTimeMillis();
        for(Item i:all(c)){
            if(i.done||i.fired)continue;
            if(i.time>now)schedule(c,i);
            else if(i.time>=now-7L*24L*3600000L && canNotify(c)){
                // Immediate delivery of recently missed alerts, no endless rearming loops.
                schedule(c,new Item(i.id,now+2500L,i.text,false,false));
            }
        }
    }
    private static PendingIntent action(Context c,int id,String verb,int requestCode){
        Intent i=new Intent(c,ReminderActionReceiver.class).setAction("uy.com.mapatrayectos.REMINDER_"+verb);
        i.setData(Uri.parse("mapareminder://"+verb+"/"+id)).putExtra("id",id);
        return PendingIntent.getBroadcast(c,requestCode,id>0?
            i : new Intent(i),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private static Notification build(Context c,Item item){
        channel(c);
        Intent open=new Intent(c,ReminderActivity.class).putExtra("show_reminder_id",item.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,item.id,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
         .setContentTitle("✦ Recordatorio · Mapa Trayectos").setContentText(item.text)
         .setStyle(new Notification.BigTextStyle().bigText(item.text))
         .setContentIntent(pi).setAutoCancel(false).setCategory(Notification.CATEGORY_REMINDER)
         .setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_VIBRATE)
         .setVisibility(Notification.VISIBILITY_PUBLIC)
         .addAction(new Notification.Action.Builder(android.R.drawable.checkbox_on_background,"HECHO",action(c,item.id,"DONE",100000+item.id)).build())
         .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_recent_history,"+10 MIN",action(c,item.id,"SNOOZE",200000+item.id)).build());
        return b.build();
    }
    /** Returns false if notification could not be posted. Don't acknowledge the alarm then. */
    public static boolean alert(Context c,Item item){
        if(!canNotify(c)){recordError(c,"Aviso bloqueado: activá las notificaciones de Mapa Trayectos");return false;}
        try{
            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)return false;
            nm.notify(24000+item.id,build(c,item));
            return true;
        }catch(Exception e){recordError(c,"Error de notificación: "+e.getMessage());return false;}
    }
}
