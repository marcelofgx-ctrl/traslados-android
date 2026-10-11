"use strict";
const {readFileSync}=require("node:fs");
const {createContext,runInContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const js=readFileSync(path.join(__dirname,"..","app.js"),"utf8");
const etaSource=readFileSync(path.join(__dirname,"..","eta-engine.js"),"utf8");
const begin=js.indexOf('let selectedPickupMode="schedule";');
const end=js.indexOf("\nfunction setWhen(kind){",begin);
assert(begin>=0&&end>begin,"Public live availability routine must exist");
const fragment=js.slice(begin,end);
assert(etaSource.includes('public_driver_availability_v1')&&fragment.includes('window.TrasladosETA.availability'),
  "Public Mapa state must use the isolated network client");
assert(fragment.indexOf('const driver=await publicDriverAvailability')<fragment.indexOf('const signedIn=authReady()'),
  "Public status must be checked BEFORE customer login");
assert(fragment.includes('estimateType==="coarse"'),"Anonymous ETA must be labeled coarse");
assert(fragment.includes('checkedAt'),"Availability message must identify last server verification");
assert(js.includes('},15000);'),"Refresh driver availability every 15 seconds while visible");
assert(fragment.includes('driver.motionHint==="moving"?30000:60000'),"Adapt ETA to movement reported by Mapa");
assert(!fragment.includes('driver.speed'),"Never expose a driver's raw speed");
assert(!fragment.includes('driverLat')&&!fragment.includes('driverLng'),
  "No raw driver coordinates may be used by the passenger");
assert(fragment.includes('sessionToken:signedIn?session.token:null')&&etaSource.includes('sessionToken'),
  "Signed-in ETA must use its own session identity");

const host={hidden:false,dataset:{},removeAttribute(k){if(k==="data-presence")delete this.dataset.presence;}};
const pickupLocationButton={hidden:true};
const els={
  "pickup-presence":host,"pickup-use-location":pickupLocationButton,"pickup-presence-whatsapp":{},
  "urgent-whatsapp":{},"pickup-presence-status":{textContent:""},
  "pickup-presence-title":{textContent:""},
  "pickup-presence-subtitle":{textContent:""},
  "pickup-presence-metrics":{hidden:true},
  "pickup-distance":{textContent:""},
  "pickup-minutes":{textContent:""},
  "pickup-estimate-note":{textContent:"",hidden:true},
  "pickup-presence-meta":{textContent:""}
};
let published=true,moving=false,limitedEta=false,publicCalls=0,guestEtaCalls=0,customerEtaCalls=0;
const ctx={
  window:{},
  $:id=>els[id],API:"https://example.invalid",KEY:"publishable-test-key",
  session:null,record:{origin:{lat:-34.83,lng:-56.02,text:"Origen verificado"},
    destination:{lat:-34.90,lng:-56.04,text:"Destino verificado"}},
  authReady:()=>Boolean(ctx.session?.token),
  encodeURIComponent,Date,Number,Map,AbortController,
  setTimeout,clearTimeout,
  fetch:async(url,opts)=>{
    if(url.includes("public_driver_availability_v1")){
      publicCalls++;
      return{ok:true,json:async()=>({available:published,
        status:published?"available_for_requests":"not_available",checkedAt:new Date().toISOString(),
        motionHint:moving?"moving":"stationary",confirmationRequired:true})};
    }
    if(url.includes("pickup-eta")){
      const payload=JSON.parse(opts.body);
      if(payload.sessionToken)customerEtaCalls++;else guestEtaCalls++;
      if(limitedEta&&!payload.sessionToken)return{ok:false,status:429,json:async()=>({available:false,reason:"rate_limited"})};
      return{ok:true,json:async()=>({available:true,distanceKm:3.5,etaMin:10,
        ...(payload.sessionToken?{}:{estimateType:"coarse"})})};
    }
    throw Error("Unexpected API");
  }
};
createContext(ctx);
runInContext(etaSource,ctx,{filename:"eta-engine.js"});
runInContext(fragment,ctx);
runInContext('selectedPickupMode="now";',ctx);
async function settled(){await Promise.resolve();await new Promise(setImmediate);}
(async()=>{
  await ctx.refreshPickupPresence(true);await settled();
  assert(host.hidden===false);
  assert.equal(pickupLocationButton.hidden,true,"Selected origin hides location CTA");
  assert.equal(guestEtaCalls,1,"Guest should get a privacy-coarsened ETA without login");
  assert.equal(customerEtaCalls,0,"No customer session required for guest");
  assert.match(els["pickup-presence-status"].textContent,/3,5 km/);
  assert.match(els["pickup-presence-status"].textContent,/Distancia y tiempo aproximados/);
  assert.match(els["pickup-presence-status"].textContent,/Conductor disponible para recogerte/);
  assert.match(els["pickup-presence-status"].textContent,/Disponibilidad verificada hace/);
  assert.equal(els["pickup-presence-metrics"].hidden,false);
  assert.equal(els["pickup-distance"].textContent,"3,5 km");
  assert.equal(els["pickup-minutes"].textContent,"10 min");
  assert(!/servidor|rastreo|límite/i.test(els["pickup-presence-status"].textContent));
  assert(!/Iniciá sesión en Mi cuenta para consultar la llegada aproximada/.test(
    els["pickup-presence-status"].textContent));
  assert.equal(host.dataset.presence,"online");
  assert(publicCalls>=1);
  // Alternar «Ahora» / «En 10 min» no debe llamar de nuevo al
  // geocodificador de llegada ni consumir el presupuesto anti-rastreo.
  runInContext('selectedPickupMode="10";',ctx);
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(guestEtaCalls,1,"Same origin and timing tabs must share a cached ETA");
  assert.match(els["pickup-presence-status"].textContent,/Estimación obtenida/);
  assert.equal(els["pickup-presence-metrics"].hidden,false);
  // Detenido: antes de 60 segundos, no se solicita otra ruta.
  runInContext("lastPickupEtaAt=Date.now()-31000;",ctx);
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(guestEtaCalls,1,"Stopped vehicle must wait 60 seconds for next route");
  // Al recibir movimiento reciente, el período baja a 30 segundos.
  moving=true;
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(guestEtaCalls,2,"Moving vehicle can update after 30 seconds");
  assert.match(els["pickup-presence-status"].textContent,/3,5 km/);
  // La siguiente actualización en menos de 30 segundos reutiliza la ruta.
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(guestEtaCalls,2,"Moving ETA must not recalculate on every presence poll");
  // Simulamos una cuota agotada con una estimación anterior aún utilizable.
  limitedEta=true;
  runInContext("lastPickupEtaAt=Date.now()-101000;lastVerifiedEtaAt=Date.now()-65000;",ctx);
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(guestEtaCalls,3);
  assert.match(els["pickup-presence-status"].textContent,/Última estimación/);
  assert.match(els["pickup-presence-status"].textContent,/Estamos actualizando tu tiempo de llegada/);
  assert.match(els["pickup-presence-status"].textContent,/Disponibilidad verificada hace/);
  assert(!/servidor|límite|rastreo|cuota/i.test(els["pickup-presence-status"].textContent));
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(guestEtaCalls,3,"Do not hammer pickup ETA again during local cooldown");
  runInContext("lastPickupEtaAt=Date.now()-101000;",ctx);
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(guestEtaCalls,3,"Server rate limit triggers 90-second conservative backoff");

  published=false;
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(host.dataset.presence,"offline");
  assert.match(els["pickup-presence-status"].textContent,/no disponible/i);
  assert.match(els["pickup-presence-status"].textContent,/Disponibilidad verificada hace/);
  assert(!/3,5 km/.test(els["pickup-presence-status"].textContent),"Remove stale ETA after trip begins");
  assert.equal(els["pickup-presence-metrics"].hidden,true,"No ETA when driver becomes busy");
  assert.equal(guestEtaCalls,3);
  assert.equal(customerEtaCalls,0);

  published=true;
  ctx.session={token:"signed-in-customer-session-example"};
  await ctx.refreshPickupPresence(true);await settled();
  assert.equal(customerEtaCalls,1,"Signed-in customer uses private ETA");
  assert.equal(guestEtaCalls,3,"Guest public request never borrows a customer session");
  assert.match(els["pickup-presence-status"].textContent,/3,5 km/);
  assert.match(els["pickup-presence-status"].textContent,/10 min/);

  runInContext('selectedPickupMode="schedule";',ctx);
  await ctx.refreshPickupPresence();await settled();
  assert.equal(host.hidden,true,"No current-location claim for future bookings");
  console.log("PASS Mapa consent/agenda state, premium passenger metrics, anonymous ETA, privacy throttle, no GPS leakage");
})().catch(e=>{console.error(e);process.exitCode=1;});
