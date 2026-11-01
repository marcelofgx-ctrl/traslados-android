"use strict";
// P2.0: source-only QA gate; no real GPS, reservations, RPC or Supabase writes.
const assert=require("node:assert/strict"),fs=require("node:fs");
const focus=fs.readFileSync("web-pasajero/quicktrip-map-focus.js","utf8");
const live=fs.readFileSync("web-pasajero/reservation-live.js","utf8");
const app=fs.readFileSync("web-pasajero/app.js","utf8");
const html=fs.readFileSync("web-pasajero/index.html","utf8");
const price=fs.readFileSync("web-pasajero/quicktrip-p18-fare-flow-icons.css","utf8");
function ok(name,result){assert.ok(result,name);console.log("PASS P20",name);}
ok("Only genuine EN_CAMINO / EN_PUNTO phase changes remount the authorized tracker",
 focus.includes('previous.pickup_phase!==r.pickup_phase')&&
 focus.includes('["EN_CAMINO","EN_PUNTO"].includes(String(r.pickup_phase))')&&
 focus.includes("return showTracking({...trackingCtx,reservation:r});"));
ok("No fake GPS READY label before authenticated road success",
 focus.includes('screen.dataset.gpsVerified="false"')&&
 focus.includes('screen.dataset.gpsVerified=data.gps===true?"true":"false"')&&
 focus.includes('" · GPS PENDIENTE"'));
ok("Same existing private RPC and 4 second cadence",
 live.includes('rpc("customer_reservation_position_v1"')&&
 live.includes('await paint([pos,destination],"driver")')&&live.includes('},4000)'));
ok("Pickup directs car toward origin; onboard toward destination",
 live.includes('const destination=ride?to:from;')&&
 live.includes('const ride=String(r.status)==="EN_VIAJE"'));
ok("No additional geo or network code in the presentation",
 !/navigator\.geolocation|watchPosition|new WebSocket|setInterval\s*\(|fetch\s*\(/.test(focus));
ok("Original route fare, backend, and base style contracts untouched",
 app.includes("window.TrasladosRoutes.verified(current)")&&
 price.includes("P1.9 seam fix")&&
 html.includes('quicktrip-map-focus.js?v=p20-pickup-activation-20261101'));
console.log("P20 source contract PASS, physical GPS still requires Samsung test");
