"use strict";
// Contratos del motor vial sin DOM, Android, internet ni proveedores reales.
const {readFileSync}=require("node:fs");
const {runInNewContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const source=readFileSync(path.join(__dirname,"..","route-engine.js"),"utf8");
const root={};
let now=100000,routeCalls=0,cacheCalls=0;
let primary={available:true,source:"openrouteservice",distanceKm:8,durationMin:14,referenceFareUyu:320};
let fallback={available:false};
const sandbox={
  window:root,Number,Map,encodeURIComponent,AbortController,setTimeout,clearTimeout,
  Date:{now:()=>now},
  fetch:async(url,init)=>{
    if(String(url).includes("public_reference_quote_v1")){
      assert.equal(init.method,"GET");assert.equal(init.credentials,"omit");
      return {ok:true,json:async()=>({available:true,currency:"UYU",referenceFareUyu:124,rateUyuPerKm:40})};
    }
    const cache=String(url).includes("public_cached_route_preview_v1");
    if(cache){
      cacheCalls++;
      assert.equal(init.method,"POST");
      assert.equal(init.cache,"no-store");
    }else{
      routeCalls++;
      assert.match(String(url),/route-estimate\?points=/);
    }
    const data=cache?fallback:primary;
    return {ok:data?.available===true,json:async()=>data};
  }
};
runInNewContext(source,sandbox,{filename:"route-engine.js"});
const routes=root.TrasladosRoutes;
const a={lat:-34.83,lng:-56.02},b={lat:-34.9,lng:-56.16};
const points=[a,b];
assert(Object.isFrozen(routes));
assert.equal(routes.key(points),"-56.020000,-34.830000;-56.160000,-34.900000");
assert.equal(routes.verified(primary),true);
assert.equal(routes.verified({...primary,distanceKm:0}),false);
assert.equal(routes.verified({...primary,source:"straight_line"}),false);
assert.equal(routes.verified({...primary,referenceFareUyu:0}),true,"Valid route may lack a quote");
assert.throws(()=>routes.key([{lat:NaN,lng:1},b]));
(async()=>{
  const first=await routes.estimate(points,{api:"https://example.invalid",publishableKey:"test-public"});
  assert.equal(first.distanceKm,8);
  assert.equal(routeCalls,1);assert.equal(cacheCalls,0);
  assert.equal((await routes.estimate(points,{api:"https://example.invalid",publishableKey:"test-public"})).distanceKm,8);
  assert.equal(routeCalls,1,"Same route must reuse short-lived cache");
  now+=300001;
  await routes.estimate(points,{api:"https://example.invalid",publishableKey:"test-public"});
  assert.equal(routeCalls,2,"Cached quote must expire after five minutes");

  routes.invalidate();
  primary={available:true,source:"straight_line",distanceKm:6,durationMin:9,referenceFareUyu:200};
  fallback={available:true,source:"supabase_route_cache",distanceKm:9.7,durationMin:18,referenceFareUyu:390};
  const safe=await routes.estimate(points,{api:"https://example.invalid",publishableKey:"test-public"});
  assert.equal(safe.source,"supabase_route_cache","Invalid provider source must fall back to verified ROAD");
  assert.equal(cacheCalls,1);
  routes.invalidate();
  fallback={available:true,source:"supabase_route_cache",distanceKm:-7,durationMin:18,referenceFareUyu:1};
  assert.equal(await routes.estimate(points,{api:"https://example.invalid",publishableKey:"test-public"}),null,
    "Missing verified road data must not invent km, minutes or price");
  assert.equal(routes.diagnostic(routes.key(points)).reason,"unverified_response",
    "Missing route must preserve safe diagnostic code without exposing addresses");
  routes.invalidate();
  assert.equal(routes.diagnostic(routes.key(points)),null,
    "Reattempt should clear old diagnostic state");

  routes.invalidate();
  primary={available:true,source:"openrouteservice",distanceKm:11,durationMin:21,referenceFareUyu:440};
  const other=[a,{lat:-34.84,lng:-56.07},b];
  const distinct=await routes.estimate(other,{api:"https://example.invalid",publishableKey:"test-public"});
  assert.equal(distinct.distanceKm,11);
  assert.notEqual(routes.key(other),routes.key(points),"Stops are part of the identity");
  console.log("PASS shared route engine: road-only, fare cache TTL, alternate provider, invalid results, stops");
})().catch(error=>{console.error(error);process.exitCode=1;});
