"use strict";
// Quick Trip QA regression gate: all browser backend responses synthetic.
// Simulated denied transport is not an actual CORS preflight; manual mode sends OPTIONS only.
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const {spawn}=require("node:child_process");
const root=path.resolve(__dirname,".."),repo=path.resolve(root,"..");
const contract=JSON.parse(fs.readFileSync(path.join(repo,"docs/quicktrip-qa-approved-origin.json"),"utf8"));
const out=process.env.QA_DIR||"quicktrip-v90-pilot-evidence/driver-contract";
fs.mkdirSync(out,{recursive:true});
function staticChecks(){
  const u=new URL(contract.qaOrigin);
  assert.equal(u.protocol,"https:");
  assert.equal(u.origin,contract.qaOrigin);
  assert.equal(u.hostname,"quicktrip-v86-qa-samsung-20261025-git-qa-05b826-marcelofgx-1927.vercel.app");
  assert.equal(contract.deploymentClass,"preview");
  assert.equal(contract.noProductionNoWrites,true);
  assert.equal(contract.liveDriverGpsVerified,false);
  const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
  const app=fs.readFileSync(path.join(root,"app.js"),"utf8");
  const eta=fs.readFileSync(path.join(root,"eta-engine.js"),"utf8");
  assert(html.includes('name="traslados-preview" content="read-only"'));
  assert(html.includes("form-action 'none'"));
  const csp=html.match(/connect-src\s+([^;]+);/)?.[1]||"";
  for(const endpoint of [contract.backendAvailabilityEndpoint,contract.backendReadOnlyEndpoint])
    assert(csp.includes("https://"+contract.backendProject+".supabase.co"+endpoint));
  assert(app.includes("function refreshPickupPresence(")&&app.includes("function paintPickupCard("));
  assert(eta.includes("public_driver_availability_v1")&&eta.includes("functions/v1/pickup-eta"));
  assert(eta.includes('method:"POST",mode:"cors",cache:"no-store"'));
  assert(fs.readFileSync(path.join(root,"quicktrip-qa-stage-b-v90.js"),"utf8").includes("LOCAL-QA"));
  return {source:"PASS",qaOrigin:contract.qaOrigin,realBackendUsed:false,liveGpsNotVerified:true};
}
async function preflight(){
  const candidate=new URL(process.env.QA_URL||"https://unapproved.invalid");
  assert.equal(candidate.origin,contract.qaOrigin,
    "RELEASE BLOCKED: deployment URL not in actual backend CORS allowlist. Use approved QA alias.");
  const url="https://"+contract.backendProject+".supabase.co"+contract.backendReadOnlyEndpoint;
  const r=await fetch(url,{method:"OPTIONS",headers:{"Origin":contract.qaOrigin,
    "Access-Control-Request-Method":"POST",
    "Access-Control-Request-Headers":"apikey,content-type"},
    signal:AbortSignal.timeout(12000)});
  const allowed=r.headers.get("access-control-allow-origin")||"";
  const methods=r.headers.get("access-control-allow-methods")||"";
  const names=r.headers.get("access-control-allow-headers")||"";
  assert(r.ok&&allowed===contract.qaOrigin&&methods.toUpperCase().includes("POST")&&
    names.toLowerCase().includes("apikey")&&names.toLowerCase().includes("content-type"),
    "RELEASE BLOCKED: live CORS refused "+JSON.stringify({http:r.status,origin:allowed,methods,names}));
  return {http:r.status,originAllowed:allowed,liveCorsPreflight:"PASS",requests:"one OPTIONS only; zero POST; ETA values not verified"};
}
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function browserChecks(){
 const {chromium}=require("playwright-core");
 const port=8204,base="http://127.0.0.1:"+port+"/web-pasajero/";
 const server=spawn("python3",["-m","http.server",String(port),"--bind","127.0.0.1"],{cwd:repo,stdio:"ignore"});
 let browser;const cases=[];
 try{
  let ready=false;
  for(let i=0;i<60;i++){try{if((await fetch(base+"index.html")).ok){ready=true;break}}catch{}await sleep(150);}
  assert(ready,"Local QA page unavailable");
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/chromium",
    args:["--no-sandbox","--disable-dev-shm-usage","--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1"]});
  for(const scenario of ["eta-ok","unavailable","eta-403","cors-blocked"]){
   const counts={availability:0,eta:0,blockedTransport:0,unexpected:[],errors:[]};
   const ctx=await browser.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,
     serviceWorkers:"block",reducedMotion:"reduce"});
   const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Methods":"POST,OPTIONS",
     "Access-Control-Allow-Headers":"apikey,content-type","Content-Type":"application/json"};
   await ctx.route("**/*",route=>{
    const u=new URL(route.request().url()),method=route.request().method();
    if(u.hostname==="127.0.0.1"&&u.port===String(port))return route.continue();
    if(u.hostname==="tile.openstreetmap.org")return route.fulfill({status:200,contentType:"image/png",
      body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
    if(u.hostname==="router.project-osrm.org"){
     if(method==="OPTIONS")return route.fulfill({status:204,headers:cors});
     const ps=decodeURIComponent(u.pathname).split("/").pop().split(";").map(v=>v.split(",").map(Number));
     return route.fulfill({status:200,headers:cors,body:JSON.stringify({code:"Ok",
      routes:[{distance:8600,duration:875,geometry:{type:"LineString",coordinates:[ps[0],ps.at(-1)]}}],
      waypoints:ps.map(p=>({location:p,name:"QA",distance:0}))})});
    }
    if(u.hostname===contract.backendProject+".supabase.co"&&
      [contract.backendAvailabilityEndpoint,contract.backendReadOnlyEndpoint].includes(u.pathname)){
     if(method==="OPTIONS"){
      return route.fulfill({status:204,headers:cors});
     }
     if(method!=="POST"){counts.unexpected.push(method);return route.abort();}
     if(u.pathname===contract.backendAvailabilityEndpoint){
      counts.availability++;const available=scenario!=="unavailable";
      return route.fulfill({status:200,headers:cors,body:JSON.stringify({
       available,status:available?"available_for_requests":"not_available",
       checkedAt:new Date().toISOString(),motionHint:"stationary"})});
     }
     counts.eta++;
     if(scenario==="cors-blocked"){
       counts.blockedTransport++;return route.abort("blockedbyclient");
     }
     return route.fulfill({status:scenario==="eta-403"?403:200,headers:cors,
      body:JSON.stringify(scenario==="eta-403"?{available:false,reason:"forbidden"}:
       {available:true,distanceKm:3.5,etaMin:10,estimateType:"coarse"})});
    }
    counts.unexpected.push({host:u.hostname,path:u.pathname,method});return route.abort("blockedbyclient");
   });
   const page=await ctx.newPage();page.on("pageerror",e=>counts.errors.push(e.message));
   await page.goto(base+"index.html",{waitUntil:"load",timeout:22000});
   await page.locator('#view-inicio [data-service="now"]').click();
   await page.locator('.qtc-app[data-qt-v90="sandbox"]').waitFor({state:"visible",timeout:15000});
   await page.evaluate(()=>{
    originPicker.persist({text:"Origen de prueba",lat:-34.85,lng:-56.05,department:"Canelones",source:"manual"});
    destPicker.persist({text:"Destino de prueba",lat:-34.90,lng:-56.04,department:"Canelones",source:"manual"});
   });
   await page.waitForFunction(kind=>{
    const phase=document.querySelector("#pickup-presence")?.dataset.phase;
    return kind==="eta-ok"?phase==="ready":kind==="unavailable"?phase==="blocked":phase==="issue";
   },scenario,{timeout:17000}).catch(async err=>{
    const trace=await page.evaluate(()=>({
      phase:document.querySelector("#pickup-presence")?.dataset.phase,
      title:document.querySelector("#pickup-presence-title")?.textContent,
      subtitle:document.querySelector("#pickup-presence-subtitle")?.textContent,
      mode:typeof selectedPickupMode==="string"?selectedPickupMode:null,
      origin:typeof record!=="undefined"?record.origin?.text:null,
      etaState:typeof lastEtaFailureMessage!=="undefined"?lastEtaFailureMessage:null,
      cardVisible:document.querySelector("#pickup-presence")?.hidden===false
    }));
    throw Error("Driver contract wait "+scenario+": "+JSON.stringify({trace,counts,cause:err.message}));
   });
   const v=await page.evaluate(()=>({
    phase:document.querySelector("#pickup-presence")?.dataset.phase,
    label:document.querySelector("#pickup-presence-title")?.textContent,
    hidden:document.querySelector("#pickup-presence-metrics")?.hidden,
    km:document.querySelector("#pickup-distance")?.textContent,
    mins:document.querySelector("#pickup-minutes")?.textContent,
    sendDisabled:document.querySelector("#urgent-send")?.disabled,
    origin:record.origin?.text
   }));
   if(scenario==="eta-ok"){
    assert.equal(v.phase,"ready");assert.equal(v.hidden,false);
    assert.equal(v.km,"3,5 km");assert.equal(v.mins,"5–10 min");
    assert.match(v.label,/Conductor disponible/);assert(counts.eta>0&&counts.availability>0);
   }else{
    assert.equal(v.hidden,true,"No fake driver distance or ETA on failure");
    assert.equal(v.km,"");
    if(scenario==="unavailable"){assert.equal(v.phase,"blocked");assert.equal(counts.eta,0);}
    else{assert.equal(v.phase,"issue");assert.match(v.label,/sin estimar/);}
    if(scenario==="cors-blocked")assert(counts.blockedTransport>0,"Simulated denied transport must be tested");
   }
   assert(v.sendDisabled,"Never enable actual booking in QA");
   assert.equal(v.origin,"Origen de prueba");
   assert.deepEqual(counts.unexpected,[]);
   assert.deepEqual(counts.errors,[]);
   const shot="driver-"+scenario+".png";
   await page.screenshot({path:path.join(out,shot),fullPage:false,animations:"disabled"});
   cases.push({scenario,result:"PASS",phase:v.phase,km:v.km,mins:v.mins,
     availabilityMockCalls:counts.availability,etaMockCalls:counts.eta,
     preflightRejectMocks:counts.blockedTransport,shot});
   await ctx.close();
  }
  return {browser:"PASS",cases,realBackendRequests:0};
 }finally{if(browser)await browser.close();server.kill("SIGTERM");}
}
(async()=>{
 if(process.argv.includes("--live-preflight")){console.log("PASS QA origin preflight "+JSON.stringify(await preflight()));return;}
 const report=staticChecks();
 if(process.argv.includes("--browser"))Object.assign(report,await browserChecks());
 fs.writeFileSync(path.join(out,"DRIVER_QA_CONTRACT.json"),JSON.stringify(report,null,2)+"\n");
 console.log("PASS Quick Trip driver consolidation "+(report.browser||report.source));
})().catch(e=>{console.error("FAIL DRIVER QA GATE "+(e.stack||e));process.exitCode=1});
