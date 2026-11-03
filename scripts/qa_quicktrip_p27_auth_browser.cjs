"use strict";
/* P2.7 isolated Chromium mobile contract: NO live bookings or backend writes.
 * Tests the guest -> account -> retained route UI entirely with local state. */
const cp=require("node:child_process"),fs=require("node:fs"),path=require("node:path");
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const DIR="artifacts/quicktrip-p27-account-qa";fs.mkdirSync(DIR,{recursive:true});
let socket,seq=0;const pending=new Map(),children=[];
function ok(v,s){if(!v)throw Error("FAIL P27 "+s);console.log("PASS BROWSER P27 "+s);}
async function poll(fn,ms=19000){const until=Date.now()+ms;while(Date.now()<until){try{let v=await fn();if(v)return v}catch{}await sleep(180)}throw Error("P27 timeout");}
async function cmd(method,params={}){return new Promise((resolve,reject)=>{
 const id=++seq;const handle=setTimeout(()=>{pending.delete(id);reject(Error("CDP timeout "+method))},16000);
 pending.set(id,{resolve:x=>{clearTimeout(handle);resolve(x)},reject:e=>{clearTimeout(handle);reject(e)}});
 socket.send(JSON.stringify({id,method,params}));
});}
async function js(fn){const x=await cmd("Runtime.evaluate",{expression:"("+fn.toString()+")()",awaitPromise:true,returnByValue:true,userGesture:true});
 if(x.exceptionDetails)throw Error(JSON.stringify(x.exceptionDetails));return x.result?.value;}
