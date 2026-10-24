"use strict";
/*
 * Quick Trip Tu Viaje v85 — prueba funcional en Chromium REAL.
 * El HTML, CSS, app.js, rutas, buscador y eventos son los originales.
 * Red externa SIEMPRE interceptada: respuestas QA simuladas en memoria.
 * Prohibido llamar a Supabase real, publicar o tocar reservas reales.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
/* This test NEVER ships an altered HTML to users. Only Chromium receives
 * this synthetic fixture, with network interception for all remote requests.
 * Published QA index keeps preview=read-only and CSP connect-src none. */
const browserFixtureHtml=fs.readFileSync(path.resolve("web-pasajero/index.html"),"utf8")
 .replace(/<meta name="traslados-preview"[^>]*>\s*/,"")
 .replace(/<meta http-equiv="Content-Security-Policy"[^>]*>\s*/,"");

const out=process.env.QA_DIR||path.resolve("quicktrip-v85-functional-qa");
fs.mkdirSync(out,{recursive:true});
const base="http://127.0.0.1:8125/web-pasajero/";
const qaAccount={id:"00000000-0000-4000-8000-00000000a085",full_name:"Pasajero QA Ficticio",phone:"099000000"};
const syntheticSession={token:"session-qa-fixture-v85-not-real",customer:qaAccount};
const airport={text:"Aeropuerto Internacional de Carrasco",lat:-34.8352,lng:-56.029,department:"Canelones"};
const street={text:"Arenal Grande 2274, Montevideo",lat:-34.902,lng:-56.175,department:"Montevideo"};
const road={available:true,source:"openrouteservice",distanceKm:6.4,durationMin:11,
  geometry:[[airport.lat,airport.lng],[-34.863,-56.082],[-34.884,-56.131],[street.lat,street.lng]]};
const history=[{id:"00000000-0000-4000-8000-00000000a086",code:"QA-HIST-V85",
  status:"FINALIZADA",pickup_date:"2026-10-20",pickup_time:"14:30",
  created_at:"2026-10-20T17:30:00Z",origin_text:"Origen histórico ficticio",
  destination_text:"Ciudad Vieja QA Histórica",
  destination_lat:-34.905,destination_lng:-56.208,route_distance_km:6.4,
  passengers:1,quote_status:"ACEPTADO",quote_final_total:256}];
const responseTicket={id:"00000000-0000-4000-8000-00000000a087",
  code:"QA-V85-RECIBIDA",status:"PENDIENTE",fare_mode:"fixed",
  quote_final_total:256,pickup_date:"2026-10-24",pickup_time:"09:42:00",
  public_token:"qa-not-real-token-v85"};

