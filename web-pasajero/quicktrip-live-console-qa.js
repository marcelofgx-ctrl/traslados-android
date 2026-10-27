"use strict";
/* Passenger QA dashboard; deliberately separate from production PWA app.js.
   Tokens exist only in runtime memory, never persistent browser storage, URLs or logs. */
(function(root,doc){
 const $=id=>doc.getElementById(id);
 const allowedHost=/^quicktrip-(?:live-)?qa[a-z0-9-]*\.[a-z0-9-]+\.workers\.dev$/i;
 const validPoint=p=>p&&typeof p.lat==="number"&&typeof p.lng==="number"&&
   Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&p.lat>=-35.5&&p.lat<=-30&&p.lng>=-59.5&&p.lng<=-52;
 const uuid=id=>/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id);
 const stages=["PENDIENTE","ACEPTADA","EN_CAMINO","EN_PUNTO","EN_VIAJE","FINALIZADA"];
 let endpoint="",passToken="",requestId="",interval=null,lastRoadAt=0,lastLocation=null,lastRouteMode="",rpc=null;
 const setNote=(msg)=>{$("qa-note").textContent=msg;};
 function config(){
   const url=new URL($("qa-endpoint").value.trim());
   if(url.protocol!=="https:"||!allowedHost.test(url.hostname)||url.port||
     url.username||url.password||url.search||url.hash||!["","/"].includes(url.pathname))
     throw Error("Solo se permite el Worker dedicado quicktrip-qa-*.workers.dev");
   const token=$("qa-token").value.trim();
   if(token.length<32||token.length>240)throw Error("Ingresá un token PASAJERO QA válido");
   endpoint=url.origin;passToken=token;$("qa-token").value="";
   return true;
 }
 async function api(method,path,body){
   if(!endpoint||!passToken||!path.startsWith("/v1/"))throw Error("No configurado");
   const res=await fetch(endpoint+path,{method,credentials:"omit",cache:"no-store",mode:"cors",
     referrerPolicy:"no-referrer",
     headers:{Authorization:"Bearer "+passToken,Accept:"application/json",...(body?{"Content-Type":"application/json"}:{})},
     ...(body?{body:JSON.stringify(body)}:{})});
   if(!res.ok)throw Error("Servidor QA: HTTP "+res.status);
   const out=await res.json();if(out.qaOnly!==true&&out.request?.qaOnly!==true)throw Error("Respuesta no QA");
   return out;
 }
 function disconnect(){
   if(interval!==null){clearInterval(interval);interval=null;}
   endpoint="";passToken="";requestId="";rpc=null;lastLocation=null;lastRoadAt=0;lastRouteMode="";
   $("qa-live").hidden=true;$("qa-map").hidden=true;$("qa-map").replaceChildren();
   $("qa-km").textContent="—";$("qa-eta").textContent="—";
   $("qa-fresh").textContent="—";setNote("Conexión cerrada. Ninguna posición queda almacenada.");
 }
 async function create(){
   try{
     config();setNote("Creando solicitud sintética en backend QA…");
     const created=await api("POST","/v1/requests",{
       idempotencyKey:crypto.randomUUID(),
       origin:{label:"Aeropuerto de Carrasco · QA",lat:-34.835,lng:-56.029},
       destination:{label:"Centro Montevideo · QA",lat:-34.905,lng:-56.186},
       fareUYU:600
     });
     if(!created.request?.id||!uuid(created.request.id))throw Error("Solicitud no verificable");
     $("qa-id").value=created.request.id;
     await connect(false);
   }catch(e){setNote(e.message||"No se pudo crear la solicitud");}
 }
 async function connect(readFields=true){
   try{
     if(readFields)config();
     const id=$("qa-id").value.trim();if(!uuid(id))throw Error("Ingresá un UUID QA válido");
     requestId=id;
     rpc=root.QuickTripLiveQA.createRpc({backendURL:endpoint+"/",tokenProvider:async()=>passToken});
     const v=await api("GET","/v1/requests/"+id);
     if(!v.request||!validPoint(v.request.origin)||!validPoint(v.request.destination))throw Error("Solicitud QA sin coordenadas válidas");
     $("qa-live").hidden=false;$("qa-trip-id").textContent="ID de prueba: "+id;
     setNote("Solicitud encontrada. Compartí el ID con Mapa QT QA.");
     if(interval!==null)clearInterval(interval);
     await refresh();
     interval=setInterval(()=>{if(!doc.hidden)refresh().catch(()=>{});},5000);
   }catch(e){setNote(e.message||"No fue posible conectar");}
 }
 async function road(position,to,mode){
   // Never draw a car based on coordinates alone: require verified public road geometry.
   if(Date.now()-lastRoadAt<20000 && lastLocation && lastRouteMode===mode &&
     Math.abs(lastLocation.driverLat-position.driverLat)<.00045 &&
     Math.abs(lastLocation.driverLng-position.driverLng)<.00045)return;
   lastRoadAt=Date.now();lastLocation=position;lastRouteMode=mode;
   const coords=[position.driverLng,position.driverLat,to.lng,to.lat];
   const p=coords.map(x=>Number(x).toFixed(6));
   const url="https://router.project-osrm.org/route/v1/driving/"+p[0]+","+p[1]+";"+p[2]+","+p[3]+
     "?overview=full&geometries=geojson&steps=false&alternatives=false";
   const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),7000);
   try{
     const response=await fetch(url,{cache:"no-store",signal:controller.signal});
     const data=await response.json(),r=data?.routes?.[0],ge=r?.geometry?.coordinates;
     if(!response.ok||data.code!=="Ok"||!Array.isArray(ge)||ge.length<2||ge.length>14000||
       !Number.isFinite(r.distance)||r.distance<0||!Number.isFinite(r.duration)||r.duration<0||
       !ge.every(p=>Array.isArray(p)&&p.length===2&&validPoint({lat:p[1],lng:p[0]})))throw Error("Ruta no verificable");
     const first=ge[0],last=ge[ge.length-1];
     if(Math.abs(first[0]-position.driverLng)>.015||Math.abs(first[1]-position.driverLat)>.015||
        Math.abs(last[0]-to.lng)>.015||Math.abs(last[1]-to.lat)>.015)throw Error("Geometría inconsistente");
     const geometry=ge.map(([lng,lat])=>[lat,lng]);
     if(!root.TrasladosPremiumMap.render($("qa-map"),geometry,{height:250,routeMode:mode}))throw Error("Mapa no disponible");
     $("qa-map").hidden=false;$("qa-km").textContent=(r.distance/1000).toLocaleString("es-UY",{maximumFractionDigits:1})+" km";
     $("qa-eta").textContent=Math.max(1,Math.ceil(r.duration/60))+" min";
     $("qa-map-note").textContent="GPS real autorizado · recorrido vial verificado. Actualización cada 5 s con la página abierta.";
   }catch(_){
     $("qa-map").hidden=true;$("qa-map").replaceChildren();
     $("qa-km").textContent="—";$("qa-eta").textContent="—";
     $("qa-map-note").textContent="GPS reciente recibido, pero el recorrido vial no pudo verificarse.";
   }finally{clearTimeout(timer);}
 }
 async function refresh(){
   if(!requestId||!rpc)return;
   try{
     const v=await api("GET","/v1/requests/"+requestId);
     const trip=v.request,state=trip?.state;
     if(!stages.includes(state)&&state!=="CANCELADA"&&state!=="RECHAZADA")throw Error("Estado inválido");
     $("qa-state").textContent=({"PENDIENTE":"Esperando conductor","ACEPTADA":"Conductor asignado",
       "EN_CAMINO":"Conductor en camino","EN_PUNTO":"Conductor llegó","EN_VIAJE":"Tu viaje está en curso",
       "FINALIZADA":"Viaje terminado","RECHAZADA":"Solicitud rechazada","CANCELADA":"Solicitud cancelada"})[state];
     if(["FINALIZADA","CANCELADA","RECHAZADA"].includes(state)){
       $("qa-map").hidden=true;$("qa-map").replaceChildren();
       $("qa-km").textContent="—";$("qa-eta").textContent="—";$("qa-fresh").textContent="Finalizado";
       $("qa-map-note").textContent="El seguimiento privado terminó y el automóvil fue retirado del mapa.";
       clearInterval(interval);interval=null;return;
     }
     if(state==="PENDIENTE"){
       $("qa-map").hidden=true;$("qa-map").replaceChildren();
       $("qa-map-note").textContent="Esperando aceptación; no hay GPS del conductor.";
       $("qa-fresh").textContent="Pendiente";return;
     }
     const position=await rpc("customer_reservation_position_v1",{p_reservation_id:requestId});
     if(!position?.available || !Number.isFinite(position.driverLat) || !Number.isFinite(position.driverLng)){
       $("qa-map").hidden=true;$("qa-map").replaceChildren();
       $("qa-km").textContent="—";$("qa-eta").textContent="—";
       $("qa-fresh").textContent="GPS pendiente";lastLocation=null;lastRoadAt=0;
       $("qa-map-note").textContent="No hay una posición GPS autorizada y reciente. No se inventa el vehículo.";
       return;
     }
     $("qa-fresh").textContent="GPS "+position.gpsAgeSec+" s";
     const to=state==="EN_VIAJE"?trip.destination:trip.origin;
     if(!validPoint(to))throw Error("Destino no verificado");
     await road(position,to,state==="EN_VIAJE"?"ride":"pickup");
   }catch(_){
     $("qa-map").hidden=true;$("qa-map").replaceChildren();
     $("qa-km").textContent="—";$("qa-eta").textContent="—";
     $("qa-fresh").textContent="Sin conexión";
     $("qa-map-note").textContent="Seguimiento temporalmente no disponible. El automóvil se ocultó.";
   }
 }
 $("qa-create").addEventListener("click",create);
 $("qa-connect").addEventListener("click",()=>connect(true));
 $("qa-disconnect").addEventListener("click",disconnect);
 $("qa-copy").addEventListener("click",async()=>{
   try{await navigator.clipboard.writeText(requestId);setNote("ID QA copiado.");}
   catch{setNote("Copiá manualmente el ID que aparece arriba.");}
 });
 root.addEventListener("pagehide",()=>{if(interval!==null)clearInterval(interval);});
})(window,document);
