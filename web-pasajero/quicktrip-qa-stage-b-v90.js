"use strict";
/* Quick Trip Etapa B — local QA fixture only.
 * No reservation writes, no driver GPS, no private RPC, no Android changes.
 * Uses existing TripBoard/OwnedPickup/ReservationLive/PremiumMap modules.
 */
(function(win,doc){
 const isolation=doc.querySelector('meta[name="quicktrip-qa-isolation"]')?.content||"";
 const policy=doc.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"";
 if(!isolation.includes("v90-route-sandbox")||
    doc.querySelector('meta[name="traslados-preview"]')?.content!=="read-only"||
    !policy.includes("form-action 'none'"))return;
 const B=win.TrasladosTripBoard,Owned=win.TrasladosOwnedPickup,
       Live=win.TrasladosReservationLive,Map=win.TrasladosPremiumMap;
 if(!B?.metadata||!B?.guidance||!Owned?.match||!Live?.mount||!Map?.render)return;
 let host=null,stage="idle",progress=0,stamp="",identity="";
 const safeRoute=()=>typeof record!=="undefined"&&record.origin&&record.destination&&
   Array.isArray(record.stops)&&record.stops.every(Boolean);
 const coords=p=>p&&Number.isFinite(Number(p.lat))&&Number.isFinite(Number(p.lng))&&
   Number(p.lat)>=-35.3&&Number(p.lat)<=-30&&Number(p.lng)>=-58.8&&Number(p.lng)<=-52.9;
 const identityOf=()=>{
   if(!safeRoute())return "";
   const ps=[record.origin,...record.stops,record.destination];
   return ps.map(p=>p.text+"@"+p.lat+","+p.lng).join("|");
 };
 const dom=(tag,cls,label)=>{
   const el=doc.createElement(tag);el.className=cls||"";
   if(label!=null)el.textContent=label;
   return el;
 };
 const stages={
  pending:{status:"PENDIENTE",phase:null},
  accepted:{status:"ACEPTADA",phase:null},
  enroute:{status:"ACEPTADA",phase:"EN_CAMINO"},
  arrived:{status:"ACEPTADA",phase:"EN_PUNTO"},
  ride:{status:"EN_VIAJE",phase:null},
  finished:{status:"FINALIZADA",phase:null},
  rejected:{status:"RECHAZADA",phase:null}
 };
 function snapshot(){
   const s=stages[stage];if(!s||!safeRoute())return null;
   const total=Number((doc.querySelector(".qt-v90-fare")?.textContent||"").replace(/[^0-9,]/g,"").replace(",","."))||0;
   const d=new Date(stamp||Date.now());
   return {
    id:"LOCAL-QA",code:"QA-SIN-RESERVA",status:s.status,pickup_phase:s.phase,
    pickup_mode:"now",fare_mode:"fixed",quote_status:"PRECIO_FIJO",
    quote_final_total:total,created_at:d.toISOString(),
    confirmed_at:stage==="pending"?null:new Date().toISOString(),
    pickup_phase_updated_at:s.phase?new Date().toISOString():null,
    origin_lat:Number(record.origin.lat),origin_lng:Number(record.origin.lng),
    destination_lat:Number(record.destination.lat),destination_lng:Number(record.destination.lng),
    origin_text:record.origin.text,destination_text:record.destination.text,stops:[]
   };
 }
 function stageButton(area,key,label,variant=""){
   const btn=dom("button","qt-b-button "+variant,label);btn.type="button";
   btn.dataset.qtBAction=key;area.append(btn);return btn;
 }
 // The demo's *verified public road geometry* comes from the existing
 // v90 route adapter. It never contains driver location or private GPS.
 function qaRoad(){
   const road=win.QuickTripV90QaRoad?.read?.();
   if(!road||!Array.isArray(road.points)||road.points.length<2||
      road.points.length>14000||!safeRoute())return null;
   const from=Live.coordinates(record.origin.lat,record.origin.lng);
   const to=Live.coordinates(record.destination.lat,record.destination.lng);
   if(!from||!to)return null;
   const first=road.points[0],last=road.points[road.points.length-1];
   if(Math.abs(first[0]-from.lat)>.008||Math.abs(first[1]-from.lng)>.008||
      Math.abs(last[0]-to.lat)>.008||Math.abs(last[1]-to.lng)>.008)return null;
   return road.points;
 }
 function qaMarker(host,geometry,isPickup){
   const frame=host.querySelector(".premium-real-map");
   const svg=frame?.querySelector("svg.premium-map-overlays");
   if(!svg)return;
   const width=Number(svg.getAttribute("viewBox")?.split(" ")[2]);
   const height=Number(svg.getAttribute("viewBox")?.split(" ")[3]);
   const view=Map.layout(geometry,width,height);
   const p=view?.points?.[0];
   if(!p)return;
   // Replace the renderer's automobile emoji with a high-contrast triangle
   // on the exact same simulated route point. The real renderer is untouched.
   for(const t of [...svg.querySelectorAll("text")]){
     if(t.textContent!=="🚘")continue;
     const prior=t.previousElementSibling;
     if(prior?.tagName.toLowerCase()==="circle")prior.remove();
     t.remove();
   }
   const ns="http://www.w3.org/2000/svg";
   const g=doc.createElementNS(ns,"g");
   g.setAttribute("class","qt-b-fake-car");
   g.setAttribute("transform","translate("+p[0]+","+p[1]+")");
   const shadow=doc.createElementNS(ns,"circle");
   shadow.setAttribute("r","19");shadow.setAttribute("fill","#062c36");
   shadow.setAttribute("stroke","#f5d99f");shadow.setAttribute("stroke-width","2.5");
   const car=doc.createElementNS(ns,"path");
   car.setAttribute("d","M0 -14 L11 12 L0 6 L-11 12 Z");
   car.setAttribute("fill","#f5d99f");car.setAttribute("stroke","#0b393b");
   car.setAttribute("stroke-width","1");
   g.append(shadow,car);
   svg.append(g);
   frame.setAttribute("aria-label","SIMULACIÓN QA: triángulo de automóvil ficticio y "+
     (isPickup?"pasajero en el punto de recogida":"destino del viaje")+
     " sobre el recorrido vial verificado; no muestra GPS real");
 }
 function drawMap(slot,kind){
   if(!slot?.isConnected)return;
   slot.replaceChildren();
   const road=qaRoad();
   if(!road){
     slot.append(dom("p","qt-b-map-unavailable",
       "El mapa aparecerá cuando termine el cálculo vial. No dibujamos ubicaciones inventadas."));
     return;
   }
   const map=dom("div","qt-b-map-frame");slot.append(map);
   const moving=kind==="enroute"||kind==="ride";
   const maxIndex=Math.max(1,Math.floor((road.length-1)*.12));
   const progressIndex=Math.max(0,Math.min(road.length-2,Math.floor((road.length-2)*progress)));
   const carIndex=Math.max(1,Math.round(maxIndex*(1-progress)));
   const segment=kind==="enroute"
     ?road.slice(0,carIndex+1).reverse()
     :kind==="ride"?road.slice(progressIndex):road;
   if(segment.length<2||!Map.render(map,segment,{
     height:252,routeMode:kind==="enroute"?"pickup":kind==="ride"?"ride":"contract"
   })){
     slot.replaceChildren(dom("p","qt-b-map-unavailable","El mapa no está disponible."));
     return;
   }
   if(moving)qaMarker(map,segment,kind==="enroute");
   const legend=dom("div","qt-b-legend");
   if(moving)legend.append(dom("span","","▲ Auto ficticio"));
   legend.append(dom("span","",kind==="ride"?"● Destino":"● Pasajero / recogida"));
   if(kind!=="enroute")legend.append(dom("span","","◆ Destino"));
   slot.append(legend);
   const note=dom("p","qt-b-map-note",moving?
     "SIMULACIÓN QA · El triángulo no es tu conductor real. El trazo sigue calles verificadas, pero su avance es ficticio.":
     "Ruta por calles verificada. No se muestra posición del vehículo hasta disponer de GPS autorizado.");
   slot.append(note);
 }
 function update(){
   if(!host)return;
   const content=host.querySelector(".qt-b-content");
   content.replaceChildren();
   const start=host.querySelector('[data-qt-b-action="request"]');
   const verified=host.closest(".qtc-app")?.dataset.qtV90Quote==="verified";
   if(start){start.hidden=stage!=="idle";start.disabled=!verified;}
   if(stage==="idle"){
     content.append(dom("p","qt-b-help",verified?
       "Recorrido listo. Tocá Simular solicitud para comprobar el circuito sin enviar nada.":
       "Elegí un origen y destino y comprobá el recorrido antes de comenzar."));
     return;
   }
   const row=snapshot();if(!row)return;
   const meta=B.metadata(row),guide=B.guidance(row);
   const status=dom("div","qt-b-state");
   status.append(dom("strong","",meta.label),dom("p","",guide));
   const mark=dom("span","qt-b-local","ESTADO LOCAL · NO ENVIADO");
   status.prepend(mark);content.append(status);
   if(stage==="pending"||stage==="accepted"){
     const same=Owned.match([row],record.origin,record.destination,record.stops);
     if(same)content.append(dom("small","qt-b-owned",
       "Circuito QA reconocido: "+Owned.presentation(same).title));
   }
   const flow=dom("div","qt-b-flow");
   const segments=["Recibida","Aceptada","Recogida","En viaje","Finalizada"];
   const rank={pending:0,accepted:1,enroute:2,arrived:2,ride:3,finished:4,rejected:0}[stage]??0;
   for(let i=0;i<segments.length;i++){
     const step=dom("span",i<=rank?"qt-b-done":"",segments[i]);flow.append(step);
   }
   content.append(flow);
   if(["accepted","enroute","arrived","ride"].includes(stage)){
     const details=dom("details","qt-b-follow");details.open=true;
     const title=dom("summary","",stage==="ride"?"Ver seguimiento del viaje":
       stage==="enroute"?"Conductor en camino · Ver mapa":
       stage==="arrived"?"Conductor llegó · Ver mapa":"Viaje aceptado · Ver recorrido");
     details.append(title);
     const slot=dom("div","qt-b-map-slot");
     details.append(slot);
     content.append(details);
     win.requestAnimationFrame(()=>{if(details.open)drawMap(slot,stage);});
     details.addEventListener("toggle",()=>{
       if(details.open)win.requestAnimationFrame(()=>drawMap(slot,stage));
     });
   }
   const actions=dom("div","qt-b-actions");
   if(stage==="pending"){
     stageButton(actions,"accept","Conductor: aceptar","primary");
     stageButton(actions,"reject","Rechazar","subtle");
   }else if(stage==="accepted"){
     stageButton(actions,"enroute","Salir hacia recogida","primary");
   }else if(stage==="enroute"){
     stageButton(actions,"advance","Avanzar simulación","subtle");
     stageButton(actions,"arrived","Marcar llegada","primary");
   }else if(stage==="arrived"){
     stageButton(actions,"ride","Iniciar viaje","primary");
   }else if(stage==="ride"){
     stageButton(actions,"advance","Avanzar simulación","subtle");
     stageButton(actions,"finished","Finalizar viaje","primary");
   }else stageButton(actions,"reset","Nueva prueba","primary");
   content.append(actions);
 }
 function reset(){
   stage="idle";progress=0;stamp="";identity=identityOf();update();
 }
 function action(name){
   if(name==="request"){
     if(stage!=="idle"||host.closest(".qtc-app")?.dataset.qtV90Quote!=="verified"||!safeRoute())return;
     stage="pending";progress=0;stamp=new Date().toISOString();identity=identityOf();
   }else if(name==="reset"){reset();return;}
   else if(name==="accept"&&stage==="pending")stage="accepted";
   else if(name==="reject"&&stage==="pending")stage="rejected";
   else if(name==="enroute"&&stage==="accepted"){stage="enroute";progress=0;}
   else if(name==="arrived"&&stage==="enroute"){stage="arrived";progress=1;}
   else if(name==="ride"&&stage==="arrived"){stage="ride";progress=0;}
   else if(name==="finished"&&stage==="ride"){stage="finished";progress=1;}
   else if(name==="advance"&&["enroute","ride"].includes(stage))progress=Math.min(.95,progress+.25);
   else return;
   update();
 }
 function install(){
   if(host)return;
   const app=doc.getElementById("qtc-app");
   const quote=app?.querySelector(".qtc-submit-region");
   if(!quote)return;
   host=dom("section","qt-b-sim");
   host.setAttribute("aria-label","Etapa B · Simulación local sin reservas");
   host.innerHTML='<div class="qt-b-heading"><span class="qt-b-pill">ETAPA B · QA LOCAL</span><h3>Solicitud y seguimiento</h3><small>Ninguna acción envía solicitudes, acepta viajes reales ni consulta GPS privado.</small></div>'+
      '<button type="button" class="qt-b-button primary qt-b-start" data-qt-b-action="request">Simular solicitud de viaje</button>'+
      '<div class="qt-b-content" aria-live="polite"></div>';
   quote.after(host);
   doc.body.classList.add("qt-b-qa");
   host.addEventListener("click",e=>{
     const btn=e.target.closest?.("[data-qt-b-action]");
     if(!btn||!host.contains(btn)||btn.disabled)return;
     action(btn.dataset.qtBAction);
   });
   identity=identityOf();
   update();
   const obs=new MutationObserver(()=>{
     if(stage!=="idle"&&identity&&identity!==identityOf())reset();
     else if(stage==="idle"){
       const start=host.querySelector('[data-qt-b-action="request"]');
       if(start)start.disabled=app.dataset.qtV90Quote!=="verified";
     }
   });
   obs.observe(app,{attributes:true,attributeFilter:["data-qt-v90-quote"]});
   doc.addEventListener("traslados:route-updated",()=>{
     if(stage!=="idle"&&identity!==identityOf())reset();
     else if(stage==="idle")update();
   });
 }
 if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",install,{once:true});
 else install();
 // quicktrip-clean creates the DOM on init. Retry only until it is mounted.
 if(!host){
   const observer=new MutationObserver(()=>{
     if(!host)install();if(host)observer.disconnect();
   });
   observer.observe(doc.body,{childList:true,subtree:true});
 }
})(window,document);
