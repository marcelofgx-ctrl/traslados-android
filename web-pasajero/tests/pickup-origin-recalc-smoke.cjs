"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const root=path.resolve(__dirname,".."),read=n=>fs.readFileSync(path.join(root,n),"utf8");
const app=read("app.js"),html=read("index.html"),css=read("premium-route.css"),sw=read("sw.js");
const start=app.indexOf('let selectedPickupMode="schedule";'),end=app.indexOf("\nfunction setWhen(kind){",start);
assert(start>=0&&end>start,"Pickup code must be isolated for contract testing");
assert(html.includes('id="pickup-eta-retry"'));
assert(css.includes(".pickup-eta-retry"));
assert(sw.includes("premium-route.css"));
const nodes={
 "pickup-presence":{dataset:{},hidden:false,removeAttribute(){}},
 "pickup-existing-open":{hidden:true,firstChild:{textContent:""},dataset:{}},
 "booking-success":{hidden:true},"urgent-actions":{hidden:false},
 "pickup-use-location":{hidden:true},
 "pickup-presence-title":{textContent:""},
 "pickup-presence-subtitle":{textContent:""},
 "pickup-presence-metrics":{hidden:true},
 "pickup-distance":{textContent:""},"pickup-minutes":{textContent:""},
 "pickup-estimate-note":{textContent:"",hidden:true},
 "pickup-presence-meta":{textContent:""},"pickup-presence-status":{textContent:""},
 "pickup-eta-retry":{hidden:true,disabled:true,textContent:""}
};
const deferred=[];let pickupCalls=0,availabilityCalls=0;
const record={origin:{lat:-34.85,lng:-56.03,text:"Origen A"},destination:{lat:-34.91,lng:-56.18,text:"Destino A"},stops:[]};
const ctx={
 window:{TrasladosPickupCadence:{intervalMs:()=>45000,arrivalLabel:()=> "Hasta 5 min"},
   TrasladosETA:{
     availability:async()=>{availabilityCalls++;return {available:true,status:"available_for_requests",
       checkedAt:new Date().toISOString(),motionHint:"stationary"};},
     pickup:()=>{pickupCalls++;return new Promise((resolve,reject)=>deferred.push({resolve,reject}));}
   }},
 $:id=>nodes[id],PREVIEW_READ_ONLY:false,API:"https://example.invalid",KEY:"public-test",
 session:null,authReady:()=>false,tripRows:[],record,Date,Number,Map
};
vm.createContext(ctx);vm.runInContext(app.slice(start,end),ctx);
vm.runInContext('selectedPickupMode="now";',ctx);
const drain=async()=>{await Promise.resolve();await new Promise(setImmediate);};
(async()=>{
 assert.equal(ctx.recognizePickupOriginChange(),true,"first selected pickup");
 await ctx.refreshPickupPresence();assert.equal(pickupCalls,1);
 deferred[0].resolve({available:false,reason:"route_unavailable"});await drain();
 assert.match(nodes["pickup-presence-subtitle"].textContent,/No pudimos verificar/);
 assert.equal(nodes["pickup-eta-retry"].hidden,false,"failure permits visible retry control");
 assert.equal(nodes["pickup-eta-retry"].disabled,true,"cannot evade 45-second cadence");
 assert.equal(ctx.recognizePickupOriginChange(),false,"unchanged origin must preserve ETA cadence");
 record.destination={lat:-34.90,lng:-56.17,text:"Destino B"};
 assert.equal(ctx.recognizePickupOriginChange(),false,"destination change does not modify driver ETA");
 record.origin={lat:-34.84,lng:-56.01,text:"Origen B"};
 assert.equal(ctx.recognizePickupOriginChange(),true,"moving pickup invalidates old route");
 assert.match(nodes["pickup-presence-title"].textContent,/Nuevo origen/);
 assert.equal(nodes["pickup-eta-retry"].hidden,true,"old route failure must disappear");
 assert.equal(nodes["pickup-presence-metrics"].hidden,true,"never show stale km");
 await ctx.refreshPickupPresence();assert.equal(pickupCalls,2,"changed origin must issue new ETA despite old cadence");
 assert.equal(availabilityCalls,1,"reuse recent availability; no extra server poll");
 deferred[1].resolve({available:true,distanceKm:2,etaMin:5});await drain();
 assert.equal(nodes["pickup-presence"].dataset.phase,"ready");
 assert.equal(nodes["pickup-distance"].textContent,"2 km");
 assert.equal(nodes["pickup-eta-retry"].hidden,true);
 const oldAttempts=pickupCalls;
 await ctx.refreshPickupPresence();
 assert.equal(pickupCalls,oldAttempts,"unchanged origin must respect cadence");
 console.log("PASS PICKUP-RECALC-001: origin-specific refresh, destination independence, honest errors, rate-safe retry");
})().catch(error=>{console.error(error);process.exitCode=1;});
