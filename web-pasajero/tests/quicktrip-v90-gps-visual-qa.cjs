"use strict";
/* QA only: v89 permission harness adapted to v90 IDE+OSRM CSP, with
 * deterministic location and route mocks; visual viewport hit-testing.
 * No device GPS, no real Supabase, no source/engine changes.
 */
const fs=require("node:fs"),path=require("node:path");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const out=process.env.QA_DIR||"quicktrip-v90-pilot-evidence/gps-visual";
fs.mkdirSync(out,{recursive:true});
const base="http://127.0.0.1:8196/web-pasajero/";
const report={state:"RUNNING",cases:[],shots:[],pageErrors:[],forbidden:[],blockedPublicReads:0,scope:"QA only"};
const check=(name,pass,detail)=>{
 report.cases.push({name,pass:Boolean(pass),detail:detail===undefined?null:detail});
 if(!pass)throw Error("ASSERT "+name+" "+JSON.stringify(detail));
};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const mockPlace=(text,lat,lng)=>({text,lat,lng,department:"Montevideo"});
const publicReadOnly=(u)=>u.hostname==="zetaudvvutlouiqxopvg.supabase.co"&&
  ["/rest/v1/rpc/public_driver_availability_v1","/functions/v1/pickup-eta"].includes(u.pathname);
