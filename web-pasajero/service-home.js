"use strict";
/* PWA v61: service home is pure presentation/navigation.
 * No GPS, Supabase, reservation creation, or geocoding lives here.
 */
(function(root){
  function mount(doc,actions){
    const notice=doc.getElementById("service-coming-soon");
    if(!notice)throw new Error("Missing service placeholder");
    const select=actions?.onSelect;
    const navigate=actions?.onNavigate;
    if(typeof select!=="function"||typeof navigate!=="function")throw new Error("Missing home navigation actions");
    const choices=new Set(["now","schedule"]);
    for(const button of doc.querySelectorAll("[data-service]")){
      button.addEventListener("click",()=>{
        const kind=button.dataset.service;
        if(kind==="delivery"){
          notice.hidden=false;
          return; // This service has no backend contract yet. Never submit a trip.
        }
        if(!choices.has(kind))return;
        notice.hidden=true;
        select(kind);
      });
    }
    const allowedViews=new Set(["inicio","viajes","instalar","reservar","cuenta"]);
    for(const button of doc.querySelectorAll("[data-open-view]")){
      button.addEventListener("click",()=>{
        const target=button.dataset.openView;
        if(allowedViews.has(target))navigate(target);
      });
    }
    return Object.freeze({services:["now","schedule","delivery"]});
  }
  root.TrasladosServiceHome=Object.freeze({mount});
})(window);
