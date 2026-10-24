"use strict";
/* QA VISUAL solamente. Dom nativo y listeners originales intactos. */
(function(win,doc){
 const begin=()=>{
   const legacyTabs=doc.querySelector(".shell>.tabs");
   if(legacyTabs){
     // JS compensates inherited styles that still re-enable the 4-item
     // navigation on some devices; tab listeners remain available to app.js.
     legacyTabs.hidden=true;
     legacyTabs.style.setProperty("display","none","important");
     legacyTabs.style.setProperty("visibility","hidden","important");
   }
   // Tema visible de la candidata, estrictamente local sin llamadas RPC.
   const header=doc.querySelector(".header .header-inner");
   if(header&&!doc.querySelector('[data-qt-component="theme-toggle"]')){
     const button=doc.createElement("button");
     button.type="button";
     button.className="qt-v86-theme-toggle";
     button.setAttribute("data-qt-component","theme-toggle");
     button.setAttribute("aria-label","Cambiar modo claro u oscuro");
     let saved="dark";
     try{saved=win.localStorage.getItem("qt.v86.theme")==="light"?"light":"dark"}catch{}
     const setTheme=theme=>{
       doc.body.classList.toggle("qt-v86-light",theme==="light");
       button.textContent=theme==="light"?"☾ Oscuro":"☼ Claro";
       button.setAttribute("aria-pressed",String(theme==="light"));
       try{win.localStorage.setItem("qt.v86.theme",theme)}catch{}
     };
     setTheme(saved);
     button.addEventListener("click",()=>setTheme(doc.body.classList.contains("qt-v86-light")?"dark":"light"));
     header.append(button);
   }
   const root=doc.getElementById("view-inicio");
   const hero=root?.querySelector(".home-hero");
   const title=hero?.querySelector("h1");
   const intro=hero?.querySelector(".eyebrow");
   if(title){
     title.textContent="Elegí cómo viajar";
     title.removeAttribute("style");
   }
   if(intro)intro.textContent="✦ QUICK TRIP · URUGUAY";
   const origin=doc.getElementById("origin-picker");
   if(!origin)return;
   const foldHelp=()=>{
     const help=origin.querySelector(".gps-permission-help");
     if(!help||help.closest(".qt-qa-gps-details"))return;
     const parent=help.parentElement;
     if(!parent)return;
     const group=doc.createElement("details");
     group.className="qt-qa-gps-details";
     group.setAttribute("aria-label","Opciones de ubicación y permisos GPS");
     const summary=doc.createElement("summary");
     summary.textContent="¿Necesitás ayuda con GPS? Ver opciones";
     group.append(summary);
     parent.insertBefore(group,help);
     group.append(help);
     // El botón nativo Solicitar permiso conserva el listener de app.js.
     // El usuario abre instrucciones solo cuando lo necesita.
   };
   foldHelp();
   const mo=new MutationObserver(foldHelp);
   mo.observe(origin,{childList:true,subtree:true});
   // No crear reservas ni fetch nuevos. No tocar datos del pasajero.
 };
 if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",begin,{once:true});
 else begin();
})(window,document);
