"use strict";
const {readFileSync}=require("node:fs");
const {runInNewContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const base=path.join(__dirname,"..");
const js=readFileSync(path.join(base,"app.js"),"utf8");
const css=readFileSync(path.join(base,"styles.css"),"utf8");
const html=readFileSync(path.join(base,"index.html"),"utf8");
const sw=readFileSync(path.join(base,"sw.js"),"utf8");

const start=js.indexOf("function locationPermissionGuidance(errorCode){");
const stop=js.indexOf("\nfunction picker(name,mount,position){",start);
assert(start>=0&&stop>start,"Missing dedicated GPS permission guidance");
const permission=runInNewContext(js.slice(start,stop)+"\nlocationPermissionGuidance;");
for(const code of [0,1,2,3]){
  const guide=permission(code);
  assert(guide.title&&guide.description&&Array.isArray(guide.steps)&&guide.steps.length);
}
const denied=permission(1);
assert.match(denied.title,/Android|Chrome/);
assert.match(denied.steps.join(" "),/Mapa Trayectos/);
assert.match(denied.steps.join(" "),/Permisos.*Ubicación/);
assert.match(denied.steps.join(" "),/Ajustes del celular/);
assert.match(denied.description,/no puede cambiar el permiso automáticamente/);
assert.match(permission(3).title,/demorando/);
assert.match(permission(2).title,/posición/);

assert(js.includes('getCurrentPosition(async p=>'),"Permission must be requested from user action");
assert(js.includes('function showGpsPermissionHelp(errorCode)'),"Persistent permission help should replace temporary toast");
assert(js.includes('showGpsPermissionHelp(error?.code??0)'),"Map GPS error codes to dedicated help");
assert(js.includes('button(actions,"↻ Volver a intentar"'),"Retry must be interactive");
assert(js.includes('button(actions,"Escribir dirección"'),"Passenger needs manual fallback");
assert(js.includes('if(geo?.disabled)return'),"Repeated taps must not stack permission prompts");
assert(!js.includes('()=>tell("No se pudo obtener GPS. Revisá permisos.")'),"Do not regress to generic error");
assert(css.includes(".gps-permission-help[hidden]"),"Help must stay hidden until needed");
assert(html.includes('./app.js?v=17')&&html.includes('./styles.css?v=17'));
assert(sw.includes('traslados-cliente-pwa-v17'));
const {createContext,runInContext}=require("node:vm");
const handlerStart=js.indexOf("  function showGpsPermissionHelp(errorCode){");
const handlerEnd=js.indexOf("\n  if(name===\"stop\"&&record.stops[position])",handlerStart);
assert(handlerStart>=0&&handlerEnd>handlerStart,"GPS handler with preflight is missing");
const fragment=js.slice(start,stop)+"\n"+js.slice(handlerStart,handlerEnd);
assert(fragment.includes('navigator.permissions.query({name:"geolocation"})'),
  "Preflight must query browser location permission");
assert(fragment.includes('showGpsPermissionPreparation()'),
  "Prompt must show preparation before calling Android");
assert(fragment.includes("Solicitar permiso")&&fragment.includes("Uber, Cabify"),
  "User must know how to close the floating bubbles before requesting permission");
assert(fragment.includes("Elegir origen sin GPS"),"Manual route must remain available");

const container=()=>({nodes:[],hidden:true,replaceChildren(){this.nodes=[];}});
function setup(permissionState){
  const gpsHelp=container();
  const geo={disabled:false,textContent:"⌖ Usar ubicación actual"};
  const input={focus(){}};
  let count=0;
  const sandbox={
    gpsHelp,geo,input,window:{isSecureContext:true},IDE:"https://example.invalid",
    navigator:{
      userAgent:"Mozilla/5.0 (Linux; Android 16)",
      permissions:{query:async()=>({state:permissionState})},
      geolocation:{getCurrentPosition(_ok,fail){count++;fail({code:1});}}
    },
    clear:(el)=>el.replaceChildren(),
    text:(parent,tag,value,cls)=>{
      const el=container();el.tag=tag;el.textContent=String(value??"");
      el.className=cls||"";parent.nodes.push(el);return el;
    },
    button:(parent,label,cls,fn)=>{
      const item={label,cls,fn};
      parent.nodes.push(item);return item;
    },
    tell:()=>{},isUruguayCoords:()=>true,persist:()=>{},fetch:async()=>({ok:false}),
    setTimeout,clearTimeout,Error,Number,String,
  };
  createContext(sandbox);
  const runtime=runInContext(fragment+"\n({useCurrent,requestGps})",sandbox);
  return {...runtime,gpsHelp,geo,called:()=>count};
}
function findButton(node,label){
  for(const item of node.nodes||[]){
    if(item.label===label)return item;
    const nested=findButton(item,label);
    if(nested)return nested;
  }
  return null;
}
(async()=>{
  const pending=setup("prompt");
  await pending.useCurrent();
  assert.equal(pending.called(),0,"Do not open the Android permission dialog while bubbles might cover it");
  assert.equal(pending.gpsHelp.hidden,false,"Show premium preparation instead");
  assert(findButton(pending.gpsHelp,"⌖ Solicitar permiso"));
  assert(findButton(pending.gpsHelp,"Elegir origen sin GPS"));
  findButton(pending.gpsHelp,"⌖ Solicitar permiso").fn();
  assert.equal(pending.called(),1,"Ask Android only following the passenger's explicit second tap");
  assert(findButton(pending.gpsHelp,"↻ Volver a intentar"),"Denied by overlay: show recovery steps");
  const denied=setup("denied");
  await denied.useCurrent();
  assert.equal(denied.called(),0,"Permanently denied permission should show guidance rather than re-prompt");
  const granted=setup("granted");
  await granted.useCurrent();
  assert.equal(granted.called(),1,"Already granted GPS needs no preparation step");
  console.log("PASS: Android preflight, single permission request, permission denied and granted.");
})().catch(e=>{console.error(e);process.exitCode=1;});

console.log("PASS: permission denied/unavailable/timeout, actionable Android help, GPS retry and manual origin; no auto-grant.");
