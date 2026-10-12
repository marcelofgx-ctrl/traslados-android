"use strict";
/* Existing requests are read only from customer-authenticated or guest-token-authorized trip rows.
 * No geolocation, PIN, database mutation, public reservation lookup or driver status inference.
 */
(function(root){
  const OPEN=new Set(["PENDIENTE","ACEPTADA"]);
  const norm=value=>String(value||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLocaleLowerCase("es-UY").replace(/\s+/g," ").trim();
  function match(rows,origin,destination,stops,now=Date.now()){
    if(!Array.isArray(rows)||!origin?.text||!destination?.text||stops?.length)return null;
    const a=norm(origin.text),b=norm(destination.text);
    const eligible=rows.filter(row=>{
      if(!OPEN.has(row?.status))return false;
      // Existing v15/v4 secure list RPCs omit pickup_mode. Server-created
      // urgent slots equal creation time or +10 minutes; scheduled bookings
      // must be >=30 min ahead and are never folded into an urgent request.
      const created=Date.parse(row.created_at||"");
      const pickupStamp=Date.parse(String(row.pickup_date||"")+"T"+
        String(row.pickup_time||"").slice(0,8)+"-03:00");
      const urgentFromServer=Number.isFinite(created)&&Number.isFinite(pickupStamp)&&
        pickupStamp-created>=-120000&&pickupStamp-created<=12*60000;
      if(row.pickup_mode==="schedule"||
        (!["now","10"].includes(row.pickup_mode)&&!urgentFromServer))return false;
      // Never collapse a genuinely different multi-stop booking into this route.
      const savedStops=row.stops;
      if(Array.isArray(savedStops)&&savedStops.length)return false;
      if(typeof savedStops==="string"&&savedStops.trim()&&savedStops.trim()!=="[]")return false;
      if(norm(row.origin_text)!==a||norm(row.destination_text)!==b)return false;
      const stamp=Date.parse(row.created_at||"");
      if(!Number.isFinite(stamp))return false;
      const age=now-stamp;
      return age>=-60000&&age<=("PENDIENTE"===row.status?90:360)*60000;
    });
    eligible.sort((x,y)=>Date.parse(y.created_at)-Date.parse(x.created_at));
    return eligible[0]||null;
  }
  function presentation(row){
    if(row.status==="ACEPTADA")return {
      title:"Tu traslado ya está confirmado",
      description:"Esta reserva ya tiene confirmación. Consultá las indicaciones y mensajes del conductor.",
      action:"Seguir mi traslado"
    };
    if(row.quote_status==="ENVIADO")return {
      title:"Tenés un presupuesto para responder",
      description:"El conductor respondió a tu solicitud. Revisá el importe y decidí desde Mis traslados.",
      action:"Revisar presupuesto"
    };
    return {
      title:"Tu solicitud está en revisión",
      description:"Ya recibimos este recorrido. Está pendiente de respuesta del conductor; no necesitás enviarlo otra vez.",
      action:"Seguir mi solicitud"
    };
  }
  root.TrasladosOwnedPickup=Object.freeze({match,presentation});
})(window);
