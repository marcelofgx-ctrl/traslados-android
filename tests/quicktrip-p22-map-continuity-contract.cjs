"use strict";
// P2.2 QA-only: guarantee a nonblank endpoint map while the verified
// booking road and private GPS leg load, without drawing a fictitious vehicle.
const fs=require("node:fs"),assert=require("node:assert/strict");
const src=fs.readFileSync("web-pasajero/reservation-live.js","utf8");
const html=fs.readFileSync("web-pasajero/index.html","utf8");
const sw=fs.readFileSync("web-pasajero/sw.js","utf8");
const focus=fs.readFileSync("web-pasajero/quicktrip-map-focus.js","utf8");
const check=(title,value)=>{assert.ok(value,title);console.log("PASS P22",title)};
check("Initial map shows only two verified reservation endpoints before any async call",
  src.includes('function showEndpointFallback()')&&
  src.includes('renderer.overview(mapHost,[[from.lat,from.lng],[to.lat,to.lng]]')&&
  src.indexOf('showEndpointFallback();')<src.indexOf('async function run()'));
check("Clearing stale driver location restores endpoint map synchronously",
  /function clearDriverRoute\(\)\s*\{[\s\S]*?mapHost\.replaceChildren\(\);\s*[\s\S]*?showEndpointFallback\(\);/.test(src));
check("GPS remains private and fresh; fallback never claims a driver's coordinates",
  src.includes('rpc("customer_reservation_position_v1"')&&
  src.includes('if(!pos||!Number.isFinite(fresh)||fresh<0||fresh>75)')&&
  src.includes('await paint([pos,destination],"driver")')&&
  src.includes('if(host.dataset.liveDriver!=="true")return;'));
check("Real routing contract and single GPS timer unchanged",
  src.includes('verifiedRoute(points,engine,api,key)')&&
  src.includes('},4000)')&&
  !/navigator\.geolocation|watchPosition|new WebSocket/.test(src));
check("P2.0 phase changes remain in place",focus.includes('["EN_CAMINO","EN_PUNTO"].includes(String(r.pickup_phase))'));
check("Updated PWA asset references avoid old map script",html.includes("reservation-live.js?v=p22-map-continuity-20261101")&&sw.includes("traslados-pwa-quicktrip-p22-map-continuity-20261101"));
console.log("P22 nonblank map source checks passed. Physical Samsung GPS QA pending.");
