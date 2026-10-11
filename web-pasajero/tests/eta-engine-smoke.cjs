"use strict";
// Contratos HTTP públicos y privados sin navegador real, datos ni red externa.
const {readFileSync}=require("node:fs");
const {runInNewContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const src=readFileSync(path.join(__dirname,"..","eta-engine.js"),"utf8");
const scope={};
const calls=[];
let driver={available:true,status:"available_for_requests",motionHint:"moving",
  checkedAt:"2026-10-10T22:00:00Z",lat:-34.9,lng:-56.1,speed:30,device_id:"private"};
let eta={available:true,distanceKm:3.5,etaMin:10,estimateType:"coarse"};
const ctx={window:scope,AbortController,Number,setTimeout,clearTimeout,
  fetch:async(url,options)=>{
    calls.push({url:String(url),options});
    const pickup=String(url).includes("pickup-eta");
    return {ok:pickup||driver.available!==null,
      json:async()=>pickup?eta:driver};
  }
};
runInNewContext(src,ctx,{filename:"eta-engine.js"});
const engine=scope.TrasladosETA;
assert(Object.isFrozen(engine));
(async()=>{
  const publicState=await engine.availability({api:"https://example.invalid",publishableKey:"publishable"});
  assert.equal(publicState.available,true);
  assert.equal(publicState.motionHint,"moving");
  for(const field of ["lat","lng","speed","device_id"])assert.equal(field in publicState,false);
  assert.equal(calls[0].options.method,"POST");
  assert.equal(calls[0].options.body,"{}");
  assert.equal(calls[0].options.cache,"no-store");
  driver={available:true,status:"not_valid",checkedAt:"x"};
  assert.equal(await engine.availability({api:"https://example.invalid",publishableKey:"publishable"}),null);
  driver={available:false,status:"not_available",motionHint:"unknown"};
  const unavailable=await engine.availability({api:"https://example.invalid",publishableKey:"publishable"});
  assert.equal(unavailable.available,false);
  assert.equal(unavailable.motionHint,"stationary");

  await engine.pickup({api:"https://example.invalid",publishableKey:"publishable",
    originLat:-34.81,originLng:-56.03});
  let body=JSON.parse(calls.at(-1).options.body);
  assert.equal(body.originLat,-34.81);
  assert(!("sessionToken" in body),"Guests must not invent customer identity");
  assert(!("driverLat" in body)&&!("driverLng" in body),"Driver GPS never sent to passenger");

  await engine.pickup({api:"https://example.invalid",publishableKey:"publishable",
    originLat:-34.81,originLng:-56.03,sessionToken:"signed-in-example"});
  body=JSON.parse(calls.at(-1).options.body);
  assert.equal(body.sessionToken,"signed-in-example");
  assert.equal(calls.at(-1).options.cache,"no-store");

  eta={available:false,reason:"rate_limited"};
  const limited=await engine.pickup({api:"https://example.invalid",publishableKey:"publishable",
    originLat:-34.81,originLng:-56.03});
  assert.equal(limited.reason,"rate_limited","Preserve backend quota status so UI can back off");
  console.log("PASS isolated ETA: public privacy, guest vs session, strict state, rate-limit contract");
})().catch(e=>{console.error(e);process.exitCode=1;});
