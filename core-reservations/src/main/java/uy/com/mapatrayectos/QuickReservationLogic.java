package uy.com.mapatrayectos;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Locale;

/** Pure, deterministic presentation policy for the compact reservation center. */
public final class QuickReservationLogic {
    private QuickReservationLogic(){}
    public static boolean active(String status){
        return !("FINALIZADA".equals(status)||"CANCELADA".equals(status)
            ||"RECHAZADA".equals(status)||"RECHAZADA_CLIENTE".equals(status));
    }
    public static boolean confirmed(String status){
        return "ACEPTADA".equals(status)||"CONFIRMADA".equals(status)
            ||"ACEPTADA_CLIENTE".equals(status)||"EN_VIAJE".equals(status);
    }
    public static boolean pending(String status){return "PENDIENTE".equals(status);}
    public static long atMillis(String day,String time){
        if(day==null||time==null||day.length()<10||time.length()<5)return -1;
        try{
            SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);
            f.setLenient(false);String input=day.substring(0,10)+" "+time.substring(0,5);
            ParsePosition p=new ParsePosition(0);Date result=f.parse(input,p);
            return result!=null&&p.getIndex()==input.length()?result.getTime():-1;
        }catch(Exception e){return -1;}
    }
    public static boolean conflicts(long aStart,int aMins,long bStart,int bMins){
        if(aStart<0||bStart<0)return false;
        long margin=15L*60000;
        long aEnd=aStart+(Math.max(15,aMins)+15L)*60000;
        long bEnd=bStart+(Math.max(15,bMins)+15L)*60000;
        return aStart<bEnd+margin&&bStart<aEnd+margin;
    }
    public static int bucket(String day,String today,String tomorrow){
        if(day==null)return 2;
        return day.equals(today)?0:day.equals(tomorrow)?1:2;
    }
    public static boolean stale(long lastSyncMs,long now){
        return lastSyncMs<=0||now<lastSyncMs||now-lastSyncMs>45000L;
    }
}
