"use strict";

/* Traslados con Reserva — web + installable PWA; same live backend as native Cliente/Conductor. */
const API="https://zetaudvvutlouiqxopvg.supabase.co";
const KEY="sb_publishable_HnbMZW2dKpm6mBq-y5qkaA_Jlfx4BB9";
const SESSION_KEY="traslados.passenger.session.v1";
const GUEST_KEY="traslados.passenger.guest-trips.v1";
const DEPTS=["Montevideo","Canelones","Artigas","Cerro Largo","Colonia","Durazno","Flores","Florida","Lavalleja","Maldonado","Paysandú","Río Negro","Rivera","Rocha","Salto","San José","Soriano","Tacuarembó","Treinta y Tres"];
const states={PENDIENTE:"Pendiente",PRESUPUESTO_ENVIADO:"Presupuesto enviado",ACEPTADA:"Aceptada",ACEPTADA_CLIENTE:"Aceptada por pasajero",CONFIRMADA:"Confirmada",EN_VIAJE:"En viaje",FINALIZADA:"Finalizada",CANCELADA:"Cancelada",RECHAZADA:"Rechazada",RECHAZADA_CLIENTE:"Rechazada por pasajero"};
const $=(id)=>document.getElementById(id);
const record={origin:null,destination:null,stops:[]};
let bookingSteps=null,routeStepAutoAdvanced=false;
// Las cabeceras del formulario son presentación, nunca estado de reserva.
function stepSummaryName(place){
  const label=String(place?.text||"").trim().split(",")[0];
  return label.length>37?label.slice(0,34)+"…":label;
}
// A condensed itinerary must preserve both full addresses, not truncate them.
function refreshJourneyComposition(routeComplete,roadVerified){
  const preview=$("route-folded-preview");
  if(preview){
    preview.hidden=!routeComplete;
    if(routeComplete){
      $("route-folded-origin").textContent=record.origin.text;
      $("route-folded-destination").textContent=record.destination.text;
      const stopBadge=$("route-folded-stops");
      stopBadge.hidden=record.stops.length===0;
      stopBadge.textContent=record.stops.length+" parada"+(record.stops.length===1?"":"s")+" intermedia"+(record.stops.length===1?"":"s");
      $("route-folded-km").textContent=roadVerified
        ?latestRoad.data.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km":"—";
      $("route-folded-minutes").textContent=roadVerified?latestRoad.data.durationMin+" min":"—";
      $("route-folded-fare").textContent=roadVerified&&Number.isFinite(latestRoad.data.referenceFareUyu)&&latestRoad.data.referenceFareUyu>0
        ?"$ "+latestRoad.data.referenceFareUyu.toLocaleString("es-UY",{maximumFractionDigits:0})
        :"A confirmar";
    }
  }
  for(const name of ["urgent","scheduled"]){
    const recap=$(name+"-route-recap");
    if(!recap)continue;
    recap.hidden=!routeComplete;
    if(!routeComplete)continue;
    recap.querySelector("[data-final-route-distance]").textContent=roadVerified
      ?latestRoad.data.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km":"—";
    recap.querySelector("[data-final-route-duration]").textContent=roadVerified
      ?latestRoad.data.durationMin+" min":"—";
    recap.querySelector("[data-final-route-fare]").textContent=roadVerified&&
      Number.isFinite(latestRoad.data.referenceFareUyu)&&latestRoad.data.referenceFareUyu>0
      ?"$ "+latestRoad.data.referenceFareUyu.toLocaleString("es-UY",{maximumFractionDigits:0})
      :"A confirmar";
  }
}
function refreshBookingSteps(){
  if(!bookingSteps)return;
  const routeComplete=Boolean(record.origin&&record.destination&&record.stops.every(Boolean));
  let routeText=routeComplete?stepSummaryName(record.origin)+" → "+stepSummaryName(record.destination)
    :!record.origin?"Elegí origen y destino"
    :!record.destination?"Falta elegir destino":"Falta completar una parada";
  let routeMetrics="";
  const verified=Boolean(routeComplete&&
    latestRoad?.key===routeKeyOf([record.origin,...record.stops,record.destination])&&
    window.TrasladosRoutes.verified(latestRoad.data));
  refreshJourneyComposition(routeComplete,verified);
  if(verified){
    const d=latestRoad.data;
    routeMetrics=d.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km · "+d.durationMin+" min";
    if(Number.isFinite(d.referenceFareUyu)&&d.referenceFareUyu>0)
      routeMetrics+=" · $ "+d.referenceFareUyu.toLocaleString("es-UY",{maximumFractionDigits:0});
  }
  bookingSteps.summary("route",routeText,routeComplete,routeMetrics);
  const p=Number($("passengers")?.value||1);
  const passengerText=p===1?"1 pasajero":p+" pasajeros";
  const d=$("pickup-date")?.value||"",t=$("pickup-time")?.value||"";
  const scheduled=selectedPickupMode==="schedule";
  let label=scheduled?(d&&t?d.slice(8,10)+"/"+d.slice(5,7)+" · "+t+" · "+passengerText:"Elegí fecha y hora"):
    (selectedPickupMode==="now"?"Ahora":"En "+selectedPickupMode+" min")+" · "+passengerText;
  const validSchedule=Boolean(d&&t&&Date.parse(d+"T"+t+":00-03:00")>=Date.now()-20000);
  bookingSteps.summary("when",label,p>=1&&p<=8&&(!scheduled||validSchedule));
  const name=authReady()?session.customer?.full_name:$("person-name")?.value;
  const phone=authReady()?session.customer?.phone:$("person-phone")?.value;
  const ready=Boolean(name?.trim().length>=2&&String(phone||"").replace(/\D/g,"").length>=8);
  bookingSteps.summary("passenger",ready?"Datos de contacto completos":authReady()?"Completá tus datos de cuenta":"Nombre y teléfono",ready);
}
function suggestBookingStep(error){
  if(!bookingSteps)return;
  const message=String(error?.message||"");
  let step="passenger",target=authReady()?null:$("person-name");
  if(/origen|destino|direcci|paradas|punto/i.test(message)){
    step="route";target=record.origin?(record.destination?null:destPicker?.input):originPicker?.input;
  }else if(/fecha|hora|horario|pasado/i.test(message)){
    step="when";target=$("pickup-date");
  }else if(!authReady()){
    const name=$("person-name"),phone=$("person-phone");
    target=name?.value?.trim().length>=2?phone:name;
  }
  if(step==="when"){
    const inline=$("booking-when-error");
    inline.textContent=/pasado/i.test(message)
      ?"El horario elegido ya pasó. Seleccioná otro horario futuro y volvé a revisar la solicitud."
      :"Revisá la fecha y la hora. La solicitud no se envió.";
    inline.hidden=false;
  }
  bookingSteps.open(step,{scroll:true});
  // Esperar al cambio de visibilidad antes de enfocar un campo plegado.
  if(target)setTimeout(()=>target.focus?.({preventScroll:true}),60);
}
const depts={origin:"Canelones",destination:"Montevideo"}; // Preferencia de orden; nunca filtra Uruguay
let session=null,registerMode=false,installEvent=null,pendingReview=null,busy=false,toastHandle=null;
let expiredPassengerSessionNotice=false;
let publicVehiclePlate="";
async function refreshPublicVehiclePlate(){
  try{
    const data=await rpc("driver_public_vehicle_v1",{});
    const plate=String(data?.plate||"").trim().toUpperCase();
    if(!/^[A-Z0-9]{2,4}-[A-Z0-9]{3,5}$/.test(plate))return;
    if(publicVehiclePlate!==plate){
      publicVehiclePlate=plate;
      if(tripLoaded)renderTripsBoard();
    }
  }catch{ /* Identity card is hidden if the public endpoint is unavailable. */ }
}

let urgentAttempt=null;

