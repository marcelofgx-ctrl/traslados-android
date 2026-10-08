package uy.com.mapatrayectos;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Authenticated originals, stored privately; 13-event pack also supports the former 5-track ZIP. */
public final class SoundPack {
    private static final String TAG="MapaSoundPack";
    private static final String DIR="mapa_sound_pack";
    private static final String[] NAMES={
        "sound_shift_start", "sound_trip_start", "sound_uber", "sound_cancel", "sound_trip_end",
        "sound_cabify", "sound_pickup", "sound_stop_start", "sound_stop_end",
        "sound_shift_end", "sound_pause", "sound_resume", "sound_ui_action"
    };
    private static final String[] LABELS={
        "Iniciar jornada", "Iniciar viaje personal", "Viaje Uber", "Cancelar viaje", "Terminar viaje",
        "Viaje Cabify", "Recoger pasajero", "Iniciar parada", "Finalizar parada",
        "Finalizar jornada", "Pausar jornada", "Reanudar jornada", "Botones del menú"
    };
    private static final String[] HASHES={
        "6fdeaf7026e10d34ca1528b02593b971bf01a489e04bce39c356cab4709158e3",
        "118f14bd5325e5b360245829f9c7f67523b1afa0b59baf95d8810e145dc590a1",
        "115cfe4f9836ca1401fb97a4051752d5870d7c5e01a7561fcad5a2a280501116",
        "899f59a89f4806dcd1feab4f8ea14d5bdcfd0337d7fed7e13fe353e9d1a86822",
        "27434f899c4f8d6e976421a2af5fe2f441858c58817a0153718629a8b6191bad",
        "54630ad061a7ba9fe381439d555e18a89c266c455506811681bb2067b8ecc7d2",
        "943c9e7f5eb1148a281ff1e768c022aa27d64b979eaa39b9c9555435b2f38285",
        "42f6807a4ca04dac647ae192de5b0d04483af07fb510b6be72fa832c8e74cfa0",
        "def38e52d2c18d85a31e2f638ec6e89cb2ab15cafba47941104f93729a32e968",
        "4178697cfa239f6083b511cf0f0f70494777a149bac85494782c05cc49cedf04",
        "3122b5cd966bf463e262fb7ad743c198e19a7836050f78b8bcced5d3e5f6fb86",
        "68c73f4cd4de85b6518ad5443b636724b4d86522e1c0c96089e8beda895ba93d",
        "0943c2b7363ec28afc9268307ce68ef0b20ce3c36f9b8e5bf14f7f693316cb39"
    };
    private static final int MAX_FILE=1200000,MAX_TOTAL=5200000;
    private static MediaPlayer active;
    private static volatile String lastError="";
    private SoundPack(){}

    private static File root(Context c){return new File(c.getFilesDir(),DIR);}
    public static String[] resources(){return NAMES.clone();}
    public static String[] labels(){return LABELS.clone();}
    public static boolean isInstalled(Context c,String name){
        if(!Arrays.asList(NAMES).contains(name))return false;
        File f=new File(root(c),name+".mp3");
        return f.isFile()&&f.length()>4000L;
    }
    public static int installedCount(Context c){
        int count=0;for(String n:NAMES)if(isInstalled(c,n))count++;return count;
    }
    public static boolean hasFullPack(Context c){return installedCount(c)==NAMES.length;}
    public static boolean ready(Context c){return installedCount(c)>=5;}
    public static String describe(Context c){
        int count=installedCount(c);
        return count==13?"13/13 sonidos originales listos":
            count==5?"5/13 instalados · faltan los 8 complementarios":
            count==0?"Sin sonidos originales · importar ZIP de 13":
            count+"/13 sonidos originales instalados";
    }
    public static String lastError(){return lastError;}

