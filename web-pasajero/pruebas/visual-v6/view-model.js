"use strict";
/* V6 G3 — one presentation model, no business calculations or network.
 * Snapshot shape (from future real adapter): {
 *   route:{key,originKey,origin:{label},destination:{label},stops:[{label,ready}],
 *     quote:{status,routeKey,distanceKm,durationMin,referenceFareUyu}},
 *   pickup:{status,originKey,distanceKm,etaMin},
 *   customer:{displayName,phoneLabel}, schedule:{label}
 * }
 * A verified quote MUST match the current route key; pickup MUST match origin.
 * This prevents old prices/distances/ETAs bleeding into a new selection. */
(function(root){
  const safeLabel=(value,fallback="Pendiente")=>{
    if(typeof value!=="string")return fallback;
    const label=value.trim().replace(/\s+/g," ");
    return label?label.slice(0,160):fallback;
  };
  const number=(v)=>typeof v==="number"&&Number.isFinite(v)?v:null;
  const fmt=(value,digits=1)=>value.toLocaleString("es-UY",{maximumFractionDigits:digits});
  const state=(snapshot)=>{
    const route=snapshot?.route||{};
    const q=route.quote||{};
    const stops=Array.isArray(route.stops)?route.stops.slice(0,12):[];
    const complete=!!(
      typeof route.key==="string"&&route.key.length>0&&
      typeof route.originKey==="string"&&route.originKey.length>0&&
      safeLabel(route.origin?.label,"")&&safeLabel(route.destination?.label,"")&&
      stops.every(s=>s?.ready===true&&safeLabel(s.label,""))
    );
    const verified=complete&&q.status==="verified"&&q.routeKey===route.key&&
      number(q.distanceKm)>0&&number(q.durationMin)>=0;
    const fare=verified&&number(q.referenceFareUyu)>0;
    const pickup=snapshot?.pickup||{};
    const pickupValid=complete&&pickup.status==="verified"&&pickup.originKey===route.originKey&&
      number(pickup.distanceKm)>=0&&number(pickup.etaMin)>=0;
    const display={
      origin:safeLabel(route.origin?.label,"Elegí el origen"),
      destination:safeLabel(route.destination?.label,"Elegí el destino"),
      price:fare?"$ "+fmt(q.referenceFareUyu,0):"A confirmar",
      distance:verified?fmt(q.distanceKm)+" km":"—",
      duration:verified?Math.round(q.durationMin)+" min":"—",
      pickupTitle:pickupValid?"Conductor disponible":"Recogida por confirmar",
      pickupEta:pickupValid?"~ "+Math.max(1,Math.ceil(pickup.etaMin))+" min":"Sin estimación",
      pickupDetail:pickupValid?fmt(pickup.distanceKm)+" km al origen":"Hasta tu origen · sin estimación verificada",
      customerName:safeLabel(snapshot?.customer?.displayName,"Datos pendientes"),
      customerPhone:safeLabel(snapshot?.customer?.phoneLabel,"Teléfono pendiente"),
      when:safeLabel(snapshot?.schedule?.label,"Ahora"),
      stops:stops.map(s=>safeLabel(s?.label,"Parada pendiente")),
      routeVerified:verified,
      pickupVerified:pickupValid
    };
    return Object.freeze(display);
  };
  const demoFixture=()=>({
    route:{
      key:"sample:airport-wtc",originKey:"sample:airport",
      origin:{label:"Aeropuerto de Carrasco"},destination:{label:"World Trade Center"},
      stops:[],quote:{status:"verified",routeKey:"sample:airport-wtc",distanceKm:16.5,durationMin:25,referenceFareUyu:660}
    },
    pickup:{status:"verified",originKey:"sample:airport",distanceKm:0.5,etaMin:5},
    customer:{displayName:"Pasajero de ejemplo",phoneLabel:"09X XXX XXX · Datos ilustrativos"},
    schedule:{label:"Ahora"}
  });
  function render(snapshot,doc=root.document){
    const value=state(snapshot);
    const fields={
      "route-origin":value.origin,"route-destination":value.destination,
      "trip-price":value.price,"trip-distance":value.distance,"trip-duration":value.duration,
      "pickup-title":value.pickupTitle,"pickup-eta":value.pickupEta,"pickup-detail":value.pickupDetail,
      "customer-name":value.customerName,"customer-phone":value.customerPhone,"selected-time":value.when
    };
    for(const [name,txt] of Object.entries(fields)){
      for(const node of doc.querySelectorAll('[data-v6-field="'+name+'"]'))node.textContent=txt;
    }
    // Pending quote / pickup are visually explicit; nothing here silently
    // copies previous trip values from any other screen.
    const quote=doc.getElementById("quote-status");
    if(quote)quote.textContent=value.routeVerified?"Recorrido estimado · dato ilustrativo":"Importe pendiente de ruta verificada";
    const pickup=doc.querySelectorAll('[data-v6-pickup-state]');
    for(const n of pickup)n.dataset.v6PickupState=value.pickupVerified?"available":"pending";
    return value;
  }
  root.TrasladosV6Model=Object.freeze({state,render,demoFixture});
})(typeof window==="undefined"?globalThis:window);
