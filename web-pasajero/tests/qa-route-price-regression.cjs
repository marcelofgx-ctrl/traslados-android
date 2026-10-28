"use strict";
// Regression for our exact QA Vercel origin, official public hostname,
// road-only routing and the *real* price RPC contract. No backend writes.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.resolve(__dirname,"..");
const code=fs.readFileSync(path.join(root,"route-engine.js"),"utf8");
const config=JSON.parse(fs.readFileSync(path.join(root,"vercel.json"),"utf8"));
const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
const app=fs.readFileSync(path.join(root,"app.js"),"utf8");
assert.deepEqual(config.rewrites,[{
 source:"/api/testing-road",
 destination:"https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate"
}],"Keep only a fixed read-only routing service; no open proxy");
assert(!html.includes('data-qt-contract="rc1"'),
  "Test must catch an accidental non-existent HTML gate");
const points=[{lat:-34.83696,lng:-56.01638},{lat:-34.827469,lng:-55.969099}];
async function run(host,prefix){
 const asked=[];
 const win={location:new URL(host),document:{documentElement:{dataset:{}}}};
 const context={window:win,fetch:()=>{throw Error("Provide request mock");},URL,Map,
  Number,Date,AbortController,setTimeout,clearTimeout,encodeURIComponent};
 vm.runInNewContext(code,context,{filename:"route-engine.js"});
 const result=await win.TrasladosRoutes.estimate(points,{api:"https://zetaudvvutlouiqxopvg.supabase.co",
  publishableKey:"public-test",request:async (url,opts)=>{
   asked.push(String(url));
   if(String(url).includes("/rpc/public_reference_quote_v1")){
     assert.equal(opts.method,"GET");
     assert(String(url).includes("p_distance_km=6.6"));
     return {ok:true,json:async()=>({available:true,currency:"UYU",
       referenceFareUyu:264,rateUyuPerKm:40})};
   }
   assert(String(url).startsWith(prefix),String(url));
   return {ok:true,json:async()=>({available:true,source:"openrouteservice",
     distanceKm:6.6,durationMin:12,geometry:[
       [-34.83696,-56.01638],[-34.83,-55.99],[-34.827469,-55.969099]
     ]})};
  }});
 assert.equal(result.distanceKm,6.6);
 assert.equal(result.durationMin,12);
 assert.equal(result.referenceFareUyu,264,"Fare must be from authorized quote RPC");
 assert(asked[0].startsWith(prefix));
 assert.equal(asked.length,2,"Expected exactly route + quote, no other calls");
 assert.equal(win.TrasladosRoutes.verified(result),true);
 return asked;
}
(async()=>{
 const hosts=[
  ["https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app",
   "https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app/api/testing-road"],
  ["https://quicktrip-live-qa-only-20261027-lowceqwh0-marcelofgx-1927.vercel.app",
   "https://quicktrip-live-qa-only-20261027-lowceqwh0-marcelofgx-1927.vercel.app/api/testing-road"],
  ["https://marcelofgx-ctrl.github.io",
   "https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate"]
 ];
 for(const [host,prefix] of hosts)await run(host,prefix);
 assert(app.includes('renderRouteSummary()'));
 assert(app.includes('referenceFareUyu'));
 assert(app.includes('route-metric-fare'));
 console.log("PASS: Stable and hashed QA Preview route and UYU quote, public origin unchanged; original booking UI preserved");
})().catch(e=>{console.error(e);process.exitCode=1});
