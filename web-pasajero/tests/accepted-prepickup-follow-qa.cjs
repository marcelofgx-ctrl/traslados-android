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
        const section=document.querySelector("#view-seguimiento");
        return {ok,opened:section.classList.contains("active"),
          hero:section.querySelector(".premium-tracking-hero h2")?.textContent,
          origin:section.querySelector(".premium-follow-place[data-point=origin] strong")?.textContent,
          destination:section.querySelector(".premium-follow-place[data-point=destination] strong")?.textContent,
          tracking:!!section.querySelector(".reservation-live")};
      });
      assert(accepted.ok&&accepted.opened&&accepted.tracking,
        "Accepted ride must open its tracking card "+JSON.stringify(accepted));
      assert.match(accepted.hero,/aceptó/);
      assert.match(accepted.origin,/Roque/);
      assert.match(accepted.destination,/Aeropuerto/);
      await page.waitForFunction(()=>document.querySelector(
        "#view-seguimiento .reservation-live-map .premium-real-map"),{timeout:18000});
      await page.waitForFunction(()=>document.querySelector(
        "#view-seguimiento .reservation-live-status")?.textContent.includes("preparando"),{timeout:15000});
      const waiting=await page.evaluate(()=>{
        const el=document.querySelector("#view-seguimiento .reservation-live");
        const map=el.querySelector(".premium-real-map");
        return {mapHeight:map.getBoundingClientRect().height,
          hasDriver:el.dataset.liveDriver==="true",
          gps:el.querySelector(".reservation-live-status")?.textContent,
          overlays:[...map.querySelectorAll(".premium-map-overlays text")].map(x=>x.textContent),
          width:document.documentElement.scrollWidth-innerWidth};
      });
      assert(waiting.mapHeight>=320,"Large map missing "+JSON.stringify(waiting));
      const simple=await page.evaluate(()=>{
        const view=document.querySelector("#view-seguimiento");
        const extras=view.querySelector(".qt-track-more");
        const card=reservationView({...tripRows[0]},"qa-guest-never-sent");
        return {addresses:view.querySelectorAll(".premium-follow-place").length,
          maps:view.querySelectorAll(".reservation-live-map").length,
          extrasClosed:extras?.open===false,
          summaryHasNoMap:!card.querySelector(".reservation-live-slot"),
          summaryButton:!!card.querySelector(".qt-trip-map-open"),
          summaryCompact:!card.open,
          overflow:document.documentElement.scrollWidth-innerWidth};
      });
      assert(simple.addresses===2&&simple.maps===1&&simple.extrasClosed&&
        simple.summaryHasNoMap&&simple.summaryButton&&simple.summaryCompact&&simple.overflow<=3,
        "Single map + simple list + folded details regression "+JSON.stringify(simple));
      assert(!waiting.hasDriver,"Never draw driver before GPS authorization");
      assert(!waiting.overlays.includes("🚘"),"Never invent a vehicle marker");
      assert(waiting.width<=3,"Mobile overflow");
      const ui=await page.evaluate(()=>{
        const root=document.querySelector("#view-seguimiento");
        const card=root.querySelector(".premium-tracking-card");
        const timeline=root.querySelector(".premium-tracking-timeline");
        const steps=[...root.querySelectorAll(".premium-tracking-step")];
        const buttons=[...root.querySelectorAll(".premium-tracking-actions .premium-tracking-action")];
        const boxes=steps.map(x=>x.getBoundingClientRect());
        const actions=buttons.map(x=>x.getBoundingClientRect());
        const header=root.querySelector(".premium-tracking-stephead");
        const strong=root.querySelector(".premium-tracking-step-text strong");
        const circle=root.querySelector(".premium-tracking-step-dot");
        return {cardWidth:card?.getBoundingClientRect().width||0,
          timelineDisplay:getComputedStyle(timeline).display,
          stepDisplay:steps.map(x=>getComputedStyle(x).display),
          stepCount:steps.length,buttonCount:buttons.length,
          noOverlap:boxes.every((b,i)=>i===0||b.top>=boxes[i-1].bottom-1),
          dotsize:circle?.getBoundingClientRect().width||0,
          labelFont:parseFloat(getComputedStyle(strong).fontSize),
          stepHeadDisplay:getComputedStyle(header).display,
          buttonHeights:actions.map(x=>x.height),
          buttonBg:getComputedStyle(buttons[0]).backgroundColor,
          buttonsInViewport:actions.every(x=>x.left>=-1&&x.right<=innerWidth+1),
          cardInViewport:card.getBoundingClientRect().right<=innerWidth+1,
          horizontalOverflow:document.documentElement.scrollWidth-innerWidth};
      });
      assert(ui.timelineDisplay==="grid"&&ui.stepCount===4&&
        ui.stepDisplay.every(x=>x==="grid")&&ui.noOverlap,
        "Timeline steps must align without overlap: "+JSON.stringify(ui));
      assert(ui.dotsize>=28&&ui.labelFont<=15&&
        ui.stepHeadDisplay==="flex"&&ui.cardInViewport,
        "Compact tracking card and labels regression: "+JSON.stringify(ui));
      assert(ui.buttonCount===3&&ui.buttonHeights.every(h=>h>=44)&&
        ui.buttonsInViewport&&ui.buttonBg!=="rgba(0, 0, 0, 0)"&&
        ui.horizontalOverflow<=3,
        "Tracking phone/WhatsApp/chat actions must be styled: "+JSON.stringify(ui));
      await page.screenshot({path:path.join(out,"accepted-"+width+".png"),fullPage:true});
      gpsReady=true;
      await page.evaluate(()=>{
        tripRows=[{...tripRows[0],pickup_phase:"EN_CAMINO"}];
        renderPremiumTracking();
      });
      await page.waitForFunction(()=>document.querySelector(
        "#view-seguimiento .reservation-live")?.dataset.liveDriver==="true",{timeout:18000});
      const moving=await page.evaluate(()=>{
        const section=document.querySelector("#view-seguimiento");
        return {hero:section.querySelector(".premium-tracking-hero h2")?.textContent,
          car:[...section.querySelectorAll(".reservation-live-map .premium-map-overlays text")]
            .some(x=>x.textContent==="🚘"),origin:section.querySelector(
            ".premium-follow-place[data-point=origin] strong")?.textContent};
      });
      assert(moving.car&&moving.hero==="Conductor en camino",JSON.stringify(moving));
      await page.locator("#view-seguimiento .qt-track-more summary").click();
      assert.equal(await page.locator("#view-seguimiento .premium-tracking-actions").isVisible(),true,
        "Contact controls must remain available inside Details");
      assert.equal(networkWrites,0,"No actual reservation mutations during browser test");
      assert.deepEqual(errors,[],"Uncaught browser errors");
      await page.screenshot({path:path.join(out,"moving-"+width+".png"),fullPage:true});
      console.log("PASS ACCEPTED PREPICKUP → EN CAMINO",width,JSON.stringify({accepted,waiting,moving}));
      await ctx.close();
    }
  }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(err=>{console.error(err);process.exitCode=1});
