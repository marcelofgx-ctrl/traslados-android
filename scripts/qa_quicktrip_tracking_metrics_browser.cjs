"use strict";
/* Chromium 390px QA: uses an in-memory synthetic UI row, NEVER an API booking.
 * Captures the active-trip design and tests return-to-ride, accessibility and scroll. */
const cp=require("node:child_process"),fs=require("node:fs"),path=require("node:path");
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const OUT="artifacts/quicktrip-tracking-metrics";fs.mkdirSync(OUT,{recursive:true});
const children=[],waiting=new Map();let socket,seq=0;
function ok(v,name){if(!v)throw Error("FAIL: "+name);console.log("PASS BROWSER "+name)}
async function poll(fn,ms=18000){const end=Date.now()+ms;while(Date.now()<end){try{const v=await fn();if(v)return v}catch{}await sleep(200)}throw Error("Timeout");}
async function cmd(method,params={}){return await new Promise((resolve,reject)=>{const id=++seq;const timer=setTimeout(()=>{waiting.delete(id);reject(Error("CDP "+method+" timeout"))},20000);waiting.set(id,{resolve:v=>{clearTimeout(timer);resolve(v)},reject:e=>{clearTimeout(timer);reject(e)}});socket.send(JSON.stringify({id,method,params}));});}
async function js(fn){const r=await cmd("Runtime.evaluate",{expression:"("+fn.toString()+")()",awaitPromise:true,returnByValue:true,userGesture:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result?.value;}
async function snap(name){const r=await cmd("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});fs.writeFileSync(path.join(OUT,name),Buffer.from(r.data,"base64"));}
async function main(){
 const chrome=["google-chrome","google-chrome-stable","chromium"].find(n=>cp.spawnSync("which",[n]).status===0);
 if(!chrome)throw Error("Chrome not installed");
 const server=cp.spawn("python3",["-m","http.server","8771","--bind","127.0.0.1","--directory","."],{stdio:"ignore"});children.push(server);
 await poll(async()=>{try{return (await fetch("http://127.0.0.1:8771/web-pasajero/")).ok}catch{return false}});
 const browser=cp.spawn(chrome,["--headless=new","--no-sandbox","--disable-gpu","--disable-dev-shm-usage",
 "--no-first-run","--disable-extensions","--remote-debugging-port=9247",
 "--user-data-dir=/tmp/qt-n1n2-"+process.pid,"--window-size=390,844","about:blank"],{stdio:"ignore"});children.push(browser);
 const wsUrl=await poll(async()=>{try{const list=await(await fetch("http://127.0.0.1:9247/json/list")).json();return list.find(x=>x.type==="page")?.webSocketDebuggerUrl}catch{return false}});
 socket=new WebSocket(wsUrl);
 await new Promise((resolve,reject)=>{socket.addEventListener("open",resolve,{once:true});socket.addEventListener("error",reject,{once:true})});
 socket.addEventListener("message",e=>{let x;try{x=JSON.parse(String(e.data))}catch{return}if(x.id&&waiting.has(x.id)){const p=waiting.get(x.id);waiting.delete(x.id);x.error?p.reject(Error(x.error.message)):p.resolve(x.result||{});}});
 await cmd("Page.enable");await cmd("Runtime.enable");
 await cmd("Emulation.setDeviceMetricsOverride",{width:390,height:844,deviceScaleFactor:1,mobile:true,screenWidth:390,screenHeight:844});
 await cmd("Emulation.setTouchEmulationEnabled",{enabled:true,maxTouchPoints:1});
 await cmd("Page.navigate",{url:"http://127.0.0.1:8771/web-pasajero/?ui=clean"});
 await poll(()=>js(function(){return document.readyState==="complete"&&Boolean(window.QuickTripActiveRide)&&Boolean(document.querySelector("#qtc-app")); }),24000);
 await sleep(550);
  const setup=await js(function(){
    const ready=!!window.QuickTripMapFocus?.showTracking&&!!window.TrasladosReservationLive?.mount;
    if(!ready)return {ready};
    const flags={mode:"waiting",calls:0,routeCalls:[],overviewCalls:0};
    const routeFor=points=>{
      const isContract=points[0].lat===-34.9;
      return {
        valid:true,source:"qa-only-source-not-real",
        geometry:points.map(p=>[p.lat,p.lng]),
        distanceKm:isContract?15.4:2.7,
        durationMin:isContract?26:6
      };
    };
    const engine={
      estimate:async points=>{flags.routeCalls.push(points.map(p=>[p.lat,p.lng]));return routeFor(points);},
      verified:route=>route?.valid===true&&Array.isArray(route.geometry)&&route.geometry.length>=2
    };
    const renderer={
      render:(node,geometry)=>{
        const div=document.createElement("div");
        div.className="premium-real-map";div.dataset.source="road";
        div.textContent="QA ONLY — synthetic polyline (no network)";
        node.replaceChildren(div);
        return true;
      },
      overview:(node)=>{flags.overviewCalls++;const v=document.createElement("div");v.className="premium-real-map";v.dataset.source="verified-endpoints";v.textContent="QA endpoints only (no car or invented road)";node.replaceChildren(v);return true}
    };
    const rpc=async(name)=>{
      if(name!=="customer_reservation_position_v1")throw Error("Unexpected RPC "+name);
      flags.calls++;
      if(flags.mode==="fresh")return {
        available:true,driverLat:-34.87,driverLng:-56.13,gpsAgeSec:4
      };
      return {available:false,reason:"no_recent_gps"};
    };
    const reservation={
      id:"qa-contract-metrics-not-a-real-id",status:"ACEPTADA",pickup_phase:"SIN_INICIAR",
      origin_lat:-34.9,origin_lng:-56.1,
      destination_lat:-34.89,destination_lng:-56.19,
      origin_text:"AVENIDA INGENIERO LUIS GIANNATTASIO 9000",destination_text:"Aeropuerto Internacional de Carrasco · Terminal de pasajeros",quote_final_total:616
    };
    window.__qtVisual={flags,reservation,engine,renderer,rpc};
    const opened=window.QuickTripMapFocus.showTracking({
      reservation,guestToken:"qa-test-not-a-real-guest-token",
      rpc,engine,renderer,api:null,key:null
    });
    return {ready,opened,immediateMap:!!document.querySelector("#qt-focus .reservation-live-map .premium-real-map[data-source=\"verified-endpoints\"]"),overviewCalls:flags.overviewCalls};
  });
  ok(setup.ready&&setup.opened&&setup.immediateMap&&setup.overviewCalls>=1,
      "P2.2 shows verified-origin/destination map synchronously, before routing/GPS");
  // P2.3 mobile regression: long Uruguayan addresses remain fully visible,
  // with right-hand clearance for the floating assistant icon.
  for(const width of [360,390,412]){
    await cmd("Emulation.setDeviceMetricsOverride",{width,height:844,
      deviceScaleFactor:1,mobile:true,screenWidth:width,screenHeight:844});
    await sleep(135);
    const layout=await js(function(){
      const o=document.querySelector("#qt-focus");
      const card=o.querySelector(".qt-focus-tracking-route");
      const origin=o.querySelector(".qt-focus-tracking-origin-text");
      const dest=o.querySelector(".qt-focus-tracking-destination-text");
      const rect=e=>{const r=e.getBoundingClientRect();
        return {top:r.top,bottom:r.bottom,left:r.left,right:r.right}};
      return {width:innerWidth,card:rect(card),origin:rect(origin),dest:rect(dest),
        scrollHeight:card.scrollHeight,clientHeight:card.clientHeight,
        paddingRight:getComputedStyle(card).paddingRight,
        visible:getComputedStyle(card).display!=="none",
        originText:origin.textContent,destText:dest.textContent,
        stylesheet:[...document.styleSheets].some(sheet=>(sheet.href||"").includes("quicktrip-p23-follow-leg-readability.css"))};
    });
    console.log("P23 ADDRESS LAYOUT",JSON.stringify(layout));
    ok(layout.stylesheet&&layout.visible&&layout.scrollHeight<=layout.clientHeight+2&&
      layout.dest.bottom<=layout.card.bottom+2&&
      layout.origin.top>=layout.card.top&&
      layout.card.right-layout.dest.right>=59&&
      layout.originText.includes("GIANNATTASIO 9000")&&
      layout.destText.includes("Terminal de pasajeros"),
      "P2.3 origin and destination fully visible, icon clearance at "+width+"px");
    await snap("p23-tracking-addresses-"+width+".png");
  }
  await cmd("Emulation.setDeviceMetricsOverride",{width:390,height:844,
    deviceScaleFactor:1,mobile:true,screenWidth:390,screenHeight:844});
  const read=()=>js(function(){
    const overlay=document.getElementById("qt-focus");
    const e=selector=>overlay.querySelector(selector);
    const cells=overlay.querySelectorAll(".qt-focus-stat small");
    return {
      distance:e(".qt-focus-distance")?.textContent,
      duration:e(".qt-focus-time")?.textContent,
      distanceTitle:cells[0]?.textContent,
      durationTitle:cells[1]?.textContent,
      source:overlay.dataset.metricsSource,
      mode:window.__qtVisual.flags.mode,
      calls:window.__qtVisual.flags.calls,
      status:e(".qt-focus-sheet-heading h2")?.textContent,
      kicker:e(".qt-focus-kicker")?.textContent,
      routeCalls:window.__qtVisual.flags.routeCalls,
      carVisible:!!e(".reservation-live-map .premium-real-map"),
      mapSource:e(".reservation-live-map .premium-real-map")?.dataset.source,
      overviewCalls:window.__qtVisual.flags.overviewCalls
    };
  });
  // P2.4: show complete addresses briefly, then compact automatically
  // without swallowing the user's own manual expansion.
  const compact=await poll(()=>js(function(){
    const root=document.querySelector("#qt-focus");
    const button=root.querySelector(".qt-focus-route-toggle");
    const card=root.querySelector(".qt-focus-tracking-route");
    return root.classList.contains("qt-route-compact")?
      {expanded:button.getAttribute("aria-expanded"),height:card.getBoundingClientRect().height,
       text:button.textContent}:null;
  }),5500);
  console.log("P24 HEADER COMPACT",JSON.stringify(compact));
  ok(compact.expanded==="false"&&compact.height<=52&&
    compact.text.includes("Ver origen y destino"),
    "P2.4 addresses collapse once after 3.3 seconds, freeing map space");
  const manual=await js(function(){
    const root=document.querySelector("#qt-focus");
    const button=root.querySelector(".qt-focus-route-toggle");
    button.click();
    return {expanded:button.getAttribute("aria-expanded"),
      compact:root.classList.contains("qt-route-compact")};
  });
  ok(manual.expanded==="true"&&!manual.compact,
    "P2.4 compact header is manually expandable with accessible state");
  const booked=await poll(async()=>{
    const x=await read();return x.source==="contract"?x:null;
  },9000);
  console.log("BOOKED QA ONLY",JSON.stringify(booked));
  ok(booked.distance==="15,4 km"&&booked.duration==="26 min"&&
      booked.distanceTitle==="DISTANCIA DEL VIAJE"&&
      booked.durationTitle==="DURACIÓN DEL VIAJE"&&booked.calls>=1,
      "Verified booked road route immediately feeds travel metrics without any driver GPS");
  ok(booked.kicker==="RECOGIDA · GPS PENDIENTE",
     "Before EN_CAMINO no GPS is claimed even with an accepted booking");
  await snap("01-booked-trip-metrics-no-gps-390.png");
  const immediate=await js(function(){
    const v=window.__qtVisual;
    v.flags.mode="fresh";
    const next={...v.reservation,pickup_phase:"EN_CAMINO"};
    v.reservation=next;
    const previous=v.flags.calls;
    const length=history.length;
    const changed=window.QuickTripMapFocus.updateTracking(next);
    return {changed,previous,length};
  });
  ok(immediate.changed,"EN_CAMINO triggers the same authorized tracker, not only a title");
  const live=await poll(async()=>{
    const x=await read();return x.source==="live"?x:null;
  },2500);
  console.log("LIVE GPS QA ONLY",JSON.stringify(live));
  ok(live.calls>immediate.previous&&live.kicker==="RECOGIDA · GPS REAL"&&
     live.status==="Conductor en camino"&&
     live.routeCalls.some(points=>points[0][0]===-34.87&&points[1][0]===-34.9),
     "EN_CAMINO requests GPS immediately and routes driver to passenger origin");
  ok(live.distance==="2,7 km"&&live.duration==="6 min"&&
     live.distanceTitle==="A RECOGIDA"&&live.durationTitle==="LLEGADA ESTIMADA",
     "Authenticated GPS leg replaces booked distance with explicitly labeled pickup km and ETA");
  const p24Marker=await js(function(){
    // Local synthetic GPS/route only. No external driver data or reservations.
    const root=document.querySelector("#qt-focus");
    const scratch=document.createElement("div");
    scratch.className="qa-p24-marker-preview";
    scratch.style.cssText="position:absolute;left:-10000px;top:0;width:390px;height:440px";
    root.querySelector(".qt-focus-map").append(scratch);
    const r=window.TrasladosPremiumMap.render(scratch,
      [[-34.9,-56.1],[-34.89,-56.19]],{
      height:440,fullscreen:true,routeMode:"pickup",
      secondaryGeometry:[[-34.87,-56.13],[-34.9,-56.1]],
      driverPosition:{lat:-34.87,lng:-56.13}
    });
    const flow=scratch.querySelector(".premium-map-approach-flow");
    const car=scratch.querySelector(".premium-map-driver");
    const info={rendered:r,flow:!!flow,car:!!car,
      carElements:car?.querySelectorAll("rect,path,circle")?.length||0,
      animation:flow?getComputedStyle(flow).animationName:null,
      solid:!!scratch.querySelector(".premium-map-approach")};
    window.TrasladosPremiumMap.render(scratch,
      [[-34.9,-56.1],[-34.89,-56.19]],{height:440,routeMode:"contract"});
    info.contractNoFakeCar=!scratch.querySelector(".premium-map-driver,.premium-map-approach-flow");
    scratch.remove();
    return info;
  });
  console.log("P24 ROUTE MOTION AND CAR",JSON.stringify(p24Marker));
  ok(p24Marker.rendered&&p24Marker.flow&&p24Marker.car&&
    p24Marker.carElements>=6&&p24Marker.solid&&
    p24Marker.animation==="qt-p24-flow"&&p24Marker.contractNoFakeCar,
    "P2.4 animated verified pickup flow and vector car; no fake car without GPS");
  const manualRetained=await js(function(){
    const r=document.querySelector("#qt-focus");
    return {compact:r.classList.contains("qt-route-compact"),
      aria:r.querySelector(".qt-focus-route-toggle").getAttribute("aria-expanded")};
  });
  ok(!manualRetained.compact&&manualRetained.aria==="true",
    "P2.4 respects manual address expansion across EN_CAMINO remount");
  await snap("02-driver-pickup-metrics-mocked-gps-390.png");
  await js(function(){window.__qtVisual.flags.mode="waiting";return true;});
  const returned=await poll(async()=>{
    const x=await read();return x.source==="contract"&&x.calls>live.calls?x:null;
  },12000);
  console.log("GPS STALE QA ONLY",JSON.stringify(returned));
  ok(returned.kicker==="RECOGIDA · GPS PENDIENTE"&&
     returned.distance==="15,4 km"&&returned.duration==="26 min"&&
    returned.distanceTitle==="DISTANCIA DEL VIAJE"&&
    returned.durationTitle==="DURACIÓN DEL VIAJE",
    "GPS expires: remove stale pickup km/ETA; display only authentic booked trip metrics");
  await snap("03-private-gps-stale-booked-route-only-390.png");
  // The arrival transition and trip start must each use the SAME live GPS
  // service, changing only the destination leg after EN_VIAJE.
  const towardRide=await js(function(){
    const v=window.__qtVisual;
    v.flags.mode="fresh";
    const arrived={...v.reservation,pickup_phase:"EN_PUNTO"};
    const result1=window.QuickTripMapFocus.updateTracking(arrived);
    v.reservation=arrived;
    return {result1,immediateMap:!!document.querySelector("#qt-focus .reservation-live-map .premium-real-map[data-source=\"verified-endpoints\"]")};
  });
  ok(towardRide.result1&&towardRide.immediateMap,
     "P2.2 EN_PUNTO transition NEVER produces a blank map before GPS or route promises resolve");
  const atOrigin=await poll(async()=>{
    const x=await read();return x.status==="En tu recogida"&&x.source==="live"?x:null;
  },2500);
  ok(atOrigin.kicker==="RECOGIDA · GPS REAL",
    "Arrival keeps verified pickup GPS tracking active");
  const started=await js(function(){
    const v=window.__qtVisual;
    const ride={...v.reservation,status:"EN_VIAJE"};
    v.reservation=ride;
    return window.QuickTripMapFocus.updateTracking(ride);
  });
  ok(started,"EN_VIAJE changes to passenger destination leg");
  const onboard=await poll(async()=>{
    const x=await read();return x.status==="Viaje en curso"&&x.source==="live"?x:null;
  },2600);
  ok(onboard.kicker==="EN VIAJE · GPS REAL"&&
     onboard.distanceTitle==="AL DESTINO"&&
     onboard.routeCalls.some(points=>points[0][0]===-34.87&&points[1][0]===-34.89),
     "Trip start keeps same tracker but changes active route to destination");
  const close=await js(function(){
    window.QuickTripMapFocus.close(true);
    return !window.QuickTripMapFocus.isTrackingOpen();
  });
  ok(close,"Tracker closes and clears its private GPS timer without altering reservations");
  const result={suite:"Quick Trip private tracking fallback",pass:true,
    backendRequests:false,roadProviderRequests:false,liveGpsVerified:false,
    testStages:["booked verified route","mock authorized GPS pickup","stale GPS fallback"],
    snapshots:["01-booked-trip-metrics-no-gps-390.png",
      "02-driver-pickup-metrics-mocked-gps-390.png",
      "03-private-gps-stale-booked-route-only-390.png"]};
  fs.writeFileSync(path.join(OUT,"tracking-metrics-qa.json"),JSON.stringify(result,null,2)+"\n");
  console.log("PASS: all contract vs GPS leg metrics stable, no fake live GPS or Supabase calls");
}
main().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>{
 try{socket?.close()}catch{}
 for(const p of children.reverse())try{p.kill("SIGTERM")}catch{}
});
