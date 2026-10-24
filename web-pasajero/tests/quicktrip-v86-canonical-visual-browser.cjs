"use strict";
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const dir=process.env.QA_DIR||"quicktrip-v85-v6-qa";fs.mkdirSync(dir,{recursive:true});
const base="http://127.0.0.1:8154/web-pasajero/";
(async()=>{
const server=spawn("python3",["-m","http.server","8154","--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
let browser;
const report={result:"IN_PROGRESS",environment:"Real Chromium emulating Samsung - NOT physical phone",cases:[],screenshots:[],pageErrors:[],realSupabaseCalls:0};
const test=(name,cond,details)=>{report.cases.push({name,pass:!!cond,details});assert(cond,name+" "+JSON.stringify(details));};
try{
 for(let i=0;i<45;i++){try{if((await fetch(base+"index.html")).ok)break;}catch{}if(i===44)throw Error("local server not running");await new Promise(r=>setTimeout(r,150));}
 browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",args:["--no-sandbox","--disable-dev-shm-usage"]});
 for(const width of [393,360]){
 const ctx=await browser.newContext({viewport:{width,height:width===393?852:780},deviceScaleFactor:3,isMobile:true,hasTouch:true,reducedMotion:"reduce",serviceWorkers:"block"});
 const denied=[];
 await ctx.route("**/*",route=>{const url=new URL(route.request().url());if(url.hostname==="127.0.0.1"&&url.port==="8154")return route.continue();denied.push(url.hostname);return route.abort("blockedbyclient")});
 const page=await ctx.newPage();page.on("pageerror",e=>report.pageErrors.push(e.message));
 await page.goto(base+"index.html",{waitUntil:"load",timeout:20000});
 await page.locator("#view-inicio.active .service-card").first().waitFor({state:"visible",timeout:12000});
 const home=await page.evaluate(()=>{
  const icon=(selector)=>{const e=document.querySelector(selector),s=getComputedStyle(e),r=e.getBoundingClientRect();return{w:Math.round(r.width),h:Math.round(r.height),radius:s.borderRadius}};
  const cards=[...document.querySelectorAll("#view-inicio .service-card-name")].map(e=>e.textContent.trim());
  return {now:icon('[data-service="now"] .service-card-icon'),pkg:icon('[data-service="delivery"] .service-card-icon'),scheduled:icon('[data-service="schedule"] .service-card-icon'),
   cardNames:cards,heroImage:getComputedStyle(document.querySelector(".home-hero")).backgroundImage,
   banner:document.querySelector("#qt-public-qa-banner")?.textContent,
   readonly:!!document.querySelector('meta[name="traslados-preview"]'),
   csp:document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"",
   oldTabsVisible:getComputedStyle(document.querySelector(".shell>.tabs")).display!=="none",
   overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)}
 });
 test("V6 3 circles restored "+width,home.now.w>=104&&home.now.w<=125&&home.pkg.w>=80&&home.pkg.w<=102&&home.scheduled.w>=80&&home.scheduled.w<=102&&home.now.radius.includes("%")&&home.heroImage.includes("premium-montevideo.webp"),home);
 test("Three official services "+width,home.cardNames.length===3&&home.cardNames.some(x=>/Tu viaje$/i.test(x))&&home.cardNames.some(x=>/Tu paquete$/i.test(x))&&home.cardNames.some(x=>/Tu viaje programado/i.test(x)),home.cardNames);
 test("No old footer, readonly and fit "+width,!home.oldTabsVisible&&home.readonly&&home.csp.includes("connect-src 'none'")&&home.overflow<=2,home);
 const image=await page.evaluate(async()=>{const img=new Image();img.src="./premium-montevideo.webp";try{await img.decode();return img.naturalWidth}catch{return 0}});
 test("City image loaded "+width,image>0,{naturalWidth:image});
 let filename="01-circulos-v6-qa-"+width+".png";
 await page.screenshot({path:path.join(dir,filename),fullPage:true,animations:"disabled"});report.screenshots.push(filename);
 const toggle=page.locator('[data-qt-component="theme-toggle"]');
 await toggle.waitFor({state:"visible",timeout:5000});
 await toggle.click();
 test("Light theme visible and active "+width,await page.evaluate(()=>document.body.classList.contains("qt-v86-light")));
 filename="03-claro-qa-"+width+".png";
 await page.screenshot({path:path.join(dir,filename),fullPage:true,animations:"disabled"});report.screenshots.push(filename);
 await toggle.click();
 test("Dark theme returns "+width,!(await page.evaluate(()=>document.body.classList.contains("qt-v86-light"))));
 await page.locator('#view-inicio [data-service="now"]').click();
 console.log("QA VIEW AFTER CLICK",JSON.stringify({v:await page.evaluate(()=>document.querySelector(".view.active")?.id),errors:report.pageErrors,hasQtc:await page.locator("#view-viaje .qtc-app").count()}));
 await page.locator("#view-viaje.active .qtc-app").waitFor({state:"visible",timeout:12000});
 await page.locator(".qt-qa-gps-details summary").waitFor({state:"attached",timeout:10000});
 const trip=await page.evaluate(()=>{
  const d=document.querySelector(".qt-qa-gps-details"),gps=d.querySelector(".gps-permission-help"),r=document.querySelector(".qtc-header").getBoundingClientRect();
  return{gpsCollapsed:!d.open,nativeGpsHelpPresent:Boolean(gps),originalGPSButtons:gps.querySelectorAll("button").length,originVisible:document.querySelector("#origin-picker").getBoundingClientRect().height>0,destinationVisible:document.querySelector("#destination-picker").getBoundingClientRect().height>0,
  headerHeight:Math.round(r.height),requestDisabled:document.querySelector("#urgent-send").disabled,
  legacyHeroVisible:getComputedStyle(document.querySelector("#view-viaje>.journey-hero")).display!=="none",
  overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)};
 });
 test("Compact Tu Viaje and native GPS disclosure "+width,trip.gpsCollapsed&&trip.nativeGpsHelpPresent&&trip.originVisible&&trip.destinationVisible&&trip.headerHeight<=105&&trip.requestDisabled&&!trip.legacyHeroVisible&&trip.overflow<=2,trip);
 filename="02-tu-viaje-compacto-"+width+".png";
 await page.screenshot({path:path.join(dir,filename),fullPage:true,animations:"disabled"});report.screenshots.push(filename);
 await page.locator(".qt-qa-gps-details summary").click();
 test("GPS controls accessible "+width,await page.locator(".qt-qa-gps-details").evaluate(e=>e.open));
 await page.waitForFunction(()=>history.state?.quickTripV85View==="viaje",null,{timeout:8000});
 await page.goBack({waitUntil:"domcontentloaded",timeout:10000});
 test("Back returns circular home "+width,(await page.locator("#view-inicio.active .service-card").count())===3);
 test("JS without exceptions "+width,report.pageErrors.length===0,report.pageErrors);
 test("Supabase never reached "+width,!denied.some(h=>h==="zetaudvvutlouiqxopvg.supabase.co"),{denied});
 await ctx.close();
 }
 report.result="PASS";console.log("PASS QA VISUAL "+JSON.stringify(report,null,2));
}catch(e){report.result="FAIL";report.error=String(e.stack||e);console.error("FAIL QA VISUAL "+report.error);process.exitCode=1;}
finally{if(browser)await browser.close();server.kill("SIGTERM");fs.writeFileSync(path.join(dir,"RESULTADOS-V85-CIRCULOS.json"),JSON.stringify(report,null,2)+"\n");}
})();