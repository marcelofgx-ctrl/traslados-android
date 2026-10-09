package uy.com.mapatrayectos;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays the exact ElevenLabs soap-bubble MP3 supplied by the user, embedded in this APK.
 * No synthesized pip and no external file import is necessary.
 */
public final class ReminderSound {
    private static final String PREF="reminder_audio_diagnostics";
    private static final String LOG="MapaReminderAudio";
    private ReminderSound(){}
    public static final class Result{
        public final boolean ok;public final String detail;
        Result(boolean successful,String message){ok=successful;detail=message;}
    }
    public static String diagnostics(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        long last=p.getLong("last_checked_at",0);
        return (last==0?"MP3 original sin probar":p.getBoolean("last_ok",false)?
            "MP3 original: reproducción completada":"MP3 original: no se reprodujo")
            +" · "+p.getString("last_detail","sin datos");
    }
    private static void status(Context c,boolean ok,String detail){
        c.getSharedPreferences(PREF,0).edit()
            .putLong("last_checked_at",System.currentTimeMillis())
            .putBoolean("last_ok",ok).putString("last_detail",detail).apply();
        if(!ok)Log.w(LOG,detail);
        else Log.i(LOG,detail);
    }
    /**
     * Runs ONLY in a background worker. ReminderReceiver uses goAsync() to keep the
     * process alive until this short MP3 completes; the in-app "Probar" uses a worker too.
     */
    public static synchronized Result playBlocking(Context c){
        MediaPlayer player=null;
        AudioManager am=null;
        int oldAlarm=-1, raisedAlarm=-1;
        boolean changedVolume=false;
        try{
            am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
            if(am==null){
                status(c,false,"AudioManager no disponible");
                return new Result(false,"AudioManager no disponible");
            }
            // Alarm reminders use the alarm stream (not notifications) on Samsung.
            // Temporarily reach at least half of the system alarm-volume range.
            int maxAlarm=am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            if(maxAlarm<=0)throw new IllegalStateException("Volumen de alarmas inválido");
            int minAlarm=Math.max(1,(maxAlarm+1)/2);
            oldAlarm=am.getStreamVolume(AudioManager.STREAM_ALARM);
            if(oldAlarm<minAlarm && !am.isVolumeFixed()){
                try{am.setStreamVolume(AudioManager.STREAM_ALARM,minAlarm,0);}
                catch(Exception e){Log.w(LOG,"Android no permitió subir el volumen de alarmas",e);}
            }
            raisedAlarm=am.getStreamVolume(AudioManager.STREAM_ALARM);
            changedVolume=raisedAlarm>oldAlarm;
            if(raisedAlarm<minAlarm){
                String why="Alarmas al "+Math.round(raisedAlarm*100f/maxAlarm)
                    +"%; Android no permitió subirlas al 50%. Revisá Ajustes > Volumen > Alarmas.";
                status(c,false,why);
                return new Result(false,why);
            }
            final CountDownLatch finished=new CountDownLatch(1);
            final AtomicBoolean completed=new AtomicBoolean(false);
            final AtomicBoolean error=new AtomicBoolean(false);
            player=new MediaPlayer();
            player.setWakeMode(c,PowerManager.PARTIAL_WAKE_LOCK);
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            try(AssetFileDescriptor fd=c.getResources().openRawResourceFd(R.raw.reminder_bubbles_elevenlabs)){
                if(fd==null)throw new IllegalStateException("MP3 original no incorporado a la APK");
                player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
            }
            player.setOnCompletionListener(p->{completed.set(true);finished.countDown();});
            player.setOnErrorListener((p,what,extra)->{
                error.set(true);Log.e(LOG,"MediaPlayer error "+what+"/"+extra);
                finished.countDown();return true;
            });
            player.prepare();
            player.setVolume(1.0f,1.0f);
            player.start();
            if(!player.isPlaying())throw new IllegalStateException("Android no inició MediaPlayer");
            boolean signaled=finished.await(4500L,TimeUnit.MILLISECONDS);
            if(error.get())throw new IllegalStateException("MediaPlayer informó error");
            if(!signaled||!completed.get())throw new IllegalStateException("Tiempo de espera agotado en reproducción");
            String result="ElevenLabs burbujas MP3 (2,04 s): reproducción completada; canal ALARMAS al "+Math.round(raisedAlarm*100f/am.getStreamMaxVolume(AudioManager.STREAM_ALARM))+"%; ajuste temporal; No molestar puede bloquear la salida.";
            status(c,true,result);
            return new Result(true,result);
        }catch(Exception e){
            String message="Error reproduciendo MP3 original: "+e.getClass().getSimpleName()+" "+e.getMessage();
            status(c,false,message);
            return new Result(false,message);
        }finally{
            if(player!=null){try{player.release();}catch(Exception ignored){}}
            if(changedVolume && am!=null && oldAlarm>=0){
                try{
                    // Do not override changes the user made during the two-second cue.
                    if(am.getStreamVolume(AudioManager.STREAM_ALARM)==raisedAlarm){
                        am.setStreamVolume(AudioManager.STREAM_ALARM,oldAlarm,0);
                        Log.i(LOG,"Volumen anterior de alarmas restaurado");
                    }
                }catch(Exception e){Log.w(LOG,"No se pudo restaurar el volumen anterior de alarmas",e);}
            }
        }
    }
    public static void preview(Context c){
        new Thread(()->playBlocking(c.getApplicationContext()),"mapa-original-reminder-preview").start();
    }
}
