"use strict";
/* Verify the ACTUAL externally hosted QA page in real Chromium mobile.
   No database connection, no production writes, no app source mutation. */
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const {chromium}=require("playwright-core");
const dir=process.env.QA_DIR||"quicktrip-v85-qa-circles-remote";
const url=process.env.QA_PREVIEW_URL;
assert(/^https:\/\/rawgit\.net\/marcelofgx-ctrl\/traslados-android\/[a-f0-9]{40}\/web-pasajero\/index\.html$/.test(url||""),"Untrusted preview");
fs.mkdirSync(dir,{recursive:true});
(async()=>{
let browser,page;
const report={status:"IN_PROGRESS",sourceUrl:url,checks:[],screenshots:[],requestsToRealSupabase:0,responsesFromRealSupabase:0,failures:[]};
const check=(label,yes,details)=>{report.checks.push({label,pass:Boolean(yes),details});assert(yes,label+" "+JSON.stringify(details));};
try{
browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",args:["--no-sandbox","--disable-dev-shm-usage"]});
const ctx=await browser.newContext({viewport:{width:393,height:852},deviceScaleFactor:3,hasTouch:true,isMobile:true,reducedMotion:"reduce",
 userAgent:"Mozilla/5.0 (Linux; Android 16; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
 serviceWorkers:"block"});
await ctx.addCookies([{name:"__Http-phish",value:"1",url:"https://rawgit.net/",httpOnly:true,secure:true,sameSite:"Lax"}]);
await ctx.route("**/*",route=>{
const u=new URL(route.request().url());
if(/supabase\.co|direcciones\.ide\.uy|workers\.dev/.test(u.hostname)){
 report.requestsToRealSupabase++;
 return route.abort("blockedbyclient");
}
return route.continue();
});
page=await ctx.newPage();
page.on("pageerror",e=>report.failures.push("PAGE "+e.message));
page.on("requestfailed",r=>{if(!/csp/.test(r.failure()?.errorText||""))report.failures.push("REQUEST "+r.url()+" "+r.failure()?.errorText)});
page.on("response",r=>{if(/supabase\.co/.test(r.url())&&r.status()<400)report.responsesFromRealSupabase++});
let res=await page.goto(url,{waitUntil:"domcontentloaded",timeout:40000});
check("HTTP 200",res?.status()===200,res?.status());
if((await page.locator("#qt-public-qa-banner").count())===0){
const t=await page.locator("body").innerText();
check("Only trusted hosting consent",t.includes("You are opening third-party content"),t.slice(0,240));
await page.getByText("Continue to this repository",{exact:true}).click();
await page.locator("#qt-public-qa-banner").waitFor({state:"visible",timeout:18000});
}
await page.locator("#view-inicio.active .service-card").first().waitFor({state:"visible",timeout:15000});
const home=await page.evaluate(()=>{
 const icon=s=>{const el=document.querySelector(s),r=el.getBoundingClientRect(),cs=getComputedStyle(el);return{w:Math.round(r.width),radius:cs.borderRadius}};
 return {
  circles:[icon('[data-service="now"] .service-card-icon'),icon('[data-service="delivery"] .service-card-icon'),icon('[data-service="schedule"] .service-card-icon')],
  labels:[...document.querySelectorAll("#view-inicio .service-card-name")].map(e=>e.textContent.trim()),
  hero:getComputedStyle(document.querySelector(".home-hero")).backgroundImage,
  title:document.querySelector("#home-title")?.textContent.trim(),
  oldFooter:getComputedStyle(document.querySelector(".shell>.tabs")).display,
  readonly:!!document.querySelector('[name="traslados-preview"]'),
  csp:document.querySelector('[http-equiv="Content-Security-Policy"]')?.content,
  bodyClass:document.body.className,
  overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)
 }});
check("3 circle icons from approved V6",home.circles[0].w>=105&&home.circles[0].w<=124&&home.circles[1].w>=80&&home.circles[1].w<=102&&home.circles[2].w>=80&&home.circles[2].w<=102&&home.circles.every(x=>x.radius==="50%"),home);
check("3 official services",home.labels.length===3&&home.labels.some(x=>/tu paquete/i.test(x))&&home.labels.some(x=>/programado/i.test(x)),home.labels);
check("No old big cards, tabs, or hero title",home.oldFooter==="none"&&home.title==="Elegí cómo viajar"&&home.hero.includes("premium-montevideo.webp"),home);
check("Isolated read-only and mobile width",home.readonly&&home.csp.includes("connect-src 'none'")&&home.overflow<3,home);
const city=await page.evaluate(async()=>{
 const image=new Image();image.src="./premium-montevideo.webp";
 try{await image.decode();return{loaded:true,width:image.naturalWidth}}catch(e){return{loaded:false,error:String(e)}}
});
check("City background real asset available",city.loaded,city);
await page.screenshot({path:path.join(dir,"01-QA-V6-PORTADA-SAMSUNG-393.png"),fullPage:true,animations:"disabled"});
report.screenshots.push("01-QA-V6-PORTADA-SAMSUNG-393.png");
await page.locator('#view-inicio [data-service="now"]').click();
await page.locator("#view-viaje.active .qtc-app").waitFor({state:"visible",timeout:15000});
const trip=await page.evaluate(()=>{
 const d=document.querySelector(".qt-qa-gps-details");
 return {gpsFoldExists:!!d,gpsClosed:d?!d.open:null,
   nativeGpsInside:!!d?.querySelector(".gps-permission-help"),
   ctaDisabled:document.querySelector("#urgent-send")?.disabled,
   originVisible:document.querySelector("#origin-picker")?.getBoundingClientRect().height>0,
   destVisible:document.querySelector("#destination-picker")?.getBoundingClientRect().height>0,
   legacyHero:getComputedStyle(document.querySelector("#view-viaje>.journey-hero")).display,
   headerHeight:Math.round(document.querySelector(".qtc-header").getBoundingClientRect().height)
 };
});
check("Compact native Tu Viaje, GPS folded, no real booking",trip.gpsFoldExists&&trip.gpsClosed&&trip.nativeGpsInside&&trip.ctaDisabled&&trip.originVisible&&trip.destVisible&&trip.legacyHero==="none"&&trip.headerHeight<=105,trip);
await page.screenshot({path:path.join(dir,"02-QA-V85-TU-VIAJE-COMPACTO-SAMSUNG-393.png"),fullPage:true,animations:"disabled"});
report.screenshots.push("02-QA-V85-TU-VIAJE-COMPACTO-SAMSUNG-393.png");
await page.waitForFunction(()=>history.state?.quickTripV85View==="viaje",null,{timeout:10000});
await page.goBack({waitUntil:"domcontentloaded",timeout:15000});
check("Browser Back returns to circular home",(await page.locator("#view-inicio.active .service-card").count())===3);
check("No real backend connection",report.requestsToRealSupabase===0&&report.responsesFromRealSupabase===0,
 {requests:report.requestsToRealSupabase,responses:report.responsesFromRealSupabase});
check("No JS exceptions",report.failures.filter(x=>x.startsWith("PAGE")).length===0,report.failures);
report.status="PASS";console.log("PASS REMOTE QA APPROVED DESIGN "+JSON.stringify(report,null,2));
await ctx.close();
}catch(e){
 report.status="FAIL";report.error=String(e.stack||e);console.error("FAIL REMOTE QA APPROVED DESIGN "+report.error);
 if(page)try{await page.screenshot({path:path.join(dir,"ERROR-REMOTE-QA.png"),fullPage:true,timeout:4000});}catch{}
 process.exitCode=1;
}finally{if(browser)await browser.close();fs.writeFileSync(path.join(dir,"RESULTADOS-REMOTO-V6-V85.json"),JSON.stringify(report,null,2)+"\n");}
})();