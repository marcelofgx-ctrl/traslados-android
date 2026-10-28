"use strict";
/* Read-only mobile browser verification of the actual passenger map.
 * No real requests, bookings, customer credentials or production deployment. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const cwd=path.resolve(__dirname,"../..");
const wait=ms=>new Promise(ok=>setTimeout(ok,ms));
(async()=>{
 const server=spawn("python3",["-m","http.server","8298","--bind","127.0.0.1"],
 {cwd,stdio:"ignore"});let browser;
 try{
  let started=false;
  for(let i=0;i<80;i++){try{const r=await fetch("http://127.0.0.1:8298/web-pasajero/");if(r.ok){started=true;break}}catch{}await wait(100)}
  assert(started,"local PWA not ready");
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  const output=path.join(cwd,"artifacts/map-nav");
  fs.mkdirSync(output,{recursive:true});
  for(const width of [390,412]){
   const ctx=await browser.newContext({viewport:{width,height:844},isMobile:true,
    hasTouch:true,serviceWorkers:"block"});
   let writes=0;
   await ctx.route("**/*",route=>{
    const req=route.request(),url=new URL(req.url());
    if(url.hostname==="127.0.0.1")return route.continue();
    if(url.hostname==="tile.openstreetmap.org")return route.fulfill({status:200,contentType:"image/png",
     body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
    // Supabase read-only RPCs also use POST. Only count actual booking
    // mutations; ALL outbound requests are intercepted before any network.
    const rpcName=url.pathname.split("/").pop()||"";
    if(req.method()==="POST"&&/^(?:create_reservation|customer_create_reservation_v\\d+|customer_request_pickup_fixed_v\\d+|customer_cancel_reservation|customer_quote_decision_v\\d+|mapa_accept_reservation.*)$/i.test(rpcName))writes++;
    return route.fulfill({status:200,contentType:"application/json",body:'{"available":false,"reason":"qa"}'});
   });
   const page=await ctx.newPage();const errors=[];page.on("pageerror",e=>errors.push(e.message));
   await page.goto("http://127.0.0.1:8298/web-pasajero/?ui=clean");
   const host=".premium-route-canvas .premium-real-map";
   await page.waitForSelector(host+" .premium-map-control-zoom-in",{timeout:22000});
   await page.locator(host).scrollIntoViewIfNeeded();
   const baseline=await page.locator(host).evaluate(el=>({
      zoom:+el.dataset.zoom,height:el.getBoundingClientRect().height,
      pageScroll:getComputedStyle(el).touchAction,
      cardHeight:el.closest(".premium-route-cartography")?.getBoundingClientRect().height
   }));
   assert(baseline.cardHeight>=338,"Map must be prominent: "+JSON.stringify(baseline));
   assert.equal(baseline.pageScroll,"pan-y","Normal swipes must scroll the page");
   await page.locator(host+" .premium-map-control-zoom-in").click();
   const zoomed=await page.locator(host).evaluate(el=>+el.dataset.zoom);
   assert.equal(zoomed,baseline.zoom+1,"Zoom + must display closer cartography");
   await page.locator(host+" .premium-map-control-explore").click();
   assert.equal(await page.locator(host).evaluate(el=>el.dataset.explore),"true");
   assert.equal(await page.locator(host).evaluate(el=>getComputedStyle(el).touchAction),"none");
   const current=await page.locator(host).evaluate(el=>({
    x:Number(el.querySelector("img")?.style.left?.replace("px","")||0),
    zoom:+el.dataset.zoom
   }));
   const box=await page.locator(host).boundingBox();assert(box);
   const center={x:box.x+box.width/2-40,y:box.y+box.height/2+20};
   await page.mouse.move(center.x,center.y);await page.mouse.down();
   await page.mouse.move(center.x+86,center.y+20,{steps:8});await page.mouse.up();
   const after=await page.locator(host).evaluate(el=>({
    x:Number(el.querySelector("img")?.style.left?.replace("px","")||0),
    manual:el.dataset.manual,zoom:+el.dataset.zoom
   }));
   assert.equal(after.manual,"true","Manual pan did not persist");
   assert.equal(after.zoom,current.zoom,"Panning should not change zoom");
   assert(Math.abs(after.x-current.x)>12,"Panning did not change viewport");
   await page.locator(host+" .premium-map-control-explore").click();
   assert.equal(await page.locator(host).evaluate(el=>getComputedStyle(el).touchAction),"pan-y");
   await page.locator(host+" .premium-map-control-reset").click();
   assert.equal(await page.locator(host).evaluate(el=>el.dataset.manual),"false","Reset should recenter real route");
   assert.equal(await page.locator(host).evaluate(el=>+el.dataset.zoom),baseline.zoom);
   const overview=await page.evaluate(()=>{
     const temp=document.createElement("section");temp.style.width="340px";
     document.body.append(temp);
     const ok=window.TrasladosPremiumMap.overview(temp,
       [[-34.835,-56.028],[-34.895,-56.185]],{height:340});
     const names=[...temp.querySelectorAll(".premium-map-overlays text")].map(x=>x.textContent);
     const falseRoute=temp.querySelectorAll(".premium-map-route").length;
     const overlay=temp.querySelector(".premium-map-view-label")?.textContent;
     temp.remove();
     return {ok,names,falseRoute,overlay};
   });
   assert(overview.ok&&overview.names.includes("O")&&overview.names.includes("D"),
     "Pending route should show both real positions: "+JSON.stringify(overview));
   assert.equal(overview.falseRoute,0,"Never invent a road while calculation is pending");
   const metrics=await page.evaluate(()=>({
    horizontalOverflow:document.documentElement.scrollWidth-innerWidth,
    pageCanScroll:document.documentElement.scrollHeight>innerHeight+150,
    controls:document.querySelectorAll(".premium-route-canvas .premium-map-control").length,
    bookingButton:!!document.querySelector("#urgent-send"),
    origin:!!document.querySelector("#origin-picker"),destination:!!document.querySelector("#destination-picker"),
    buttonPanelWidth:document.querySelector(".premium-route-canvas .premium-map-controls")?.getBoundingClientRect().width,
    legend:document.querySelector(".premium-route-canvas .premium-map-view-label")?.textContent,
    instructionsHidden:document.querySelector(".premium-route-canvas .premium-map-gesture-hint")?.hidden,
    controlsNearBottom:(()=>{
      const panel=document.querySelector(".premium-route-canvas .premium-map-controls");
      const map=document.querySelector(".premium-route-canvas .premium-real-map");
      if(!panel||!map)return false;
      const gap=map.getBoundingClientRect().bottom-panel.getBoundingClientRect().bottom;
      return gap>=4&&gap<=43;
    })(),
    controlsBottomOffset:(()=>{
      const panel=document.querySelector(".premium-route-canvas .premium-map-controls");
      const map=document.querySelector(".premium-route-canvas .premium-real-map");
      return Math.round(map.getBoundingClientRect().bottom-panel.getBoundingClientRect().bottom);
    })(),
    tilesCoverMap:(()=>{
      const map=document.querySelector(".premium-route-canvas .premium-real-map");
      const tiles=[...map.querySelectorAll(".premium-map-tiles img")];
      if(!tiles.length)return false;
      const minX=Math.min(...tiles.map(i=>parseFloat(i.style.left)));
      const maxX=Math.max(...tiles.map(i=>parseFloat(i.style.left)+256));
      const minY=Math.min(...tiles.map(i=>parseFloat(i.style.top)));
      const maxY=Math.max(...tiles.map(i=>parseFloat(i.style.top)+256));
      return minX<=0&&maxX>=map.clientWidth&&minY<=0&&maxY>=map.clientHeight;
    })(),
    viewportTileCount:document.querySelectorAll(".premium-route-canvas .premium-map-tiles img").length
   }));
   assert(metrics.horizontalOverflow<=3,"Horizontal overflow: "+JSON.stringify(metrics));
   assert(metrics.pageCanScroll,"Page must remain vertically scrollable");
   assert(metrics.bookingButton&&metrics.origin&&metrics.destination,"Booking engines / controls lost");
   assert.equal(metrics.controls,4);
   assert(metrics.buttonPanelWidth<=60,"Floating map controls too large: "+JSON.stringify(metrics));
   assert.equal(metrics.instructionsHidden,true,"Avoid permanent help overlay covering cartography");
   assert(metrics.legend,"Missing compact verified map context badge");
   assert(metrics.controlsNearBottom,"Controls must float at the bottom-right, not over the airport: "+JSON.stringify(metrics));
   assert(metrics.tilesCoverMap,"Viewport tile coverage incomplete: "+JSON.stringify(metrics));
   assert.equal(writes,0,"Browser map test must be read-only");
   await page.screenshot({path:path.join(output,"map-close-"+width+".png"),fullPage:true});
   console.log("PASS interactive reference map",width,JSON.stringify({baseline,zoomed,after,overview,metrics}));
   assert.deepEqual(errors,[],"Uncaught browser JS errors");
   await ctx.close();
  }
 }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(e=>{console.error(e);process.exitCode=1});
