"use strict";
/* Diagnostic-only 5-stage flow. Every non-local browser request intercepted.
 * No real reservations, Supabase or Cloudflare requests, and no code changes.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),{spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const cwd=path.resolve(__dirname,"../.."),out=path.join(cwd,"artifacts/continuous-follow-audit");
fs.mkdirSync(out,{recursive:true});
const sleep=ms=>new Promise(ok=>setTimeout(ok,ms));
const tile=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64");
const findings=[],steps=[];
function check(ok,name,detail){
  const item={name,passed:Boolean(ok),detail};
  console[ok?"log":"error"]("QA "+(ok?"PASS ":"FAIL ")+name+" "+JSON.stringify(detail));
  if(!ok)findings.push(item);
}
const server=spawn("python3",["-m","http.server","8292","--bind","127.0.0.1"],{cwd,stdio:"ignore"});
let browser;
(async()=>{
 try{
   let serving=false;
   for(let n=0;n<100;n++){
     try{if((await fetch("http://127.0.0.1:8292/web-pasajero/")).ok){serving=true;break}}catch{}
     await sleep(120);
   }
   assert(serving,"Local server unavailable");
   browser=await chromium.launch({headless:true,
     executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
     args:["--no-sandbox","--disable-dev-shm-usage"]});
   for(const width of [390,412]){
     const state={row:{
       id:"00000000-0000-4000-8000-000000009999",
       code:"QT-QA-CONTINUOUS",status:"ACEPTADA",pickup_phase:"SIN_INICIAR",
       created_at:new Date().toISOString(),pickup_mode:"now",
       pickup_date:new Date().toISOString().slice(0,10),pickup_time:"16:00",
       fare_mode:"fixed",quote_final_total:124,
       origin_text:"Parque de Carrasco, Canelones",
       destination_text:"Aeropuerto Internacional de Carrasco",
       origin_lat:-34.815,origin_lng:-56.053,
       destination_lat:-34.834,destination_lng:-56.025
     },gps:false};
     let attempts=0,blocked=0;
     const ctx=await browser.newContext({viewport:{width,height:844},isMobile:true,
       hasTouch:true,serviceWorkers:"block",reducedMotion:"reduce"});
     await ctx.route("**/*",route=>{
       const req=route.request(),url=new URL(req.url());
       if(url.hostname==="127.0.0.1")return route.continue();
       if(url.hostname==="tile.openstreetmap.org")
         return route.fulfill({status:200,contentType:"image/png",body:tile});
       const rpc=url.pathname.split("/").pop();
       if(req.method()==="POST"&&/(create_reservation|customer_request_pickup_fixed|cancel_reservation|customer_quote_decision|customer_trip_chat_send)/i.test(rpc))
         attempts++;
       if(rpc==="get_reservation_by_token_v5")return route.fulfill({json:state.row});
       if(rpc==="customer_reservation_position_v1")return route.fulfill({json:state.gps?
         {available:true,driverLat:-34.804,driverLng:-56.081,gpsAgeSec:3}:
         {available:false,reason:"preparing_pickup"}});
       if(url.pathname.endsWith("/route-estimate")){
         const pp=(url.searchParams.get("points")||"").split(";").map(x=>x.split(",").map(Number));
         if(pp.length<2||pp.some(x=>x.length!==2||x.some(v=>!Number.isFinite(v))))
           return route.fulfill({status:400,json:{available:false,reason:"invalid_points"}});
         const a=pp[0],b=pp[pp.length-1];
         return route.fulfill({json:{available:true,source:"openrouteservice",
           distanceKm:3.2,durationMin:7,
           geometry:[[a[1],a[0]],[(a[1]+b[1])/2+.0005,(a[0]+b[0])/2],[b[1],b[0]]]}});
       }
       if(rpc==="public_reference_quote_v1")return route.fulfill({json:{
         available:true,currency:"UYU",referenceFareUyu:124,rateUyuPerKm:40}});
       if(rpc==="public_driver_availability_v1"||rpc==="public_cached_route_preview_v1")
         return route.fulfill({json:{available:false,reason:"qa"}});
       blocked++;return route.abort();
     });
     await ctx.addInitScript(()=>{
       try{
         localStorage.setItem("traslados.passenger.guest-trips.v1",
           JSON.stringify([{token:"qa-synthetic-owner-token"}]));
         sessionStorage.setItem("quicktrip.gps.welcome.session.v1","completed");
       }catch{}
     });
     const page=await ctx.newPage(),errors=[];
     page.on("pageerror",e=>errors.push(e.message));
     async function capture(stage){
       const s=await page.evaluate(()=>{
         const el=document.querySelector("#qt-focus"),
           frame=el?.querySelector(".premium-real-map"),
           live=el?.querySelector(".reservation-live"),
           main=el?.querySelector(".premium-map-route"),
           approach=el?.querySelector(".premium-map-approach");
         const labels=[...(el?.querySelectorAll(".premium-map-overlays text")||[])].map(x=>x.textContent);
         return {open:!!el&&!el.hidden,heading:el?.querySelector(".qt-focus-sheet-heading h2")?.textContent,
           sheet:el?.dataset.sheet,origin:el?.querySelector(".qt-focus-tracking-origin-text")?.textContent,
           destination:el?.querySelector(".qt-focus-tracking-destination-text")?.textContent,
           fare:el?.querySelector(".qt-focus-fare")?.textContent,
           zoom:frame?.dataset.zoom,manual:frame?.dataset.manual,
           hasCar:labels.includes("🚘"),dual:!!(main&&approach),
           approachColor:approach?getComputedStyle(approach).stroke:null,
           rideColor:main?getComputedStyle(main).stroke:null,
           driverLive:live?.dataset.liveDriver==="true",
           mapPresent:!!frame,livePresent:!!live,
           statusText:live?.querySelector(".reservation-live-status")?.textContent,
           message:el?.querySelector(".qt-focus-message")?.textContent,
           alertOpen:!document.querySelector("#trip-alert-overlay")?.hidden,
           alertTitle:document.querySelector("#trip-alert-title")?.textContent,
           overflow:document.documentElement.scrollWidth-innerWidth};
       });
       steps.push({width,stage,...s});
       await page.screenshot({path:path.join(out,stage+"-"+width+".png"),fullPage:true});
       return s;
     }
     async function waitHeading(t,car){
       await page.waitForFunction(title=>{
         const el=document.querySelector("#qt-focus");
         return el&&!el.hidden&&el.querySelector(".qt-focus-sheet-heading h2")?.textContent===title;
       },t,{timeout:12000});
       await page.waitForFunction(()=>{
         const s=document.querySelector("#qt-focus");
         return !!s?.querySelector(".premium-real-map");
       },null,{timeout:2500}).catch(()=>{});
       if(car)await page.waitForFunction(()=>{
         const s=document.querySelector("#qt-focus");
         return s?.querySelector(".reservation-live")?.dataset.liveDriver==="true"&&
           [...s.querySelectorAll(".premium-map-overlays text")].some(x=>x.textContent==="🚘");
       },null,{timeout:2500}).catch(()=>{});
     }
     try{
       await page.goto("http://127.0.0.1:8292/web-pasajero/",{waitUntil:"load"});
       await waitHeading("Viaje aceptado",false);
       const a=await capture("01-accepted");
       check(!a.hasCar&&a.open&&a.sheet==="compact"&&
         /Parque de Carrasco/.test(a.origin||"")&&/Aeropuerto/.test(a.destination||""),
         width+"-accepted-no-fake-car",a);
       state.row={...state.row,pickup_phase:"EN_CAMINO"};state.gps=true;
       await page.evaluate(async()=>loadTrips({background:true}));
       await waitHeading("Conductor en camino",true);
       await page.waitForFunction(()=>!!document.querySelector("#qt-focus .premium-map-approach"),
         null,{timeout:1800}).catch(()=>{});
       const b=await capture("02-en-camino");
       check(b.dual&&b.approachColor!==b.rideColor&&b.hasCar,
         width+"-approach-two-colors",b);
       check(a.fare===b.fare,width+"-fare-preserved",{
         before:a.fare,after:b.fare});
       await page.locator("#qt-focus .premium-map-control-zoom-in").click();
       await page.locator("#qt-focus .qt-focus-handle").click();
       const custom=await capture("02b-user-expanded");
       check(custom.manual==="true"&&custom.sheet==="expanded",
         width+"-user-customization",custom);
       state.row={...state.row,pickup_phase:"EN_PUNTO"};
       await page.evaluate(async()=>loadTrips({background:true}));
       await waitHeading("En tu recogida",true);
       const c=await capture("03-en-punto");
       check(c.mapPresent&&c.hasCar&&c.dual,width+"-pickup-position-with-gps",c);
       check(c.sheet==="expanded",width+"-keep-sheet-on-arrival",{
         before:custom.sheet,after:c.sheet});
       check(c.manual==="true"&&c.zoom===custom.zoom,
         width+"-keep-map-camera-on-arrival",{
           before:{zoom:custom.zoom,manual:custom.manual},
           after:{zoom:c.zoom,manual:c.manual}});
       state.row={...state.row,status:"EN_VIAJE"};
       await page.evaluate(async()=>loadTrips({background:true}));
       await waitHeading("Viaje en curso",true);
       const d=await capture("04-en-viaje");
       check(d.open&&d.hasCar&&!d.dual,width+"-onboard-direction",d);
       check(d.fare===a.fare&&d.origin===a.origin&&d.destination===a.destination,
         width+"-trip-contract-preserved",d);
       state.row={...state.row,status:"FINALIZADA"};state.gps=false;
       await page.evaluate(async()=>loadTrips({background:true}));
       await page.waitForFunction(()=>window.QuickTripMapFocus?.isTrackingOpen?.()===false,
         null,{timeout:4000}).catch(()=>{});
       const e=await capture("05-finalizada");
       check(!e.open,width+"-final-close",e);
       check(e.alertOpen&&/Gracias por viajar/.test(e.alertTitle||""),
         width+"-final-notice-consistent",e);
     }catch(e){
       const msg=String(e.stack||e);
       findings.push({name:width+"-execution-error",detail:msg});
       console.error("QA ERROR "+width+" "+msg);
       await page.screenshot({path:path.join(out,"failure-"+width+".png"),fullPage:true}).catch(()=>{});
     }finally{
       check(attempts===0,width+"-no-booking-writes",{attempts,blocked});
       check(errors.length===0,width+"-no-js-errors",errors);
       await ctx.close();
     }
   }
 }finally{
   if(browser)await browser.close();
   server.kill("SIGTERM");
   fs.writeFileSync(path.join(out,"audit-result.json"),
     JSON.stringify({steps,findings,passed:findings.length===0,
       note:"Browser external network fully intercepted; synthetic reservation and GPS."},null,2));
   console.log("QA AUDIT SUMMARY "+JSON.stringify({
     stages:steps.length,failures:findings.length,codes:findings.map(x=>x.name)}));
   if(findings.length)process.exitCode=1;
 }
})().catch(e=>{console.error("QA FATAL",e);process.exitCode=1;});
