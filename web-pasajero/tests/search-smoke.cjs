"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const index=[
 ["w100","Punta Carretas Shopping",-34.922,-56.16,"MONTEVIDEO","COMERCIO","",""],
 ["w101","Plaza Italia Shopping Outlet",-34.888,-56.12,"MONTEVIDEO","COMERCIO","Av Italia 4250",""],
 ["n102","Hospital de Clínicas",-34.89,-56.15,"MONTEVIDEO","SALUD","",""],
 ["n103","Avenida Italia",-34.9,-56.14,"MONTEVIDEO","CALLE","",""],
];
let calls=0;
const sandbox={
  window:{},
  fetch:async url=>{
    calls++;
    assert.match(String(url),/\.\/data\/uy-pois\.json/);
    // Runtime index guards against incomplete results; pad with test POIs.
    const rows=[...index,...Array.from({length:5001},(_,i)=>
      ["n"+(i+200),"Local Uruguay "+i,-34.7,-56.0,"CANELONES","COMERCIO","",""])];
    return {ok:true,json:async()=>({version:1,items:rows})};
  },
};
vm.runInNewContext(fs.readFileSync("web-pasajero/geo-search.js","utf8"),sandbox,{
  filename:"geo-search.js",
});
const geo=sandbox.window.TrasladosGeo;
assert(geo,"motor no cargado");
(async()=>{
  const known=geo.immediate("Aeropuerto","Canelones");
  assert.equal(known[0]?.id,"uy-airport-carrasco","Carrasco debe estar primero sin red");
  assert.equal(known[0]?.department,"Canelones");
  assert(known[0]?.lat>-35&&known[0]?.lng<-55,"Carrasco debe tener punto real");
  assert.equal(calls,0,"Aeropuerto inmediato sin solicitudes HTTP");
  const names=await geo.find("Punta Carreta Shopping","Canelones");
  assert.equal(names[0]?.name,"Punta Carretas Shopping",
    "Tolerar singular/plural sin listas manuales");
  const other=await geo.find("plaza italia","Maldonado");
  assert.equal(other[0]?.name,"Plaza Italia Shopping Outlet",
    "Departamento no puede excluir un POI del país");
  const hospital=await geo.find("hospital clinicas","Montevideo");
  assert.equal(hospital[0]?.name,"Hospital de Clínicas");
  assert.equal(calls,1,"El índice debe solicitarse una única vez");
  assert.equal(geo.normalize("Punta Carrétas"),"punta carretas");
  console.log("PASS: Carrasco inmediato, Punta Carretas, Plaza Italia, Hospital Clínicas, Uruguay general, índice cacheado");
})().catch(e=>{console.error(e);process.exitCode=1});
