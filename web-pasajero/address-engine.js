"use strict";
/* Módulo independiente de direcciones oficiales de Uruguay.
 * Sin DOM, sesión, reservas ni cálculo de rutas: reutilizable por
 * traslados y, en el futuro, mensajería/paquetería.
 */
(function(root){
  const IDE="https://direcciones.ide.uy/api/v1/geocode";
  const norm=s=>root.TrasladosGeo.normalize(String(s||""));
  const uy=(lat,lng)=>Number.isFinite(lat)&&Number.isFinite(lng)&&
    lat>=-35.3&&lat<=-30&&lng>=-58.8&&lng<=-52.9;
  function score(item,q,department){
    if(!item?.address||!department)return -1;
    const address=norm(item.address),requestedDept=norm(department);
    const returnedDept=norm(item.departamento);
    if(returnedDept ? returnedDept!==requestedDept : !address.split(" ").includes(requestedDept))return -1;
    const ignored=new Set(["calle","av","avenida","avda","nro","numero","num","esq","esquina","de","del","la","las","los","el","y"]);
    const terms=norm(q).split(" ").filter(w=>w&&!/^\d+$/.test(w)&&!ignored.has(w));
    if(!terms.length)return -1;
    const words=address.split(" ");
    if(!terms.every(t=>words.some(w=>w===t||(t.length>=3&&w.startsWith(t)))))return -1;
    // Una altura ausente o incorrecta nunca es una coincidencia exacta.
    const askedNumbers=String(q).match(/\b\d{1,5}\b/g)||[];
    const foundNumbers=String(item.address).match(/\b\d{1,5}\b/g)||[];
    if(!askedNumbers.every(n=>foundNumbers.includes(n)))return -1;
    return 20+(address.startsWith(norm(q))?10:0)+
      (Number.isFinite(Number(item.lat))&&Number.isFinite(Number(item.lng))?2:0);
  }
  function verified(rows,q,department){
    return (Array.isArray(rows)?rows:[]).filter(x=>x&&x.address&&
      ((x.lat==null||x.lng==null)||uy(Number(x.lat),Number(x.lng))))
      .map(item=>({item,rank:score(item,q,department)})).filter(x=>x.rank>=0)
      .sort((a,b)=>b.rank-a.rank).map(x=>x.item);
  }
  async function query(endpoint,q,limit){
    const resp=await fetch(IDE+"/"+endpoint+"?limit="+limit+"&q="+encodeURIComponent(q),{cache:"no-store"});
    if(!resp.ok)throw new Error("La búsqueda de direcciones no está disponible.");
    return resp.json();
  }
  async function find(q,department){
    const trimmed=String(q||"").trim();
    if(trimmed.length<3)return [];
    let addresses=verified(await query("candidates",trimmed,30),trimmed,department);
    if(!addresses.length&&/\b\d{1,5}\b/.test(trimmed)){
      addresses=verified(await query("direcUnica",trimmed+", "+department,10),trimmed,department);
    }
    return addresses.slice(0,12);
  }
  async function resolve(item,asked,department){
    if(score(item,asked,department)<0)throw new Error("La dirección seleccionada ya no coincide con la búsqueda.");
    const lat=Number(item.lat),lng=Number(item.lng);
    if(uy(lat,lng))return {lat,lng};
    const rows=verified(await query("direcUnica",item.address,5),asked,department);
    const found=rows.find(x=>uy(Number(x.lat),Number(x.lng)));
    if(!found)throw new Error("La dirección no tiene coordenadas verificables.");
    return {lat:Number(found.lat),lng:Number(found.lng)};
  }
  root.TrasladosAddress=Object.freeze({score,find,resolve});
})(window);
