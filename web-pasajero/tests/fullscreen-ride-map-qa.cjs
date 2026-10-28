"use strict";
// Full-screen map QA on two Samsung-like viewports. Backend requests intercepted;
// NEVER write a real reservation or show an invented driver car/payment method.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),{spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const cwd=path.resolve(__dirname,"../.."),delay=ms=>new Promise(ok=>setTimeout(ok,ms));
(async()=>{
 const server=spawn("python3",["-m","http.server","8299","--bind","127.0.0.1"],{cwd,stdio:"ignore"});
 let browser;
 try{
  for(let i=0;i<80;i++){try{if((await fetch("http://127.0.0.1:8299/web-pasajero/")).ok)break}catch{}await delay(120)}
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  const dir=path.join(cwd,"artifacts/fullscreen-ride");
  fs.mkdirSync(dir,{recursive:true});
  for(const width of [390,412]){
   const ctx=await browser.newContext({viewport:{width,height:844},isMobile:true,hasTouch:true,
    reducedMotion:"reduce",serviceWorkers:"block"});
   let writes=0;
   await ctx.route("**/*",route=>{
    const r=route.request(),url=new URL(r.url());
    if(url.hostname==="127.0.0.1")return route.continue();
    if(url.hostname==="tile.openstreetmap.org")return route.fulfill({
      status:200,contentType:"image/png",
      body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")
    });
    const rpc=url.pathname.split("/").pop();
    if(r.method()==="POST"&&/create_reservation|customer_request_pickup_fixed|cancel_reservation/i.test(rpc))writes++;
    return route.fulfill({status:200,contentType:"application/json",body:'{"available":false,"reason":"qa"}'});
   });
   await ctx.addInitScript(()=>{try{sessionStorage.setItem("quicktrip.gps.welcome.session.v1","completed")}catch{}});
   const page=await ctx.newPage(),errors=[];
   page.on("pageerror",e=>errors.push(e.message));
   await page.goto("http://127.0.0.1:8299/web-pasajero/",{waitUntil:"load"});
   await page.waitForFunction(()=>!!window.QuickTripMapFocus&&!!document.querySelector(".premium-route-expand"),
     {timeout:18000});
   const state={
    origin:{lat:-34.83696,lng:-56.01638,text:"Aeropuerto Internacional de Carrasco"},
    destination:{lat:-34.827469,lng:-55.969099,text:"Shangrilá, Ciudad de la Costa"},
    stops:[],geometry:[[-34.83696,-56.01638],[-34.834,-56.004],[-34.83,-55.985],[-34.827469,-55.969099]],
    metrics:{available:true,source:"openrouteservice",distanceKm:6.6,durationMin:11,
      referenceFareUyu:264}
   };
   let open=await page.evaluate(st=>window.QuickTripMapFocus.show(st),state);
   assert.equal(open,true);
   await page.waitForSelector("#qt-focus .premium-real-map .premium-map-route",{timeout:15000});
   const detail=await page.evaluate(()=>{
    const overlay=document.querySelector("#qt-focus");
    const route=overlay.querySelector(".premium-map-route");
    return {visible:!overlay.hidden,top:overlay.getBoundingClientRect().top,
      height:overlay.getBoundingClientRect().height,width:overlay.getBoundingClientRect().width,
      fare:overlay.querySelector(".qt-focus-fare")?.textContent,
      address:overlay.querySelector(".qt-focus-address-text")?.textContent,
      distance:overlay.querySelector(".qt-focus-distance")?.textContent,
      duration:overlay.querySelector(".qt-focus-time")?.textContent,
      route:!!route,car:!!overlay.querySelector(".premium-map-overlays text")?.textContent.includes("🚘"),
      filter:getComputedStyle(overlay.querySelector(".premium-map-tiles img")).filter,
      sheetTop:overlay.querySelector(".qt-focus-sheet").getBoundingClientRect().top,
      mapBottom:overlay.querySelector(".premium-real-map").getBoundingClientRect().bottom,
      mapWidth:overlay.querySelector(".premium-real-map").getBoundingClientRect().width,
      tilePosition:getComputedStyle(overlay.querySelector(".premium-map-tiles")).position,
      imagePosition:getComputedStyle(overlay.querySelector(".premium-map-tiles img")).position,
      overlayPosition:getComputedStyle(overlay.querySelector(".premium-map-overlays")).position,
      svgWidth:overlay.querySelector(".premium-map-overlays").getBoundingClientRect().width,
      svgHeight:overlay.querySelector(".premium-map-overlays").getBoundingClientRect().height,
      paintedRoadBounds:(()=>{
        const el=overlay.querySelector(".premium-map-route");const bbox=el?.getBBox();
        const svg=overlay.querySelector(".premium-map-overlays");
        return bbox?{x:bbox.x,y:bbox.y,w:bbox.width,h:bbox.height,
          visible:bbox.width>0&&bbox.height>0&&bbox.x+bbox.width>0&&
            bbox.x<svg.clientWidth&&bbox.y+bbox.height>0&&bbox.y<svg.clientHeight}:null;
      })(),
      viewportTileCoverage:(()=>{
        const map=overlay.querySelector(".premium-real-map");
        const tiles=[...map.querySelectorAll(".premium-map-tiles img")];
        return tiles.length>=4&&
          Math.min(...tiles.map(i=>parseFloat(i.style.left)))<=0&&
          Math.max(...tiles.map(i=>parseFloat(i.style.left)+256))>=map.clientWidth&&
          Math.min(...tiles.map(i=>parseFloat(i.style.top)))<=0&&
          Math.max(...tiles.map(i=>parseFloat(i.style.top)+256))>=map.clientHeight;
      })(),
      horizontalOverflow:document.documentElement.scrollWidth-innerWidth
    };
   });
   assert(detail.visible&&detail.top===0&&detail.height>=840,
     "Full screen required "+JSON.stringify(detail));
   assert(detail.route&&detail.filter!=="none","Must show a dark, real road route");
   assert.equal(detail.tilePosition,"absolute","Tiles are missing the required map positioning");
   assert.equal(detail.imagePosition,"absolute","Tile images were stacked in document flow");
   assert.equal(detail.overlayPosition,"absolute","Route SVG was not positioned over the tiles");
   assert(Math.abs(detail.svgWidth-detail.mapWidth)<2,"SVG map did not fill the viewport");
   assert(detail.svgHeight>350,"SVG map height is truncated");
   assert(detail.mapBottom>detail.sheetTop-55&&detail.mapBottom<detail.sheetTop+55,
     "Map height must end under the bottom sheet, not leave uncovered viewport");
   assert(detail.paintedRoadBounds?.visible,"The verified road has no visible SVG bounds");
   assert(detail.viewportTileCoverage,"Tile images do not cover the map viewport");
   assert.match(detail.fare,/264/);
   assert.match(detail.address,/Shangrilá/);
   assert.equal(detail.distance,"6,6 km");assert.equal(detail.duration,"11 min");
   assert(!detail.car,"Never display a fake car before accepted GPS");
   assert(detail.sheetTop>380&&detail.sheetTop<700,"Bottom sheet absent");
   assert(detail.horizontalOverflow<=3);
   await page.screenshot({path:path.join(dir,"quicktrip-map-"+width+".png"),
     animations:"disabled",fullPage:true});
   await page.locator("#qt-focus .qt-focus-back").click();
   assert.equal(await page.locator("#qt-focus").evaluate(el=>el.hidden),true,"Back must return to existing form");
   await page.evaluate(st=>window.QuickTripMapFocus.show({
    ...st,geometry:null,metrics:null
   }),state);
   const missing=await page.locator("#qt-focus").evaluate(el=>({
    disabled:el.querySelector(".qt-focus-submit").disabled,
    routeLines:el.querySelectorAll(".premium-map-route").length,
    retry:el.querySelector(".qt-focus-retry").hidden,
    origin:el.querySelector(".qt-focus-origin").textContent
   }));
   assert(missing.disabled&&missing.routeLines===0&&!missing.retry,
     "Never invent road or fare "+JSON.stringify(missing));
   await page.locator("#qt-focus .qt-focus-back").click();
   assert.equal(writes,0,"No actual booking writes in QA browser");
   assert.deepEqual(errors,[],"Unexpected JS errors");
   console.log("PASS FULLSCREEN QUICK TRIP",width,JSON.stringify({detail,missing}));
   await ctx.close();
  }
 }finally{if(browser)await browser.close();server.kill("SIGTERM");}
})().catch(e=>{console.error(e);process.exitCode=1});
