"use strict";
// Reproduces the exact 16/10 Samsung failure: IDE returns several CALLE
// candidates with lat=lng=0. They are valid suggestions, not valid route pins.
const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm");
const source=fs.readFileSync("web-pasajero/address-engine.js","utf8");
const app=fs.readFileSync("web-pasajero/app.js","utf8");
const fixed=[
 {type:"CALLE",id:"20238",address:"ROQUE SAENZ PEÑA, LAGOMAR, CANELONES",
  departamento:"CANELONES",localidad:"LAGOMAR",lat:0,lng:0},
 {type:"CALLE",id:"20238",address:"ROQUE SAENZ PEÑA, SHANGRILA, CANELONES",
  departamento:"CANELONES",localidad:"SHANGRILA",lat:0,lng:0},
 {type:"CALLE",id:"8492",address:"ROQUE SAENZ PEÑA, MONTEVIDEO, MONTEVIDEO",
  departamento:"MONTEVIDEO",localidad:"MONTEVIDEO",lat:0,lng:0}
];
const resolved=[
 {...fixed[1],lat:-34.84797238706324,lng:-55.998250476922856},
 {...fixed[0],lat:-34.84797238706324,lng:-55.998250476922856}
];
let calls=0;let resolveHasGeo=true;
const ctx={window:{TrasladosGeo:{normalize(v){
return String(v||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
.toLowerCase().replace(/[^a-z0-9]+/g," ").trim();
}}},AbortController,setTimeout,clearTimeout,Date,Number,String,Error,Set,Map,
fetch:async url=>{
 calls++;
 const path=String(url);
 if(path.includes("/candidates?"))return {ok:true,json:async()=>fixed};
 if(path.includes("/direcUnica?"))return {ok:true,json:async()=>resolveHasGeo?resolved:fixed};
 throw Error("Unexpected IDE endpoint "+path);
}};
vm.runInNewContext(source,ctx,{filename:"address-engine.js"});
const ide=ctx.window.TrasladosAddress;
(async()=>{
 const local=await ide.find("Roque saenz peña","Canelones");
 assert.equal(local.length,2,"LAGOMAR and SHANGRILA must be shown even before geocoding");
 assert(local.every(r=>r.lat===0&&r.lng===0));
 assert(local.every(r=>r.departamento==="CANELONES"));
 const across=await ide.findAcross("Roque Saenz Pena","Canelones");
 assert.equal(across.local.length,2);
 assert.equal(across.other.length,1);
 assert.equal(across.other[0].departamento,"MONTEVIDEO");
 assert.equal(ide.score(fixed[0],"Roque saenz peña","Canelones")>=0,true);
 assert.equal(ide.score(fixed[0],"Roque saenz peña","Montevideo"),-1);
 const lagomar=await ide.resolve(local[0],"Roque saenz peña","Canelones");
 assert(lagomar.lat < -34 && lagomar.lng < -55);
 assert.equal(lagomar.approximate,true,"Street centroid is a reference, not a confirmed doorway");
 const repeat=await ide.resolve(local[0],"Roque saenz peña","Canelones");
 assert.deepEqual(repeat,lagomar);
 resolveHasGeo=false;
 // Changing query avoids cache; a provider response with 0,0 must be rejected.
 const another={...fixed[0],address:"ROQUE SAENZ PEÑA, PARQUE CARRASCO, CANELONES"};
 await assert.rejects(ide.resolve(another,"Roque saenz peña","Canelones"),/número o esquina/i);
 assert(app.includes("window.TrasladosAddress.findAcross(q,dept)"));
 assert(app.includes("Otras ubicaciones en Uruguay"));
 assert(app.includes("Referenci")||app.includes("referencia aproximada"));
 assert(!app.includes("Sin coincidencias en "), "Old misleading no-results error removed");
 console.log("PASS Roque Saenz Pena: 0,0 suggestions retained, cross-dept options, official resolve, no fictitious coordinate, clear UX");
})().catch(e=>{console.error(e);process.exitCode=1;});
