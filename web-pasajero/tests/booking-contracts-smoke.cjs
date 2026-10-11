"use strict";
// Contrato EXACTO de carga útil de reservas: no se crea ninguna reserva real.
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const root=path.join(__dirname,"..");
const moduleSrc=fs.readFileSync(path.join(root,"booking-contracts.js"),"utf8");
const appSrc=fs.readFileSync(path.join(root,"app.js"),"utf8");
const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
const sw=fs.readFileSync(path.join(root,"sw.js"),"utf8");
const env={window:{}};
vm.runInNewContext(moduleSrc,env,{filename:"booking-contracts.js"});
const api=env.window.TrasladosBookingContracts;
assert(Object.isFrozen(api),"Los contratos no deben cambiar desde UI");
assert(html.indexOf("booking-contracts.js")<html.indexOf('./app.js?v='),"Load contracts before app");
assert(sw.includes("booking-contracts.js"),"Offline assets include contract code");
assert(appSrc.includes('window.TrasladosBookingContracts.checkAvailability(session.token,p)'));
assert(appSrc.includes('window.TrasladosBookingContracts.registeredReservation(session.token,p)'));
assert(appSrc.includes('window.TrasladosBookingContracts.guestReservation(p)'));
assert(appSrc.includes('if(record.stops.length&&!authReady())throw new Error('),
 "No presentar paradas al invitado que el backend no acepta");
const p={
 date:"2026-11-22",time:"15:40",passengers:2,comments:"Una valija",
 name:"Pasajero de prueba",phone:"099123456",
 origin:{text:"Origen verificado",lat:-34.817,lng:-56.05,department:"Canelones"},
 destination:{text:"Destino verificado",lat:-34.91,lng:-56.17,department:"Montevideo"},
 stops:[{text:"Parada verificada",lat:-34.86,lng:-56.1,department:"Montevideo"}]
};
const clean=x=>JSON.parse(JSON.stringify(x));
assert.deepEqual(clean(api.checkAvailability("test-session",p)),{
 p_session_token:"test-session",p_pickup_date:"2026-11-22",p_pickup_time:"15:40",
 p_trip_duration_min:null,p_origin_lat:-34.817,p_origin_lng:-56.05,
 p_destination_lat:-34.91,p_destination_lng:-56.17
});
assert.deepEqual(clean(api.registeredReservation("test-session",p)),{
 p_session_token:"test-session",p_pickup_date:"2026-11-22",p_pickup_time:"15:40",
 p_passengers:2,p_comments:"Una valija",
 p_origin_text:"Origen verificado",p_origin_lat:-34.817,p_origin_lng:-56.05,
 p_origin_department:"Canelones",
 p_destination_text:"Destino verificado",p_destination_lat:-34.91,p_destination_lng:-56.17,
 p_destination_department:"Montevideo",
 p_stops:[{position:1,address_text:"Parada verificada",lat:-34.86,lng:-56.1,department:"Montevideo"}],
 p_passenger_name:null,p_passenger_phone:null
});
const direct={...p,stops:[]};
assert.deepEqual(clean(api.guestReservation(direct)),{
 p_customer_name:"Pasajero de prueba",p_customer_phone:"099123456",
 p_pickup_date:"2026-11-22",p_pickup_time:"15:40",
 p_passengers:2,p_comments:"Una valija",
 p_origin_text:"Origen verificado",p_origin_lat:-34.817,p_origin_lng:-56.05,
 p_destination_text:"Destino verificado",p_destination_lat:-34.91,p_destination_lng:-56.17
});
assert.equal(api.guestReservation(direct).p_stops,undefined,
 "No inventar soporte del backend invitado");
assert.equal(api.registeredReservation("test-session",p).p_stops[0].position,1);
assert.equal(JSON.stringify(p).includes("p_session_token"),false,
 "No modificar los datos ni persistir tokens en el borrador");
console.log("PASS: same registered/guest booking payloads, availability check and stop rules");
