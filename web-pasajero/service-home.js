"use strict";
/* PWA v63: service home is pure presentation/navigation.
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
          const opening=notice.hidden;
          notice.hidden=!opening;
          button.setAttribute?.("aria-expanded",String(opening));
          // On short mobile screens the explanation sits below three cards.
          // Reveal it without trapping focus or triggering a fake booking.
          const arrow=button.querySelector?.(".service-card-arrow");
          if(arrow)arrow.textContent=opening?"−":"＋";
          if(opening){
            const reduced=Boolean(root.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches);
            notice.scrollIntoView?.({block:"nearest",behavior:reduced?"auto":"smooth"});
          }
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