function jsonStorage(key,defaultValue){try{return JSON.parse(localStorage.getItem(key)||"null")??defaultValue;}catch{return defaultValue;}}
function saveStorage(key,v){try{localStorage.setItem(key,JSON.stringify(v));}catch{}}
function text(parent,tag,contents,cls){const e=document.createElement(tag);if(cls)e.className=cls;if(contents!==undefined)e.textContent=String(contents);parent.appendChild(e);return e;}
function button(parent,contents,cls,fn){const b=text(parent,"button",contents,cls);b.type="button";if(fn)b.addEventListener("click",fn);return b;}
function clear(n){n.replaceChildren();}
function tell(message){const el=$("toast");clearTimeout(toastHandle);el.textContent=message;el.hidden=false;toastHandle=setTimeout(()=>el.hidden=true,5500);}
function showError(e,fallback="No se pudo completar la acción"){
  let msg=e instanceof Error?e.message:fallback;
  if(/gen_random_bytes|function .*does not exist|SQLSTATE|permission denied for schema/i.test(msg))
    msg="No pudimos registrar la solicitud por un problema técnico. Revisá Mis traslados antes de volver a enviarla.";
  else if(/HORARIO_YA_NO_DISPONIBLE|HORARIO_NO_DISPONIBLE/i.test(msg))
    msg="Ese horario ya no está disponible. Elegí una nueva fecha y hora para solicitar el traslado.";
  else if(/La solicitud inmediata venci[oó]/i.test(msg))
    msg="Esta solicitud inmediata venció. Para viajar, enviá una nueva solicitud desde Reservar.";
  else if(/No hay un presupuesto pendiente|presupuesto ya recibi[oó] una respuesta/i.test(msg))
    msg="Este presupuesto ya no admite esa respuesta. Actualizá Mis traslados para ver su estado.";
  else if(/^[A-Z][A-Z0-9_]{5,}$/.test(msg))
    msg="No pudimos completar la operación. Actualizá Mis traslados antes de volver a intentar.";
  tell(msg);
}
function loading(el,value){
  if(!el)return;
  el.disabled=value;el.setAttribute("aria-busy",String(Boolean(value)));
  const label=el.querySelector?.(".booking-cta-label");
  if(label){
    label.dataset.original??=label.textContent;
    label.textContent=value?"Enviando solicitud…":label.dataset.original;
    return;
  }
  el.dataset.original??=el.textContent;
  el.textContent=value?"Procesando…":el.dataset.original;
}
function clearBookingFeedback(){
  for(const id of ["booking-send-error","booking-when-error","booking-fix-when","urgent-send-error"]){
    const el=$(id);if(el)el.hidden=true;
  }
}
function bookingSendFailure(error){
  const message=String(error?.message||"");
  const scheduleIssue=/horario|fecha|hora|disponible|pasado/i.test(message);
  const notice=$("booking-send-error");
  if(notice){
    notice.textContent=scheduleIssue
      ?"El horario no pudo confirmarse. La solicitud no se registró; elegí otra fecha u hora para continuar."
      :"No pudimos confirmar el resultado del envío. Revisá «Mis traslados» antes de intentarlo nuevamente.";
    notice.hidden=false;
  }
  $("booking-fix-when").hidden=!scheduleIssue;
  showError(error);
}
function guardConnection(){if(!navigator.onLine)throw new Error("Estás sin Internet; no enviamos reservas offline.");}
async function rpc(name,payload={}){
  guardConnection();
  return window.TrasladosRpc.call({api:API,publishableKey:KEY,name,payload});
}
function updateConnectivity(){const offline=!navigator.onLine;$("offline-band").hidden=!offline;$("confirm-booking").disabled=offline;$("urgent-send").disabled=offline;if(offline)tell("Sin conexión: no podés enviar solicitudes hasta reconectarte.");}
function currentUY(offsetMinutes=0){
  const dt=new Date(Date.now()+offsetMinutes*60000);
  const parts=Object.fromEntries(new Intl.DateTimeFormat("en-US",{timeZone:"America/Montevideo",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(dt).filter(x=>x.type!=="literal").map(x=>[x.type,x.value]));
  return{day:`${parts.year}-${parts.month}-${parts.day}`,time:`${parts.hour}:${parts.minute}`};
}
let selectedPickupMode="schedule";
let pickupEtaTimer=null,pickupEtaRequestSeq=0,lastPickupEtaKey="",lastPickupEtaAt=0,lastPickupEtaMessage="";
let lastPickupEtaIsEstimate=false,lastVerifiedEtaKey="",lastVerifiedEtaAt=0,lastVerifiedEtaMessage="";
let lastVerifiedEtaResult=null;
let etaRetryNotBefore=0,etaRetryIdentity="";
let pickupEtaPendingKey="",lastEtaFailureKey="",lastEtaFailureMessage="";
let publicPresenceCache=null,publicPresenceAt=0,publicPresencePending=null;
function pickupIdentityKey(){
  if(selectedPickupMode==="schedule"||!record.origin)return "";
  const {lat,lng}=record.origin;
  if(!Number.isFinite(lat)||!Number.isFinite(lng))return "";
  return (authReady()?session.token:"visitante")+"|"+lat.toFixed(5)+","+lng.toFixed(5);
}
function etaResultStillApplies(key){
  // An availability refresh must not invalidate an ETA response for the same
  // origin and session; switching account, origin or view still must.
  return pickupIdentityKey()===key&&lastPickupEtaKey===key&&
    publicPresenceCache?.available===true;
}
function forgetPickupEta(){
  lastPickupEtaKey="";lastPickupEtaAt=0;lastPickupEtaMessage="";
  lastPickupEtaIsEstimate=false;lastVerifiedEtaKey="";lastVerifiedEtaAt=0;lastVerifiedEtaMessage="";
  lastVerifiedEtaResult=null;
  lastEtaFailureKey="";lastEtaFailureMessage="";
  // El enfriamiento de cuota pertenece al visitante, no al origen.
}
/** Una tarjeta con jerarquía visual: disponibilidad, km, minutos, información secundaria.
 * El lector de pantalla recibe un único resumen; nunca se muestra información
 * del servidor ni coordenadas del conductor.
 */
function paintPickupCard({presence,title,subtitle="",km=null,minutes=null,estimateNote="",checked="",phase="idle"}){
  const host=$("pickup-presence");
  if(host){host.dataset.presence=presence;host.dataset.phase=phase;}
  // Only transitions enter the persisted, allowlisted diagnostics. No text,
  // coordinates, URL, tokens, names, or other passenger content is recorded.
  const sig=presence+"|"+phase+"|"+(Number.isFinite(km)&&Number.isFinite(minutes)?"eta":"no_eta");
  if(sig!==paintPickupCard.lastSignature){
    paintPickupCard.lastSignature=sig;
    window.TrasladosPickupHealth?.recordContext?.("card",phase,phase,
      typeof authReady==="function"&&authReady()?"session":"guest");
  }
/* Recorrido decorativo sin leyenda redundante; la información operativa
   * sigue en el título, el subtítulo y las métricas verificadas. */
  const main=$("pickup-presence-title");
  const sub=$("pickup-presence-subtitle");
  const metrics=$("pickup-presence-metrics");
  const distance=$("pickup-distance");
  const eta=$("pickup-minutes");
  const note=$("pickup-estimate-note");
  const meta=$("pickup-presence-meta");
  const status=$("pickup-presence-status");
  if(main)main.textContent=title;
  if(sub)sub.textContent=subtitle;
  const hasEstimate=Number.isFinite(km)&&km>=0&&Number.isFinite(minutes)&&minutes>=0;
  if(metrics)metrics.hidden=!hasEstimate;
  if(distance)distance.textContent=hasEstimate?km.toLocaleString("es-UY",{maximumFractionDigits:1})+" km":"";
  if(eta)eta.textContent=hasEstimate?window.TrasladosPickupCadence.arrivalLabel(minutes):"";
  if(note){note.textContent=estimateNote;note.hidden=!estimateNote;}
  if(meta)meta.textContent=checked;
  if(status){
    const announcement=[title,subtitle,hasEstimate?km.toLocaleString("es-UY",{maximumFractionDigits:1})+
      " km · "+window.TrasladosPickupCadence.arrivalLabel(minutes)+" aproximados":"",estimateNote,checked].filter(Boolean).join(" · ");
    if(status.textContent!==announcement)status.textContent=announcement;
  }
}
function latestPickupEstimate(key){
  return lastVerifiedEtaResult&&lastVerifiedEtaKey===key&&Date.now()-lastVerifiedEtaAt<=300000
    ?lastVerifiedEtaResult:null;
}
function elapsedShort(ms){
  const seconds=Math.max(0,Math.round(ms/1000));
  return seconds<60?"hace "+seconds+" s":"hace "+Math.floor(seconds/60)+" min";
}
async function publicDriverAvailability(force=false){
  if(!force&&publicPresenceCache&&Date.now()-publicPresenceAt<8000)
    return publicPresenceCache;
  if(publicPresencePending)return publicPresencePending;
  publicPresencePending=(async()=>{
    const started=Date.now();
    const v=await window.TrasladosETA.availability({api:API,publishableKey:KEY});
    window.TrasladosPickupHealth?.recordContext?.("availability",
      v?(v.available?"available":"unavailable"):"network_error",
      v?(v.available?"ready":"blocked"):"issue",authReady()?"session":"guest",Date.now()-started);
    if(!v)return null;
    publicPresenceCache=v;
    publicPresenceAt=Date.now();
    return publicPresenceCache;
  })();
  try{return await publicPresencePending;}
  finally{publicPresencePending=null;}
}
async function refreshPickupPresence(force=false){
  const host=$("pickup-presence");
  if(!host)return;
  const urgent=selectedPickupMode!=="schedule";
  host.hidden=!urgent;
  const locationAction=$("pickup-use-location");
  if(locationAction)locationAction.hidden=!urgent||Boolean(record.origin);
  const ownedAction=$("pickup-existing-open");
  if(!urgent){
    pickupEtaRequestSeq++;host.removeAttribute("data-presence");
    if(ownedAction)ownedAction.hidden=true;
    return;
  }
  const seq=++pickupEtaRequestSeq;
  // A request owned by this signed-in customer (or this browser's guest token)
  // must never be mistaken for a new unavailable driver or resubmitted.
  const own=window.TrasladosOwnedPickup?.match?.(tripRows,record.origin,record.destination,record.stops);
  if(ownedAction)ownedAction.hidden=!own;
  if(own){
    const view=window.TrasladosOwnedPickup.presentation(own);
    if(ownedAction){ownedAction.dataset.code=own.code;ownedAction.firstChild.textContent=view.action+" ";}
    if($("booking-success")?.hidden&&$("urgent-actions"))$("urgent-actions").hidden=true;
    forgetPickupEta();
    paintPickupCard({presence:"owned",title:view.title,subtitle:view.description,
      checked:"Solicitud "+own.code+" · Consultá Mis traslados",phase:"owned"});
    return;
  }
  if($("booking-success")?.hidden&&$("urgent-actions"))$("urgent-actions").hidden=false;
  if(!host.dataset.presence||host.dataset.presence==="unknown"){
    paintPickupCard({presence:"checking",title:"Consultando disponibilidad",
      subtitle:"Estamos comprobando si puede pasar a buscarte.",phase:"checking"});
  }
  const driver=await publicDriverAvailability(force);
  if(seq!==pickupEtaRequestSeq||selectedPickupMode==="schedule")return;
  if(!driver){
    forgetPickupEta();
    paintPickupCard({presence:"unknown",title:"No pudimos comprobar la disponibilidad",
      subtitle:"Reintentaremos la consulta. Podés enviar tu solicitud.",phase:"issue"});
    return;
  }
  const serverAge=Date.parse(driver.checkedAt||"");
  const elapsedCheck=Number.isFinite(serverAge)
    ?Math.max(0,Math.round((Date.now()-serverAge)/1000)):null;
  // checkedAt refleja la consulta al servidor, NO una lectura nueva del GPS.
  // Un estado negativo no identifica por sí solo si faltan datos de ubicación,
  // red, consentimiento u horarios. Evitar un falso diagnóstico al pasajero.
  const checkedLabel=(elapsedCheck===null?"Disponibilidad consultada":
    elapsedCheck<25?"Disponibilidad comprobada ahora":
    elapsedCheck<60?"Disponibilidad consultada hace "+elapsedCheck+" s":
    "Disponibilidad consultada hace "+Math.floor(elapsedCheck/60)+" min")+
    " · La llegada se calcula por separado";
  if(!driver.available){
    forgetPickupEta();
    // Backend confirms only a combined public yes/no. Its cause is private and
    // may be agenda, activity, GPS or a request temporarily holding a slot.
    paintPickupCard({presence:"busy",title:"Recogida inmediata sin confirmar",
      subtitle:"No podemos asegurar una recogida ahora. Podés enviar una solicitud para que el conductor la revise; no confirma el viaje.",
      checked:checkedLabel,phase:"blocked"});
    return;
  }
  if(!record.origin){
    paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
      subtitle:"Elegí tu origen para ver cuánto tardaría en llegar.",
      checked:checkedLabel,phase:"origin"});
    return;
  }
  const signedIn=authReady();
  const lat=record.origin.lat,lng=record.origin.lng;
  const identity=signedIn?session.token:"visitante";
  const key=pickupIdentityKey();
  const lastGood=latestPickupEstimate(key);
  // Mapa envía el último movimiento detectado; la web no obtiene velocidad
  // ni posición del conductor. Los períodos aplican en primer plano.
  const etaInterval=window.TrasladosPickupCadence.intervalMs(driver.motionHint);
  const showWaiting=(prefix,requestActive=false)=>{
    if(lastGood){
      const estimateAge=Date.now()-lastVerifiedEtaAt;
      const freshEstimate=estimateAge<etaInterval+5000;
      paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
        subtitle:freshEstimate?"Llegada aproximada hasta tu origen.":
          "Actualizando la llegada a tu origen…",
        km:lastGood.km,minutes:lastGood.minutes,
        estimateNote:(freshEstimate?"Estimación obtenida ":"Última estimación ")+
          elapsedShort(estimateAge)+" · Puede variar.",checked:checkedLabel,phase:freshEstimate?"ready":requestActive?"routing":"waiting"});
    }else{
      paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
        subtitle:prefix||"Calculando llegada aproximada a tu origen…",
        checked:checkedLabel,phase:requestActive?"routing":"waiting"});
    }
  };
  // Consulta de estado cada 15 s y ruta cada 25 s en movimiento / 45 s detenido.
  // El límite SQL sigue vigente; nunca saltar el backoff ni cuotas existentes.
  // The routing Edge can take longer than a 15-second presence tick.
  // Never duplicate an in-flight request or lose its eventual result.
  if(pickupEtaPendingKey===key){
    showWaiting("Calculando llegada aproximada…",true);
    return;
  }
  const cooling=etaRetryIdentity===identity&&Date.now()<etaRetryNotBefore;
  const cadence=key===lastPickupEtaKey&&Date.now()-lastPickupEtaAt<etaInterval;
  if(cooling||cadence){
    // A failed ETA is not a confirmed driver arrival. Keep its reason visible
    // during the approved cooldown instead of looking like endless loading.
    showWaiting(lastEtaFailureKey===key?lastEtaFailureMessage:
      cooling?"Reintentaremos el cálculo de llegada en breve.":"Actualizando la llegada…");
    return;
  }
  lastPickupEtaKey=key;lastPickupEtaAt=Date.now();
  lastPickupEtaMessage="";lastPickupEtaIsEstimate=false;
  lastEtaFailureKey="";lastEtaFailureMessage="";
  pickupEtaPendingKey=key;
  showWaiting("Calculando llegada aproximada…",true);
  const etaStart=Date.now();
  window.TrasladosETA.pickup({
    api:API,publishableKey:KEY,originLat:lat,originLng:lng,
    sessionToken:signedIn?session.token:null
  }).then(data=>{
    window.TrasladosPickupHealth?.recordContext?.("eta",
      data?.available?"success":String(data?.reason||"other"),
      data?.available?"ready":"issue",signedIn?"session":"guest",Date.now()-etaStart);
    if(!etaResultStillApplies(key))return;
    if(data?.sessionInvalid&&signedIn){
      // The server rejected the saved token. Browser localStorage is not a
      // verified login. Keep the valid guest ETA returned by the trusted Edge.
      window.TrasladosPickupHealth?.recordContext?.("eta","login_required",
        "issue","session",Date.now()-etaStart);
      invalidatePassengerSession();
      const guestKey=pickupIdentityKey();
      lastPickupEtaKey=guestKey;
      lastPickupEtaAt=Date.now();
    }
    if(data?.available&&Number.isFinite(data.distanceKm)&&data.distanceKm>=0
       &&Number.isFinite(data.etaMin)&&data.etaMin>=0){
      const km=data.distanceKm,minutes=data.etaMin;
      lastVerifiedEtaResult={km,minutes,coarse:data.estimateType==="coarse"};
      lastVerifiedEtaKey=pickupIdentityKey();lastVerifiedEtaAt=Date.now();
      lastPickupEtaIsEstimate=true;
      etaRetryNotBefore=0;etaRetryIdentity="";
      paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
        subtitle:"Llegada aproximada hasta tu origen.",
        km,minutes,estimateNote:data?.sessionInvalid?
          "Estimación pública. La sesión anterior venció; volvé a ingresar o creá una cuenta desde Mi cuenta.":
          "Distancia y tiempo aproximados. La recogida requiere confirmación.",
        checked:checkedLabel,phase:"ready"});
      lastPickupEtaMessage=$("pickup-presence-status")?.textContent||"";
    }else{
      const reason=data?.reason;
      if(reason==="not_available"||reason==="occupied"){
        forgetPickupEta();
        // La consulta ETA puede perder la frescura del GPS entre la lectura
        // de disponibilidad y la petición de ruta. No atribuirlo a desconexión.
        publicPresenceCache=null;publicPresenceAt=0;
        paintPickupCard({presence:"busy",title:"Recogida inmediata sin confirmar",
          subtitle:"No podemos confirmar la recogida en este momento. Podés enviar una solicitud para revisión.",
          checked:checkedLabel,phase:"blocked"});
        return;
      }
      if(reason==="rate_limited"){
        // Espera prudente para no insistir sobre una cuota aún agotada.
        etaRetryIdentity=authReady()?session.token:"visitante";
        etaRetryNotBefore=Math.max(etaRetryNotBefore,Date.now()+90000);
      }
      const text=data?.sessionInvalid?
        "La sesión anterior ya no es válida. La llegada pública no está disponible por ahora; podés usar Mi cuenta para volver a ingresar o registrarte.":
        reason==="rate_limited"?"La llegada se actualizará en breve.":
        reason==="login_required"?(signedIn
          ?"La sesión no permitió calcular la llegada. Revisá Mi cuenta."
          :"La llegada para visitantes no está disponible ahora. Reintentaremos."):
        reason==="route_unavailable"?"Por ahora no hay tiempo estimado de llegada. Reintentaremos.":
        "No pudimos calcular la llegada. Reintentaremos.";
      lastEtaFailureKey=pickupIdentityKey();lastEtaFailureMessage=text;
      showWaiting(text);
      lastPickupEtaMessage=$("pickup-presence-status")?.textContent||"";
    }
  }).catch(()=>{
    window.TrasladosPickupHealth?.recordContext?.("eta","network_error","issue",
      signedIn?"session":"guest",Date.now()-etaStart);
    if(!etaResultStillApplies(key))return;
    lastEtaFailureKey=key;lastEtaFailureMessage="No pudimos calcular la llegada. Reintentaremos.";
    showWaiting(lastEtaFailureMessage);
    lastPickupEtaMessage=$("pickup-presence-status")?.textContent||"";
  }).finally(()=>{
    if(pickupEtaPendingKey===key)pickupEtaPendingKey="";
  });
}