    /** A ZIP must contain exactly the 5 originals or the full 13; no path traversal or ZIP junk. */
    public static synchronized void install(Context c,Uri zipUri) throws Exception {
        File base=root(c);
        File tmp=new File(c.getFilesDir(),DIR+"_temp");
        File backup=new File(c.getFilesDir(),DIR+"_previous");
        delete(tmp);
        if(!tmp.mkdirs())throw new IOException("No se pudo crear directorio temporal");
        Map<String,String> expected=new HashMap<>();
        for(int i=0;i<NAMES.length;i++)expected.put(NAMES[i]+".mp3",HASHES[i]);
        Set<String> found=new HashSet<>();
        long allBytes=0L;
        boolean success=false;
        try{
            try(InputStream raw=c.getContentResolver().openInputStream(zipUri)){
                if(raw==null)throw new IOException("No se pudo abrir el ZIP");
                try(ZipInputStream input=new ZipInputStream(raw)){
                    ZipEntry entry;byte[] bytes=new byte[16384];
                    while((entry=input.getNextEntry())!=null){
                        String name=entry.getName();
                        if(entry.isDirectory()||!expected.containsKey(name)||!found.add(name))
                            throw new IOException("ZIP inválido: archivo inesperado o duplicado: "+name);
                        File output=new File(tmp,name);
                        MessageDigest hash=MessageDigest.getInstance("SHA-256");
                        int fileBytes=0;
                        try(FileOutputStream out=new FileOutputStream(output)){
                            int count;
                            while((count=input.read(bytes))!=-1){
                                fileBytes+=count;allBytes+=count;
                                if(fileBytes>MAX_FILE||allBytes>MAX_TOTAL)
                                    throw new IOException("El paquete supera el tamaño de seguridad");
                                hash.update(bytes,0,count);out.write(bytes,0,count);
                            }
                            out.getFD().sync();
                        }
                        if(fileBytes<4000||!expected.get(name).equals(hex(hash.digest())))
                            throw new IOException("El audio no coincide con el original: "+name);
                        input.closeEntry();
                    }
                }
            }
            if(found.size()!=5&&found.size()!=13)
                throw new IOException("Se necesitan 5 o 13 archivos originales (recibidos "+found.size()+")");
            for(int i=0;i<5;i++)if(!found.contains(NAMES[i]+".mp3"))
                throw new IOException("Falta un audio original: "+NAMES[i]);
            if(found.size()==5&&hasFullPack(c))
                throw new IOException("Ya tenés 13 sonidos. Elegí el ZIP de 13 para actualizar.");

            // Preserve the previous 5-track pack during upgrade/rollback.
            delete(backup);
            if(base.exists()&&!base.renameTo(backup))throw new IOException("No se pudo resguardar el pack anterior");
            if(!tmp.renameTo(base)){
                if(backup.exists())backup.renameTo(base);
                throw new IOException("No se pudo activar el paquete");
            }
            success=true;
            lastError="";
        }catch(Exception ex){
            lastError=ex.getMessage()==null?ex.toString():ex.getMessage();
            Log.e(TAG,"Import failed: "+lastError,ex);
            throw ex;
        }finally{
            delete(tmp);
            if(success)delete(backup);
        }
    }
    private static String hex(byte[] data){
        char[] alpha="0123456789abcdef".toCharArray();char[] out=new char[data.length*2];
        for(int i=0;i<data.length;i++){out[2*i]=alpha[(data[i]&255)>>4];out[2*i+1]=alpha[data[i]&15];}
        return new String(out);
    }
    private static void delete(File f){
        if(!f.exists())return;
        if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File child:a)delete(child);}
        f.delete();
    }
    private static void releaseActive(){
        if(active==null)return;
        try{active.stop();}catch(Exception ignored){}
        try{active.release();}catch(Exception ignored){}
        active=null;
    }
    /** One strong MediaPlayer at a time; newest important sound takes precedence, never overlaps. */
    public static synchronized boolean play(Context c,String name){
        if(!isInstalled(c,name))return false;
        File file=new File(root(c),name+".mp3");
        MediaPlayer player=new MediaPlayer();
        try{
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(file.getAbsolutePath());
            player.setVolume(0.72f,0.72f);
            player.prepare();
            releaseActive();
            active=player;
            player.setOnCompletionListener(done->{
                synchronized(SoundPack.class){
                    if(active==done){try{done.release();}catch(Exception ignored){}active=null;}
                    else try{done.release();}catch(Exception ignored){}
                }
            });
            player.setOnErrorListener((failed,what,extra)->{
                synchronized(SoundPack.class){
                    lastError="MediaPlayer "+name+" error "+what+"/"+extra;
                    Log.e(TAG,lastError);
                    if(active==failed)active=null;
                    try{failed.release();}catch(Exception ignored){}
                }
                return true;
            });
            player.start();
            lastError="";
            return true;
        }catch(Exception ex){
            try{player.release();}catch(Exception ignored){}
            lastError="Error al reproducir "+name+": "+ex.getMessage();
            Log.e(TAG,lastError,ex);
            return false;
        }
    }
}