"use strict";
// Pure regression: no backend writes, no network, no PIN exposure.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,"..","web-pasajero"),source=n=>fs.readFileSync(path.join(root,n),"utf8");
const env={window:{},Date,Number,Math,Set,Map,Intl};
vm.runInNewContext(source("booking-contracts.js"),env);
vm.runInNewContext(source("trip-board.js"),env);
const c=env.window.TrasladosBookingContracts,board=env.window.TrasladosTripBoard;
const p={pickupMode:"now",passengers:2,name:"Pasajero",phone:"099123456",
 comments:"Valija",origin:{text:"De Madrid",lat:-34.82747,lng:-55.9691,department:"Canelones"},
 destination:{text:"Carrasco",lat:-34.83696,lng:-56.01638,department:"Canelones"},stops:[]};
const quote={available:true,source:"openrouteservice",distanceKm:6.4,durationMin:11,
 referenceRateUyuPerKm:40,referenceFareUyu:256};
const request=c.fixedFarePickup(null,p,"00000000-0000-4000-8000-000000000011",quote);
assert.equal(request.p_expected_rate,40);
assert.equal(request.p_expected_total,256);
assert.equal(request.p_route_distance_km,6.4);
assert.equal(request.p_mode,"now");
assert.equal(request.p_session_token,null);
assert.equal(request.p_customer_name,"Pasajero");
assert(!("p_pickup_date" in request),"Backend controls arrival date");
assert.throws(()=>c.fixedFarePickup(null,p,"x",{...quote,referenceFareUyu:260}),/precio cambió/i);
assert.throws(()=>c.fixedFarePickup(null,p,"x",{...quote,source:"haversine"}),/verificados/i);
assert.throws(()=>c.fixedFarePickup(null,p,"x",{...quote,referenceRateUyuPerKm:NaN}),/verificados/i);
assert.throws(()=>c.fixedFarePickup(null,{...p,pickupMode:"schedule"},"x",quote),/inválida/i);
const pending={status:"PENDIENTE",fare_mode:"fixed",quote_status:"PRECIO_FIJO",
 quote_final_total:256,pickup_mode:"now",created_at:new Date().toISOString()};
const first=board.metadata(pending);
assert.equal(first.tone,"pending");
assert.match(first.label,/conductor/i);
assert.match(board.guidance(pending),/no necesitás aprobar otro presupuesto/i);
assert.equal(board.metadata({...pending,status:"ACEPTADA",quote_status:"ACEPTADO"}).tone,"confirmed");
const oldQuote=board.metadata({...pending,fare_mode:"quote",quote_status:"ENVIADO"});
assert.equal(oldQuote.tone,"attention","Scheduled quote still requires passenger approval");
const app=source("app.js");
assert(app.includes('rpc("customer_request_pickup_fixed_v1"'));
assert(app.includes('customer_list_reservations_v16'));
assert(app.includes('get_reservation_by_token_v5'));
assert(!app.includes('rpc("customer_request_pickup_v1"'),"No legacy quote for Tu viaje");
// The historical conductor PIN UI was removed in the verified Android
// baseline. This passenger-only test keeps every current fixed fare,
// anti-spoof, RPC and scheduling assertion, without obsolete driver UI text.
console.log("PASS P1 fixed fare: verified road km × rate, amount, quote integrity, RPC, guest, status");
