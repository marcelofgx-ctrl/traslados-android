"use strict";
/*
 * Quick Trip v85 — sólo resiliencia de presentación: botón Atrás y borrador
 * de la pestaña actual. No crea RPC, fetch, GPS, rutas, cuentas ni reservas.
 * La sesión del pasajero sigue siendo responsabilidad exclusiva de app.js.
 */
(function(win,doc){
  const KEY="quicktrip.v85.draft.session.v1";
  const MAX_AGE=6*60*60*1000;
  const MAX_NOTES=900;
  const VIEW_KEY="quickTripV85View";
  let restoring=false,previousView="inicio",initialised=false;
  let saveTimer=null,previousFingerprint="";
  const el=id=>doc.getElementById(id);
  const activeView=()=>{
    const e=doc.querySelector(".view.active");
    return e?.id?.startsWith("view-")?e.id.slice(5):"";
  };
  function selectedPoint(point){
    const lat=Number(point?.lat),lng=Number(point?.lng),text=String(point?.text||"").trim();
    if(!Number.isFinite(lat)||!Number.isFinite(lng)||
       lat< -35.3||lat> -30||lng< -58.8||lng> -52.9||
       !text||text.length>280)return null;
    const dept=String(point?.department||"").slice(0,60);
    return {text,lat,lng,department:dept,approximate:Boolean(point?.approximate)};
  }
  function currentDraft(){
    if(typeof record==="undefined")return null;
    return {
      origin:selectedPoint(record.origin),
      destination:selectedPoint(record.destination),
      // Sólo valores del formulario; nunca guardar cookies, PIN ni token.
      passengers:String(el("passengers")?.value||"1"),
      comments:String(el("comments")?.value||"").slice(0,MAX_NOTES)
    };
  }
  function fingerprint(draft){return JSON.stringify(draft);}
  function erase(){try{win.sessionStorage.removeItem(KEY);}catch{}previousFingerprint="";}
  function save(){
    if(restoring||!initialised)return;
    // Nunca rehidratar un pedido que el servidor acaba de confirmar.
    if(el("booking-success")?.hidden===false){erase();return;}
    const data=currentDraft();
    if(!data)return;
    const finger=fingerprint(data);
    if(finger===previousFingerprint)return;
    previousFingerprint=finger;
    if(!data.origin&&!data.destination&&data.passengers==="1"&&!data.comments.trim()){
      erase();return;
    }
    try{win.sessionStorage.setItem(KEY,JSON.stringify({at:Date.now(),data}));}
    catch{/* Private mode / storage blocked: native form remains usable. */}
  }
  function scheduleSave(){
    if(restoring)return;
    win.clearTimeout(saveTimer);
    saveTimer=win.setTimeout(save,120);
  }
  function restore(){
    let snapshot=null;
    try{snapshot=JSON.parse(win.sessionStorage.getItem(KEY)||"null");}
    catch{erase();return;}
    if(!snapshot)return;
    if(!Number.isFinite(snapshot.at)||Date.now()-snapshot.at>MAX_AGE||
       snapshot.at>Date.now()+60000||!snapshot.data||typeof snapshot.data!=="object"){
      erase();return;
    }
    const d=snapshot.data;
    const origin=d.origin?selectedPoint(d.origin):null;
    const destination=d.destination?selectedPoint(d.destination):null;
    if((d.origin&&!origin)||(d.destination&&!destination)){erase();return;}
    if(typeof originPicker==="undefined"||typeof destPicker==="undefined")return;
    restoring=true;
    try{
      // Reusar estrictamente los selectores originales: validan las coordenadas,
      // actualizan record y recalculan la ruta por motor vial verificado.
      // No persistir geometría, precio ni ETA: se vuelven a consultar.
      if(typeof routeStepAutoAdvanced!=="undefined")routeStepAutoAdvanced=true;
      if(origin)originPicker.persist(origin);
      if(destination)destPicker.persist(destination);
      const passengers=el("passengers");
      if(passengers&&/^[1-8]$/.test(String(d.passengers||"")))passengers.value=String(d.passengers);
      const notes=el("comments");
      if(notes&&typeof d.comments==="string")notes.value=d.comments.slice(0,MAX_NOTES);
      if(typeof routeStepAutoAdvanced!=="undefined")routeStepAutoAdvanced=false;
      if(typeof refreshBookingSteps==="function")refreshBookingSteps();
      previousFingerprint=fingerprint(currentDraft());
    }catch{
      // Dejar la reserva en su estado original si un borrador no es válido.
      erase();
    }finally{restoring=false;}
  }
  function syncView(){
    const now=activeView();
    if(!now||now===previousView)return;
    if(now==="viaje"&&previousView!=="viaje"){
      // Entrada sólo tras el selector original; no modificar URLs ni deep links.
      if(win.history.state?.[VIEW_KEY]!=="viaje"){
        win.history.pushState({...win.history.state,[VIEW_KEY]:"viaje"},"",win.location.href);
      }
    }
    previousView=now;
  }
  function boot(){
    if(initialised)return;
    if(!el("view-viaje")||!el("view-inicio"))return;
    initialised=true;
    previousView=activeView()||"inicio";
    if(previousView==="inicio"&&win.history.state?.[VIEW_KEY]===undefined)
      win.history.replaceState({...win.history.state,[VIEW_KEY]:"inicio"},"",win.location.href);
    const observer=new MutationObserver(syncView);
    for(const view of doc.querySelectorAll(".view"))
      observer.observe(view,{attributes:true,attributeFilter:["class"]});
    win.addEventListener("popstate",()=>{
      const target=win.history.state?.[VIEW_KEY];
      if(target==="inicio"&&typeof shiftView==="function"){
        // Original navigation, never replace its handlers.
        shiftView("inicio");
        previousView="inicio";
      }
    });
    doc.addEventListener("click",event=>{
      const back=event.target.closest?.(".journey-back[data-open-view='inicio']");
      if(back&&win.history.state?.[VIEW_KEY]==="viaje")win.history.back();
      // El botón sigue ejecutando su listener original en service-home.js.
    },{capture:true});
    for(const id of ["origin-picker","destination-picker"]){
      const host=el(id);
      if(host)new MutationObserver(scheduleSave).observe(host,{
        childList:true,subtree:true,attributes:true,attributeFilter:["class","hidden"]
      });
    }
    const receipt=el("booking-success");
    if(receipt)new MutationObserver(scheduleSave).observe(receipt,{
      childList:true,attributes:true,attributeFilter:["hidden"]
    });
    for(const id of ["passengers","comments"]){
      const field=el(id);if(field){
        field.addEventListener("input",scheduleSave);
        field.addEventListener("change",scheduleSave);
      }
    }
    win.addEventListener("pagehide",()=>{win.clearTimeout(saveTimer);save();});
    restore();
    scheduleSave();
  }
  if(doc.readyState==="complete")boot();
  else win.addEventListener("load",boot,{once:true});
})(window,document);
