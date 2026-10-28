"use strict";
// Acceptance: independent, verified approach + contracted ride, authorized GPS,
// draggable bottom sheet, no backend writes. Runs only on isolated QA branch.
const assert=require("node:assert/strict");
const path=require("node:path"),fs=require("node:fs");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const cwd=path.resolve(__dirname,"../.."),sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const server=spawn("python3",["-m","http.server","8296","--bind","127.0.0.1"],{cwd,stdio:"ignore"});let browser;
 try{
  let ready=false;for(let i=0;i<80;i++){try{if((await fetch("http://127.0.0.1:8296/web-pasajero/")).ok){ready=true;break}}catch{}await sleep(130)}
  assert(ready,"local preview server");
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  const out=path.join(cwd,"artifacts/dual-leg-sheet");fs.mkdirSync(out,{recursive:true});
  for(const width of [390,412]){
   const ctx=await browser.newContext({viewport:{width,height:844},isMobile:true,hasTouch:true,
    serviceWorkers:"block",reducedMotion:"reduce"});
   let outboundWrites=0;
   await ctx.route("**/*",route=>{
    const req=route.request(),url=new URL(req.url());
    if(url.hostname==="127.0.0.1")return route.continue();
    if(url.hostname==="tile.openstreetmap.org")return route.fulfill({
      status:200,contentType:"image/png",
      body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
    if(req.method()!=="GET")outboundWrites++;
    return route.abort();
   });
   const page=await ctx.newPage(),errors=[];
   page.on("pageerror",e=>errors.push(e.message));
   await page.goto("http://127.0.0.1:8296/web-pasajero/",{waitUntil:"load"});
   await page.waitForFunction(()=>!!window.QuickTripMapFocus&&!!window.TrasladosPremiumMap&&
     !!window.TrasladosReservationLive,{timeout:20000});
   const result=await page.evaluate(()=>{
    const r={id:"00000000-0000-4000-8000-000000000721",status:"ACEPTADA",
      pickup_phase:"EN_CAMINO",quote_final_total:124,
      origin_text:"Parque de Carrasco",destination_text:"Aeropuerto de Carrasco",
      origin_lat:-34.815,origin_lng:-56.053,
      destination_lat:-34.834,destination_lng:-56.025};
    window.__dualR=r;
    const engine={verified:v=>v?.available===true&&v.source==="openrouteservice",
      estimate:async points=>{
        const [a,b]=points;
        return {available:true,source:"openrouteservice",distanceKm:2.6,
          durationMin:6,geometry:[[a.lat,a.lng],
            [(a.lat+b.lat)/2+.001,(a.lng+b.lng)/2-.001],[b.lat,b.lng]]};
      }};
    const opts={reservation:r,guestToken:"test-fixture-guest-token",sessionToken:null,
      rpc:async(name)=>{if(name!=="customer_reservation_position_v1")throw Error("Unexpected RPC "+name);
        return {available:true,driverLat:-34.804,driverLng:-56.081,gpsAgeSec:2};},
      engine,api:"https://invalid.local",key:"qa-only",renderer:window.TrasladosPremiumMap};
    window.__dualOpts=opts;
    const denied=window.QuickTripMapFocus.showTracking({...opts,guestToken:null});
    const accepted=window.QuickTripMapFocus.showTracking(opts);
    return {denied,accepted};
   });
   assert.equal(result.denied,false,"No tracking without ownership token");
   assert.equal(result.accepted,true,"Authorized accepted booking must open full map");
   await page.waitForFunction(()=>{
    const overlay=document.querySelector("#qt-focus");
    return overlay?.dataset.mode==="tracking"&&
      overlay.querySelector(".premium-map-approach")&&
      overlay.querySelector(".premium-map-route")&&
      [...overlay.querySelectorAll(".premium-map-overlays text")].some(x=>x.textContent==="🚘");
   },{timeout:18000});
   const first=await page.evaluate(()=>{
    const s=document.querySelector("#qt-focus"),map=s.querySelector(".premium-real-map");
    const main=s.querySelector(".premium-map-route"),secondary=s.querySelector(".premium-map-approach");
    return {visible:!s.hidden,gps:!!s.querySelector(".premium-map-approach"),
      car:[...s.querySelectorAll(".premium-map-overlays text")].some(x=>x.textContent==="🚘"),
      distinct:main?.getAttribute("points")!==secondary?.getAttribute("points"),
      approachColor:getComputedStyle(secondary).stroke,
      rideColor:getComputedStyle(main).stroke,
      sheet:s.querySelector(".qt-focus-sheet").getBoundingClientRect().top,
      price:s.querySelector(".qt-focus-fare").textContent,
      compact:s.dataset.sheet==="compact",
      mapWidth:map.getBoundingClientRect().width,screenWidth:innerWidth,
      overflow:document.documentElement.scrollWidth-innerWidth};
   });
   assert(first.visible&&first.gps&&first.car&&first.distinct,JSON.stringify(first));
   assert.notEqual(first.approachColor,first.rideColor,"Two legs must have distinct colors");
   assert.match(first.price,/124/);assert(first.compact);
   assert(Math.abs(first.mapWidth-first.screenWidth)<2&&first.overflow<=3);
   await page.screenshot({path:path.join(out,"approach-"+width+".png"),fullPage:true});
   // Handle click expands details, another click minimizes; swipe works too.
   const handle=page.locator("#qt-focus .qt-focus-handle");
   await handle.click();
   assert.equal(await page.locator("#qt-focus").getAttribute("data-sheet"),"expanded");
   assert.equal(await handle.getAttribute("aria-expanded"),"true");
   const stats=await page.locator("#qt-focus .qt-focus-stats").isVisible();
   assert(stats,"Expanded sheet must show distance and duration");
   await page.screenshot({path:path.join(out,"expanded-"+width+".png"),fullPage:true});
   const bbox=await page.locator("#qt-focus .qt-focus-sheet-heading").boundingBox();
   assert(bbox);
   await page.mouse.move(bbox.x+50,bbox.y+18);await page.mouse.down();
   await page.mouse.move(bbox.x+50,bbox.y+91,{steps:7});await page.mouse.up();
   assert.equal(await page.locator("#qt-focus").getAttribute("data-sheet"),"compact","Swipe down must collapse");
   const changed=await page.evaluate(()=>window.QuickTripMapFocus.updateTracking({
     ...window.__dualR,status:"EN_VIAJE",pickup_phase:"EN_PUNTO"}));
   assert.equal(changed,true);
   await page.waitForFunction(()=>{
     const s=document.querySelector("#qt-focus");
     return s.dataset.mode==="tracking"&&s.querySelector(".premium-map-route")&&
       !s.querySelector(".premium-map-approach")&&
       s.querySelector(".qt-focus-sheet-heading h2")?.textContent==="Tu viaje en curso";
   },{timeout:16000});
   assert.equal(await page.locator("#qt-focus .qt-focus-legend-approach").isVisible(),false);
   await page.screenshot({path:path.join(out,"in-ride-"+width+".png"),fullPage:true});
   await page.locator("#qt-focus .qt-focus-back").click();
   assert.equal(await page.locator("#qt-focus").evaluate(e=>e.hidden),true);
   assert.equal(outboundWrites,0,"No production writes");
   assert.deepEqual(errors,[],"Browser errors");
   console.log("PASS DUAL-LEG AND SHEET",width,JSON.stringify(first));
   await ctx.close();
  }
 }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(e=>{console.error(e);process.exitCode=1});
