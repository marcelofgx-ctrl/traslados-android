/* Traslados · buscador de comercios y lugares de Uruguay.
 * Índice OSM generado automáticamente desde Geofabrik; no se mantienen
 * shoppings/hospitales a mano. Dirección/calles: IDE Uruguay independiente.
 * © OpenStreetMap contributors, ODbL.
 */
(function (global) {
  "use strict";
  const catalogUrl="./data/uy-pois.json";
  const namedKnown=[
    {id:"uy-airport-carrasco",name:"Aeropuerto Internacional de Carrasco · Terminal de pasajeros",
      lat:-34.83696,lng:-56.01638,department:"Canelones",hint:"Aeropuerto MVD · Paso Carrasco",
      aliases:"aeropuerto carrasco aeropuerto montevideo aeropuerto canelones mvd terminal de pasajeros cesareo berisso"},
    {id:"uy-airport-punta",name:"Aeropuerto de Punta del Este · Laguna del Sauce",
      lat:-34.8552,lng:-55.09405,department:"Maldonado",hint:"Aeropuerto PDP",
      aliases:"aeropuerto punta del este aeropuerto maldonado aeropuerto laguna del sauce pdp"},
  ];
  let indexPromise=null;
  let readyCount=0;
  function normalize(value) {
    return String(value||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
      .toLowerCase().replace(/[^a-z0-9]+/g," ").trim();
  }
  function words(s){return normalize(s).split(/\s+/).filter(Boolean);}
  function extract(row){
    return {id:"osm-"+row[0],name:row[1],lat:Number(row[2]),lng:Number(row[3]),
      department:row[4]||"",category:row[5]||"LUGAR",
      hint:row[6]||"",aliases:row[7]||""};
  }
  async function preload(){
    if(indexPromise)return indexPromise;
    indexPromise=fetch(catalogUrl,{cache:"default"}).then(async r=>{
      if(!r.ok)throw new Error("Índice de lugares no publicado ("+r.status+")");
      const data=await r.json();
      if(data.version!==1||!Array.isArray(data.items)||data.items.length<5000)
        throw new Error("Índice de lugares incompleto");
      readyCount=data.items.length;
      return data.items.map(extract).filter(x=>Number.isFinite(x.lat)&&Number.isFinite(x.lng));
    }).catch(e=>{indexPromise=null;throw e;});
    return indexPromise;
  }
  function score(p,q,selectedDept){
    const want=words(q),got=words(p.name+" "+p.aliases);
    if(!want.length||!want.every(w=>got.some(t=>t.startsWith(w))))return -1;
    const a=normalize(p.name),b=normalize(q);
    return (a===b?250:a.startsWith(b)?180:a.includes(b)?115:40)+
      (normalize(p.department)===normalize(selectedDept)?18:0)+
      (p.category==="COMERCIO"?6:0)+Math.max(0,12-Math.abs(a.length-b.length)/6);
  }
  function lookupIn(list,query,dept,limit=9){
    const scored=[];
    for(const p of list){
      const rank=score(p,query,dept);
      if(rank<0)continue;
      if(scored.length<limit){scored.push({p,rank});scored.sort((a,b)=>b.rank-a.rank);}
      else if(rank>scored[scored.length-1].rank){
        scored.pop();scored.push({p,rank});scored.sort((a,b)=>b.rank-a.rank);
      }
    }
    return scored.map(x=>x.p);
  }
  function immediate(query,dept){return lookupIn(namedKnown,query,dept,4);}
  async function find(query,dept){
    const immediatePlaces=immediate(query,dept);
    if(normalize(query).length<3)return immediatePlaces;
    try{
      const entries=await preload();
      const rows=lookupIn(entries,query,dept,12);
      const seen=new Set();
      return [...immediatePlaces,...rows].filter(p=>{
        const k=normalize(p.name)+"|"+p.lat.toFixed(3)+","+p.lng.toFixed(3);
        if(seen.has(k))return false;seen.add(k);return true;
      }).slice(0,10);
    }catch{return immediatePlaces;}
  }
  global.TrasladosGeo={normalize,preload,find,immediate,lookupIn,
    get indexedCount(){return readyCount;}};
})(window);