async function main(){
 const server=spawn("python3",["-m","http.server","8125","--bind","127.0.0.1"],{
  cwd:process.cwd(),stdio:"ignore"});
 let browser;
 const report={result:"IN_PROGRESS",backend:"100% mocked",actualSupabaseCalls:0,
  reservationWritesReal:0,cases:[],httpMocks:[],screenshots:[]};
 try{
  for(let i=0;i<50;i++){
    try{if((await fetch(base)).ok)break;}catch{}
    if(i===49)throw Error("QA local server not ready");
    await new Promise(ok=>setTimeout(ok,130));
  }
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  async function makePage({mode="success",authenticated=true}={}){
    const ctx=await browser.newContext({viewport:{width:393,height:852},
      isMobile:true,hasTouch:true,reducedMotion:"reduce",serviceWorkers:"block"});
    if(authenticated){
      await ctx.addInitScript(({key,value})=>{
        try{localStorage.setItem(key,JSON.stringify(value));}catch{}
      },{key:"traslados.passenger.session.v1",value:syntheticSession});
    }
    const mock={mode,ctx,writeCalls:[],allExternal:[],pageErrors:[],quoteCalls:0,routeCalls:0,historyCalls:0};
    await ctx.route("**/*",async route=>{
      const req=route.request(),url=new URL(req.url());
      if(url.hostname==="127.0.0.1"&&url.port==="8125"){
        if(url.pathname==="/web-pasajero/"||url.pathname==="/web-pasajero/index.html")
          return route.fulfill({status:200,contentType:"text/html; charset=utf-8",body:browserFixtureHtml});
        return route.continue();
      }
      mock.allExternal.push({host:url.hostname,path:url.pathname,method:req.method()});
      // No route.continue() for any external host; all data are simulated.
      if(url.hostname==="direcciones.ide.uy"){
        const exact={address:street.text,departamento:"Montevideo",localidad:"Cordón",
          type:"DIRECCION",lat:street.lat,lng:street.lng};
        return route.fulfill({json:url.pathname.includes("reverse")?[]:[exact]});
      }
      if(url.hostname==="traslados-web.marcelof-gx.workers.dev"&&
          url.pathname==="/api/public/route-estimate"){
        mock.routeCalls++;
        if(mode==="no-road")return route.fulfill({status:503,json:{available:false,reason:"no_route"}});
        return route.fulfill({json:road});
      }
      if(url.hostname==="zetaudvvutlouiqxopvg.supabase.co"){
        const rpcName=url.pathname.split("/").pop();
        if(rpcName==="public_reference_quote_v1"){
          mock.quoteCalls++;
          return route.fulfill({json:{available:true,currency:"UYU",rateUyuPerKm:40,referenceFareUyu:256}});
        }
        if(rpcName==="public_cached_route_preview_v1")return route.fulfill({json:{available:false}});
        if(rpcName==="customer_list_reservations_v16"){
          mock.historyCalls++;
          return route.fulfill({json:history});
        }
        if(rpcName==="public_driver_availability_v1")
          return route.fulfill({json:{available:false,status:"not_available",motionHint:"unknown"}});
        if(rpcName==="public_app_config_v1")return route.fulfill({json:{schema_version:0}});
        if(rpcName==="customer_request_pickup_fixed_v1"){
          const payload=req.postDataJSON();
          mock.writeCalls.push({name:rpcName,payload});
          // Simulate busy state to detect accidental double send.
          await new Promise(ok=>setTimeout(ok,250));
          return route.fulfill({json:responseTicket});
        }
        return route.fulfill({json:null});
      }
      // All other external requests (including public tile servers) are blocked.
      return route.abort("blockedbyclient");
    });
    const page=await ctx.newPage();mock.page=page;
    page.on("pageerror",e=>mock.pageErrors.push(e.message));
    await page.goto(base,{waitUntil:"load"});
    await page.locator("#view-inicio.active").waitFor({state:"visible",timeout:12000});
    return mock;
  }

  const ok=await makePage();
  let page=ok.page;
  await page.locator('#view-inicio [data-service="now"]').click();
  await page.locator("#view-viaje.active .qtc-app").waitFor({state:"visible",timeout:12000});
  assert.equal(await page.locator("#view-viaje .premium-route-canvas").count(),1);
  assert.equal(await page.locator("#view-viaje #urgent-send").count(),1);
  assert.equal(await page.evaluate(()=>selectedPickupMode),"now");
  report.cases.push("Ingreso Tu Viaje y un único formulario/mapa/CTA: PASS");

  // The REAL address picker handles these keyboard searches and selects QA-only results.
  await page.locator('#origin-picker input[type="search"]').fill("Aeropuerto");
  await page.locator('#origin-picker input[type="search"]').press("Enter");
  await page.locator("#origin-picker button.result").filter({hasText:"Aeropuerto"}).first().click({timeout:10000});
  await page.waitForFunction(()=>record.origin?.text?.includes("Aeropuerto"));
  await page.locator('#destination-picker input[type="search"]').fill("Arenal Grande 2274");
  await page.locator('#destination-picker input[type="search"]').press("Enter");
  await page.locator("#destination-picker button.result").filter({hasText:"Arenal Grande 2274"}).first().click({timeout:10000});
  await page.waitForFunction(()=>record.destination?.text==="Arenal Grande 2274, Montevideo");
  await page.waitForFunction(()=>latestRoad?.data?.referenceFareUyu===256,{timeout:15000});
  const calculated=await page.evaluate(()=>({
    origin:record.origin.text,destination:record.destination.text,
    distance:latestRoad.data.distanceKm,duration:latestRoad.data.durationMin,
    fare:latestRoad.data.referenceFareUyu,rate:latestRoad.data.referenceRateUyuPerKm,
    source:latestRoad.data.source,recap:$("urgent-route-recap").textContent
  }));
  assert.equal(calculated.distance,6.4);
  assert.equal(calculated.duration,11);
  assert.equal(calculated.fare,256);
  assert.match(calculated.recap,/256/);
  report.calculated=calculated;
  report.cases.push("Búsqueda real UI (POI + IDE simulado) y cálculo de tarifa $256: PASS");

  const schedule=page.locator("#view-viaje .qtc-schedule");
  await schedule.locator("summary").click();
  const select=page.locator("#journey-when-select");
  await select.selectOption("10");
  assert.equal(await page.evaluate(()=>selectedPickupMode),"10");
  assert.match(await page.locator("#qtc-when-detail").textContent(),/10/);
  await select.selectOption("now");
  assert.equal(await page.evaluate(()=>selectedPickupMode),"now");
  await schedule.locator("summary").click();
  await page.locator("#view-viaje .qtc-contact summary").click();
  await page.locator("#passengers").selectOption("2");
  await page.locator("#comments").fill("Nota totalmente ficticia QA");
  const before=await page.evaluate(()=>({
    passengers:$("passengers").value,comments:$("comments").value,
    account:authReady(),route:record.origin?.text+" -> "+record.destination?.text
  }));
  assert.equal(before.account,true);assert.equal(before.passengers,"2");
  report.cases.push("Horario ahora/10 min, datos y pasajeros conservados: PASS");

  await page.locator("#view-viaje .qtc-history-button").click();
  await page.locator("#view-viaje .qtc-history-item").first().waitFor({state:"visible",timeout:10000});
  assert.match(await page.locator("#view-viaje .qtc-history-item").first().textContent(),/Ciudad Vieja QA Histórica/);
  await page.locator("#view-viaje .qtc-history-button").click();
  report.cases.push("Historial desde cuenta autenticada ficticia, sin usar teléfono: PASS");

  // Only mocked RPC receives the synthetic request. Two fast click events
  // must not dispatch a second write.
  await page.evaluate(()=>{
    const button=document.getElementById("urgent-send");
    button.click();button.click();
  });
  await page.locator("#booking-success:not([hidden])").waitFor({state:"visible",timeout:12000});
  assert.equal(ok.writeCalls.length,1,"A second submit was dispatched");
  const payload=ok.writeCalls[0].payload;
  assert.equal(payload.p_expected_total,256);
  assert.equal(payload.p_expected_rate,40);
  assert.equal(payload.p_route_distance_km,6.4);
  assert.equal(payload.p_passengers,2);
  assert.equal(payload.p_mode,"now");
  assert.equal(payload.p_session_token,syntheticSession.token);
  assert.equal(payload.p_customer_name,null);
  assert.equal(payload.p_customer_phone,null);
  assert.equal(payload.p_pickup_date,undefined);
  assert.equal(payload.p_pickup_time,undefined);
  assert.match(await page.locator("#booking-success").textContent(),/Solicitud enviada/);
  assert.match(await page.locator("#booking-success").textContent(),/todavía no es un viaje confirmado/);
  report.request={mockedRpcCount:ok.writeCalls.length,payload:{mode:payload.p_mode,
    passengers:payload.p_passengers,distanceKm:payload.p_route_distance_km,
    rateUyuPerKm:payload.p_expected_rate,totalUyu:payload.p_expected_total},
    responseStatus:responseTicket.status};
  report.cases.push("Doble clic -> un solo RPC simulado, carga $256 y estado PENDIENTE: PASS");
  await page.screenshot({path:path.join(out,"01-recibo-simulado-393.png"),fullPage:true,animations:"disabled"});
  report.screenshots.push("01-recibo-simulado-393.png");
  assert.deepEqual(ok.pageErrors,[]);

  // A different service must use its original panel, not a second app.
  await page.evaluate(()=>shiftView("inicio"));
  await page.locator('#view-inicio [data-service="schedule"]').click();
  await page.locator("#view-reservar.active").waitFor({state:"visible"});
  assert.equal(await page.evaluate(()=>selectedPickupMode),"schedule");
  assert.equal(await page.locator("#view-reservar #pickup-date").count(),1);
  assert.equal(await page.locator("#view-reservar #pickup-time").count(),1);
  assert.equal(await page.locator("#view-reservar #urgent-send").count(),0);
  assert.equal(await page.evaluate(()=>document.querySelectorAll(".premium-route-canvas").length),1);
  report.cases.push("Cambio a Tu Viaje Programado sin duplicar motores: PASS");

  await ok.ctx.close();

  // A missing verified road route must NOT trigger any synthetic booking RPC.
  const denied=await makePage({mode:"no-road"});
  page=denied.page;
  await page.locator('#view-inicio [data-service="now"]').click();
  await page.locator("#view-viaje.active .qtc-app").waitFor({state:"visible"});
  await page.evaluate(()=>{
    originPicker.persist({text:"Aeropuerto QA",lat:-34.8352,lng:-56.029,department:"Canelones"});
    destPicker.persist({text:"Destino QA",lat:-34.902,lng:-56.175,department:"Montevideo"});
  });
  await page.waitForFunction(()=>Boolean(document.querySelector("#route-overview .route-retry")?.hidden===false),
    {timeout:12000});
  await page.locator("#view-viaje #urgent-send").click();
  await page.waitForTimeout(150);
  assert.equal(denied.writeCalls.length,0,"Attempted to submit with unverified road");
  assert.equal(await page.locator("#booking-success:not([hidden])").count(),0);
  assert.match(await page.locator("#toast").textContent(),/kilómetros y precio por carretera/i);
  report.cases.push("Ruta sin verificar bloquea solicitud; 0 RPC: PASS");
  await denied.ctx.close();

  assert(report.cases.length===7);
  report.result="PASS";
  report.externalPolicy="Todos los requests remotos interceptados (simulados/abortados)";
  report.httpMocks=[...new Set([...ok.allExternal,...denied.allExternal].map(x=>x.host))];
  report.javascriptErrors=[...ok.pageErrors,...denied.pageErrors];
  assert.deepEqual(report.javascriptErrors,[]);
  fs.writeFileSync(path.join(out,"RESULTADOS-FUNCIONALES-v85.json"),JSON.stringify(report,null,2)+"\n");
  console.log("PASS Quick Trip v85 functional isolated; "+report.cases.length+" scenarios");
  console.log(JSON.stringify(report,null,2));
 }catch(e){
  report.result="FAIL";report.reason=String(e.stack||e);
  fs.writeFileSync(path.join(out,"RESULTADOS-FUNCIONALES-v85.json"),JSON.stringify(report,null,2)+"\n");
  throw e;
 }finally{
  if(browser)await browser.close();
  server.kill("SIGTERM");
 }
}
main().catch(e=>{console.error("FAIL FUNCTIONAL QA",e);process.exitCode=1});
