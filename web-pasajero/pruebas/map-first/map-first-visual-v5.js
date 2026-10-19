"use strict";
/* Map-First Visual V5, phase 1: ONE existing map, ONE authoritative quote,
 * ONE confirmation button. Presentation only; no API, GPS, price or booking code. */
(function(root){
  const doc=root.document;
  if(new URL(root.location.href).searchParams.get("mapfirst")!=="1")return;
  if(!doc.querySelector('meta[name="traslados-preview"]'))return;
  doc.body.classList.add("mf-v5-phase1");
  function mount(){
    const view=doc.getElementById("view-viaje");
    const route=doc.getElementById("booking-step-route");
    const map=doc.querySelector('[data-booking-step="route"] .premium-route-canvas');
    const ticket=doc.querySelector("#urgent-actions.mf-v4-confirm .mf-v4-ticket");
    if(!view||!route||!map||!ticket)return false;
    if(doc.body.dataset.mfV5Phase1==="mounted")return true;
    doc.body.dataset.mfV5Phase1="mounted";
    // Compact first screen: editable origin/destination precede the SAME
    // geographic canvas. Never clone route, fare, map, booking or selection.
    const destination=route.querySelector("#destination-picker");
    function arrangeRoute(){
      if(doc.body.classList.contains("journey-mode-quick") && destination?.parentElement===route && map.parentElement===route && destination.nextElementSibling!==map){
        destination.after(map);
      }
    }
    // The V3 owner returns map to its original location in Tu reserva.
    // Observe quick-mode only, not child mutations (no self-triggering loop).
    new MutationObserver(arrangeRoute).observe(doc.body,{attributes:true,attributeFilter:["class"]});
    arrangeRoute();
    // Consolidate previous multi-box ticket into two visually distinct zones.
    const head=ticket.querySelector(".mf-v4-ticket-head");
    const hero=ticket.querySelector(".request-hero");
    const recap=ticket.querySelector("#urgent-route-recap");
    const itinerary=ticket.querySelector(".premium-confirm-route");
    const meta=ticket.querySelector(".mf-v4-ticket-meta");
    const action=ticket.querySelector(".mf-v4-ticket-action");
    const perforation=ticket.querySelector(".mf-v4-ticket-perforation");
    if(head&&hero&&recap&&itinerary&&meta&&action){
      const spotlight=doc.createElement("div");
      spotlight.className="mf-v5-spotlight";
      const content=doc.createElement("div");
      content.className="mf-v5-ticket-content";
      // Reparenting leaves the original IDs, server-bound values and listeners.
      ticket.prepend(spotlight);
      spotlight.append(head,hero,recap);
      content.append(itinerary,meta);
      action.before(content);
      perforation?.remove(); // V5 is a clean travel summary, not a faux paper ticket.
      const title=head.querySelector(".mf-v4-ticket-brand strong");
      if(title)title.textContent="Tu viaje, listo";
    }
    return true;
  }
  function init(){
    if(mount())return;
    let count=0;
    function tryMount(){if(mount())return;if(++count<40)root.setTimeout(tryMount,150);}
    tryMount();
  }
  if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",init,{once:true});
  else init();
})(window);
