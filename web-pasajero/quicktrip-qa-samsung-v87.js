"use strict";
/* Only presentation state in isolated read-only QA; no fetch, GPS, price,
   ETA, availability, reservations or new event handlers for native actions. */
(function(win,doc){
 const isolation=doc.querySelector('meta[name="quicktrip-qa-isolation"]');
 const csp=doc.querySelector('meta[http-equiv="Content-Security-Policy"]');
 if(!isolation||!String(csp?.content||"").includes("connect-src 'none'"))return;
 doc.body.classList.add("qt-v87-qa");
 doc.title="Quick Trip · Web/PWA v87 · Samsung QA";
 const top=doc.getElementById("qt-public-qa-banner");
 if(top)top.textContent="QUICK TRIP v87 · PRUEBA VISUAL · SIN BACKEND · NO INGRESAR DATOS REALES";
 let scheduled=false;
 function apply(){
  scheduled=false;
  const app=doc.getElementById("qtc-app");if(!app)return;
  const map=app.querySelector(".premium-route-canvas");
  if(map){
   let note=map.querySelector(".qt-v87-route-note");
   if(!note){
    note=doc.createElement("div");note.className="qt-v87-route-note";note.setAttribute("role","status");
    note.innerHTML='<span class="qt-v87-route-mark" aria-hidden="true">⌖</span><span><strong>Mapa pendiente de verificación</strong><span>Sin ruta, kilómetros ni precio comprobables en esta prueba.</span></span>';
    map.prepend(note);
   }
   const recap=doc.getElementById("urgent-route-recap");
   const distance=recap?.querySelector("[data-final-route-distance]")?.textContent?.trim()||"";
   const verified=Boolean(recap&&!recap.hidden&&distance&&distance!=="—"&&distance!=="Sin estimación");
   const want=verified?"verified":"unverified";
   if(map.dataset.qtV87Route!==want)map.dataset.qtV87Route=want;
   if(note.hidden!==verified)note.hidden=verified;
  }
  const driver=app.querySelector(".qtc-driver");
  if(driver){
   if(driver.dataset.qtV87Qa!=="true")driver.dataset.qtV87Qa="true";
   const small=driver.querySelector(".qtc-section-name small");
   if(small&&small.textContent!=="Sin conexión real en QA")small.textContent="Sin conexión real en QA";
   if(!driver.querySelector(".qt-v87-driver-note")){
    const item=doc.createElement("p");item.className="qt-v87-driver-note";
    item.textContent="Disponibilidad y llegada no consultables en esta prueba.";
    item.setAttribute("role","status");
    driver.querySelector(".qtc-section-name")?.after(item);
   }
  }
  const waiting=doc.getElementById("qtc-summary-wait");
  const text="Importe, distancia y duración no verificables en modo QA. No se enviarán solicitudes.";
  if(waiting&&!waiting.hidden&&waiting.textContent!==text)waiting.textContent=text;
  // Fail closed: native CTA and its existing handler stay intact but disabled.
  const send=doc.getElementById("urgent-send");
  if(send&&!send.disabled)send.disabled=true;
 }
 function schedule(){if(scheduled)return;scheduled=true;win.requestAnimationFrame(apply);}
 if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",schedule,{once:true});else schedule();
 const obs=new MutationObserver(records=>{
  if(records.some(r=>!r.target.closest?.(".qt-v87-route-note,.qt-v87-driver-note")&&(r.type==="childList"||["hidden","class"].includes(r.attributeName))))schedule();
 });
 obs.observe(doc.body,{subtree:true,childList:true,attributes:true,attributeFilter:["hidden","class"]});
})(window,document);
