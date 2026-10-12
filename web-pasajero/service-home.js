"use strict";
/* PWA v62: service home is pure presentation/navigation.
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
          // The preview is an accessible disclosure, never a reservation request.
          notice.hidden=!notice.hidden;
          button.setAttribute?.("aria-expanded",String(!notice.hidden));
          return;
        }
        if(!choices.has(kind))return;
        notice.hidden=true;
        const parcel=doc.querySelector('[data-service="delivery"]');
        parcel?.setAttribute?.("aria-expanded","false");
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
