"use strict";
/* V4 CONFIRM TICKET. Distinct compositional redesign, not a new quote/booking engine.
 * Moves the ORIGINAL itinerary, server-calculated price/km/min, validation errors
 * and original confirmation control. Never clones data or intercepts booking APIs. */
(function(root){
  const doc=root.document;
  if(new URL(root.location.href).searchParams.get("mapfirst")!=="1")return;
  if(!doc.querySelector('meta[name="traslados-preview"]'))return;
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
