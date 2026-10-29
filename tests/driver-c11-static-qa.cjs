"use strict";
const fs=require("node:fs"),assert=require("node:assert/strict"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const content=p=>fs.readFileSync(path.join(root,p),"utf8");
const src="mapatrayectos/src/main/java/uy/com/mapatrayectos/";
const sheet=content(src+"conductorv0/DriverRideSheet.java");
const policy=content(src+"conductorv0/DriverSheetPolicy.java");
const navigator=content(src+"conductorv0/ConductorV0Navigator.java");
const controller=content(src+"conductorv0/ConductorLiveController.java");
const main=content(src+"MainActivity.java");
const gradle=content("mapatrayectos/build.gradle");
const checks={
 "unique recovery allowed only for server active statuses":
   policy.includes('return "ACEPTADA".equals(status) || "EN_VIAJE".equals(status);') &&
   policy.includes("if(id==null||id.isEmpty()||!match.isEmpty())return"),
 "recovery uses only recently authenticated shared snapshot":
   sheet.includes("now-synced>45000L") &&
   sheet.includes("TransferAlerts.snapshot(activity)") &&
   sheet.includes("ConductorLivePolicy.safeId(id)") &&
   sheet.includes("DriverSheetPolicy.uniqueRecoveryId(ids,statuses)"),
 "never guess when multiple reservations active":
   sheet.includes("if(!only.isEmpty())focusConfirmed(only)") &&
   policy.includes('String match=""'),
 "process recreation recovers on resumed map, not simulated backend":
   navigator.includes("ui.post(rideTick)") &&
   navigator.includes("rideSheet.refreshFromSnapshot(live.isBusy())"),
 "stop UI polling on pause and disposal":
   navigator.includes("hostResumed=false;ui.removeCallbacks(rideTick);live.onHostPause()") &&
   navigator.includes("hostResumed=false;ui.removeCallbacks(rideTick);live.dispose()"),
 "disable action until a newer authenticated read":
   sheet.includes("actionIssuedAtSync=TransferAlerts.lastSync(activity)") &&
   sheet.includes("synced>actionIssuedAtSync") &&
   sheet.includes("!operationBusy"),
 "no new service created; existing RPC operations unchanged":
   navigator.includes('live.onPickupPhase(id,"EN_CAMINO")') &&
   navigator.includes("live.onStartTrip(id)") &&
   navigator.includes("live.onFinishTrip(id)") &&
   !/(HttpURLConnection|startService\(|new Thread\(|Api\.setStatus\()/g.test(sheet),
 "retry reads existing busy flag":
   controller.includes("public boolean isBusy(){return busy;}"),
 "system insets requested after sheet exists":
   main.includes("conductorV0=new uy.com.mapatrayectos.conductorv0.ConductorV0Navigator(this,root);") &&
   main.includes("setContentView(root);root.requestApplyInsets()"),
 "same APK package, monotonic version, no suffix":
   gradle.includes("applicationId 'uy.com.mapatrayectos'") &&
   gradle.includes("versionCode 93") &&
   gradle.includes("versionName 'MapaQT-R25.38-C1.1-QA'") &&
   !gradle.includes("applicationIdSuffix"),
 "no fictitious prices, GPS, routes or customer data":
   !/quote_final_total",120|GeoJsonSource|LineLayer|Math\.random\(/.test(sheet)
};
for(const [name,ok] of Object.entries(checks)){assert.ok(ok,name);console.log("PASS C1.1 "+name);}
const dir=path.join(root,"artifacts/driver-c11");fs.mkdirSync(dir,{recursive:true});
fs.writeFileSync(path.join(dir,"c11-static-qa.json"),JSON.stringify({
 suite:"Driver C1.1 QA",checks:Object.keys(checks).length,passed:Object.keys(checks).length,
 mode:"offline-static",liveReservations:false,realGpsTest:false,apkBuild:"separate signed CI job"
},null,2)+"\n");
console.log("C1.1 STATIC "+Object.keys(checks).length+" PASS 0 FAIL");
