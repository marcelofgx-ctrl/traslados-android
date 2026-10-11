"use strict";
/* Observabilidad local, no persistida: solo fases/códigos/latencia.
 * Nunca conservar GPS, reservas, teléfonos, claves, tokens ni IP. */
(function(root){
  const steps=new Set(["availability","eta"]);
  const outcomes=new Set(["available","unavailable","http_error","invalid_response","network_error",
    "success","occupied","not_available","route_unavailable","temporarily_unavailable",
    "rate_limited","login_required","invalid_origin","other"]);
  const events=[];
  function record(step,outcome,latencyMs){
    if(!steps.has(step))return;
    const safeOutcome=outcomes.has(outcome)?outcome:"other";
    const duration=Number.isFinite(latencyMs)?Math.round(Math.max(0,Math.min(60000,latencyMs))):null;
    const evt={step,outcome:safeOutcome,latencyMs:duration,at:new Date().toISOString()};
    events.push(evt);
    if(events.length>30)events.shift();
    // Una URL con ?diagnostico=1 activa logs locales sin datos personales.
    // No transmitir telemetría a servicios externos desde el navegador.
    try{
      if(root.location?.search&&new URLSearchParams(root.location.search).get("diagnostico")==="1")
        root.console?.info?.("[Traslados ETA]",evt);
    }catch{}
  }
  function summary(){
    return events.map(e=>({...e}));
  }
  root.TrasladosPickupHealth=Object.freeze({record,summary});
})(window);
