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
// Contrato explícito del motor geográfico reutilizable.
let ideCalls=0;
const addresses=[
 {address:"Arenal Grande 2274, Montevideo",departamento:"Montevideo",lat:-34.9,lng:-56.2},
 {address:"Sarandí Grande, Florida",departamento:"Florida",lat:-33.4,lng:-56.3},
 {address:"Playa Grande, Maldonado",departamento:"Maldonado",lat:-34.8,lng:-55.2},
 {address:"Arenal Grande 2275, Montevideo",departamento:"Montevideo",lat:-34.9,lng:-56.2}
];
const addressesContext={
  window:{TrasladosGeo:geo},
  fetch:async url=>{
    ideCalls++;
    assert.match(String(url),/direcciones\.ide\.uy\/api\/v1\/geocode\//);
    if(String(url).includes("/reverse?"))return {ok:true,json:async()=>[addresses[0]]};
    if(String(url).includes("/direcUnica?"))return {ok:true,json:async()=>[addresses[0]]};
    return {ok:true,json:async()=>addresses};
  },
  encodeURIComponent,Number,Set,Array,String,Error
};
vm.runInNewContext(fs.readFileSync("web-pasajero/address-engine.js","utf8"),addressesContext,{
  filename:"address-engine.js"
});
const addressEngine=addressesContext.window.TrasladosAddress;
assert(addressEngine?.score&&addressEngine?.find&&addressEngine?.resolve&&addressEngine?.reverse,
  "Motor IDE desacoplado con contratos públicos");
const scoreAddress=(item,q,dept="Montevideo")=>addressEngine.score(item,q,dept);
const sample=(address,departamento)=>({address,departamento,lat:-34.9,lng:-56.2});
assert(scoreAddress(sample("Arenal Grande 2274, Montevideo","Montevideo"),"Arenal Grande 2274")>=0,"Debe aceptar dirección exacta");
assert(scoreAddress(sample("Sarandí Grande, Florida","Florida"),"Arenal Grande 2274")<0,"No sugerir Florida");
assert(scoreAddress(sample("Playa Grande, Maldonado","Maldonado"),"Arenal Grande 2274")<0,"No sugerir Maldonado");
assert(scoreAddress(sample("Arenal Grande, Montevideo","Montevideo"),"Arenal Grande 2274")<0,"No fingir una altura faltante");
assert(scoreAddress(sample("Arenal Grande 2275, Montevideo","Montevideo"),"Arenal Grande 2274")<0,"No reemplazar altura por otra");
assert(scoreAddress(sample("Arenal Grande 2274, Canelones","Canelones"),"Arenal Grande 2274")<0,"Respetar departamento");
(async()=>{
  const actual=await addressEngine.find("Arenal Grande 2274","Montevideo");
  assert.equal(actual.length,1,"Filtrar resultados imprecisos por departamento y número");
  assert.equal(actual[0].address,addresses[0].address);
  const exact=await addressEngine.resolve(actual[0],"Arenal Grande 2274","Montevideo");
  assert.equal(exact.lat,-34.9);
  const reversed=await addressEngine.reverse(-34.9,-56.2);
  assert.equal(reversed.address,addresses[0].address);
  assert(ideCalls>=2,"Llamadas oficiales encapsuladas en un único módulo");
  console.log("PASS: IDE directions validate street, exact door number and selected department");
})().catch(e=>{console.error(e);process.exitCode=1});

(async()=>{
  const known=geo.immediate("Aeropuerto","Canelones");
  assert.equal(known[0]?.id,"uy-airport-carrasco","Carrasco debe estar primero sin red");
  assert.equal(known[0]?.department,"Canelones");
  assert(known[0]?.lat>-35&&known[0]?.lng<-55,"Carrasco debe tener punto real");
  assert.equal(calls,0,"Aeropuerto inmediato sin solicitudes HTTP");
  const names=await geo.find("Punta Carreta Shopping","Montevideo");
  assert.equal(names[0]?.name,"Punta Carretas Shopping",
    "Tolerar singular/plural sin listas manuales");
  const other=await geo.find("plaza italia","Maldonado");
  assert.equal(other.length,0,"No mezclar resultados de otro departamento");
  const sameDept=await geo.find("plaza italia","Montevideo");
  assert.equal(sameDept[0]?.name,"Plaza Italia Shopping Outlet");
  const hospital=await geo.find("hospital clinicas","Montevideo");
  assert.equal(hospital[0]?.name,"Hospital de Clínicas");
  assert.equal(calls,1,"El índice debe solicitarse una única vez");
  assert.equal(geo.normalize("Punta Carrétas"),"punta carretas");
  assert.equal(geo.immediate("madrid","Canelones")[0]?.name,"De Madrid");
  assert.equal(geo.immediate("madrid","Montevideo").length,0);
  console.log("PASS: Carrasco inmediato, Punta Carretas, Plaza Italia, Hospital Clínicas, Uruguay general, índice cacheado");
})().catch(e=>{console.error(e);process.exitCode=1});
