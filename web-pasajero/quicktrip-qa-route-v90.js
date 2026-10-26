"use strict";
/* Quick Trip v90: isolated QA presentation adapter. No stable-engine changes.
   Public driver availability and pickup ETA use the existing read-only clients.
   Route and price remain DEMO; never submit reservations from this preview. */
(function(win,doc){
 const iso=doc.querySelector('meta[name="quicktrip-qa-isolation"]')?.content||"";
 const policy=doc.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"";
 if(!iso.includes("v90-route-sandbox")||
    !policy.includes("https://direcciones.ide.uy")||
    !policy.includes("https://router.project-osrm.org"))return;
 doc.body.classList.add("qt-v90-qa");
 const banner=doc.getElementById("qt-public-qa-banner");
 if(banner)banner.textContent="QA v90 · DISPONIBILIDAD REAL DE CONSULTA · RUTA Y PRECIO DEMO · SIN RESERVAS";
 doc.title="Quick Trip · v90 · Recogida real (consulta) y recorrido QA";
 const RATE_QA=40,ROUTER="https://router.project-osrm.org",TIMEOUT=9500;
 const isUy=p=>p&&Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&
   p.lat>=-35.3&&p.lat<=-30&&p.lng>=-58.8&&p.lng<=-52.9;
 const kmString=n=>n.toLocaleString("es-UY",{minimumFractionDigits:1,maximumFractionDigits:1});
 const money=n=>n.toLocaleString("es-UY",{minimumFractionDigits:0,maximumFractionDigits:2});
 let lastKey="",sequence=0,controller=null,root=null,ui=null;
 function points(){
   if(typeof record==="undefined"||!record.origin||!record.destination)return [];
   const arr=[record.origin,...record.stops,record.destination];
   return arr.length>=2&&arr.length<=6&&arr.every(isUy)?arr:[];
 }
 function routeKey(ps){return ps.map(p=>p.lng.toFixed(6)+","+p.lat.toFixed(6)).join(";");}
 function safeRoute(data,ps){
  if(data?.code!=="Ok"||!Array.isArray(data.routes)||!Array.isArray(data.waypoints)||data.waypoints.length!==ps.length)return null;
  const r=data.routes[0];
  if(!r||!Number.isFinite(r.distance)||!Number.isFinite(r.duration)||
     r.distance<=100||r.distance>600000||r.duration<=30||r.duration>24*3600)return null;
  const ge=r.geometry;
  const geometry=ge?.type==="LineString"&&Array.isArray(ge.coordinates)&&
    ge.coordinates.length>=2&&ge.coordinates.length<=14000&&
    ge.coordinates.every(p=>Array.isArray(p)&&p.length>=2&&
      Number.isFinite(p[0])&&Number.isFinite(p[1])&&isUy({lng:p[0],lat:p[1]}))
      ?ge.coordinates:null;
  // A verified distance and time may exist without usable geometry.
  return {km:r.distance/1000,min:Math.ceil(r.duration/60),
    fare:Math.round((r.distance/1000)*RATE_QA*100)/100,geometry};
 }
 function node(tag,cls,txt){
   const n=doc.createElement(tag);n.className=cls;
   if(txt!==undefined)n.textContent=txt;return n;
 }
 function mapSVG(geometry){
  if(!geometry||geometry.length<2)return null;
  const xs=geometry.map(p=>p[0]),ys=geometry.map(p=>p[1]);
  const [minX,maxX,minY,maxY]=[Math.min(...xs),Math.max(...xs),Math.min(...ys),Math.max(...ys)];
  const spanX=Math.max(.001,maxX-minX),spanY=Math.max(.001,maxY-minY);
  const mapped=geometry.map(([x,y])=>[(12+272*(x-minX)/spanX).toFixed(1),(110-94*(y-minY)/spanY).toFixed(1)]);
  const svg=doc.createElementNS("http://www.w3.org/2000/svg","svg");
  svg.setAttribute("viewBox","0 0 300 126");svg.setAttribute("role","img");
  svg.setAttribute("aria-label","Forma aproximada del recorrido vial de prueba, sin escala cartográfica");
  const path=doc.createElementNS(svg.namespaceURI,"polyline");
  path.setAttribute("fill","none");path.setAttribute("stroke","#8ec1ae");path.setAttribute("stroke-width","3");
  path.setAttribute("stroke-linecap","round");path.setAttribute("stroke-linejoin","round");
  path.setAttribute("points",mapped.map(p=>p.join(",")).join(" "));
  svg.appendChild(path);
  for(const [p,label] of [[mapped[0],"O"],[mapped[mapped.length-1],"D"]]){
   const dot=doc.createElementNS(svg.namespaceURI,"circle");
   dot.setAttribute("cx",p[0]);dot.setAttribute("cy",p[1]);
   dot.setAttribute("r","7");dot.setAttribute("stroke","#e7c88e");dot.setAttribute("stroke-width","2");
   dot.setAttribute("fill","#13373b");svg.appendChild(dot);
   const t=doc.createElementNS(svg.namespaceURI,"text");
   t.setAttribute("x",p[0]);t.setAttribute("y",String(Number(p[1])+2.5));
   t.setAttribute("text-anchor","middle");t.setAttribute("fill","#fff7e6");
   t.setAttribute("font-size","7");t.textContent=label;svg.appendChild(t);
  }
  return svg;
 }
 function install(){
  if(root)return true;
  const app=doc.getElementById("qtc-app");
  const summary=app?.querySelector(".qtc-submit-region");
  const driver=app?.querySelector(".qtc-driver");
  const map=app?.querySelector(".premium-route-canvas");
  if(!summary||!driver||!map)return false;
  root=app;root.dataset.qtV90="sandbox";
  const quote=node("section","qt-v90-quote");quote.setAttribute("aria-label","Cálculo vial de prueba");
  const heading=node("strong","qt-v90-quote-head","Recorrido de prueba");
  const text=node("p","qt-v90-quote-message","Elegí origen y destino para calcular una ruta vial de prueba.");
  const results=node("div","qt-v90-quote-results");
  results.hidden=true;
  const fields=[["km","Distancia vial"],["min","Duración estimada"],["fare","Precio ilustrativo"]];
  const slots={};for(const [id,label] of fields){
   const item=node("div","qt-v90-metric"),small=node("small","",label),strong=node("strong","qt-v90-"+id,"—");
   item.append(small,strong);results.appendChild(item);slots[id]=strong;
  }
  const disclaimer=node("small","qt-v90-disclaimer","Solo QA · OSRM demo (OpenStreetMap). Precio ilustrativo: 40 UYU/km. NO es cotización oficial.");
  const roadDetails=node("details","qt-v90-map-details");roadDetails.hidden=true;
  const roadSummary=node("summary","","Ver mapa del recorrido");
  roadDetails.append(roadSummary);
  quote.append(heading,text,results,disclaimer,roadDetails);
  const title=summary.querySelector(".qtc-summary-title");
  if(title)title.after(quote);else summary.prepend(quote);
  // The old read-only QA warning is true for official results, but not for
  // the separately labelled demo, so we hide it ONLY inside v90 when quote is valid.
  const art=node("div","qt-v90-map");art.setAttribute("aria-label","Recorrido vial calculado en QA");
  const mapTitle=node("strong","","Recorrido vial · vista de prueba");
  const mapHelp=node("small","","Mapa del trayecto seleccionado, no muestra un conductor real.");
  const mapStage=node("div","qt-v90-map-stage");
  art.append(mapTitle,mapStage,mapHelp);art.hidden=true;roadDetails.append(art);
  // Reuse the original passenger pickup-presence node: app.js already fetches
  // public_driver_availability_v1 and pickup-eta. Both are read-only.
  // No driver position is exposed or fabricated. Submit remains disabled.
  driver.dataset.qtV90Driver="public";
  ui={text,results,slots,map,art,mapStage,roadDetails,disclaimer,routeGeometry:null};
  function expandMap(){
    if(!roadDetails.open||!ui?.routeGeometry)return;
    const latlng=ui.routeGeometry.map(([lng,lat])=>[lat,lng]);
    // The same map renderer used by the existing passenger app, only in
    // contract mode (origin to destination); never draws a moving car.
    const ok=win.TrasladosPremiumMap?.render?.(mapStage,latlng,{height:218,routeMode:"contract"});
    if(!ok){
      const sketch=mapSVG(ui.routeGeometry);
      mapStage.replaceChildren();
      if(sketch)mapStage.append(sketch);
    }
  }
  roadDetails.addEventListener("toggle",expandMap);
  const cta=doc.getElementById("urgent-send");if(cta)cta.disabled=true;
  return true;
 }
 function setState(message,verified=null){
  if(!ui)return;
  ui.text.textContent=message;
  ui.results.hidden=!verified;
  ui.art.hidden=!verified;
  ui.roadDetails.hidden=!verified;
  ui.map.dataset.qtV90Road=verified?"verified":"pending";
  root.dataset.qtV90Quote=verified?"verified":"pending";
  ui.routeGeometry=verified?.geometry||null;
  // QA-only read of already verified public OSRM geometry. No GPS, no
  // backend request and no mutation of the stable routing engine.
  if(typeof win.QuickTripV90QaRoad!=="object")win.QuickTripV90QaRoad=Object.freeze({
    read:()=>{
      if(!ui?.routeGeometry||root?.dataset.qtV90Quote!=="verified")return null;
      return {key:lastKey,points:ui.routeGeometry.map(([lng,lat])=>[lat,lng])};
    }
  });
  ui.mapStage.replaceChildren();
  if(verified){
   ui.slots.km.textContent=kmString(verified.km)+" km";
   ui.slots.min.textContent=verified.min+" min";
   ui.slots.fare.textContent="$ "+money(verified.fare)+" UYU";
   const svg=mapSVG(verified.geometry);
   if(svg)ui.mapStage.append(svg);
   else ui.art.hidden=true;
   if(ui.roadDetails.open&&ui.routeGeometry)win.requestAnimationFrame(()=>{
     if(ui?.roadDetails.open)win.TrasladosPremiumMap?.render?.(
       ui.mapStage,ui.routeGeometry.map(([lng,lat])=>[lat,lng]),{height:218,routeMode:"contract"});
   });
  }
 }
 async function recalc(){
   if(!install())return;
   const ps=points();
   const key=ps.length?routeKey(ps):"";
   if(key===lastKey)return;
   lastKey=key;sequence++;
   if(controller)controller.abort();
   if(!key){
     setState("Elegí origen y destino válidos para la prueba de ruta.");
     return;
   }
   const seq=sequence;
   controller=new AbortController();
   const timer=setTimeout(()=>controller.abort(),TIMEOUT);
   setState("Consultando ruta vial pública de prueba…");
   try{
    const url=ROUTER+"/route/v1/driving/"+key+"?overview=full&geometries=geojson&steps=false";
    const response=await fetch(url,{method:"GET",mode:"cors",credentials:"omit",
     cache:"no-store",referrerPolicy:"no-referrer",signal:controller.signal});
    if(!response.ok)throw Error("HTTP "+response.status);
    const payload=await response.json();
    const validated=safeRoute(payload,ps);
    if(!validated)throw Error("Respuesta de ruta no verificable");
    if(seq!==sequence)return;
    setState("Ruta vial comprobada en OSRM demo; el precio es solo un cálculo de QA.",validated);
   }catch(e){
    if(seq!==sequence)return;
    setState("Sin ruta vial verificable en esta prueba. No mostramos kilómetros ni precio inventados.");
   }finally{clearTimeout(timer);}
 }
 let scheduled=false;
 function schedule(){
  if(scheduled)return;
  scheduled=true;
  win.requestAnimationFrame(()=>{scheduled=false;recalc()});
 }
 function start(){
  const observer=new MutationObserver(changes=>{
   if(!root){schedule();return;}
   if(changes.some(x=>(x.target.closest?.("#origin-picker,#destination-picker,.premium-route-stops")&&
    !x.target.closest?.(".qt-v90-quote,.qt-v90-driver-demo"))))schedule();
  });
  observer.observe(doc.body,{subtree:true,childList:true,attributes:true,attributeFilter:["hidden","class"]});
  doc.addEventListener("traslados:route-updated",schedule);
  schedule();
 }
 if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",start,{once:true});
 else start();
})(window,document);
