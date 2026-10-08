package uy.com.mapatrayectos;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.*;
import android.util.Log;

/** Deterministic soft three-bubble cue. Runs under BroadcastReceiver.goAsync() while alarm is delivered. */
public final class ReminderSound {
    private static final String PREF="reminder_audio_diagnostics";
    private static final int RATE=24000;
    private ReminderSound(){}
    public static final class Result{
        public final boolean ok;public final String detail;
        Result(boolean successful,String message){ok=successful;detail=message;}
    }
    public static String diagnostics(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        long last=p.getLong("last_checked_at",0);
        return (last==0?"Sin prueba de reproducción":p.getBoolean("last_ok",false)?"Motor de audio: inició correctamente":"Audio no reproducido")
            +" · "+p.getString("last_detail","sin datos");
    }
    private static void status(Context c,boolean ok,String detail){
        c.getSharedPreferences(PREF,0).edit().putLong("last_checked_at",System.currentTimeMillis())
            .putBoolean("last_ok",ok).putString("last_detail",detail).apply();
        if(!ok)Log.w("MapaReminderAudio",detail);
    }
    /**
     * Must be invoked in a background worker; do not run on main thread.
     * Returns after playback, allowing the caller to use BroadcastReceiver.goAsync().
     */
    public static Result playBlocking(Context c){
        AudioTrack audio=null;
        try{
            AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
            if(am==null){status(c,false,"AudioManager ausente");return new Result(false,"AudioManager ausente");}
            if(am.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL){
                status(c,false,"Dispositivo en silencio o vibración");return new Result(false,"Modo silencio/vibración");
            }
            int current=am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            if(current<=0){
                status(c,false,"Volumen de notificaciones en cero");return new Result(false,"Volumen de notificaciones en cero");
            }
            short[] samples=makeSamples();
            AudioAttributes attrs=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            AudioFormat format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setSampleRate(RATE).build();
            audio=new AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.length*2).build();
            if(audio.getState()!=AudioTrack.STATE_INITIALIZED)
                throw new IllegalStateException("AudioTrack sin inicializar");
            int written=audio.write(samples,0,samples.length,AudioTrack.WRITE_BLOCKING);
            if(written!=samples.length)
                throw new IllegalStateException("PCM incompleto "+written+"/"+samples.length);
            audio.setVolume(.92f);
            audio.play();
            if(audio.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)
                throw new IllegalStateException("AudioTrack no inició");
            // Blocking wait keeps audio alive for ~0.8s in the pending broadcast.
            Thread.sleep(920);
            status(c,true,"PCM "+RATE+" Hz / "+samples.length+" muestras; audio iniciado");
            return new Result(true,"Motor de burbujas iniciado");
        }catch(Exception e){
            String d="Fallo burbujas: "+e.getClass().getSimpleName()+" "+e.getMessage();
            status(c,false,d);return new Result(false,d);
        }finally{
            if(audio!=null){try{audio.pause();}catch(Exception ignored){}
                try{audio.flush();}catch(Exception ignored){}
                try{audio.release();}catch(Exception ignored){}}
        }
    }
    public static void preview(Context c){
        new Thread(()->playBlocking(c.getApplicationContext()),"mapa-bubbles-preview").start();
    }
    static short[] makeSamples(){
        final double[] starts={.025,.20,.39};
        final double[] pitches={380,510,660};
        final int len=RATE*82/100;short[] data=new short[len];
        for(int i=0;i<len;i++){
            double t=i/(double)RATE,signal=0;
            for(int j=0;j<3;j++){
                double age=t-starts[j];
                if(age<0||age>.17)continue;
                double x=age/.17;
                // Pleasant rising tiny droplets, soft attack/release and no sharp edges.
                double envelope=Math.pow(Math.sin(Math.PI*x),2.0)*Math.exp(-3.2*age);
                double phase=2*Math.PI*(pitches[j]*age+110*age*age);
                signal+=envelope*(Math.sin(phase)+0.12*Math.sin(2*phase));
            }
            data[i]=(short)Math.max(-32767,Math.min(32767,5200*signal));
        }
        return data;
    }
}