async function snap(name){const r=await cmd("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});
 fs.writeFileSync(path.join(DIR,name),Buffer.from(r.data,"base64"));}
async function main(){
 const chrome=["google-chrome","google-chrome-stable","chromium"].find(n=>cp.spawnSync("which",[n]).status===0);
 if(!chrome)throw Error("Chrome missing");
 const server=cp.spawn("python3",["-m","http.server","8784","--bind","127.0.0.1","--directory","."],{stdio:"ignore"});children.push(server);
 await poll(async()=>{try{return (await fetch("http://127.0.0.1:8784/web-pasajero/")).ok}catch{return false}});
 const browser=cp.spawn(chrome,["--headless=new","--no-sandbox","--disable-gpu","--disable-dev-shm-usage","--no-first-run",
 "--disable-extensions","--remote-debugging-port=9245","--user-data-dir=/tmp/qt-p27-"+process.pid,
 "--window-size=390,844","about:blank"],{stdio:"ignore"});children.push(browser);
 const ws=await poll(async()=>{try{return (await(await fetch("http://127.0.0.1:9245/json/list")).json()).find(x=>x.type==="page")?.webSocketDebuggerUrl}catch{return false}});
 socket=new WebSocket(ws);await new Promise((res,rej)=>{socket.addEventListener("open",res,{once:true});socket.addEventListener("error",rej,{once:true});});
 socket.addEventListener("message",evt=>{let v;try{v=JSON.parse(String(evt.data))}catch{return;}
  if(v.id&&pending.has(v.id)){const p=pending.get(v.id);pending.delete(v.id);v.error?p.reject(Error(v.error.message)):p.resolve(v.result||{});}});
 await cmd("Page.enable");await cmd("Runtime.enable");
 await cmd("Emulation.setDeviceMetricsOverride",{width:390,height:844,deviceScaleFactor:1,mobile:true,screenWidth:390,screenHeight:844});
 await cmd("Emulation.setTouchEmulationEnabled",{enabled:true,maxTouchPoints:1});
 await cmd("Page.navigate",{url:"http://127.0.0.1:8784/web-pasajero/?ui=clean"});
 await poll(()=>js(function(){return document.readyState==="complete"&&document.getElementById("urgent-send")&&document.getElementById("passenger-auth-action")&&typeof syncAuthUI==="function"}));
 await js(function(){document.getElementById("qt-gps-welcome-manual")?.click();return true;});
 // Intercept any attempt to write a real booking, even if a future bug calls it.
 const initial=await js(function(){
  window.__p27BookingWrites=0;
  const saved=window.fetch;
  window.fetch=function(input,init){
   const target=String(input?.url||input||"");
   if(/\/rpc\/(?:create_reservation|customer_request_pickup|customer_create_reservation)/.test(target)){
    window.__p27BookingWrites++;return Promise.reject(new Error("P27 safety: no bookings in QA browser"));
   }
   return saved.apply(this,arguments);
  };
  setWhen("now");shiftView("viaje");
  record.origin={text:"ROQUE SAENZ PEÑA 1711",lat:-34.851,lng:-56.006,department:"Canelones"};
  record.destination={text:"Aeropuerto Internacional de Carrasco",lat:-34.838,lng:-56.035,department:"Canelones"};
  syncAuthUI();
  const gate=document.getElementById("passenger-auth-gate"),inputs=document.getElementById("passenger-identification");
  const cta=document.getElementById("urgent-send");
  return {gate:!gate.hidden&&gate.getBoundingClientRect().height>60,inputsHidden:inputs.hidden,
    cta:cta.innerText,gateText:gate.textContent,guest:!authReady(),
    preserved:record.origin.text};
 });
 console.log("P27 GUEST UI",JSON.stringify(initial));
 ok(initial.guest&&initial.gate&&initial.inputsHidden&&
    initial.cta.includes("INGRESAR PARA SOLICITAR")&&initial.gateText.includes("creá una cuenta"),
    "Guest reads fares but sees login gate, not name/phone fields");
 await js(function(){document.getElementById("passenger-auth-gate").scrollIntoView({block:"center"});return true;});
 await snap("01-guest-account-gate-390.png");
 const after=await js(function(){
   document.getElementById("urgent-send").click();
   return {account:document.getElementById("view-cuenta").classList.contains("active"),
     preserved:record.origin?.text,stored:accountJourneyReturn?.view,
     bookingWrites:window.__p27BookingWrites};
 });
 console.log("P27 AFTER REQUEST",JSON.stringify(after));
 ok(after.account&&after.stored==="viaje"&&
    after.preserved===initial.preserved&&after.bookingWrites===0,
    "Guest CTA redirects to account, keeps origin and never creates booking");
 await snap("02-login-before-request-390.png");
 const scheduled=await js(function(){
   setWhen("schedule");shiftView("reservar");showPreview();
   return {account:document.getElementById("view-cuenta").classList.contains("active"),
    stored:accountJourneyReturn?.view,origin:record.origin.text,writes:window.__p27BookingWrites};
 });
 ok(scheduled.account&&scheduled.stored==="reservar"&&
    scheduled.origin===initial.preserved&&scheduled.writes===0,
    "Scheduled review also requires login before any reservation");
 const signed=await js(function(){
   session={token:"P27_LOCAL_FAKE_TOKEN_NOT_A_REAL_SESSION",customer:{full_name:"Pasajero QA",phone:"099 123 456"}};
   syncAuthUI();shiftView("viaje");
   return {summary:!document.getElementById("passenger-account-summary").hidden,
     gateHidden:document.getElementById("passenger-auth-gate").hidden,
     fieldsHidden:document.getElementById("passenger-identification").hidden,
     cta:document.getElementById("urgent-send").innerText,
     origin:record.origin.text,writes:window.__p27BookingWrites};
 });
 console.log("P27 FAKE SESSION UI",JSON.stringify(signed));
 ok(signed.summary&&signed.gateHidden&&signed.fieldsHidden&&
    signed.cta.includes("SOLICITAR VIAJE")&&signed.origin===initial.preserved&&signed.writes===0,
    "In-memory signed-in UI shows customer summary and preserves itinerary without sending");
 console.log("P27 mobile browser flow PASSED with zero booking writes.");
}
main().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>{
 try{socket?.close()}catch{}
 for(const p of children.reverse())try{p.kill("SIGTERM")}catch{}
});
