"use strict";
// Professional visual hierarchy contract + true reactive data on the folded route.
// Tests real JS helper functions without network, reservations or personal data.
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const root=path.resolve(__dirname,"..");
const app=fs.readFileSync(path.join(root,"app.js"),"utf8");
const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
const css=fs.readFileSync(path.join(root,"styles.css"),"utf8");
const sw=fs.readFileSync(path.join(root,"sw.js"),"utf8");
for(const id of ["route-folded-preview","route-folded-origin","route-folded-destination",
    "route-folded-km","route-folded-minutes","route-folded-fare","route-folded-stops",
    "urgent-route-recap","scheduled-route-recap"]){
  assert(html.includes('id="'+id+'"'),"Missing data slot "+id);
}
assert.equal((html.match(/data-final-route-distance/g)||[]).length,2);
assert.equal((html.match(/data-final-route-duration/g)||[]).length,2);
assert.equal((html.match(/data-final-route-fare/g)||[]).length,2);
assert(css.includes(".route-folded-address")&&css.includes("overflow-wrap:break-word"),
    "Full addresses must wrap instead of ellipsis");
assert(css.includes("grid-template-columns:repeat(3,minmax(0,1fr))"),
    "Three metrics must fit on a narrow screen");
assert(css.includes(".request-hero")&&css.includes(".request-recap-metrics"),
    "Premium final action card must preserve full quote context");
assert(html.includes('styles.css?v=76')&&html.includes('app.js?v=76'));
assert(sw.includes('traslados-cliente-pwa-v76'),"Service worker must refresh v57 assets");
const begin=app.indexOf("function refreshJourneyComposition(");
const end=app.indexOf("\nfunction suggestBookingStep(",begin);
assert(begin>=0&&end>begin,"Production helpers are missing");

const make=()=>({textContent:"",hidden:false,value:""});
const els=Object.fromEntries([
  "route-folded-preview","route-folded-origin","route-folded-destination",
  "route-folded-km","route-folded-minutes","route-folded-fare","route-folded-stops",
  "urgent-route-recap","scheduled-route-recap","passengers","pickup-date",
  "pickup-time","person-name","person-phone"
].map(x=>[x,make()]));
for(const id of ["urgent-route-recap","scheduled-route-recap"]){
  const children=Object.fromEntries(["distance","duration","fare"].map(k=>[k,make()]));
  els[id].querySelector=(sel)=>{
    const key=sel.startsWith("[data-final-route-")?sel.slice("[data-final-route-".length,-1):null;
    if(!key)throw new Error("Unexpected selector "+sel);
    return children[key];
  };
  els[id].children=children;
}
els.passengers.value="1";
els["person-name"].value="Ana Rodríguez";els["person-phone"].value="099123456";
const record={
  origin:{text:"Arenal Grande 2274",lat:-34.9,lng:-56.18},
  destination:{text:"Aeropuerto Internacional de Carrasco · Terminal de pasajeros",lat:-34.837,lng:-56.016},
  stops:[]
};
const routeKeyOf=points=>points.map(x=>x.lat+","+x.lng).join(";");
const key=routeKeyOf([record.origin,record.destination]);
const out=[];
const ctx={
  Number,Date,
  $:id=>els[id]||null,
  record,routeKeyOf,stepSummaryName:place=>String(place?.text||"").split(",")[0],
  latestRoad:{key,data:{available:true,source:"openrouteservice",
    distanceKm:19.8,durationMin:29,referenceFareUyu:790}},
  bookingSteps:{summary:(...args)=>out.push(args)},
  authReady:()=>false,
  selectedPickupMode:"now",
  window:{TrasladosRoutes:{verified:d=>d?.available===true}},
  session:null
};
vm.runInNewContext(app.slice(begin,end),ctx,{filename:"app-journey.js"});
ctx.refreshBookingSteps();
assert.equal(els["route-folded-preview"].hidden,false);
assert.equal(els["route-folded-origin"].textContent,"Arenal Grande 2274");
assert.equal(els["route-folded-destination"].textContent,
  "Aeropuerto Internacional de Carrasco · Terminal de pasajeros");
assert.equal(els["route-folded-km"].textContent,"19,8 km");
assert.equal(els["route-folded-minutes"].textContent,"29 min");
assert.equal(els["route-folded-fare"].textContent,"$ 790");
assert.equal(els["urgent-route-recap"].children.distance.textContent,"19,8 km");
assert.equal(els["scheduled-route-recap"].children.duration.textContent,"29 min");
assert.equal(els["urgent-route-recap"].children.fare.textContent,"$ 790");
assert.equal(out[0][2],true,"Route complete is independent of external service");
assert.equal(els["route-folded-stops"].hidden,true);

record.stops.push({text:"Parada intermedia",lat:-34.86,lng:-56.09});
ctx.refreshBookingSteps();
assert.equal(els["route-folded-stops"].textContent,"1 parada intermedia");
assert.equal(els["route-folded-stops"].hidden,false);
assert.equal(els["route-folded-km"].textContent,"—","Quote must be invalidated when an intermediate stop changes");
assert.equal(els["urgent-route-recap"].children.fare.textContent,"A confirmar");
assert.equal(els["scheduled-route-recap"].children.distance.textContent,"—");
record.stops.length=0;record.destination=null;
ctx.refreshBookingSteps();
assert.equal(els["route-folded-preview"].hidden,true);
assert.equal(els["urgent-route-recap"].hidden,true);
assert.equal(els["scheduled-route-recap"].hidden,true);
console.log("PASS PWA v57: full itinerary, three real metrics, stale-quote safety and both booking modes");