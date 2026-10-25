"use strict";
/* Browser GPS-reverse regression: strictly mock coordinates and external IDE.
   No real location, no calls to Supabase nor other external origins. */
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const output=process.env.QA_DIR||"quicktrip-v88-gps-qa-evidence";
fs.mkdirSync(output,{recursive:true});
const base="http://127.0.0.1:8178/web-pasajero/";
const results={result:"IN_PROGRESS",tests:[],screenshots:[],external:[],errors:[],note:"All GPS coordinates and addresses in this report are synthetic QA fixtures"};
function ok(name,pass,data){results.tests.push({name,pass:!!pass,data});assert(pass,name+" "+JSON.stringify(data))}
(async()=>{
 const server=spawn("python3",["-m","http.server","8178","--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
 let browser=null;
 try{
  for(let i=0;i<50;i++){try{if((await fetch(base+"index.html")).ok)break}catch{}if(i===49)throw Error("Local HTTP server failed");await new Promise(resolve=>setTimeout(resolve,150))}
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",args:["--no-sandbox","--disable-dev-shm-usage"]});
  for(const width of [393,360]){
   const ctx=await browser.newContext({
    viewport:{width,height:width===393?852:780},deviceScaleFactor:2,
    isMobile:true,hasTouch:true,serviceWorkers:"block",
    geolocation:{latitude:-34.85,longitude:-56.05,accuracy:25},
    permissions:["geolocation"],reducedMotion:"reduce"
   });
   let reverseCalls=0, blockedExternal=0, supabaseCalls=0;
   await ctx.route("**/*",route=>{
    const u=new URL(route.request().url());
    if(u.hostname==="127.0.0.1"&&u.port==="8178")return route.continue();
    if(u.hostname==="direcciones.ide.uy"&&u.pathname==="/api/v1/geocode/reverse"){
      reverseCalls++;
      return route.fulfill({
       status:200,headers:{"Access-Control-Allow-Origin":"*","Content-Type":"application/json"},
       body:JSON.stringify([{address:"Calle de Prueba QA 123, Canelones",departamento:"Canelones"}])
      });
    }
    if(u.hostname.includes("supabase.co"))supabaseCalls++;
    blockedExternal++;
    results.external.push({hostname:u.hostname,path:u.pathname,blocked:true});
    return route.abort("blockedbyclient");
   });
   const page=await ctx.newPage();page.on("pageerror",e=>results.errors.push(e.message));
   await page.goto(base+"index.html",{waitUntil:"load",timeout:20000});
   const safety=await page.evaluate(()=>{
     const html=document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"";
     const v=html.match(/connect-src\s+([^;]+)/)?.[1]?.trim();
     return {directive:v,readonly:!!document.querySelector('meta[name="traslados-preview"]'),
       formNone:html.includes("form-action 'none'"),
       threeServices:document.querySelectorAll("#view-inicio .service-card").length===3};
   });
   ok("Only IDE is allowed for browser fetch "+width,safety.directive==="https://direcciones.ide.uy",safety);
   ok("Read only, form blocked, three services "+width,safety.readonly&&safety.formNone&&safety.threeServices,safety);
   await page.locator('#view-inicio [data-service="now"]').click();
   await page.locator("#view-viaje.active .qtc-app").waitFor({state:"visible",timeout:10000});
   const native=await page.evaluate(()=>typeof window.TrasladosAddress?.reverse==="function"&&typeof originPicker?.useCurrent==="function");
   ok("Original GPS and IDE reverse engine present "+width,native);
   await page.locator("#origin-picker .location-use-current").click();
   await page.waitForFunction(()=>record?.origin?.text?.includes("Calle de Prueba QA 123"),{timeout:14000});
   const geo=await page.evaluate(()=>({
    label:document.querySelector("#origin-picker .location-selected-label")?.textContent?.trim(),
    coordLat:record?.origin?.lat,coordLng:record?.origin?.lng,
    source:record?.origin?.source,disabled:document.querySelector("#urgent-send")?.disabled,
    header:document.querySelector("#qt-public-qa-banner")?.textContent?.trim(),
    driverNotice:document.querySelector(".qt-v87-driver-note")?.textContent?.trim()
   }));
   ok("A mocked IDE address is shown after pressing the native GPS button "+width,geo.label==="Calle de Prueba QA 123, Canelones",geo);
   ok("Uses exact MOCKED GPS location, no real device location "+width,Math.abs(geo.coordLat-(-34.85))<0.00001&&Math.abs(geo.coordLng-(-56.05))<0.00001,geo);
   ok("Reverse endpoint invoked once "+width,reverseCalls===1,{reverseCalls});
   ok("Real bookings still disabled "+width,geo.disabled===true,geo);
   ok("Visible QA notice honestly names IDE and no Supabase "+width,geo.header.includes("IDE")&&geo.header.includes("SIN SUPABASE"),geo);
   ok("Driver unavailable label remains QA only "+width,geo.driverNotice?.includes("no consultables"),geo);
   ok("No Supabase network "+width,supabaseCalls===0,{supabaseCalls});
   ok("No extra external origins reached "+width,blockedExternal===0,{blockedExternal});
   const file="01-gps-direccion-ficticia-v88-"+width+".png";
   await page.screenshot({path:path.join(output,file),animations:"disabled",fullPage:true});results.screenshots.push(file);
   await ctx.close();
  }
  ok("No browser JavaScript exceptions",results.errors.length===0,results.errors);
  results.result="PASS";console.log("PASS v88 GPS QA "+results.tests.length+" checks "+results.screenshots.length+" screenshot(s)");
 }catch(error){results.result="FAIL";results.error=String(error.stack||error);console.error(results.error);process.exitCode=1}
 finally{if(browser)await browser.close();server.kill("SIGTERM");fs.writeFileSync(path.join(output,"RESULT-v88-GPS.json"),JSON.stringify(results,null,2)+"\n")}
})();
