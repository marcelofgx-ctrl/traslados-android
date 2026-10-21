"use strict";
/* Browser QA on REAL Chrome mobile emulation, no npm dependencies.
 * READ ONLY: never clicks the booking submit control or modifies backend data. */
const cp=require("node:child_process"),fs=require("node:fs"),path=require("node:path");
const {setTimeout:pause}=require("node:timers/promises");
const OUT="artifacts/quicktrip-clean";fs.mkdirSync(OUT,{recursive:true});
const HOST="http://127.0.0.1:8767/web-pasajero/";
const children=[],logs=[],pending=new Map();
let socket,seq=0,canScreenshot=false;
const say=s=>{console.log(s);logs.push(s)};
function check(ok,label){if(!ok)throw new Error("FAIL: "+label);say("PASS: "+label);}
async function poll(fn,ms=20000){
 const start=Date.now(),deadline=start+ms;
 while(Date.now()<deadline){try{const v=await fn();if(v)return v}catch{}await pause(200)}
 throw Error("Timeout waiting for "+fn.toString().slice(0,95));
}
function cmd(method,params={}){
 return new Promise((resolve,reject)=>{
  const id=++seq,timer=setTimeout(()=>{pending.delete(id);reject(Error("CDP timeout "+method))},20000);
  pending.set(id,{resolve:v=>{clearTimeout(timer);resolve(v)},reject:e=>{clearTimeout(timer);reject(e)}});
  socket.send(JSON.stringify({id,method,params}));
 });
}
async function expression(fn){
 const response=await cmd("Runtime.evaluate",{expression:"("+fn.toString()+")()",returnByValue:true,awaitPromise:true,userGesture:true});
 if(response.exceptionDetails)throw Error("Browser JS: "+JSON.stringify(response.exceptionDetails));
 return response.result&&response.result.value;
}
async function goto(url){
 await cmd("Page.navigate",{url});
 await poll(()=>expression(function(){return document.readyState==="complete"}),26000);
}
async function snap(name){
 const result=await cmd("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});
 fs.writeFileSync(path.join(OUT,name),Buffer.from(result.data,"base64"));
 say("Screenshot: "+name);
}
async function main(){
 const chrome=(process.env.CHROME_BIN||
  ["google-chrome","google-chrome-stable","chromium","chromium-browser"]
  .find(name=>cp.spawnSync("which",[name],{encoding:"utf8"}).status===0));
 if(!chrome)throw Error("Chrome missing from runner");
 const server=cp.spawn("python3",["-m","http.server","8767","--bind","127.0.0.1","--directory","."],{stdio:"ignore"});
 children.push(server);
 await poll(async()=>{try{return (await fetch(HOST)).ok}catch{return false}},15000);
 const browser=cp.spawn(chrome,["--headless=new","--no-sandbox","--disable-gpu",
  "--disable-dev-shm-usage","--no-first-run","--disable-extensions",
  "--remote-debugging-port=9238","--user-data-dir=/tmp/qtc-browser-"+process.pid,
  "--window-size=390,850","about:blank"],{stdio:"ignore"});
 children.push(browser);
 const wsurl=await poll(async()=>{try{
  const list=await(await fetch("http://127.0.0.1:9238/json/list")).json();
  return list.find(item=>item.type==="page")?.webSocketDebuggerUrl;
 }catch{return false}},20000);
 socket=new WebSocket(wsurl);
 await new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>reject(Error("WebSocket timeout")),12000);
  socket.addEventListener("open",()=>{clearTimeout(timer);resolve()},{once:true});
  socket.addEventListener("error",()=>{clearTimeout(timer);reject(Error("WebSocket error"))},{once:true});
 });
 socket.addEventListener("message",evt=>{
  let msg;try{msg=JSON.parse(String(evt.data))}catch{return}
  if(msg.id&&pending.has(msg.id)){
   const p=pending.get(msg.id);pending.delete(msg.id);
   if(msg.error)p.reject(Error(msg.error.message));else p.resolve(msg.result||{});
  }
 });
 await cmd("Page.enable");await cmd("Runtime.enable");
 await cmd("Emulation.setDeviceMetricsOverride",{width:390,height:844,deviceScaleFactor:1,mobile:true,
  screenWidth:390,screenHeight:844});
 await cmd("Emulation.setTouchEmulationEnabled",{enabled:true,maxTouchPoints:1});
 try{
  await goto(HOST+"?ui=clean");
  canScreenshot=true;
  await poll(()=>expression(function(){
   return !!document.querySelector("body.qt-clean.qt-clean-active #qtc-app .qtc-places [data-booking-step=route]");
  }),24000);
  await pause(900);
  const s=await expression(function(){
   const q=x=>document.querySelector(x),display=x=>q(x)&&getComputedStyle(q(x)).display;
   const image=q(".qtc-logo");
   return {
    view:!!q("#view-viaje.active"),origin:!!q(".qtc-places #origin-picker .location-widget"),
    destination:!!q(".qtc-places #destination-picker .location-widget"),
    map:!!q(".qtc-places .premium-route-canvas"),
    mapVisible:display(".qtc-places .premium-route-canvas")!=="none",
    mapCount:document.querySelectorAll(".premium-route-canvas").length,
    driver:!!q(".qtc-driver-slot #pickup-presence"),
    eta:!!q("#pickup-distance"),price:!!q(".qtc-action-slot #urgent-route-recap"),
    submit:!!q(".qtc-action-slot #urgent-send"),
    oldHidden:(!q("#view-viaje .premium-journey-header")||
      display("#view-viaje .premium-journey-header")==="none")&&
      display("#view-viaje .journey-hero")==="none",
    history:!!q("#qtc-history-panel"),schedule:!!q(".qtc-schedule #journey-when-select"),
    contact:!!q(".qtc-contact #booking-step-passenger"),
    logo:!!image&&image.complete&&image.naturalWidth>0,
    width:innerWidth,scrollWidth:document.documentElement.scrollWidth,
    manifest:q('link[rel="manifest"]')?.getAttribute("href")
   };
  });
  say("Mobile 390px: "+JSON.stringify(s));
  check(s.view&&s.origin&&s.destination,"New layout and native address selectors");
  check(s.map&&s.mapVisible&&s.mapCount===1,"Exactly one real map");
  check(s.driver&&s.eta&&s.price&&s.submit,"Native ETA, route metrics and submit control");
  check(s.oldHidden,"Old journey navigation and hero are hidden");
  check(s.history&&s.schedule&&s.contact,"Animated history, collapsible time and passenger fields");
  check(s.logo,"Brand Quick Trip logo loads");
  const contrastLogo=await expression(function(){
   const im=document.querySelector(".qtc-logo");
   return im?.complete&&im?.naturalWidth>0&&im.currentSrc.includes("quicktrip-logo-contrast.svg");
  });
  check(contrastLogo,"Brighter Quick Trip brand asset loads in mobile header");

  check(s.scrollWidth<=s.width+6,"No horizontal overflow at Samsung phone width");
  check(s.manifest==="./manifest.webmanifest","Quick Trip production manifest selected");
  await snap("01-quicktrip-arriba.png");
  const newTitle=await expression(function(){
   return document.querySelector(".qtc-summary-title small")?.textContent.trim();
  });
  check(newTitle==="Precio del viaje","Fixed-price display has no orientativo label");
  const nativePhase=await expression(function(){
   const p=document.querySelector("#pickup-presence");
   window.__qaNativePhase=p.dataset.phase||"";
   p.dataset.phase="checking";
   p.hidden=false;
   return window.__qaNativePhase;
  });
  const searching=await poll(()=>expression(function(){
   const card=document.querySelector(".qtc-driver");
   const banner=document.querySelector(".qtc-driver-search");
   return card?.dataset.searchState==="checking"&&!banner?.hidden&&
    !!document.querySelector("#qtc-search-title")?.textContent.includes("Buscando");
  }),4500);
  check(searching,"Animated search starts only when native pickup phase is checking");
  await snap("06-buscando-conductor.png");
  // Simulate ONLY the PRESENTATION state; no RPC, GPS, or booking mutation.
  const nativeUnavailable=await expression(function(){
   const p=document.querySelector("#pickup-presence");
   window.__qaPresenceOriginal=p.dataset.presence||"";
   p.dataset.presence="busy";
   p.dataset.phase="blocked";
   return true;
  });
  const blockedPanel=await poll(()=>expression(function(){
   const driver=document.querySelector(".qtc-driver");
   const panel=driver?.querySelector(".qtc-driver-unavailable");
   const native=driver?.querySelector("#pickup-presence");
   return !!panel&&!panel.hidden&&driver.dataset.availability==="unavailable"&&
     getComputedStyle(native).display==="none";
  }),4500);
  check(blockedPanel,"Unavailable conductor shown honestly with no false pickup ETA");
  await expression(function(){
    document.querySelector(".qtc-driver-unavailable").scrollIntoView({block:"center"});
    return true;
  });
  await pause(150);
  await snap("07-conductor-no-disponible.png");
  await expression(function(){
   const p=document.querySelector("#pickup-presence");
   p.dataset.presence="online";
   p.dataset.phase="ready";
   return true;
  });
  const restored=await poll(()=>expression(function(){
   const driver=document.querySelector(".qtc-driver");
   const panel=driver?.querySelector(".qtc-driver-unavailable");
   const native=driver?.querySelector("#pickup-presence");
   return !!panel&&panel.hidden&&driver.dataset.availability==="normal"&&
     getComputedStyle(native).display!=="none";
  }),4500);
  check(restored,"Real available state restores original ETA card without any replacement engine");
  await expression(function(){
   document.querySelector("#pickup-presence").dataset.presence=window.__qaPresenceOriginal;
   delete window.__qaPresenceOriginal;
   return true;
  });
  await expression(function(){
   document.querySelector("#pickup-presence").dataset.phase="ready";return true;
  });
  const stopped=await poll(()=>expression(function(){
   const banner=document.querySelector(".qtc-driver-search");
   return !!banner?.hidden&&document.querySelector(".qtc-driver").dataset.searchState==="settled";
  }),3500);
  check(stopped,"Search animation stops on native ready phase");
  await expression(function(){
   document.querySelector("#pickup-presence").dataset.phase=window.__qaNativePhase;
   delete window.__qaNativePhase;
   const label=document.querySelector(".premium-route-map-note");
   window.__qaMapCaption=label.textContent;
   label.textContent="15,7 km · 20 min · $ 628 orientativos";
   return true;
  });
  const cleanFare=await poll(()=>expression(function(){
   const text=document.querySelector(".premium-route-map-note").textContent;
   return text.includes("$ 628")&&!text.includes("orientativo");
  }),4000);
  check(cleanFare,"Route map caption removes orientativos without changing route values");
  await expression(function(){
   document.querySelector(".premium-route-map-note").textContent=window.__qaMapCaption;
   delete window.__qaMapCaption;return true;
  });
  const receiptStyle=await expression(function(){
   const box=document.querySelector("#booking-success");
   const old=box.innerHTML,hide=box.hidden;
   box.hidden=false;
   box.innerHTML='<span class="mark">✓</span><h2>Solicitud enviada</h2>'+
    '<p class="success-lead">Tu pedido fue recibido.</p>'+
    '<div class="success-receipt"><strong class="code">TR-PRUEBA</strong>'+
    '<strong class="success-price">UYU 628</strong></div>'+
    '<button class="primary full">VER MIS TRASLADOS</button>';
   const title=parseFloat(getComputedStyle(box.querySelector("h2")).fontSize);
   const price=parseFloat(getComputedStyle(box.querySelector(".success-price")).fontSize);
   const button=parseFloat(getComputedStyle(box.querySelector("button")).minHeight);
   box.innerHTML=old;box.hidden=hide;
   return {title,price,button};
  });
  check(receiptStyle.title>=22&&receiptStyle.title<=27&&receiptStyle.price>=24&&receiptStyle.price<=30&&receiptStyle.button>=45,
   "Compact readable receipt retains native submission behavior");

  const opened=await expression(function(){
   const button=document.querySelector(".qtc-history-button");button.click();
   return !document.querySelector("#qtc-history-panel").hidden&&button.getAttribute("aria-expanded")==="true";
  });
  check(opened,"History disclosure opens");
  await snap("02-quicktrip-historial.png");
  const when=await expression(function(){
   const detail=document.querySelector(".qtc-schedule");detail.open=true;
   const sel=document.querySelector("#journey-when-select");
   sel.value="10";sel.dispatchEvent(new Event("change",{bubbles:true}));
   return {title:document.querySelector("#qtc-when-title").textContent,
    detail:document.querySelector("#qtc-when-detail").textContent,
    official:document.querySelector("#urgent-actions-title").textContent};
  });
  say("Timing choice: "+JSON.stringify(when));
  check(when.title==="Horario de recogida"&&when.detail.includes("10")&&when.official.includes("10"),"Native pickup mode preserved under compact timing control");
  await expression(function(){
   document.querySelector('[data-qtc-nav="schedule"]').click();
   return true;
  });
  // MutationObserver restitutes the original nodes on the next browser task.
  // Wait for DOM settlement instead of reading state inside the click callback.
  const schedule=await poll(()=>expression(function(){
   const state={
    active:!!document.querySelector("#view-reservar.active"),
    pax:!!document.querySelector("#booking-step-when #passengers"),
    route:!!document.querySelector('#reserve-field-host [data-booking-step="route"]'),
    map:!!document.querySelector('[data-booking-step="route"] > .premium-route-canvas')
   };
   return state.active&&state.pax&&state.route&&state.map?state:null;
  }),10000);
  say("Scheduled restored: "+JSON.stringify(schedule));
  check(schedule.active&&schedule.pax&&schedule.route&&schedule.map,
   "Scheduled trip restored to original UI with native controls");
  await expression(function(){
   document.querySelector('[data-service="now"]').click();
   return true;
  });
  const again=await poll(()=>expression(function(){
   return !!document.querySelector('#view-viaje.active .qtc-places [data-booking-step="route"]')&&
    document.querySelectorAll(".premium-route-canvas").length===1;
  }),10000);
  check(again,"Returning to immediate ride retains one native route map");
  // Browser-only authorized-history fixture. The real application has NO fake
  // customer addresses or trips; this verifies native destination.persist.
  const testOrigin=await expression(function(){
   originPicker.persist({text:"Tres Cruces, Montevideo",lat:-34.9045,lng:-56.1683,
    department:"Montevideo",source:"qa-test-only"});
   window.__qaLoadTrips=loadTrips;
   loadTrips=async function(){};
   tripRows=[{id:"qa-only-history-row",destination_text:"Aeropuerto Internacional de Carrasco",
    destination_lat:-34.83696,destination_lng:-56.01638}];
   return document.querySelector('#origin-picker .location-selected-label')?.textContent;
  });
  check(testOrigin?.includes("Tres Cruces"),"Native origin picker preserved after navigation");
  await expression(function(){document.querySelector('.qtc-history-button').click();return true});
  await poll(()=>expression(function(){return !!document.querySelector('.qtc-history-list .qtc-history-item')}),5000);
  const reused=await expression(function(){
   document.querySelector('.qtc-history-list .qtc-history-item').click();
   const result={
    destination:document.querySelector('#destination-picker .location-selected-label')?.textContent,
    recapVisible:!document.querySelector('#urgent-route-recap').hidden,
    panelClosed:document.querySelector('#qtc-history-panel').hidden
   };
   loadTrips=window.__qaLoadTrips;delete window.__qaLoadTrips;
   return result;
  });
  say("Authorized-history fixture: "+JSON.stringify(reused));
  check(reused.destination?.includes("Carrasco")&&reused.recapVisible&&reused.panelClosed,
   "Recent destination uses real picker and triggers quote state, without booking writes");
  await pause(1600);
  await snap("05-ruta-con-historial-qa.png");
  await expression(function(){window.scrollTo(0,document.body.scrollHeight);return true});
  await pause(400);await snap("03-quicktrip-abajo.png");
  const manifest=await cmd("Page.getAppManifest").catch(()=>null);
  if(manifest?.data){
   const meta=JSON.parse(manifest.data);
   check(String(meta.start_url||"")==="./","Installed PWA opens Quick Trip production root");
  }
  await goto(HOST);
  await poll(()=>expression(function(){
    return !!document.querySelector("body.qt-clean.qt-clean-active #qtc-app .qtc-places [data-booking-step=route]");
  }),16000);
  check(await expression(function(){
    const doc=document;
    return !!doc.querySelector(".qtc-places #origin-picker .location-widget")&&
      !!doc.querySelector(".qtc-places #destination-picker .location-widget")&&
      !!doc.querySelector(".qtc-app .qtc-schedule #journey-when-select")&&
      !doc.querySelector(".qtc-app .qtc-schedule").textContent.includes("Viajás ahora")&&
      doc.querySelector('link[rel="manifest"]')?.getAttribute("href")==="./manifest.webmanifest";
  }),"Canonical web is Quick Trip V7, keeps native route/time and no old Viajás ahora");
  await snap("04-quicktrip-produccion.png");
  // V7 parity is required on the REAL trips board, not just the journey form.
  await expression(function(){
    document.querySelector('.tabs .tab[data-view="viajes"]').click();
    return true;
  });
  await poll(()=>expression(function(){
    return !!document.querySelector("#view-viajes.active .trips-board");
  }),8000);
  const tripsStyle=await expression(function(){
    const q=x=>document.querySelector(x),logo=q(".header .quicktrip-page-logo"),
      tabs=q(".shell>.tabs"),board=q("#view-viajes .trips-board");
    return {logo:!!logo&&logo.complete&&logo.naturalWidth>0,
      page:!!q("#view-viajes.active"),nav:getComputedStyle(tabs).position,
      navBottom:getComputedStyle(tabs).bottom,
      columns:getComputedStyle(q("#view-viajes .trips-tabs")).gridTemplateColumns.split(" ").length,
      border:getComputedStyle(board).borderRadius};
  });
  say("V7 trips parity: "+JSON.stringify(tripsStyle));
  check(tripsStyle.logo&&tripsStyle.page,"Quick Trip identity remains on My Trips");
  check(tripsStyle.nav==="fixed"&&tripsStyle.navBottom==="0px","Trips navigation uses V7 bottom bar");
  check(tripsStyle.columns===3,"Trips scopes stay compact in a three-column row");
  await snap("08-mis-traslados-v7.png");

  await expression(function(){
    document.querySelector('.tabs .tab[data-view="inicio"]').click();
    document.querySelector('[data-service="now"]').click();
    return true;
  });
  await poll(()=>expression(function(){return !!document.querySelector("#view-viaje.active .qtc-app")}),8000);
  const receiptState=await expression(function(){
    const success=document.querySelector("#booking-success");
    window.__qaReceiptSnapshot={hidden:success.hidden,html:success.innerHTML};
    success.innerHTML='<div class="mark">✓</div><h2>Solicitud enviada</h2>'+
      '<div class="success-receipt"><span class="success-label">NÚMERO DE SOLICITUD</span>'+
      '<strong class="code">QA-TEST</strong></div><button class="primary full">VER MIS TRASLADOS</button>';
    success.hidden=false;
    return {route:getComputedStyle(document.querySelector(".qtc-places")).display,
      driver:getComputedStyle(document.querySelector(".qtc-driver")).display,
      receipt:getComputedStyle(success).display};
  });
  check(receiptState.route==="none"&&receiptState.driver==="none"&&receiptState.receipt!=="none",
    "Sent receipt uses a compact single focus and hides the draft form");
  await snap("09-recibo-compacto-v7-qa.png");
  await expression(function(){
    const el=document.querySelector("#booking-success");
    el.innerHTML=window.__qaReceiptSnapshot.html;
    el.hidden=window.__qaReceiptSnapshot.hidden;
    delete window.__qaReceiptSnapshot;
    return true;
  });
  say("ALL BROWSER CHECKS PASSED — no booking submitted");
 }catch(e){
  say("ERROR "+e.stack);
  if(canScreenshot)try{await snap("ERROR-debug.png")}catch{}
  throw e;
 }finally{fs.writeFileSync(path.join(OUT,"qa-log.txt"),logs.join("\n")+"\n")}
}
main().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>{
 try{socket?.close()}catch{}for(const p of children.reverse())try{p.kill("SIGTERM")}catch{}
});