function setWhen(kind){
  // "Ahora" y "En 10 min" comparten el mismo origen: cambiar de pestaña
  // no debe disparar nuevas ETA ni eludir la cuota de privacidad.
  selectedPickupMode=kind;
  const serviceCaption=$("booking-service-caption");
  if(serviceCaption)serviceCaption.textContent=kind==="schedule"?"VIAJE PROGRAMADO":kind==="now"?"RECOGIDA AHORA":"RECOGIDA EN "+kind+" MIN";
  clearBookingFeedback();
  document.querySelectorAll("[data-when]").forEach(x=>x.classList.toggle("active",x.dataset.when===kind));
  const urgent=kind!=="schedule";
  // La fecha/hora inmediata se fija EXCLUSIVAMENTE en Supabase al enviar, no en el cliente.
  $("pickup-datetime-block").hidden=urgent;
  $("when-next").hidden=urgent;
  $("pickup-mode-caption").hidden=!urgent;
  $("pickup-mode-caption").textContent=kind==="now"
    ?"Recogida ahora · Horario fijado por el servidor"
    :"Recogida en "+kind+" minutos · Horario fijado por el servidor";
  $("urgent-actions-title").textContent=kind==="now"
    ?"¿Necesitás viajar ahora?":"¿Te recogemos en "+kind+" minutos?";
  $("urgent-actions-description").textContent="Enviá tu recorrido al conductor. Recibirás un código para seguir la solicitud, que quedará pendiente de su confirmación.";
  $("booking-success").hidden=true;
  $("urgent-actions").hidden=!urgent;
  $("booking-final").hidden=urgent;
  $("review-booking").hidden=urgent;
  $("booking-preview").hidden=true;
  $("submit-actions").hidden=true;
  pendingReview=null;
  if(!urgent){
    const existingDate=$("pickup-date").value,existingTime=$("pickup-time").value;
    const chosen=existingDate&&existingTime?Date.parse(existingDate+"T"+existingTime+":00-03:00"):0;
    if(!chosen||chosen<Date.now()+45*60000){
      const later=currentUY(60);
      $("pickup-date").value=later.day;$("pickup-time").value=later.time;
    }
    $("availability").textContent="Reserva programada: los horarios disponibles se comprueban antes de enviar.";
    refreshBookingSteps();refreshPickupPresence();return;
  }
  const when=currentUY(kind==="now"?0:Number(kind)||0);
  $("pickup-date").value=when.day;$("pickup-time").value=when.time;
  $("availability").textContent="La hora se registra automáticamente al enviar. La solicitud queda pendiente de respuesta del conductor.";
  refreshBookingSteps();refreshPickupPresence();
}
// Dos experiencias de presentación independientes, con el mismo motor validado
// de búsqueda, GPS, identidad y contratos. Mover nodos conserva listeners y valores.
function enterService(kind){
  const quick=kind!=="schedule";
  const host=$(quick?"journey-field-host":"reserve-field-host");
  if(!host)return;
  for(const key of ["route","when","passenger"]){
    const card=document.querySelector('[data-booking-step="'+key+'"]');
    if(card&&card.parentElement!==host)host.appendChild(card);
  }
  const urgent=$("urgent-actions");
  if(quick&&urgent.parentElement!==host)host.appendChild(urgent);
  if(!quick&&urgent.parentElement===host)$("booking-preview").insertAdjacentElement("afterend",urgent);
  document.body.classList.toggle("journey-mode-quick",quick);
  setWhen(kind);
  if(quick&&$("journey-when-select"))$("journey-when-select").value=kind;
  routeStepAutoAdvanced=false;
  bookingSteps.open("route");
  shiftView(quick?"viaje":"reservar");
}
function shiftView(name){
  for(const panel of document.querySelectorAll(".view"))panel.classList.toggle("active",panel.id==="view-"+name);
  for(const tab of document.querySelectorAll(".tab")){
    const active=tab.dataset.view===name;tab.classList.toggle("active",active);
    if(active)tab.setAttribute("aria-current","page");else tab.removeAttribute("aria-current");
  }
  if(name==="viajes")void loadTrips();
  if(name==="cuenta")renderAccount();
  window.scrollTo({top:0,behavior:"smooth"});
}
function isUruguayCoords(lat,lng){return Number.isFinite(lat)&&Number.isFinite(lng)&&lat>=-35.3&&lat<=-30&&lng>=-58.8&&lng<=-52.9;}
// El navegador solo puede SOLICITAR el permiso al tocar el botón.
// Android/Chrome controlan su concesión; ninguna PWA puede activarlo por su cuenta.
function locationPermissionGuidance(errorCode){
  if(errorCode===1)return{
    title:"Ubicación bloqueada por Android o Chrome",
    description:"Traslados no puede cambiar el permiso automáticamente. Habilitalo una sola vez y volvé a probar.",
    steps:[
      "Cuando estés estacionado, cerrá las burbujas de Uber, Cabify o Mapa Trayectos. Si siguen superpuestas, desactivá temporalmente «Aparecer encima» para esas aplicaciones en Ajustes de Android.",
      "Abrí Traslados en Chrome, tocá el icono junto a la dirección y entrá en Permisos → Ubicación → Permitir.",
      "Si Chrome no tiene acceso: Ajustes del celular → Aplicaciones → Chrome → Permisos → Ubicación → Permitir mientras se usa la aplicación, con ubicación precisa."
    ]
  };
  if(errorCode===3)return{
    title:"La ubicación está demorando",
    description:"Android no consiguió una posición antes de que terminara el tiempo de espera.",
    steps:["Activá la ubicación (GPS) del celular y probá en un lugar con mejor señal."]
  };
  if(errorCode===2)return{
    title:"No pudimos determinar tu posición",
    description:"El permiso puede estar concedido, pero Android no obtuvo coordenadas.",
    steps:["Activá la ubicación del teléfono, comprobá la señal y volvé a intentar."]
  };
  return{
    title:"No se puede acceder a tu ubicación",
    description:"Esta función requiere abrir Traslados mediante una dirección HTTPS en un navegador compatible.",
    steps:["Usá Chrome actualizado o elegí el origen escribiendo una dirección."]
  };
}
function picker(name,mount,position){
  const key=name==="origin"||name==="destination"?name:"stop"+position;
  const box=document.createElement("section");box.className="location-widget";box.dataset.locationKind=name;mount.appendChild(box);
  const caption=text(box,"div",name==="origin"?"ORIGEN":name==="destination"?"DESTINO":"PARADA "+(position+1),"location-title");
  const selectedBox=text(box,"div","","location-selected");selectedBox.hidden=true;
  const controls=text(box,"div","","location-controls");
  const dep=text(controls,"div","","departments");
  let dept=depts[key]||(name==="destination"?"Montevideo":"Canelones");
  let selected=null,sequence=0,debounce=null;
  const depButtons=[];
  for(const opt of ["Montevideo","Canelones"]){
    const btn=button(dep,opt,"",()=>chooseDept(opt));depButtons.push(btn);
  }
  const select=document.createElement("select");select.setAttribute("aria-label","Otros departamentos de Uruguay");
  const placeholder=document.createElement("option");
  placeholder.value="";placeholder.textContent="Otros";
  select.appendChild(placeholder);
  for(const d of DEPTS.filter(x=>x!=="Montevideo"&&x!=="Canelones")){
    const item=document.createElement("option");item.value=d;item.textContent=d;select.appendChild(item);
  }
  dep.appendChild(select);
  function highlight(){
    for(const btn of depButtons)btn.classList.toggle("selected",btn.textContent===dept);
    select.value=(dept==="Montevideo"||dept==="Canelones")?"":dept;
  }
  highlight();
  const row=text(controls,"div","","loc-search");
  row.classList.add(name==="origin"?"loc-search-origin":"loc-search-standard");
  const input=document.createElement("input");input.type="search";
  input.placeholder="Calle, número o lugar…";
  input.autocomplete="off";input.enterKeyHint="search";
  input.setAttribute("aria-label",name==="origin"?"Dirección de origen":name==="destination"?"Dirección de destino":"Dirección de la parada "+(position+1));
  row.appendChild(input);
  const search=button(row,"Buscar","tiny-btn location-search-action",()=>void lookup());
  search.setAttribute("aria-label","Buscar dirección o lugar");
  search.title="Buscar dirección";
  const geo=name==="origin"?button(row,"⌖ Usar ubicación actual","tiny-btn location-use-current",()=>void useCurrent()):null;
  if(geo){
    geo.setAttribute("aria-label","Usar mi ubicación actual como origen");
    geo.title="Usar ubicación actual";
  }
  const gpsHelp=name==="origin"?text(controls,"div","","gps-permission-help"):null;
  if(gpsHelp){gpsHelp.hidden=true;gpsHelp.setAttribute("role","alert");}
  const results=text(controls,"div","","results");
  results.id="location-results-"+key;
  input.setAttribute("aria-controls",results.id);
  const feedback=text(controls,"p","","geo-feedback");feedback.hidden=true;
  const source=text(controls,"p","","geo-attribution");source.hidden=true;
  function repaint(){
    box.classList.toggle("is-selected",Boolean(selected));
    clear(selectedBox);
    if(!selected){selectedBox.hidden=true;controls.hidden=false;return;}
    selectedBox.hidden=false;controls.hidden=true;
    const shown=text(selectedBox,"div","","location-chosen-content");
    text(shown,"strong",selected.text,"location-selected-label");
    text(shown,"small",selected.department?selected.department+" · Ubicación seleccionada":"Ubicación seleccionada","location-selected-hint");
    button(selectedBox,"Editar","tiny-btn location-edit",()=>{
      selected=null;box.classList.remove("is-selected");selectedBox.hidden=true;controls.hidden=false;
      if(name==="origin"||name==="destination")record[name]=null;else record.stops[position]=null;
      renderRouteSummary();input.focus();
      clear(results);feedback.hidden=true;
    });
  }
  function persist(loc){
    if(!isUruguayCoords(loc.lat,loc.lng))throw new Error("El punto no está dentro de Uruguay.");
    selected={...loc,lat:Number(loc.lat),lng:Number(loc.lng)};
    ++sequence;clearTimeout(debounce);
    if(name==="origin"||name==="destination"){record[name]=selected;depts[name]=selected.department||dept;}
    else record.stops[position]=selected;
    clear(results);repaint();renderRouteSummary();
    if((name==="origin"||name==="destination")&&record.origin&&record.destination&&
       !routeStepAutoAdvanced&&bookingSteps?.active()==="route"){
      routeStepAutoAdvanced=true;
      bookingSteps.open("when",{scroll:true});
      refreshBookingSteps();
    }
  }
  function chooseDept(d){
    dept=d;depts[key]=d;highlight();
    if(selected){selected=null;repaint();if(name==="origin"||name==="destination")record[name]=null;else record.stops[position]=null;}
    clear(results);feedback.hidden=true;renderRouteSummary();
    if(input.value.trim().length>=2)void lookup();
  }
  select.addEventListener("change",()=>{if(select.value)chooseDept(select.value);});
  // El motor de direcciones se encuentra en address-engine.js.
  function renderEntries(places,addresses,q){
    if(selected)return;
    clear(results);source.hidden=true;
    const dedup=new Set(),unique=[];
    const all=[
      ...places.map(p=>({type:"place",name:p.name,secondary:[p.hint,p.department].filter(Boolean).join(" · "),point:p})),
      ...addresses.map(p=>({type:"address",name:p.address,secondary:p.departamento||"Uruguay",point:p})),
    ];
    for(const item of all){
      const normalized=window.TrasladosGeo.normalize(item.name);
      const key=normalized+"|"+item.type;
      if(!normalized||dedup.has(key))continue;
      dedup.add(key);unique.push(item);
      if(unique.length>=11)break;
    }
    for(const item of unique){
      const btn=button(results,"",item.type==="place"?"result result-place":"result",async()=>{
        try{
          const p=item.point;
          let lat=Number(p.lat),lng=Number(p.lng);
          if(item.type==="address"){
            const resolved=await window.TrasladosAddress.resolve(p,q,dept);
            lat=resolved.lat;lng=resolved.lng;
          }
          persist({text:item.name,lat,lng,department:item.type==="place"?p.department||dept:p.departamento||dept});
        }catch(e){showError(e);}
      });
      text(btn,"span",item.name,"result-title");
      text(btn,"small",(item.type==="place"?"◉ Lugar · ":"⌖ Dirección · ")+item.secondary,"result-subtitle");
    }
    const fromOsm=places.some(p=>p.id?.startsWith("osm-"));
    const local=places.some(p=>p.source==="local-verified");
    if(fromOsm||local){
      source.hidden=false;
      source.textContent=(fromOsm?"Lugares: © OpenStreetMap contributors (ODbL). ":"")+
        (local?"Guía de la Costa: ubicación de referencia. ":"")+
        "Confirmá en el mapa el acceso exacto si es necesario.";
    }
    if(!unique.length){
      feedback.hidden=false;
      feedback.textContent=window.TrasladosGeo.catalogState==="error"
        ?"No pudimos consultar los lugares. Revisá la conexión y volvé a buscar; también podés probar una dirección."
        :"Sin coincidencias en "+dept+". Probá el nombre completo o una dirección. Para buscar en otro departamento, cambialo arriba.";
    }
    else feedback.hidden=true;
  }
  const lookup=async()=>{
    const q=input.value.trim();
    if(q.length<2){clear(results);feedback.hidden=true;return;}
    const seq=++sequence;
    const quick=window.TrasladosGeo.immediate(q,dept);
    renderEntries(quick,[],q);
    if(q.length<3)return;
    let places=quick,addresses=[],done=0;
    loading(search,true);
    feedback.hidden=false;feedback.textContent=quick.length?"Buscando otros lugares…":"Buscando lugares y direcciones en "+dept+"…";
    const update=()=>{
      if(seq!==sequence||selected)return;
      renderEntries(places,addresses,q);
      if(done<2){
        feedback.hidden=false;feedback.textContent="Buscando más resultados…";
      }
      if(done===2)loading(search,false);
    };
    const placesTask=(async()=>{
      try{places=await window.TrasladosGeo.find(q,dept);}catch{/* IDE fallback still available */}
      finally{done++;update();}
    })();
    const streetTask=(async()=>{
      try{addresses=await window.TrasladosAddress.find(q,dept);}
      catch{/* POI index still available */}
      finally{done++;update();}
    })();
    await Promise.allSettled([placesTask,streetTask]);
  };
  input.addEventListener("keydown",event=>{
    if(event.key==="Enter"){
      event.preventDefault();
      clearTimeout(debounce);
      void lookup();
    }
  });
  input.addEventListener("input",()=>{
    ++sequence;clearTimeout(debounce);clear(results);feedback.hidden=true;
    const q=input.value.trim();
    if(q.length<2)return;
    renderEntries(window.TrasladosGeo.immediate(q,dept),[],q);
    debounce=setTimeout(()=>void lookup(),240);
  });
  function showGpsPermissionHelp(errorCode){
    if(!gpsHelp)return;
    const guidance=locationPermissionGuidance(errorCode);
    clear(gpsHelp);gpsHelp.hidden=false;
    text(gpsHelp,"strong",guidance.title,"gps-permission-title");
    text(gpsHelp,"p",guidance.description,"gps-permission-description");
    const steps=text(gpsHelp,"ol","","gps-permission-steps");
    for(const step of guidance.steps)text(steps,"li",step);
    const actions=text(gpsHelp,"div","","gps-permission-actions");
    button(actions,"↻ Volver a intentar","tiny-btn",()=>void useCurrent());
    button(actions,"Escribir dirección","tiny-btn gps-manual",()=>{
      gpsHelp.hidden=true;input.focus();
    });
  }
  function showGpsPermissionPreparation(){
    if(!gpsHelp)return;
    clear(gpsHelp);gpsHelp.hidden=false;
    text(gpsHelp,"strong","Antes de autorizar la ubicación","gps-permission-title");
    text(gpsHelp,"p","Android bloquea el permiso si hay burbujas flotantes abiertas. En tu caso vimos Uber y Cabify. No hace falta desinstalarlas.","gps-permission-description");
    const steps=text(gpsHelp,"ol","","gps-permission-steps");
    text(steps,"li","Cuando estés estacionado, cerrá temporalmente las burbujas de Uber, Cabify o Mapa Trayectos.");
    text(steps,"li","Después tocá «Solicitar permiso» para que Android permita la ubicación de Traslados.");
    const actions=text(gpsHelp,"div","","gps-permission-actions");
    button(actions,"⌖ Solicitar permiso","tiny-btn",requestGps);
    button(actions,"Elegir origen sin GPS","tiny-btn gps-manual",()=>{
      gpsHelp.hidden=true;input.focus();
    });
  }
  // El sitio no puede detectar ni cerrar superposiciones de otras apps.
  // Antes del PRIMER pedido de permiso en Android, prevenir el diálogo bloqueado.
  const useCurrent=async()=>{
    if(!navigator.geolocation||window.isSecureContext===false){
      showGpsPermissionHelp(0);return;
    }
    if(geo?.disabled)return;
    const android=/Android/i.test(navigator.userAgent||"");
    if(!navigator.permissions?.query){
      if(android)showGpsPermissionPreparation();
      else requestGps();
      return;
    }
    geo.disabled=true;
    const original=geo.textContent;
    geo.textContent="⌖ Revisando permiso…";
    let state="prompt";
    try{
      const status=await navigator.permissions.query({name:"geolocation"});
      state=status.state;
    }catch{/* Some browsers do not expose the Permissions API. */}
    finally{
      geo.disabled=false;geo.textContent=original;
    }
    if(state==="denied"){
      showGpsPermissionHelp(1);
    }else if(state==="granted"||!android){
      requestGps();
    }else{
      showGpsPermissionPreparation();
    }
  };
  function requestGps(){
    if(!navigator.geolocation||geo?.disabled)return;
    if(gpsHelp)gpsHelp.hidden=true;
    const original=geo.textContent;
    geo.disabled=true;geo.textContent="⌖ Obteniendo ubicación…";
    const finish=()=>{geo.disabled=false;geo.textContent=original;};
    tell("Solicitando ubicación al navegador…");
    // La ventana de Android se solicita SOLO al tocar «Solicitar permiso».
    navigator.geolocation.getCurrentPosition(async p=>{
      try{
        const lat=p.coords.latitude,lng=p.coords.longitude;
        if(!isUruguayCoords(lat,lng)){
          tell("La ubicación detectada no está en Uruguay. Elegí el origen manualmente.");return;
        }
        let address="Ubicación actual (GPS)",selectedDept=dept;
        try{
          const result=await window.TrasladosAddress.reverse(lat,lng);
          if(result?.address){
            address=result.address;selectedDept=result.departamento||dept;
          }
        }catch{/* Las coordenadas GPS siguen siendo válidas sin geocodificación. */}
        persist({text:address,lat,lng,department:selectedDept});
      }catch{showGpsPermissionHelp(2);}
      finally{finish();}
    },error=>{
      finish();
      showGpsPermissionHelp(error?.code??0);
    },{enableHighAccuracy:true,timeout:14000,maximumAge:15000});
  }
  if(name==="stop"&&record.stops[position])persist(record.stops[position]);
  return {state:()=>selected,chooseDept,input,persist,box,useCurrent};
}
// El motor vial no depende de la UI ni del estado de reservas.
const roadEngine=window.TrasladosRoutes;
let latestRoad=null,activeRouteKey="",routeSequence=0;
function selectedRoadPoints(){
  return record.origin&&record.destination ? [record.origin,...record.stops.filter(Boolean),record.destination] : [];
}
function routeKeyOf(points){return roadEngine.key(points);}
function addRoadDiagram(parent,geometry){
  if(!Array.isArray(geometry)||geometry.length<2)return;
  const valid=geometry.filter(p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite));
  if(valid.length<2)return;
  const lat=valid.map(p=>p[0]),lng=valid.map(p=>p[1]);
  const bounds={minLat:Math.min(...lat),maxLat:Math.max(...lat),minLng:Math.min(...lng),maxLng:Math.max(...lng)};
  const dx=Math.max(.0001,bounds.maxLng-bounds.minLng),dy=Math.max(.0001,bounds.maxLat-bounds.minLat);
  const w=600,h=180,pad=24;
  const coords=valid.filter((_,i)=>i===0||i===valid.length-1||i%Math.max(1,Math.floor(valid.length/280))===0)
    .map(([a,b])=>[pad+(b-bounds.minLng)/dx*(w-2*pad),h-pad-(a-bounds.minLat)/dy*(h-2*pad)]);
  const NS="http://www.w3.org/2000/svg";
  const svg=document.createElementNS(NS,"svg");svg.setAttribute("viewBox","0 0 600 180");
  svg.setAttribute("role","img");svg.setAttribute("aria-label","Esquema del trayecto calculado por calles");
  svg.classList.add("route-road-svg");parent.appendChild(svg);
  const path=document.createElementNS(NS,"polyline");
  path.setAttribute("fill","none");path.setAttribute("stroke","#e9c982");path.setAttribute("stroke-width","4");
  path.setAttribute("stroke-linecap","round");path.setAttribute("stroke-linejoin","round");
  path.setAttribute("points",coords.map(p=>p.map(v=>v.toFixed(1)).join(",")).join(" "));
  svg.appendChild(path);
  for(const [i,label] of [[0,"O"],[coords.length-1,"D"]]){
    const p=coords[i];
    if(!p)continue;
    const dot=document.createElementNS(NS,"circle");dot.setAttribute("cx",String(p[0]));
    dot.setAttribute("cy",String(p[1]));dot.setAttribute("r","12");
    dot.setAttribute("fill","#0e3237");dot.setAttribute("stroke","#f0cf88");
    dot.setAttribute("stroke-width","3");svg.appendChild(dot);
    const textNode=document.createElementNS(NS,"text");textNode.setAttribute("x",String(p[0]));
    textNode.setAttribute("y",String(p[1]+4));textNode.setAttribute("text-anchor","middle");
    textNode.setAttribute("fill","#fff3d6");textNode.setAttribute("font-size","11");textNode.textContent=label;
    svg.appendChild(textNode);
  }
  text(parent,"p","Esquema de ruta por calles (no a escala). Abrí Maps para ver el mapa completo.","route-map-caption");
}
function renderRouteSummary(){
  const host=$("route-overview");
  if(!host)return;
  // Una parada todavía sin dirección invalidaría cualquier presupuesto vial:
  // jamás mostrar una cotización del trayecto anterior sin esa parada.
  if(record.stops.some(stop=>!stop)){
    activeRouteKey="";latestRoad=null;routeSequence++;
    clear(host);host.hidden=false;
    text(host,"p","Elegí la dirección de todas las paradas para calcular el recorrido y su valor orientativo.","route-status");
    refreshBookingSteps();return;
  }
  const points=selectedRoadPoints();
  clear(host);host.hidden=points.length<2;
  if(points.length<2){
    activeRouteKey="";latestRoad=null;routeSequence++;
    refreshBookingSteps();refreshPickupPresence();return;
  }
  const key=routeKeyOf(points);activeRouteKey=key;refreshBookingSteps();refreshPickupPresence();

  // A and B are already displayed directly above, in their editable cards.
  // Do not repeat addresses inside a second giant card.
  const header=text(host,"div","","route-summary-heading");
  const heading=text(header,"div","","route-summary-title");
  text(heading,"span","✦","route-summary-symbol");
  text(heading,"h3","Estimación del viaje");
  const indicator=text(header,"span","Consultando ruta…","route-summary-indicator");
  indicator.setAttribute("role","status");

  const metrics=text(host,"div","","route-metrics");
  const km=text(metrics,"div","","route-metric");
  text(km,"small","DISTANCIA DEL VIAJE");
  const distance=text(km,"strong","…");
  const min=text(metrics,"div","","route-metric");
  text(min,"small","DURACIÓN DEL VIAJE");
  const duration=text(min,"strong","…");
  const fare=text(metrics,"div","","route-metric route-metric-fare");
  const fareInfo=text(fare,"div","","route-fare-heading");
  text(fareInfo,"small","VALOR DE REFERENCIA");
  const reference=text(fareInfo,"strong","Consultando…");

  const footer=text(host,"div","","route-footer");
  const note=text(footer,"p","Calculamos por carretera; no incluye tráfico en vivo.","route-status");
  const a=text(footer,"a","Abrir en Maps ↗","route-google");
  a.href=mapsLinkFromBooking({origin:points[0],destination:points[points.length-1],stops:points.slice(1,-1)});
  a.target="_blank";a.rel="noopener noreferrer";
  const retry=document.createElement("button");
  retry.type="button";
  retry.className="route-retry";
  retry.textContent="↻ Reintentar cálculo";
  retry.hidden=true;
  retry.onclick=()=>{
    roadEngine.invalidate(key);
    renderRouteSummary();
  };
  footer.appendChild(retry);

  const details=document.createElement("details");
  details.className="route-extra";host.appendChild(details);
  text(details,"summary","Detalles de la estimación");
  const detailCaption=text(details,"p","","route-map-caption");
  const map=text(details,"div","","route-road-map");
  const seq=++routeSequence;
  const finish=data=>{
    if(seq!==routeSequence||key!==activeRouteKey)return;
    const valid=roadEngine.verified(data);
    if(valid){
      latestRoad={key,data};
      distance.textContent=data.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km";
      duration.textContent=data.durationMin+" min";
      reference.textContent=Number.isFinite(data.referenceFareUyu)&&data.referenceFareUyu>0
        ? "$ "+data.referenceFareUyu.toLocaleString("es-UY",{maximumFractionDigits:0})
        : "A confirmar";
      retry.hidden=true;
      indicator.textContent="Ruta verificada";
      indicator.classList.add("is-verified");
      note.textContent="Valor orientativo según kilómetros del viaje. No incluye el recorrido del conductor hasta recogerte, peajes ni extras; el conductor confirma el precio final.";
      detailCaption.textContent=data.source==="supabase_route_cache"
        ? "Ruta guardada, calculada por carretera. Datos cartográficos © OpenStreetMap contributors. No incluye tráfico en vivo."
        : "Ruta estimada por carretera. Datos cartográficos © OpenStreetMap contributors. No incluye tráfico en vivo.";
      clear(map);
      if(Array.isArray(data.geometry)&&data.geometry.length>1)addRoadDiagram(map,data.geometry);
    }else{
      latestRoad=null;
      distance.textContent="—";duration.textContent="—";reference.textContent="A confirmar";
      indicator.textContent="Sin cálculo por ahora";
      indicator.classList.remove("is-verified");
      retry.hidden=false;
      const reason=roadEngine.diagnostic?.(key)?.reason||"unverified_response";
      const messages={
        not_configured:"El servicio de rutas necesita habilitación en el servidor. El recorrido sigue seleccionado; podés reintentar más tarde.",
        temporarily_unavailable:"El servicio de rutas está temporalmente ocupado. Tocá Reintentar cálculo en unos instantes.",
        no_route:"No encontramos una ruta por carretera para estos puntos. Revisá las direcciones o probá nuevamente.",
        invalid_points:"Una dirección no tiene coordenadas válidas. Probá editarla y seleccionarla otra vez.",
        timeout:"El cálculo demoró demasiado. Tocá Reintentar cálculo cuando tengas buena conexión.",
        network_error:"No pudimos conectar con el servicio de rutas. Revisá Internet y tocá Reintentar cálculo.",
        http_error:"El servicio de rutas no respondió correctamente. Podés reintentar sin cambiar las direcciones."
      };
      note.textContent=messages[reason]||"No pudimos verificar la ruta en este momento. Tocá Reintentar cálculo.";
      detailCaption.textContent="Estado del motor: "+reason+". Sin ruta vial verificada no mostramos kilómetros, minutos ni tarifa inventados. Podés usar Maps mientras se restablece el cálculo.";
      clear(map);
    }
    refreshBookingSteps();
  };

  const hit=roadEngine.peek(key);
  if(hit){finish(hit);return;}
  latestRoad=null;
  roadEngine.estimate(points,{api:API,publishableKey:KEY})
    .then(finish).catch(()=>finish(null));
}

