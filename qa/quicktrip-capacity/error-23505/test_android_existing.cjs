"use strict";
const fs=require("node:fs"),os=require("node:os"),path=require("node:path");
const assert=require("node:assert/strict");
const {execFileSync}=require("node:child_process");
const src=fs.readFileSync(path.join(__dirname,"../../../conductor/src/main/java/uy/com/traslados/conductor/Api.java"),"utf8");
const alerts=fs.readFileSync(path.join(__dirname,"../../../mapatrayectos/src/main/java/uy/com/mapatrayectos/TransferAlerts.java"),"utf8");
assert.match(src,/class ApiException extends IOException/);
assert.match(src,/backendCode/);
assert.match(src,/backendMessage/);
assert.match(src,/backendCode=j\.optString\("code"/);
assert.match(alerts,/else message=uy\.com\.traslados\.conductor\.Api\.publicMessage\("fixed",ex\)/);
assert.match(alerts,/boolean ok=false/);
assert.match(alerts,/ok=true/);
const first=src.indexOf("public static String publicMessage("),
      last=src.indexOf("public static String postRpc(",first);
assert.ok(first>0 && last>first,"actual Android publicMessage source present");
const method=src.slice(first,last);
const java=`import java.io.IOException;
import java.util.Locale;
public final class ActualAndroidMessageHarness {
  static class ApiException extends IOException {
    public final String backendCode,backendMessage;
    ApiException(String code,String message){
      super("HTTP 409: "+message);backendCode=code;backendMessage=message;
    }
  }
  ${method}
  public static void main(String[] args) {
    Exception conflict=new ApiException("23505",
      "duplicate key value violates unique constraint \\"qt_one_active_service_uq\\"");
    String actual=publicMessage("fixed",conflict);
    if(!actual.startsWith("Conductor ocupado:"))
      throw new AssertionError("Capacity error was not mapped: "+actual);
    Exception schedule=new ApiException("P0001","HORARIO_YA_NO_DISPONIBLE");
    if(!publicMessage("fixed",schedule).contains("horario ya no está disponible"))
      throw new AssertionError("Schedule error regression");
    java.util.List<String> cases=java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]));
    int count=0;
    for(String line:cases){
      if(line.isBlank()||line.startsWith("#"))continue;
      String[] fields=line.split("\\t",3);
      if(fields.length!=3)throw new AssertionError("Malformed fixture: "+line);
      String code=fields[0].equals("-")?null:fields[0];
      String mapped=publicMessage("fixed",new ApiException(code,fields[1]));
      boolean busy=mapped.startsWith("Conductor ocupado:");
      if(busy!=fields[2].equals("BUSY"))
        throw new AssertionError("Misclassified: "+line+" -> "+mapped);
      count++;
    }
    if(count!=14)throw new AssertionError("Fixture drift: "+count);
    if(publicMessage("fixed",new java.net.SocketTimeoutException("timed out")).startsWith("Conductor ocupado:"))
      throw new AssertionError("Network timeout must not become occupancy");
    if(!publicMessage("fixed",new java.net.SocketTimeoutException("timed out")).contains("Revisá Internet"))
      throw new AssertionError("Network timeout regression");
    System.out.println("PASS ANDROID INTEGRATED: "+count+" fixtures through real extracted publicMessage; schedule/network intact");
  }
}`;
const temp=fs.mkdtempSync(path.join(os.tmpdir(),"qt-android-error-"));
try{
  const filename=path.join(temp,"ActualAndroidMessageHarness.java");
  fs.writeFileSync(filename,java);
  execFileSync("javac",["-encoding","UTF-8","-d",temp,filename],{stdio:"inherit"});
  execFileSync("java",["-cp",temp,"ActualAndroidMessageHarness",
    path.join(__dirname,"cases.tsv")],{stdio:"inherit"});
}finally{fs.rmSync(temp,{recursive:true,force:true});}
