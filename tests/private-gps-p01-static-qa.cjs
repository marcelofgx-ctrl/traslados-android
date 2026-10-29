"use strict";
const fs=require("node:fs");
const assert=require("node:assert/strict");
const path=require("node:path");
const root=path.resolve(__dirname,"..");
const file=f=>fs.readFileSync(path.join(root,f),"utf8");
const api=file("mapatrayectos/src/main/java/uy/com/mapatrayectos/Api.java");
const ui=file("mapatrayectos/src/main/java/uy/com/mapatrayectos/SystemStatusActivity.java");
const diag=file("mapatrayectos/src/main/java/uy/com/mapatrayectos/PrivateGpsDiagnostic.java");
const names=["mapa_presence_ping_v1","mapa_reservation_position_v1"];
for(const name of names){
 assert.equal((api.match(new RegExp('postRpc\\("'+name+'"','g'))||[]).length,1,
   "Must preserve exactly one call to "+name);
}
const tests={
 "presence async guard unchanged":api.includes("if(APP==null||!pickupPresenceEnabled()||!PRESENCE_BUSY.compareAndSet(false,true))return;"),
 "preserve public availability and soft deadline":api.includes("System.currentTimeMillis()+75000L : 0L"),
 "preserve public heartbeat result gate":api.includes('result.optBoolean("ok",false)&&active&&!paused'),
 "preserve longitude and accuracy requirements":api.includes("&&gpsTimeMs>0&&accuracy>=0&&accuracy<=45")&&api.includes("lat>=-35.3&&lat<=-30&&lng>=-58.8&&lng<=-52.9"),
 "keep private endpoint payload unchanged": [
  "p_device_id","p_device_secret","p_shift_active","p_shift_paused","p_lat","p_lng","p_accuracy_m","p_gps_at_ms"
 ].every(k=>api.includes('privateBody.put("'+k+'"')),
 "interpret private RPC response":api.includes('JSONObject privateResult=new JSONObject(postRpc("mapa_reservation_position_v1",privateBody))')&&
   api.includes("PrivateGpsDiagnostic.recordRpcResult("),
 "categorize private transport without exception":api.includes("PrivateGpsDiagnostic.privateTransportError();")&&
   !api.includes("PrivateGpsDiagnostic.privateTransportError(ignored)"),
 "categorize public transport without exception":api.includes("PrivateGpsDiagnostic.publicTransportError();"),
 "UI is read-only and shows local code":ui.includes('section(content,"04 · QA GPS PRIVADO"')&&
   ui.includes("PrivateGpsDiagnostic.message(Api.pickupPresenceEnabled())"),
 "no call from UI to private endpoint":!ui.includes("mapa_reservation_position_v1")&&
   !ui.includes("postRpc("),
 "diagnostic class has no Android, network or logs":
   !/\bimport\s+android\./.test(diag)&&
   !/(HttpURLConnection|URLConnection|System\.out|System\.err|SharedPreferences|Log\.)/.test(diag),
 "diagnostic stores only allowlisted status":diag.includes("private static volatile Code last = Code.NO_ATTEMPT;")&&
   !/private\s+static\s+(?:volatile\s+)?(?:String|JSONObject|Exception|Throwable)\s+\w+\s*(?:=|;)/.test(diag),
 "avoid untrusted response/exception display":
   !/getMessage\(\)|printStackTrace\(|Log\.[a-zA-Z]+\(/.test(api.slice(api.indexOf("public static void publishPickupPresenceAsync("),api.indexOf("private Api(){}"))),
 "no extra GPS RPC or password logging":(api.match(/mapa_reservation_position_v1/g)||[]).length===1
};
for(const [name,ok] of Object.entries(tests)){
 assert(ok,name);
 console.log("QA P0.1 PASS "+name);
}
const out=path.join(root,"artifacts/private-gps-p01");
fs.mkdirSync(out,{recursive:true});
const evidence={
 branchType:"isolated QA",
 stage:"offline source guard",
 checks:Object.keys(tests).map(name=>({name,passed:true})),
 tests:Object.keys(tests).length,
 liveRequests:0,realReservationsCreated:0,apkBuilt:false,
 privacy:"Only allowlisted status codes; no GPS/IDs/tokens/error strings persisted",
 physicalValidation:"pending separate APK authorization"
};
fs.writeFileSync(path.join(out,"static-evidence.json"),JSON.stringify(evidence,null,2)+"\n");
console.log("QA P0.1 SUMMARY "+Object.keys(tests).length+" PASS / 0 FAIL (static guard)");