let originPicker,destPicker,stopPickers=[];
function renderStops(){
  const host=$("stops");clear(host);stopPickers=[];
  for(let i=0;i<record.stops.length;i++){
    const row=document.createElement("div");row.className="stop-row";host.appendChild(row);
    button(row,"Quitar","tiny-btn",()=>{record.stops.splice(i,1);renderStops();renderRouteSummary();refreshBookingSteps();});
    const mount=document.createElement("div");row.appendChild(mount);
    stopPickers[i]=picker("stop",mount,i);
  }
  $("add-stop").disabled=record.stops.length>=8;
}
function authReady(){return !!session?.token&&!!session?.customer;}
function syncAuthUI(){
  const logged=authReady();
  $("stop-wrap").hidden=!logged;$("login-for-stops").hidden=logged;
  $("passenger-identification").hidden=logged;
  $("passenger-account-summary").hidden=!logged;
  $("passenger-account-name").textContent=logged?(session.customer.full_name||"Sin nombre"):"";
  $("passenger-account-phone").textContent=logged?(session.customer.phone||"Sin teléfono"):"";
  renderAccount();renderStops();refreshBookingSteps();
}
function clearPassengerSessionLocally(){
  session=null;localStorage.removeItem(SESSION_KEY);
  closeTripChat();$("trip-chat-dialog-content").replaceChildren();tripQueryEpoch++;tripsLoading=false;tripRows=[];tripLoaded=false;tripSnap=null;tripRenderedSignature=null;
  $("trips-list").replaceChildren();$("trips-status").textContent="Actualizá Mis traslados para consultar esta sesión.";
  $("trips-focus").textContent="";$("trips-attention-count").hidden=true;
  for(const tab of document.querySelectorAll("[data-trips-scope]")){
    const selected=tab.dataset.tripsScope==="upcoming";
    tab.classList.toggle("active",selected);tab.setAttribute("aria-pressed",String(selected));
  }
  openTripCards.clear();openTripGroups.clear();openTripDetails.clear();tripChatDrafts.clear();tripChatAttempts.clear();
  tripsScopeChosen=false;tripsScope="upcoming";tripsFilter="all";$("trips-status-filter").value="all";
  $("person-name").value="";$("person-phone").value="";
  syncAuthUI();
}
function invalidatePassengerSession(){
  if(!authReady())return;
  expiredPassengerSessionNotice=true;
  clearPassengerSessionLocally();
  tell("La sesión guardada ya no existe en el servidor. Podés consultar como visitante; ingresá o creá una cuenta en Mi cuenta.");
}
function renderAccount(){
  const logged=authReady();
  $("account-session").hidden=!logged;$("account-form").hidden=logged;
  const warning=$("account-session-expired");
  if(warning)warning.hidden=!expiredPassengerSessionNotice;
  if(!logged)return;
  const c=$("account-session");clear(c);
  text(c,"h3","Sesión iniciada");
  text(c,"p",(session.customer.full_name||"Pasajero")+" · "+(session.customer.phone||""));
  button(c,"CERRAR SESIÓN","secondary full",async()=>{
    if(!window.confirm("¿Cerrar sesión en este dispositivo?"))return;
    try{await rpc("customer_logout",{p_session_token:session.token});}catch{}
    expiredPassengerSessionNotice=false;
    clearPassengerSessionLocally();tell("Sesión cerrada");
  });
}
/** Account-only support UI. Diagnostics live on this device and never leave
 * it except by explicit user copy. A readonly text box remains as fallback
 * for Android WebView versions without clipboard API permissions. */
