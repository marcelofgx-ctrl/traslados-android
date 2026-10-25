"use strict";
/* Quick Trip v89 UX-only. Hook existing native GPS button; do NOT add a GPS
   request, permission request, fetch or new routing/booking function. */
(function(win,doc){
 const iso=doc.querySelector('meta[name="quicktrip-qa-isolation"]')?.content||"";
 const csp=doc.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"";
 if(!iso.includes("v89-") || !csp.includes("connect-src https://direcciones.ide.uy"))return;
 doc.body.classList.add("qt-v89-gps");
 doc.title="Quick Trip · Web/PWA v89 · GPS intuitivo QA";
 const banner=doc.getElementById("qt-public-qa-banner");
 if(banner)banner.textContent="QA v89 · SOLO GPS/IDE · SIN SUPABASE · SIN RESERVAS";
 const $=q=>doc.querySelector(q);
 let hooked=false;
 function install(){
  if(hooked)return;
  const host=$("#origin-picker"),button=host?.querySelector(".location-use-current");
  if(!button)return;
  hooked=true;
  button.classList.add("qt-v89-location-trigger");
  button.dataset.qtGpsLabel="Mi ubicación";
  button.setAttribute("aria-label","Usar mi ubicación actual como origen");
  button.title="Usar mi ubicación actual";
  const controls=host.querySelector(".location-controls");
  const selection=host.querySelector(".location-selected");
  const help=host.querySelector(".gps-permission-help");
  const status=doc.createElement("p");
  status.className="qt-v89-gps-status";status.hidden=true;
  status.setAttribute("role","status");status.setAttribute("aria-live","polite");
  host.querySelector(".loc-search-origin")?.after(status);
  const show=message=>{if(status.textContent!==message)status.textContent=message;status.hidden=!message};
  const refreshBusy=()=>{
    const busy=button.disabled;
    button.dataset.qtGpsLabel=busy?"Buscando ubicación…":"Mi ubicación";
    if(busy&&controls&&!controls.hidden)show("Buscando tu ubicación. Si Chrome ya tiene permiso, no necesita pedirlo otra vez.");
  };
  function refreshHelp(){
    if(!help||help.hidden)return;
    const title=help.querySelector(".gps-permission-title")?.textContent||"";
    const details=help.closest(".qt-qa-gps-details");
    if(details&&!details.open)details.open=true;
    if(/bloquead|denegad/i.test(title)){
      show("Ubicación bloqueada. Tocá el candado de Chrome → Permisos → Ubicación → Permitir.");
    }else if(/antes de autorizar/i.test(title)){
      show("Chrome necesita tu permiso una sola vez. Tocá «Solicitar permiso» abajo.");
    }else if(/demorando|determinar/i.test(title)){
      show("Android no obtuvo una posición. Revisá que esté activada Ubicación y volvé a intentar.");
    }else{
      show("No pudimos obtener tu ubicación. Podés reintentar o escribir el origen.");
    }
  }
  const refreshSelection=()=>{
    if(!selection||selection.hidden){return}
    const label=selection.querySelector(".location-selected-label");
    if(!label)return;
    show("");
    const generic=label.textContent.trim()==="Ubicación actual (GPS)";
    const old=selection.querySelector(".qt-v89-gps-fallback");
    if(generic&&!old){
      const hint=doc.createElement("small");hint.className="qt-v89-gps-fallback";
      hint.textContent="GPS obtenido, pero no pudimos obtener la calle. Tocá Editar para ajustarla.";
      label.after(hint);
    }else if(!generic&&old){old.remove()}
  };
  button.addEventListener("click",()=>{
    show("Comprobando permiso y ubicación en Chrome…");
    win.requestAnimationFrame(refreshBusy);
  },{passive:true});
  const observer=new MutationObserver(records=>{
    if(records.some(r=>r.target===button))refreshBusy();
    if(records.some(r=>r.target===help||help?.contains(r.target)))refreshHelp();
    if(records.some(r=>r.target===selection||selection?.contains(r.target)))refreshSelection();
  });
  observer.observe(button,{attributes:true,attributeFilter:["disabled"]});
  if(help)observer.observe(help,{subtree:true,childList:true,attributes:true,attributeFilter:["hidden"]});
  if(selection)observer.observe(selection,{subtree:true,childList:true,attributes:true,attributeFilter:["hidden"]});
  refreshBusy();refreshHelp();refreshSelection();
 }
 if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",install,{once:true});
 else install();
 // Original native pickers are constructed by deferred app.js.
 if(!hooked){
  const startup=new MutationObserver(()=>{install();if(hooked)startup.disconnect()});
  startup.observe(doc.body,{childList:true,subtree:true});
 }
})(window,document);
