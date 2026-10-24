"use strict";
/**
 * Quick Trip Samsung SM-S918B profile: Chromium mobile emulation, NOT real phone.
 * Operative PWA from disk, every external request mocked or blocked.
 * All captured coordinates/names/tokens are synthetic. Never deploys.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const dir=process.env.QA_DIR||"quicktrip-samsung-v85-qa";
fs.mkdirSync(dir,{recursive:true});
const base="http://127.0.0.1:8138/web-pasajero/";
const phoneUA="Mozilla/5.0 (Linux; Android 16; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36";
const ORIGIN={text:"Aeropuerto QA Montevideo",lat:-34.8352,lng:-56.029,department:"Canelones"};
const DEST={text:"Destino QA Ciudad Vieja",lat:-34.902,lng:-56.175,department:"Montevideo"};
const ROAD={available:true,source:"openrouteservice",distanceKm:6.4,durationMin:11,
geometry:[[ORIGIN.lat,ORIGIN.lng],[-34.860,-56.080],[-34.895,-56.140],[DEST.lat,DEST.lng]]};
const account={token:"qa-fixture-samsung-never-real",customer:{
  id:"00000000-0000-4000-8000-000000009086",
  full_name:"Pasajero QA Ficticio",phone:"099000000"}};

async function main(){
 const server=spawn("python3",["-m","http.server","8138","--bind","127.0.0.1"],{
  cwd:process.cwd(),stdio:"ignore"
 });let browser;
 const report={kind:"CHROMIUM_MOBILE_EMULATION_NOT_PHYSICAL_SAMSUNG",
  device:"Samsung Galaxy S23 Ultra SM-S918B (UA + CSS viewport simulation)",
  actualSupabaseCalls:0,realReservationWrites:0,
  cases:[],limitations:[],screenshots:[],status:"IN_PROGRESS",javascriptErrors:[]};
 const note=(name,ok,detail)=>report.cases.push({name,status:ok?"PASS":"FAIL",detail});
 async function shot(page,name){
   await page.screenshot({path:path.join(dir,name),fullPage:true,animations:"disabled"});
   report.screenshots.push(name);
 }
 try{
  for(let i=0;i<45;i++){
    try{if((await fetch(base)).ok)break;}catch{}
    if(i===44)throw Error("Local QA HTTP unavailable");
    await new Promise(resolve=>setTimeout(resolve,140));
  }
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  async function context(width,height){
    const ctx=await browser.newContext({viewport:{width,height},deviceScaleFactor:3,
       userAgent:phoneUA,isMobile:true,hasTouch:true,reducedMotion:"reduce",
       serviceWorkers:"block",locale:"es-UY",timezoneId:"America/Montevideo"});
    await ctx.addInitScript(({key,value})=>{
      if(!localStorage.getItem(key))localStorage.setItem(key,JSON.stringify(value));
    },{key:"traslados.passenger.session.v1",value:account});
    await ctx.route("**/*",async route=>{
      const req=route.request(),u=new URL(req.url());
      if(u.hostname==="127.0.0.1"&&u.port==="8138")return route.continue();
      const name=u.pathname.split("/").pop();
      // Absolutely nothing passes from Chromium to external network.
      if(u.hostname==="traslados-web.marcelof-gx.workers.dev"&&
         u.pathname==="/api/public/route-estimate")return route.fulfill({json:ROAD});
      if(u.hostname==="zetaudvvutlouiqxopvg.supabase.co"){
        if(name==="public_reference_quote_v1")return route.fulfill({json:{
          available:true,currency:"UYU",referenceFareUyu:256,rateUyuPerKm:40}});
        if(name==="public_cached_route_preview_v1")return route.fulfill({json:{available:false}});
        if(name==="public_driver_availability_v1")return route.fulfill({json:{
          available:false,status:"not_available",motionHint:"stationary"}});
        if(name==="customer_list_reservations_v16")return route.fulfill({json:[]});
        return route.fulfill({json:null});
      }
      return route.abort("blockedbyclient");
    });
    return ctx;
  }
  async function start(page){
    page.on("pageerror",e=>report.javascriptErrors.push(e.message));
    await page.goto(base,{waitUntil:"load"});
    await page.locator('#view-inicio.active [data-service="now"]').click();
    await page.locator('#view-viaje.active .qtc-app').waitFor({state:"visible",timeout:12000});
  }
  async function draft(page){
    await page.evaluate(({origin,destination})=>{
      originPicker.persist(origin);destPicker.persist(destination);
    },{origin:ORIGIN,destination:DEST});
    await page.waitForFunction(()=>latestRoad?.data?.referenceFareUyu===256,null,{timeout:12000});
    await page.locator("#view-viaje .qtc-contact summary").click();
    await page.locator("#passengers").selectOption("2");
    await page.locator("#comments").fill("Equipaje QA; sin información personal");
    await page.waitForFunction(()=>Boolean(sessionStorage.getItem("quicktrip.v85.draft.session.v1")),
     null,{timeout:8000});
    await page.waitForTimeout(300);
  }
  const ctx=await context(393,852),page=await ctx.newPage();
  await start(page);
  const agent=await page.evaluate(()=>({ua:navigator.userAgent,touch:navigator.maxTouchPoints,
    viewport:[innerWidth,innerHeight],secure:isSecureContext,
    manifests:[...document.querySelectorAll('link[rel="manifest"]')].map(x=>x.getAttribute("href"))}));
  assert(agent.ua.includes("SM-S918B")&&agent.touch>0);
  note("Agente Samsung SM-S918B y táctil",true,agent);
  const manifest=await page.evaluate(async()=>{
    const l=document.querySelector('link[rel="manifest"]');
    if(!l)return null;
    const response=await fetch(l.href);
    return response.json();
  });
  const validManifest=manifest?.display==="standalone"&&
    manifest.start_url==="./"&&manifest.scope==="./";
  note("Manifest PWA autónomo y alcance correcto",validManifest,{
    name:manifest?.name,display:manifest?.display,start_url:manifest?.start_url,
    scope:manifest?.scope});
  await shot(page,"01-samsung-v85-inicio-tu-viaje.png");
  await page.waitForFunction(()=>history.state?.quickTripV85View==="viaje");
  await page.goBack({waitUntil:"domcontentloaded"});
  const backOk=(await page.locator("#view-inicio.active").count())===1;
  note("Atrás sistema simulado (history.back) retorna a portada",backOk,{
    currentUrl:page.url(),state:await page.evaluate(()=>history.state)});
  await shot(page,"02-samsung-v85-atras-vuelve-inicio.png");
  assert(backOk,"Android-like Back fails");

  await page.locator('#view-inicio.active [data-service="now"]').click();
  await page.locator("#view-viaje.active").waitFor({state:"visible"});
  await draft(page);
  const before=await page.evaluate(()=>({
    origin:record.origin?.text,dest:record.destination?.text,
    passengers:$("passengers").value,notes:$("comments").value,
    fare:latestRoad?.data?.referenceFareUyu,
    storage:sessionStorage.getItem("quicktrip.v85.draft.session.v1")
  }));
  assert(before.storage);
  assert(!before.storage.includes(account.token));
  assert(!before.storage.includes("referenceFareUyu"));
  await shot(page,"03-samsung-v85-formulario-completo-ficticio.png");

  await page.reload({waitUntil:"load"});
  await page.locator('#view-inicio.active [data-service="now"]').click();
  await page.locator("#view-viaje.active").waitFor({state:"visible"});
  const restored=await page.evaluate(()=>({
    origin:record.origin?.text||null,dest:record.destination?.text||null,
    passengers:$("passengers").value,notes:$("comments").value,
    session:Boolean(session?.token),
    fare:latestRoad?.data?.referenceFareUyu||null
  }));
  const reloadOk=restored.origin===ORIGIN.text&&restored.dest===DEST.text&&
    restored.passengers==="2"&&restored.notes===before.notes&&restored.session;
  note("Recarga de PWA conserva borrador sin conservar tarifa falsa",reloadOk,{before:{
     origin:before.origin,dest:before.dest,passengers:before.passengers,notes:before.notes,
     fare:before.fare
  },after:restored});
  await shot(page,"04-samsung-v85-borrador-restaurado-tras-recarga.png");
  assert(reloadOk,"Reload draft recovery fails");

  const cta=await page.evaluate(()=>({count:document.querySelectorAll("#urgent-send").length,
    overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)}));
  note("Formulario íntegro, sin duplicar CTA ni desbordar ancho",cta.count===1&&cta.overflow<=2,cta);

  // Closing a tab/new standalone-like window is NOT a real Android app restart.
  // It is an intentionally stricter browser lifecycle probe: sessionStorage is tab-scoped.
  await page.waitForTimeout(160);
  await page.close();
  const reopened=await ctx.newPage();
  await start(reopened);
  const reopen=await reopened.evaluate(()=>({
    origin:record.origin?.text||null,dest:record.destination?.text||null,
    session:Boolean(session?.token),
    sessionDraft:sessionStorage.getItem("quicktrip.v85.draft.session.v1")
  }));
  note("Nueva pestaña conserva cuenta del mismo contexto",reopen.session,reopen);
  report.limitations.push({
   case:"Cierre de pestaña y apertura de otra dentro del mismo perfil Chromium",
   observedDraftRestored:Boolean(reopen.origin&&reopen.dest),
   status:reopen.origin&&reopen.dest?"PRESERVED":"NOT_PRESERVED",
   explanation:"sessionStorage está asociado a la pestaña; esto NO predice de forma concluyente el restablecimiento de una PWA instalada tras cerrar la app Android. Requiere Samsung físico."
  });
  await shot(reopened,"05-samsung-v85-reapertura-nueva-pestana.png");
  await ctx.close();

  // Smaller screen, touch and service navigation.
  const small=await context(360,780);
  const smallPage=await small.newPage();await start(smallPage);
  const geo=await smallPage.evaluate(()=>({
    width:innerWidth,height:innerHeight,
    overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
    button:document.querySelector("#urgent-send")?.getBoundingClientRect().toJSON(),
    footer:document.querySelector(".qtc-bottom-nav")?.getBoundingClientRect().toJSON()
  }));
  note("Pantalla 360px sin scroll horizontal",geo.overflow<=2,{
    width:geo.width,overflow:geo.overflow,
    ctaBottom:Math.round(geo.button?.bottom||0),
    footerTop:Math.round(geo.footer?.top||0)
  });
  report.limitations.push({
    case:"CTA contra footer fijo a 360x780",
    bottom:Math.round(geo.button?.bottom||0),
    footerTop:Math.round(geo.footer?.top||0),
    inViewportWithoutScrolling:Boolean(geo.button&&geo.footer&&
      geo.button.bottom<=geo.footer.top-5),
    note:"Si es false el usuario debe desplazar; no llamarlo botón fijo visible."
  });
  await shot(smallPage,"06-samsung-v85-360px-pantalla-completa.png");
  await small.close();

  assert.deepEqual(report.javascriptErrors,[],"Unexpected runtime exception");
  assert(report.cases.every(x=>x.status==="PASS"),"Samsung emulation regression");
  report.status="PASS_WITH_PHYSICAL_VALIDATION_PENDING";
  fs.writeFileSync(path.join(dir,"SAMSUNG-EMULACION-v85-RESULTADOS.json"),
    JSON.stringify(report,null,2)+"\n");
  console.log("PASS SAMSUNG EMULATION "+JSON.stringify(report,null,2));
 }catch(e){
  report.status="FAIL";report.error=String(e.stack||e);
  fs.writeFileSync(path.join(dir,"SAMSUNG-EMULACION-v85-RESULTADOS.json"),
    JSON.stringify(report,null,2)+"\n");throw e;
 }finally{if(browser)await browser.close();server.kill("SIGTERM");}
}
main().catch(e=>{console.error("FAIL SAMSUNG QA EMULATED",e);process.exitCode=1;});
