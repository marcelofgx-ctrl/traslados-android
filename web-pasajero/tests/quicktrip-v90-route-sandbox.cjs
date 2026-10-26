"use strict";
/* v90 QA only — Chromium Samsung simulation with mock OSRM. No live GPS,
 * no real Supabase, no Cloudflare app worker, no bookings. */
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const evidence=process.env.QA_DIR||"quicktrip-v90-evidence";fs.mkdirSync(evidence,{recursive:true});
const base="http://127.0.0.1:8190/web-pasajero/";
const report={status:"RUNNING",checks:[],screenshots:[],externalRequests:[],pageErrors:[],syntheticCoordinates:true,realBackendRequests:0};
const check=(name,pass,detail)=>{report.checks.push({name,pass:!!pass,detail});assert(pass,name+" "+JSON.stringify(detail))};
(async()=>{
 const server=spawn("python3",["-m","http.server","8190","--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
 let browser;
 try{
  for(let t=0;t<50;t++){try{if((await fetch(base+"index.html")).ok)break}catch{}if(t===49)throw Error("localhost QA unavailable");await new Promise(r=>setTimeout(r,150))}
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",args:["--no-sandbox","--disable-dev-shm-usage"]});
  for(const width of [393,360]){
   const ctx=await browser.newContext({viewport:{width,height:width===393?852:780},deviceScaleFactor:2,isMobile:true,hasTouch:true,serviceWorkers:"block",reducedMotion:"reduce",
    userAgent:"Mozilla/5.0 (Linux; Android 16; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"});
   let osrmCalls=0,realBackendCalls=0,invalid=false;
   await ctx.route("**/*",route=>{
    const u=new URL(route.request().url());
    if(u.hostname==="127.0.0.1"&&u.port==="8190")return route.continue();
    if(u.hostname==="router.project-osrm.org"&&u.pathname.startsWith("/route/v1/driving/")){
     osrmCalls++;
     const q=decodeURI(u.pathname);
     if(invalid)return route.fulfill({status:200,headers:{"Access-Control-Allow-Origin":"*","Content-Type":"application/json"},
      body:JSON.stringify({code:"NoRoute",routes:[],waypoints:[]})});
     const points=q.split("/").pop().split(";").map(s=>s.split(",").map(Number));
     const first=points[0],last=points[points.length-1];
     const geometry=[first,[(first[0]+last[0])/2,(first[1]+last[1])/2],last];
     return route.fulfill({status:200,headers:{"Access-Control-Allow-Origin":"*","Content-Type":"application/json"},
      body:JSON.stringify({code:"Ok",routes:[{distance:8600,duration:875,geometry:{type:"LineString",coordinates:geometry}}],
       waypoints:points.map(p=>({location:p,distance:0,name:"Calle de prueba QA"}))})});
    }
    if(u.hostname.includes("supabase.co")||u.hostname.includes("workers.dev"))realBackendCalls++;
    report.externalRequests.push({host:u.hostname,path:u.pathname,blocked:true});
    return route.abort("blockedbyclient");
   });
   const page=await ctx.newPage();
   page.on("pageerror",e=>report.pageErrors.push(e.message));
   await page.goto(base+"index.html",{waitUntil:"load",timeout:22000});
   await page.locator('#view-inicio [data-service="now"]').click();
   await page.locator('.qtc-app[data-qt-v90="sandbox"]').waitFor({state:"visible",timeout:12000});
   const safety=await page.evaluate(()=>{
    const html=document.querySelector('meta[http-equiv="Content-Security-Policy"]').content;
    const connect=html.match(/connect-src\s+([^;]+)/)?.[1]?.trim();
    return {connect,readonly:!!document.querySelector('meta[name="traslados-preview"]'),
      threeServices:document.querySelectorAll("#view-inicio .service-card").length,
      shape:document.body.classList.contains("qt-v89-gps"),
      v90:document.body.classList.contains("qt-v90-qa"),
      buttonLabel:document.querySelector("#origin-picker .location-use-current")?.dataset.qtGpsLabel,
      sendDisabled:document.getElementById("urgent-send").disabled};
   });
   check(width+" CSP public IDE+OSRM only",safety.connect==="https://direcciones.ide.uy https://router.project-osrm.org",safety);
   check(width+" approved v89 GPS UX still mounted",safety.shape&&safety.buttonLabel==="Mi ubicación",safety);
   check(width+" read-only and native booking disabled",safety.readonly&&safety.sendDisabled&&safety.threeServices===3,safety);
   const startName="01-initial-v90-"+width+".png";
   await page.screenshot({path:path.join(evidence,startName),fullPage:true,animations:"disabled"});report.screenshots.push(startName);
   await page.evaluate(()=>{
    const origin={text:"Origen simulado - Montevideo QA",lat:-34.902,lng:-56.165,department:"Montevideo"};
    const destination={text:"Destino simulado - Montevideo QA",lat:-34.926,lng:-56.159,department:"Montevideo"};
    originPicker.persist(origin);destPicker.persist(destination);
   });
   await page.locator('.qtc-app[data-qt-v90-quote="verified"]').waitFor({state:"attached",timeout:14000});
   const quote=await page.evaluate(()=>{
    const x=q=>document.querySelector(q),txt=q=>x(q)?.textContent.trim();
    const card=x(".qt-v90-quote").getBoundingClientRect(),btn=x("#urgent-send").getBoundingClientRect();
    return {km:txt(".qt-v90-km"),min:txt(".qt-v90-min"),fare:txt(".qt-v90-fare"),
     message:txt(".qt-v90-quote-message"),disclaimer:txt(".qt-v90-disclaimer"),
     mapVisible:!x(".qt-v90-map").hidden,mapSvg:!!x(".qt-v90-map svg"),
     driver:txt(".qt-v90-driver-demo"),scenario:x(".qt-v90-driver-demo").dataset.scenario,
     CTA:x("#urgent-send").disabled,layout:{quoteY:card.y,ctaY:btn.y},overflow:document.documentElement.scrollWidth-innerWidth};
   });
   check(width+" road distance verified by mock OSRM API",quote.km==="8,6 km"&&quote.min==="15 min",{quote,osrmCalls});
   check(width+" indicative price 40UYU/km is arithmetic and prominently labelled",quote.fare.includes("344")&&quote.disclaimer.includes("40 UYU/km")&&quote.disclaimer.includes("NO es cotización oficial"),quote);
   check(width+" verified GeoJSON sketch and no fake straight line",quote.mapVisible&&quote.mapSvg,quote);
   check(width+" simulated driver explicitly fictitious",quote.driver.includes("FICTICIO")&&quote.driver.includes("2,4 km")&&quote.driver.includes("7 min"),quote);
   check(width+" button and UI placement no overflow",quote.CTA&&quote.layout.quoteY<quote.layout.ctaY&&quote.overflow<=2,quote);
   check(width+" single OSRM route request",osrmCalls===1,{osrmCalls});
   const positive="02-route-verified-fictitious-driver-"+width+".png";
   await page.screenshot({path:path.join(evidence,positive),fullPage:true,animations:"disabled"});report.screenshots.push(positive);
   await page.locator(".qt-v90-scenario").click();
   const switchState=await page.locator(".qt-v90-driver-demo").evaluate(e=>({status:e.dataset.scenario,text:e.textContent}));
   check(width+" no-driver fake scenario",switchState.status==="unavailable"&&switchState.text.includes("Dato ficticio"),switchState);
   // Change selected destination; invalid response must erase stale metrics.
   invalid=true;
   await page.evaluate(()=>destPicker.persist({text:"Destino alternativo simulado QA",lat:-34.92,lng:-56.145,department:"Montevideo"}));
   await page.locator('.qtc-app[data-qt-v90-quote="pending"]').waitFor({state:"attached",timeout:13000});
   await page.waitForFunction(()=>document.querySelector(".qt-v90-quote-message")?.textContent.includes("Sin ruta vial verificable"),null,{timeout:13000});
   const fail=await page.evaluate(()=>({text:document.querySelector(".qt-v90-quote-message")?.textContent,
    metricsHidden:document.querySelector(".qt-v90-quote-results")?.hidden,
    mapHidden:document.querySelector(".qt-v90-map")?.hidden,bookingDisabled:document.querySelector("#urgent-send")?.disabled}));
   check(width+" invalid road data fails closed and clears past price",fail.metricsHidden&&fail.mapHidden&&fail.bookingDisabled&&fail.text.includes("No mostramos"),fail);
   check(width+" no Supabase or original Cloudflare Worker network calls",realBackendCalls===0,{realBackendCalls});
   const negative="03-route-failure-no-invented-data-"+width+".png";
   await page.screenshot({path:path.join(evidence,negative),fullPage:true,animations:"disabled"});report.screenshots.push(negative);
   await ctx.close();
  }
  check("No browser JS errors",report.pageErrors.length===0,report.pageErrors);
  check("No forbidden outgoing network",report.externalRequests.length===0,report.externalRequests);
  report.status="PASS";console.log("PASS QA v90 "+report.checks.length+" checks; "+report.screenshots.length+" screenshots");
 }catch(e){report.status="FAIL";report.error=String(e.stack||e);console.error(report.error);process.exitCode=1}
 finally{if(browser)await browser.close();server.kill("SIGTERM");fs.writeFileSync(path.join(evidence,"QA_V90_RESULT.json"),JSON.stringify(report,null,2)+"\n")}
})();
