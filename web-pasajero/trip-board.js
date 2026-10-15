"use strict";
/* Módulo puro de seguimiento: sin red, DOM, acceso a sesión ni geolocalización. */
(function(root){
  const terminal=new Set(["FINALIZADA","CANCELADA","RECHAZADA","RECHAZADA_CLIENTE"]);
  const moving=new Set(["EN_VIAJE","EN_CAMINO","EN_PUNTO"]);
  const accepted=new Set(["ACEPTADA","CONFIRMADA","ACEPTADA_CLIENTE"]);
  const months=["enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"];
  const days=["dom","lun","mar","mié","jue","vie","sáb"];
  function validDay(day){return /^\d{4}-\d{2}-\d{2}$/.test(String(day||""));}
  function localDate(day){
    if(!validDay(day))return null;
    const d=new Date(day+"T12:00:00Z");
    return Number.isNaN(d.getTime())?null:d;
  }
  // PWA v55: fecha operativa en Uruguay. El servidor conserva la autoridad
  // para aceptar y aplicar conflictos/buffers. Esta es SOLO una protección UI.
  const QUOTE_WINDOW_MS=90*60*1000;
  function pickupTimeMs(r){
    const day=String(r?.pickup_date||"");
    const clock=String(r?.pickup_time||"");
    if(!validDay(day)||!/^\d{2}:\d{2}/.test(clock))return null;
    const ms=Date.parse(day+"T"+clock.slice(0,5)+":00-03:00");
    return Number.isFinite(ms)?ms:null;
  }
  function isImmediateRequest(r){
    const mode=String(r?.pickup_mode||"");
    if(mode==="now"||(/^\\d{1,3}$/.test(mode)&&Number(mode)>=1&&Number(mode)<=120))return true;
    if(mode==="schedule")return false;
    // Legacy authorized v15 / guest v4 JSON omits pickup_mode. An urgent
    // pickup is timestamped by Supabase now or now + 10m; scheduled slots
    // are >=30m after creation. Never classify missing timestamps as urgent.
    const pickup=pickupTimeMs(r),created=Date.parse(String(r?.created_at||""));
    return pickup!==null&&Number.isFinite(created)&&
      pickup-created>=-120000&&pickup-created<=12*60000;
  }
  function expiredImmediate(r,at=Date.now()){
    if(r?.status!=="PENDIENTE"||!isImmediateRequest(r))return false;
    const created=Date.parse(String(r.created_at||""));
    return Number.isFinite(created)&&at-created>=QUOTE_WINDOW_MS;
  }
  function expiredQuote(r,at=Date.now()){
    if(r?.status!=="PENDIENTE"||r?.quote_status!=="ENVIADO")return false;
    const pickup=pickupTimeMs(r),mode=String(r.pickup_mode||"");
    if(expiredImmediate(r,at))return true; // v13 backend's strict 90-min deadline
    if(isImmediateRequest(r))return false;
    if(mode==="schedule"&&pickup!==null)return at>=pickup;
    // Unknown legacy booking: the original conservative behavior remains.
    return pickup!==null&&at-pickup>=QUOTE_WINDOW_MS;
  }
  function metadata(r){
    const status=String(r?.status||"PENDIENTE");
    const q=String(r?.quote_status||"SIN_PRESUPUESTO");
    const total=Number(r?.quote_final_total||0);
    const quotePending=q==="ENVIADO"&&total>0&&status==="PENDIENTE";
    const quoteExpired=quotePending&&expiredQuote(r);
    const urgentExpired=expiredImmediate(r);
    if(terminal.has(status)){
      if(status==="FINALIZADA")return {label:"Finalizada",tone:"complete",icon:"check-circle",rank:5,action:"Ver detalle"};
      if(status==="RECHAZADA_CLIENTE")return {label:"Presupuesto rechazado",tone:"muted",icon:"x-circle",rank:5,action:"Ver detalle"};
      return {label:status==="CANCELADA"?"Cancelada":"Rechazada",tone:"muted",icon:"x-circle",rank:5,action:"Ver detalle"};
    }
    if(quoteExpired)return {label:"Horario vencido",tone:"expired",icon:"clock-alert",rank:1,action:"Ver detalle"};
    if(urgentExpired)return {label:"Solicitud vencida",tone:"expired",icon:"clock-alert",rank:1,action:"Solicitar de nuevo"};
    if(status==="EN_VIAJE")return {label:"En viaje",tone:"moving",icon:"navigation",rank:4,action:"Seguir viaje"};
    // Fase informada explícitamente desde la APK Conductor y validada por PIN.
    if(accepted.has(status)&&r?.pickup_phase==="EN_CAMINO")
      return {label:"Conductor en camino",tone:"moving",icon:"car-front",rank:3,action:"Ver seguimiento"};
    if(accepted.has(status)&&r?.pickup_phase==="EN_PUNTO")
      return {label:"Conductor llegó",tone:"moving",icon:"map-pin-check",rank:3,action:"Ver detalle"};
    if(status==="EN_CAMINO")return {label:"Conductor en camino",tone:"moving",icon:"car-front",rank:3,action:"Ver llegada"};
    if(status==="EN_PUNTO")return {label:"Conductor llegó",tone:"moving",icon:"map-pin-check",rank:3,action:"Ver detalle"};
    if(accepted.has(status)||q==="ACEPTADO")return {label:"Confirmada",tone:"confirmed",icon:"check-circle-2",rank:2,action:"Ver detalle"};
    if(quotePending)return {label:"Presupuesto para responder",tone:"attention",icon:"wallet",rank:1,action:"Aceptar o rechazar"};
    if(q==="ENVIADO")return {label:"Presupuesto enviado",tone:"quote",icon:"receipt",rank:1,action:"Ver presupuesto"};
    return {label:"Pendiente",tone:"pending",icon:"clock-3",rank:0,action:"Ver detalle"};
  }
  // Mensaje único y contextual según la fase confirmada por el servidor.
  // Nunca inferir una llegada, GPS ni aceptación a partir de una estimación anterior.
  function guidance(r){
    const meta=metadata(r);
    if(meta.tone==="expired")return "La hora prevista ya pasó sin confirmación. Podés solicitar otro traslado; esta reserva no se cancela automáticamente.";
    if(meta.tone==="attention")return "Tenés un presupuesto para responder. Revisá el importe y elegí si lo aceptás.";
    if(meta.tone==="pending")return "Recibimos tu solicitud. El conductor la revisará.";
    if(meta.tone==="confirmed")return "Tu traslado está confirmado. Podés escribirle al conductor.";
    if(meta.tone==="moving"){
      if(r?.status==="EN_VIAJE")return "Tu traslado está en curso.";
      if(r?.pickup_phase==="EN_PUNTO"||r?.status==="EN_PUNTO")
        return "El conductor llegó al punto de recogida.";
      if(r?.pickup_phase==="EN_CAMINO"||r?.status==="EN_CAMINO")
        return "El conductor está en camino a tu origen.";
      return "El conductor actualizó el estado de tu traslado.";
    }
    if(meta.tone==="complete")return "El viaje terminó. Podés consultar sus detalles y mensajes.";
    if(meta.tone==="muted")return "Esta solicitud está cerrada. Sus datos y mensajes siguen disponibles.";
    return meta.label;
  }
  function isHistory(r){return terminal.has(r?.status)||expiredQuote(r)||expiredImmediate(r);}
  function dateLabel(day,groupBy){
    const d=localDate(day);
    if(!d)return "Sin fecha";
    if(groupBy==="month")return months[d.getUTCMonth()].replace(/^./,x=>x.toUpperCase())+" "+d.getUTCFullYear();
    return days[d.getUTCDay()]+" "+String(d.getUTCDate()).padStart(2,"0")+" de "+months[d.getUTCMonth()];
  }
  function compactDay(day){
    const d=localDate(day);
    if(!d)return "Fecha a confirmar";
    return days[d.getUTCDay()]+" "+d.getUTCDate()+" "+months[d.getUTCMonth()].slice(0,3);
  }
  function sortKey(r){return validDay(r.pickup_date)?r.pickup_date+"T"+String(r.pickup_time||"00:00").slice(0,5):"";}
  function matches(r,options){
    const meta=metadata(r);
    const scope=options?.scope||"upcoming";
    if(scope==="upcoming"&&isHistory(r))return false;
    if(scope==="history"&&!isHistory(r))return false;
    if(scope==="attention"&&!meta.tone.includes("attention"))return false;
    const f=options?.status||"all";
    if(f==="pending"&&meta.tone!=="pending")return false;
    if(f==="quote"&&!["attention","quote"].includes(meta.tone))return false;
    if(f==="confirmed"&&meta.tone!=="confirmed")return false;
    if(f==="moving"&&meta.tone!=="moving")return false;
    if(f==="finished"&&!isHistory(r))return false;
    return true;
  }
  function groupReservations(rows,options={}){
    const groupBy=options.groupBy==="month"?"month":"day";
    const filtered=(Array.isArray(rows)?rows:[]).filter(r=>matches(r,options));
    // Próximos: orden cronológico ascendente, urgencias primero; historial: descendente.
    const future=options.scope!=="history"&&options.scope!=="all";
    filtered.sort((a,b)=>future?sortKey(a).localeCompare(sortKey(b)):sortKey(b).localeCompare(sortKey(a)));
    const out=[], map=new Map();
    for(const r of filtered){
      const day=validDay(r.pickup_date)?r.pickup_date:"";
      const key=groupBy==="month"?day.slice(0,7):day;
      let group=map.get(key);
      if(!group){
        group={key,label:dateLabel(day,groupBy),items:[]};
        map.set(key,group);out.push(group);
      }
      group.items.push(r);
    }
    return out;
  }
  function signature(r){
    return [r.status||"",r.pickup_phase||"",r.pickup_phase_updated_at||"",r.quote_status||"",r.quote_sent_at||"",r.quote_final_total||"",
      r.last_driver_message_id||"",r.updated_at||""].map(x=>String(x)).join("|");
  }
  function notifications(previous,items){
    const changed=[];
    if(!previous||typeof previous.has!=="function")return changed;
    for(const r of items){
      const key=String(r.id||r.code||"");
      if(!key||!previous.has(key))continue; // first load is baseline
      const p=previous.get(key);
      if(p.signature===signature(r))continue;
      const m=metadata(r);
      // Nuevos mensajes: solo si ya existía una línea base conocida.
      if(r.last_driver_message_id&&p.lastDriverMessage!==r.last_driver_message_id)
        changed.push({kind:"message",row:r});
      // A fresh quote merits a foreground alert; no push or GPS inference.
      if(m.tone==="attention"&&p.quoteSent!==r.quote_sent_at)changed.push({kind:"quote",row:r});
      else if(["confirmed","moving","complete"].includes(m.tone)&&(p.status!==r.status||p.phase!==r.pickup_phase))
        changed.push({kind:"state",row:r});
    }
    return changed;
  }
  function snapshot(rows){
    return new Map((rows||[]).filter(r=>r.id||r.code).map(r=>[String(r.id||r.code),{
      status:r.status,phase:r.pickup_phase,quoteSent:r.quote_sent_at,lastDriverMessage:r.last_driver_message_id,signature:signature(r)
    }]));
  }
  root.TrasladosTripBoard=Object.freeze({
    metadata,guidance,isHistory,expiredQuote,expiredImmediate,isImmediateRequest,compactDay,groupReservations,notifications,snapshot,signature
  });
})(window);
