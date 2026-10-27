package uy.com.mapatrayectos.qa;
import java.net.URI;
import java.util.Locale;
public final class LiveQaPolicy {
 private LiveQaPolicy(){}
 public static String endpoint(String input){
  try{
   URI url=new URI(input==null?"":input.trim());
   String host=url.getHost()==null?"":url.getHost().toLowerCase(Locale.ROOT);
   if(!"https".equals(url.getScheme()) || url.getPort()!=-1 ||
     url.getUserInfo()!=null || url.getQuery()!=null || url.getFragment()!=null ||
     !(url.getPath()==null||url.getPath().isEmpty()||"/".equals(url.getPath())) ||
     !host.matches("quicktrip-(?:live-)?qa(?:-[a-z0-9]+)?\\.[a-z0-9-]+\\.workers\\.dev"))
     throw new IllegalArgumentException();
   return "https://"+host;
  }catch(Exception e){throw new IllegalArgumentException("Se requiere Worker HTTPS Quick Trip QA");}
 }
 public static String requestId(String id){
  String s=id==null?"":id.trim().toLowerCase(Locale.ROOT);
  if(!s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
   throw new IllegalArgumentException("ID de prueba inválido");
  return s;
 }
 public static boolean valid(boolean shift,boolean paused,boolean trip,boolean has,
     double lat,double lng,float accuracy,long age){
  return shift&&!paused&&trip&&has&&Double.isFinite(lat)&&Double.isFinite(lng)
   &&lat>=-35.5&&lat<=-30&&lng>=-59.5&&lng<=-52
   &&Float.isFinite(accuracy)&&accuracy>0&&accuracy<=45&&age>=0&&age<=12000;
 }
}
