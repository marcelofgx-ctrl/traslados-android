"use strict";
// Samsung 390/412px regression for the real inline Tu viaje map.
// Full network interception: never read or write live customer records.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const cwd=path.resolve(__dirname,"../.."),sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const server=spawn("python3",["-m","http.server","8295","--bind","127.0.0.1"],{cwd,stdio:"ignore"});
 let browser;
 try{
  let ready=false;
  for(let i=0;i<80;i++){try{if((await fetch("http://127.0.0.1:8295/web-pasajero/")).ok){ready=true;break}}catch{}await sleep(120)}
  assert(ready,"Local QA passenger page unavailable");
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  const out=path.join(cwd,"artifacts/inline-map-tiles");fs.mkdirSync(out,{recursive:true});
  for(const width of [390,412]){
   const ctx=await browser.newContext({viewport:{width,height:844},isMobile:true,
     hasTouch:true,serviceWorkers:"block",reducedMotion:"reduce"});
   let writes=0;
   await ctx.route("**/*",route=>{
    const req=route.request(),url=new URL(req.url());
    if(url.hostname==="127.0.0.1")return route.continue();
    if(url.hostname==="tile.openstreetmap.org")return route.fulfill({
     status:200,contentType:"image/png",
     body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
    const name=url.pathname.split("/").pop()||"";
    if(req.method()==="POST"&&/create_reservation|customer_request_pickup_fixed|cancel_reservation|customer_quote_decision/i.test(name))writes++;
    return route.abort();
   });
   const page=await ctx.newPage(),errors=[];page.on("pageerror",e=>errors.push(e.message));
   await page.goto("http://127.0.0.1:8295/web-pasajero/?ui=clean",{waitUntil:"load"});
   await page.waitForFunction(()=>document.body.classList.contains("qt-clean")&&
     !!document.querySelector("#view-viaje .premium-route-cartography")&&
     !!window.TrasladosPremiumMap,{timeout:20000});
   assert(await page.evaluate(()=>window.TrasladosPremiumMap.render(
     document.querySelector("#view-viaje .premium-route-cartography"),
     [[-34.815,-56.053],[-34.822,-56.045],[-34.829,-56.035],[-34.834,-56.025]],
     {height:340})),"Verified fixture route should render");
   const result=await page.evaluate(()=>{
    const host=document.querySelector("#view-viaje .premium-route-cartography");
    const map=host.querySelector(".premium-real-map"),tiles=map?.querySelector(".premium-map-tiles");
    const imgs=[...(tiles?.querySelectorAll("img")||[])],svg=map?.querySelector(".premium-map-overlays");
    const road=svg?.querySelector(".premium-map-route");
    if(!map||!tiles||imgs.length<4||!svg||!road)return {missing:true,count:imgs.length};
    const m=map.getBoundingClientRect(),s=svg.getBoundingClientRect(),t=tiles.getBoundingClientRect();
    const boxes=imgs.map(i=>({r:i.getBoundingClientRect(),left:parseFloat(i.style.left),
      top:parseFloat(i.style.top),pos:getComputedStyle(i).position}));
    const bbox=road.getBBox();
    const bounds={left:Math.min(...boxes.map(b=>b.r.left)),right:Math.max(...boxes.map(b=>b.r.right)),
      top:Math.min(...boxes.map(b=>b.r.top)),bottom:Math.max(...boxes.map(b=>b.r.bottom))};
    return {tilePosition:getComputedStyle(tiles).position,svgPosition:getComputedStyle(svg).position,
      imagePositionOk:boxes.every(b=>b.pos==="absolute"),
      imageOffsetOk:boxes.every(b=>Math.abs(b.r.left-m.left-b.left)<2&&
        Math.abs(b.r.top-m.top-b.top)<2&&b.r.width===256&&b.r.height===256),
      tilesCover:bounds.left<=m.left+1&&bounds.right>=m.right-1&&
        bounds.top<=m.top+1&&bounds.bottom>=m.bottom-1,
      svgFills:Math.abs(m.width-s.width)<2&&Math.abs(m.height-s.height)<2&&
        Math.abs(t.width-m.width)<2&&Math.abs(t.height-m.height)<2,
      height:m.height,stroke:getComputedStyle(road).stroke,
      roadVisible:bbox.width>0&&bbox.height>0&&bbox.x+bbox.width>0&&
        bbox.y+bbox.height>0&&bbox.x<s.width&&bbox.y<s.height,
      roadCount:svg.querySelectorAll(".premium-map-route").length,
      noOverflow:document.documentElement.scrollWidth-innerWidth<=3,
      scrollable:document.documentElement.scrollHeight>innerHeight+100,
      originalBooking:!!document.querySelector("#urgent-send"),
      originalRouteCard:!!host.closest(".premium-route-canvas")
    };
   });
   assert(!result.missing,"Inline map missing: "+JSON.stringify(result));
   assert.equal(result.tilePosition,"absolute");
   assert.equal(result.svgPosition,"absolute");
   assert(result.imagePositionOk&&result.imageOffsetOk&&result.tilesCover,
    "OSM mosaic is unpositioned or incomplete: "+JSON.stringify(result));
   assert(result.svgFills&&result.height>=339&&result.roadVisible&&
     result.stroke!=="none"&&result.roadCount===1,
     "Verified road not visible above tiles: "+JSON.stringify(result));
   assert(result.noOverflow&&result.scrollable&&result.originalBooking&&result.originalRouteCard,
     "Passenger booking UI regressed: "+JSON.stringify(result));
   await page.locator("#view-viaje .premium-route-cartography").scrollIntoViewIfNeeded();
   await page.screenshot({path:path.join(out,"inline-route-"+width+".png"),fullPage:true,animations:"disabled"});
   const negative=await page.evaluate(()=>{
    const host=document.querySelector("#view-viaje .premium-route-cartography");
    window.TrasladosPremiumMap.overview(host,[[-34.815,-56.053],[-34.834,-56.025]],{height:340});
    return {lines:host.querySelectorAll(".premium-map-route").length,
      markers:[...host.querySelectorAll(".premium-map-overlays text")].map(n=>n.textContent)};
   });
   assert(negative.lines===0&&negative.markers.includes("O")&&negative.markers.includes("D"),
      "No verified route: do not draw an invented line");
   assert.equal(writes,0,"Unexpected reservation mutation");
   assert.deepEqual(errors,[],"Browser JavaScript error");
   console.log("PASS inline map tiles and road",width,JSON.stringify(result));
   await ctx.close();
  }
 }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(e=>{console.error(e);process.exitCode=1});