function refreshPickupDiagnosticsPreview(){
  const box=$("pickup-diagnostics-preview");
  if(!box)return;
  const rows=window.TrasladosPickupHealth?.summary?.()||[];
  box.value=window.TrasladosPickupHealth?.exportSafe?.()||
    '{"events":[]}';
  const count=$("pickup-diagnostics-count");
  if(count)count.textContent=rows.length+" evento(s) conservados por hasta 24 horas en este dispositivo.";
}
async function copyPickupDiagnostics(){
  refreshPickupDiagnosticsPreview();
  const txt=$("pickup-diagnostics-preview").value;
  let copied=false;
  try{
    if(navigator.clipboard?.writeText){
      await navigator.clipboard.writeText(txt);
      copied=true;
    }
  }catch{}
  if(!copied){
    try{
      const field=$("pickup-diagnostics-preview");
      field.focus();field.select();
      copied=Boolean(document.execCommand("copy"));
    }catch{}
  }
  tell(copied?"Diagnóstico técnico copiado, sin datos personales.":
    "Seleccioná el contenido para copiar el diagnóstico.");
}
function setAccountMode(register){
  registerMode=register;
  $("mode-login").classList.toggle("active",!register);
  $("mode-register").classList.toggle("active",register);
  $("register-name-wrap").hidden=!register;
  $("account-submit").textContent=register?"CREAR CUENTA":"INGRESAR";
}
async function accountSubmit(){
  const btn=$("account-submit");
  if(busy)return;
  const phone=$("login-phone").value.trim(),pin=$("login-pin").value.trim();
  const name=$("register-name").value.trim();
  if(phone.replace(/\D/g,"").length<8||!/^[0-9]{6}$/.test(pin)||registerMode&&name.length<2){
    tell("Completá teléfono, PIN de 6 dígitos y nombre si vas a crear la cuenta.");return;
  }
  busy=true;loading(btn,true);
  try{
    const result=registerMode?await rpc("customer_register",{p_full_name:name,p_phone:phone,p_pin:pin,p_device_label:"PWA Traslados Cliente"}):
      await rpc("customer_login",{p_phone:phone,p_pin:pin,p_device_label:"PWA Traslados Cliente"});
    if(!result?.session_token||!result.customer)throw new Error("No se recibió una sesión válida.");
    session={token:result.session_token,customer:result.customer};
    expiredPassengerSessionNotice=false;
    closeTripChat();$("trip-chat-dialog-content").replaceChildren();tripQueryEpoch++;tripsLoading=false;tripRows=[];tripLoaded=false;tripSnap=null;tripRenderedSignature=null;
    $("trips-list").replaceChildren();$("trips-status").textContent="Actualizá Mis traslados para consultar esta sesión.";
    $("trips-focus").textContent="";$("trips-attention-count").hidden=true;
    for(const tab of document.querySelectorAll("[data-trips-scope]")){
      const selected=tab.dataset.tripsScope==="upcoming";
      tab.classList.toggle("active",selected);tab.setAttribute("aria-pressed",String(selected));
    }
    openTripCards.clear();openTripGroups.clear();openTripDetails.clear();tripChatDrafts.clear();tripChatAttempts.clear();
    tripsScopeChosen=false;tripsScope="upcoming";tripsFilter="all";$("trips-status-filter").value="all";
    lastPickupEtaAt=0;refreshPickupPresence(true);
    saveStorage(SESSION_KEY,session);$("login-pin").value="";
    // Claim existing guest bookings in this browser so they become visible
    // in the new account, just like the native Cliente app does.
    const guestTrips=jsonStorage(GUEST_KEY,[]);
    const unclaimed=[];
    for(const prior of guestTrips){
      try{await rpc("customer_claim_reservation",{
        p_session_token:session.token,p_public_token:prior.token
      });}catch{unclaimed.push(prior);}
    }
    saveStorage(GUEST_KEY,unclaimed);
    syncAuthUI();tell("Bienvenido: cuenta conectada con Traslados Conductor.");shiftView("reservar");void loadTrips({background:true});
  }catch(e){showError(e);}finally{busy=false;loading(btn,false);}
}
function bookingData(){
  const scheduled=selectedPickupMode==="schedule";
  const date=scheduled?$("pickup-date").value:null,time=scheduled?$("pickup-time").value:null;
  const passengers=Number($("passengers").value);
  const origin=record.origin,destination=record.destination;
  const name=authReady()?(session.customer.full_name||"").trim():$("person-name").value.trim();
  const phone=authReady()?(session.customer.phone||"").trim():$("person-phone").value.trim();
  if(!name||name.length<2||phone.replace(/\D/g,"").length<8)throw new Error("Faltan nombre o teléfono válidos.");
  if(scheduled&&(!date||!time))throw new Error("Elegí fecha y hora.");
  if(scheduled&&new Date(date+"T"+time+":00-03:00").getTime()<Date.now()-20000)throw new Error("El horario elegido quedó en el pasado.");
  if(!Number.isInteger(passengers)||passengers<1||passengers>8)throw new Error("Cantidad de pasajeros inválida.");
  if(!origin||!destination)throw new Error("Tenés que elegir direcciones verificadas para origen y destino.");
  if(origin.lat===destination.lat&&origin.lng===destination.lng)throw new Error("Origen y destino no pueden ser el mismo punto.");
  if(record.stops.some(s=>!s))throw new Error("Hay paradas sin dirección geográfica verificada.");
  if(record.stops.length&&!authReady())throw new Error("Para agregar paradas intermedias, ingresá a tu cuenta.");
  return{date,time,pickupMode:selectedPickupMode,passengers,origin,destination,name,phone,comments:$("comments").value.trim(),stops:[...record.stops]};
}
function reviewDate(date,time){
  const [year,month,day]=String(date).split("-").map(Number);
  const utc=new Date(Date.UTC(year,month-1,day));
  const label=Number.isNaN(utc.getTime())?String(date):new Intl.DateTimeFormat("es-UY",{
    weekday:"short",day:"numeric",month:"short",year:"numeric",timeZone:"UTC"
  }).format(utc);
  return label+" · "+String(time).slice(0,5)+" h";
}
function mapsLinkFromBooking(p){
  const origin=p.origin.lat+","+p.origin.lng;
  const destination=p.destination.lat+","+p.destination.lng;
  const qs=new URLSearchParams({api:"1",origin,destination,travelmode:"driving"});
  if(p.stops.length)qs.set("waypoints",p.stops.map(x=>x.lat+","+x.lng).join("|"));
  return "https://www.google.com/maps/dir/?"+qs.toString();
}
function previewLocation(parent,kind,address){
  const item=text(parent,"div","","preview-place");
  const mark=text(item,"span",kind==="Origen"?"●":"◆","preview-place-icon");
  mark.setAttribute("aria-hidden","true");
  const contents=text(item,"div","","preview-place-text");
  text(contents,"span",kind,"preview-label");
  text(contents,"strong",address,"preview-place-name");
}
function showPreview(){
  if(selectedPickupMode!=="schedule"){tell("Usá Enviar solicitud para pedir una recogida inmediata.");return;}
  clearBookingFeedback();
  try{
    pendingReview=bookingData();
    const p=pendingReview;
    const target=$("booking-preview");clear(target);target.hidden=false;

    const title=text(target,"div","","preview-title");
    text(title,"span","ÚLTIMO PASO · CONFIRMACIÓN","preview-eyebrow");
    text(title,"h3","Confirmá tu solicitud");
    text(title,"p","Podés solicitar horarios fuera de la franja habitual. El conductor revisará tu pedido, podrá enviarte un presupuesto y solo se confirmará cuando lo aceptes.");

    const journey=text(target,"div","","preview-journey");
    previewLocation(journey,"Origen",p.origin.text);
    if(p.stops.length){
      const stops=text(journey,"div","","preview-stops");
      text(stops,"span",p.stops.length+" parada"+(p.stops.length===1?"":"s")+" intermedia"+(p.stops.length===1?"":"s"),"preview-stop-count");
      const more=document.createElement("details");more.className="preview-stops-detail";stops.appendChild(more);
      text(more,"summary","Ver paradas");
      for(const stop of p.stops)text(more,"p",stop.text);
    }
    previewLocation(journey,"Destino",p.destination.text);

    if(latestRoad?.key===routeKeyOf([p.origin,...p.stops,p.destination])){
      const estimate=text(target,"div","","preview-road");
      text(estimate,"strong",latestRoad.data.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km por carretera · "+latestRoad.data.durationMin+" min aprox.");
      text(estimate,"small","Tiempo de conducción sin tráfico en vivo.");
    }else{
      const estimate=text(target,"p","Distancia y duración por carretera: consultá Google Maps.","preview-road-note");
    }

    const chips=text(target,"div","","preview-chips");
    const when=text(chips,"div","","preview-chip");
    text(when,"span","FECHA Y HORA","preview-label");
    text(when,"strong",reviewDate(p.date,p.time));
    const passengers=text(chips,"div","","preview-chip");
    text(passengers,"span","PASAJEROS","preview-label");
    text(passengers,"strong",p.passengers===1?"1 persona":p.passengers+" personas");

    const budget=text(target,"div","","preview-budget");
    text(budget,"span","VALOR ORIENTATIVO","preview-label");
    const quote=latestRoad?.key===routeKeyOf([p.origin,...p.stops,p.destination])
      ? latestRoad.data.referenceFareUyu : null;
    text(budget,"strong",Number.isFinite(quote)&&quote>0
      ? "$ "+quote.toLocaleString("es-UY",{maximumFractionDigits:0})+" aprox." : "A confirmar por el conductor");
    text(budget,"p","Es una referencia sin peajes ni extras. El conductor enviará el presupuesto definitivo desde Mapa; no se cobra ni confirma el viaje al solicitar.");

    const tools=text(target,"div","","preview-tools");
    const link=text(tools,"a","↗ Ver recorrido en Google Maps","preview-maps");
    link.href=mapsLinkFromBooking(p);link.target="_blank";link.rel="noopener noreferrer";

    const extra=document.createElement("details");
    extra.className="preview-extra";target.appendChild(extra);
    text(extra,"summary","Datos del pasajero e indicaciones");
    const details=text(extra,"div","","preview-extra-body");
    for(const [label,value] of [
      ["Pasajero",p.name],["Teléfono",p.phone],["Indicaciones",p.comments||"Sin indicaciones"]
    ]){
      const row=text(details,"div","","preview-row");
      text(row,"span",label);text(row,"span",value);
    }
    $("booking-final").hidden=true;$("review-booking").hidden=true;$("submit-actions").hidden=false;
    target.scrollIntoView({behavior:"smooth",block:"start"});
  }catch(e){pendingReview=null;suggestBookingStep(e);showError(e);}
}
function saveGuest(result){
  const old=jsonStorage(GUEST_KEY,[]).filter(x=>x.token!==result.public_token);
  old.unshift({code:result.code,token:result.public_token,savedAt:Date.now()});
  saveStorage(GUEST_KEY,old.slice(0,18));
}

function newClientRequestId(){
  const secure=window.crypto;
  if(secure?.randomUUID)return secure.randomUUID();
  if(!secure?.getRandomValues)throw new Error("No se pudo crear un identificador seguro. Abrí Traslados en Chrome actualizado.");
  const bytes=new Uint8Array(16);secure.getRandomValues(bytes);
  bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
  const hex=[...bytes].map(x=>x.toString(16).padStart(2,"0")).join("");
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join("-");
}
function renderBookingSuccess(result,mode="schedule"){
  const box=$("booking-success");clear(box);box.hidden=false;
  text(box,"div","✓","mark");
  text(box,"h2","Solicitud enviada");
  const urgent=mode!=="schedule";
  text(box,"p",urgent
    ?"Recibimos tu pedido de recogida. El conductor debe responder; todavía no es un viaje confirmado."
    :"Recibimos tu solicitud. El conductor revisará el horario y podrá ofrecerte un presupuesto; el viaje se confirma únicamente cuando lo aceptes.");
  text(box,"div",result.code,"code");
  if(urgent&&result.pickup_date&&result.pickup_time){
    text(box,"p",mode==="now"
      ?"Recogida solicitada ahora, "+result.pickup_time+" h del "+result.pickup_date+"."
      :"Recogida solicitada para "+result.pickup_time+" h del "+result.pickup_date+".");
  }
  text(box,"p","Podés seguir el estado en Mis traslados. No se realiza ningún cobro al enviar.");
  button(box,"VER MIS TRASLADOS","primary full",()=>shiftView("viajes"));
  for(const id of ["booking-preview","booking-final","review-booking","submit-actions","urgent-actions"])
    $(id).hidden=true;
  pendingReview=null;
  box.scrollIntoView({behavior:"smooth",block:"start"});
}
async function submitUrgentPickup(){
  if(selectedPickupMode==="schedule"||busy)return;
  const notice=$("urgent-send-error");notice.hidden=true;
  const same=window.TrasladosOwnedPickup?.match?.(tripRows,record.origin,record.destination,record.stops);
  if(same){
    notice.textContent="Este recorrido ya tiene una solicitud registrada. Revisá Mis traslados antes de enviar otra.";
    notice.hidden=false;refreshPickupPresence();
    return;
  }
  let p;
  try{p=bookingData();}catch(e){suggestBookingStep(e);showError(e);return;}
  // Reintentos tras una respuesta ambigua reutilizan el mismo UUID.
  // Un cambio de datos o modalidad inicia otra solicitud, nunca reenvía la anterior.
  const signature=JSON.stringify(p);
  if(!urgentAttempt||urgentAttempt.signature!==signature)
    urgentAttempt={signature,id:newClientRequestId()};
  const btn=$("urgent-send");busy=true;loading(btn,true);
  try{
    guardConnection();
    const result=await rpc("customer_request_pickup_v1",
      window.TrasladosBookingContracts.immediatePickup(authReady()?session.token:null,p,urgentAttempt.id));
    if(!result?.code)throw new Error("El servidor no devolvió un código. Revisá Mis traslados antes de repetir.");
    if(result.public_token)saveGuest(result);
    urgentAttempt=null;
    renderBookingSuccess(result,p.pickupMode);
  }catch(e){
    const msg=String(e?.message||"");
    notice.textContent=/solicitud reciente pendiente|Demasiadas solicitudes/i.test(msg)
      ?"Ya existe una solicitud reciente o alcanzaste el límite. Revisá Mis traslados antes de enviar otra."
      :"No pudimos comprobar el envío. Si reintentás sin cambiar los datos, el servidor reutilizará el mismo identificador y evitará duplicados.";
    notice.hidden=false;
    showError(e);
  }finally{busy=false;loading(btn,false);}
}

async function submitBooking(){
  if(selectedPickupMode!=="schedule"){tell("Para ahora o en 10 minutos, tocá Enviar solicitud.");return;}
  if(busy||!pendingReview)return;
  clearBookingFeedback();
  // El resumen aprobado debe coincidir exactamente con los campos al enviar:
  // editar detrás del panel nunca puede registrar un viaje desactualizado.
  try{
    if(JSON.stringify(bookingData())!==JSON.stringify(pendingReview)){
      pendingReview=null;
      $("booking-preview").hidden=true;
      $("submit-actions").hidden=true;
      $("booking-final").hidden=false;
      tell("Cambiaste algún dato. Revisá nuevamente la solicitud antes de enviarla.");
      return;
    }
  }catch(e){
    pendingReview=null;
    $("booking-preview").hidden=true;
    $("submit-actions").hidden=true;
    $("booking-final").hidden=false;
    suggestBookingStep(e);showError(e);return;
  }
  const btn=$("confirm-booking");busy=true;loading(btn,true);
  try{
    guardConnection();const p=pendingReview;
    let result;
    if(authReady()){
      const availability=await rpc("customer_check_availability_v11_4",
        window.TrasladosBookingContracts.checkAvailability(session.token,p));
      if(!availability?.requestable)throw new Error(
        availability?.request_reason==="SCHEDULE_REQUESTS_PAUSED"
          ?"El conductor pausó temporalmente los pedidos programados."
          :"Este horario no admite solicitudes. Elegí una fecha futura o consultá al conductor."
      );
      result=await rpc("customer_create_reservation_v12",
        window.TrasladosBookingContracts.registeredReservation(session.token,p));
    }else{
      result=await rpc("create_reservation",
        window.TrasladosBookingContracts.guestReservation(p));
      if(result?.public_token)saveGuest(result);
    }
    if(!result?.code)throw new Error("El servidor no devolvió el código de la reserva. Revisá Mis traslados antes de repetir.");
    renderBookingSuccess(result,"schedule");
  }catch(e){bookingSendFailure(e);}finally{busy=false;loading(btn,false);}
}

const tripBoard=window.TrasladosTripBoard;
let tripRows=[],tripLoaded=false,tripsLoading=false,tripSnap=null;
let tripQueryEpoch=0,tripRenderedSignature=null;
// A 20-second response may arrive after logout/login: discard that old account's result.
function tripDisplayKey(rows){
  return JSON.stringify(rows.map(r=>[
    r.id,r.code,r.status,r.pickup_date,r.pickup_time,r.origin_text,r.destination_text,
    r.stops,r.comments,r.passengers,r.quote_status,r.quote_final_total,r.quote_sent_at,
    r.quote_accepted_at,r.quote_includes,r.pickup_phase,r.pickup_phase_updated_at,
    r.confirmed_at,r.created_at,r.updated_at,r.last_driver_message_id,
    tripBoard.metadata(r).tone
  ]));
}
let tripsScope="upcoming",tripsGroupBy="day",tripsFilter="all",tripFocused=null,tripPreviouslyFocused=null;
let tripsScopeChosen=false,openTripCards=new Set(),openTripGroups=new Set(),openTripDetails=new Set();
let activeTripChat=null,tripChatPreviouslyFocused=null,tripChatPollTimer=null;
const tripChatDrafts=new Map(),tripChatAttempts=new Map();
let tripAlertAudio=null;
function statusName(s){return states[s]||s||"Sin estado";}
function tripMoney(n){return new Intl.NumberFormat("es-UY",{style:"currency",currency:"UYU",maximumFractionDigits:0}).format(n);}
function tripDateTime(t){
  if(!t)return "";
  const date=new Date(t);
  return Number.isNaN(date.getTime())?"":date.toLocaleString("es-UY",{timeZone:"America/Montevideo",day:"2-digit",month:"2-digit",hour:"2-digit",minute:"2-digit"});
}
function tripLine(parent,icon,label){
  const el=text(parent,"div","","trip-detail-line");
  text(el,"span",icon).setAttribute("aria-hidden","true");text(el,"span",label);
}
function tripProgress(parent,meta){
  const stages=["Solicitud","Presupuesto","Confirmación","En viaje","Finalizada"],ranks=[0,1,2,4,5];
  const rail=text(parent,"div","","trip-steps");
  rail.setAttribute("aria-label","Etapa actual: "+meta.label);
  stages.forEach((stage,i)=>{
    const el=text(rail,"div","","trip-step");
    const rank=ranks[i];
    if(rank<meta.rank)el.classList.add("completed");
    else if(rank===meta.rank||(meta.rank===3&&rank===4))el.classList.add("active");
    text(el,"div","","trip-step-line");text(el,"small",stage);
  });
}
function tripTimeline(parent,r){
  const events=text(parent,"div","","trip-events");
  function event(name,time){
    if(!time)return;
    const line=text(events,"div","","trip-event");
    text(line,"span","","trip-event-dot");
    const info=text(line,"div",name);text(info,"time"," · "+tripDateTime(time));
  }
  event("Solicitud registrada",r.created_at);
  event("Presupuesto enviado",r.quote_sent_at);
  event("Presupuesto aceptado",r.quote_accepted_at);
  if(r.pickup_phase==="EN_CAMINO")event("Conductor en camino",r.pickup_phase_updated_at);
  if(r.pickup_phase==="EN_PUNTO")event("Conductor llegó",r.pickup_phase_updated_at);
  if(r.confirmed_at&&r.confirmed_at!==r.quote_accepted_at)event("Traslado confirmado",r.confirmed_at);
  // No atribuir un horario a EN_VIAJE o FINALIZADA si la API no lo proporciona.
  if(["EN_VIAJE","FINALIZADA","CANCELADA","RECHAZADA"].includes(r.status))
    text(events,"div","Estado actual: "+statusName(r.status),"trip-event");
}

function tripChatAuth(r,guestToken){
  return {
    p_reservation_id:r.id,
    p_session_token:guestToken?null:session?.token||null,
    p_public_token:guestToken||null
  };
}
function closeTripChat(){
  const sheet=$("trip-chat-sheet");
  if(!sheet||sheet.hidden)return;
  sheet.hidden=true;document.body.classList.remove("trip-chat-modal-open");
  if(tripChatPollTimer!==null){clearInterval(tripChatPollTimer);tripChatPollTimer=null;}
  activeTripChat=null;
  const prev=tripChatPreviouslyFocused;tripChatPreviouslyFocused=null;
  if(prev?.isConnected)prev.focus();
}
function openTripChat(r,guestToken,trigger){
  if(!r.id)return;
  const sheet=$("trip-chat-sheet"),content=$("trip-chat-dialog-content");
  if(!sheet||!content)return;
  if(sheet.hidden)tripChatPreviouslyFocused=trigger||document.activeElement;
  activeTripChat={id:r.id,code:r.code,token:guestToken||null,closed:["FINALIZADA","CANCELADA","RECHAZADA","RECHAZADA_CLIENTE"].includes(r.status)};
  $("trip-chat-dialog-status").textContent="Solicitud "+String(r.code||"")+" · "+statusName(r.status);
  clear(content);
  const feed=text(content,"div","Cargando mensajes…","trip-chat-feed");
  feed.setAttribute("role","log");feed.setAttribute("aria-live","polite");
  if(activeTripChat.closed){
    const archived=text(content,"p","Esta conversación finalizó. Podés consultar los mensajes anteriores.","trip-chat-archived");
    archived.setAttribute("role","status");
    tripChatDrafts.delete(r.code);
  }else{
    const row=text(content,"div","","trip-chat-compose");
    const input=text(row,"textarea","","trip-chat-input");
    input.placeholder="Escribí al conductor…";input.maxLength=800;input.rows=2;
    input.setAttribute("aria-label","Mensaje para el conductor");
    input.value=tripChatDrafts.get(r.code)||"";
    input.addEventListener("input",()=>tripChatDrafts.set(r.code,input.value));
    const send=button(row,"Enviar","trip-chat-send",()=>void sendTripChat(r,guestToken,feed,input,send));
    text(content,"p","Los mensajes se actualizan mientras Traslados está abierto.","trip-chat-hint");
  }
  sheet.hidden=false;document.body.classList.add("trip-chat-modal-open");
  $("trip-chat-dialog-close").focus();
  void loadTripChat(r,guestToken,feed);
  if(tripChatPollTimer!==null)clearInterval(tripChatPollTimer);
  tripChatPollTimer=null;
  // Consultar solo con chat visible, sesión activa, internet y app en primer plano.
  // La agenda sigue con su propio intervalo: mensajes nuevos no esperan su refresco.
  if(!activeTripChat.closed)tripChatPollTimer=setInterval(()=>{
    if(document.visibilityState!=="visible"||navigator.onLine===false||sheet.hidden)return;
    const conversation=activeTripChat;
    if(!conversation||conversation.closed)return;
    const current=tripRows.find(row=>String(row.id)===String(conversation.id));
    const currentFeed=content.querySelector(".trip-chat-feed");
    if(current&&currentFeed)void loadTripChat(current,conversation.token,currentFeed);
  },6000);
}
function mountTripChat(parent,r,guestToken){
  if(!r.id)return;
  const closed=["FINALIZADA","CANCELADA","RECHAZADA","RECHAZADA_CLIENTE"].includes(r.status);
  const root=text(parent,"div","","trip-chat");
  root.dataset.code=r.code||"";
  const trigger=button(root,closed?"✉ Ver mensajes archivados":"✉ Conversar con el conductor","trip-chat-open",()=>{
    openTripChat(r,guestToken,trigger);
  });
  const annotation=text(root,"span",closed?"Solo lectura":"Mensajes privados","trip-chat-arrow");
  annotation.setAttribute("aria-hidden","true");
}
async function loadTripChat(r,guestToken,feed){
  if(!feed?.isConnected||feed.dataset.loading==="1")return;
  feed.dataset.loading="1";
  try{
    const messages=await rpc("customer_trip_chat_v1",{...tripChatAuth(r,guestToken),p_after_id:0});
    if(!feed.isConnected||$("trip-chat-sheet").hidden||
       String(activeTripChat?.id)!==String(r.id))return;
    // Una consulta sin mensajes nuevos no debe borrar, repintar ni desplazar el chat.
    const items=Array.isArray(messages)?messages:[];
    const signature=JSON.stringify(items.map(m=>[m.id,m.sender_role,m.body,m.created_at]));
    if(feed.dataset.signature===signature)return;
    const initial=!feed.dataset.signature;
    const following=feed.scrollHeight-feed.scrollTop-feed.clientHeight<=72;
    clear(feed);
    feed.dataset.signature=signature;
    if(!items.length){
      text(feed,"p","Todavía no hay mensajes. Podés consultar los detalles de tu traslado.","trip-chat-empty");
    }else{
      for(const item of items){
        const bubble=text(feed,"div","","trip-chat-bubble");
        bubble.classList.add(item.sender_role==="customer"?"from-customer":"from-driver");
        text(bubble,"small",item.sender_role==="customer"?"Vos":"Conductor");
        text(bubble,"span",item.body||"");
        const when=tripDateTime(item.created_at);
        if(when)text(bubble,"time",when);
      }
      if(initial||following)feed.scrollTop=feed.scrollHeight;
    }
  }catch(e){
    // Una falla temporal no debe borrar la conversación que ya está visible.
    if(feed.isConnected&&!feed.dataset.signature){
      clear(feed);text(feed,"p","No se pudieron actualizar los mensajes. Intentá nuevamente.","trip-chat-empty");
    }
  }
  finally{feed.dataset.loading="0";}
}
async function sendTripChat(r,guestToken,feed,input,send){
  const body=input.value.trim();
  if(!body||body.length>800||send.disabled)return;
  const key=r.code||r.id,attempt=tripChatAttempts.get(key);
  const requestId=attempt?.body===body?attempt.id:newClientRequestId();
  tripChatAttempts.set(key,{body,id:requestId});
  send.disabled=true;
  try{
    const response=await rpc("customer_trip_chat_send_v1",{
      ...tripChatAuth(r,guestToken),p_body:body,p_request_id:requestId
    });
    if(!response?.id)throw new Error("El servidor no confirmó el mensaje");
    tripChatAttempts.delete(key);tripChatDrafts.delete(key);
    input.value="";
    tell("Mensaje enviado al conductor");
    await loadTripChat(r,guestToken,feed);
  }catch(e){showError(e);}
  finally{send.disabled=false;}
}
function armTripSound(){
  if(!$("trips-alert-sound").checked)return;
  try{
    const AudioClass=window.AudioContext||window.webkitAudioContext;
    if(!AudioClass)return;
    tripAlertAudio??=new AudioClass();
    void tripAlertAudio.resume();
  }catch{}
}
function playTripAlertSound(){
  if(!$("trips-alert-sound").checked||!tripAlertAudio||tripAlertAudio.state!=="running")return;
  try{
    const ctx=tripAlertAudio;
    for(const [n,frequency] of [[0,740],[1,980]]){
      const oscillator=ctx.createOscillator(),gain=ctx.createGain(),at=ctx.currentTime+n*.16;
      oscillator.type="sine";oscillator.frequency.value=frequency;
      gain.gain.setValueAtTime(.0001,at);
      gain.gain.exponentialRampToValueAtTime(.055,at+.018);
      gain.gain.exponentialRampToValueAtTime(.0001,at+.22);
      oscillator.connect(gain).connect(ctx.destination);
      oscillator.start(at);oscillator.stop(at+.23);
    }
  }catch{}
}

function reservationView(r,guestToken){
  const meta=tripBoard.metadata(r),price=Number(r.quote_final_total||0);
  const quoteExpired=tripBoard.expiredQuote(r);
  const immediateExpired=tripBoard.expiredImmediate(r);
  const expired=quoteExpired||immediateExpired;
  const d=document.createElement("details");d.className="reservation trip-card";
  d.dataset.tripCode=r.code||"";d.dataset.tone=meta.tone;d.open=openTripCards.has(r.code);
  d.addEventListener("toggle",()=>{if(d.open)openTripCards.add(r.code);else openTripCards.delete(r.code);});
  const top=text(d,"summary"),head=text(top,"div","","trip-head");
  const date=text(head,"div","","trip-date-wrap");
  text(date,"span","▣","trip-date-badge").setAttribute("aria-hidden","true");
  const v=text(date,"div");
  text(v,"strong",tripBoard.compactDay(r.pickup_date)+" · "+String(r.pickup_time||"").slice(0,5));
  text(v,"small",r.code||"Solicitud");
  const badge=text(head,"span","","trip-status");text(badge,"span","","trip-status-dot");text(badge,"span",meta.label);
  if(meta.tone==="moving"){
    // La fase validada por servidor es prioritaria: visible incluso con tarjeta cerrada.
    const onBoard=r.status==="EN_VIAJE";
    const arrived=r.pickup_phase==="EN_PUNTO"||r.status==="EN_PUNTO";
    const live=text(top,"div","","trip-live-spotlight");
    live.dataset.stage=onBoard?"trip":arrived?"arrived":"approach";
    live.setAttribute("role","status");
    text(live,"span",onBoard?"◆":arrived?"✓":"●","trip-live-symbol").setAttribute("aria-hidden","true");
    const copy=text(live,"div","","trip-live-copy");
    text(copy,"strong",onBoard?"Tu viaje está en curso":arrived?"Tu conductor llegó":"Tu conductor está en camino");
    text(copy,"span",onBoard?"El traslado ya comenzó.":arrived?"Está en el punto de recogida.":"Va hacia el lugar donde te recogerá.");
    if(publicVehiclePlate)text(copy,"span","Matrícula · "+publicVehiclePlate,"trip-live-vehicle-plate");
  }
  const route=text(top,"div","","trip-route");
  function stop(icon,value){
    const row=text(route,"div","","trip-route-stop");
    text(row,"span",icon,"trip-route-icon").setAttribute("aria-hidden","true");
    text(row,"span",String(value||""),"trip-route-label");
  }
  stop("◉",r.origin_text||"Origen");
  stop(/aeropuerto/i.test(r.destination_text||"")?"✈":"◆",r.destination_text||"Destino");
  const foot=text(top,"div","","trip-card-footer");
  const amount=text(foot,"span",price>0?tripMoney(price):"Importe pendiente",price>0?"trip-card-quote":"trip-card-prompt");
  if(price>0)amount.setAttribute("aria-label","Presupuesto "+tripMoney(price));
  const affordance=meta.tone==="attention"?"Responder presupuesto":meta.tone==="moving"?"Ver seguimiento":"Ver detalles";
  text(foot,"span",affordance,"trip-card-open");

  const inner=text(d,"div","","trip-detail");
  const guidance=tripBoard.guidance(r);
  text(inner,"p",guidance,"trip-focus-message");
  if(quoteExpired)text(inner,"p","⌛ Horario vencido. No es posible aceptar este presupuesto para la recogida anterior.","trip-expired-note");
  else if(immediateExpired)text(inner,"p","⌛ El horario de esta solicitud inmediata ya venció sin confirmación. Si necesitás viajar, pedí una nueva recogida.","trip-expired-note");

  // One meaningful decision at a time, before secondary history and details.
  const actions=text(inner,"div","","action-row trip-primary-actions");
  if(expired)button(actions,"Solicitar nuevo traslado","primary",()=>{
    // Always prepare the form; never create the replacement automatically.
    enterService(tripBoard.isImmediateRequest(r)?"now":"schedule");
    bookingSteps.open("when",{scroll:true});
  });
  if(!expired&&price>0&&r.quote_status==="ENVIADO"&&r.status==="PENDIENTE"&&((authReady()&&r.id)||guestToken)){
    button(actions,"Aceptar "+tripMoney(price),"primary",()=>void decideQuote(r.id,true,guestToken));
    button(actions,"Rechazar presupuesto","secondary",()=>void decideQuote(r.id,false,guestToken));
  }
  if(!actions.children.length)actions.remove();

  const info=text(inner,"details","","trip-detail-disclosure");
  info.open=openTripDetails.has(String(r.code||"")+":info");
  text(info,"summary","Datos del traslado","trip-disclosure-head");
  const infoBody=text(info,"div","","trip-disclosure-body");
  tripLine(infoBody,"◉",r.origin_text||"Origen");
  for(const s of Array.isArray(r.stops)?r.stops:[])tripLine(infoBody,"•",s.address_text||"Parada");
  tripLine(infoBody,"◆",r.destination_text||"Destino");
  tripLine(infoBody,"◷",tripBoard.compactDay(r.pickup_date)+" · "+String(r.pickup_time||"").slice(0,5)+" h");
  tripLine(infoBody,"♙",Number(r.passengers||1)+" pasajero(s)");
  if(r.comments)tripLine(infoBody,"✎",r.comments);
  const includes=String(r.quote_includes??"").trim();
  if(includes&&!/^(null|undefined|n\/a|-)$/i.test(includes))
    text(infoBody,"p","Incluye: "+includes,"trip-detail-note");
  if(!price)text(infoBody,"p","El importe todavía no fue confirmado por el conductor.","trip-detail-note");

  const historical=text(inner,"details","","trip-detail-disclosure trip-history-disclosure");
  historical.open=openTripDetails.has(String(r.code||"")+":history");
  text(historical,"summary","Historial y etapas del viaje","trip-disclosure-head");
  const historyBody=text(historical,"div","","trip-disclosure-body");
  tripProgress(historyBody,meta);
  tripTimeline(historyBody,r);
  mountTripChat(inner,r,guestToken);

  if(guestToken&&["PENDIENTE","ACEPTADA"].includes(r.status))
    button(inner,"Cancelar solicitud","trip-cancel-action",async()=>{
      if(!confirm("¿Cancelar esta solicitud?"))return;
      try{await rpc("cancel_reservation_by_token",{p_token:guestToken});tell("Solicitud cancelada");void loadTrips();}catch(e){showError(e);}
    });
  if(authReady()&&r.id&&["PENDIENTE","ACEPTADA"].includes(r.status))
    button(inner,"Cancelar solicitud","trip-cancel-action",async()=>{
      if(!confirm("¿Cancelar esta solicitud?"))return;
      try{await rpc("customer_cancel_reservation",{p_session_token:session.token,p_reservation_id:r.id});tell("Solicitud cancelada");void loadTrips();}catch(e){showError(e);}
    });
  return d;
}
async function decideQuote(id,accept,guestToken=null){
  if(!confirm(accept?"¿Aceptás el presupuesto definitivo?":"¿Rechazás este presupuesto?"))return;
  try{
    const result=await rpc("customer_quote_decision_v13",{
      p_reservation_id:id||null,p_accept:accept,
      p_session_token:guestToken?null:session?.token||null,
      p_public_token:guestToken||null
    });
    if(!result?.code)throw new Error("No recibimos confirmación de la respuesta. Actualizá Mis traslados.");
    tell(accept?"Presupuesto aceptado. Tu traslado quedó confirmado.":"Presupuesto rechazado.");
    await loadTrips();
  }catch(e){showError(e);await loadTrips();}
}
function closeTripNotice(){
  $("trip-alert-overlay").hidden=true;
  tripFocused=null;
  tripPreviouslyFocused?.focus?.();tripPreviouslyFocused=null;
}
function showTripNotice(change){
  if(document.visibilityState!=="visible"||!$("trip-alert-overlay").hidden||
      !$("trip-chat-sheet").hidden)return;
  const quote=change.kind==="quote",message=change.kind==="message";
  tripFocused=change.row.code;tripPreviouslyFocused=document.activeElement;
  $("trip-alert-title").textContent=quote?"Tenés un presupuesto nuevo":message?"Nuevo mensaje del conductor":"Tu traslado se actualizó";
  $("trip-alert-description").textContent=quote
    ?"El conductor envió un presupuesto de "+tripMoney(Number(change.row.quote_final_total))+". Revisalo y respondé desde tu reserva."
    :message?"El conductor escribió en la reserva "+change.row.code+". Entrá para leer y responder.":"El estado de "+change.row.code+" cambió a "+tripBoard.metadata(change.row).label+".";
  $("trip-alert-overlay").hidden=false;$("trip-alert-close").focus();
  if($("trips-alert-vibrate").checked&&navigator.vibrate)navigator.vibrate([90,65,90]);
  playTripAlertSound();
}
function renderTripsBoard({reset=false}={}){
  const list=$("trips-list");
  if(!reset&&list.children.length){
    openTripGroups=new Set([...list.querySelectorAll(".trips-day[open]")].map(x=>x.dataset.key));
    openTripCards=new Set([...list.querySelectorAll(".trip-card[open]")].map(x=>x.dataset.tripCode));
    openTripDetails=new Set([...list.querySelectorAll(".trip-card .trip-detail-disclosure[open]")].map(x=>{
      const code=x.closest(".trip-card")?.dataset.tripCode;
      if(!code)return null;
      return code+":"+(x.classList.contains("trip-history-disclosure")?"history":"info");
    }).filter(Boolean));
  }
  clear(list);
  const attention=tripRows.filter(r=>tripBoard.metadata(r).tone==="attention").length;
  $("trips-attention-count").hidden=attention===0;$("trips-attention-count").textContent=String(attention);
  const focus=$("trips-focus");
  if(focus){
    const liveTrip=tripRows.find(r=>tripBoard.metadata(r).tone==="moving");
    const liveVisible=Boolean(liveTrip&&!["history","attention"].includes(tripsScope));
    focus.classList.toggle("is-live",liveVisible&&attention===0);
    focus.textContent=attention>0
      ?"Tenés "+attention+" presupuesto"+(attention===1?"":"s")+" para responder. Entrá en Responder para continuar."
      :liveVisible?tripBoard.metadata(liveTrip).label+" · "+tripBoard.guidance(liveTrip)
      :tripsScope==="history"?"Historial de servicios. Abrí un traslado si necesitás consultar sus detalles."
      :tripsScope==="all"?"Viendo todos tus traslados, incluidos los finalizados."
      :"Tus próximas recogidas y solicitudes pendientes se muestran aquí.";
  }
  const groups=tripBoard.groupReservations(tripRows,{scope:tripsScope,groupBy:tripsGroupBy,status:tripsFilter});
  if(!groups.length){
    text(list,"div",tripRows.length?"No hay traslados que coincidan con estos filtros.":"Todavía no hay reservas para mostrar.","trip-empty");return;
  }
  groups.forEach((g,i)=>{
    const day=document.createElement("details");day.className="trips-day";day.dataset.key=g.key;
    day.open=openTripGroups.size?openTripGroups.has(g.key):i<2;
    const head=text(day,"summary");
    const title=text(head,"span","","trips-day-caption");
    text(title,"span","▦").setAttribute("aria-hidden","true");
    text(title,"span",g.label);
    text(head,"span",g.items.length+" traslado(s)","trips-day-count");
    text(head,"span","","trips-day-chevron").setAttribute("aria-hidden","true");
    const body=text(day,"div","","trips-day-content");
    g.items.forEach(r=>body.appendChild(reservationView(r,r.__guestToken||null)));
    list.appendChild(day);
  });
}
async function loadTrips({background=false}={}){
  if(tripsLoading||!navigator.onLine)return;
  const requestEpoch=tripQueryEpoch,requestSession=authReady()?session.token:null;
  tripsLoading=true;
  const status=$("trips-status");if(!background&&!tripLoaded)status.textContent="Consultando tus traslados…";
  try{
    let rows=[];
    if(requestSession){
      const result=await rpc("customer_list_reservations_v15",{p_session_token:requestSession});
      rows=Array.isArray(result)?result:[];
    }else{
      const guests=jsonStorage(GUEST_KEY,[]);
      const result=await Promise.allSettled(guests.map(g=>rpc("get_reservation_by_token_v4",{p_token:g.token})));
      result.forEach((x,i)=>{if(x.status==="fulfilled"&&x.value)rows.push({...x.value,__guestToken:guests[i].token});});
    }
    if(requestEpoch!==tripQueryEpoch)return; // Session changed during network request.
    const nextRenderKey=tripDisplayKey(rows);
    const needsRender=!tripLoaded||nextRenderKey!==tripRenderedSignature;
    const updates=tripSnap?tripBoard.notifications(tripSnap,rows):[];
    tripRows=rows;tripLoaded=true;tripSnap=tripBoard.snapshot(rows);
    tripRenderedSignature=nextRenderKey;
    if(!tripsScopeChosen&&rows.length){
      const actionable=rows.some(r=>tripBoard.metadata(r).tone==="attention");
      const upcoming=rows.some(r=>!tripBoard.isHistory(r));
      tripsScope=actionable?"attention":upcoming?"upcoming":"history";
      for(const tab of document.querySelectorAll("[data-trips-scope]")){
        const active=tab.dataset.tripsScope===tripsScope;
        tab.classList.toggle("active",active);tab.setAttribute("aria-pressed",String(active));
      }
      if(actionable&&!openTripCards.size){
        const wanted=rows.find(r=>tripBoard.metadata(r).tone==="attention");
        if(wanted?.code)openTripCards.add(wanted.code);
      }
    }
    if(activeTripChat){
      const newest=rows.find(x=>x.id===activeTripChat.id);
      if(newest){
        const closed=["FINALIZADA","CANCELADA","RECHAZADA","RECHAZADA_CLIENTE"].includes(newest.status);
        if(closed!==activeTripChat.closed)openTripChat(newest,newest.__guestToken||null,tripChatPreviouslyFocused);
        else if(updates.some(change=>change.kind==="message"&&change.row.id===newest.id)){
          const feed=$("trip-chat-dialog-content")?.querySelector(".trip-chat-feed");
          if(feed)void loadTripChat(newest,newest.__guestToken||null,feed);
        }
      }
    }
    if(needsRender)renderTripsBoard();
    if(selectedPickupMode!=="schedule")void refreshPickupPresence();
    status.textContent=rows.length+" traslado(s) · actualizado a las "+
      new Date().toLocaleTimeString("es-UY",{hour:"2-digit",minute:"2-digit"})+".";
    if(updates.length)showTripNotice(updates[0]);
  }catch(e){
    if(requestEpoch!==tripQueryEpoch)return;
    status.textContent="No se pudo actualizar. Conservamos la última información.";
    if(!background)showError(e);
  }finally{if(requestEpoch===tripQueryEpoch)tripsLoading=false;}
}

// La instalación automática depende del navegador: no hay API para abrir su menú
// cuando beforeinstallprompt no se emitió. La alternativa siempre debe ser visible.
function isStandaloneTraslados(){
  // Cliente Android v14/v15/v16 abre esta misma web con WebView nativa.
  // Nunca ofrecer otra APK o instalación PWA desde dentro de esa misma APK.
  return Boolean(/TrasladosClienteNative\/(?:14|15|16)/i.test(navigator.userAgent||"")||
    window.matchMedia?.("(display-mode: standalone)")?.matches||navigator.standalone===true);
}
function installHelpContent(){
  const agent=navigator.userAgent||"";
  const ios=/iPad|iPhone|iPod/i.test(agent);
  const samsung=/SamsungBrowser/i.test(agent);
  const android=/Android/i.test(agent);
  if(ios)return {
    title:"Instalación desde Safari",
    steps:["Abrí esta web en Safari.","Tocá Compartir (cuadrado con flecha).","Elegí «Agregar a pantalla de inicio» y confirmá."],
    note:"El instalador no puede abrirse automáticamente desde esta pantalla."
  };
  if(samsung)return {
    title:"Instalación desde Samsung Internet",
    steps:["Abrí el menú ☰ de Samsung Internet.","Buscá «Agregar página a» o «Añadir a pantalla de inicio».","Seleccioná Pantalla de inicio y confirmá."],
    note:"La opción depende de la versión del navegador."
  };
  if(android)return {
    title:"Instalación desde Chrome",
    steps:["Abrí Traslados directamente en Chrome (no dentro de WhatsApp, Facebook u otra aplicación).","Tocá el menú ⋮ de Chrome con la página de Traslados abierta.","Elegí «Instalar y crear acceso directo» → «Instalar». En Chrome antiguo puede decir «Instalar aplicación» o «Agregar a pantalla principal»."],
    note:"Elegí «Instalar» si querés que Traslados se abra como aplicación independiente. «Crear acceso directo» solo agrega un ícono para abrir Chrome. Si no aparece «Instalar», revisá el navegador y si ya está instalada: no se puede forzar desde esta página."
  };
  return {
    title:"Agregar Traslados a tu dispositivo",
    steps:["Abrí este enlace en Chrome, Edge o Safari.","Buscá «Instalar aplicación» en el menú del navegador.","Confirmá para crear tu acceso directo."],
    note:"La opción de instalación depende del navegador y del dispositivo."
  };
}
function installUI(){
  const action=$("install-action"),info=$("install-info"),guide=$("install-guide");
  const guideTitle=$("install-guide-title"),guideSteps=$("install-guide-steps"),guideNote=$("install-guide-note");
  const copyButton=$("install-copy-link");
  const apkOption=$("install-apk-option"),apkLink=$("install-apk-link");
  const apkGuideButton=$("install-apk-web-guide");
  const catalog=window.TrasladosPublicConfig;
  const android=/Android/i.test(navigator.userAgent||"");
  let apkReady=false,installConfirmed=false;
  function applyPublishedApk(){
    if(!catalog)return;
    const published=catalog.get();
    if(!published?.clientApkUrl)return;
    const version=String(published.clientVersion||"");
    if(!/^[1-9][0-9]*$/.test(version))return;
    if(apkLink.href===published.clientApkUrl)return;
    apkLink.href=published.clientApkUrl;
    apkLink.download="Traslados_Cliente_Premium_v"+version+"_RELEASE.apk";
    const hint=$("install-apk-hint");
    if(hint)hint.textContent="Cliente Premium v"+version+
      ", firmada y con la misma interfaz de Traslados con Reserva. Las reservas necesitan conexión a Internet. Android puede pedir autorización para instalar aplicaciones descargadas.";
    apkReady=false;
    updateUI();
    void checkVerifiedApk();
  }
  const isReady=()=>Boolean(installEvent);
  // No suponer que una URL existe: comprobar HEAD y tipo binario antes de
  // mostrar el botón de descarga; nunca dar un enlace al workflow de GitHub.
  async function checkVerifiedApk(){
    if(!android||isStandaloneTraslados())return;
    const candidate=apkLink.href;
    let verified=false;
    try{
      const response=await fetch(candidate,{method:"HEAD",cache:"no-store"});
      const type=(response.headers.get("content-type")||"").toLowerCase();
      const length=Number(response.headers.get("content-length")||"0");
      verified=response.ok&&length>10000&&!/text\/html|application\/json|text\/plain/.test(type);
    }catch{verified=false;}
    // An old HEAD reply must never re-enable a superseded APK link.
    if(apkLink.href!==candidate)return;
    apkReady=verified;
    updateUI();
  }
  function showGuide(){
    if(!guide)return;
    const content=installHelpContent();
    guide.hidden=false;
    guideTitle.textContent=content.title;
    guideSteps.replaceChildren();
    for(const step of content.steps)text(guideSteps,"li",step);
    guideNote.textContent=content.note;
  }
  function updateUI(){
    // Mostrar siempre APK como opción secundaria si el dispositivo es Android.
    // No ocultarla porque Chrome también permite instalar la PWA.
    apkOption.hidden=!(android&&apkReady)||isStandaloneTraslados();
    if(isStandaloneTraslados()){
      action.textContent="ABRIR MIS TRASLADOS";
      info.textContent="Traslados ya está abierto como aplicación. No necesitás instalarlo otra vez.";
      guide.hidden=true;
    }else if(installConfirmed){
      action.textContent="INSTALACIÓN SOLICITADA";
      guide.hidden=true;
    }else if(isReady()){
      action.textContent="INSTALAR TRASLADOS";
      info.textContent="Chrome permite instalar Traslados como aplicación web. Tocá para confirmar.";
      guide.hidden=true;
    }else if(android&&apkReady){
      action.textContent="DESCARGAR TRASLADOS PARA ANDROID";
      info.textContent="Chrome no ofreció instalación web automática. Podés instalar nuestra app nativa Cliente o consultar cómo agregar la web al inicio.";
      apkOption.hidden=false;
    }else{
      action.textContent="VER CÓMO INSTALAR";
      info.textContent="Si el navegador no ofrece instalar automáticamente, podés agregar Traslados desde su menú.";
    }
  }
  window.addEventListener("beforeinstallprompt",e=>{
    e.preventDefault();
    installEvent=e;
    updateUI();
  });
  window.addEventListener("appinstalled",()=>{
    installEvent=null;
    installConfirmed=true;
    action.textContent="ABRIR MIS TRASLADOS";
    info.textContent="Traslados se agregó a tu dispositivo. Buscá su ícono en la pantalla de inicio.";
    guide.hidden=true;
  });
  action.addEventListener("click",async()=>{
    if(isStandaloneTraslados()){shiftView("viajes");return;}
    if(installConfirmed){info.textContent="Instalación solicitada. Buscá el ícono de Traslados en el inicio.";return;}
    if(!installEvent){
      if(android&&apkReady){
        // El enlace real es el de esta misma web, a una APK cuya presencia
        // y tamaño ya fueron comprobados. El usuario inicia la descarga.
        apkLink.click();
        info.textContent="Descarga solicitada. Android puede pedir autorización para instalar la aplicación.";
        return;
      }
      showGuide();return;
    }
    const pending=installEvent;
    installEvent=null; // Un evento de instalación no se reutiliza.
    action.disabled=true;
    try{
      await pending.prompt();
      const choice=await pending.userChoice;
      if(choice?.outcome==="accepted"){
        installConfirmed=true;
        info.textContent="Confirmaste la instalación. Buscá el ícono de Traslados en el inicio.";
        guide.hidden=true;
      }else{
        info.textContent="No se completó la instalación. Podés volver cuando quieras.";
        showGuide();
      }
    }catch{
      info.textContent="El navegador no abrió la instalación automática. Probá desde su menú.";
      showGuide();
    }finally{
      action.disabled=false;
      if(!isStandaloneTraslados()&&!isReady()&&!installConfirmed)updateUI();
    }
  });
  copyButton.addEventListener("click",async()=>{
    const link=new URL("./",window.location.href).href;
    try{
      await navigator.clipboard.writeText(link);
      info.textContent="Enlace de Traslados copiado. Pegalo en la barra de Chrome para abrirlo allí.";
    }catch{
      info.textContent="Abrí esta misma dirección directamente en Chrome para instalar Traslados.";
    }
  });
  apkGuideButton.addEventListener("click",showGuide);
  updateUI();
  applyPublishedApk(); // Apply any valid cached remote release immediately.
  catalog?.subscribe?.(applyPublishedApk);
  void catalog?.refresh?.(); // Bounded background fetch, never blocks booking.
  void checkVerifiedApk();
  if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self){
    const register=()=>navigator.serviceWorker.register("./sw.js",{scope:"./"}).catch(()=>{});
    if(document.readyState==="complete")void register();
    else window.addEventListener("load",register,{once:true});
  }
}
function init(){
  session=jsonStorage(SESSION_KEY,null);
  void refreshPublicVehiclePlate();
  bookingSteps=window.TrasladosBookingSteps.mount(document);
  // Service cards are navigation-only; the existing native/backend contracts
  // keep full responsibility for preparing and sending transport requests.
  window.TrasladosServiceHome.mount(document,{
    onSelect:kind=>enterService(kind),
    onNavigate:target=>target==="reservar"?enterService("schedule"):shiftView(target)
  });
  for(const tab of document.querySelectorAll(".tab"))tab.addEventListener("click",()=>tab.dataset.view==="reservar"?enterService("schedule"):shiftView(tab.dataset.view));
  $("journey-when-select").addEventListener("change",()=>{
    const mode=$("journey-when-select").value;
    if(mode==="custom"){$("journey-clock-action").click();return;}
    if(mode!=="now"&&!/^(10|15|20|30|45|60)$/.test(mode))return;
    setWhen(mode);
  });
  $("journey-clock-action").addEventListener("click",()=>{
    const input=$("journey-clock-input");
    input.hidden=false;
    input.focus({preventScroll:true});
    try{if(typeof input.showPicker==="function")input.showPicker();}catch{}
  });
  $("journey-clock-input").addEventListener("change",()=>{
    const time=$("journey-clock-input").value;
    if(!/^([01]\\d|2[0-3]):[0-5]\\d$/.test(time))return;
    const today=currentUY();
    let target=Date.parse(today.day+"T"+time+":00-03:00");
    let delay=Math.ceil((target-Date.now())/60000);
    if(delay<1||delay>120){
      tell("Tu viaje permite elegir una recogida dentro de las próximas 2 horas. Para más adelante, usá Tu reserva.");
      $("journey-when-select").value=["now","10","15","20","30","45","60"].includes(selectedPickupMode)?selectedPickupMode:"custom";
      return;
    }
    setWhen(String(delay));
    $("journey-when-select").value="custom";
    $("journey-custom-time-label").textContent="Recogida solicitada hacia las "+time+" h (sujeta a confirmación)";
    $("journey-custom-time-label").hidden=false;
    input.hidden=true;
  });
  document.querySelectorAll("[data-open-login]").forEach(x=>x.addEventListener("click",()=>shiftView("cuenta")));
  $("pickup-diagnostics").addEventListener("toggle",()=>{
    if($("pickup-diagnostics").open)refreshPickupDiagnosticsPreview();
  });
  $("pickup-diagnostics-copy").addEventListener("click",()=>void copyPickupDiagnostics());
  $("pickup-diagnostics-clear").addEventListener("click",()=>{
    window.TrasladosPickupHealth?.clear?.();
    refreshPickupDiagnosticsPreview();
    tell("Diagnóstico local borrado.");
  });
  document.querySelectorAll("[data-when]").forEach(b=>b.addEventListener("click",()=>setWhen(b.dataset.when)));
  // Bounded refresh only while a foreground visitor requests an urgent pickup.
  pickupEtaTimer=setInterval(()=>{
    if(selectedPickupMode!=="schedule"&&document.visibilityState==="visible")
      void refreshPickupPresence(true);
  },15000);
  setWhen("schedule");const tomorrow=currentUY(60);
  $("pickup-date").value=tomorrow.day;$("pickup-date").min=currentUY().day;$("pickup-time").value=tomorrow.time;
  originPicker=picker("origin",$("origin-picker"),0);
  // La ubicación del pasajero se solicita solo al pulsar el botón (permiso explícito).
  $("pickup-use-location").addEventListener("click",()=>void originPicker.useCurrent());
  destPicker=picker("destination",$("destination-picker"),0);
  $("add-stop").addEventListener("click",()=>{if(record.stops.length>=8){tell("Hasta 8 paradas");return;}record.stops.push(null);renderStops();renderRouteSummary();refreshBookingSteps();});
  for(const key of ["pickup-date","pickup-time","passengers","person-name","person-phone","comments"]){
    $(key).addEventListener("input",refreshBookingSteps);
    $(key).addEventListener("change",refreshBookingSteps);
  }
  $("passenger-view-account").addEventListener("click",()=>shiftView("cuenta"));
  $("urgent-send").addEventListener("click",()=>void submitUrgentPickup());
  $("pickup-existing-open").addEventListener("click",()=>{
    const code=$("pickup-existing-open").dataset.code;
    if(!code)return;
    tripsScope="all";tripsFilter="all";tripsScopeChosen=true;
    $("trips-status-filter").value="all";
    for(const tab of document.querySelectorAll("[data-trips-scope]")){
      const active=tab.dataset.tripsScope==="all";
      tab.classList.toggle("active",active);tab.setAttribute("aria-pressed",String(active));
    }
    openTripGroups.clear();openTripCards.add(code);
    shiftView("viajes");
    if(tripLoaded)renderTripsBoard({reset:true});
    const card=[...document.querySelectorAll("#trips-list .trip-card")].find(x=>x.dataset.tripCode===code);
    if(card){card.closest(".trips-day").open=true;card.open=true;}
  });
  $("booking-fix-when").addEventListener("click",()=>{
    bookingSteps.open("when",{scroll:true});
    $("pickup-date").focus({preventScroll:true});
  });
  for(const id of ["pickup-date","pickup-time"]){
    $(id).addEventListener("change",()=>{
      $("booking-when-error").hidden=true;
      $("booking-send-error").hidden=true;
      $("booking-fix-when").hidden=true;
    });
  }
  $("when-next").addEventListener("click",()=>{
    if(selectedPickupMode!=="schedule")return;
    const d=$("pickup-date").value,t=$("pickup-time").value;
    if(!d||!t||Date.parse(d+"T"+t+":00-03:00")<Date.now()-20000){
      tell("Elegí una fecha y hora futuras para continuar.");
      bookingSteps.open("when");$("pickup-date").focus();return;
    }
    bookingSteps.open("passenger",{scroll:true});
  });
  $("review-booking").addEventListener("click",showPreview);
  $("edit-booking").addEventListener("click",()=>{pendingReview=null;$("booking-preview").hidden=true;$("booking-final").hidden=false;$("review-booking").hidden=false;$("submit-actions").hidden=true;bookingSteps.open("passenger",{scroll:true});});
  $("confirm-booking").addEventListener("click",()=>void submitBooking());
  $("refresh-trips").addEventListener("click",()=>void loadTrips());
  for(const tab of document.querySelectorAll("[data-trips-scope]"))tab.addEventListener("click",()=>{
    tripsScope=tab.dataset.tripsScope;tripsScopeChosen=true;
    tripsFilter="all";$("trips-status-filter").value="all";
    for(const el of document.querySelectorAll("[data-trips-scope]")){
      const active=el===tab;el.classList.toggle("active",active);el.setAttribute("aria-pressed",String(active));
    }
    openTripGroups.clear();renderTripsBoard({reset:true});
  });
  $("trips-group-by").addEventListener("change",()=>{tripsGroupBy=$("trips-group-by").value;openTripGroups.clear();renderTripsBoard({reset:true});});
  $("trips-status-filter").addEventListener("change",()=>{tripsFilter=$("trips-status-filter").value;openTripGroups.clear();renderTripsBoard({reset:true});});
  $("trips-alert-vibrate").checked=jsonStorage("traslados.vibrate-prefs.v1",false)===true;
  $("trips-alert-vibrate").addEventListener("change",()=>saveStorage("traslados.vibrate-prefs.v1",$("trips-alert-vibrate").checked));
  $("trips-alert-sound").checked=jsonStorage("traslados.sound-prefs.v1",false)===true;
  $("trips-alert-sound").addEventListener("change",()=>{
    saveStorage("traslados.sound-prefs.v1",$("trips-alert-sound").checked);
    armTripSound();
  });
  // Tras reabrir Traslados, Web Audio requiere un gesto real para reanudar.
  // Nunca reproducir sonidos sin el consentimiento guardado.
  document.addEventListener("pointerdown",armTripSound,{once:true});
  document.addEventListener("keydown",armTripSound,{once:true});
  $("trip-alert-close").addEventListener("click",closeTripNotice);
  $("trip-alert-open").addEventListener("click",()=>{
    const target=tripFocused;closeTripNotice();
    tripsScope="all";tripsFilter="all";$("trips-status-filter").value="all";
    for(const tab of document.querySelectorAll("[data-trips-scope]")){
      const yes=tab.dataset.tripsScope==="all";tab.classList.toggle("active",yes);tab.setAttribute("aria-pressed",String(yes));
    }
    openTripGroups.clear();openTripCards.add(target);shiftView("viajes");renderTripsBoard({reset:true});
    const card=[...document.querySelectorAll("#trips-list .trip-card")].find(x=>x.dataset.tripCode===target);
    if(card){card.closest(".trips-day").open=true;card.open=true;card.scrollIntoView({block:"center",behavior:"smooth"});}
  });
  $("trip-chat-dialog-close").addEventListener("click",closeTripChat);
  $("trip-chat-sheet").addEventListener("click",e=>{if(e.target===$("trip-chat-sheet"))closeTripChat();});
  document.addEventListener("keydown",e=>{
    const sheet=$("trip-chat-sheet");
    if(sheet.hidden)return;
    if(e.key==="Escape"){e.preventDefault();closeTripChat();return;}
    if(e.key!=="Tab")return;
    const controls=[...sheet.querySelectorAll("button:not([disabled]),textarea:not([disabled])")];
    if(!controls.length)return;
    const first=controls[0],last=controls[controls.length-1];
    if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}
    else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}
  });
  $("trip-alert-overlay").addEventListener("click",e=>{if(e.target===$("trip-alert-overlay"))closeTripNotice();});
  document.addEventListener("keydown",e=>{if(e.key==="Escape"&&!$("trip-alert-overlay").hidden)closeTripNotice();});
  let lastBackgroundTripsCheck=0;
  setInterval(()=>{
    if(!tripLoaded||navigator.onLine===false||document.visibilityState!=="visible")return;
    const inTrips=$("view-viajes")?.classList.contains("active");
    const activeTrip=inTrips&&tripRows.some(r=>!tripBoard.isHistory(r));
    const cadence=activeTrip?8000:20000;
    if(Date.now()-lastBackgroundTripsCheck<cadence)return;
    lastBackgroundTripsCheck=Date.now();
    void loadTrips({background:true});
  },4000);
  $("mode-login").addEventListener("click",()=>setAccountMode(false));
  $("mode-register").addEventListener("click",()=>setAccountMode(true));
  $("account-submit").addEventListener("click",()=>void accountSubmit());
  window.addEventListener("online",updateConnectivity);window.addEventListener("offline",updateConnectivity);
  syncAuthUI();setAccountMode(false);updateConnectivity();refreshBookingSteps();installUI();
  if(authReady()||jsonStorage(GUEST_KEY,[]).length)void loadTrips({background:true});
}
if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",init,{once:true});else init();
