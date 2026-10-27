"use strict";
/* Visual/PWA regression for RC1. External traffic mocked/blocked; no backend writes. */
const assert=require("node:assert/strict"),path=require("node:path"),fs=require("node:fs");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const root=path.resolve(__dirname,"../..");
const out=process.env.QA_DIR||path.join(root,"quicktrip-rc1-evidence/visual-pwa");
fs.mkdirSync(out,{recursive:true});
const delay=ms=>new Promise(done=>setTimeout(done,ms));
(async()=>{
 const port=8248,server=spawn("python3",["-m","http.server",String(port),"--bind","127.0.0.1"],{cwd:root,stdio:"ignore"});
 const base="http://127.0.0.1:"+port+"/web-pasajero/";
 let browser;const reports=[],captures=[];
 try{
  let ready=false;
  for(let j=0;j<60;j++){try{if((await fetch(base)).ok){ready=true;break}}catch{}await delay(160)}
  assert(ready,"local RC1 host unavailable");
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/chromium",
   args:["--no-sandbox","--disable-dev-shm-usage",
    "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1"]});
  for(const width of [360,393,412]){
   const errors=[],blocked=[];
   const context=await browser.newContext({viewport:{width,height:852},isMobile:true,hasTouch:true,
    serviceWorkers:width===393?"allow":"block",reducedMotion:"reduce"});
   await context.route("**/*",route=>{
    const req=route.request(),u=new URL(req.url()),name=u.pathname.split("/").pop();
    if(u.hostname==="127.0.0.1"&&u.port===String(port))return route.continue();
    if(u.hostname==="traslados-web.marcelof-gx.workers.dev"&&u.pathname==="/api/public/route-estimate")
      return route.fulfill({json:{available:true,source:"openrouteservice",distanceKm:6.4,durationMin:11,
       geometry:[[-34.82747,-55.9691],[-34.83,-55.99],[-34.83696,-56.01638]]}});
    if(u.hostname==="tile.openstreetmap.org")return route.fulfill({status:200,contentType:"image/png",
     body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
    if(u.hostname==="zetaudvvutlouiqxopvg.supabase.co"){
     if(name==="public_reference_quote_v1")return route.fulfill({json:{available:true,currency:"UYU",rateUyuPerKm:40,referenceFareUyu:256}});
     if(name==="public_cached_route_preview_v1")return route.fulfill({json:{available:false,reason:"no_route"}});
     if(name==="public_driver_availability_v1")return route.fulfill({json:{available:true,status:"available_for_requests",
      checkedAt:new Date().toISOString(),motionHint:"stationary"}});
     if(name==="pickup-eta")return route.fulfill({json:{available:true,distanceKm:3.5,etaMin:10,estimateType:"coarse"}});
     blocked.push("Unexpected RPC "+name);return route.abort();
    }
    blocked.push("No external traffic: "+u.hostname+u.pathname);return route.abort();
   });
   const page=await context.newPage();
   page.on("pageerror",e=>errors.push(e.message));
   await page.goto(base,{waitUntil:"load",timeout:20000});
   await page.evaluate(()=>shiftView("inicio"));
   await page.locator("#view-inicio.active").waitFor({state:"visible"});
   const home=await page.evaluate(()=>({
    services:document.querySelectorAll('#view-inicio [data-service]').length,
    overflow:document.documentElement.scrollWidth-innerWidth,
    theme:document.body.classList.contains("qt-v86-light")
   }));
   assert.equal(home.services,3);assert(home.overflow<=2,"RC1 home overflows "+width+": "+JSON.stringify(home));
   for(const mode of ["dark","light"]){
    const want=mode==="light";
    await page.evaluate(light=>{
     const toggle=document.querySelector('.header [data-qt-component="theme-toggle"]');
     if(document.body.classList.contains("qt-v86-light")!==light)toggle?.click();
    },want);
    assert.equal(await page.evaluate(()=>document.body.classList.contains("qt-v86-light")),want);
    const shot="home-"+width+"-"+mode+".png";
    await page.screenshot({path:path.join(out,shot),animations:"disabled",fullPage:false});
    captures.push(shot);
   }
   await page.locator('#view-inicio [data-service="now"]').click();
   await page.locator("#qtc-app").waitFor({state:"visible",timeout:12000});
   await page.evaluate(()=>{
    originPicker.persist({text:"Punto de prueba A",lat:-34.82747,lng:-55.9691,department:"Canelones"});
    destPicker.persist({text:"Punto de prueba B",lat:-34.83696,lng:-56.01638,department:"Canelones"});
   });
   await page.waitForFunction(()=>latestRoad?.data?.referenceFareUyu===256,{timeout:17000});
   await page.locator("#origin-picker .qt-v90-selected-gps").waitFor({state:"visible",timeout:8000});
   assert.equal(await page.locator(".qt-b-sim,.qt-v90-driver-demo").count(),0);
   assert.equal(await page.locator("#urgent-send").isVisible(),true);
   assert.equal(await page.evaluate(()=>PREVIEW_READ_ONLY),false);
   // The updated selected-origin GPS action delegates to the ORIGINAL native button.
   const calls=await page.evaluate(()=>{
    let hits=0;
    const original=document.querySelector("#origin-picker .location-use-current");
    const onTap=event=>{hits++;event.stopImmediatePropagation();event.preventDefault()};
    original.addEventListener("click",onTap,{capture:true});
    document.querySelector("#origin-picker .qt-v90-selected-gps").click();
    original.removeEventListener("click",onTap,{capture:true});
    return hits;
   });
   assert.equal(calls,1,"GPS button did not delegate one native click");
   const screen=await page.evaluate(()=>({
    overflow:document.documentElement.scrollWidth-innerWidth,
    header:document.querySelector(".qtc-header")?.getBoundingClientRect().height,
    origin:document.querySelector("#origin-picker")?.getBoundingClientRect().height,
    gpsVisible:document.querySelector(".qt-v90-selected-gps")?.getBoundingClientRect().height>=40,
    themeVisible:document.querySelector(".qt-rc1-journey-theme")?.getBoundingClientRect().height>=38,
    routePrice:latestRoad?.data?.referenceFareUyu,
    pickupCardPresent:!!document.querySelector("#pickup-presence")
   }));
   assert(screen.overflow<=2&&screen.origin>=58&&screen.gpsVisible&&screen.themeVisible
    &&screen.routePrice===256&&screen.pickupCardPresent,JSON.stringify(screen));
   for(const mode of ["dark","light"]){
    const want=mode==="light";
    await page.evaluate(light=>{
     if(document.body.classList.contains("qt-v86-light")!==light)
      document.querySelector(".qt-rc1-journey-theme")?.click();
    },want);
    const shot="trip-"+width+"-"+mode+".png";
    await page.screenshot({path:path.join(out,shot),animations:"disabled",fullPage:false});
    captures.push(shot);
   }
   // Programado is still a REAL independent service; never send an RPC in this test.
   await page.evaluate(()=>shiftView("inicio"));
   await page.locator('#view-inicio [data-service="schedule"]').click();
   const scheduled=await page.evaluate(()=>({
    mode:selectedPickupMode,readOnly:PREVIEW_READ_ONLY,
    bookingButton:!!document.querySelector("#confirm-booking"),
    visible:document.querySelector("#view-reservar")?.classList.contains("active")||
            document.querySelector("#view-viaje")?.classList.contains("active")
   }));
   assert.equal(scheduled.mode,"schedule");assert.equal(scheduled.readOnly,false);
   assert(scheduled.visible&&scheduled.bookingButton);
   let sw={notTested:true};
   if(width===393){
    sw=await page.evaluate(async()=>{
     const registration=await navigator.serviceWorker.register("./sw.js");
     await navigator.serviceWorker.ready;
     const keys=await caches.keys();
     const cache=await caches.open("traslados-pwa-rc1-candidate-20261026");
     const css=await cache.match("./quicktrip-rc1.css");
     const js=await cache.match("./quicktrip-rc1-ui.js");
     return {active:!!registration.active,key:keys.includes("traslados-pwa-rc1-candidate-20261026"),
      css:!!css,js:!!js};
    });
    assert(sw.key&&sw.css&&sw.js,"PWA RC1 precache incomplete: "+JSON.stringify(sw));
   }
   assert.deepEqual(errors,[],"Uncaught JS exceptions");
   reports.push({width,status:"PASS",home,screen,scheduled,sw,
    remoteRequestsReal:0,screenshots:captures.filter(c=>c.includes("-"+width+"-"))});
   await context.close();
  }
  const result={status:"PASS",reports,screenshots:captures,environment:"local mock only",
   realDriverOrServerPost:false,androidApkBuilt:false};
  fs.writeFileSync(path.join(out,"RC1_VISUAL_PWA_QA.json"),JSON.stringify(result,null,2));
  console.log("PASS RC1 360/393/412 visual light+dark, selected native GPS, separate schedule, PWA RC1 cache");
 }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(error=>{console.error("FAIL RC1 VISUAL/PWA: "+(error.stack||error));process.exitCode=1});
