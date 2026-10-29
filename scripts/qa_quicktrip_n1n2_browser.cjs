"use strict";
/* Chromium 390px QA: uses an in-memory synthetic UI row, NEVER an API booking.
 * Captures the active-trip design and tests return-to-ride, accessibility and scroll. */
const cp=require("node:child_process"),fs=require("node:fs"),path=require("node:path");
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const OUT="artifacts/quicktrip-n1n2";fs.mkdirSync(OUT,{recursive:true});
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
 const initial=await js(function(){
   const hero=document.getElementById("quicktrip-live-trip");
   const qtc=document.getElementById("qtc-app");
   return hero?.hidden===true&&!!qtc&&getComputedStyle(qtc).display!=="none";
 });
 ok(initial,"No active ride means original booking screen is preserved");
 const active=await js(function(){
   window.__qaOpened=0;window.__qaListed=0;
   window.QuickTripActiveRide.render([{id:"qa-synthetic-not-a-real-booking",
     status:"ACEPTADA",pickup_phase:"EN_CAMINO",
     origin_text:"Origen de prueba · QA",destination_text:"Destino de prueba · QA",
     quote_final_total:764}],{onOpen:()=>{window.__qaOpened++},onList:()=>{window.__qaListed++}});
   const q=s=>document.querySelector(s);
   return {
     title:q("#quicktrip-live-trip .qt-live-title")?.textContent,
     price:q(".qt-live-fare")?.textContent,
     knob:!!q(".qt-live-knob"),
     touch:getComputedStyle(q(".qt-live-knob")).touchAction,
     fallback:!!q(".qt-live-tap"),
     hiddenForm:getComputedStyle(q("#qtc-app")).display==="none",
     overflow:document.documentElement.scrollWidth-innerWidth,
     cardWidth:q("#quicktrip-live-trip").getBoundingClientRect().width
   };
 });
 ok(active.title==="Tu viaje activo"&&active.price.includes("764"),"Live ride title and source-only fare");
 ok(active.knob&&active.touch==="none"&&active.fallback,"Accessible slide plus tap fallback");
 ok(active.hiddenForm,"Active ride leads, new booking form behind explicit option");
 ok(active.overflow<=6&&active.cardWidth<=390,"No Samsung horizontal overflow");
 await snap("01-active-trip-synthetic-samsung-390.png");
 const click=await js(function(){
   document.querySelector(".qt-live-tap").click();
   return window.__qaOpened;
 });
 ok(click===1,"Fallback opens map exactly once without booking writes");
 const newTrip=await js(function(){
   document.querySelector(".qt-live-new-trip").click();
   const visible=getComputedStyle(document.getElementById("qtc-app")).display!=="none";
   document.querySelector(".qt-live-new-trip").click();
   const hidden=getComputedStyle(document.getElementById("qtc-app")).display==="none";
   return {visible,hidden};
 });
 ok(newTrip.visible&&newTrip.hidden,"Prepare another trip toggles without dropping current ride");
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
   return Boolean(document.getElementById("view-viaje").classList.contains("active"));
 });
 ok(back,"Tracking-closed returns to Tu viaje instead of history");
 await snap("02-active-flow-return-samsung-390.png");
 const empty=await js(function(){
   window.QuickTripActiveRide.render([]);
   return document.getElementById("quicktrip-live-trip").hidden&&
     !document.getElementById("view-viaje").classList.contains("qt-has-active-ride");
 });
 ok(empty,"Terminal / empty state restores initial booking layout");
 console.log("N1/N2 CHROME PASS — only synthetic UI, no reservation created");
}
main().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>{
 try{socket?.close()}catch{}
 for(const p of children.reverse())try{p.kill("SIGTERM")}catch{}
});
