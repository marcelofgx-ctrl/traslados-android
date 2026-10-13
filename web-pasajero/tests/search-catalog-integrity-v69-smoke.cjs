"use strict";
/* Integridad con el índice real OSM publicado; no usar datos de producción vivos. */
const fs=require("node:fs"),vm=require("node:vm"),assert=require("node:assert/strict");
const doc=JSON.parse(fs.readFileSync("web-pasajero/data/uy-pois.json","utf8"));
assert.equal(doc.version,1);
assert(Array.isArray(doc.items)&&doc.items.length>5000,"Índice OSM de producción disponible");
const src=fs.readFileSync("web-pasajero/geo-search.js","utf8");
let n=0;
const ctx={window:{},fetch:async()=>{n++;return {ok:true,json:async()=>doc}}};
vm.runInNewContext(src,ctx);
const geo=ctx.window.TrasladosGeo;
(async()=>{
  const all=await geo.preload();
  assert.equal(geo.catalogState,"ready");
  assert(all.length>5000,"Coordenadas válidas");
  for(const dept of ["Montevideo","Canelones","Maldonado"]){
    const sample=all.find(p=>geo.normalize(p.department)===geo.normalize(dept)&&
      geo.normalize(p.name).length>=6&&!/^[0-9]/.test(p.name));
    assert(sample,"Hay al menos un lugar conocido en "+dept);
    const same=geo.lookupIn(all,sample.name,dept);
    assert(same.some(p=>p.id===sample.id),"Lugar encontrado en su propio departamento: "+dept);
    assert(same.every(p=>geo.normalize(p.department)===geo.normalize(dept)),
      "Sin resultados intrusos para "+dept);
  }
  assert((await geo.find("Punta Carretas Shopping","Canelones")).every(p=>
    geo.normalize(p.department)==="canelones"),"No filtrar mal Montevideo → Canelones");
  assert((await geo.find("Madrid","Canelones")).some(p=>p.name==="De Madrid"),
    "El restaurante real siempre puede encontrarse en Canelones");
  assert.equal(n,1,"Una única descarga incluso para varios departamentos");
  console.log("PASS: catálogo real "+all.length+" OSM, tres departamentos, integridad, contaminación cruzada y De Madrid");
})().catch(e=>{console.error(e);process.exitCode=1});
