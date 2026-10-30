"use strict";
/* Chromium 390px QA: uses an in-memory synthetic UI row, NEVER an API booking.
 * Captures the active-trip design and tests return-to-ride, accessibility and scroll. */
const cp=require("node:child_process"),fs=require("node:fs"),path=require("node:path");
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const OUT="artifacts/quicktrip-p13-approved";fs.mkdirSync(OUT,{recursive:true});
const children=[],waiting=new Map();let socket,seq=0;
function ok(v,name){if(!v)throw Error("FAIL: "+name);console.log("PASS BROWSER "+name)}
async function poll(fn,ms=18000){const end=Date.now()+ms;while(Date.now()<end){try{const v=await fn();if(v)return v}catch{}await sleep(200)}throw Error("Timeout");}
async function cmd(method,params={}){return await new Promise((resolve,reject)=>{const id=++seq;const timer=setTimeout(()=>{waiting.delete(id);reject(Error("CDP "+method+" timeout"))},20000);waiting.set(id,{resolve:v=>{clearTimeout(timer);resolve(v)},reject:e=>{clearTimeout(timer);reject(e)}});socket.send(JSON.stringify({id,method,params}));});}
async function js(fn){const r=await cmd("Runtime.evaluate",{expression:"("+fn.toString()+")()",awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result?.value;}
async function snap(name){const r=await cmd("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});fs.writeFileSync(path.join(OUT,name),Buffer.from(r.data,"base64"));}
async function main(){
 const chrome=["google-chrome","google-chrome-stable","chromium"].find(n=>cp.spawnSync("which",[n]).status===0);
 if(!chrome)throw Error("Chrome not installed");
 const server=cp.spawn("python3",["-m","http.server","8768","--bind","127.0.0.1","--directory","."],{stdio:"ignore"});children.push(server);
 await poll(async()=>{try{return (await fetch("http://127.0.0.1:8768/web-pasajero/")).ok}catch{return false}});
 const browser=cp.spawn(chrome,["--headless=new","--no-sandbox","--disable-gpu","--disable-dev-shm-usage",
 "--no-first-run","--disable-extensions","--remote-debugging-port=9239",
 "--user-data-dir=/tmp/qt-n1n2-"+process.pid,"--window-size=390,844","about:blank"],{stdio:"ignore"});children.push(browser);
 const wsUrl=await poll(async()=>{try{const list=await(await fetch("http://127.0.0.1:9239/json/list")).json();return list.find(x=>x.type==="page")?.webSocketDebuggerUrl}catch{return false}});
 socket=new WebSocket(wsUrl);
 await new Promise((resolve,reject)=>{socket.addEventListener("open",resolve,{once:true});socket.addEventListener("error",reject,{once:true})});
 socket.addEventListener("message",e=>{let x;try{x=JSON.parse(String(e.data))}catch{return}if(x.id&&waiting.has(x.id)){const p=waiting.get(x.id);waiting.delete(x.id);x.error?p.reject(Error(x.error.message)):p.resolve(x.result||{});}});
 await cmd("Page.enable");await cmd("Runtime.enable");
 await cmd("Emulation.setDeviceMetricsOverride",{width:390,height:844,deviceScaleFactor:1,mobile:true,screenWidth:390,screenHeight:844});
 await cmd("Emulation.setTouchEmulationEnabled",{enabled:true,maxTouchPoints:1});
 await cmd("Page.navigate",{url:"http://127.0.0.1:8768/web-pasajero/?ui=clean"});
 await poll(()=>js(function(){return document.readyState==="complete"&&Boolean(window.QuickTripActiveRide)&&Boolean(document.querySelector("#qtc-app")); }),24000);
 await sleep(550);
 // User-visible GPS welcome is unrelated to N1/N2; dismiss it through its
 // real manual-entry action before visual capture (NO location permission).
 await js(function(){document.getElementById("qt-gps-welcome-manual")?.click();return true;});
 const welcomeGone=await js(function(){
   const el=document.getElementById("qt-gps-welcome");
   return !el||el.hidden||getComputedStyle(el).display==="none";
 });
 ok(welcomeGone,"Welcome overlay dismissed before screenshot; real active UI visible");
 const initial=await js(function(){
   const hero=document.getElementById("quicktrip-live-trip");
   const qtc=document.getElementById("qtc-app");
   return hero?.hidden===true&&!!qtc&&getComputedStyle(qtc).display!=="none";
 });
 ok(initial,"No active ride means original booking screen is preserved");
 const etaBefore=await js(function(){
   const p=document.getElementById("pickup-presence");
   p.hidden=false;
   paintPickupCard({presence:"online",title:"Conductor conectado",
     subtitle:"Llegada aproximada hasta tu origen.",km:1.8,minutes:6,
     estimateNote:"Estimación visual QA, no GPS",phase:"ready"});
   const head=p.querySelector(".pickup-card-header");
   const metrics=document.getElementById("pickup-presence-metrics");
   return {node:p.parentElement?.classList.contains("qtc-driver-slot"),
     visible:getComputedStyle(p).display!=="none"&&p.getBoundingClientRect().height>0,
     header:getComputedStyle(head).display!=="none",
     metrics:getComputedStyle(metrics).display!=="none",
     text:document.getElementById("pickup-distance").textContent+" "+
       document.getElementById("pickup-minutes").textContent,
     aboveMap:document.querySelector(".qtc-driver").getBoundingClientRect().top <
       document.querySelector(".premium-route-canvas").getBoundingClientRect().top,
     driverTop:Math.round(document.querySelector(".qtc-driver").getBoundingClientRect().top),
     mapTop:Math.round(document.querySelector(".premium-route-canvas").getBoundingClientRect().top),
     owner:document.querySelector(".qtc-driver").parentElement?.id,
     identity:p===document.getElementById("pickup-presence")};
 });
 console.log("P0 ETA visibility diagnostic",JSON.stringify(etaBefore));
 await snap("00-eta-diagnostic-before-assert.png");
 ok(etaBefore.node&&etaBefore.visible&&etaBefore.header&&etaBefore.metrics&&
    etaBefore.text.includes("1,8")&&etaBefore.text.includes("min")&&
    etaBefore.aboveMap&&etaBefore.driverTop<844&&etaBefore.identity,
    "Route planning preserves original visible availability, pickup km and minutes");
 await js(function(){document.querySelector(".qtc-driver")?.scrollIntoView({block:"center"});return true;});
 await snap("00-compact-booking-eta-samsung-390.png");
 const form=await js(function(){
   const map=document.querySelector(".qtc-places .premium-route-canvas");
   const source=document.getElementById("pickup-presence");
   return {mapMounted:!!map,mapWidth:map?.clientWidth,mapHeight:map?.getBoundingClientRect().height,
      etaVisible:source?.getBoundingClientRect().height>0,
      quoteMounted:!!document.getElementById("urgent-route-recap"),
      sendMounted:!!document.getElementById("urgent-send"),
      contactMounted:!!document.querySelector(".qtc-contact"),
      scheduleMounted:!!document.querySelector(".qtc-schedule")};
 });
 ok(form.mapMounted&&form.mapWidth>220&&form.mapHeight<5&&
    form.etaVisible&&form.quoteMounted&&form.sendMounted&&form.contactMounted&&form.scheduleMounted,
    "One-screen form folds duplicate map but preserves source ETA, fare and submit");
 const photo=await js(function(){
   const q=s=>document.querySelector(s),o=q("#origin-picker"),d=q("#destination-picker");
   const a=o.getBoundingClientRect(),b=d.getBoundingClientRect();
   const op=getComputedStyle(o,"::before"),dp=getComputedStyle(d,"::before");
   return {
     loaded:Array.from(document.styleSheets).some(s=>(s.href||"").includes("quicktrip-p1-photo1.css")),
     pinA:parseFloat(op.width),pinB:parseFloat(dp.width),
     shaped:op.transform!=="none"&&dp.transform!=="none",
     connected:getComputedStyle(o,"::after").content!=="none",
     gap:b.top-a.bottom,aligned:Math.abs(a.left-b.left)<3,
     pickupNative:q("#pickup-presence").parentElement?.classList.contains("qtc-driver-slot"),
     pickupVisible:q("#pickup-presence").getBoundingClientRect().height>0,
     cardVisible:q(".qtc-driver").getBoundingClientRect().height>0,
     noOverflow:document.documentElement.scrollWidth-innerWidth<=6
   };
 });
 console.log("PHOTO 1 layout diagnostic",JSON.stringify(photo));
 ok(photo.loaded&&photo.pinA>=27&&photo.pinB>=27&&photo.shaped&&
    photo.connected&&photo.gap<=5&&photo.aligned&&photo.noOverflow,
    "Photo1 design: connected large pointed markers with compact shared origin and destination");
 ok(photo.pickupNative&&photo.pickupVisible&&photo.cardVisible,
    "Photo1 driver ETA remains one original, displayed source element");
 const relocation=await js(function(){
   const o=document.getElementById("origin-picker"),banner=document.createElement("section");
   banner.className="pickup-origin-drift-notice";
   const heading=document.createElement("strong");heading.textContent="Te alejaste del punto de recogida";
   const message=document.createElement("p");message.textContent="¿Querés actualizar tu origen con tu GPS actual?";
   const actions=document.createElement("div");actions.className="pickup-origin-drift-actions";
   for(const label of ["Actualizar origen","Mantener origen"]){
     const b=document.createElement("button");b.textContent=label;b.type="button";actions.append(b);
   }
   banner.append(heading,message,actions);o.after(banner);
   const heights=Array.from(actions.children).map(x=>x.getBoundingClientRect().height);
   const height=banner.getBoundingClientRect().height;banner.remove();
   return {height,heights};
 });
 ok(relocation.height<190&&relocation.heights.length===2&&relocation.heights.every(h=>h>=40),
    "Photo1 compact GPS drift notice retains both original action affordances");
 const price=await js(function(){
   const host=document.getElementById("urgent-actions"),recap=document.getElementById("urgent-route-recap");
   const prevHost=host.hidden,prevRecap=recap.hidden;
   host.hidden=false;recap.hidden=false;
   const x=Array.from(recap.querySelectorAll(".request-recap-metrics>span"));
   const a=x.map(c=>c.getBoundingClientRect());
   const data={fareAbove:a[2].bottom<=a[0].top+4&&a[2].bottom<=a[1].top+4,
     valuesFromNative:!!recap.querySelector("[data-final-route-distance]")&&
       !!recap.querySelector("[data-final-route-duration]")&&
       !!recap.querySelector("[data-final-route-fare]"),
     oneButton:document.querySelectorAll("#urgent-send").length===1,
     positions:a.map(x=>({top:x.top,bottom:x.bottom,left:x.left,width:x.width}))};
   host.hidden=prevHost;recap.hidden=prevRecap;return data;
 });
 console.log("P1.3 FARE GEOMETRY",JSON.stringify(price));
 ok(price.fareAbove&&price.valuesFromNative&&price.oneButton,
    "Photo1 financial hierarchy preserves source-bound route kilometers, minutes, fare and one action");
 await js(function(){document.querySelector(".qtc-driver")?.scrollIntoView({block:"center"});return true;});
 await snap("p1-01-driver-eta-mobile-390.png");
 const cta=await js(function(){
   const host=document.getElementById("urgent-actions");
   const button=document.getElementById("urgent-send");
   const wasHidden=host.hidden;
   // Layout-only fixture: no quotation or booking is submitted.
   host.hidden=false;
   const box=button.getBoundingClientRect();
   const nav=document.querySelector(".qtc-bottom-nav")?.getBoundingClientRect();
   const result={position:getComputedStyle(button).position,
     inViewport:box.width>200&&box.top>=0&&box.bottom<innerHeight,
     aboveNav:!nav||box.bottom<=nav.top+5,
     oneButton:document.querySelectorAll("#urgent-send").length===1};
   host.hidden=wasHidden;
   return result;
 });
 ok(cta.position==="static"&&cta.oneButton,
   "One original request button uses normal flow rather than covering source route metrics");
 const approvedMock=await js(function(){
   const q=s=>document.querySelector(s),r=e=>e?.getBoundingClientRect();
   const o=q("#origin-picker"),d=q("#destination-picker"),driver=q(".qtc-driver");
   const when=q(".qtc-schedule"),contact=q(".qtc-contact"),stops=q(".qtc-stops-slot"),fare=q(".qtc-submit-region");
   return {
     css:Array.from(document.styleSheets).some(x=>(x.href||"").includes("quicktrip-p13-approved.css")),
     greenPin:getComputedStyle(o,"::before").transform!=="none",
     coralPin:getComputedStyle(d,"::before").transform!=="none",
     connector:getComputedStyle(o,"::after").content!=="none",
     order:r(o).top<r(d).top&&r(d).top<=r(driver).top&&r(driver).top<r(when).top&&
       r(when).top<r(contact).top&&r(contact).top<r(stops).top&&r(stops).top<r(fare).top,
     stopButton:q(".premium-route-stops")?.parentElement===stops,
     stopForm:q("#stop-wrap")?.parentElement===stops,
     stopLogin:q("#login-for-stops")?.parentElement===stops,
     allNative:[q("#pickup-presence"),q("#pickup-distance"),q("#pickup-minutes"),
       q("#urgent-route-recap [data-final-route-distance]"),
       q("#urgent-route-recap [data-final-route-duration]"),
       q("#urgent-route-recap [data-final-route-fare]")].every(Boolean),
     oneSubmit:document.querySelectorAll("#urgent-send").length===1,
     oneStop:document.querySelectorAll(".premium-route-stops").length===1,
     width:document.documentElement.scrollWidth-innerWidth
   };
 });
 console.log("P1.3 APPROVED MOCK LAYOUT",JSON.stringify(approvedMock));
 ok(approvedMock.css&&approvedMock.greenPin&&approvedMock.coralPin&&approvedMock.connector&&approvedMock.order&&
    approvedMock.stopButton&&approvedMock.stopForm&&approvedMock.stopLogin&&approvedMock.allNative&&
    approvedMock.oneSubmit&&approvedMock.oneStop&&approvedMock.width<=6,
    "Approved photo: original pins, conductor ETA source, schedule, passenger, stops, route and one request button");
 const approvedPrice=await js(function(){
   const host=document.getElementById("urgent-actions"),recap=document.getElementById("urgent-route-recap");
   const old=[host.hidden,recap.hidden];host.hidden=false;recap.hidden=false;
   const boxes=Array.from(recap.querySelectorAll(".request-recap-metrics>span")).map(x=>x.getBoundingClientRect());
   const button=document.getElementById("urgent-send");
   const bb=button.getBoundingClientRect();
   const result={priceAbove:boxes[2].bottom<=boxes[0].top+4&&boxes[2].bottom<=boxes[1].top+4,
     sideBySide:Math.abs(boxes[0].top-boxes[1].top)<3&&boxes[1].left>boxes[0].left,
     buttonAfterMetrics:bb.top>=Math.max(boxes[0].bottom,boxes[1].bottom),
     fixed:getComputedStyle(button).position==="fixed"};
   host.hidden=old[0];recap.hidden=old[1];return result;
 });
 console.log("P1.3 APPROVED FARE HIERARCHY",JSON.stringify(approvedPrice));
 ok(approvedPrice.priceAbove&&approvedPrice.sideBySide&&approvedPrice.buttonAfterMetrics&&!approvedPrice.fixed,
    "Source fare in route header, native kilometers and minutes alongside; CTA never covers those values");
 const active=await js(function(){
   window.__qaOpened=0;window.__qaListed=0;
   window.QuickTripActiveRide.render([{id:"qa-synthetic-not-a-real-booking",
     status:"ACEPTADA",pickup_phase:"EN_CAMINO",
     origin_text:"Origen de prueba · QA",destination_text:"Destino de prueba · QA",
     quote_final_total:764}],{onOpen:()=>{window.__qaOpened++},
       onList:()=>{window.__qaListed++}});
   // The existing visual observer re-parents the SAME original ETA card once
   // the active ride class folds the new-booking form.
   return new Promise(resolve=>requestAnimationFrame(()=>{
     const q=x=>document.querySelector(x);
     resolve({
       title:q("#quicktrip-live-trip .qt-live-title")?.textContent,
       price:q(".qt-live-fare")?.textContent,
       knob:!!q(".qt-live-knob"),
       rail:!!q(".qt-live-rail"),
       manualMapButton:!!q(".qt-live-tap"),
       newTrip:!!q(".qt-live-new-trip"),
       homeLink:!!q(".qt-live-bottom-links .qt-live-nav-link"),
       appVisible:getComputedStyle(q("#qtc-app")).display!=="none",
       routeHidden:getComputedStyle(q(".qtc-places")).display==="none",
       driverVisible:getComputedStyle(q(".qtc-driver")).display!=="none" &&
         q(".qtc-driver").getBoundingClientRect().height>0,
       etaVisible:getComputedStyle(q("#pickup-presence")).display!=="none" &&
         getComputedStyle(q("#pickup-presence .pickup-card-header")).display!=="none",
       metricsVisible:getComputedStyle(q("#pickup-presence-metrics")).display!=="none",
       metricsCanRender:(()=>{
         const el=q("#pickup-presence-metrics"),saved=el.hidden;
         el.hidden=false;
         const visible=getComputedStyle(el).display!=="none"&&el.getBoundingClientRect().height>0;
         el.hidden=saved;return visible;
       })(),
       metricsSourcePresent:!!q("#pickup-distance")&&!!q("#pickup-minutes"),
       metricsHiddenBySource:q("#pickup-presence-metrics").hidden,
       overflow:document.documentElement.scrollWidth-innerWidth,
       cardWidth:q("#quicktrip-live-trip").getBoundingClientRect().width,
       parent:q(".qtc-driver").parentElement?.className
     });
   }));
 });
 console.log("P0 active visibility diagnostic",JSON.stringify(active));
 ok(active.title==="Tu viaje activo"&&active.price.includes("764"),"Live ride title and source-only fare");
 ok(!active.knob&&!active.rail&&!active.manualMapButton&&active.newTrip&&active.homeLink,
   "No swipe, no manual map button: retain route info, explicit new booking and navigation");
 ok(active.appVisible&&active.routeHidden&&active.driverVisible&&active.etaVisible&&active.metricsCanRender&&active.metricsSourcePresent,
   "Active ride preserves original ETA fields without displaying stale prebooking numbers");
 ok(active.overflow<=6&&active.cardWidth<=390,"No Samsung horizontal overflow");
 await snap("01-active-trip-synthetic-samsung-390.png");
 const manual=await js(function(){return window.__qaOpened;});
 ok(manual===0,"Displaying accepted card never opens map without verified acceptance event");
 const newTrip=await js(function(){
   document.querySelector(".qt-live-new-trip").click();
   const visible=getComputedStyle(document.querySelector(".qtc-places")).display!=="none";
   document.querySelector(".qt-live-new-trip").click();
   const hidden=getComputedStyle(document.querySelector(".qtc-places")).display==="none";
   const driverStillVisible=getComputedStyle(document.querySelector(".qtc-driver")).display!=="none";
   return {visible,hidden,driverStillVisible};
 });
 ok(newTrip.visible&&newTrip.hidden&&newTrip.driverStillVisible,
   "New trip toggles form but never hides the real conductor/ETA area");
 const unavailable=await js(function(){
   const p=document.getElementById("pickup-presence");
   p.hidden=false;
   paintPickupCard({presence:"busy",title:"Recogida por confirmar",
     subtitle:"No podemos asegurar una recogida ahora.",phase:"blocked"});
   return new Promise(resolve=>requestAnimationFrame(()=>{
     resolve({visible:getComputedStyle(p).display!=="none"&&p.getBoundingClientRect().height>0,
       header:getComputedStyle(p.querySelector(".pickup-card-header")).display!=="none",
       title:document.getElementById("pickup-presence-title").textContent,
       duplicate:!document.querySelector(".qtc-driver-unavailable")?.hidden,
       spinner:!document.querySelector(".qtc-driver-search")?.hidden});
   }));
 });
 ok(unavailable.visible&&unavailable.header&&unavailable.title==="Recogida por confirmar"&&
    !unavailable.duplicate&&!unavailable.spinner,
    "Unavailable pickup keeps backend-state wording and never shows a false denial");
 await js(function(){document.querySelector(".qtc-driver")?.scrollIntoView({block:"center"});return true;});
 await snap("03-unavailable-no-false-denial-samsung-390.png");
 const networkIssue=await js(function(){
   paintPickupCard({presence:"unknown",title:"No pudimos comprobar la disponibilidad",
     subtitle:"Reintentaremos la consulta.",phase:"issue"});
   const p=document.getElementById("pickup-presence");
   return {visible:getComputedStyle(p).display!=="none",
     label:document.getElementById("pickup-presence-title").textContent};
 });
 ok(networkIssue.visible&&networkIssue.label.includes("No pudimos comprobar"),
    "Transport failure stays distinct from real unavailable status");
 const ambiguous=await js(function(){
   const shared={status:"EN_VIAJE"};
   window.QuickTripActiveRide.render([{...shared,id:"qa-one"},{...shared,id:"qa-two"}],{
     onOpen:()=>{window.__qaOpened++},onList:()=>{window.__qaListed++}});
   return {title:document.querySelector(".qt-live-title")?.textContent,
     slider:!!document.querySelector(".qt-live-knob"),
     list:!!document.querySelector(".qt-live-secondary")};
 });
 ok(ambiguous.title==="Tus viajes activos"&&!ambiguous.slider&&ambiguous.list,
   "Multiple real journeys never silently choose one");
 const back=await js(function(){
   document.querySelector('.tab[data-view="viajes"]')?.click();
   window.dispatchEvent(new CustomEvent("quicktrip:tracking-closed"));
   return Boolean(document.getElementById("view-inicio").classList.contains("active"));
 });
 ok(back,"Tracking-closed returns to Inicio without map reentry");
 await snap("02-active-flow-return-samsung-390.png");
 const empty=await js(function(){
   window.QuickTripActiveRide.render([]);
   return document.getElementById("quicktrip-live-trip").hidden&&
     !document.getElementById("view-viaje").classList.contains("qt-has-active-ride");
 });
 ok(empty,"Terminal / empty state restores initial booking layout");
 const scheduled=await js(function(){
   document.querySelector('[data-service="schedule"]').click();
   return new Promise(resolve=>requestAnimationFrame(()=>resolve({
     view:document.getElementById("view-reservar").classList.contains("active"),
     driverHome:document.querySelector(".qtc-driver").parentElement?.classList.contains("qtc-content"),
     oneDriver:document.querySelectorAll(".qtc-driver").length===1,
     oneMap:document.querySelectorAll(".premium-route-canvas").length===1,
     onePresence:document.querySelectorAll("#pickup-presence").length===1
   })));
 });
 console.log("P0 scheduled transition diagnostic",JSON.stringify(scheduled));
 const originalStops=await js(function(){
   const par=id=>{const e=typeof id==="string"?document.querySelector(id):id;
     return {parent:e?.parentElement?.id||e?.parentElement?.className||null,
       previous:e?.previousSibling?.nodeType===8?e.previousSibling.textContent:null};};
   const button=par(".premium-route-stops"),form=par("#stop-wrap"),login=par("#login-for-stops");
   return {button,form,login,view:document.body.className,pass:
     button.parent==="booking-step-route"&&form.parent==="booking-step-route"&&
     login.parent==="booking-step-route"};
 });
 console.log("P1.3 SCHEDULED NATIVE STOPS",JSON.stringify(originalStops));
 ok(originalStops.pass,"Programar restores original native stop button, inputs, login hint and events");
 ok(scheduled.view&&scheduled.driverHome&&scheduled.oneDriver&&scheduled.oneMap&&scheduled.onePresence,
    "Scheduled booking restores native driver layout, without duplicate map or presence");
 const returned=await js(function(){
   document.querySelector('[data-service="now"]').click();
   return new Promise(resolve=>requestAnimationFrame(()=>resolve({
     view:document.getElementById("view-viaje").classList.contains("active"),
     driverAboveMap:document.querySelector(".qtc-driver").getBoundingClientRect().top <
       document.querySelector(".premium-route-canvas").getBoundingClientRect().top,
     mapCount:document.querySelectorAll(".premium-route-canvas").length,
     pickupCount:document.querySelectorAll("#pickup-presence").length
   })));
 });
 const movedAgain=await js(function(){
   return document.querySelector(".premium-route-stops")?.parentElement?.classList.contains("qtc-stops-slot")&&
     document.getElementById("stop-wrap")?.parentElement?.classList.contains("qtc-stops-slot");
 });
 ok(movedAgain,"Tu viaje reuses the original stop controls in the approved location on return");
 ok(returned.view&&returned.driverAboveMap&&returned.mapCount===1&&returned.pickupCount===1,
    "Quick journey restores same driver ahead of route map after scheduled view");
 const camera=await js(function(){
   const host=document.createElement("div");
   host.style.cssText="width:390px;height:660px;position:fixed;left:0;top:0;z-index:20000";
   host.id="qt-qa-camera-only";document.body.append(host);
   const route=[[-34.897,-56.192],[-34.895,-56.19],[-34.891,-56.184]];
   const opts={routeMode:"ride",height:660,fullscreen:true,followVehicle:true,
     cameraPosition:{lat:-34.897,lng:-56.192}};
   window.TrasladosPremiumMap.render(host,route,opts);
   const state=()=>{const f=host.querySelector(".premium-real-map");return {
     following:f?.dataset.followVehicle,zoom:f?.dataset.zoom,
     manual:f?.dataset.manual,controls:host.querySelectorAll(".premium-map-control").length
   }};
   const initial=state();
   host.querySelector(".premium-map-control-zoom-in")?.click();
   const zoom=state();
   host.querySelector(".premium-map-control-reset")?.click();
   const reset=state();
   host.querySelector(".premium-map-control-explore")?.click();
   const explore=state();
   host.querySelector(".premium-map-control-route-all")?.click();
   const whole=state();
   host.remove();
   return {initial,zoom,reset,explore,whole};
 });
 ok(camera.initial.following==="true"&&camera.initial.zoom==="16"&&
    camera.initial.controls>=5&&camera.zoom.following==="false"&&
    camera.reset.following==="true"&&camera.explore.following==="false"&&
    camera.whole.manual==="true",
    "Live-car framing starts close, manual freezes it, recenter resumes, full route fits");
 const reentry=await js(function(){
   const original=refreshPickupPresence,mode=selectedPickupMode;
   let calls=0;
   try{
     refreshPickupPresence=async()=>{calls++;};
     lastPickupReturnRefreshAt=0;
     selectedPickupMode="now";
     shiftView("inicio");
     const before=calls;
     shiftView("viaje");
     const afterReturn=calls;
     document.dispatchEvent(new Event("visibilitychange"));
     window.dispatchEvent(new Event("online"));
     const afterBurst=calls;
     lastPickupReturnRefreshAt=0;
     document.dispatchEvent(new Event("visibilitychange"));
     const afterForeground=calls;
     lastPickupReturnRefreshAt=0;
     const ev=new Event("pageshow");
     Object.defineProperty(ev,"persisted",{value:true});
     window.dispatchEvent(ev);
     const afterBfcache=calls;
     selectedPickupMode="schedule";
     lastPickupReturnRefreshAt=0;
     window.dispatchEvent(new Event("online"));
     const afterScheduled=calls;
     return {before,afterReturn,afterBurst,afterForeground,afterBfcache,afterScheduled};
   }finally{refreshPickupPresence=original;selectedPickupMode=mode;}
 });
 console.log("P1.1 REENTRY",JSON.stringify(reentry));
 ok(reentry.afterReturn===reentry.before+1&&reentry.afterBurst===reentry.afterReturn&&
    reentry.afterForeground===reentry.afterBurst+1&&
    reentry.afterBfcache===reentry.afterForeground+1&&
    reentry.afterScheduled===reentry.afterBfcache,
    "Returning to Tu viaje refreshes original pickup adapter without duplication or future-booking requests");
 const copy=await js(function(){
   const host=document.getElementById("pickup-presence");host.hidden=false;
   paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
     subtitle:"Llegada aproximada hasta tu origen.",km:2.1,minutes:7,
     estimateNote:"Estimación obtenida hace 38 s · Puede variar.",
     checked:"Disponibilidad comprobada ahora · La recogida requiere confirmación del conductor",
     phase:"ready"});
   const meta=document.getElementById("pickup-presence-meta");
   const note=document.getElementById("pickup-estimate-note");
   const metrics=document.getElementById("pickup-presence-metrics");
   return {metaHidden:getComputedStyle(meta).display==="none",
     noteHidden:getComputedStyle(note).display==="none",
     metricsVisible:getComputedStyle(metrics).display!=="none"&&!metrics.hidden,
     distance:document.getElementById("pickup-distance").textContent,
     arrival:document.getElementById("pickup-minutes").textContent,
     a11y:document.getElementById("pickup-presence-status").textContent};
 });
 console.log("P1.1 CLEAN CARD",JSON.stringify(copy));
 ok(copy.metaHidden&&copy.noteHidden&&copy.metricsVisible&&copy.distance.includes("2,1")&&
    copy.arrival.includes("min")&&copy.a11y.includes("Estimación obtenida"),
    "Hide only redundant freshness copy; original ETA kilometers/minutes and accessible status remain");
 await js(function(){document.querySelector(".qtc-driver")?.scrollIntoView({block:"center"});return true;});
 await snap("p11-reentry-clean-driver-card-390.png");
 // QA-only direct acceptance transition: the server-originated status change
 // automatically opens fullscreen tracking. No click, swipe, fake GPS or RPC.
 const acceptance=await js(function(){
   const previous=openFullscreenTracking;let opened=0;
   const recent=new Date().toISOString();
   const code="qa-p12-acceptance-only-virtual";
   const base={id:code,created_at:recent,pickup_mode:"now",
     status:"PENDIENTE",__guestToken:"qa-only-not-a-real-token",
     origin_lat:-34.88,origin_lng:-56.13,destination_lat:-34.9,destination_lng:-56.15};
   const accepted={...base,status:"ACEPTADA"};
   const future={...accepted,id:code+"-future",pickup_mode:"schedule"};
   const finished={...accepted,id:code+"-done",status:"FINALIZADA"};
   const savedKey=premiumTrackingKey;
   try{
     shiftView("viaje");
     document.getElementById("trip-alert-overlay").hidden=true;
     openFullscreenTracking=()=>{opened++;return true;};
     const pending=autoFollowAccepted([base],[]);
     const futureOpened=autoFollowAccepted([future],[{...future,status:"PENDIENTE"}]);
     const acceptedOpened=autoFollowAccepted([accepted],[base]);
     const afterAcceptance=opened;
     const duplicate=autoFollowAccepted([accepted],[base]);
     const finishedOpened=autoFollowAccepted([finished],[accepted]);
     return {pending,futureOpened,acceptedOpened,afterAcceptance,
       duplicate,finishedOpened,opened};
   }finally{
     openFullscreenTracking=previous;
     premiumTrackingKey=savedKey;
   }
 });
 console.log("P1.2 auto-confirmation diagnostic",JSON.stringify(acceptance));
 ok(!acceptance.pending&&!acceptance.futureOpened&&acceptance.acceptedOpened&&
    acceptance.afterAcceptance===1&&!acceptance.duplicate&&
    !acceptance.finishedOpened&&acceptance.opened===1,
    "Server-confirmed immediate acceptance alone opens map exactly once; pending/scheduled/finished do not");
 console.log("P1.3 APPROVED DESIGN QA PASS — no real GPS or reservations");
}
main().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>{
 try{socket?.close()}catch{}
 for(const p of children.reverse())try{p.kill("SIGTERM")}catch{}
});
