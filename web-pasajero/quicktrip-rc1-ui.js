"use strict";
/* Presentation owner: validated V86 shape. Native backend/event handlers stay intact. */
(function(win,doc){
 if(doc.documentElement.dataset.qtContract!=="rc1")return;
 doc.body.classList.add("qt-rc1");
 const begin=()=>{
   const legacyTabs=doc.querySelector(".shell>.tabs");
   if(legacyTabs){
     // JS compensates inherited styles that still re-enable the 4-item
     // navigation on some devices; tab listeners remain available to app.js.
     legacyTabs.hidden=true;
     legacyTabs.style.setProperty("display","none","important");
     legacyTabs.style.setProperty("visibility","hidden","important");
   }
   // Local theme preference; no RPC.
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
   // No new requests, pricing or reservations. Do not mutate passenger data.
 };
 if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",begin,{once:true});
 else begin();
})(window,document);

"use strict";
/* Quick Trip RC1: presentational reuse of verified GPS UX. Hook existing native GPS button; do NOT add a GPS
   request, permission request, fetch or new routing/booking function. */
(function(win,doc){
 if(doc.documentElement.dataset.qtContract!=="rc1")return;
 doc.body.classList.add("qt-v89-gps");
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
  // Keep a direct GPS action available with a selected origin. Reuse the
  // existing native button: no new geolocation call, lookup or persistence.
  const selectedGps=doc.createElement("button");
  selectedGps.type="button";
  selectedGps.className="qt-v90-selected-gps";
  selectedGps.textContent="⌖ Actualizar con mi ubicación";
  selectedGps.setAttribute("aria-label","Actualizar origen con mi ubicación GPS actual");
  selectedGps.title="Solicitar mi posición GPS al tocar, no automáticamente";
  selectedGps.hidden=true;
  host.querySelector(".location-widget")?.append(selectedGps);
  selectedGps.addEventListener("click",()=>{
    if(button.disabled)return;
    button.click();
  });
  const syncSelectedHelp=()=>{
    if(!controls||!selection)return;
    const selected=!selection.hidden;
    const showing=selected&&help&&!help.hidden;
    host.classList.toggle("qt-v90-selected-gps-help",Boolean(showing));
    if(showing)controls.hidden=false;
    else if(selected)controls.hidden=true;
  };

  const show=message=>{if(status.textContent!==message)status.textContent=message;status.hidden=!message};
  const refreshBusy=()=>{
    const busy=button.disabled;
    button.dataset.qtGpsLabel=busy?"Buscando ubicación…":"Mi ubicación";
    selectedGps.disabled=busy;
    selectedGps.textContent=busy?"⌖ Buscando ubicación…":"⌖ Actualizar con mi ubicación";
    if(busy&&controls&&!controls.hidden)show("Buscando tu ubicación. Si Chrome ya tiene permiso, no necesita pedirlo otra vez.");
  };
  function refreshHelp(){
    syncSelectedHelp();
    if(!help||help.hidden){host.classList.remove("qt-v89-prompt-help");return;}
    const title=help.querySelector(".gps-permission-title")?.textContent||"";
    const isPrompt=/antes de autorizar/i.test(title);
    host.classList.toggle("qt-v89-prompt-help",isPrompt);
    if(isPrompt){
      const req=[...help.querySelectorAll("button")].find(x=>x.textContent.includes("Solicitar permiso"));
      if(req)req.textContent="Permitir ubicación";
    }
    const details=help.closest(".qt-qa-gps-details");
    if(details&&!details.open)details.open=true;
    if(/bloquead|denegad/i.test(title)){
      show("Ubicación bloqueada. Tocá el candado de Chrome → Permisos → Ubicación → Permitir.");
    }else if(/antes de autorizar/i.test(title)){
      show("Chrome necesita tu permiso una sola vez. Tocá «Permitir ubicación» abajo.");
    }else if(/demorando|determinar/i.test(title)){
      show("Android no obtuvo una posición. Revisá que esté activada Ubicación y volvé a intentar.");
    }else{
      show("No pudimos obtener tu ubicación. Podés reintentar o escribir el origen.");
    }
  }
  const refreshSelection=()=>{
    selectedGps.hidden=!selection||selection.hidden;
    syncSelectedHelp();
    if(!selection||selection.hidden){return}
    const label=selection.querySelector(".location-selected-label");
    if(!label)return;
    show("");
    const oldToast=doc.getElementById("toast");
    if(oldToast&&/Solicitando ubicación al navegador/i.test(oldToast.textContent||""))oldToast.hidden=true;
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
    // Some Android/Chromium builds expose help before dispatching mutation
    // records for the folded container. Reconcile presentation after the
    // original async permission check, never request location ourselves.
    win.setTimeout(refreshHelp,150);
    win.setTimeout(refreshHelp,450);
    win.setTimeout(syncSelectedHelp,500);
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


/* Keep theme toggle available INSIDE Tu Viaje, not only in hidden homepage bar. */
(function(win,doc){
 if(doc.documentElement.dataset.qtContract!=="rc1")return;
 function install(){
  const brand=doc.querySelector("#qtc-app .qtc-brand");
  const original=doc.querySelector('.header [data-qt-component="theme-toggle"]');
  if(!brand||!original||brand.querySelector(".qt-rc1-journey-theme"))return;
  const toggle=doc.createElement("button");
  toggle.type="button";toggle.className="qt-v86-theme-toggle qt-rc1-journey-theme";
  toggle.setAttribute("aria-label","Cambiar modo claro u oscuro");
  const refresh=()=>{
    toggle.textContent=original.textContent;
    toggle.setAttribute("aria-pressed",original.getAttribute("aria-pressed")||"false");
  };
  refresh();
  toggle.addEventListener("click",()=>{original.click();refresh()});
  new MutationObserver(refresh).observe(original,{attributes:true,childList:true,subtree:true});
  brand.append(toggle);
 }
 win.addEventListener("load",()=>win.requestAnimationFrame(install),{once:true});
})(window,document);
