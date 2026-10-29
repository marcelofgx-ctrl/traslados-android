"use strict";
const fs=require("node:fs"),crypto=require("node:crypto"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const locked={
 "web-pasajero/route-engine.js":"031232f14038d71fe3987897369135b6c972744c",
 "web-pasajero/eta-engine.js":"7ea575b66c05cf74c1c0f187aa6fd348641cd870",
 "web-pasajero/booking-contracts.js":"8fb535ad1742fe5ae72ce13dc2de33e48c5a4686",
 "web-pasajero/pickup-cadence.js":"33487be94d7b923503f4a7c79a2df3f7665d722b"
};
let problems=0;
const get=f=>fs.readFileSync(path.join(root,f));
for(const [file,expected] of Object.entries(locked)){
 const b=get(file);
 const actual=crypto.createHash("sha1").update("blob "+b.length+"\0").update(b).digest("hex");
 if(actual!==expected){
  console.error("QA F1 FAIL engine hash changed "+file+": "+actual);
  problems++;
 }else console.log("QA F1 PASS engine "+file);
}
const tests=[
 "web-pasajero/tests/route-engine-smoke.cjs",
 "web-pasajero/tests/fixed-fare-v1-smoke.cjs",
 "web-pasajero/tests/booking-contracts-smoke.cjs",
 "web-pasajero/tests/public-driver-presence-smoke.cjs",
 "web-pasajero/tests/reservation-live-v82-smoke.cjs",
 "web-pasajero/tests/tracking-fases-gps-real-smoke.cjs",
 "web-pasajero/tests/continuous-follow-audit-qa.cjs"
];
for(const f of tests)if(!fs.existsSync(path.join(root,f))){
 console.error("QA F1 FAIL missing test "+f);problems++;
}
const live=get("web-pasajero/reservation-live.js").toString("utf8");
const focus=get("web-pasajero/quicktrip-map-focus.js").toString("utf8");
const audit=get("web-pasajero/tests/continuous-follow-audit-qa.cjs").toString("utf8");
const contracts={
 "private GPS RPC":live.includes("customer_reservation_position_v1"),
 "GPS age":live.includes("gpsAgeSec"),
 "two-leg renderer":focus.includes("verifiedTwoLegRenderer"),
 "stage preservation":focus.includes("syncTrackingStage"),
 "no live booking writes":audit.includes("no-booking-writes")
};
for(const [k,ok] of Object.entries(contracts)){
 if(ok)console.log("QA F1 PASS "+k);
 else{console.error("QA F1 FAIL "+k);problems++;}
}
console.log("OPEN P0: no physical proof of private driver GPS or continuous map while onboard");
if(problems)process.exitCode=1;
