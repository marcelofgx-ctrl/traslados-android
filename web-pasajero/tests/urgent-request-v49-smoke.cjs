"use strict";
// Contract + client request flow. All RPCs mocked; never writes production data.
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const dir=path.join(__dirname,"..");
const src=fs.readFileSync(path.join(dir,"app.js"),"utf8");
const html=fs.readFileSync(path.join(dir,"index.html"),"utf8");
const sw=fs.readFileSync(path.join(dir,"sw.js"),"utf8");
const contractsSrc=fs.readFileSync(path.join(dir,"booking-contracts.js"),"utf8");
const window={};
vm.runInNewContext(contractsSrc,{window});
const contracts=window.TrasladosBookingContracts;
const p={
 date:null,time:null,pickupMode:"now",passengers:2,comments:"Equipaje",
 name:"Cliente de prueba",phone:"099123456",
 origin:{text:"Origen verificado",lat:-34.82,lng:-56.08,department:"Montevideo"},
 destination:{text:"Destino verificado",lat:-34.86,lng:-56.05,department:"Canelones"},
 stops:[]
};
const uuid="0e9238d3-5150-4d12-9a32-839fc99370b9";
const guest=contracts.immediatePickup(null,p,uuid);
assert.equal(guest.p_request_id,uuid);
assert.equal(guest.p_mode,"now");
assert.equal(guest.p_customer_name,"Cliente de prueba");
assert.equal(guest.p_customer_phone,"099123456");
assert.equal(guest.p_session_token,null);
assert.equal(guest.p_pickup_date,undefined,"Server controls immediate date");
assert.equal(guest.p_pickup_time,undefined,"Server controls immediate time");
assert.deepEqual(JSON.parse(JSON.stringify(guest.p_stops)),[]);
const logged=contracts.immediatePickup("secret-only-in-runtime",{...p,pickupMode:"10",stops:[{
 text:"Parada",lat:-34.84,lng:-56.09,department:"Montevideo"
}]},uuid);
assert.equal(logged.p_mode,"10");
assert.equal(logged.p_customer_name,null,"Backend chooses account identity");
assert.equal(logged.p_customer_phone,null,"No spoofing name/phone with account");
assert.equal(logged.p_session_token,"secret-only-in-runtime");
assert.equal(logged.p_stops[0].position,1);
assert.throws(()=>contracts.immediatePickup(null,{...p,pickupMode:"schedule"},uuid),/inválida/);
assert(html.includes('id="urgent-send"'),"Actual submit CTA");
assert(!html.includes('href="https://wa.me/'),"WhatsApp removed from public booking interface");
assert(!html.includes('id="urgent-switch-scheduled"'),"Urgent no longer reroutes to programming");
assert(html.includes('app.js?v=66')&&html.includes('styles.css?v=66'));
assert(sw.includes('traslados-cliente-pwa-v66'));
assert(src.includes('rpc("customer_request_pickup_v1"'),"New backend RPC connected");
assert(src.includes("window.TrasladosBookingContracts.immediatePickup"));
assert(src.includes('renderBookingSuccess(result,p.pickupMode)'),"Real receipt");
assert(src.includes('if(!result?.code)throw'),"No false success without server code");
assert(src.includes('const signature=JSON.stringify(p)'),"Idempotent retry fingerprint");
assert(src.includes('urgentAttempt={signature,id:newClientRequestId()}'));
assert(src.includes('if(result.public_token)saveGuest(result)'),"Guest reservation token stored");
assert(src.includes('if(scheduled&&(!date||!time))'),"Schedule validates date");
assert(src.includes('const date=scheduled?$("pickup-date").value:null'),"Urgent never sends browser-selected time");
assert(src.includes('selectedPickupMode=kind'),"Mode is preserved");
assert(src.includes('setWhen("schedule")'),"Programar remains accessible independently");
assert(src.includes('window.TrasladosETA.availability'),"Live driver ETA remains");
assert(src.includes('window.TrasladosBookingContracts.registeredReservation'),"Existing scheduled flow intact");
console.log("PASS PWA v49: immediate RPC, backend clock, both modes, authenticated/guest, safe retries, no WhatsApp");
