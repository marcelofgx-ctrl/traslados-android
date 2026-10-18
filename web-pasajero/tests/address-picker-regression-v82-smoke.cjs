"use strict";
/* DIR-001: test the actual app.js passenger picker, not only the old find() helper.
 * Deterministic synthetic IDE responses; no real bookings, coordinates or DB writes.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm");
const source=p=>fs.readFileSync("web-pasajero/"+p,"utf8");
const calls=[];
const exact={address:"Arenal Grande 2274, Montevideo",departamento:"Montevideo",
  type:"DIRECCION",localidad:"Cordón",lat:-34.902,lng:-56.175};
const wrong={address:"Arenal Grande 2275, Montevideo",departamento:"Montevideo",
  type:"DIRECCION",localidad:"Cordón",lat:-34.902,lng:-56.175};
const other={address:"Arenal Grande 2274, Canelones",departamento:"Canelones",
  type:"DIRECCION",localidad:"Ciudad de la Costa",lat:-34.81,lng:-56.02};
let candidatesMode="other",uniqueMode="exact";
const fetch=async url=>{
  const endpoint=String(url).includes("/direcUnica?")?"direcUnica":"candidates";
  const query=new URL(String(url)).searchParams.get("q");
  calls.push({endpoint,query});
  if(endpoint==="candidates"){
    if(candidatesMode==="error")throw new Error("candidates timed out");
    if(candidatesMode==="local")return {ok:true,json:async()=>[exact,other]};
    if(candidatesMode==="street")return {ok:true,json:async()=>[
      {address:"Arenal Grande, Montevideo",departamento:"Montevideo",type:"CALLE",lat:0,lng:0}
    ]};
    return {ok:true,json:async()=>[other,wrong]};
  }
  if(uniqueMode==="error")throw new Error("direcUnica timed out");
  return {ok:true,json:async()=>uniqueMode==="none"?[wrong]:[exact,wrong]};
};
function node(tag="div"){
  const out={tag,children:[],dataset:{},listeners:{},attributes:{},
    hidden:false,textContent:"",className:"",value:"",disabled:false,
    classList:{add(){},remove(){},toggle(){}},
    appendChild(n){this.children.push(n);return n;},
    replaceChildren(...n){this.children=n;},
    setAttribute(k,v){this.attributes[k]=v;},
    addEventListener(type,cb){(this.listeners[type]??=[]).push(cb);},
    focus(){},click(){for(const cb of this.listeners.click||[])cb();}};
  return out;
}
const record={origin:null,destination:null,stops:[]};
const geo={normalize:s=>String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
    .toLowerCase().replace(/[^a-z0-9]+/g," ").trim(),
  immediate:()=>[],find:async()=>[],get catalogState(){return "ready";}};
const window={TrasladosGeo:geo,TrasladosAddressViewport:{attach(){}},isSecureContext:true};
const document={createElement:node};
const context={window,document,fetch,URL,Date,Number,Array,String,Set,Map,Math,
  AbortController,setTimeout,clearTimeout,encodeURIComponent,
  DEPTS:["Montevideo","Canelones","Florida"],depts:{},record,
  bookingSteps:null,routeStepAutoAdvanced:false,
  refreshBookingSteps(){},renderRouteSummary(){},loading(){},
  isUruguayCoords:(lat,lng)=>lat>=-35.3&&lat<=-30&&lng>=-58.8&&lng<=-52.9,
  navigator:{userAgent:"Android"}};
vm.createContext(context);
vm.runInContext(source("address-engine.js"),context,{filename:"address-engine.js"});
assert.equal(typeof window.TrasladosAddress.findAcross,"function");
const app=source("app.js");
const begin=app.indexOf("function picker(name,mount,position){");
const finish=app.indexOf("\n// El motor vial no depende",begin);
assert(begin>=0&&finish>begin,"Cannot locate actual passenger picker");
vm.runInContext(