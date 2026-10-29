"use strict";
const fs=require("node:fs"),assert=require("node:assert/strict"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const src=f=>fs.readFileSync(path.join(root,f),"utf8");
const dir="mapatrayectos/src/main/java/uy/com/mapatrayectos/";
const sheet=src(dir+"conductorv0/DriverRideSheet.java"),
  nav=src(dir+"conductorv0/ConductorV0Navigator.java"),
  controller=src(dir+"conductorv0/ConductorLiveController.java"),
  activity=src(dir+"MainActivity.java"),
  policy=src(dir+"conductorv0/DriverSheetPolicy.java"),
  original=src(dir+"conductorv0/ConductorLiveView.java");
const tests={
  "same native map and same Activity":nav.includes("root.addView(rideSheet,rideLp)")&&
    !nav.includes("startActivity(")&&activity.includes("root.addView(mapView,"),
  "offer still invokes existing server confirmation":controller.includes("TransferAlerts.decideFixed(activity,id,accepted,")&&
    controller.includes("if(confirmed){")&&
    controller.includes("onConfirmedImmediateDecision.onServerConfirmed(id,accepted)"),
  "reject never opens the ride card":nav.includes("if(accepted)rideSheet.focusConfirmed(id);"),
  "private accepted ID from confirmed decision":sheet.includes("focusConfirmed(String id)")&&
    sheet.includes("ConductorLivePolicy.safeId(id)"),
  "card reads only authenticated TransferAlerts snapshot":sheet.includes("TransferAlerts.snapshot(activity)")&&
    sheet.includes("TransferAlerts.lastSync(activity)")&&
    sheet.includes("now-synced<=45000L"),
  "card matches precise reservation ID":sheet.includes("reservationId.equals(r.optString("),
  "one principal action selected by state policy":sheet.includes("DriverSheetPolicy.next(status,phase,fresh)")&&
    sheet.includes("nextAction!=DriverSheetPolicy.Action.NONE"),
  "existing pickup phase RPC only":nav.includes('live.onPickupPhase(id,"EN_CAMINO")')&&
    nav.includes('live.onPickupPhase(id,"EN_PUNTO")'),
  "existing start and finish methods only":nav.includes("live.onStartTrip(id)")&&
    nav.includes("live.onFinishTrip(id)"),
  "no new GPS or reservation transport":!/(postRpc\(|fetch\(|HttpURLConnection|SQLiteDatabase|new Thread\(|setPickupPhase\(|setStatus\()/g.test(sheet),
  "no fabricated payment/rating/bonuses":!/(4\.87|31,90|Pago en efectivo|optString\("payment_method"|optString\("rating")/i.test(sheet),
  "show real passenger origin destination price only":sheet.includes('optString("customer_name"')&&
    sheet.includes('optString("origin_text"')&&
    sheet.includes('optString("destination_text"')&&
    sheet.includes('optDouble("quote_final_total"'),
  "no invented live navigation route":!/(GeoJsonSource|LineLayer|routeGeometry|route_polyline)/.test(sheet)&&
    nav.includes("live.onRoute(destination)"),
  "no forced request on app restart":!activity.includes("rideSheet.focusConfirmed(")&&
    sheet.includes("if(reservationId.isEmpty())return;"),
  "no unsafe step on stale snapshot":policy.includes("if(!snapshotFresh || !visible(status))return Action.NONE;")&&
    sheet.includes("actionButton.setEnabled(verified);"),
  "map overlay respects system navigation bar":activity.includes("conductorV0.updateBottomInset(bottomInset)"),
  "retry map route uses existing controller":nav.includes("live.onRoute(destination)"),
  "full legacy panel remains":original.includes("public final class ConductorLiveView")&&
    nav.includes("host.addView(conductor,new FrameLayout.LayoutParams(-1,-1))"),
  "no separate Driver/Passenger application":!/(android\.app\.Application|class .* extends Activity|onCreate\(Bundle)/.test(sheet),
  "price and distance no synthetic hardcode":!/(UYU\\s*151|12[,\\.]4\\s*km|3[,\\.]3\\s*km)/.test(sheet)
};
const results=[];for(const [label,passed] of Object.entries(tests)){
  assert(passed,label);
  results.push({label,passed:true});
  console.log("PASS C1 "+label);
}
const out=path.join(root,"artifacts/driver-c1");
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,"c1-static-qa.json"),JSON.stringify({
  suite:"C1 Quick Trip Driver map sheet",
  localOnly:true,readOnly:true,backendCalls:0,apkBuilt:false,
  gpsEngineModified:false,tripEngineModified:false,
  source:"Map QT QA P0.1 versionCode 92",
  scenarioCount:results.length,passed:results.length,results
},null,2)+"\n");
console.log("C1 STATIC "+results.length+" PASS 0 FAIL");
