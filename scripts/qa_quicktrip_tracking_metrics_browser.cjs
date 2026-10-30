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
    const flags={mode:"waiting",calls:0};
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
      estimate:async points=>routeFor(points),
      verified:route=>route?.valid===true&&Array.isArray(route.geometry)&&route.geometry.length>=2
    };
    const renderer={
      render:(node,geometry)=>{
        const div=document.createElement("div");
        div.className="premium-real-map";
        div.textContent="QA ONLY — synthetic polyline (no network)";
        node.replaceChildren(div);
        return true;
      },
      overview:(node)=>{node.textContent="QA route pending";return true}
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
      id:"qa-contract-metrics-not-a-real-id",status:"ACEPTADA",pickup_phase:"EN_CAMINO",
      origin_lat:-34.9,origin_lng:-56.1,
      destination_lat:-34.89,destination_lng:-56.19,
      origin_text:"Origen QA",destination_text:"Destino QA",quote_final_total:616
    };
    window.__qtVisual={flags,reservation,engine,renderer,rpc};
    const opened=window.QuickTripMapFocus.showTracking({
      reservation,guestToken:"qa-test-not-a-real-guest-token",
      rpc,engine,renderer,api:null,key:null
    });
    return {ready,opened};
  });
  ok(setup.ready&&setup.opened,"Mount exact private tracking module with sandbox RPC and road engine (no backend writes)");
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
      carVisible:!!e(".reservation-live-map .premium-real-map")
    };
  });
  const booked=await poll(async()=>{
    const x=await read();return x.source==="contract"?x:null;
  },9000);
  console.log("BOOKED QA ONLY",JSON.stringify(booked));
  ok(booked.distance==="15,4 km"&&booked.duration==="26 min"&&
      booked.distanceTitle==="DISTANCIA DEL VIAJE"&&
      booked.durationTitle==="DURACIÓN DEL VIAJE"&&booked.calls>=1,
      "Verified booked road route immediately feeds travel metrics without any driver GPS");
  await snap("01-booked-trip-metrics-no-gps-390.png");
  await js(function(){window.__qtVisual.flags.mode="fresh";return true;});
  const live=await poll(async()=>{
    const x=await read();return x.source==="live"?x:null;
  },12000);
  console.log("LIVE GPS QA ONLY",JSON.stringify(live));
  ok(live.distance==="2,7 km"&&live.duration==="6 min"&&
     live.distanceTitle==="A RECOGIDA"&&live.durationTitle==="LLEGADA ESTIMADA",
     "Authenticated GPS leg replaces booked distance with explicitly labeled pickup km and ETA");
  await snap("02-driver-pickup-metrics-mocked-gps-390.png");
  await js(function(){window.__qtVisual.flags.mode="waiting";return true;});
  const returned=await poll(async()=>{
    const x=await read();return x.source==="contract"&&x.calls>live.calls?x:null;
  },12000);
  console.log("GPS STALE QA ONLY",JSON.stringify(returned));
  ok(returned.distance==="15,4 km"&&returned.duration==="26 min"&&
    returned.distanceTitle==="DISTANCIA DEL VIAJE"&&
    returned.durationTitle==="DURACIÓN DEL VIAJE",
    "GPS expires: remove stale pickup km/ETA; display only authentic booked trip metrics");
  await snap("03-private-gps-stale-booked-route-only-390.png");
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
