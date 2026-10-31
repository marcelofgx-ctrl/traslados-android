"use strict";
/* P1.5 QA-only presentation of the ORIGINAL pickup controller.
 * This module does not request GPS, send bookings, fetch an ETA, or bypass quotas.
 * The native card remains the single source of availability and ETA truth. */
(function(win,doc){
  const explanations=Object.freeze({
    route_unavailable:"No se verificó una ruta vial de recogida.",
    rate_limited:"El servicio indicó que hay que esperar para reintentar.",
    network_error:"La consulta no entregó una respuesta válida.",
    login_required:"La sesión no permitió calcular la llegada.",
    invalid_origin:"El origen no pudo validarse.",
    temporarily_unavailable:"El servicio no devolvió una llegada verificable.",
    http_error:"El servidor rechazó la consulta.",
    other:"No se recibió una ETA válida."
  });
  const $=id=>doc.getElementById(id);
  let card=null,title=null,detail=null;
  function sync(){
    if(!card)return;
    const native=$("pickup-presence");
    if(!native)return;
    const phase=native.dataset.phase||"";
    const code=native.dataset.etaFailureCode||"";
    const visible=doc.body.classList.contains("journey-mode-quick") &&
      $("view-viaje")?.classList.contains("active") &&
      $("booking-success")?.hidden!==false &&
      !native.hidden && ["checking","routing","waiting","issue"].includes(phase);
    card.hidden=!visible;
    if(!visible)return;
    card.dataset.phase=phase;
    const heading=phase==="checking"?"Consultando disponibilidad":
      phase==="routing"?"Calculando llegada del conductor":
      phase==="waiting"?"Esperando próxima comprobación":
      code?"Llegada aún sin estimar":"Disponibilidad por verificar";
    if(title.textContent!==heading)title.textContent=heading;
    if(phase==="issue"&&code){
      const allowedAt=Number(native.dataset.etaRetryEligibleAt);
      const seconds=Number.isFinite(allowedAt)&&allowedAt>Date.now()?
        Math.max(1,Math.ceil((allowedAt-Date.now())/1000)):0;
      detail.textContent=(explanations[code]||explanations.other)+" "+
        (seconds?"Nuevo intento permitido en "+seconds+" s.":
          "Esperando el siguiente control automático.");
    }else if(phase==="checking"){
      detail.textContent="Consultando estado público; no significa una recogida confirmada.";
    }else if(phase==="routing"){
      detail.textContent="Comprobando kilómetros y minutos reales hasta tu origen.";
    }else if(phase==="waiting"){
      detail.textContent="Disponibilidad cada 15 s. Todavía no hay una nueva ETA.";
    }else{
      detail.textContent="La disponibilidad se volverá a comprobar automáticamente.";
    }
  }
  function install(){
    if(card)return;
    const host=doc.querySelector(".qtc-app .qtc-driver"),native=$("pickup-presence");
    if(!host||!native)return;
    card=doc.createElement("div");card.id="qtc-p15-cycle";card.hidden=true;
    card.setAttribute("aria-label","Actividad de búsqueda y llegada del conductor");
    const marker=doc.createElement("span");marker.className="qtc-p15-cycle-mark";
    marker.setAttribute("aria-hidden","true");
    const copy=doc.createElement("span");copy.className="qtc-p15-cycle-copy";
    title=doc.createElement("strong");title.id="qtc-p15-cycle-title";
    detail=doc.createElement("small");detail.id="qtc-p15-cycle-detail";
    detail.setAttribute("aria-live","off"); // avoid one accessibility announcement per second
    copy.append(title,detail);card.append(marker,copy);host.append(card);
    new MutationObserver(sync).observe(native,{attributes:true,
      attributeFilter:["data-phase","data-presence","data-eta-failure-code",
        "data-eta-retry-eligible-at","hidden"]});
    new MutationObserver(sync).observe(doc.body,{attributes:true,attributeFilter:["class"]});
    new MutationObserver(sync).observe($("view-viaje"),{attributes:true,attributeFilter:["class"]});
    // Only update the visual clock; no calls, polling requests or extra ETA.
    win.setInterval(()=>{
      if(doc.visibilityState==="visible"&&!card.hidden)sync();
    },1000);
    sync();
  }
  if(doc.readyState==="complete")install();
  else win.addEventListener("load",install,{once:true});
})(window,document);
