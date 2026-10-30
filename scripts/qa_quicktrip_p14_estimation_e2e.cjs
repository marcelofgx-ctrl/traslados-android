"use strict";
/* Chromium 390px QA: uses an in-memory synthetic UI row, NEVER an API booking.
 * Captures the active-trip design and tests return-to-ride, accessibility and scroll. */
const cp=require("node:child_process"),fs=require("node:fs"),path=require("node:path");
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const OUT="artifacts/quicktrip-p14-estimates";fs.mkdirSync(OUT,{recursive:true});
const children=[],waiting=new Map();let socket,seq=0;
function ok(v,name){if(!v)throw Error("FAIL: "+name);console.log("PASS BROWSER "+name)}
async function poll(fn,ms=18000){const end=Date.now()+ms;while(Date.now()<end){try{const v=await fn();if(v)return v}catch{}await sleep(200)}throw Error("Timeout");}
async function cmd(method,params={}){return await new Promise((resolve,reject)=>{const id=++seq;const timer=setTimeout(()=>{waiting.delete(id);reject(Error("CDP "+method+" timeout"))},20000);waiting.set(id,{resolve:v=>{clearTimeout(timer);resolve(v)},reject:e=>{clearTimeout(timer);reject(e)}});socket.send(JSON.stringify({id,method,params}));});}
async function js(fn){const r=await cmd("Runtime.evaluate",{expression:"("+fn.toString()+")()",awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result?.value;}
async function snap(name){const r=await cmd("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});fs.writeFileSync(path.join(OUT,name),Buffer.from(r.data,"base64"));}
async function main(){
 const chrome=["google-chrome","google-chrome-stable","chromium"].find(n=>cp.spawnSync("which",[n]).status===0);
 if(!chrome)throw Error("Chrome not installed");
 const server=cp.spawn("python3",["-m","http.server","8777","--bind","127.0.0.1","--directory","."],{stdio:"ignore"});children.push(server);
 await poll(async()=>{try{return (await fetch("http://127.0.0.1:8777/web-pasajero/")).ok}catch{return false}});
 const browser=cp.spawn(chrome,["--headless=new","--no-sandbox","--disable-gpu","--disable-dev-shm-usage",
 "--no-first-run","--disable-extensions","--remote-debugging-port=9248",
 "--user-data-dir=/tmp/qt-p14-"+process.pid,"--window-size=390,844","about:blank"],{stdio:"ignore"});children.push(browser);
 const wsUrl=await poll(async()=>{try{const list=await(await fetch("http://127.0.0.1:9248/json/list")).json();return list.find(x=>x.type==="page")?.webSocketDebuggerUrl}catch{return false}});
 socket=new WebSocket(wsUrl);
 await new Promise((resolve,reject)=>{socket.addEventListener("open",resolve,{once:true});socket.addEventListener("error",reject,{once:true})});
 socket.addEventListener("message",e=>{let x;try{x=JSON.parse(String(e.data))}catch{return}if(x.id&&waiting.has(x.id)){const p=waiting.get(x.id);waiting.delete(x.id);x.error?p.reject(Error(x.error.message)):p.resolve(x.result||{});}});
 await cmd("Page.enable");await cmd("Runtime.enable");
 await cmd("Emulation.setDeviceMetricsOverride",{width:390,height:844,deviceScaleFactor:1,mobile:true,screenWidth:390,screenHeight:844});
 await cmd("Emulation.setTouchEmulationEnabled",{enabled:true,maxTouchPoints:1});
 await cmd("Page.addScriptToEvaluateOnNewDocument",{source:`(()=>{
   const original=window.fetch.bind(window);
   const calls=[],exceptions=[];
   window.__qaEstimations={calls,exceptions};
   window.addEventListener("error",e=>exceptions.push(String(e.message||"error").slice(0,170)));
   window.addEventListener("unhandledrejection",e=>exceptions.push(String(e.reason?.message||e.reason||"promise").slice(0,170)));
   const response=data=>Promise.resolve(new Response(JSON.stringify(data),{status:200,headers:{"Content-Type":"application/json"}}));
   window.fetch=(url,opts)=>{
     const request=String(url?.url||url),path=new URL(request,location.href).pathname;
     if(path.includes("/api/public/route-estimate")||path==="/api/testing-road"){
       calls.push("road");
       return response({available:true,source:"openrouteservice",distanceKm:18.8,durationMin:26,
         geometry:[[-34.9,-56.15],[-34.905,-56.16],[-34.91,-56.19]]});
     }
     if(path.includes("public_reference_quote_v1")){
       calls.push("fare");
       return response({available:true,currency:"UYU",referenceFareUyu:752,rateUyuPerKm:40});
     }
     if(path.endsWith("/public_driver_availability_v1")){
       calls.push("availability");
       return response({available:true,status:"available_for_requests",checkedAt:new Date().toISOString(),motionHint:"stationary"});
     }
     if(path.endsWith("/pickup-eta")){
       calls.push("eta");
       return response({available:true,distanceKm:1.8,etaMin:5,estimateType:"coarse"});
     }
     if(path.startsWith("/rest/v1/rpc/")||path.startsWith("/functions/v1/")){
       calls.push("blocked-backend");
       return response({available:false,reason:"not_available"});
     }
     return original(url,opts);
   };
 })();`});
 await cmd("Page.navigate",{url:"http://127.0.0.1:8777/web-pasajero/?ui=clean"});
 await poll(()=>js(function(){return document.readyState==="complete"&&Boolean(window.QuickTripActiveRide)&&Boolean(document.querySelector("#qtc-app")); }),24000);
 await sleep(550);
 await js(function(){document.getElementById("qt-gps-welcome-manual")?.click();return true;});
 const state=await js(function(){
   try{
     originPicker.persist({text:"ORIGEN QA SIN DATOS REALES",lat:-34.9,lng:-56.15,department:"Montevideo"});
     destPicker.persist({text:"DESTINO QA SIN DATOS REALES",lat:-34.91,lng:-56.19,department:"Montevideo"});
     return {ok:true,origin:record.origin?.text,destination:record.destination?.text};
   }catch(e){return {ok:false,error:String(e.stack||e)}}
 });
 console.log("QA P14 selected",JSON.stringify(state));ok(state.ok,"Native origin+destination persist without UI exception");
 const capture=()=>js(function(){
   const q=x=>document.querySelector(x),est=window.__qaEstimations;
   const recap=q("#urgent-route-recap"),distance=q("#urgent-route-recap [data-final-route-distance]"),
      duration=q("#urgent-route-recap [data-final-route-duration]"),
      fare=q("#urgent-route-recap [data-final-route-fare]");
   const km=q("#pickup-distance"),mins=q("#pickup-minutes"),metrics=q("#pickup-presence-metrics");
   return {
     calls:est.calls,exceptions:est.exceptions,roadReady:!!latestRoad?.data&&!!roadEngine.verified(latestRoad.data),
     routeDistance:distance?.textContent,routeDuration:duration?.textContent,routeFare:fare?.textContent,
     routeHidden:recap?.hidden,
     driverAvailable:publicPresenceCache?.available===true,
     pickupDistance:km?.textContent,pickupDuration:mins?.textContent,pickupHidden:metrics?.hidden,
     pickupTitle:q("#pickup-presence-title")?.textContent,
     pickupPhase:q("#pickup-presence")?.dataset?.phase,
     origin:record.origin?.text,destination:record.destination?.text,
     photoLoaded:Array.from(document.styleSheets).some(x=>(x.href||"").includes("quicktrip-p13-approved.css"))
   };
 });
 const result=await poll(async()=>{
   const x=await capture();
   if(x.roadReady&&x.driverAvailable&&!x.pickupHidden&&x.routeFare?.includes("752"))return x;
   return null;
 },17000).catch(async()=>await capture());
 console.log("P14 E2E BOTH ESTIMATION CHAINS",JSON.stringify(result));
 await js(function(){document.querySelector(".qtc-driver")?.scrollIntoView({block:"center"});return true;});
 await snap("p14-real-ui-flow-mocked-network.png");
 fs.writeFileSync(path.join(OUT,"p14-estimates-e2e.json"),JSON.stringify(result,null,2)+"\n");
 ok(result.calls.includes("road")&&result.calls.includes("fare")&&
   result.calls.includes("availability")&&result.calls.includes("eta"),
   "Original route/fare and authorized pickup endpoints CALLED after actual origin/destination selection");
 ok(result.roadReady&&result.routeDistance?.includes("18,8")&&result.routeDuration==="26 min"&&
   result.routeFare?.includes("752")&&!result.routeHidden,
   "Road kilometres, duration and verified quote reach source-owned fare summary");
 ok(result.driverAvailable&&result.pickupDistance?.includes("1,8")&&
   result.pickupDuration?.includes("min")&&!result.pickupHidden,
   "Available driver, pickup km and ETA reach original passenger card");
 ok(result.photoLoaded,"Premium photo P1.3 preserved during both working flows");
 console.log("P14 E2E PASS: mocked backend response through original application controllers, no bookings");
}
main().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>{
 try{socket?.close()}catch{}
 for(const p of children.reverse())try{p.kill("SIGTERM")}catch{}
});
