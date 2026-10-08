package uy.com.mapatrayectos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public final class FeedbackReceiver extends BroadcastReceiver {
    private static final String PREFS="mapa_feedback_state";
    private static final String KEY_INITIALIZED="initialized",KEY_SHIFT="shift",KEY_TRIP="trip",KEY_STAGE="stage",KEY_STOP="stop",KEY_PAUSED="paused";

    private static final int SHIFT_START=1,SERVICE_START=2,PICKUP=3,STOP_START=4,STOP_END=5,TRIP_END=6,SHIFT_END=7,SHIFT_PAUSE=8,SHIFT_RESUME=9;

    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!TrackingService.ACTION_STATE.equals(intent.getAction()))return;
        boolean shift=intent.getBooleanExtra("shift_active",false),trip=intent.getBooleanExtra("trip_active",false),stop=intent.getBooleanExtra("stop_active",false),paused=intent.getBooleanExtra("shift_paused",false);
        String stage=intent.getStringExtra("trip_stage");if(stage==null)stage=trip?"onboard":"none";
        SharedPreferences sp=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!sp.getBoolean(KEY_INITIALIZED,false)){sp.edit().putBoolean(KEY_INITIALIZED,true).putBoolean(KEY_SHIFT,shift).putBoolean(KEY_TRIP,trip).putBoolean(KEY_STOP,stop).putBoolean(KEY_PAUSED,paused).putString(KEY_STAGE,stage).apply();return;}
        boolean oldShift=sp.getBoolean(KEY_SHIFT,false),oldTrip=sp.getBoolean(KEY_TRIP,false),oldStop=sp.getBoolean(KEY_STOP,false),oldPaused=sp.getBoolean(KEY_PAUSED,false);String oldStage=sp.getString(KEY_STAGE,oldTrip?"onboard":"none");
        sp.edit().putBoolean(KEY_SHIFT,shift).putBoolean(KEY_TRIP,trip).putBoolean(KEY_STOP,stop).putBoolean(KEY_PAUSED,paused).putString(KEY_STAGE,stage).apply();

        int event=0;
        if(!oldShift&&shift&&!trip)event=SHIFT_START;
        else if(oldShift&&!oldTrip&&shift&&trip)event=SERVICE_START;
        else if(oldTrip&&trip&&"to_pickup".equals(oldStage)&&"onboard".equals(stage))event=PICKUP;
        else if(oldTrip&&trip&&!oldStop&&stop)event=STOP_START;
        else if(oldTrip&&trip&&oldStop&&!stop)event=STOP_END;
        else if(oldTrip&&!trip&&shift)event=TRIP_END;
        else if(oldShift&&!shift)event=SHIFT_END;
        else if(oldShift&&shift&&!trip&&!oldPaused&&paused)event=SHIFT_PAUSE;
        else if(oldShift&&shift&&!trip&&oldPaused&&!paused)event=SHIFT_RESUME;
        if(event!=0)playFeedback(context,event,intent.getStringExtra("trip_type"),intent.getStringExtra("trip_status"));
    }

    private void playFeedback(Context context,int event,String type,String status){
        vibrate(context,event);
        final PendingResult pending=goAsync();
        new Thread(()->{
            try{if(!playSelectedSound(context,event,type,status))playChime(event);}
            catch(Exception ignored){try{playChime(event);}catch(Exception ignoredAgain){}}
            finally{pending.finish();}
        },"mapa-sound").start();
    }

    /** A chosen MP3 plays once; full-pack failures are logged, never disguised as a new pip-pip. */
    private boolean playSelectedSound(Context context,int event,String type,String status){
        String name=null;
        switch(event){
            case SHIFT_START:name="sound_shift_start";break;
            case SERVICE_START:
                name="uber".equalsIgnoreCase(type)?"sound_uber":
                     "cabify".equalsIgnoreCase(type)?"sound_cabify":"sound_trip_start";break;
            case PICKUP:name="sound_pickup";break;
            case STOP_START:name="sound_stop_start";break;
            case STOP_END:name="sound_stop_end";break;
            case TRIP_END:name="cancelled".equalsIgnoreCase(status)?"sound_cancel":"sound_trip_end";break;
            case SHIFT_END:name="sound_shift_end";break;
            case SHIFT_PAUSE:name="sound_pause";break;
            case SHIFT_RESUME:name="sound_resume";break;
        }
        if(name==null)return false;
        if(SoundPack.play(context,name))return true;
        // Once all thirteen were imported, do not fall back to the old synthesized tones.
        if(SoundPack.hasFullPack(context)){
            android.util.Log.w("MapaFeedback","Audio unavailable: "+name+"; "+SoundPack.lastError());
            return true;
        }
        // Compatibility with the old five-track ZIP: only unassigned events use R20 chimes.
        return false;
    }

    /** Only for deliberate non-driving menu actions, not GPS clock or slider movements. */
    public static void playUiCue(Context context){
        Context app=context.getApplicationContext();
        if(!SoundPack.isInstalled(app,"sound_ui_action"))return;
        new Thread(()->SoundPack.playUi(app),"mapa-ui-cue").start();
    }

    private void playChime(int event){
        double[] f;int[] ms;
        switch(event){
            case SHIFT_START:f=new double[]{523.25,659.25};ms=new int[]{150,210};break;
            case SERVICE_START:f=new double[]{440.00,554.37,659.25};ms=new int[]{115,125,190};break;
            case PICKUP:f=new double[]{587.33,739.99,880.00};ms=new int[]{120,135,220};break;
            case STOP_START:f=new double[]{523.25,392.00};ms=new int[]{150,230};break;
            case STOP_END:f=new double[]{392.00,523.25,659.25};ms=new int[]{110,125,190};break;
            case TRIP_END:f=new double[]{783.99,659.25,523.25};ms=new int[]{120,145,235};break;
            case SHIFT_PAUSE:f=new double[]{493.88,392.00};ms=new int[]{130,180};break;
            case SHIFT_RESUME:f=new double[]{392.00,523.25};ms=new int[]{115,200};break;
            default:f=new double[]{659.25,493.88,392.00};ms=new int[]{120,150,260};break;
        }
        byte[] pcm=synth(f,ms,62);
        AudioTrack track=new AudioTrack(AudioManager.STREAM_MUSIC,22050,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,pcm.length,AudioTrack.MODE_STATIC);
        track.write(pcm,0,pcm.length);track.setVolume(0.58f);track.play();
        try{Thread.sleep(total(ms)+62L*(ms.length-1)+120L);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        try{track.stop();}catch(Exception ignored){}track.release();
    }

    private byte[] synth(double[] freq,int[] dur,int gapMs){
        final int sr=22050;int samples=0;for(int d:dur)samples+=sr*d/1000;samples+=Math.max(0,freq.length-1)*(sr*gapMs/1000);
        byte[] out=new byte[samples*2];int pos=0;
        for(int n=0;n<freq.length;n++){
            int count=sr*dur[n]/1000;for(int i=0;i<count;i++){
                double t=i/(double)sr,phase=i/(double)Math.max(1,count-1);double env=Math.min(1.0,phase/0.12)*Math.min(1.0,(1.0-phase)/0.28);env=Math.max(0,env);
                double s=(Math.sin(2*Math.PI*freq[n]*t)+0.16*Math.sin(2*Math.PI*freq[n]*2.0*t))*env*0.28;
                short v=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,(int)(s*32767)));out[pos++]=(byte)(v&255);out[pos++]=(byte)((v>>8)&255);
            }
            if(n<freq.length-1){int gap=sr*gapMs/1000;for(int i=0;i<gap;i++){out[pos++]=0;out[pos++]=0;}}
        }
        return out;
    }

    private long total(int[] ms){long t=0;for(int x:ms)t+=x;return t;}

    private void vibrate(Context context,int event){
        try{
            Vibrator v=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);if(v==null||!v.hasVibrator())return;
            long[] timing;int[] amp;
            switch(event){
                case SHIFT_START:timing=new long[]{0,150,60,105};amp=new int[]{0,220,0,175};break;
                case SERVICE_START:timing=new long[]{0,135,55,125};amp=new int[]{0,210,0,185};break;
                case PICKUP:timing=new long[]{0,170,55,140};amp=new int[]{0,235,0,205};break;
                case STOP_START:timing=new long[]{0,125,55,145};amp=new int[]{0,190,0,220};break;
                case STOP_END:timing=new long[]{0,115,45,115,45,130};amp=new int[]{0,175,0,200,0,220};break;
                case TRIP_END:timing=new long[]{0,190,70,220};amp=new int[]{0,235,0,220};break;
                case SHIFT_PAUSE:timing=new long[]{0,75,30,90};amp=new int[]{0,120,0,120};break;
                case SHIFT_RESUME:timing=new long[]{0,85,45,110};amp=new int[]{0,135,0,145};break;
                default:timing=new long[]{0,210,75,245};amp=new int[]{0,225,0,205};break;
            }
            if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(timing,amp,-1));else v.vibrate(timing,-1);
        }catch(Exception ignored){}
    }
}
