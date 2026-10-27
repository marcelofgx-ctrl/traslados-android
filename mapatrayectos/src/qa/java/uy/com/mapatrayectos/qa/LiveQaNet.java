package uy.com.mapatrayectos.qa;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** QA endpoint guard. Deliberately incapable of reaching the production Supabase host. */
final class LiveQaNet {
    private static final Pattern HOST = Pattern.compile(
        "^quicktrip-(?:live-)?qa[a-z0-9-]*\\.[a-z0-9-]+\\.workers\\.dev$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE);
    private LiveQaNet(){}

    static String base(String input) {
        try {
            URI uri = new URI(input == null ? "" : input.trim());
            if (!"https".equals(uri.getScheme()) || uri.getHost()==null
                || !HOST.matcher(uri.getHost()).matches()
                || uri.getPort()!=-1 || uri.getRawUserInfo()!=null
                || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
                throw new IllegalArgumentException("Solo Worker QA HTTPS *.workers.dev");
            return uri.getScheme()+"://"+uri.getHost();
        } catch(Exception e) { throw new IllegalArgumentException("URL inválida: usar quicktrip-qa-...<cuenta>.workers.dev"); }
    }
    static String id(String value) {
        String s=value==null?"":value.trim();
        if (!UUID.matcher(s).matches()) throw new IllegalArgumentException("ID de solicitud QA inválido");
        return s;
    }
    static String token(String value) {
        String s=value==null?"":value.trim();
        if (s.length()<32 || s.length()>240 || !s.matches("[A-Za-z0-9._~-]+"))
            throw new IllegalArgumentException("Token temporal QA inválido");
        return s;
    }
    static JSONObject call(String endpoint,String auth,String method,String path,JSONObject body) throws IOException {
        base(endpoint);token(auth);
        if (!path.startsWith("/v1/") || path.contains("..") || path.contains("?"))
            throw new IOException("Ruta QA no permitida");
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(endpoint+path).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(6500);c.setReadTimeout(9000);
            c.setRequestMethod(method);
            c.setRequestProperty("Authorization","Bearer "+auth);
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("Cache-Control","no-store");
            if(body!=null){
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                if(bytes.length>2500)throw new IOException("Cuerpo QA demasiado grande");
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            }
            int status=c.getResponseCode();
            InputStream in=(status>=200&&status<300)?c.getInputStream():c.getErrorStream();
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            if(in!=null)try(InputStream stream=in){byte[] buf=new byte[2048];int n;while((n=stream.read(buf))!=-1){
                if(output.size()+n>12000)throw new IOException("Respuesta excesiva");
                output.write(buf,0,n);
            }}
            if(status<200||status>=300)throw new IOException("Backend QA: HTTP "+status);
            JSONObject obj=new JSONObject(output.toString("UTF-8"));
            if(!obj.optBoolean("qaOnly",false) && !(obj.optJSONObject("request")!=null && obj.optJSONObject("request").optBoolean("qaOnly",false)))throw new IOException("Respuesta sin marca QA");
            return obj;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("No se pudo validar la respuesta QA");}
        finally{if(c!=null)c.disconnect();}
    }
}
