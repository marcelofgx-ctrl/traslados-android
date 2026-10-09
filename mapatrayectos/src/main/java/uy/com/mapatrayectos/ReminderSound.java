package uy.com.mapatrayectos;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
            "MP3 original: reproducción iniciada":"MP3 original: no se reprodujo")
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
    public static Result playBlocking(Context c){
        MediaPlayer player=null;
        try{
            AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
            if(am==null){
                status(c,false,"AudioManager no disponible");
                return new Result(false,"AudioManager no disponible");
            }
            if(am.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL){
                String why="Teléfono en silencio/vibración: no se reproduce el MP3";
                status(c,false,why);
                return new Result(false,why);
            }
            int volume=am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            if(volume<=0){
                status(c,false,"Volumen de notificaciones en cero");
                return new Result(false,"Volumen de notificaciones en cero");
            }
            final CountDownLatch finished=new CountDownLatch(1);
            final AtomicBoolean completed=new AtomicBoolean(false);
            final AtomicBoolean error=new AtomicBoolean(false);
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
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
            player.setVolume(.85f,.85f);
            player.start();
            if(!player.isPlaying())throw new IllegalStateException("Android no inició MediaPlayer");
            boolean signaled=finished.await(4500L,TimeUnit.MILLISECONDS);
            if(error.get())throw new IllegalStateException("MediaPlayer informó error");
            if(!signaled||!completed.get())throw new IllegalStateException("Tiempo de espera agotado en reproducción");
            String result="ElevenLabs burbujas MP3 (2,04 s): reproducción completada";
            status(c,true,result);
            return new Result(true,result);
        }catch(Exception e){
            String message="Error reproduciendo MP3 original: "+e.getClass().getSimpleName()+" "+e.getMessage();
            status(c,false,message);
            return new Result(false,message);
        }finally{
            if(player!=null){try{player.release();}catch(Exception ignored){}}
        }
    }
    public static void preview(Context c){
        new Thread(()->playBlocking(c.getApplicationContext()),"mapa-original-reminder-preview").start();
    }
}
