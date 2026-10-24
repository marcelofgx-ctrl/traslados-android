"use strict";
/*
 * Quick Trip QA v86: SOLO estado de pantalla y borrador de Tu Viaje.
 * No crea ni intercepta reservas, precio, sesión, GPS, ETA ni llamadas HTTP.
 * La PWA usa exactamente los selectores y métodos nativos de app.js.
 */
(function(win,doc){
  const STORE="quicktrip.draft.now.v1";
  const MAX_AGE=12*60*60*1000;
  const ALLOWED=new Set(["inicio","viaje","reservar","viajes","cuenta","instalar","seguimiento"]);
  let restoring=false,readyToSave=false,handlingPop=false,lastView="",saveTimer=null,finished=false;
  const el=id=>doc.getElementById(id);
  const currentView=()=>doc.querySelector(".view.active")?.id?.slice(5)||"inicio";
  const owner=()=>{
    // Guardamos solamente el id de propietario para no restaurar datos de
    // un pasajero en otra cuenta. Nunca persistir tokens ni PIN en este módulo.
    try{return typeof session!=="undefined"&&session?.customer?.id
      ?String(session.customer.id):"guest";}catch{return "guest";}
  };
  function safeRemove(){try{win.localStorage.removeItem(STORE);}catch{}}
  function safeRead(){
    try{
      const raw=win.localStorage.getItem(STORE);
      if(!raw)return null;
      const item=JSON.parse(raw);
      if(item?.version!==1||!Number.isFinite(item.at)||
        Date.now()-item.at>MAX_AGE||item.at>Date.now()+60000||
        item.owner!==owner()){safeRemove();return null;}
      return item;
    }catch{safeRemove();return null;}
  }
  function safePoint(point){
    if(!point||typeof point.text!=="string"||!point.text.trim()||
       point.text.length>280||typeof point.department!=="string"||
       point.department.length>50)return null;
    const lat=Number(point.lat),lng=Number(point.lng);
    if(!Number.isFinite(lat)||!Number.isFinite(lng)||
      lat < -35.3||lat > -30||lng < -58.8||lng > -52.9)return null;
    return {text:point.text,lat,lng,department:point.department,
      approximate:point.approximate===true};
  }
  function snapshot(){
    if(typeof record==="undefined"||typeof selectedPickupMode==="undefined")return null;
    const mode=String(selectedPickupMode);
    if(mode==="schedule")return null;
    const origin=safePoint(record.origin),destination=safePoint(record.destination);
    if(!origin&&!destination)return null;
    const count=Number(el("passengers")?.value||1);
    const passengers=Number.isInteger(count)&&count>=1&&count<=8?count:1;
    const comments=String(el("comments")?.value||"").slice(0,600);
    return {version:1,at:Date.now(),owner:owner(),origin,destination,
      passengers,comments,mode:/^(now|10|15|20|30|45|60)$/.test(mode)?mode:"now"};
  }
  function saveDraft(){
    if(!readyToSave||restoring||finished||currentView()!=="viaje"||
      !el("booking-success")?.hidden)return;
    const data=snapshot();
    if(!data)return;
    try{win.localStorage.setItem(STORE,JSON.stringify(data));}catch{}
  }
  function scheduleSave(){
    if(!readyToSave||restoring||finished)return;
    win.clearTimeout(saveTimer);
    saveTimer=win.setTimeout(saveDraft,150);
  }
  function restoreDraft(){
    if(readyToSave||restoring||currentView()!=="viaje")return;
    restoring=true;
    try{
      const draft=safeRead();
      if(draft&&typeof originPicker!=="undefined"&&typeof destPicker!=="undefined"){
        // Reusar persist() NATIVO: recalcula una nueva ruta verificada. Nunca
        // recuperar un precio anterior, conductor, ETA ni una solicitud.
        const a=safePoint(draft.origin),b=safePoint(draft.destination);
        if(a)originPicker.persist(a);
        if(b)destPicker.persist(b);
        const passengers=Number(draft.passengers);
        if(Number.isInteger(passengers)&&passengers>=1&&passengers<=8)
          el("passengers").value=String(passengers);
        if(typeof draft.comments==="string")el("comments").value=draft.comments.slice(0,600);
        if(/^(now|10|15|20|30|45|60)$/.test(String(draft.mode))&&
           typeof setWhen==="function"){
          setWhen(draft.mode);
          if(el("journey-when-select"))el("journey-when-select").value=draft.mode;
        }
        if(typeof refreshBookingSteps==="function")refreshBookingSteps();
        doc.dispatchEvent(new CustomEvent("quicktrip:draft-restored"));
      }
    }catch{
      // Datos corruptos o selector aún ausente: nunca bloquear un viaje nuevo.
      safeRemove();
    }finally{restoring=false;readyToSave=true;}
  }
  function clearAfterReceipt(){
    if(el("booking-success")?.hidden===false){
      finished=true;
      win.clearTimeout(saveTimer);
      safeRemove();
    }
  }
  function stateFor(view){
    return {...(typeof win.history.state==="object"&&win.history.state||{}),
      qtNavigation:{view}};
  }
  function navigateBackState(view){
    handlingPop=true;
    try{
      if(view==="viaje"&&typeof enterService==="function")enterService("now");
      else if(view==="reservar"&&typeof enterService==="function")enterService("schedule");
      else if(typeof shiftView==="function")shiftView(view);
    }finally{win.setTimeout(()=>{handlingPop=false;},0);}
  }
  function onViewChanged(){
    const view=currentView();
    if(view===lastView)return;
    lastView=view;
    if(view==="viaje"){finished=false;restoreDraft();}
    if(!handlingPop&&ALLOWED.has(view)){
      // Cada pantalla es una entrada de historial, no una nueva página.
      if(win.history.state?.qtNavigation?.view!==view)
        win.history.pushState(stateFor(view),"",win.location.href);
    }
  }
  function boot(){
    if(!el("view-inicio")||!el("view-viaje")||typeof enterService!=="function"||
      typeof originPicker==="undefined"||!originPicker)return;
    lastView=currentView();
    const initial=win.history.state?.qtNavigation?.view;
    if(!initial)win.history.replaceState(stateFor(lastView),"",win.location.href);
    for(const view of doc.querySelectorAll(".view")){
      new MutationObserver(onViewChanged).observe(view,{
        attributes:true,attributeFilter:["class"]
      });
    }
    win.addEventListener("popstate",event=>{
      const view=event.state?.qtNavigation?.view;
      if(view&&ALLOWED.has(view)){
        navigateBackState(view);
        lastView=view;
        if(view==="viaje"){finished=false;restoreDraft();}
      }
    });
    doc.addEventListener("click",event=>{
      const back=event.target.closest?.(".journey-back");
      if(!back||currentView()!=="viaje")return;
      if(win.history.state?.qtNavigation?.view!=="viaje")return;
      event.preventDefault();event.stopImmediatePropagation();
      win.history.back();
    },true);
    for(const id of ["origin-picker","destination-picker"]){
      const target=el(id);
      if(target)new MutationObserver(scheduleSave).observe(target,{childList:true,subtree:true});
    }
    const success=el("booking-success");
    if(success)new MutationObserver(clearAfterReceipt).observe(success,{
      attributes:true,attributeFilter:["hidden"]
    });
    for(const id of ["passengers","comments","journey-when-select"]){
      const field=el(id);
      if(field){field.addEventListener("input",scheduleSave);
        field.addEventListener("change",scheduleSave);}
    }
    // Si el pasajero llega directamente a Tu Viaje vía historial del navegador.
    if(lastView==="viaje")restoreDraft();
    win.QuickTripResilience=Object.freeze({
      status:()=>({view:currentView(),draftStored:Boolean(safeRead()),readyToSave}),
      storageKey:STORE
    });
  }
  if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",boot,{once:true});
  else boot();
})(window,document);
