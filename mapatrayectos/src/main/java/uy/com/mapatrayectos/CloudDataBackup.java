package uy.com.mapatrayectos;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Private, user-initiated export of cloud HISTORY; never modifies remote tables. */
public final class CloudDataBackup {
    private CloudDataBackup(){}
    public static final String[] DATASETS={
        "jornadas","viajes_mapa","puntos_gps","reservas",
        "eventos_reserva","paradas_reserva","mensajes_reserva"
    };
    private static final int PAGE_SIZE=800;
    private static final int MAX_PAGES_PER_DATASET=100000;

    public interface Progress { void onProgress(String message); }

    public static final class Result {
        public final JSONObject counts;
        public final long cutoffMs;
        public final String sha256;
        public final long bytes;
        Result(JSONObject counts,long cutoffMs,String sha256,long bytes){
            this.counts=counts;this.cutoffMs=cutoffMs;this.sha256=sha256;this.bytes=bytes;
        }
        public String summary(){
            return "Jornadas: "+counts.optLong("jornadas")+
                "\nViajes Mapa: "+counts.optLong("viajes_mapa")+
                "\nPuntos GPS: "+counts.optLong("puntos_gps")+
                "\nReservas: "+counts.optLong("reservas")+
                "\nIntegridad ZIP verificada (SHA-256): "+sha256;
        }
    }
    public static String suggestedName(){
        return "Traslados_Nube_Historial_"+new SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US)
            .format(new Date())+".zip";
    }

    public static Result export(Context context,Uri destination,String pin,Progress progress)
        throws Exception {
        if(pin==null||!pin.matches("[0-9]{4,10}"))
            throw new IllegalArgumentException("PIN de conductor inválido");
        // Explicitly test authorization before writing any user-visible file.
        JSONObject status=Api.getSystemStatus(pin);
        if(status.optLong("database_bytes",-1)<0)
            throw new IOException("No se pudo verificar el acceso al servidor");
        Api.registerCloudBackupDevice();
        long cutoffMs=System.currentTimeMillis();
        JSONObject counts=new JSONObject();
        OutputStream raw=context.getContentResolver().openOutputStream(destination,"w");
        if(raw==null)throw new IOException("No se pudo abrir el archivo de destino");
        try(ZipOutputStream zip=new ZipOutputStream(raw)){
            for(String dataset:DATASETS){
                if(Thread.currentThread().isInterrupted())throw new IOException("Exportación interrumpida");
                if(progress!=null)progress.onProgress("Nube: exportando "+dataset.replace('_',' ')+"…");
                zip.putNextEntry(new ZipEntry("historial/"+dataset+".jsonl"));
                long total=0;String cursor="";int pages=0;
                while(true){
                    if(++pages>MAX_PAGES_PER_DATASET)
                        throw new IOException("Se superó el límite seguro de páginas");
                    JSONObject reply=Api.getCloudHistoryPage(pin,dataset,cursor,cutoffMs,PAGE_SIZE);
                    if(!dataset.equals(reply.optString("dataset")))
                        throw new IOException("Respuesta inesperada del servidor");
                    JSONArray items=reply.getJSONArray("items");
                    int n=items.length();
                    if(n>PAGE_SIZE)throw new IOException("Página demasiado grande");
                    for(int i=0;i<n;i++){
                        byte[] data=(items.getJSONObject(i).toString()+"\n")
                            .getBytes(StandardCharsets.UTF_8);
                        zip.write(data);
                    }
                    total+=n;
                    if(progress!=null && pages%10==0)
                        progress.onProgress("Nube: "+dataset.replace('_',' ')+" · "+total+" registros");
                    if(!reply.optBoolean("has_more",false))break;
                    String next=reply.optString("next_cursor","");
                    if(n==0||next.isEmpty()||next.compareTo(cursor)<=0)
                        throw new IOException("Paginación inconsistente: exportación detenida");
                    cursor=next;
                }
                zip.closeEntry();
                counts.put(dataset,total);
            }
            JSONObject manifest=new JSONObject();
            manifest.put("format","traslados-cloud-history-export");
            manifest.put("format_version",1);
            manifest.put("created_at_ms",System.currentTimeMillis());
            manifest.put("cutoff_ms",cutoffMs);
            manifest.put("counts",counts);
            manifest.put("source","Supabase · Traslados");
            manifest.put("read_only",true);
            manifest.put("data_preserved_on_server",true);
            manifest.put("description","Archivo de historial JSONL. No es un restaurador automático.");
            manifest.put("excluded","Credenciales, PIN, sesiones, secretos y configuración");
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        // Reopen the SAF document. ZIP integrity + row counts are checked independently.
        String hash=verify(context,destination,counts);
        if(progress!=null)progress.onProgress("Respaldo verificado correctamente");
        return new Result(counts,cutoffMs,hash,-1);
    }

    private static String verify(Context context,Uri file,JSONObject expected) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream raw=context.getContentResolver().openInputStream(file)){
            if(raw==null)throw new IOException("No se pudo leer el ZIP exportado");
            byte[] buf=new byte[32768];int n;
            while((n=raw.read(buf))!=-1)digest.update(buf,0,n);
        }
        try(InputStream raw=context.getContentResolver().openInputStream(file);
            ZipInputStream zip=new ZipInputStream(raw)){
            if(raw==null)throw new IOException("ZIP no accesible para validación");
            byte[] buf=new byte[32768];
            JSONObject found=new JSONObject();
            boolean manifest=false;
            for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                String name=e.getName();
                if("manifest.json".equals(name)){
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                    int n;while((n=zip.read(buf))!=-1){
                        if(out.size()+n>32768)throw new IOException("Manifest demasiado grande");
                        out.write(buf,0,n);
                    }
                    JSONObject m=new JSONObject(out.toString("UTF-8"));
                    if(!"traslados-cloud-history-export".equals(m.optString("format")))
                        throw new IOException("Manifest no reconocido");
                    if(!expected.similar(m.getJSONObject("counts")))
                        throw new IOException("No coincide el resumen del respaldo");
                    manifest=true;
                }else if(name.startsWith("historial/")&&name.endsWith(".jsonl")){
                    String dataset=name.substring("historial/".length(),name.length()-".jsonl".length());
                    if(!containsDataset(dataset))throw new IOException("Datos no reconocidos en ZIP");
                    long lines=0;int n;boolean nonempty=false;byte last=10;
                    while((n=zip.read(buf))!=-1)for(int i=0;i<n;i++){
                        if(buf[i]=='\n')lines++;
                        last=buf[i];nonempty=true;
                    }
                    if(nonempty&&last!=10)throw new IOException("Registro incompleto");
                    found.put(dataset,lines);
                }else throw new IOException("Entrada ZIP inesperada");
                zip.closeEntry();
            }
            if(!manifest||found.length()!=DATASETS.length||!found.similar(expected))
                throw new IOException("Contenido incompleto o conteos incorrectos");
        }
        StringBuilder hash=new StringBuilder();
        for(byte b:digest.digest())hash.append(String.format(Locale.US,"%02x",b&0xff));
        return hash.toString();
    }

    private static boolean containsDataset(String input){
        for(String name:DATASETS)if(name.equals(input))return true;
        return false;
    }
}
