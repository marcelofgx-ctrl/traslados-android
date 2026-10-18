"use strict";
/* DIR-001: execute the ACTUAL passenger picker on synthetic IDE responses.
 * Never talks to Supabase or records real addresses, GPS or bookings.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm");
const read=n=>fs.readFileSync("web-pasajero/"+n,"utf8");
const calls=[];
const exact={address:"Arenal Grande 2274, Montevideo",departamento:"Montevideo",type:"DIRECCION",
  localidad:"Cordón",lat:-34.902,lng:-56.175};
const wrong={address:"Arenal Grande 2275, Montevideo",departamento:"Montevideo",type:"DIRECCION",
  localidad:"Cordón",lat:-34.901,lng:-56.175};
const other={address:"Arenal Grande 2274, Canelones",departamento:"Canelones",type:"DIRECCION",
  localidad:"Ciudad de la Costa",lat:-34.81,lng:-56.02};
const fetch=async url=>{
  const endpoint=String(url).includes("/direcUnica?")?"direcUnica":"candidates";
  calls.push(endpoint);
  // The old passenger lookup saw these candidates and wrongly stopped.
  return {ok:true,json:async()=>endpoint==="candidates"?[other,wrong]:[exact,wrong]};
};
function node(tag="div"){
  const out={tag,children:[],dataset:{},listeners:{},attributes:{},hidden:false,
    textContent:"",className:"",value:"",disabled:false,
    classList:{add(){},remove(){},toggle(){}},
    appendChild(n){this.children.push(n);return n;},
    replaceChildren(...n){this.children=n;},
    setAttribute(k,v){this.attributes[k]=v;},
    addEventListener(type,cb){(this.listeners[type]??=[]).push(cb);},
    focus(){},click(){for(const cb of this.listeners.click||[])cb();}};
  return out;
}
const norm=s=>String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
  .toLowerCase().replace(/[^a-z0-9]+/g," ").trim();
const geo={normalize:norm,immediate:()=>[],find:async()=>[],get catalogState(){return "ready";}};
const window={TrasladosGeo:geo,TrasladosAddressViewport:{attach(){}},isSecureContext:true};
const document={createElement:node};
const record={origin:null,destination:null,stops:[]};
const context={window,document,fetch,URL,Date,Number,Array,String,Set,Map,Math,
  AbortController,setTimeout,clearTimeout,encodeURIComponent,
  DEPTS:["Montevideo","Canelones","Florida"],depts:{},record,
  bookingSteps:null,routeStepAutoAdvanced:false,
  refreshBookingSteps(){},renderRouteSummary(){},loading(){},
  isUruguayCoords:(lat,lng)=>lat>=-35.3&&lat<=-30&&lng>=-58.8&&lng<=-52.9,
  navigator:{userAgent:"Android"}};
vm.createContext(context);
vm.runInContext(read("address-engine.js"),context,{filename:"address-engine.js"});
const app=read("app.js");
const begin=app.indexOf("function picker(name,mount,position){");
const finish=app.indexOf("\n// El motor vial no depende",begin);
assert(begin>=0&&finish>begin,"Cannot locate actual passenger picker in app.js");
const helpers=[
 'function text(parent,tag,contents,cls){const e=document.createElement(tag);e.className=cls||"";if(contents!==undefined)e.textContent=String(contents);parent.appendChild(e);return e;}',
 'function button(parent,contents,cls,fn){const b=text(parent,"button",contents,cls);if(fn)b.addEventListener("click",fn);return b;}',
 'function clear(n){n.replaceChildren();}'
].join("\n");
vm.runInContext(helpers+"\n"+app.slice(begin,finish),context,{filename:"app-picker-real.js"});
const flatten=n=>[n,...(n.children||[]).flatMap(flatten)];
const sleep=()=>new Promise(ok=>setTimeout(ok,70));
(async()=>{
  const mount=node("div"),picker=context.picker("destination",mount,0);
  const search=flatten(mount).find(x=>x.attributes?.["aria-label"]==="Buscar dirección o lugar");
  assert(search,"Real passenger Search control must exist");
  picker.input.value="Arenal Grande 2274";
  search.click();await sleep();
  assert(calls.includes("candidates"));
  assert(calls.includes("direcUnica"),
    "Actual passenger picker must retry exact-number geocoding despite wrong candidates");
  const titles=flatten(mount).filter(x=>x.className==="result-title").map(x=>x.textContent);
  assert(titles.includes(exact.address),"Passenger picker should show verified Arenal Grande 2274");
  assert(!titles.includes(wrong.address),"Must not suggest incorrect number 2275");
  const option=flatten(mount).find(x=>x.tag==="button"&&x.children.some(n=>n.textContent===exact.address));
  assert(option,"Correct option selectable");option.click();await sleep();
  assert.equal(record.destination.text,exact.address);
  assert.equal(record.destination.department,"Montevideo");
  const engine=window.TrasladosAddress;
  assert((await engine.find("Arenal Grande 2274","Montevideo")).some(x=>x.address===exact.address));
  const across=await engine.findAcross("Arenal Grande 2274","Montevideo");
  assert(across.local.some(x=>x.address===exact.address));
  assert(across.other.some(x=>x.departamento==="Canelones"));
  console.log("PASS DIR-001: real picker retries exact address and both wrappers share a safe pipeline");
})().catch(e=>{console.error(e);process.exitCode=1;});
