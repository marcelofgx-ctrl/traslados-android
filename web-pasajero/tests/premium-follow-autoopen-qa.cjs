"use strict";
// QA: passenger UI and GPS automation on a local origin with network fully
// intercepted. No bookings, Supabase writes, or public deployment.
const assert=require("node:assert/strict");
const path=require("node:path"),fs=require("node:fs");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const root=path.resolve(__dirname,"../..");
const code=fs.readFileSync(path.join(root,"web-pasajero/app.js"),"utf8");
const tracker=fs.readFileSync(path.join(root,"web-pasajero/reservation-live.js"),"utf8");
const style=fs.readFileSync(path.join(root,"web-pasajero/quicktrip-clean.css"),"utf8");
assert(code.includes("function autoFollowAccepted(rows,previous)"));
assert(code.includes("const autoOpened=autoFollowAccepted(rows,oldTripRows);"));
assert(code.includes("const cadence=activeTrip||awaiting?4000:20000;"));
assert(tracker.includes("},4000);"));
assert(tracker.includes("previousCar:kind===\"driver\"?lastDriverPos:null"));
assert(style.includes(".premium-follow-places"));
const delay=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const server=spawn("python3",["-m","http.server","8291","--bind","127.0.0.1"],
   {cwd:root,stdio:"ignore"});
 let browser;
 try{
  let available=false;
  for(let i=0;i<100;i++){try{if((await fetch("http://127.0.0.1:8291/web-pasajero/")).ok){available=true;break}}catch{}await delay(120)}
  assert(available,"local Web did not start");
  browser=await chromium.launch({headless:true,
    executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
    args:["--no-sandbox","--disable-dev-shm-usage"]});
  const out=process.env.QA_DIR||path.join(root,"artifacts/premium-follow");
  fs.mkdirSync(out,{recursive:true});
  for(const width of [390,412]){
   const context=await browser.newContext({viewport:{width,height:844},
      isMobile:true,hasTouch:true,reducedMotion:"reduce",serviceWorkers:"block"});
   let writes=0;
   await context.route("**/*",async route=>{
     const request=route.request(),u=new URL(request.url()),rpc=u.pathname.split("/").pop();
     if(u.hostname==="127.0.0.1")return route.continue();
     if(u.hostname==="tile.openstreetmap.org")return route.fulfill({status:200,contentType:"image/png",
        body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
     if(u.hostname==="traslados-web.marcelof-gx.workers.dev"&&u.pathname.endsWith("/route-estimate"))
       return route.fulfill({json:{available:true,source:"openrouteservice",distanceKm:3.2,durationMin:7,
          geometry:[[-34.805,-56.04],[-34.807,-56.05],[-34.81,-56.06]]}});
     if(u.hostname==="zetaudvvutlouiqxopvg.supabase.co"){
       if(request.method()==="POST"&&/create|request|transition|cancel/i.test(rpc))writes++;
       if(rpc==="customer_reservation_position_v1")return route.fulfill({json:{
         available:true,driverLat:-34.805,driverLng:-56.04,gpsAgeSec:3}});
       if(rpc==="public_driver_availability_v1")return route.fulfill({json:{available:false}});
       if(rpc==="public_cached_route_preview_v1")return route.fulfill({json:{available:false,reason:"no_route"}});
       return route.fulfill({json:{available:false,reason:"not_available"}});
     }
     return route.abort();
   });
   await context.addInitScript(()=>{try{sessionStorage.setItem("quicktrip.gps.welcome.session.v1","completed")}catch{}});
   const page=await context.newPage(),errors=[];
   page.on("pageerror",e=>errors.push(e.message));
   await page.goto("http://127.0.0.1:8291/web-pasajero/",{waitUntil:"load"});
   await page.waitForFunction(()=>typeof autoFollowAccepted==="function"&&
      !!document.querySelector("#view-seguimiento"),{timeout:15000});
   const observed=await page.evaluate(()=>{
     shiftView("inicio");
     const pending={id:"00000000-0000-4000-8000-000000001011",code:"QT-PRUEBA",
       status:"PENDIENTE",pickup_phase:"SIN_INICIAR",created_at:new Date().toISOString(),
       pickup_date:"2026-10-27",pickup_time:"20:00",
       origin_text:"Aeropuerto de Carrasco",destination_text:"Centro de Montevideo",
       origin_lat:-34.81,origin_lng:-56.06,destination_lat:-34.9,destination_lng:-56.19,
       __guestToken:"synthetic-token-never-sent"};
     const accepted={...pending,status:"ACEPTADA",pickup_phase:"EN_CAMINO"};
     tripRows=[accepted];tripLoaded=true;tripSnap=null;
     const opened=autoFollowAccepted([accepted],[pending]);
     const suppressed=autoFollowAccepted([accepted],[pending]);
     const focus=document.querySelector("#qt-focus");
     return {opened,suppressed,active:focus?.dataset.mode==="tracking"&&!focus.hidden,
       origin:focus?.querySelector(".qt-focus-tracking-origin-text")?.textContent,
       destination:focus?.querySelector(".qt-focus-tracking-destination-text")?.textContent,
       oldView:document.querySelector("#view-seguimiento").classList.contains("active")};
   });
   assert(observed.opened&&!observed.suppressed&&observed.active&&!observed.oldView,
      "Canonical map must auto-open without old view "+JSON.stringify(observed));
   assert.equal(observed.origin,"Aeropuerto de Carrasco");
   assert.equal(observed.destination,"Centro de Montevideo");
   await page.waitForFunction(()=>document.querySelector(
       "#qt-focus .reservation-live-map .premium-real-map"),{timeout:15000});
   await page.waitForFunction(()=>document.querySelector(
       "#qt-focus .reservation-live")?.dataset.liveDriver==="true",{timeout:15000});
   const results=await page.evaluate(()=>{
     const section=document.querySelector("#qt-focus");
     const map=section.querySelector(".reservation-live-map .premium-real-map");
     return {mapHeight:map?.getBoundingClientRect().height,
       tiles:map?.querySelectorAll(".premium-map-tiles img").length,
       gps:section.querySelector(".reservation-live-freshness")?.textContent,
       routeLabel:section.querySelector(".qt-focus-message")?.textContent,
       canonicalHeader:getComputedStyle(section.querySelector(".qt-focus-tracking-route")).display==="grid",
       compact:section.dataset.sheet==="compact",
       width:innerWidth,overflow:document.documentElement.scrollWidth-innerWidth,
       hasCar:[...(map?.querySelectorAll(".premium-map-overlays text")||[])].some(e=>e.textContent?.includes("🚘"))};
   });
   assert(results.mapHeight>=320,"Map is still a miniature: "+JSON.stringify(results));
   assert(results.tiles>0,"Real map tiles were not laid out");
   assert(results.hasCar&&results.canonicalHeader&&results.compact,
      "Authorized GPS and compact canonical map required "+JSON.stringify(results));
   assert(results.overflow<=3,"Mobile horizontal overflow: "+JSON.stringify(results));
   assert.equal(writes,0,"Browser test must not write a booking");
   await page.screenshot({path:path.join(out,"seguimiento-"+width+".png"),fullPage:true,animations:"disabled"});
   console.log("PASS premium passenger live:",width,JSON.stringify({observed,results}));
   assert.deepEqual(errors,[],"Uncaught JS browser exception");
   await context.close();
  }
 }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(e=>{console.error(e);process.exitCode=1});
