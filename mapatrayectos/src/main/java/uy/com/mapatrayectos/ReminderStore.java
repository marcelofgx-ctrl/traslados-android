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
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Separate from trip SQLite: resetting trip data cannot accidentally erase reminders. */
public final class ReminderStore {
    private static final String PREF="personal_reminders_v1",KEY="items",CHANNEL="mapa_personal_reminders";
    private ReminderStore(){}
    public static final class Item {
        public int id;public long time;public String text;public boolean done;public boolean fired;
        Item(int id,long time,String text,boolean done,boolean fired){this.id=id;this.time=time;this.text=text;this.done=done;this.fired=fired;}
    }
    public static synchronized List<Item> all(Context context){
        List<Item> result=new ArrayList<>();
        String json=context.getSharedPreferences(PREF,0).getString(KEY,"[]");
        try{
            JSONArray arr=new JSONArray(json);
            for(int i=0;i<arr.length();i++){
                JSONObject x=arr.getJSONObject(i);
                result.add(new Item(x.optInt("id"),x.optLong("time"),x.optString("text"),x.optBoolean("done"),x.optBoolean("fired")));
            }
        }catch(Exception ignored){}
        result.sort((a,b)->Long.compare(a.time,b.time));
        return result;
    }
    private static synchronized void write(Context context,List<Item> data){
        JSONArray array=new JSONArray();
        for(Item i:data)try{
            JSONObject v=new JSONObject();v.put("id",i.id);v.put("time",i.time);
            v.put("text",i.text);v.put("done",i.done);v.put("fired",i.fired);
            array.put(v);
        }catch(Exception ignored){}
        if(!context.getSharedPreferences(PREF,0).edit().putString(KEY,array.toString()).commit())
            throw new IllegalStateException("No se pudo guardar el recordatorio");
    }
    public static synchronized void create(Context c,String text,long when){
        if(text==null||text.trim().isEmpty()||when<=System.currentTimeMillis())throw new IllegalArgumentException("Fecha futura y texto requeridos");
        List<Item> data=all(c);
        int id=c.getSharedPreferences(PREF,0).getInt("next_id",100);
        if(!c.getSharedPreferences(PREF,0).edit().putInt("next_id",id+1).commit())throw new IllegalStateException("Error de guardado");
        Item item=new Item(id,when,text.trim(),false,false);
        data.add(item);write(c,data);schedule(c,item);
    }
    public static synchronized void change(Context c,int id,boolean done,boolean delete,long newTime){
        List<Item> data=all(c);
        Item found=null;
        for(Item i:data)if(i.id==id){found=i;break;}
        if(found==null)return;
        cancel(c,id);
        if(delete)data.remove(found);
        else{found.done=done;if(newTime>0){found.time=newTime;found.done=false;}found.fired=false;}
        write(c,data);
        if(!delete&&!found.done)schedule(c,found);
    }
    public static synchronized Item get(Context c,int id){
        for(Item x:all(c))if(x.id==id)return x;
        return null;
    }
    public static synchronized void markFired(Context c,int id){
        List<Item> data=all(c);
        for(Item i:data)if(i.id==id&&!i.done){i.fired=true;write(c,data);return;}
    }
    public static void rearm(Context c){
        long now=System.currentTimeMillis();
        for(Item i:all(c))if(!i.done&&!i.fired){
            if(i.time>now)schedule(c,i);
            else if(i.time>now-36L*3600000L)schedule(c,new Item(i.id,now+3000L,i.text,false,false));
        }
    }
    private static PendingIntent intent(Context c,int id){
        Intent i=new Intent(c,ReminderReceiver.class).setAction("uy.com.mapatrayectos.PERSONAL_REMINDER");
        i.putExtra("id",id);
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    public static void cancel(Context c,int id){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)am.cancel(intent(c,id));
    }
    public static void schedule(Context c,Item i){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pending=intent(c,i.id);
        try{
            if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,i.time,pending);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,i.time,pending);
        }catch(SecurityException ex){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,i.time,pending);}
    }
    static void alert(Context c,Item item){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Recordatorios personales",NotificationManager.IMPORTANCE_HIGH));
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent open=new Intent(c,ReminderActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,item.id,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        n.setContentTitle("Recordatorio · Mapa Trayectos").setContentText(item.text)
         .setStyle(new Notification.BigTextStyle().bigText(item.text))
         .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).setContentIntent(pi)
         .setCategory(Notification.CATEGORY_REMINDER);
        nm.notify(24000+item.id,n.build());
    }
}
