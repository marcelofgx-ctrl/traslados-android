"use strict";
// Quick Trip V7: tests synthetic status transitions only; NO live bookings or GPS.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.resolve(__dirname,".."),read=name=>fs.readFileSync(path.join(root,name),"utf8");
const source=read("quicktrip-clean.js");
const css=read("quicktrip-clean.css");
const html=read("index.html"),sw=read("sw.js");
assert(html.includes("quicktrip-clean.js?v=5")&&html.includes("quicktrip-clean.css?v=6"));
assert(sw.includes("./quicktrip-clean.js")&&sw.includes("./quicktrip-clean.css"));
assert(!/\bfetch\s*\(|XMLHttpRequest|\.rpc\s*\(/.test(source),
  "Pure presentation must not introduce a second HTTP or reservation engine");
assert(source.includes("win.TrasladosReservationLive.mount"),
  "Accepted pickup must use the existing GPS-authorized tracking module");
assert(css.includes('data-ride-stage="tracking"')&&css.includes(".qtc-owned-tracking"),
  "Only accepted tracking substitutes its map for the planned-trip map");

// Exercise the real view-model function with synthetic authorized rows and a
// test-only mount stub. This does NOT query Supabase or invent GPS values.
const start=source.indexOf("  function syncOwnedJourney(driver,instant){");
const end=source.indexOf("  // Presentación únicamente:",start);
assert(start>0&&end>start,"Scoped passenger state controller must exist");
const fragment=source.slice(start,end);
const title={textContent:""},description={textContent:""},details={hidden:true};
const panel={hidden:true,dataset:{}};
const tracking={hidden:true,clears:0,replaceChildren(){this.clears++;}};
const driver={dataset:{},querySelector:sel=>sel===".qtc-owned-journey"?panel:
  sel===".qtc-owned-tracking"?tracking:null};
const shell={dataset:{}};
let mounts=0,rpcCalls=0;
const ctx={
  win:{TrasladosOwnedPickup:{match(rows,origin,dest){
    return rows.find(row=>["PENDIENTE","ACEPTADA"].includes(row.status)&&
      row.origin_text===origin.text&&row.destination_text===dest.text)||null;
  }},TrasladosReservationLive:{mount(){mounts++;}},TrasladosPremiumMap:{}},
  record:{origin:{text:"Aeropuerto"},destination:{text:"Centro"},stops:[]},
  tripRows:[],shell,
  ownedRouteKey:"",ownedCode:"",mountedTrackingKey:"",
  authReady:()=>true,session:{token:"synthetic-auth-token"},
  rpc:()=>{rpcCalls++;throw Error("Unexpected RPC");},roadEngine:{},API:"synthetic",KEY:"synthetic",
  byId:id=>({"qtc-owned-title":title,"qtc-owned-description":description,
    "qtc-owned-details":details})[id]||null
};
vm.createContext(ctx);
vm.runInContext(fragment+"\nthis.syncOwnedJourney=syncOwnedJourney;",ctx);
const row={id:17,code:"QT-123",status:"PENDIENTE",pickup_phase:null,
  origin_text:"Aeropuerto",destination_text:"Centro"};
ctx.tripRows=[row];
assert.equal(ctx.syncOwnedJourney(driver,true),true);
assert.match(title.textContent,/Esperando respuesta/);
assert.equal(mounts,0,"Pending driver must not be represented as moving");
assert.equal(tracking.hidden,true);
assert.equal(shell.dataset.rideStage,"none");
row.status="ACEPTADA";ctx.tripRows=[row];
assert.equal(ctx.syncOwnedJourney(driver,true),true);
assert.match(title.textContent,/Viaje aceptado/);
assert.equal(mounts,1,"Confirmed request mounts existing authorized tracker");
assert.equal(tracking.hidden,false);
assert.equal(shell.dataset.rideStage,"tracking");
ctx.syncOwnedJourney(driver,true);
assert.equal(mounts,1,"Same reservation/phase must NOT mount duplicate GPS pollers");
row.pickup_phase="EN_CAMINO";ctx.syncOwnedJourney(driver,true);
assert.match(title.textContent,/Conductor en camino/);
assert.equal(mounts,2,"Pickup-phase change updates the authoritative stage");
row.status="RECHAZADA";ctx.tripRows=[row];
assert.equal(ctx.syncOwnedJourney(driver,true),true);
assert.match(title.textContent,/rechazada/i);
assert.equal(tracking.hidden,true,"Rejected request cannot continue private GPS");
assert.equal(shell.dataset.rideStage,"none");
assert.equal(details.hidden,false);
ctx.record.origin.text="Otro origen";
assert.equal(ctx.syncOwnedJourney(driver,true),false,"Changing route clears tracked request");
assert.equal(panel.hidden,true);
assert.equal(rpcCalls,0,"Pure presentation must never issue backend RPC");
console.log("PASS Quick Trip V7: pending, accepted, moving, rejected, route switch and one authorized tracker");
