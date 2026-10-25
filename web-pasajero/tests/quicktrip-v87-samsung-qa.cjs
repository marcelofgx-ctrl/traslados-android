"use strict";
/* v87 Samsung QA only — real Chromium, no backend or network outside localhost. */
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const dir=process.env.QA_DIR||"quicktrip-v87-evidence";
fs.mkdirSync(dir,{recursive:true});
const base="http://127.0.0.1:8177/web-pasajero/";
const report={result:"IN_PROGRESS",source:"Quick Trip v87 QA, Samsung viewport emulation only",cases:[],images:[],pageErrors:[],externalRequests:[]};
const check=(label,ok,detail)=>{report.cases.push({label,passed:!!ok,detail});assert(ok,label+": "+JSON.stringify(detail));};
(async()=>{
 const server=spawn("python3",["-m","http.server","8177","--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
 let browser;
 try{
  for(let n=0;n<65;n++){try{if((await fetch(base+"index.html")).ok)break}catch{}if(n===64)throw Error("Local QA HTTP unavailable");await new Promise(r=>setTimeout(r,140))}
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",args:["--no-sandbox","--disable-dev-shm-usage"]});
  for(const width of [393,360]){
   const ctx=await browser.newContext({viewport:{width,height:width===393?852:780},deviceScaleFactor:2,isMobile:true,hasTouch:true,reducedMotion:"reduce",serviceWorkers:"block"});
   await ctx.route("**/*",route=>{
    const u=new URL(route.request().url());
    if(u.hostname==="127.0.0.1"&&u.port==="8177")return route.continue();
    report.externalRequests.push({host:u.hostname,path:u.pathname});
    return route.abort("blockedbyclient");
   });
   const page=await ctx.newPage();
   page.on("pageerror",e=>report.pageErrors.push(e.message));
   await page.goto(base+"index.html",{waitUntil:"load",timeout:20000});
   await page.locator("#view-inicio.active .service-card").first().waitFor();
   check("Three official services "+width,await page.locator("#view-inicio .service-card").count()===3);
   check("Read-only CSP "+width,await page.evaluate(()=>!!document.querySelector('meta[name="quicktrip-qa-isolation"]')&&document.querySelector('meta[http-equiv="Content-Security-Policy"]').content.includes("connect-src 'none'")));
   let name="01-inicio-v87-"+width+".png";
   await page.screenshot({path:path.join(dir,name),fullPage:true,animations:"disabled"});report.images.push(name);
   await page.locator('#view-inicio [data-service="now"]').click();
   await page.locator('#view-viaje.active .qtc-app').waitFor({state:"visible",timeout:12000});
   await page.locator(".premium-route-canvas[data-qt-v87-route='unverified']").waitFor({state:"attached",timeout:7000});
   await page.locator(".qt-v87-driver-note").waitFor({state:"visible",timeout:5000});
   const d=await page.evaluate(()=>{
    const el=q=>document.querySelector(q),box=q=>{const e=el(q);if(!e)return null;const b=e.getBoundingClientRect();return{x:Math.round(b.x),y:Math.round(b.y),w:Math.round(b.width),h:Math.round(b.height)}};
    return {mapHidden:getComputedStyle(el(".premium-route-cartography")).display==="none",
     mapNoteVisible:getComputedStyle(el(".qt-v87-route-note")).display!=="none",
     routeNote:el(".qt-v87-route-note").textContent.trim(),
     driverNote:el(".qt-v87-driver-note").textContent.trim(),
     ctaDisabled:el("#urgent-send").disabled,
     ctaBox:box(".qtc-submit-region"),timeBox:box(".qtc-schedule"),
     navBox:box(".qtc-bottom-nav"),headerBox:box(".qtc-header"),
     overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
     cssV87:!![...document.styleSheets].some(x=>x.href?.includes("quicktrip-qa-samsung-v87.css")),
     originalRoute:typeof window.TrasladosRoutes?.estimate==="function",
     originalMap:typeof window.TrasladosPremiumMap?.render==="function",
     active:document.body.classList.contains("qt-v87-qa")
    };
   });
   check("QA overlay loaded "+width,d.active&&d.cssV87);
   check("No partial route tiles "+width,d.mapHidden&&d.mapNoteVisible&&d.routeNote.includes("pendiente"));
   check("Honest driver state "+width,d.driverNote.includes("no consultables"));
   check("Native CTA fail closed "+width,d.ctaDisabled);
   check("Original road and map engines present "+width,d.originalRoute&&d.originalMap);
   check("CTA before secondary options "+width,d.ctaBox&&d.timeBox&&d.ctaBox.y<d.timeBox.y,d);
   check("No horizontal overflow "+width,d.overflow<=2,d);
   check("Compact header "+width,d.headerBox?.h<=70,d);
   name="02-tu-viaje-v87-"+width+".png";
   await page.screenshot({path:path.join(dir,name),fullPage:true,animations:"disabled"});report.images.push(name);
   name="03-viewport-Samsung-v87-"+width+".png";
   await page.screenshot({path:path.join(dir,name),fullPage:false,animations:"disabled"});report.images.push(name);
   // Distinct fake locations: exercise the exact chosen-origin/destination layout
   // seen in the user's phone, without reading their GPS or querying a backend.
   const fixture=await page.evaluate(()=>{
    if(typeof originPicker==="undefined"||typeof destPicker==="undefined")return {supported:false};
    originPicker.persist({text:"Ubicación de muestra (GPS ficticio QA)",lat:-34.8501,lng:-56.0482,department:"Canelones"});
    destPicker.persist({text:"Aeropuerto Internacional de Carrasco · terminal de pasajeros (QA)",lat:-34.8352,lng:-56.029,department:"Canelones"});
    return {supported:true,origin:record.origin?.text||"",dest:record.destination?.text||""};
   });
   check("Original location pickers retained "+width,fixture.supported&&fixture.origin.includes("muestra")&&fixture.dest.includes("Aeropuerto"),fixture);
   await page.waitForTimeout(350);
   check("Still no unverified route image with selected places "+width,
    await page.locator(".premium-route-canvas").getAttribute("data-qt-v87-route")==="unverified");
   check("Still no real request allowed "+width,await page.locator("#urgent-send").evaluate(e=>e.disabled));
   const historyGeometry=await page.evaluate(()=>{
    const a=document.querySelector("#destination-picker .qtc-history-button")?.getBoundingClientRect();
    const b=document.querySelector("#destination-picker .location-selected-label")?.getBoundingClientRect();
    if(!a||!b)return {valid:false};
    const intersection=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*
      Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
    return {valid:true,intersection,history:{x:a.x,y:a.y,w:a.width,h:a.height},
      label:{x:b.x,y:b.y,w:b.width,h:b.height}};
   });
   check("Historial never covers selected destination "+width,
     historyGeometry.valid&&historyGeometry.intersection===0,historyGeometry);
   name="04-seleccion-ficticia-sin-ruta-v87-"+width+".png";
   await page.screenshot({path:path.join(dir,name),fullPage:true,animations:"disabled"});report.images.push(name);
   const time=page.locator(".qtc-schedule summary");
   await time.click();
   check("Schedule accordion still opens "+width,await page.locator(".qtc-schedule").evaluate(e=>e.open));
   await page.locator(".qtc-contact summary").click();
   check("Passenger accordion still opens "+width,await page.locator(".qtc-contact").evaluate(e=>e.open));
   await ctx.close();
  }
  check("No JS exception",report.pageErrors.length===0,report.pageErrors);
  check("No actual Supabase network calls",!report.externalRequests.some(x=>x.host.includes("supabase.co")),report.externalRequests);
  report.result="PASS";
  console.log("PASS v87 QA "+report.cases.length+" checks, "+report.images.length+" screenshots");
 }catch(e){report.result="FAIL";report.error=String(e.stack||e);console.error(report.error);process.exitCode=1}
 finally{if(browser)await browser.close();server.kill("SIGTERM");fs.writeFileSync(path.join(dir,"RESULTS-v87.json"),JSON.stringify(report,null,2)+"\n");}
})();
