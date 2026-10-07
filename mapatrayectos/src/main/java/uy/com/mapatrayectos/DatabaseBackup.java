package uy.com.mapatrayectos;

import android.content.*;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public final class DatabaseBackup {
    private static final long MAX_BACKUP_BYTES=512L*1024L*1024L;
    private static final int MIN_IMPORT_DB_VERSION=2;
    private DatabaseBackup(){}

    public static final class BackupInfo {
        public final String createdAt,appVersion,sha256;
        public final int dbVersion,shifts,trips,points;
        BackupInfo(String createdAt,String appVersion,int dbVersion,int shifts,int trips,int points,String sha256){
            this.createdAt=createdAt;this.appVersion=appVersion;this.dbVersion=dbVersion;this.shifts=shifts;this.trips=trips;this.points=points;this.sha256=sha256;
        }
        public String summary(){
            return "Fecha: "+createdAt+"\nVersión: "+appVersion+"\nJornadas: "+shifts+" · Viajes: "+trips+" · Puntos GPS: "+points+"\nBase: v"+dbVersion;
        }
    }

    public static String suggestedName(){
        return "MapaTrayectos_Backup_"+new SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(new Date())+".zip";
    }

    public static BackupInfo exportBackup(Context context, Uri destination) throws Exception {
        TrackDb helper=new TrackDb(context);
        helper.getWritableDatabase();
        helper.checkpoint();
        JSONObject counts=helper.counts();
        helper.close();

        File db=context.getDatabasePath(TrackDb.DB_NAME);
        if(!db.exists()) throw new IOException("No existe la base local");
        String sha=sha256(db);
        String version=appVersion(context);
        String created=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",new Locale("es","UY")).format(new Date());

        JSONObject manifest=new JSONObject();
        manifest.put("format","mapa-trayectos-backup");
        manifest.put("format_version",1);
        manifest.put("created_at",created);
        manifest.put("app_version",version);
        manifest.put("version_code",versionCode(context));
        manifest.put("db_version",TrackDb.DB_VERSION);
        manifest.put("shifts",counts.optInt("shifts"));
        manifest.put("trips",counts.optInt("trips"));
        manifest.put("points",counts.optInt("points"));
        manifest.put("db_size_bytes",db.length());
        manifest.put("db_sha256",sha);

        OutputStream raw=context.getContentResolver().openOutputStream(destination,"w");
        if(raw==null) throw new IOException("No se pudo abrir el destino");
        try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(raw))){
            out.putNextEntry(new ZipEntry(TrackDb.DB_NAME));
            try(InputStream in=new BufferedInputStream(new FileInputStream(db))){copy(in,out);}
            out.closeEntry();
            out.putNextEntry(new ZipEntry("manifest.json"));
            out.write(manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return new BackupInfo(created,version,TrackDb.DB_VERSION,counts.optInt("shifts"),counts.optInt("trips"),counts.optInt("points"),sha);
    }

    public static BackupInfo inspectBackup(Context context, Uri source) throws Exception {
        Extracted e=extractAndValidate(context,source);
        try{return e.info;}finally{deleteQuietly(e.db);deleteQuietly(e.manifest);deleteQuietly(e.dir);}
    }

    public static BackupInfo importBackup(Context context, Uri source) throws Exception {
        Extracted e=extractAndValidate(context,source);
        File db=context.getDatabasePath(TrackDb.DB_NAME);
        File parent=db.getParentFile();
        if(parent==null) throw new IOException("Ruta de base inválida");
        parent.mkdirs();

        TrackDb helper=new TrackDb(context);
        helper.getWritableDatabase();
        helper.checkpoint();
        helper.close();

        File oldCopy=new File(context.getCacheDir(),"mapa_trayectos_pre_restore.db");
        File staged=new File(parent,TrackDb.DB_NAME+".restore.tmp");
        deleteQuietly(oldCopy);deleteQuietly(staged);
        if(db.exists()) Files.copy(db.toPath(),oldCopy.toPath(),StandardCopyOption.REPLACE_EXISTING);
        try{
            Files.copy(e.db.toPath(),staged.toPath(),StandardCopyOption.REPLACE_EXISTING);
            deleteQuietly(new File(db.getPath()+"-wal"));
            deleteQuietly(new File(db.getPath()+"-shm"));
            deleteQuietly(new File(db.getPath()+"-journal"));
            try{
                Files.move(staged.toPath(),db.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException ex){
                Files.move(staged.toPath(),db.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            validateDatabase(db,e.info.dbVersion,e.info.shifts,e.info.trips,e.info.points);
            if(e.info.dbVersion<TrackDb.DB_VERSION){TrackDb upgraded=new TrackDb(context);upgraded.getWritableDatabase();upgraded.checkpoint();upgraded.close();validateDatabase(db,TrackDb.DB_VERSION,e.info.shifts,e.info.trips,e.info.points);}
            deleteQuietly(oldCopy);
            return e.info;
        }catch(Exception failure){
            deleteQuietly(staged);
            if(oldCopy.exists()) Files.copy(oldCopy.toPath(),db.toPath(),StandardCopyOption.REPLACE_EXISTING);
            throw failure;
        }finally{
            deleteQuietly(e.db);deleteQuietly(e.manifest);deleteQuietly(e.dir);deleteQuietly(oldCopy);
        }
    }

    private static Extracted extractAndValidate(Context context, Uri source) throws Exception {
        File dir=new File(context.getCacheDir(),"mapa_import_"+System.currentTimeMillis());
        if(!dir.mkdirs()) throw new IOException("No se pudo preparar la importación");
        File db=new File(dir,TrackDb.DB_NAME),manifestFile=new File(dir,"manifest.json");
        long total=0;
        InputStream raw=context.getContentResolver().openInputStream(source);
        if(raw==null) throw new IOException("No se pudo abrir el backup");
        try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(raw))){
            ZipEntry z;
            while((z=zin.getNextEntry())!=null){
                if(z.isDirectory()){zin.closeEntry();continue;}
                String name=z.getName();
                if(name.contains("..")||name.contains("/")||name.contains("\\")) throw new IOException("ZIP inválido");
                File out;
                if(TrackDb.DB_NAME.equals(name)) out=db; else if("manifest.json".equals(name)) out=manifestFile; else {zin.closeEntry();continue;}
                try(OutputStream os=new BufferedOutputStream(new FileOutputStream(out))){
                    byte[] buf=new byte[32768];int n;while((n=zin.read(buf))>0){total+=n;if(total>MAX_BACKUP_BYTES)throw new IOException("Backup demasiado grande");os.write(buf,0,n);}
                }
                zin.closeEntry();
            }
        }
        if(!db.exists()||!manifestFile.exists()) throw new IOException("El backup no contiene base y manifest");
        String manifestText=new String(Files.readAllBytes(manifestFile.toPath()),java.nio.charset.StandardCharsets.UTF_8);
        JSONObject m=new JSONObject(manifestText);
        if(!"mapa-trayectos-backup".equals(m.optString("format"))) throw new IOException("Formato de backup no reconocido");
        int dbVersion=m.optInt("db_version",-1);
        if(dbVersion<MIN_IMPORT_DB_VERSION||dbVersion>TrackDb.DB_VERSION) throw new IOException("Versión de base incompatible: "+dbVersion+" (compatibles "+MIN_IMPORT_DB_VERSION+"–"+TrackDb.DB_VERSION+")");
        String expected=m.optString("db_sha256","");
        String actual=sha256(db);
        if(expected.isEmpty()||!expected.equalsIgnoreCase(actual)) throw new IOException("Checksum SHA-256 inválido");
        int shifts=m.optInt("shifts",-1),trips=m.optInt("trips",-1),points=m.optInt("points",-1);
        validateDatabase(db,dbVersion,shifts,trips,points);
        BackupInfo info=new BackupInfo(m.optString("created_at","Sin fecha"),m.optString("app_version","Desconocida"),dbVersion,shifts,trips,points,actual);
        return new Extracted(dir,db,manifestFile,info);
    }

    private static void validateDatabase(File dbFile,int expectedVersion,int expectedShifts,int expectedTrips,int expectedPoints) throws Exception {
        SQLiteDatabase db=SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try{
            Cursor ic=db.rawQuery("PRAGMA integrity_check",null);try{if(!ic.moveToFirst()||!"ok".equalsIgnoreCase(ic.getString(0)))throw new IOException("La base no supera integrity_check");}finally{ic.close();}
            Cursor uv=db.rawQuery("PRAGMA user_version",null);try{int v=uv.moveToFirst()?uv.getInt(0):-1;if(v!=expectedVersion)throw new IOException("DB_VERSION no coincide: "+v);}finally{uv.close();}
            int shifts=count(db,"shifts"),trips=count(db,"trips"),points=count(db,"points");
            if(expectedShifts>=0&&shifts!=expectedShifts)throw new IOException("Conteo de jornadas no coincide");
            if(expectedTrips>=0&&trips!=expectedTrips)throw new IOException("Conteo de viajes no coincide");
            if(expectedPoints>=0&&points!=expectedPoints)throw new IOException("Conteo de puntos no coincide");
        }finally{db.close();}
    }

    private static int count(SQLiteDatabase db,String table){
        Cursor c=db.rawQuery("select count(*) from "+table,null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}
    }

    private static String appVersion(Context c) throws Exception {PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return p.versionName==null?"":p.versionName;}
    private static long versionCode(Context c) throws Exception {PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    private static String sha256(File f) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)md.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();
    }
    private static void copy(InputStream in,OutputStream out) throws IOException {byte[] b=new byte[32768];int n;while((n=in.read(b))>0)out.write(b,0,n);}
    private static void deleteQuietly(File f){if(f!=null&&f.exists())try{f.delete();}catch(Exception ignored){}}
    private static final class Extracted {final File dir,db,manifest;final BackupInfo info;Extracted(File dir,File db,File manifest,BackupInfo info){this.dir=dir;this.db=db;this.manifest=manifest;this.info=info;}}
}
