"use strict";
/* Traslados PWA v70: diagnostic breadcrumb trail stored ONLY in this device.
 * 24-hour expiry, maximum 60 allowlisted events; no coordinates, reservation
 * codes, visitor IDs, phones, PIN, tokens, names, origin or destinations.
 * Never upload diagnostics or intercept request bodies / headers.
 */
(function(root){
  const KEY="traslados.passenger.pickup.diagnostics.v1";
  const MAX=60,TTL=24*60*60*1000;
  const steps=new Set(["availability","eta","card","retry"]);
  const outcomes=new Set(["available","unavailable","http_error","invalid_response","network_error",
    "success","occupied","not_available","route_unavailable","temporarily_unavailable",
    "rate_limited","login_required","invalid_origin","other","checking","routing","ready",
    "waiting","blocked","issue","origin","owned","idle","cooldown","started","stale"]);
  const phases=new Set(["checking","routing","ready","waiting","blocked","issue","origin","owned","idle"]);
  const modes=new Set(["guest","session","unknown"]);
  const events=[];
  function clean(raw){
    if(!raw || typeof raw!=="object")return null;
    if(!steps.has(raw.step)||!outcomes.has(raw.outcome))return null;
    const date=Date.parse(raw.at);
    if(!Number.isFinite(date)||date>Date.now()+60000||Date.now()-date>TTL)return null;
    const evt={step:raw.step,outcome:raw.outcome,
      latencyMs:Number.isFinite(raw.latencyMs)?Math.round(Math.max(0,Math.min(60000,raw.latencyMs))):null,
      at:new Date(date).toISOString()};
    if(phases.has(raw.phase))evt.phase=raw.phase;
    if(modes.has(raw.mode))evt.mode=raw.mode;
    return evt;
  }
  function persist(){
    try{
      if(!root.localStorage)return;
      root.localStorage.setItem(KEY,JSON.stringify(events.slice(-MAX)));
    }catch{} // private browsing / quota issues must never block reservations
  }
  function restore(){
    try{
      const items=JSON.parse(root.localStorage?.getItem(KEY)||"[]");
      if(Array.isArray(items)){
        for(const row of items.slice(-MAX)){
          const safe=clean(row);if(safe)events.push(safe);
        }
      }
    }catch{}
    persist(); // purge aged events and any unknown fields
  }
  function append(step,outcome,latencyMs,phase,mode){
    const evt=clean({step,outcome:outcomes.has(outcome)?outcome:"other",latencyMs,phase,mode,at:new Date().toISOString()});
    if(!evt)return;
    events.push(evt);
    while(events.length>MAX)events.shift();
    persist();
    try{
      if(root.location?.search&&new URLSearchParams(root.location.search).get("diagnostico")==="1")
        root.console?.info?.("[Traslados ETA]",evt);
    }catch{}
  }
  function record(step,outcome,latencyMs){
    append(step,outcome,latencyMs);
  }
  function recordContext(step,outcome,phase,mode,latencyMs){
    append(step,outcome,latencyMs,phase,mode);
  }
  function summary(){return events.map(e=>({...e}));}
  function exportSafe(){
    return JSON.stringify({product:"Traslados Pasajero",version:"PWA v70",
      scope:"Diagnóstico técnico local de disponibilidad y ETA; sin datos personales",
      generatedAt:new Date().toISOString(),events:summary()},null,2);
  }
  function clear(){
    events.length=0;
    try{root.localStorage?.removeItem(KEY);}catch{}
  }
  restore();
  root.TrasladosPickupHealth=Object.freeze({record,recordContext,summary,exportSafe,clear});
})(window);
