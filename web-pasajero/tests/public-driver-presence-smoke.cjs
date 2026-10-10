"use strict";
const {readFileSync}=require("node:fs");
const {createContext,runInContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const js=readFileSync(path.join(__dirname,"..","app.js"),"utf8");
const begin=js.indexOf('let selectedPickupMode="schedule";');
const end=js.indexOf("\nfunction setWhen(kind){",begin);
assert(begin>=0&&end>begin,"Public live availability routine must exist");
const fragment=js.slice(begin,end);
assert(fragment.includes('public_driver_availability_v1'),"Must read the Mapa status without login");
assert(fragment.indexOf('const driver=await publicDriverAvailability')<fragment.indexOf('if(!authReady())'),
  "Public status must be checked BEFORE customer login");
assert(!fragment.includes('driverLat')&&!fragment.includes('driverLng'),
  "No raw driver coordinates may be used by the passenger");
assert(fragment.includes('sessionToken:session.token'),"Private ETA still requires a real customer session");

const host={hidden:false,dataset:{},removeAttribute(k){if(k==="data-presence")delete this.dataset.presence;}};
const els={
  "pickup-presence":host,"pickup-presence-whatsapp":{},
  "urgent-whatsapp":{},"pickup-presence-status":{textContent:""}
};
let published=true,publicCalls=0,privateCalls=0;
const ctx={
  $:id=>els[id],API:"https://example.invalid",KEY:"publishable-test-key",
  session:null,record:{origin:{lat:-34.83,lng:-56.02,text:"Origen verificado"},
    destination:{lat:-34.90,lng:-56.04,text:"Destino verificado"}},
  authReady:()=>Boolean(ctx.session?.token),
  encodeURIComponent,Date,Number,Map,AbortController,
  setTimeout,clearTimeout,
  fetch:async(url)=>{
    if(url.includes("public_driver_availability_v1")){
      publicCalls++;
      return{ok:true,json:async()=>({available:published,
        status:published?"available_for_requests":"not_available",confirmationRequired:true})};
    }
    if(url.includes("pickup-eta")){
      privateCalls++;
      return{ok:true,json:async()=>({available:true,distanceKm:3.5,etaMin:10})};
    }
    throw Error("Unexpected API");
  }
};
createContext(ctx);
runInContext(fragment,ctx);
runInContext('selectedPickupMode="now";',ctx);
async function settled(){await Promise.resolve();await new Promise(setImmediate);}
(async()=>{
  await ctx.refreshPickupPresence(true);await settled();
  assert(host.hidden===false);
  assert.equal(privateCalls,0,"No private ETA for anonymous visitor");
  assert.match(els["pickup-presence-status"].textContent,/Conductor disponible para consultas/);
  assert(!/Iniciá sesión en Mi cuenta para consultar la llegada aproximada/.test(
    els["pickup-presence-status"].textContent));
  assert.equal(host.dataset.presence,"online");
  assert(publicCalls>=1);

  published=false;
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(host.dataset.presence,"offline");
  assert.match(els["pickup-presence-status"].textContent,/no disponible/i);
  assert.equal(privateCalls,0);

  published=true;
  ctx.session={token:"signed-in-customer-session-example"};
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(privateCalls,1,"Only signed-in customers may request origin-based ETA");
  assert.match(els["pickup-presence-status"].textContent,/3,5 km/);
  assert.match(els["pickup-presence-status"].textContent,/10 min/);

  runInContext('selectedPickupMode="schedule";',ctx);
  await ctx.refreshPickupPresence();await settled();
  assert.equal(host.hidden,true,"No current-location claim for future bookings");
  console.log("PASS public availability from Mapa without login; secure ETA remains session-gated");
})().catch(e=>{console.error(e);process.exitCode=1;});
