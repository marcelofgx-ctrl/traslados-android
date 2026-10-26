"use strict";
/* QA-only Playwright extension for Quick Trip v90.
 * NO device GPS, NO real backend, NO public routing. All calls to OSRM
 * are intercepted and fulfilled synthetically; all other external traffic
 * is rejected. No source or stable engine is modified. */
const fs=require("node:fs"), path=require("node:path"), assert=require("node:assert/strict");
const {spawn}=require("node:child_process"), {chromium}=require("playwright-core");
const out=process.env.QA_DIR||"quicktrip-v90-pilot-evidence/negative";
fs.mkdirSync(out,{recursive:true});
const port=8191, url="http://127.0.0.1:"+port+"/web-pasajero/";
const report={result:"RUNNING",sourceBase:"8d882e63e1a4e779f956868af64c9e6158c4c0ac",
  cases:[],screenshots:[],pageErrors:[],unexpectedExternal:[],
  usedSyntheticCoordinates:true,realBookingRequests:0,realGpsRequests:0};
const check=(name,condition,detail)=>{
  report.cases.push({name,pass:!!condition,detail:detail===undefined?null:detail});
  assert.ok(condition,name+" "+JSON.stringify(detail));
};
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const mockPlace=(label,lat,lng)=>({text:label,lat,lng,department:"Montevideo"});
const stop1=mockPlace("Parada ficticia A",-34.910,-56.153);
const stop2=mockPlace("Parada ficticia B",-34.916,-56.161);
const stop3=mockPlace("Parada ficticia C",-34.918,-56.167);
const stop4=mockPlace("Parada ficticia D",-34.920,-56.170);
const stop5=mockPlace("Parada ficticia E",-34.922,-56.172);
const headers={"Access-Control-Allow-Origin":"*","Content-Type":"application/json"};
(async()=>{
 const server=spawn("python3",["-m","http.server",String(port),"--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
 let browser;
 try{
  for(let i=0;i<60;i++){
   try{if((await fetch(url+"index.html")).ok)break}catch{}
   if(i===59)throw Error("Local QA HTTP server unavailable");
   await sleep(150);
  }
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/chromium",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  for(const width of [393,360]){
   const ctx=await browser.newContext({
    viewport:{width,height:width===393?852:780},deviceScaleFactor:2,
    isMobile:true,hasTouch:true,serviceWorkers:"block",reducedMotion:"reduce",
    userAgent:"Mozilla/5.0 (Linux; Android 16; SM-S918B) AppleWebKit/537.36 Chrome/145.0 Mobile Safari/537.36"
   });
   let mode="ok",osrm=0;
   await ctx.route("**/*",route=>{
    const u=new URL(route.request().url());
    if(u.hostname==="127.0.0.1"&&u.port===String(port))
      return route.continue();
    if(u.hostname==="router.project-osrm.org"&&u.pathname.startsWith("/route/v1/driving/")){
      osrm++;
      if(mode==="offline")return route.abort("internetdisconnected");
      if(mode==="no-route")
        return route.fulfill({status:200,headers,body:JSON.stringify({code:"NoRoute",routes:[],waypoints:[]})});
      const coords=decodeURIComponent(u.pathname).split("/").pop().split(";").map(s=>s.split(",").map(Number));
      const first=coords[0],last=coords[coords.length-1];
      if(mode==="invalid"){
        return route.fulfill({status:200,headers,body:JSON.stringify({
          code:"Ok",routes:[{distance:-5,duration:0}],waypoints:coords.map(c=>({location:c}))})});
      }
      const distance=coords.length===2?8600:coords.length===3?12500:18000;
      const seconds=coords.length===2?875:coords.length===3?1200:1500;
      const geo=[first,...coords.slice(1,-1),[(first[0]+last[0])/2,(first[1]+last[1])/2],last];
      return route.fulfill({status:200,headers,body:JSON.stringify({
       code:"Ok",routes:[{distance,duration:seconds,geometry:{type:"LineString",coordinates:geo}}],
       waypoints:coords.map(c=>({location:c,name:"Lugar ficticio",distance:0}))
      })});
    }
    if(u.hostname.includes("supabase.co")||u.hostname.includes("workers.dev"))report.realBookingRequests++;
    report.unexpectedExternal.push({host:u.hostname,path:u.pathname});
    return route.abort("blockedbyclient");
   });
   const page=await ctx.newPage();
   page.on("pageerror",e=>report.pageErrors.push({width,message:e.message}));
   await page.goto(url+"index.html",{waitUntil:"load",timeout:22000});
   await page.locator('#view-inicio [data-service="now"]').click();
   await page.locator('.qtc-app[data-qt-v90="sandbox"]').waitFor({state:"visible",timeout:15000});
   const safety=await page.evaluate(()=>{
     const m=document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"";
     return {readOnly:document.querySelector('meta[name="traslados-preview"]')?.content==="read-only",
       csp:m.match(/connect-src\s+([^;]+)/)?.[1].trim(),formNone:m.includes("form-action 'none'"),
       navCount:document.querySelectorAll('#view-inicio [data-qt-service]').length,
       sendDisabled:document.querySelector("#urgent-send")?.disabled,
       v90:document.body.classList.contains("qt-v90-qa")
     };
   });
   check(width+" isolated v90 / CSP / no submission",safety.readOnly&&safety.formNone&&
     safety.csp==="https://direcciones.ide.uy https://router.project-osrm.org"&&
     safety.navCount===3&&safety.sendDisabled&&safety.v90,safety);
   await page.evaluate(([a,b])=>{originPicker.persist(a);destPicker.persist(b)},[
     mockPlace("Origen QA",-34.902,-56.165),mockPlace("Destino QA",-34.926,-56.159)]);
   await page.waitForFunction(()=>document.querySelector(".qtc-app")?.dataset.qtV90Quote==="verified"
     && document.querySelector(".qt-v90-km")?.textContent.trim()==="8,6 km",null,{timeout:14000});
   let current=await page.evaluate(()=>({
     distance:document.querySelector(".qt-v90-km")?.textContent.trim(),
     duration:document.querySelector(".qt-v90-min")?.textContent.trim(),
     fare:document.querySelector(".qt-v90-fare")?.textContent.trim(),
     priceDisclaimer:document.querySelector(".qt-v90-disclaimer")?.textContent,
     mapFolded:!document.querySelector(".qt-v90-map-details")?.open,
     noOverflow:document.documentElement.scrollWidth-innerWidth<=2,
     fixture:document.querySelector(".qt-v90-driver-demo")?.textContent
   }));
   check(width+" valid 8.6km 15min 344UYU, map folded",current.distance==="8,6 km"&&
     current.duration==="15 min"&&current.fare.includes("344")&&
     current.mapFolded&&current.noOverflow&&
     current.priceDisclaimer.includes("NO es cotización oficial"),current);
   check(width+" driver fictitious disclosure",current.fixture.includes("FICTICIO")&&current.fixture.includes("7 min"),current.fixture);
   await page.screenshot({path:path.join(out,"01-verified-"+width+".png"),fullPage:true,animations:"disabled"});
   report.screenshots.push("01-verified-"+width+".png");
   await page.locator(".qt-v90-scenario").click();
   check(width+" driver unavailable is expressly fictitious",
     await page.locator('.qt-v90-driver-demo[data-scenario="unavailable"]').count()===1);
   await page.locator(".qt-v90-scenario").click();
   check(width+" driver availability restored",
     await page.locator('.qt-v90-driver-demo[data-scenario="available"]').count()===1);
   // Route with one fictitious intermediate stop. Adapter recalculates using
   // the existing, unmodified route-updated event and original record object.
   await page.evaluate(stop=>{
     record.stops.splice(0,record.stops.length,stop);
     document.dispatchEvent(new Event("traslados:route-updated"));
   },stop1);
   await page.waitForFunction(()=>document.querySelector(".qtc-app")?.dataset.qtV90Quote==="verified"
     && document.querySelector(".qt-v90-km")?.textContent.trim()==="12,5 km",null,{timeout:14000});
   current=await page.evaluate(()=>({
     km:document.querySelector(".qt-v90-km")?.textContent,
     duration:document.querySelector(".qt-v90-min")?.textContent,
     fare:document.querySelector(".qt-v90-fare")?.textContent
   }));
   check(width+" intermediate stop recomputes route and price at 40UYU/km",
     current.km==="12,5 km"&&current.duration==="20 min"&&current.fare.includes("500"),current);
   await page.screenshot({path:path.join(out,"02-with-stop-"+width+".png"),fullPage:true,animations:"disabled"});
   report.screenshots.push("02-with-stop-"+width+".png");
   await page.evaluate(stops=>{
     record.stops.splice(0,record.stops.length,...stops);
     document.dispatchEvent(new Event("traslados:route-updated"));
   },[stop1,stop2]);
   await page.waitForFunction(()=>document.querySelector(".qtc-app")?.dataset.qtV90Quote==="verified"
      && document.querySelector(".qt-v90-km")?.textContent.trim()==="18,0 km",null,{timeout:14000});
   check(width+" two-stop case is recalculated to 18km and 720UYU",
     await page.locator(".qt-v90-fare").textContent().then(x=>x.includes("720")));
   // Adapter contract supports up to 4 stops: beyond that it must fail closed
   // without calling OSRM and without displaying previous quote.
   const beforeLimit=osrm;
   await page.evaluate(stops=>{
     record.stops.splice(0,record.stops.length,...stops);
     document.dispatchEvent(new Event("traslados:route-updated"));
   },[stop1,stop2,stop3,stop4,stop5]);
   await page.waitForFunction(()=>document.querySelector(".qtc-app")?.dataset.qtV90Quote==="pending"
     && document.querySelector(".qt-v90-quote-results")?.hidden,null,{timeout:12000});
   check(width+" over four QA stops fails closed without OSRM call",
     osrm===beforeLimit,{osrm,beforeLimit});
   check(width+" no fabricated quote after invalid stop count",
     await page.locator(".qt-v90-map-details").evaluate(e=>e.hidden));
   // Recover from invalid input by removing stops. This must not reuse stale quote.
   await page.evaluate(()=>{
     record.stops.splice(0,record.stops.length);
     document.dispatchEvent(new Event("traslados:route-updated"));
   });
   await page.waitForFunction(()=>document.querySelector(".qtc-app")?.dataset.qtV90Quote==="verified"
      && document.querySelector(".qt-v90-km")?.textContent.trim()==="8,6 km",null,{timeout:14000});
   check(width+" returns to original fare after stop removal",
     await page.locator(".qt-v90-fare").textContent().then(x=>x.includes("344")));
   // NoRoute, malformed response, network disconnection: none can retain last
   // valid km, minutes, price, or drawing.
   for(const [failMode,newDest] of [
     ["no-route",mockPlace("Sin ruta QA",-34.924,-56.145)],
     ["invalid",mockPlace("Respuesta inválida QA",-34.921,-56.146)],
     ["offline",mockPlace("Sin internet QA",-34.920,-56.147)]
   ]){
     mode=failMode;
     await page.evaluate(dest=>destPicker.persist(dest),newDest);
     await page.waitForFunction(()=>document.querySelector(".qt-v90-quote-message")?.textContent.includes("Sin ruta vial verificable"),null,{timeout:14000});
     const state=await page.evaluate(()=>({
       hidden:document.querySelector(".qt-v90-quote-results")?.hidden,
       mapHidden:document.querySelector(".qt-v90-map-details")?.hidden,
       disabled:document.querySelector("#urgent-send")?.disabled,
       state:document.querySelector(".qtc-app")?.dataset.qtV90Quote,
       message:document.querySelector(".qt-v90-quote-message")?.textContent,
       fakeDriver:document.querySelector(".qt-v90-driver-demo")?.textContent.includes("FICTICIO")
     }));
     check(width+" "+failMode+" hides stale price, route and blocks booking",
       state.hidden&&state.mapHidden&&state.disabled&&
       state.state==="pending"&&state.fakeDriver,state);
     if(failMode==="offline"){
       await page.screenshot({path:path.join(out,"03-disconnected-"+width+".png"),fullPage:true,animations:"disabled"});
       report.screenshots.push("03-disconnected-"+width+".png");
     }
   }
   mode="ok";
   await page.evaluate(dest=>destPicker.persist(dest),mockPlace("Recuperación QA",-34.919,-56.148));
   await page.waitForFunction(()=>document.querySelector(".qtc-app")?.dataset.qtV90Quote==="verified"
      && document.querySelector(".qt-v90-km")?.textContent.trim()==="8,6 km",null,{timeout:14000});
   check(width+" reconnect recovers strictly simulated quote",
     await page.locator(".qt-v90-fare").textContent().then(x=>x.includes("344")));
   check(width+" only synthetic OSRM route mocks called",osrm>=7,{osrm});
   await ctx.close();
  }
  check("no real reservations or Supabase/Worker access",report.realBookingRequests===0,report.realBookingRequests);
  check("no other external requests",report.unexpectedExternal.length===0,report.unexpectedExternal);
  check("no browser exceptions",report.pageErrors.length===0,report.pageErrors);
  report.result="PASS";
  console.log("PASS v90 acceleration: "+report.cases.length+" checks, "+report.screenshots.length+" screenshots; no real backend");
 }catch(e){
  report.result="FAIL";report.error=String(e.stack||e);console.error("FAIL v90 acceleration",report.error);
  process.exitCode=1;
 }finally{
  if(browser)await browser.close();
  server.kill("SIGTERM");
  fs.writeFileSync(path.join(out,"QA_V90_PILOT_RESULT.json"),JSON.stringify(report,null,2)+"\n");
 }
})();
