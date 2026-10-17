"use strict";
/* Traslados · direcciones oficiales IDE Uruguay. Sin DOM ni reservas.
 * IMPORTANTE: /candidates entrega calles CALLE con lat=0,lng=0. Eso NO
 * significa "calle inexistente": la posición se resuelve por /direcUnica
 * recién al tocar el resultado. Eliminar calles por 0,0 rompe autocompletado.
 */
(function(root){
  const IDE="https://direcciones.ide.uy/api/v1/geocode";
  const norm=s=>root.TrasladosGeo.normalize(String(s||""));
  const uy=(lat,lng)=>Number.isFinite(lat)&&Number.isFinite(lng)&&
    lat>=-35.3&&lat<=-30&&lng>=-58.8&&lng<=-52.9;
  const ignored=new Set(["calle","av","avenida","avda","nro","numero","num","esq","esquina","de","del","la","las","los","el","y"]);
  const cache=new Map();
  const CACHE_MS=60000,MAX_CACHE=80;
  const terms=q=>norm(q).split(" ").filter(w=>w&&!/^\d+$/.test(w)&&!ignored.has(w));
  const isStreet=item=>String(item?.type||"").toUpperCase()==="CALLE";
  const coords=item=>({lat:Number(item?.lat),lng:Number(item?.lng)});
  function score(item,q,department){
    if(!item?.address)return -1;
    const address=norm(item.address),requestedDept=norm(department);
    const returnedDept=norm(item.departamento);
    if(requestedDept && (returnedDept
       ?returnedDept!==requestedDept
       :!address.split(" ").includes(requestedDept)))return -1;
    const tokens=terms(q);
    if(!tokens.length)return -1;
    const words=address.split(" ");
    if(!tokens.every(t=>words.some(w=>w===t||(t.length>=3&&w.startsWith(t)))))return -1;
    const askedNumbers=String(q).match(/\b\d{1,5}\b/g)||[];
    const foundNumbers=String(item.address).match(/\b\d{1,5}\b/g)||[];
    // No presentamos una altura equivocada como si fuese exacta.
    if(!askedNumbers.every(n=>foundNumbers.includes(n)))return -1;
    return 20+(address.startsWith(norm(q))?10:0)+
      (uy(coords(item).lat,coords(item).lng)?2:0);
  }
  function verified(rows,q,department){
    return (Array.isArray(rows)?rows:[]).filter(item=>{
      if(!item?.address)return false;
      const p=coords(item);
      // CALLE puede ser 0,0 sin geometría; se resuelve al elegirla.
      return uy(p.lat,p.lng)||(isStreet(item)&&p.lat===0&&p.lng===0);
    }).map(item=>({item,rank:score(item,q,department)}))
      .filter(x=>x.rank>=0).sort((a,b)=>b.rank-a.rank)
      .map(x=>x.item);
  }
  async function query(endpoint,q,limit){
    const key=endpoint+"|"+q+"|"+limit;
    const current=cache.get(key);
    if(current&&Date.now()-current.at<CACHE_MS)return current.rows;
    const ctrl=typeof AbortController==="function"?new AbortController():null;
    const timer=ctrl&&typeof setTimeout==="function"?setTimeout(()=>ctrl.abort(),9500):null;
    try{
      const resp=await fetch(IDE+"/"+endpoint+"?limit="+limit+"&q="+encodeURIComponent(q),
        {cache:"no-store",...(ctrl?{signal:ctrl.signal}:{})});
      if(!resp.ok)throw new Error("No pudimos consultar direcciones de Uruguay.");
      const rows=await resp.json();
      if(!Array.isArray(rows))throw new Error("Respuesta de direcciones inesperada.");
      if(cache.size>=MAX_CACHE)cache.delete(cache.keys().next().value);
      cache.set(key,{rows,at:Date.now()});
      return rows;
    }finally{if(timer!==null)clearTimeout(timer);}
  }
  async function find(q,department){
    const trimmed=String(q||"").trim();
    if(trimmed.length<3)return [];
    const rows=await query("candidates",trimmed,40);
    let result=verified(rows,trimmed,department);
    // La altura exacta puede no salir en /candidates; se intenta dirección
    // única, sin aceptar automáticamente otro número.
    if(!result.length&&/\b\d{1,5}\b/.test(trimmed)){
      result=verified(await query("direcUnica",trimmed+", "+department,12),trimmed,department);
    }
    return result.slice(0,12);
  }
  async function findAcross(q,preferredDepartment){
    const trimmed=String(q||"").trim();
    if(trimmed.length<3)return {local:[],other:[]};
    const rows=await query("candidates",trimmed,40);
    const valid=verified(rows,trimmed,null);
    const preferred=norm(preferredDepartment);
    const local=valid.filter(item=>norm(item.departamento)===preferred);
    const other=valid.filter(item=>norm(item.departamento)!==preferred);
    return {local:local.slice(0,12),other:other.slice(0,8)};
  }
  async function resolve(item,asked,department){
    const realDepartment=item?.departamento||department;
    if(score(item,asked,realDepartment)<0)
      throw new Error("La dirección seleccionada ya no coincide con la búsqueda.");
    let p=coords(item);
    if(uy(p.lat,p.lng))
      return {...p,approximate:isStreet(item)||String(item.stateMsg||"").toLowerCase().includes("aproxim")};
    // No utilizar nunca coordenadas 0,0 ni asignar las del centro del departamento.
    // /direcUnica consulta la geometría oficial para la calle elegida.
    const rows=verified(await query("direcUnica",item.address,15),asked,realDepartment);
    const exactLocality=norm(item.localidad);
    const matched=rows.find(x=>norm(x.localidad)===exactLocality&&uy(coords(x).lat,coords(x).lng))||
      rows.find(x=>uy(coords(x).lat,coords(x).lng));
    if(!matched)throw new Error("Encontramos la calle, pero no su punto exacto. Agregá número o esquina para ubicarla.");
    p=coords(matched);
    return {...p,approximate:isStreet(item)||isStreet(matched)||
      String(matched.stateMsg||"").toLowerCase().includes("aproxim")};
  }
  async function reverse(lat,lng){
    if(!uy(Number(lat),Number(lng)))throw new Error("Punto fuera de Uruguay");
    const resp=await fetch(IDE+"/reverse?limit=1&latitud="+encodeURIComponent(lat)+
      "&longitud="+encodeURIComponent(lng),{cache:"no-store"});
    if(!resp.ok)return null;
    const rows=await resp.json();
    const first=Array.isArray(rows)?rows[0]:null;
    return first?.address?{address:first.address,departamento:first.departamento}:null;
  }
  root.TrasladosAddress=Object.freeze({score,verified,find,findAcross,resolve,reverse});
})(window);
