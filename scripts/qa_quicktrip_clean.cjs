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
    oldHidden:display("#view-viaje .premium-journey-header")==="none"&&
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
  check(s.scrollWidth<=s.width+6,"No horizontal overflow at Samsung phone width");
  check(s.manifest==="./manifest-quicktrip-clean.webmanifest","Separate PWA manifest selected");
  await snap("01-quicktrip-arriba.png");
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
    official:document.querySelector("#urgent-actions-title").textContent};
  });
  say("Timing choice: "+JSON.stringify(when));
  check(when.title.includes("10")&&when.official.includes("10"),"Existing pickup-mode handler reused");
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
  await expression(function(){window.scrollTo(0,document.body.scrollHeight);return true});
  await pause(400);await snap("03-quicktrip-abajo.png");
  const manifest=await cmd("Page.getAppManifest").catch(()=>null);
  if(manifest?.data){
   const meta=JSON.parse(manifest.data);
   check(String(meta.start_url||"").includes("ui=clean"),"Installed PWA opens new UI");
  }
  await goto(HOST);
  await poll(()=>expression(function(){return !!document.querySelector("#view-inicio.active")}),12000);
  check(await expression(function(){return !document.body.classList.contains("qt-clean")}),
   "Ordinary web is not affected by testing UI");
  await snap("04-original-protegida.png");
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
