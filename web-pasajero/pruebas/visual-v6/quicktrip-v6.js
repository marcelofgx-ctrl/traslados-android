"use strict";
/* Quick Trip V6 visual composition, independent from V1–V5 and all network engines.
 * QA-only sample data. No real booking, GPS, user account or payment. */
(()=>{
 const d=document,top=d.querySelector(".topbar"),progress=d.querySelector(".flow-heading");
 if(!top||!progress||!d.querySelector('[data-screen="3"]'))return;
 top.innerHTML='<button type="button" class="qt-back" id="header-back" aria-label="Volver al paso anterior"><span aria-hidden="true">‹</span></button>'
 +'<a class="qt-brand" href="#paso-1" aria-label="Quick Trip, volver al primer paso"><img src="./quicktrip-logo.svg" alt="Quick Trip" width="240" height="80"></a>'
 +'<button type="button" class="theme-switch" id="theme-switch" aria-label="Cambiar a modo claro" aria-pressed="true" title="Cambiar claro y oscuro">'
 +'<span class="theme-sun" aria-hidden="true">☀</span><span class="theme-moon" aria-hidden="true">☾</span><span id="theme-label" class="visually-hidden">Oscuro</span></button>';
 progress.innerHTML='<div class="progress-line" id="progress" role="progressbar" aria-label="Progreso de tu viaje, paso 1 de 4" aria-valuemin="1" aria-valuemax="4" aria-valuenow="1"><span id="progress-fill" style="width:25%"></span></div>'
 +'<ol class="flow-steps" aria-label="Etapas del viaje">'
 +'<li class="is-current" aria-current="step"><span class="step-icon" aria-hidden="true">🚘</span><span>Recorrido</span></li>'
 +'<li><span class="step-icon" aria-hidden="true">▦</span><span>Cuándo</span></li>'
 +'<li><span class="step-icon" aria-hidden="true">♙</span><span>Tus datos</span></li>'
 +'<li><span class="step-icon" aria-hidden="true">✓</span><span>Confirmar</span></li></ol>'
 +'<span id="step-counter" class="visually-hidden">01 / 04</span>';
 d.getElementById("title-route").textContent="¿A dónde vamos?";
 d.querySelector(".route-title")?.classList.add("qt-visually-reduced");
 d.getElementById("title-when").textContent="¿Cuándo querés viajar?";
 d.getElementById("title-when").nextElementSibling.textContent="Ahora o en las próximas 2 horas.";
 d.getElementById("title-contact").textContent="Tus datos";
 d.getElementById("title-confirm").textContent="Todo listo para solicitar";
 d.getElementById("title-confirm").nextElementSibling.textContent="Revisá los datos de tu viaje antes de confirmar.";
 d.querySelectorAll(".route-editor .edit-link").forEach(b=>b.innerHTML='<span aria-hidden="true">✎</span> Editar');
 const map=d.querySelector(".map-figure");
 if(map){
   map.classList.add("qt-map-illustration");
   const actual=map.querySelector('path[stroke="#176955"]');
   const glow=map.querySelector('path[stroke="#b3d7ca"]');
   if(actual)actual.setAttribute("stroke","#efcb78");
   if(glow)glow.setAttribute("stroke","#efd994");
   map.querySelector(".map-float").textContent="MAPA DE EJEMPLO · SIN GPS";
 }
 const fare=d.querySelector(".trip-price");
 if(fare){
   fare.querySelector(".trip-price-main .field-kicker").textContent="PRECIO ORIENTATIVO DEL VIAJE";
   const labs=fare.querySelectorAll(".trip-minor .field-kicker");
   if(labs[0])labs[0].textContent="DISTANCIA DEL VIAJE";
   if(labs[1])labs[1].textContent="DURACIÓN ESTIMADA";
 }
 const stop=d.querySelector("#paso-1 .quiet-details summary");
 if(stop)stop.innerHTML='<span class="plus-sign" aria-hidden="true">+</span> Agregar paradas <small>(opcional)</small><span class="detail-chevron" aria-hidden="true">›</span>';
 const pickup=d.querySelector(".pickup-inline");
 if(pickup){
   const car=d.createElement("span");car.className="qt-driver-art";car.textContent="🚘";car.setAttribute("aria-hidden","true");
   pickup.insertBefore(car,pickup.firstChild);
 }
 const times=d.querySelector(".time-choices");
 if(times){
   const slots=[["Ahora","⚡","Lo antes posible"],["En 10 minutos","◷",""],["En 15 minutos","◷",""],["En 20 minutos","◷",""],
   ["En 30 minutos","◷",""],["En 45 minutos","◷",""],["En 1 hora","◷",""],["Otra hora","▦",""]];
   times.innerHTML=slots.map(([value,icon,subtitle],i)=>
      '<button type="button" class="time-choice'+(i===0?' is-picked':'')+'" data-time="'+value+'" aria-pressed="'+(i===0)+'">'
     +'<span class="qt-time-icon" aria-hidden="true">'+icon+'</span><span class="time-choice-title">'+(value==="Otra hora"?"Elegir otra hora":value)
     +'</span><span class="time-tick" aria-hidden="true">✓</span>'+(subtitle?'<small>'+subtitle+'</small>':'')+'</button>').join("");
 }
 const total=d.querySelector("#paso-4 .total-topline");
 if(total)total.firstElementChild.textContent="PRECIO DEL VIAJE";
 const footer=d.querySelector(".bottom-action");
 if(footer){
   const nav=d.createElement("nav");nav.className="qt-bottom-nav";nav.setAttribute("aria-label","Navegación principal de prueba");
   nav.innerHTML='<button type="button" data-demo="Inicio" aria-current="page"><span aria-hidden="true">⌂</span>Inicio</button>'
   +'<button type="button" data-demo="Mis traslados"><span aria-hidden="true">▤</span>Mis traslados</button>'
   +'<button type="button" data-demo="Menú"><span aria-hidden="true">☰</span>Menú</button>';
   footer.insertAdjacentElement("afterend",nav);
 }
 d.getElementById("header-back")?.addEventListener("click",()=>{
   const back=d.getElementById("back-button");
   if(back&&!back.hidden)back.click();
 });
})();