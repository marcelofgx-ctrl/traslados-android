"use strict";
// Regression: accepted-only ride must show a REAL route map and request GPS
// ONLY through the existing authorized tracking RPC. Never fake car position.
const assert=require("node:assert/strict");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const path=require("node:path"),fs=require("node:fs");
const root=path.resolve(__dirname,"../..");
const source=fs.readFileSync(path.join(root,"web-pasajero/app.js"),"utf8");
assert(source.includes('meta.tone!=="confirmed"'));
assert(source.includes("before.pickup_phase!==current.pickup_phase"));
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
  const server=spawn("python3",["-m","http.server","8297","--bind","127.0.0.1"],{
    cwd:root,stdio:"ignore"
  });
  let browser;
  try{
    let running=false;
    for(let i=0;i<80;i++){
      try{const r=await fetch("http://127.0.0.1:8297/web-pasajero/");if(r.ok){running=true;break}}catch{}
      await delay(110);
    }
    assert(running);
    browser=await chromium.launch({headless:true,
      executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
      args:["--no-sandbox","--disable-dev-shm-usage"]});
    const out=path.join(root,"artifacts/accepted-follow");
    fs.mkdirSync(out,{recursive:true});
    for(const width of [390,412]){
      const ctx=await browser.newContext({viewport:{width,height:844},
        isMobile:true,hasTouch:true,serviceWorkers:"block"});
      let gpsReady=false,networkWrites=0;
      await ctx.route("**/*",route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.hostname==="127.0.0.1")return route.continue();
        if(url.hostname==="tile.openstreetmap.org"){
          return route.fulfill({status:200,contentType:"image/png",
            body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
        }
        const rpc=url.pathname.split("/").pop();
        if(req.method()==="POST"&&/customer_create_reservation|customer_request_pickup_fixed|customer_cancel_reservation|create_reservation/i.test(rpc))
          networkWrites++;
        if(rpc==="customer_reservation_position_v1")
          return route.fulfill({json:gpsReady
            ?{available:true,driverLat:-34.805,driverLng:-56.04,gpsAgeSec:3}
            :{available:false,reason:"preparing_pickup"}});
        if(url.hostname==="traslados-web.marcelof-gx.workers.dev"&&url.pathname.endsWith("route-estimate"))
          return route.fulfill({json:{available:true,source:"openrouteservice",
            distanceKm:4.2,durationMin:9,
            geometry:[[-34.805,-56.04],[-34.81,-56.06],[-34.82,-56.08]]}});
        return route.fulfill({status:200,contentType:"application/json",body:'{"available":false,"reason":"qa"}'});
      });
      await ctx.addInitScript(()=>{try{sessionStorage.setItem("quicktrip.gps.welcome.session.v1","completed")}catch{}});
      const page=await ctx.newPage();let errors=[];
      page.on("pageerror",e=>errors.push(e.message));
      await page.goto("http://127.0.0.1:8297/web-pasajero/",{waitUntil:"load"});
      await page.waitForFunction(()=>typeof autoFollowAccepted==="function");
      const accepted=await page.evaluate(()=>{
        shiftView("inicio");
        const r={id:"00000000-0000-4000-8000-000000001011",
          code:"QT-QA-ACEPTADA",fare_mode:"fixed",quote_final_total:480,
          status:"ACEPTADA",pickup_phase:"SIN_INICIAR",
          created_at:new Date().toISOString(),pickup_mode:"now",
          pickup_date:"2026-10-27",pickup_time:"22:30",
          origin_text:"Roque Sáenz Peña 1711, Canelones",
          destination_text:"Aeropuerto Internacional de Carrasco",
          origin_lat:-34.81,origin_lng:-56.06,
          destination_lat:-34.835,destination_lng:-56.028,
          __guestToken:"qa-guest-never-sent"};
        tripRows=[r];tripLoaded=true;
        const ok=autoFollowAccepted([r],[{...r,status:"PENDIENTE"}]);
        const section=document.querySelector("#qt-focus");
        return {ok,opened:!section.hidden&&section.dataset.mode==="tracking",
          hero:section.querySelector(".qt-focus-sheet-heading h2")?.textContent,
          origin:section.querySelector(".qt-focus-tracking-origin-text")?.textContent,
          destination:section.querySelector(".qt-focus-tracking-destination-text")?.textContent,
          tracking:!!section.querySelector(".reservation-live"),
          oldView:document.querySelector("#view-seguimiento").classList.contains("active")};
      });
      assert(accepted.ok&&accepted.opened&&accepted.tracking&&!accepted.oldView,
        "Accepted ride must open only canonical map "+JSON.stringify(accepted));
      assert.match(accepted.hero,/aceptado/);
      assert.match(accepted.origin,/Roque/);
      assert.match(accepted.destination,/Aeropuerto/);
      await page.waitForFunction(()=>document.querySelector(
        "#qt-focus .reservation-live-map .premium-real-map"),{timeout:18000});
      await page.waitForFunction(()=>document.querySelector(
        "#qt-focus .reservation-live-status")?.textContent.includes("preparando"),{timeout:15000});
      const waiting=await page.evaluate(()=>{
        const el=document.querySelector("#qt-focus .reservation-live");
        const map=el.querySelector(".premium-real-map");
        return {mapHeight:map.getBoundingClientRect().height,
          hasDriver:el.dataset.liveDriver==="true",
          gps:el.querySelector(".reservation-live-status")?.textContent,
          overlays:[...map.querySelectorAll(".premium-map-overlays text")].map(x=>x.textContent),
          width:document.documentElement.scrollWidth-innerWidth};
      });
      assert(waiting.mapHeight>=320,"Large map missing "+JSON.stringify(waiting));
      assert(!waiting.hasDriver,"Never draw driver before GPS authorization");
      assert(!waiting.overlays.includes("🚘"),"Never invent a vehicle marker");
      assert(waiting.width<=3,"Mobile overflow");
      const canonical=await page.evaluate(()=>{
        const focus=document.querySelector("#qt-focus");
        const sheet=focus.querySelector(".qt-focus-sheet");
        const map=focus.querySelector(".qt-focus-map");
        const header=focus.querySelector(".qt-focus-tracking-route");
        const controls=focus.querySelector(".qt-focus-tracking-extra");
        const tabs=document.querySelector(".tabs");
        return {
          sheet:focus.dataset.sheet,header:getComputedStyle(header).display,
          origin:focus.querySelector(".qt-focus-tracking-origin-text").textContent,
          destination:focus.querySelector(".qt-focus-tracking-destination-text").textContent,
          mapHeight:map.getBoundingClientRect().height,
          sheetHeight:sheet.getBoundingClientRect().height,
          extrasHidden:getComputedStyle(controls).display==="none",
          oldTrackerActive:document.querySelector("#view-seguimiento").classList.contains("active"),
          tabsCovered:document.elementFromPoint(innerWidth/2,innerHeight-18)?.closest("#qt-focus")!==null,
          overflow:document.documentElement.scrollWidth-innerWidth
        };
      });
      assert(canonical.sheet==="compact"&&canonical.header==="grid"&&
        canonical.mapHeight>=350&&canonical.extrasHidden&&!canonical.oldTrackerActive&&
        canonical.tabsCovered&&canonical.overflow<=3,
        "Canonical map must dominate screen "+JSON.stringify(canonical));
      assert.match(canonical.origin,/Roque/);
      assert.match(canonical.destination,/Aeropuerto/);
      await page.screenshot({path:path.join(out,"canonical-compact-"+width+".png"),fullPage:true});
      await page.locator("#qt-focus .qt-focus-handle").click();
      const expanded=await page.locator("#qt-focus").evaluate(el=>({
        state:el.dataset.sheet,
        contacts:getComputedStyle(el.querySelector(".qt-focus-tracking-extra")).display,
        controls:[...el.querySelectorAll(".qt-focus-tracking-contact")].map(x=>({
          height:x.getBoundingClientRect().height,href:x.getAttribute("href"),disabled:x.disabled
        }))
      }));
      assert(expanded.state==="expanded"&&expanded.contacts==="grid"&&
        expanded.controls.length===3&&expanded.controls.every(x=>x.height>=44),
        "Expanded details/contact actions must work "+JSON.stringify(expanded));
      await page.screenshot({path:path.join(out,"canonical-expanded-"+width+".png"),fullPage:true});
      await page.locator("#qt-focus .qt-focus-handle").click();
      assert.equal(await page.locator("#qt-focus").getAttribute("data-sheet"),"compact");
      gpsReady=true;
      await page.evaluate(()=>{
        tripRows=[{...tripRows[0],pickup_phase:"EN_CAMINO"}];
        window.QuickTripMapFocus.updateTracking(tripRows[0]);
      });
      await page.waitForFunction(()=>document.querySelector(
        "#qt-focus .reservation-live")?.dataset.liveDriver==="true",{timeout:18000});
      const moving=await page.evaluate(()=>{
        const section=document.querySelector("#qt-focus");
        return {hero:section.querySelector(".qt-focus-sheet-heading h2")?.textContent,
          car:[...section.querySelectorAll(".reservation-live-map .premium-map-overlays text")]
            .some(x=>x.textContent==="🚘"),origin:section.querySelector(
            ".qt-focus-tracking-origin-text")?.textContent};
      });
      assert(moving.car&&moving.hero==="Conductor en camino",JSON.stringify(moving));
      assert.equal(networkWrites,0,"No actual reservation mutations during browser test");
      assert.deepEqual(errors,[],"Uncaught browser errors");
      await page.screenshot({path:path.join(out,"moving-"+width+".png"),fullPage:true});
      console.log("PASS ACCEPTED PREPICKUP → EN CAMINO",width,JSON.stringify({accepted,waiting,moving}));
      await ctx.close();
    }
  }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(err=>{console.error(err);process.exitCode=1});
