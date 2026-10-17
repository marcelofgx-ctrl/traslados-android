"use strict";
/* PWA v67: no duplicate ETA during slow Edge calls; keep valid delayed result,
 * readable no-route state, and discard stale origin/session results. No network. */
const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const root=path.join(__dirname,".."),read=n=>fs.readFileSync(path.join(root,n),"utf8");
const app=read("app.js"),html=read("index.html"),css=read("styles.css"),eta=read("eta-engine.js"),sw=read("sw.js");
assert(html.includes("app.js?v=81")&&html.includes("styles.css?v=81"));
assert(sw.includes("traslados-pwa-v81"));
assert(eta.includes("setTimeout(()=>ctrl.abort(),18000)"));
assert(!html.includes('id="pickup-route-caption"'));
assert(!app.includes("Animación ilustrativa · No es seguimiento en vivo"));
assert(css.includes('[data-phase="waiting"] .pickup-route-car'));
assert(css.includes("pickupRouteTravel 3.7s"));
const begin=app.indexOf('let selectedPickupMode="schedule";'),end=app.indexOf("\nfunction setWhen(kind){",begin);
assert(begin>=0&&end>begin);
const nodes={
 "pickup-presence":{dataset:{},hidden:false,removeAttribute(k){delete this.dataset[k.replace(/^data-/,"")];}},
 "pickup-existing-open":{hidden:true,firstChild:{textContent:""},dataset:{}},
 "booking-success":{hidden:true},"urgent-actions":{hidden:false},
 "pickup-use-location":{hidden:true},"pickup-presence-title":{textContent:""},
 "pickup-presence-subtitle":{textContent:""},"pickup-presence-metrics":{hidden:true},
 "pickup-distance":{textContent:""},"pickup-minutes":{textContent:""},
 "pickup-estimate-note":{textContent:"",hidden:true},
 "pickup-presence-meta":{textContent:""},"pickup-presence-status":{textContent:""}
};
let pickupCalls=0;const pending=[];
const ctx={PREVIEW_READ_ONLY:false,
 window:{TrasladosETA:{
  availability:async()=>({available:true,status:"available_for_requests",checkedAt:new Date().toISOString(),motionHint:"stationary"}),
  pickup:()=>{pickupCalls++;return new Promise((resolve,reject)=>pending.push({resolve,reject}));}
 },TrasladosPickupCadence:{intervalMs:()=>45000,arrivalLabel:min=>min+" min"}},
 $:id=>nodes[id],API:"https://example.invalid",KEY:"publishable",
 authReady:()=>false,session:null,tripRows:[],Number,Date,Map,
 record:{origin:{text:"Origen 1",lat:-34.85,lng:-56.03},destination:{text:"Destino"},stops:[]}
};
vm.createContext(ctx);vm.runInContext(app.slice(begin,end),ctx);
vm.runInContext('selectedPickupMode="now";',ctx);
async function tick(){await Promise.resolve();await new Promise(setImmediate);}
(async()=>{
 await ctx.refreshPickupPresence(true);
 assert.equal(pickupCalls,1);
 assert.equal(nodes["pickup-presence"].dataset.phase,"routing");
 assert.match(nodes["pickup-presence-title"].textContent,/calculando llegada/i,
  "A positive presence must not claim the driver is ready for pickup without routed ETA");
 assert.notEqual(nodes["pickup-presence-title"].textContent,"Conductor disponible para recogerte");
 assert.equal(nodes["pickup-presence-metrics"].hidden,true);
 await ctx.refreshPickupPresence(true);
 await ctx.refreshPickupPresence(true);
 assert.equal(pickupCalls,1,"Repeated 15s presence polls must not send duplicate slow ETA");
 pending[0].resolve({available:true,distanceKm:4.5,etaMin:15});
 await tick();
 assert.equal(nodes["pickup-presence"].dataset.phase,"ready",
  "Delayed successful ETA must survive newer presence polls");
 assert.equal(nodes["pickup-distance"].textContent,"4,5 km");
 assert.equal(nodes["pickup-minutes"].textContent,"15 min");
 vm.runInContext("lastPickupEtaAt=Date.now()-46000;lastVerifiedEtaAt=Date.now()-360000;",ctx);
 await ctx.refreshPickupPresence(true);
 assert.equal(pickupCalls,2);
 pending[1].resolve({available:false,reason:"route_unavailable"});
 await tick();
 assert.equal(nodes["pickup-presence"].dataset.presence,"eta-unavailable",
  "A route provider failure must be a separate degraded state, never ready");
 assert.match(nodes["pickup-presence-title"].textContent,/sin estimar/i);
 assert.match(nodes["pickup-presence-subtitle"].textContent,/No pudimos verificar la ruta/i);
 assert.equal(nodes["pickup-presence-metrics"].hidden,true);
 await ctx.refreshPickupPresence(true);
 assert.equal(pickupCalls,2,"Respect cooldown after missing real route");
 assert.match(nodes["pickup-presence-subtitle"].textContent,/No pudimos verificar la ruta/i,
  "Do not revert to an endless spinner on the next availability poll");
 vm.runInContext("lastPickupEtaAt=Date.now()-46000;",ctx);
 await ctx.refreshPickupPresence(true);
 assert.equal(pickupCalls,3);
 pending[2].reject(new Error("timeout from bounded network"));
 await tick();
 assert.match(nodes["pickup-presence-subtitle"].textContent,/No pudimos calcular la llegada/);
 await ctx.refreshPickupPresence(true);
 assert.match(nodes["pickup-presence-subtitle"].textContent,/No pudimos calcular la llegada/);
 assert.equal(pickupCalls,3);
 vm.runInContext("lastPickupEtaAt=Date.now()-46000;",ctx);
 await ctx.refreshPickupPresence(true);
 assert.equal(pickupCalls,4);
 ctx.record.origin={text:"Origen 2",lat:-34.86,lng:-56.04};
 await ctx.refreshPickupPresence(true);
 assert.equal(pickupCalls,5,"New origin may obtain its own distinct protected ETA");
 pending[3].resolve({available:true,distanceKm:99,etaMin:120});
 await tick();
 assert.notEqual(nodes["pickup-distance"].textContent,"99 km","Old origin must not replace current route");
 pending[4].resolve({available:true,distanceKm:2,etaMin:5});
 await tick();
 assert.equal(nodes["pickup-distance"].textContent,"2 km");
 assert.equal(nodes["pickup-presence"].dataset.phase,"ready");
 vm.runInContext('selectedPickupMode="schedule";',ctx);
 await ctx.refreshPickupPresence();
 assert.equal(nodes["pickup-presence"].hidden,true);
 console.log("PASS v67: ETA delay dedup, accepted slow results, honest failures, no stale GPS or extra requests");
})().catch(e=>{console.error(e);process.exitCode=1;});