"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const root=path.join(__dirname,"..");
const source=fs.readFileSync(path.join(root,"route-engine.js"),"utf8");
const config=JSON.parse(fs.readFileSync(path.join(root,"vercel.json"),"utf8"));
const origin="https://quicktrip-live-qa-only-20261027-jfz3kqn98-marcelofgx-1927.vercel.app";
assert.deepEqual(config.rewrites,[{
 source:"/api/testing-road",
 destination:"https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate"
}],"Do not expose an arbitrary open proxy");
const points=[{lat:-34.835,lng:-56.03},{lat:-34.90,lng:-56.18}];
async function check(urlRoot,contract,expectPrefix){
 const reqs=[];
 const window={
  location:new URL(urlRoot),
  document:{documentElement:{dataset:{qtContract:contract}}}
 };
 const context={window,setTimeout,clearTimeout,AbortController,URL};
 vm.runInNewContext(source,context,{filename:"route-engine.js"});
 const data=await window.TrasladosRoutes.estimate(points,{
  request:async url=>{
   reqs.push(url);
   return {ok:true, json:async()=>({
    available:true,distanceKm:17.4,durationMin:26,
    source:"openrouteservice",geometry:[[-34.835,-56.03],[-34.90,-56.18]]
   })};
  }
 });
 assert.equal(data.distanceKm,17.4);
 assert(reqs[0].startsWith(expectPrefix),JSON.stringify(reqs));
 assert(reqs[0].includes("points="),"Route coordinates query lost");
}
(async()=>{
 await check(origin,"rc1",origin+"/api/testing-road");
 await check("https://marcelofgx-ctrl.github.io","rc1",
  "https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate");
 await check(origin,"not-qa",
  "https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate");
 console.log("PASS: QA same-origin route rewrite; production engine unchanged; no open proxy");
})().catch(e=>{console.error(e);process.exitCode=1});
