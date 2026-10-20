"use strict";
/* Approved 1/4–4/4, exactly as preserved in archived Map-First V5.
   All inputs, origin edits, GPS, route and submit IDs are the live ones.
   No preview-only disabled-booking click interception. */
(function(root){
  if(new URLSearchParams(root.location.search).get("ui")!=="v6")return;
  const doc=root.document;
  const styles=[
    "pruebas/map-first/map-first-review.css",
    "pruebas/map-first/map-first-v3.css",
    "pruebas/map-first/map-first-v4-confirm.css",
    "pruebas/map-first/map-first-visual-v5.css",
    "pruebas/map-first/map-first-v5-2.css",
    "tu-viaje-reference.css"
  ];
  for(const file of styles){
    const link=doc.createElement("link");
    link.rel="stylesheet";link.href="./"+file+(file==="tu-viaje-reference.css"?"?v=3":"?v=1");
    doc.head.appendChild(link);
  }
  function mount(){
    const view=doc.getElementById("view-viaje");
    const header=view?.querySelector(".premium-brand");
    // On first page load the route editor/map are still under
    // #reserve-field-host. app.js moves them into #view-viaje ONLY after
    // choosing the service. Requiring the map inside the journey at startup
    // aborted the skin entirely on Samsung (duplicate tabs, black map, old
    // header, giant GPS card). Keep the same LIVE map; never clone it.
    const liveMap=doc.querySelector(".premium-route-canvas");
    if(!view||!header||!liveMap)return false;
    if(doc.body.dataset.tuViajeReference==="approved-1-4")return true;
    doc.body.classList.add("tu-viaje-ref","map-first-review");
    doc.body.dataset.tuViajeReference="approved-1-4";
    header.innerHTML='<span class="premium-car" aria-hidden="true"><svg viewBox="0 0 64 64" fill="none"><path d="M13 38l5-12c1-3 4-5 7-5h15c4 0 6 2 8 5l4 12" stroke="currentColor" stroke-width="2.7" stroke-linecap="round"/><path d="M11 38h42v10H11z" stroke="currentColor" stroke-width="2.7" stroke-linejoin="round"/><path d="M16 48v5m32-5v5M18 41h7m14 0h7M22 28h20" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/></svg></span><span><small>TRASLADOS</small><strong>Tu viaje</strong></span>';
    view.querySelector(".qt-v6-contact-card")?.remove();
    for(const icon of doc.querySelectorAll(".qt-v6-tab-icon"))icon.remove();
    // "Tu viaje" is ONE service. Keep only Home and the passenger's real
    // trip history inside the immediate ride; scheduling belongs to Home.
    // Reuse the original navigational buttons and their existing listeners.
    const historyTab=doc.querySelector('.tabs .tab[data-view="viajes"]');
    if(historyTab){
      historyTab.textContent="Mis viajes";
      historyTab.setAttribute("aria-label","Mis viajes: solicitudes, aceptaciones e historial");
    }
    const receipt=doc.getElementById("booking-success");
    if(receipt){
      const syncReceipt=()=>{
        if(receipt.hidden || !view.classList.contains("active"))return;
        const status=receipt.querySelector(".success-lead");
        if(status)status.textContent="Solicitud enviada. Esperando respuesta del conductor: puede aceptar o rechazar el viaje.";
        const link=receipt.querySelector("button.primary");
        if(link&&link.textContent.trim()!=="VER MIS VIAJES")link.textContent="VER MIS VIAJES";
      };
      new MutationObserver(syncReceipt).observe(receipt,{childList:true,attributes:true,attributeFilter:["hidden"]});
      syncReceipt();
    }
    const presence=doc.querySelector("#view-viaje .premium-live-presence");
    const intro=view.querySelector(".premium-intro");
    function arrangeStage(){
      const stage=view.dataset.premiumStep||"route";
      const active=view.classList.contains("active");
      doc.body.classList.toggle("qt-functional-v6",!active);
      doc.body.classList.toggle("tu-viaje-immediate",active);
      if(!active)return;
      if(stage==="route"){
        // The real form is relocated after entering the service. Resolve its
        // current node each time instead of capturing an absent route card.
        const routeCard=view.querySelector('[data-booking-step="route"]');
        if(routeCard&&presence&&routeCard.nextElementSibling!==presence)routeCard.after(presence);
      }else if(presence&&intro){
        view.querySelector(".premium-journey-header")?.after(presence);
        presence.after(intro);
      }
      if(stage==="confirm"){
        // Instant pickup never asks the passenger for a tracking/booking code.
        // The server's technical identifier continues to exist for history,
        // idempotency and the driver's accept/reject workflow.
        const description=doc.getElementById("urgent-actions-description");
        if(description)description.textContent=
          "El conductor recibe tu solicitud y la acepta o rechaza. Seguís su respuesta en Mis viajes.";
      }
      if(intro){
        const heading=intro.querySelector("h2"),note=intro.querySelector("p");
        if(stage==="passenger"){
          if(heading)heading.textContent="Tus datos";
          if(note)note.textContent="Tus datos permiten coordinar el traslado.";
        }else if(stage==="when"){
          if(heading)heading.textContent="¿Cuándo querés viajar?";
          if(note)note.textContent="Ahora o en las próximas 2 horas.";
        }
      }
    }
    new MutationObserver(arrangeStage).observe(view,{attributes:true,attributeFilter:["class","data-premium-step"]});
    arrangeStage();
    return true;
  }
  function start(){
    if(mount())return;
    // Resilient to a deferred initialisation after DOMContentLoaded: finish
    // the ONE visual mount when the real route composer appears.
    const observer=new MutationObserver(()=>{
      if(mount())observer.disconnect();
    });
    observer.observe(doc.body,{childList:true,subtree:true});
  }
  if(doc.readyState==="complete")start();
  else doc.addEventListener("DOMContentLoaded",start,{once:true});
})(window);

