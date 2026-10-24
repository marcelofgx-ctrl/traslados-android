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
    if(!"No pudimos completar la operación. Intentá nuevamente.".equals(actual))
      throw new AssertionError("Current Android behavior changed: "+actual);
    Exception schedule=new ApiException("P0001","HORARIO_YA_NO_DISPONIBLE");
    if(!publicMessage("fixed",schedule).contains("horario ya no está disponible"))
      throw new AssertionError("Schedule error regression");
    System.out.println("PASS ANDROID CURRENT: real extracted publicMessage lacks distinct 23505 message; schedule preserved");
  }
}`;
const temp=fs.mkdtempSync(path.join(os.tmpdir(),"qt-android-error-"));
try{
  const filename=path.join(temp,"ActualAndroidMessageHarness.java");
  fs.writeFileSync(filename,java);
  execFileSync("javac",["-encoding","UTF-8","-d",temp,filename],{stdio:"inherit"});
  execFileSync("java",["-cp",temp,"ActualAndroidMessageHarness"],{stdio:"inherit"});
}finally{fs.rmSync(temp,{recursive:true,force:true});}
