"use strict";
/* Quick Trip v89: real Chromium UI test. All GPS positions and IDE answers
   are fictitious; ZERO real network connections outside localhost. */
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const dir=process.env.QA_DIR||"quicktrip-v89-evidence";fs.mkdirSync(dir,{recursive:true});
const base="http://127.0.0.1:8199/web-pasajero/";
const report={status:"IN_PROGRESS",cases:[],shots:[],pageErrors:[],external:[],noRealGPS:true};
const test=(name,condition,detail)=>{report.cases.push({name,pass:!!condition,detail});assert(condition,name+": "+JSON.stringify(detail))};
(async()=>{
 const server=spawn("python3",["-m","http.server","8199","--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
 let browser;
 try{
  for(let k=0;k<60;k++){try{if((await fetch(base+"index.html")).ok)break}catch{}if(k===59)throw Error("QA server unavailable");await new Promise(r=>setTimeout(r,150))}
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",args:["--no-sandbox","--disable-dev-shm-usage"]});
  for(const width of [393,360]){
   for(const mode of ["granted","denied","prompt"]){
    const ctx=await browser.newContext({viewport:{width,height:width===393?852:780},deviceScaleFactor:2,isMobile:true,hasTouch:true,
     geolocation:{latitude:-34.85,longitude:-56.05,accuracy:25},reducedMotion:"reduce",serviceWorkers:"block"});
    if(mode==="granted")await ctx.grantPermissions(["geolocation"],{origin:base});
    if(mode==="denied")await ctx.grantPermissions([],{origin:base});
    // For prompt leave as browser-default; no OS dialog invoked in this branch.
    let reverse=0,supabase=0;
    await ctx.route("**/*",route=>{
     const u=new URL(route.request().url());
     if(u.hostname==="127.0.0.1"&&u.port==="8199")return route.continue();
     if(u.hostname==="direcciones.ide.uy"&&u.pathname==="/api/v1/geocode/reverse"){
      reverse++;
      return route.fulfill({status:200,headers:{"Access-Control-Allow-Origin":"*","Content-Type":"application/json"},
       body:JSON.stringify([{address:"Calle Ficticia QA 123, Canelones",departamento:"Canelones"}])});
     }
     if(u.hostname.includes("supabase.co"))supabase++;
     report.external.push({host:u.hostname,endpoint:u.pathname,blocked:true});
     return route.abort("blockedbyclient");
    });
    const page=await ctx.newPage();
    page.on("pageerror",e=>report.pageErrors.push(e.message));
    await page.goto(base+"index.html",{waitUntil:"load",timeout:20000});
    await page.locator('#view-inicio [data-service="now"]').click();
    await page.locator('#origin-picker .location-use-current[data-qt-gps-label="Mi ubicación"]').waitFor({state:"visible",timeout:10000});
    const before=await page.evaluate(()=>{
     const btn=document.querySelector("#origin-picker .location-use-current");
     const input=document.querySelector("#origin-picker .loc-search-origin input");
     const search=document.querySelector("#origin-picker .location-search-action");
     const css=getComputedStyle(btn),label=getComputedStyle(btn,"::after"),icon=getComputedStyle(btn,"::before");
     const b=btn.getBoundingClientRect(),ib=input.getBoundingClientRect(),sb=search.getBoundingClientRect();
     return {caption:btn.dataset.qtGpsLabel,aria:btn.getAttribute("aria-label"),
      buttonBox:{x:b.x,y:b.y,w:b.width,h:b.height},inputBox:{x:ib.x,y:ib.y,w:ib.width,h:ib.height},searchBox:{x:sb.x,y:sb.y,w:sb.width,h:sb.height},
      visualLabel:label.content,hasIcon:icon.maskImage!=="none"||icon.webkitMaskImage!=="none",
      horizontalOverflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
      QA:document.body.classList.contains("qt-v89-gps"),
      CSP:document.querySelector('meta[http-equiv="Content-Security-Policy"]').content};
    });
    test(mode+" "+width+" rendered elegant button",before.QA&&before.visualLabel.includes("Mi ubicación")&&before.hasIcon&&before.buttonBox.w>=135&&before.buttonBox.h>=39,before);
    test(mode+" "+width+" button not over search",before.buttonBox.y>=before.inputBox.y+before.inputBox.h-1&&before.inputBox.w>=100&&before.horizontalOverflow<=2,before);
    test(mode+" "+width+" only public IDE in CSP",before.CSP.match(/connect-src\s+([^;]+)/)?.[1]?.trim()==="https://direcciones.ide.uy"&&before.CSP.includes("form-action 'none'"));
    test(mode+" "+width+" original GPS button accessibility",before.aria==="Usar mi ubicación actual como origen");
    const file0="01-control-ubicacion-"+mode+"-"+width+".png";
    await page.screenshot({path:path.join(dir,file0),fullPage:false,animations:"disabled"});report.shots.push(file0);
    await page.locator("#origin-picker .location-use-current").click();
    if(mode==="granted"){
     await page.waitForFunction(()=>record?.origin?.text==="Calle Ficticia QA 123, Canelones",null,{timeout:13000});
     test("granted "+width+" uses preauthorized GPS and IDE once",reverse===1&&await page.locator("#origin-picker .location-selected-label").textContent()==="Calle Ficticia QA 123, Canelones",{reverse});
     test("granted "+width+" no permission guidance",await page.locator("#origin-picker .gps-permission-help").evaluate(e=>e.hidden));
    }else{
     await page.locator("#origin-picker .qt-v89-gps-status:not([hidden])").waitFor({state:"visible",timeout:6000});
     const state=await page.evaluate(()=>{
      const h=document.querySelector("#origin-picker .gps-permission-help");
      const det=h?.closest("details");
      return {helpVisible:!!h&&!h.hidden,expanded:!!det?.open,helpTitle:h?.querySelector("strong")?.textContent,
       status:document.querySelector("#origin-picker .qt-v89-gps-status")?.textContent,
       promptButton:!![...(h?.querySelectorAll("button")||[])].find(x=>x.textContent.includes("Solicitar permiso"))};
     });
     test(mode+" "+width+" help auto-opens without hiding behind collapsed section",state.helpVisible&&state.expanded,state);
     if(mode==="denied")test("denied "+width+" explains Chrome settings",state.status.includes("candado")&&state.helpTitle.includes("bloqueada"),state);
     if(mode==="prompt")test("prompt "+width+" offers native permission request",state.promptButton&&state.status.includes("una sola vez"),state);
     test(mode+" "+width+" no geolocation requested without user OS permission",reverse===0,{reverse});
    }
    test(mode+" "+width+" booking disabled",await page.locator("#urgent-send").evaluate(x=>x.disabled));
    test(mode+" "+width+" zero actual Supabase",supabase===0,{supabase});
    const file1="02-resultado-"+mode+"-"+width+".png";
    await page.screenshot({path:path.join(dir,file1),fullPage:true,animations:"disabled"});report.shots.push(file1);
    await ctx.close();
   }
  }
  test("No page errors",report.pageErrors.length===0,report.pageErrors);
  test("No forbidden outbound traffic",report.external.length===0,report.external);
  report.status="PASS";console.log("PASS v89: "+report.cases.length+" checks "+report.shots.length+" screenshots");
 }catch(e){report.status="FAIL";report.error=String(e.stack||e);console.error("FAIL v89 "+report.error);process.exitCode=1}
 finally{if(browser)await browser.close();server.kill("SIGTERM");fs.writeFileSync(path.join(dir,"RESULTADOS-v89-GPS.json"),JSON.stringify(report,null,2)+"\n")}
})();
