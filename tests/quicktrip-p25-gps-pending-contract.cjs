"use strict";
// P2.5 UX-only: do not invent the driver, location, green route or ETA.
const fs=require("node:fs"),assert=require("node:assert/strict");
const live=fs.readFileSync("web-pasajero/reservation-live.js","utf8");
const focus=fs.readFileSync("web-pasajero/quicktrip-map-focus.js","utf8");
const html=fs.readFileSync("web-pasajero/index.html","utf8");
const sw=fs.readFileSync("web-pasajero/sw.js","utf8");
const ok=(label,result)=>{assert.ok(result,label);console.log("PASS P25",label)};
ok("True live GPS always requires private accepted reservation RPC",
 live.includes('rpc("customer_reservation_position_v1"')&&
 live.includes('const pos=value?.available?coordinates(value.driverLat,value.driverLng):null;')&&
 live.includes('if(!pos||!Number.isFinite(fresh)||fresh<0||fresh>75)'));
ok("No fake green line, GPS or ETA while still pending",
 live.includes('notify({stage:ride?"en_viaje":"recogida",gps:false,')&&
 live.includes("Sin GPS reciente no se calculan distancia ni llegada del conductor.")&&
 live.includes('void paint([from,to],"contract")'));
ok("Safe allowlisted GPS reason labels, never raw RPC payloads",
 live.includes("function gpsPendingLabel(reason)")&&
 live.includes('no_unique_active_reservation')&&
 live.includes('not_authorized')&&live.includes('gpsPendingLabel(value?.reason)'));
ok("Contract-only road failures do not claim GPS or erase its state",
 live.includes('notify({stage:"contract_pending",routePending:true});')&&
 focus.includes('if(data.routePending===true&&data.stage==="contract_pending")')&&
 focus.includes('if(screen.dataset.metricsSource==="pending")'));
ok("Missing GPS is explicitly distinguished from driver pickup metrics",
 focus.includes('"GPS pendiente · esperando conductor"')&&
 focus.includes('setMetricLabels(onboard?"AL DESTINO":"A RECOGIDA"')&&
 focus.includes('onboard?"TIEMPO AL DESTINO":"LLEGADA ESTIMADA"'));
ok("Existing polling cadence and private GPS system unchanged",
 live.includes('},4000)')&&
 live.includes('if(updating||!visible())return;')&&
 !/navigator\.geolocation|watchPosition|new WebSocket/.test(live));
ok("PWA shell loads same old map design and new diagnostic scripts",
 html.includes('quicktrip-p24-tracking-polish.css?v=p24-20261102')&&
 html.includes('reservation-live.js?v=p25-gps-explained-20261102')&&
 html.includes('quicktrip-map-focus.js?v=p25-gps-explained-20261102')&&
 sw.includes('traslados-pwa-quicktrip-p25-gps-explained-20261102'));
console.log("P2.5 diagnostic scope passed: physical driver permissions not verified.");
