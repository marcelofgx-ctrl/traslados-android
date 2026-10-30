"use strict";
// QA-only source integrity gate: real routing, GPS access and prices are unchanged.
const fs=require("node:fs"),assert=require("node:assert/strict");
const read=x=>fs.readFileSync(x,"utf8");
const m=read("web-pasajero/quicktrip-map-focus.js");
const g=read("web-pasajero/reservation-live.js");
const app=read("web-pasajero/app.js");
let n=0;
const test=(title,yes)=>{assert.ok(yes,title);console.log("PASS TRACKING",title);n++};
test("Contract metrics only originate after successful VERIFIED road render",
 g.includes('if(kind==="contract")contractRoute=result;')&&
 g.includes('if(!renderer.render(mapHost,dual?contractRoute.geometry:result.geometry')&&
 g.includes('if(Number.isFinite(km)&&km>0&&Number.isFinite(mins)&&mins>=0)')&&
 g.includes('notify({stage:"contract",gps:false,tripMetrics:true,'));
test("All live pickup data must still come from private authenticated GPS",
 g.includes('rpc("customer_reservation_position_v1"')&&
 g.includes('if(!pos||!Number.isFinite(fresh)||fresh<0||fresh>75)')&&
 g.includes('await paint([pos,destination],"driver")'));
test("Booked trip and live pickup use different explicit labels",
 m.includes('"DISTANCIA DEL VIAJE"')&&m.includes('"DURACIÓN DEL VIAJE"')&&
 m.includes('"A RECOGIDA"')&&m.includes('"LLEGADA ESTIMADA"')&&
 m.includes('"AL DESTINO"')&&m.includes('"TIEMPO AL DESTINO"'));
test("GPS missing must remove stale pickup values without faking a vehicle",
 m.includes('if(bookedMetrics)presentMetrics(bookedMetrics,"contract");')&&
 m.includes('screen.dataset.metricsSource="pending"')&&
 g.includes('clearDriverRoute();'));
test("Out of order contracted route cannot override already live GPS leg",
 m.includes('screen.dataset.metricsSource!=="live"'));
test("No extra polling or separate geolocation requests in UI adapter",
 !/getCurrentPosition|watchPosition|new WebSocket|setInterval\s*\(/.test(m)&&
 g.includes('},4000)')&&
 app.includes('window.TrasladosETA.pickup({'));
test("Original fixed fare source preserved",
 m.includes('Number(r.quote_final_total)>0')&&
 m.includes('UYU(Number(r.quote_final_total))'));
fs.mkdirSync("artifacts/quicktrip-tracking-metrics",{recursive:true});
fs.writeFileSync("artifacts/quicktrip-tracking-metrics/contract.json",
 JSON.stringify({checks:n,passed:n,liveDriverGpsVerified:false,realBookingsCreated:false,source:"QA-only"},null,2)+"\n");
console.log("TRACKING-METRICS SOURCE CONTRACT",n,"PASS");
