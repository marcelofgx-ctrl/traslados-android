"use strict";
/* Map-First V3: opt-in presentation only. Moves EXISTING UI nodes, keeps their
 * listeners / values / RPC / GPS intact. Does not call APIs, fake maps or fares. */
(function(root){
  const doc=root.document;
  if(new URL(root.location.href).searchParams.get("mapfirst")!=="1")return;
  if(!doc.querySelector('meta[name="traslados-preview"]'))return;
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