/* Frozen preview composition, promoted to LIVE PWA. */
"use strict";
/* Map-First V3: opt-in presentation only. Moves EXISTING UI nodes, keeps their
 * listeners / values / RPC / GPS intact. Does not call APIs, fake maps or fares. */
(function(root){
  const doc=root.document;
  if(new URL(root.location.href).searchParams.get("ui")!=="v6")return;
  
  doc.body.classList.add("map-first-v3");
  function install(){
    const route=doc.querySelector('[data-booking-step="route"]');
    const routeBody=doc.getElementById("booking-step-route");
    const map=route?.querySelector(".premium-route-canvas");
    const stops=route?.querySelector(".premium-route-stops");
    const passenger=doc.querySelector('[data-booking-step="passenger"] [data-step-body]');
    const comment=doc.getElementById("comments")?.closest("label");
    const extras=passenger?.querySelector(".premium-contact-extras");
    if(!route||!routeBody||!map||!stops||!passenger||!extras||!comment)return false;
    if(doc.body.dataset.mapFirstV3Mounted==="1")return true;
    doc.body.dataset.mapFirstV3Mounted="1";
    const originalMap=doc.createComment("map-first-v3-map-origin");
    const originalStops=doc.createComment("map-first-v3-stops-origin");
    map.before(originalMap);stops.before(originalStops);
    function arrange(){
      const enabled=doc.body.classList.contains("journey-mode-quick");
      if(enabled){
        // DOM order is essential: previous CSS-only order was ineffective
        // because map and route fields had different parent elements.
        if(map.parentElement!==routeBody)routeBody.append(map);
        if(stops.parentElement!==routeBody)routeBody.append(stops);
      }else{
        if(map.parentNode!==originalMap.parentNode)originalMap.after(map);
        if(stops.parentNode!==originalStops.parentNode)originalStops.after(stops);
      }
    }
    const options=doc.createElement("details");
    options.className="mf-v3-optional";
    const label=doc.createElement("summary");
    label.textContent="Indicaciones, email y preferencias (opcional)";
    options.append(label);
    // Moving existing labels preserves original user-entered text/listeners.
    comment.before(options);
    options.append(comment,extras);
    const screen=doc.getElementById("view-viaje");
    const next=screen.querySelector(".premium-next");
    next?.addEventListener("click",()=>{
      if(screen.dataset.premiumStep!=="passenger")return;
      const email=doc.getElementById("premium-person-email");
      if(email?.value?.trim()&&!email.checkValidity())options.open=true;
    },true);
    // Prevent map/stops layout regression when switching Tu viaje / Tu reserva.
    const watch=new MutationObserver(arrange);
    watch.observe(doc.body,{attributes:true,attributeFilter:["class"]});
    arrange();
    return true;
  }
  function init(){
    if(install())return;
    let attempts=0;
    const tryUntilReady=()=>{
      if(install())return;
      if(++attempts<20)root.setTimeout(tryUntilReady,150);
    };
    tryUntilReady();
  }
  if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",()=>root.setTimeout(init,0),{once:true});
  else root.setTimeout(init,0);
})(window);

"use strict";
/* V4 CONFIRM TICKET. Distinct compositional redesign, not a new quote/booking engine.
 * Moves the ORIGINAL itinerary, server-calculated price/km/min, validation errors
 * and original confirmation control. Never clones data or intercepts booking APIs. */
