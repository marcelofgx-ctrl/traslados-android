package uy.com.mapatrayectos;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs the 5 exact MP3 files already provided by the user, with SHA-256 verification. */
public final class SoundPack {
    private static final String DIR="mapa_sound_pack";
    private static final String[] NAMES={
        "sound_shift_start.mp3","sound_trip_start.mp3","sound_uber.mp3","sound_cancel.mp3","sound_trip_end.mp3"
    };
    private static final String[] HASHES={
        "6fdeaf7026e10d34ca1528b02593b971bf01a489e04bce39c356cab4709158e3",
        "118f14bd5325e5b360245829f9c7f67523b1afa0b59baf95d8810e145dc590a1",
        "115cfe4f9836ca1401fb97a4051752d5870d7c5e01a7561fcad5a2a280501116",
        "899f59a89f4806dcd1feab4f8ea14d5bdcfd0337d7fed7e13fe353e9d1a86822",
        "27434f899c4f8d6e976421a2af5fe2f441858c58817a0153718629a8b6191bad"
    };
    private static final int MAX_FILE=1200000;
    private static final int MAX_TOTAL=5200000;
    private SoundPack(){}

    private static File root(Context c){return new File(c.getFilesDir(),DIR);}
    public static boolean ready(Context c){
        File base=root(c);
        if(!base.isDirectory())return false;
        for(String n:NAMES)if(!new File(base,n).isFile())return false;
        return true;
    }
    public static String describe(Context c){return ready(c)?"5 de 5 sonidos originales instalados":"Pendiente: importar los 5 sonidos originales";}

    /** Files are staged first; never leave a half-imported package if validation fails. */
    public static synchronized void install(Context c,Uri zipUri) throws Exception {
        File base=root(c);
        File tmp=new File(c.getFilesDir(),DIR+"_temp");
        File backup=new File(c.getFilesDir(),DIR+"_previous");
        delete(tmp);
        if(!tmp.mkdirs())throw new IOException("No se pudo crear directorio temporal");
        Map<String,String> expected=new HashMap<>();
        for(int i=0;i<NAMES.length;i++)expected.put(NAMES[i],HASHES[i]);
        Set<String> found=new HashSet<>();
        long allBytes=0L;
        boolean success=false;
        try{
            try(InputStream raw=c.getContentResolver().openInputStream(zipUri)){
                if(raw==null)throw new IOException("No se pudo abrir el ZIP");
                try(ZipInputStream input=new ZipInputStream(raw)){
                    ZipEntry entry;
                    byte[] bytes=new byte[16384];
                    while((entry=input.getNextEntry())!=null){
                        String name=entry.getName();
                        if(entry.isDirectory()||!expected.containsKey(name)||!found.add(name))
                            throw new IOException("ZIP inválido: archivo inesperado o duplicado");
                        File output=new File(tmp,name);
                        MessageDigest hash=MessageDigest.getInstance("SHA-256");
                        int fileBytes=0;
                        try(FileOutputStream out=new FileOutputStream(output)){
                            int count;
                            while((count=input.read(bytes))!=-1){
                                fileBytes+=count;allBytes+=count;
                                if(fileBytes>MAX_FILE||allBytes>MAX_TOTAL)throw new IOException("Paquete de audio demasiado grande");
                                hash.update(bytes,0,count);out.write(bytes,0,count);
                            }
                            out.getFD().sync();
                        }
                        if(fileBytes<4000||!expected.get(name).equals(hex(hash.digest())))
                            throw new IOException("Audio original alterado: "+name);
                        input.closeEntry();
                    }
                }
            }
            if(found.size()!=NAMES.length)throw new IOException("Faltan MP3 del paquete (recibidos "+found.size()+"/5)");
            delete(backup);
            if(base.exists()&&!base.renameTo(backup))throw new IOException("No se pudo resguardar el pack anterior");
            if(!tmp.renameTo(base)){
                if(backup.exists())backup.renameTo(base);
                throw new IOException("No se pudo activar el paquete");
            }
            success=true;
        }finally{
            delete(tmp);
            if(success)delete(backup);
        }
    }
    private static String hex(byte[] data){
        char[] alpha="0123456789abcdef".toCharArray();
        char[] result=new char[data.length*2];
        for(int i=0;i<data.length;i++){result[2*i]=alpha[(data[i]&255)>>4];result[2*i+1]=alpha[data[i]&15];}
        return new String(result);
    }
    private static void delete(File file){
        if(!file.exists())return;
        if(file.isDirectory()){
            File[] children=file.listFiles();
            if(children!=null)for(File c:children)delete(c);
        }
        file.delete();
    }
    public static boolean play(Context c,String resourceName){
        if(resourceName==null)return false;
        if(!Arrays.asList("sound_shift_start","sound_trip_start","sound_uber","sound_cancel","sound_trip_end").contains(resourceName))
            return false;
        File audio=new File(root(c),resourceName+".mp3");
        if(!audio.isFile())return false;
        MediaPlayer player=new MediaPlayer();
        try{
            player.setDataSource(audio.getAbsolutePath());
            player.setOnCompletionListener(MediaPlayer::release);
            player.setOnErrorListener((p,what,extra)->{p.release();return true;});
            player.prepare();player.start();
            return true;
        }catch(Exception e){
            try{player.release();}catch(Exception ignored){}
            return false;
        }
    }
}
