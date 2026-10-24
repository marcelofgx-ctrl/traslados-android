"use strict";
/* Actual Chromium Service Worker + CacheStorage. Locally served official sw.js.
 * No app loaded, no external requests, no Supabase auth or mutations.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const dir=process.env.QA_DIR||path.resolve("quicktrip-v85-edge-qa");
fs.mkdirSync(dir,{recursive:true});
const base="http://127.0.0.1:8127/web-pasajero/";
async function run(){
 const server=spawn("python3",["-m","http.server","8127","--bind","127.0.0.1"],{
  cwd:process.cwd(),stdio:"ignore"
 });
 let browser;const report={status:"STARTED",runtime:"Chromium actual SW and CacheStorage",
  remoteRequests:0,productionWrites:0,checks:[],findings:[]};
 try{
  for(let i=0;i<50;i++){
    try{if((await fetch(base+"offline.html")).ok)break;}catch{}
    if(i===49)throw Error("Local HTTP server unavailable");
    await new Promise(ok=>setTimeout(ok,150));
  }
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage",
    "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1"]
  });
  const ctx=await browser.newContext({serviceWorkers:"allow"});
  const page=await ctx.newPage();const external=[];
  page.on("request",r=>{const u=new URL(r.url());if(u.hostname!=="127.0.0.1")external.push(r.url());});
  await page.goto(base+"offline.html",{waitUntil:"load"});
  const init=await page.evaluate(async()=>{
   const registration=await navigator.serviceWorker.register("./sw.js",{scope:"./"});
   await navigator.serviceWorker.ready;
   const keys=await caches.keys();
   return {scope:registration.scope,keys};
  });
  await page.waitForFunction(()=>Boolean(navigator.serviceWorker.controller),null,{timeout:15000});
  const current=init.keys.find(k=>k.includes("tu-viaje-compacto-v85"));
  assert(current,"Actual SW didn't install v85 cache: "+JSON.stringify(init));
  report.cacheVersion=current;
  report.checks.push({name:"Original SW registered under local PWA scope",status:"PASS"});

  const seeded=await page.evaluate(async(name)=>{
    const cache=await caches.open(name);
    const first=await cache.match("./quicktrip-clean.css");
    if(!first)throw new Error("Original CSS absent from actual precache");
    const latest=new Request(new URL("./quicktrip-clean.css?v=85",location.href));
    await cache.put(latest,new Response("QA_LAST_GOOD_CSS_v85",{
      status:200,headers:{"Content-Type":"text/css"}
    }));
    return {precachePresent:Boolean(first),keys:(await cache.keys()).filter(r=>r.url.includes("quicktrip-clean.css")).map(r=>r.url)};
  },current);
  assert(seeded.precachePresent);
  report.checks.push({name:"Real Chromium stores unversioned precache + updated CSS query",status:"PASS",...seeded});

  await ctx.setOffline(true);
  let offlineResult;
  try{
   offlineResult=await page.evaluate(async()=>{
     const r=await fetch("./quicktrip-clean.css?v=86");
     return {status:r.status,body:(await r.text()).slice(0,500),
       fromSW:Boolean(navigator.serviceWorker.controller)};
   });
  }catch(error){offlineResult={error:String(error)};}
  const latestWasServed=offlineResult.body==="QA_LAST_GOOD_CSS_v85";
  const match={name:"Actual Chromium offline fallback chooses latest CSS",status:latestWasServed?"PASS":"DEFECT",
     observed:latestWasServed?"Last good query cache":"Unversioned precache OR fetch failure",
     responseStatus:offlineResult.status??null,
     fromServiceWorker:offlineResult.fromSW??false,
     textPrefix:offlineResult.body?.slice(0,95)||null,error:offlineResult.error||null,
     actualCacheKeys:seeded.keys};
  report.checks.push(match);
  if(!latestWasServed)report.findings.push(match);
  assert.deepEqual(external,[],"Attempted remote traffic: "+JSON.stringify(external));
  report.remoteRequests=0;
  report.status="FINISHED";
  fs.writeFileSync(path.join(dir,"CHROMIUM-CACHE-REAL-v85.json"),
    JSON.stringify(report,null,2)+"\n");
  console.log("REAL_CHROMIUM_CACHE "+JSON.stringify(report,null,2));
  await ctx.close();
 }catch(e){
  report.status="HARNESS_FAILURE";report.failure=String(e.stack||e);
  fs.writeFileSync(path.join(dir,"CHROMIUM-CACHE-REAL-v85.json"),
    JSON.stringify(report,null,2)+"\n");
  throw e;
 }finally{if(browser)await browser.close();server.kill("SIGTERM");}
}
run().catch(e=>{console.error("FAIL REAL CHROMIUM SW",e);process.exitCode=1});
