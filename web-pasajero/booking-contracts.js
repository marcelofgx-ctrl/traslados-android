"use strict";
/* Contratos reutilizables de reservas de pasajeros.
 * Funciones puras; sin DOM, PIN, geolocalización, fetch ni persistencia.
 * Los nombres RPC y su semántica no cambian.
 * Paquetería será un dominio diferente: no reutilizar contratos de pasajeros
 * para describir bultos ni crear envíos por accidente.
 */
(function(root){
  function checkAvailability(sessionToken,p){
    return {
      p_session_token:sessionToken,p_pickup_date:p.date,p_pickup_time:p.time,
      p_trip_duration_min:null,p_origin_lat:p.origin.lat,p_origin_lng:p.origin.lng,
      p_destination_lat:p.destination.lat,p_destination_lng:p.destination.lng
    };
  }
  function registeredReservation(sessionToken,p){
    return {
      p_session_token:sessionToken,p_pickup_date:p.date,p_pickup_time:p.time,
      p_passengers:p.passengers,p_comments:p.comments,
      p_origin_text:p.origin.text,p_origin_lat:p.origin.lat,p_origin_lng:p.origin.lng,
      p_origin_department:p.origin.department,
      p_destination_text:p.destination.text,p_destination_lat:p.destination.lat,
      p_destination_lng:p.destination.lng,p_destination_department:p.destination.department,
      p_stops:p.stops.map((x,i)=>({
        position:i+1,address_text:x.text,lat:x.lat,lng:x.lng,department:x.department
      })),
      p_passenger_name:null,p_passenger_phone:null
    };
  }
  function guestReservation(p){
    // El backend invitado no soporta paradas: la UI ya exige una cuenta
    // antes de permitirlas. No inventamos ese parámetro en la RPC.
    return {
      p_customer_name:p.name,p_customer_phone:p.phone,
      p_pickup_date:p.date,p_pickup_time:p.time,
      p_passengers:p.passengers,p_comments:p.comments,
      p_origin_text:p.origin.text,p_origin_lat:p.origin.lat,p_origin_lng:p.origin.lng,
      p_destination_text:p.destination.text,p_destination_lat:p.destination.lat,
      p_destination_lng:p.destination.lng
    };
  }
  function immediatePickup(sessionToken,p,requestId){
    if(p.pickupMode!=="now"&&(!/^\d{1,3}$/.test(String(p.pickupMode))||Number(p.pickupMode)<1||Number(p.pickupMode)>120))throw new Error("Modalidad inmediata inválida");
    return {
      p_request_id:requestId,
      p_mode:p.pickupMode,
      p_session_token:sessionToken||null,
      p_customer_name:sessionToken?null:p.name,
      p_customer_phone:sessionToken?null:p.phone,
      p_passengers:p.passengers,
      p_comments:p.comments,
      p_origin_text:p.origin.text,
      p_origin_lat:p.origin.lat,
      p_origin_lng:p.origin.lng,
      p_origin_department:p.origin.department||null,
      p_destination_text:p.destination.text,
      p_destination_lat:p.destination.lat,
      p_destination_lng:p.destination.lng,
      p_destination_department:p.destination.department||null,
      p_stops:p.stops.map((x,i)=>({
        position:i+1,address_text:x.text,lat:x.lat,lng:x.lng,department:x.department
      }))
    };
  }
  function fixedFarePickup(sessionToken,p,requestId,road){
    if(!road||!Number.isFinite(road.distanceKm)||!Number.isInteger(road.durationMin)||
       !Number.isFinite(road.referenceFareUyu)||!Number.isFinite(road.referenceRateUyuPerKm)||
       !["openrouteservice","supabase_route_cache"].includes(road.source)||
       road.distanceKm<=0||road.durationMin<1||road.referenceFareUyu<=0||road.referenceRateUyuPerKm<=0)
      throw new Error("Calculá una ruta y un precio verificados antes de solicitar el viaje.");
    const routeKm=Math.round(road.distanceKm*10)/10;
    const exact=Math.round((routeKm*road.referenceRateUyuPerKm+Number.EPSILON)*100)/100;
    if(Math.abs(exact-road.referenceFareUyu)>0.009)
      throw new Error("El precio cambió. Volvé a calcular el recorrido.");
    return {
      ...immediatePickup(sessionToken,p,requestId),
      p_route_distance_km:routeKm,
      p_route_duration_min:road.durationMin,
      p_expected_rate:road.referenceRateUyuPerKm,
      p_expected_total:exact,
      p_route_source:road.source
    };
  }
  root.TrasladosBookingContracts=Object.freeze({
    checkAvailability,registeredReservation,guestReservation,immediatePickup,fixedFarePickup
  });
})(window);
