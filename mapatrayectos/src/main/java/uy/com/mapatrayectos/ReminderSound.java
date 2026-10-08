package uy.com.mapatrayectos;

import android.content.Context;
import android.media.*;

/** Three gentle, ascending glass-bubble chimes, generated locally without MP3 downloads. */
public final class ReminderSound {
    private ReminderSound(){}
    public static void play(Context c){
        AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        if(am!=null&&am.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL)return;
        new Thread(()->{
            AudioTrack audio=null;
            try{
                final int hz=24000;
                final short[] samples=new short[hz*62/100];
                final double[] starts={.03,.19,.35};
                final double[] freqs={390,495,612};
                for(int i=0;i<samples.length;i++){
                    double t=i/(double)hz,v=0;
                    for(int j=0;j<3;j++){
                        double age=t-starts[j];
                        if(age<0||age>.155)continue;
                        double envelope=Math.pow(Math.sin(Math.PI*age/.155),2)*Math.exp(-5*age);
                        double phase=2*Math.PI*(freqs[j]*age+122*age*age);
                        v+=envelope*(Math.sin(phase)+.17*Math.sin(2*phase));
                    }
                    samples[i]=(short)Math.max(-32767,Math.min(32767,6400*v));
                }
                AudioAttributes attr=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                AudioFormat format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(hz).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                audio=new AudioTrack.Builder().setAudioAttributes(attr).setAudioFormat(format)
                    .setBufferSizeInBytes(samples.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();
                if(audio.getState()!=AudioTrack.STATE_INITIALIZED)return;
                audio.write(samples,0,samples.length);
                audio.setVolume(.80f);
                audio.play();
                Thread.sleep(740);
            }catch(Exception ex){
                android.util.Log.w("MapaReminders","Bubble tone unavailable; standard Android notification remains",ex);
            }finally{
                if(audio!=null){try{audio.stop();}catch(Exception ignored){}
                    try{audio.release();}catch(Exception ignored){}}
            }
        },"mapa-bubble-sound").start();
    }
}