let server,browser;
(async()=>{
 try{
  server=spawn("python3",["-m","http.server","8196","--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
  for(let i=0;i<60;i++){try{if((await fetch(base+"index.html")).ok)break;}catch{}if(i===59)throw Error("Local GPS test server unavailable");await sleep(150);}
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/chromium",args:["--no-sandbox","--disable-dev-shm-usage",
   "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1"]});
  for(const width of [360,393]){
   for(const mode of ["granted","denied","prompt"]){
    const ctx=await browser.newContext({viewport:{width,height:width===360?780:852},isMobile:true,hasTouch:true,
     serviceWorkers:"block",reducedMotion:"reduce",geolocation:{latitude:-34.85,longitude:-56.05,accuracy:25},
     userAgent:"Mozilla/5.0 (Linux; Android 16; SM-S918B) AppleWebKit/537.36 Chrome/145.0 Mobile Safari/537.36"});
    if(mode==="granted")await ctx.grantPermissions(["geolocation"],{origin:base});
    if(mode==="denied")await ctx.grantPermissions([],{origin:base});
    await ctx.addInitScript((isPrompt)=>{
     window.__qtGPSCalls=0;
     const g=navigator.geolocation,orig=g.getCurrentPosition.bind(g);
     Object.defineProperty(g,"getCurrentPosition",{configurable:true,value:(...args)=>{
      window.__qtGPSCalls++;return orig(...args);
     }});
     if(isPrompt){
      const real=navigator.permissions.query.bind(navigator.permissions);
      Object.defineProperty(navigator.permissions,"query",{configurable:true,value:(opts)=>
       opts?.name==="geolocation"?Promise.resolve({state:"prompt"}):real(opts)});
     }
    },mode==="prompt");
    let reverse=0,osrm=0;
    await ctx.route("**/*",route=>{
     const u=new URL(route.request().url());
     if(u.hostname==="127.0.0.1"&&u.port==="8196")return route.continue();
     // The QA road map may open here. Never contact OSM from CI.
     if(u.hostname==="tile.openstreetmap.org")return route.fulfill({
      status:200,contentType:"image/png",
      body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")
     });
     if(u.hostname==="direcciones.ide.uy"&&u.pathname==="/api/v1/geocode/reverse"){
      reverse++;return route.fulfill({status:200,headers:{"Access-Control-Allow-Origin":"*","Content-Type":"application/json"},
       body:JSON.stringify([{address:"Calle Ficticia QA 123, Canelones",departamento:"Canelones"}])});
     }
     if(u.hostname==="router.project-osrm.org"&&u.pathname.startsWith("/route/v1/driving/")){
      osrm++;const ps=decodeURIComponent(u.pathname).split("/").pop().split(";").map(s=>s.split(",").map(Number));
      return route.fulfill({status:200,headers:{"Access-Control-Allow-Origin":"*","Content-Type":"application/json"},
       body:JSON.stringify({code:"Ok",routes:[{distance:8600,duration:875,
        geometry:{type:"LineString",coordinates:[ps[0],ps[ps.length-1]]}}],
        waypoints:ps.map(p=>({location:p,name:"Ficticio",distance:0}))})});
     }
     if(publicReadOnly(u)){report.blockedPublicReads++;return route.abort("blockedbyclient");}
     report.forbidden.push({host:u.hostname,path:u.pathname});return route.abort("blockedbyclient");
    });
    const page=await ctx.newPage();
    page.on("pageerror",e=>report.pageErrors.push({width,mode,message:e.message}));
    await page.goto(base+"index.html",{waitUntil:"load",timeout:22000});
    await page.locator('#view-inicio [data-service="now"]').click();
    await page.locator('#origin-picker .location-use-current[data-qt-gps-label="Mi ubicación"]').waitFor({state:"visible",timeout:15000});
    const before=await page.evaluate(()=>({
     calls:window.__qtGPSCalls||0,destination:record?.destination?.text||"",
     csp:document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"",
     button:document.querySelector("#origin-picker .location-use-current")?.getAttribute("aria-label")
    }));
    check(mode+" "+width+" no GPS before user interaction",before.calls===0,before);
    check(mode+" "+width+" v90 CSP and GPS action accessible",
     before.csp.includes("connect-src https://direcciones.ide.uy https://router.project-osrm.org https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/public_driver_availability_v1 https://zetaudvvutlouiqxopvg.supabase.co/functions/v1/pickup-eta;")&&
     before.csp.includes("form-action 'none'")&&before.button==="Usar mi ubicación actual como origen",before);
    if(mode==="granted"){
     await page.evaluate(dest=>destPicker.persist(dest),mockPlace("Destino QA independiente",-34.927,-56.159));
    }
    // Reproduce the Samsung regression: an origin was already selected.
    await page.evaluate(()=>originPicker.persist({
      text:"Origen guardado QA (no GPS)",lat:-34.86,lng:-56.052,
      department:"Canelones",source:"manual"
    }));
    const directGps=page.locator("#origin-picker .qt-v90-selected-gps");
    await directGps.waitFor({state:"visible",timeout:7000});
    const preselected=await page.evaluate(()=>({
      selected:document.querySelector("#origin-picker .location-selected")?.hidden===false,
      editorHidden:document.querySelector("#origin-picker .location-controls")?.hidden===true,
      current:record.origin?.text, calls:window.__qtGPSCalls||0
    }));
    check(mode+" "+width+" selected origin keeps direct native GPS control",
      preselected.selected&&preselected.editorHidden&&preselected.calls===0&&
      preselected.current==="Origen guardado QA (no GPS)",preselected);
    await directGps.click();
    if(mode==="granted"){
     await page.waitForFunction(()=>record?.origin?.text==="Calle Ficticia QA 123, Canelones",null,{timeout:15000});
     await page.waitForFunction(()=>document.querySelector(".qtc-app")?.dataset.qtV90Quote==="verified",null,{timeout:15000});
     const state=await page.evaluate(()=>({origin:record.origin?.text,destination:record.destination?.text,
      calls:window.__qtGPSCalls,quote:document.querySelector(".qtc-app")?.dataset.qtV90Quote,
      fare:document.querySelector(".qt-v90-fare")?.textContent}));
     check(mode+" "+width+" GPS permission mock changes only origin and recomputes v90",state.calls===1&&reverse===1&&
      state.origin==="Calle Ficticia QA 123, Canelones"&&state.destination==="Destino QA independiente"&&
      state.quote==="verified"&&state.fare.includes("344")&&osrm>0,{...state,reverse,osrm});
    }else{
     await page.locator("#origin-picker .gps-permission-help:not([hidden])").waitFor({state:"visible",timeout:11000});
     await page.waitForFunction(()=>document.querySelector("#origin-picker .gps-permission-help")?.closest("details")?.open===true,
      null,{timeout:10000});
     await page.waitForFunction(m=>{
      const s=document.querySelector("#origin-picker .qt-v89-gps-status")?.textContent||"";
      return m==="denied"?s.includes("candado"):s.includes("una sola vez");
     },mode,{timeout:8000});
     const state=await page.evaluate(()=>{
      const h=document.querySelector("#origin-picker .gps-permission-help");
      return {title:h.querySelector(".gps-permission-title")?.textContent||"",
       status:document.querySelector("#origin-picker .qt-v89-gps-status")?.textContent||"",
       buttons:[...h.querySelectorAll("button")].map(x=>x.textContent.trim()),
       calls:window.__qtGPSCalls,destination:record?.destination?.text||""};
     });
     check(mode+" "+width+" permission rejection preserves selected origin and reveals help",
       await page.evaluate(()=>record?.origin?.text==="Origen guardado QA (no GPS)" &&
         document.querySelector("#origin-picker")?.classList.contains("qt-v90-selected-gps-help") &&
         document.querySelector("#origin-picker .qt-v90-selected-gps")?.getBoundingClientRect().height>=40 &&
         getComputedStyle(document.querySelector("#origin-picker .gps-permission-help")).display!=="none"));
     check(mode+" "+width+" permission guidance distinct and no IDE request",
      reverse===0&&(mode==="denied"?(state.status.includes("candado")&&/bloqueada/i.test(state.title)):
      (state.status.includes("una sola vez")&&state.buttons.some(x=>x.includes("Permitir ubicación")))),state);
    }
    check(mode+" "+width+" CTA remains disabled",await page.locator("#urgent-send").evaluate(x=>x.disabled));
    if(width===393||mode==="granted"){
     const name="gps-"+mode+"-"+width+".png";
     await page.screenshot({path:path.join(out,name),fullPage:false,animations:"disabled"});report.shots.push(name);
    }
    if(mode==="granted"){
     const selectors=['[data-qt-b-action="request"]',".qtc-schedule > summary",".qtc-contact > summary",
      ".qt-v90-map-details > summary"];
     await page.locator(".qt-v90-map-details > summary").click();
     for(let i=0;i<selectors.length;i++){
      const selector=selectors[i],loc=page.locator(selector);
      await loc.evaluate(e=>e.scrollIntoView({block:"center",behavior:"instant"}));
      await sleep(60);
      const hit=await page.evaluate(s=>{
       const e=document.querySelector(s),nav=document.querySelector(".qtc-bottom-nav");
       const b=e.getBoundingClientRect(),n=nav.getBoundingClientRect();
       const x=b.x+b.width/2,y=b.y+b.height/2;
       const found=document.elementFromPoint(x,y);
       const inViewport=y>0&&y<innerHeight;
       const clickable=found===e||e.contains(found);
       const navOccludes=y>=n.top&&y<=n.bottom;
       return {selector:s,inViewport,clickable,navOccludes,
        x:Math.round(x),y:Math.round(y),navTop:Math.round(n.top),
        scrollY:Math.round(scrollY),width:innerWidth,height:innerHeight};
      },selector);
      check("VIS "+width+" viewport clickable "+selector,hit.inViewport&&hit.clickable&&!hit.navOccludes,hit);
      const shot="visual-"+width+"-"+i+".png";
      await page.screenshot({path:path.join(out,shot),fullPage:false,animations:"disabled"});report.shots.push(shot);
     }
     check("VIS "+width+" no horizontal overflow",await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth<=2));
    }
    await ctx.close();
   }
  }
  check("GPS/VIS no real backend or other remote traffic",report.forbidden.length===0,report.forbidden);
  check("GPS/VIS no page exceptions",report.pageErrors.length===0,report.pageErrors);
  report.state="PASS";
 }catch(e){report.state="FAIL";report.error=String(e.stack||e);process.exitCode=1;}
 finally{if(browser)await browser.close();if(server)server.kill("SIGTERM");
  fs.writeFileSync(path.join(out,"GPS_VISUAL_V90_RESULT.json"),JSON.stringify(report,null,2)+"\n");
  console.log("GPS VISUAL "+report.state+" "+report.cases.filter(c=>c.pass).length+"/"+report.cases.length+
   " screenshots="+report.shots.length+" "+(report.error||""));
 }
})();