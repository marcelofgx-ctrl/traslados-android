"use strict";
/* BROWSER EDGE QA — REAL Quick Trip UI, ALL external requests mocked or aborted.
 * No actual database connection, no production deploy, no engine mutations.
 * Observe UX defects without pretending they pass.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const output=process.env.QA_DIR||path.resolve("quicktrip-v85-edge-qa");
const base="http://127.0.0.1:8126/web-pasajero/";
fs.mkdirSync(output,{recursive:true});
const account={token:"fixture-qa-session-not-real",customer:{
  id:"00000000-0000-4000-8000-000000009085",
  full_name:"Persona Ficticia QA",phone:"099000000"}};
const origin={text:"Aeropuerto QA",lat:-34.8352,lng:-56.029,department:"Canelones"};
const dest={text:"Destino QA Montevideo",lat:-34.902,lng:-56.175,department:"Montevideo"};
const road={available:true,source:"openrouteservice",distanceKm:6.4,durationMin:11,
  geometry:[[origin.lat,origin.lng],[-34.86,-56.08],[-34.89,-56.13],[dest.lat,dest.lng]]};
const syntheticResult={id:"00000000-0000-4000-8000-000000009086",
  code:"QA-EDGE-REQUEST",status:"PENDIENTE",fare_mode:"fixed",quote_final_total:256,
  pickup_date:"2026-10-24",pickup_time:"13:12:00"};

async function main(){
 const server=spawn("python3",["-m","http.server","8126","--bind","127.0.0.1"],{
  cwd:process.cwd(),stdio:"ignore"});
 let browser;const sessions=[],report={status:"IN_PROGRESS",
  browser:"Chromium 393x852; installed real PWA assets",
  backend:"100% intercepted synthetic responses, no external pass-through",
  productionSupabaseCalls:0,realReservationWrites:0,
  tests:[],findings:[],screenshots:[]};
 const record=(name,pass,detail)=>{report.tests.push({name,status:pass?"PASS":"DEFECT",detail});
   if(!pass)report.findings.push({name,detail});};
 try{
  for(let i=0;i<45;i++){
   try{if((await fetch(base)).ok)break;}catch{}
   if(i===44)throw Error("Local QA server failed");
   await new Promise(r=>setTimeout(r,170));
  }
  browser=await chromium.launch({
    headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
    args:["--no-sandbox","--disable-dev-shm-usage"]
  });
  async function create(options={}){
   const ctx=await browser.newContext({viewport:{width:393,height:852},
    isMobile:true,hasTouch:true,reducedMotion:"reduce",serviceWorkers:"block"});
   await ctx.addInitScript(({key,value})=>{
    if(!localStorage.getItem(key))localStorage.setItem(key,JSON.stringify(value));
   },{key:"traslados.passenger.session.v1",value:account});
   const state={ctx,requests:[],errors:[],routeCalls:0,requestsChecked:0,
    writes:[],writeMode:options.writeMode||"success",routeMode:options.routeMode||"ok"};
   await ctx.route("**/*",async route=>{
    const req=route.request(),u=new URL(req.url());
    if(u.hostname==="127.0.0.1"&&u.port==="8126")return route.continue();
    const name=u.pathname.split("/").pop();
    state.requests.push({host:u.hostname,path:u.pathname,method:req.method()});
    if(u.hostname==="traslados-web.marcelof-gx.workers.dev"&&
      u.pathname==="/api/public/route-estimate"){
      state.routeCalls++;
      if(state.routeMode==="fail")return route.fulfill({status:503,json:{available:false,reason:"no_route"}});
      return route.fulfill({json:road});
    }
    if(u.hostname==="zetaudvvutlouiqxopvg.supabase.co"){
     if(name==="public_reference_quote_v1")return route.fulfill({
      json:{available:true,currency:"UYU",rateUyuPerKm:40,referenceFareUyu:256}
     });
     if(name==="public_cached_route_preview_v1")return route.fulfill({json:{available:false}});
     if(name==="public_driver_availability_v1")return route.fulfill({
      json:{available:false,status:"not_available",motionHint:"stationary"}
     });
     if(name==="customer_list_reservations_v16")return route.fulfill({json:[]});
     if(name==="public_app_config_v1")return route.fulfill({json:{schema_version:0}});
     if(name==="customer_request_pickup_fixed_v1"){
      const payload=req.postDataJSON();
      state.writes.push({name,payload,requestId:payload.p_request_id});
      await new Promise(resolve=>setTimeout(resolve,260));
      if(state.writeMode==="ambiguous")return route.fulfill({
       status:504,json:{message:"Gateway timeout simulated: response uncertain"}
      });
      return route.fulfill({json:syntheticResult});
     }
     return route.fulfill({json:null});
    }
    // IDE, OSM, cloud services and every other external host denied.
    return route.abort("blockedbyclient");
   });
   const page=await ctx.newPage();state.page=page;
   page.on("pageerror",err=>state.errors.push(err.message));
   sessions.push(state);
   return state;
  }
  async function load(s,withPrevious=false){
   if(withPrevious)await s.page.goto(base+"offline.html",{waitUntil:"load"});
   await s.page.goto(base,{waitUntil:"load"});
   await s.page.locator("#view-inicio.active").waitFor({state:"visible",timeout:12000});
   await s.page.locator('#view-inicio [data-service="now"]').click();
   await s.page.locator("#view-viaje.active .qtc-app").waitFor({state:"visible",timeout:12000});
  }
  async function choose(s){
   const page=s.page;
   await page.evaluate(({origin,dest})=>{
    originPicker.persist(origin);destPicker.persist(dest);
   },{origin,dest});
   if(s.routeMode!=="fail"){
    await page.waitForFunction(()=>latestRoad?.data?.referenceFareUyu===256,null,{timeout:12000});
    assert.equal(await page.evaluate(()=>latestRoad.data.referenceRateUyuPerKm),40);
   }else{
    await page.waitForFunction(()=>document.querySelector("#route-overview .route-retry")?.hidden===false,
     null,{timeout:12000});
   }
  }
  async function shot(s,name){
   await s.page.screenshot({path:path.join(output,name),fullPage:true,animations:"disabled"});
   report.screenshots.push(name);
  }
  // A. Back arrow uses original in-app navigation; hardware Back uses browser history.
  const nav=await create();await load(nav,true);
  await nav.page.locator(".qtc-back-slot .journey-back").click();
  assert.equal(await nav.page.locator("#view-inicio.active").count(),1);
  record("Flecha visible Atrás vuelve al Inicio",true,"Misma página, tres servicios intactos");
  await nav.page.locator('#view-inicio [data-service="now"]').click();
  await nav.page.locator("#view-viaje.active").waitFor({state:"visible"});
  const historyBefore=await nav.page.evaluate(()=>({length:history.length,url:location.href}));
  await nav.page.goBack({waitUntil:"domcontentloaded",timeout:12000});
  const browserBack=nav.page.url();
  const wentHome=browserBack===base &&
    await nav.page.locator("#view-inicio.active").count()===1;
  record("Atrás del navegador desde Tu Viaje",wentHome,{
    before:historyBefore,afterUrl:browserBack,
    expected:"Inicio Quick Trip; no abandonar la PWA",
    observed:wentHome?"Volvió Inicio":"Salió al documento previo del historial Chrome"
  });
  if(!wentHome)await shot(nav,"01-browser-atras-fuera-de-pwa.png");
  await nav.ctx.close();

  // B. Preserving the draft when leaving via the INTERNAL button; reload/kill-recovery separate.
  const draft=await create();await load(draft);await choose(draft);
  await draft.page.locator("#view-viaje .qtc-contact summary").click();
  await draft.page.locator("#passengers").selectOption("2");
  await draft.page.locator("#comments").fill("Indicaciones ficticias persistentes");
  await draft.page.locator(".qtc-back-slot .journey-back").click();
  await draft.page.locator('#view-inicio [data-service="now"]').click();
  await draft.page.locator("#view-viaje.active").waitFor({state:"visible"});
  const preserved=await draft.page.evaluate(()=>({
    origin:record.origin?.text||null,dest:record.destination?.text||null,
    passengers:$("passengers").value,comments:$("comments").value,
    token:session?.token||null,roadKm:latestRoad?.data?.distanceKm||null
  }));
  const preservedOk=preserved.origin===origin.text&&preserved.dest===dest.text &&
    preserved.passengers==="2"&&preserved.comments==="Indicaciones ficticias persistentes";
  record("Volver al Inicio y reabrir sin perder borrador",preservedOk,preserved);
  await draft.page.reload({waitUntil:"load"});
  await draft.page.locator('#view-inicio [data-service="now"]').click();
  await draft.page.locator("#view-viaje.active").waitFor({state:"visible"});
  const afterReload=await draft.page.evaluate(()=>({
    origin:record.origin?.text||null,dest:record.destination?.text||null,
    passengers:$("passengers").value,comments:$("comments").value,
    token:session?.token||null
  }));
  const recovered=afterReload.origin===origin.text && afterReload.dest===dest.text &&
    afterReload.comments==="Indicaciones ficticias persistentes";
  record("Recuperar borrador tras recarga completa",recovered,{
   before:preserved,after:afterReload,
   expected:"Recorrido y observaciones recuperados, sin nueva reserva",
   observed:recovered?"Recuperado":"Borrador reiniciado; la cuenta permanece"
  });
  assert.equal(afterReload.token,account.token,"Local account session should survive reload");
  record("Sesión del pasajero sobrevive la recarga",true,{fixtureSessionPersisted:true});
  if(!recovered)await shot(draft,"02-borrador-tras-recarga.png");
  await draft.ctx.close();

  // C. Offline behavior: disabled send, no bookings, connection restores.
  const offline=await create();await load(offline);await choose(offline);
  await offline.ctx.setOffline(true);
  await offline.page.waitForFunction(()=>navigator.onLine===false && $("urgent-send").disabled,
    null,{timeout:7000});
  assert.equal(offline.writes.length,0);
  const offState=await offline.page.evaluate(()=>({online:navigator.onLine,
   disabled:$("urgent-send").disabled,bannerHidden:$("offline-band").hidden}));
  record("Modo sin conexión impide solicitar viaje",!offState.online&&
    offState.disabled&&!offState.bannerHidden,offState);
  await offline.ctx.setOffline(false);
  await offline.page.waitForFunction(()=>navigator.onLine && !$("urgent-send").disabled,
    null,{timeout:7000});
  record("Reconexión rehabilita solicitar",true,{pendingWrites:offline.writes.length});
  await offline.ctx.close();

  // D. A 503 road provider cannot manufacture a fare or submit an RPC.
  const unavailable=await create({routeMode:"fail"});await load(unavailable);await choose(unavailable);
  await unavailable.page.locator("#view-viaje #urgent-send").click();
  await unavailable.page.waitForTimeout(150);
  const routeUnsafe=await unavailable.page.evaluate(()=>({
   latestFare:latestRoad?.data?.referenceFareUyu||null,
   message:$("toast").textContent,
   receiptVisible:!$("booking-success").hidden
  }));
  assert.equal(unavailable.writes.length,0);
  assert.equal(routeUnsafe.receiptVisible,false);
  record("Falla de red vial bloquea envío y precio falso",true,{
   requestCount:unavailable.writes.length,...routeUnsafe
  });
  await unavailable.ctx.close();

  // E. An ambiguous 504 must not mark accepted, retain same idempotency key on retry.
  const ambiguous=await create({writeMode:"ambiguous"});await load(ambiguous);await choose(ambiguous);
  await ambiguous.page.locator("#view-viaje #urgent-send").click();
  await ambiguous.page.waitForFunction(()=>!$("urgent-send").disabled && !$("urgent-send-error").hidden,
    null,{timeout:12000});
  const firstNotice=await ambiguous.page.locator("#urgent-send-error").textContent();
  assert.match(firstNotice,/No pudimos confirmar/i);
  assert.equal(await ambiguous.page.locator("#booking-success:not([hidden])").count(),0);
  assert.equal(ambiguous.writes.length,1);
  const firstId=ambiguous.writes[0].requestId;
  await ambiguous.page.locator("#view-viaje #urgent-send").click();
  await ambiguous.page.waitForFunction(()=>!$("urgent-send").disabled &&
    !$("urgent-send-error").hidden,null,{timeout:12000});
  assert.equal(ambiguous.writes.length,2);
  assert.equal(ambiguous.writes[1].requestId,firstId);
  record("Error 504 ambiguo no da éxito y reintento reutiliza UUID",true,{
   errorsShown:2,requestsMocked:2,uniqueIds:new Set(ambiguous.writes.map(x=>x.requestId)).size,
   fakeSuccess:false,serverIdempotencyNotVerified:true
  });
  await shot(ambiguous,"03-error-respuesta-ambigua.png");
  await ambiguous.ctx.close();

  // F. Rapid double click while first POST is inflight must be 1 simulated request.
  const double=await create();await load(double);await choose(double);
  await double.page.evaluate(()=>{
   const b=document.getElementById("urgent-send");b.click();b.click();
  });
  await double.page.locator("#booking-success:not([hidden])").waitFor({state:"visible",timeout:12000});
  assert.equal(double.writes.length,1);
  assert.equal(double.writes[0].payload.p_expected_total,256);
  assert.equal(syntheticResult.status,"PENDIENTE");
  record("Doble clic durante POST envía exactamente una solicitud simulada",true,{
    callCount:double.writes.length,priceUyu:double.writes[0].payload.p_expected_total,
    resultingState:"PENDIENTE, no aceptado"
  });
  await shot(double,"04-respuesta-pendiente-tras-doble-clic.png");
  await double.ctx.close();

  assert(sessions.every(x=>x.errors.length===0),"Browser JavaScript exceptions: "+
    sessions.map(x=>x.errors.join(",")).join(" | "));
  report.pageErrors=[];
  report.totals={pass:report.tests.filter(x=>x.status==="PASS").length,
    defects:report.tests.filter(x=>x.status==="DEFECT").length,
    total:report.tests.length};
  report.status="COMPLETED_WITH_FINDINGS";
  fs.writeFileSync(path.join(output,"BROWSER-CASOS-LIMITE-v85.json"),
   JSON.stringify(report,null,2)+"\n");
  console.log("FINISHED Quick Trip v85 edge real browser: "+report.totals.pass+" PASS, "+
   report.totals.defects+" DEFECT\n"+JSON.stringify(report,null,2));
 }catch(e){
  report.status="HARNESS_FAILURE";report.failure=String(e.stack||e);
  fs.writeFileSync(path.join(output,"BROWSER-CASOS-LIMITE-v85.json"),
    JSON.stringify(report,null,2)+"\n");
  throw e;
 }finally{
  for(const s of sessions)try{await s.ctx.close();}catch{}
  if(browser)await browser.close();
  server.kill("SIGTERM");
 }
}
main().catch(e=>{console.error("FAIL EDGE QA",e);process.exitCode=1;});
