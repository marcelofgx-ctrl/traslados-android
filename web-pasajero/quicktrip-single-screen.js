"use strict";
/* Single-screen passenger presentation, opt-in ?ui=single.
   No booking, geocoding, ETA, GPS, route or Supabase implementation here. */
(function(){
 if(new URLSearchParams(location.search).get("ui")!=="single")return;
 const ready=()=>{
  const view=document.getElementById("view-viaje");
  if(!view)return;
  document.body.classList.add("qt-single");
  document.title="Quick Trip · Tu viaje";
  const hero=view.querySelector(".journey-hero");
  if(hero&&!document.getElementById("qt-single-heading")){
   const heading=document.createElement("div");heading.id="qt-single-heading";
   heading.innerHTML='<img src="./pruebas/visual-v6/quicktrip-logo.svg" alt="Quick Trip"><span>Tu viaje</span>';
   hero.before(heading);
  }
  const picker=document.getElementById("destination-picker");
  if(picker&&!document.getElementById("qt-single-history")){
   const wrap=document.createElement("div");wrap.id="qt-single-history";
   const toggle=document.createElement("button");toggle.type="button";toggle.className="qt-history-toggle";
   toggle.textContent="◷ Destinos recientes";toggle.setAttribute("aria-expanded","false");
   const panel=document.createElement("div");panel.className="qt-history-panel";panel.hidden=true;
   panel.innerHTML='<p>Consultá tus destinos anteriores desde Mis viajes.</p>';
   const open=document.createElement("button");open.type="button";open.textContent="Ver mis viajes";
   open.addEventListener("click",()=>document.querySelector('.tab[data-view="viajes"]')?.click());
   panel.append(open);wrap.append(toggle,panel);picker.after(wrap);
   toggle.addEventListener("click",()=>{panel.hidden=!panel.hidden;toggle.setAttribute("aria-expanded",String(!panel.hidden))});
   document.addEventListener("click",e=>{if(!wrap.contains(e.target)){panel.hidden=true;toggle.setAttribute("aria-expanded","false")}});
  }
  const when=document.getElementById("journey-when-select");
  if(when){when.value="now";when.dispatchEvent(new Event("change",{bubbles:true}))}
 };
 if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",ready,{once:true});else ready();
})();