(function(root){
  const doc=root.document;
  if(new URL(root.location.href).searchParams.get("ui")!=="v6")return;
  
  function mount(){
    const view=doc.getElementById("view-viaje");
    const host=doc.getElementById("urgent-actions");
    const hero=host?.querySelector(".request-hero");
    const route=host?.querySelector(".premium-confirm-route");
    const recap=host?.querySelector("#urgent-route-recap");
    const error=host?.querySelector("#urgent-send-error");
    const button=host?.querySelector("#urgent-send");
    const legal=host?.querySelector(".booking-final-note");
    const description=host?.querySelector("#urgent-actions-description");
    if(!view||!host||!hero||!route||!recap||!error||!button||!legal||!description)return false;
    if(host.classList.contains("mf-v4-confirm"))return true;
    host.classList.add("mf-v4-confirm");

    const ticket=doc.createElement("section");
    ticket.className="mf-v4-ticket";
    ticket.setAttribute("aria-label","Tu traslado listo para revisar");
    const masthead=doc.createElement("div");
    masthead.className="mf-v4-ticket-head";
    const brand=doc.createElement("div");
    brand.className="mf-v4-ticket-brand";
    brand.innerHTML='<span class="mf-v4-emblem" aria-hidden="true">↗</span><span><small>TRASLADOS · TU PRÓXIMO VIAJE</small><strong>Todo listo para solicitar</strong></span>';
    const state=doc.createElement("span");
    state.className="mf-v4-ticket-status";
    state.textContent="SIN ENVIAR";
    masthead.append(brand,state);

    const meta=doc.createElement("div");
    meta.className="mf-v4-ticket-meta";
    const when=doc.createElement("span");
    when.className="mf-v4-meta-when";
    when.setAttribute("aria-label","Horario elegido");
    const passengers=doc.createElement("span");
    passengers.className="mf-v4-meta-passengers";
    passengers.setAttribute("aria-label","Pasajeros");
    meta.append(when,passengers);

    const tear=doc.createElement("div");
    tear.className="mf-v4-ticket-perforation";
    tear.setAttribute("aria-hidden","true");

    const action=doc.createElement("div");
    action.className="mf-v4-ticket-action";
    const instructions=doc.createElement("details");
    instructions.className="mf-v4-ticket-info";
    const summary=doc.createElement("summary");
    summary.textContent="Qué ocurre después de solicitar";
    instructions.append(summary,description);
    const sendLabel=doc.createElement("p");
    sendLabel.className="mf-v4-ticket-action-label";
    sendLabel.textContent="Comprobá los datos y continuá";
    action.append(sendLabel,error,button,legal,instructions);

    host.prepend(ticket);
    ticket.append(masthead,hero,recap,tear,route,meta,action);
    // The recap retains its original IDs and selector targets so live updates
    // from app.js/premium-journey.js still go to the authoritative nodes.
    const descriptions={
      now:"Recogida ahora",10:"En 10 minutos",15:"En 15 minutos",
      20:"En 20 minutos",30:"En 30 minutos",
      45:"En 45 minutos",60:"En 1 hora",custom:"Hora elegida"
    };
    function refresh(){
      if(view.dataset.premiumStep!=="confirm")return;
      const selector=doc.getElementById("journey-when-select");
      const value=selector?.value||"now";
      const chosen=selector?.selectedOptions?.[0]?.textContent?.trim()||"";
      const picked=(doc.getElementById("pickup-time")?.value||"").trim();
      const text=descriptions[value]||chosen||"Horario por confirmar";
      when.textContent="◷ "+(value==="custom"&&picked?"A las "+picked:text);
      const pax=Number(doc.getElementById("passengers")?.value)||1;
      passengers.textContent="♧ "+pax+" "+(pax===1?"pasajero":"pasajeros");
    }
    doc.addEventListener("change",refresh,true);
    const observer=new MutationObserver(refresh);
    observer.observe(view,{attributes:true,attributeFilter:["data-premium-step"]});
    refresh();
    return true;
  }
  function boot(){
    if(mount())return;
    let attempts=0;
    const retry=()=>{if(mount())return;if(++attempts<40)root.setTimeout(retry,120)};
    retry();
  }
  if(doc.readyState==="loading")doc.addEventListener("DOMContentLoaded",boot,{once:true});
  else boot();
})(window);

"use strict";
/* Map-First Visual V5, phase 1: ONE existing map, ONE authoritative quote,
 * ONE confirmation button. Presentation only; no API, GPS, price or booking code. */
(function(root){
  const doc=root.document;
  if(new URL(root.location.href).searchParams.get("ui")!=="v6")return;
  
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
