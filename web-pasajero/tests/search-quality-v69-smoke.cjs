"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm");
const items=[
  ["m1","Calzados Madrid",-34.9,-56.1,"Montevideo","COMERCIO","",""],
  ["m2","Fernández Crespo y Madrid",-34.9,-56.1,"Montevideo","LUGAR","",""],
  ["c1","Costa Urbana Shopping",-34.8,-56.0,"Canelones","COMERCIO","",""],
  ["m3","World Trade Center",-34.9,-56.1,"Montevideo","COMERCIO","",""],
  ["c2","Café Solymar",-34.8,-56.0,"Canelones","COMERCIO","",""],
  ["m4","Punta Carretas Shopping",-34.9,-56.1,"Montevideo","COMERCIO","",""],
  ["d1","Playa Grande",-34.8,-55.2,"Maldonado","LUGAR","",""],
  ...Array.from({length:5001},(_,i)=>["x"+i,"Prueba "+i,-34.7,-56,"Canelones","LUGAR","",""])
];
const ctx={window:{},count:0,fetch:async()=>{ctx.count++;return {ok:true,json:async()=>({version:1,items})};}};
const src=fs.readFileSync("web-pasajero/geo-search.js","utf8");
vm.runInNewContext(src,ctx);const g=ctx.window.TrasladosGeo;
(async()=>{
  for(const [q,dept,name] of [
    ["Madrid","Canelones","De Madrid"],["de madrid","Canelones","De Madrid"],
    ["restaurante de madrid","Canelones","De Madrid"],["MÁDRID","Canelones","De Madrid"],
    ["madr","Canelones","De Madrid"],["Costa Urbana","Canelones","Costa Urbana Shopping"],
    ["cafe solymar","Canelones","Café Solymar"],["World Trade Center","Montevideo","World Trade Center"],
    ["punta carretas","Montevideo","Punta Carretas Shopping"],
    ["Calzados Madrid","Montevideo","Calzados Madrid"],["Playa Grande","Maldonado","Playa Grande"]
  ]){
    const out=await g.find(q,dept);
    assert.equal(out[0]?.name,name,"Debe resolver "+q+" en "+dept);
    assert(out.every(p=>g.normalize(p.department)===g.normalize(dept)), "Filtro "+dept);
  }
  for(const [q,dept] of [
    ["Madrid","Maldonado"],["Calzados Madrid","Canelones"],
    ["Costa Urbana","Montevideo"],["World Trade Center","Canelones"],
    ["Punta Carretas","Canelones"],["Playa Grande","Montevideo"]
  ])assert.equal((await g.find(q,dept)).length,0,q+" nunca debe mostrarse en "+dept);
  assert.equal(ctx.count,1,"Índice cacheado");
  assert.equal(g.catalogState,"ready");
  const bad={window:{},fetch:async()=>{throw new Error("offline")}};
  vm.runInNewContext(src,bad);
  assert.equal((await bad.window.TrasladosGeo.find("Madrid","Canelones"))[0]?.name,"De Madrid");
  assert.equal(bad.window.TrasladosGeo.catalogState,"error");
  console.log("PASS: 17 búsquedas, aislamiento geográfico, alias, acentos, offline e índice cacheado");
})().catch(e=>{console.error(e);process.exitCode=1});
