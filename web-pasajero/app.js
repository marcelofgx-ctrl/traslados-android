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
function refreshBookingSteps(){
  if(!bookingSteps)return;
  const routeComplete=Boolean(record.origin&&record.destination&&record.stops.every(Boolean));
  let routeText=routeComplete?stepSummaryName(record.origin)+" → "+stepSummaryName(record.destination)
    :!record.origin?"Elegí origen y destino"
    :!record.destination?"Falta elegir destino":"Falta completar una parada";
  let routeMetrics="";
  if(routeComplete&&latestRoad?.key===routeKeyOf([record.origin,...record.stops,record.destination])&&
      window.TrasladosRoutes.verified(latestRoad.data)){
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
    (selectedPickupMode==="10"?"En 10 min":"Ahora")+" · "+passengerText;
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
  bookingSteps.open(step,{scroll:true});
  // Esperar al cambio de visibilidad antes de enfocar un campo plegado.
  if(target)setTimeout(()=>target.focus?.({preventScroll:true}),60);
}
const depts={origin:"Canelones",destination:"Montevideo"}; // Preferencia de orden; nunca filtra Uruguay
let session=null,registerMode=false,installEvent=null,pendingReview=null,busy=false,toastHandle=null;

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
  else if(/HORARIO_NO_DISPONIBLE/i.test(msg))
    msg="Ese horario dejó de estar disponible. Elegí otra hora para tu traslado.";
  tell(msg);
}
function loading(el,value){if(!el)return;el.disabled=value;el.dataset.original??=el.textContent;if(value)el.textContent="Procesando…";else{el.textContent=el.dataset.original;}}
function guardConnection(){if(!navigator.onLine)throw new Error("Estás sin Internet; no enviamos reservas offline.");}
async function rpc(name,payload={}){
  guardConnection();
  return window.TrasladosRpc.call({api:API,publishableKey:KEY,name,payload});
}
function updateConnectivity(){const offline=!navigator.onLine;$("offline-band").hidden=!offline;$("confirm-booking").disabled=offline;if(offline)tell("Sin conexión: no podés enviar reservas hasta reconectarte.");}
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
let publicPresenceCache=null,publicPresenceAt=0,publicPresencePending=null;
function forgetPickupEta(){
  lastPickupEtaKey="";lastPickupEtaAt=0;lastPickupEtaMessage="";
  lastPickupEtaIsEstimate=false;lastVerifiedEtaKey="";lastVerifiedEtaAt=0;lastVerifiedEtaMessage="";
  lastVerifiedEtaResult=null;
  // El enfriamiento de cuota pertenece al visitante, no al origen.
}
/** Una tarjeta con jerarquía visual: disponibilidad, km, minutos, información secundaria.
 * El lector de pantalla recibe un único resumen; nunca se muestra información
 * del servidor ni coordenadas del conductor.
 */
function paintPickupCard({presence,title,subtitle="",km=null,minutes=null,estimateNote="",checked=""}){
  const host=$("pickup-presence");
  if(host)host.dataset.presence=presence;
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
  if(status)status.textContent=[title,subtitle,hasEstimate?km.toLocaleString("es-UY",{maximumFractionDigits:1})+
    " km · "+window.TrasladosPickupCadence.arrivalLabel(minutes)+" aprox.":"",estimateNote,checked].filter(Boolean).join(" · ");
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
    const v=await window.TrasladosETA.availability({api:API,publishableKey:KEY});
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
  if(!urgent){pickupEtaRequestSeq++;host.removeAttribute("data-presence");return;}
  const label=selectedPickupMode==="10"?"en 10 minutos":"lo antes posible";
  const message=["Hola, quisiera consultar una recogida "+label+".",
    record.origin?"Origen: "+record.origin.text:"",
    record.destination?"Destino: "+record.destination.text:"",
    "¿Estás libre y cuánto tardarías en llegar?"
  ].filter(Boolean).join("\n");
  const contact="https://wa.me/59897228175?text="+encodeURIComponent(message);
  $("pickup-presence-whatsapp").href=contact;
  $("urgent-whatsapp").href=contact;
  const seq=++pickupEtaRequestSeq;
  if(!host.dataset.presence||host.dataset.presence==="unknown"){
    paintPickupCard({presence:"checking",title:"Consultando disponibilidad",
      subtitle:"Un momento, estamos comprobando la próxima recogida."});
  }
  const driver=await publicDriverAvailability(force);
  if(seq!==pickupEtaRequestSeq||selectedPickupMode==="schedule")return;
  if(!driver){
    forgetPickupEta();
    paintPickupCard({presence:"unknown",title:"No pudimos comprobar la disponibilidad",
      subtitle:"Probá nuevamente en unos instantes o consultanos por WhatsApp."});
    return;
  }
  const serverAge=Date.parse(driver.checkedAt||"");
  const elapsedCheck=Number.isFinite(serverAge)
    ?Math.max(0,Math.round((Date.now()-serverAge)/1000)):null;
  const checkedLabel="Disponibilidad verificada"+
    (elapsedCheck===null?" recientemente":" hace "+elapsedCheck+" s")+
    " · Recogida sujeta a confirmación.";
  if(!driver.available){
    forgetPickupEta();
    paintPickupCard({presence:"offline",title:"Conductor no disponible ahora",
      subtitle:"Podés programar tu viaje o consultar otro horario.",
      checked:checkedLabel});
    return;
  }
  if(!record.origin){
    paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
      subtitle:"Elegí tu punto de partida para conocer la distancia y el tiempo de llegada.",
      checked:checkedLabel});
    return;
  }
  const signedIn=authReady();
  const lat=record.origin.lat,lng=record.origin.lng;
  const identity=signedIn?session.token:"visitante";
  const key=identity+"|"+lat.toFixed(5)+","+lng.toFixed(5);
  const lastGood=latestPickupEstimate(key);
  // Mapa envía el último movimiento detectado; la web no obtiene velocidad
  // ni posición del conductor. Los períodos aplican en primer plano.
  const etaInterval=window.TrasladosPickupCadence.intervalMs(driver.motionHint);
  const showWaiting=(prefix)=>{
    if(lastGood){
      const estimateAge=Date.now()-lastVerifiedEtaAt;
      const freshEstimate=estimateAge<etaInterval+5000;
      paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
        subtitle:freshEstimate?"Llegada estimada hasta tu punto de partida.":
          "Estamos actualizando tu tiempo de llegada.",
        km:lastGood.km,minutes:lastGood.minutes,
        estimateNote:(freshEstimate?"Estimación obtenida ":"Última estimación ")+
          elapsedShort(estimateAge)+" · La llegada puede variar.",checked:checkedLabel});
    }else{
      paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
        subtitle:prefix||"Estamos actualizando la llegada aproximada a tu origen.",
        checked:checkedLabel});
    }
  };
  // Consulta de estado cada 15 s y ruta cada 25 s en movimiento / 45 s detenido.
  // El límite SQL sigue vigente; nunca saltar el backoff ni cuotas existentes.
  if((etaRetryIdentity===identity&&Date.now()<etaRetryNotBefore) ||
      (key===lastPickupEtaKey&&Date.now()-lastPickupEtaAt<etaInterval)){
    showWaiting("Estamos actualizando la llegada aproximada a tu origen.");
    return;
  }
  lastPickupEtaKey=key;lastPickupEtaAt=Date.now();
  lastPickupEtaMessage="";lastPickupEtaIsEstimate=false;
  showWaiting("Calculando distancia y tiempo hasta tu origen…");
  window.TrasladosETA.pickup({
    api:API,publishableKey:KEY,originLat:lat,originLng:lng,
    sessionToken:signedIn?session.token:null
  }).then(data=>{
    if(seq!==pickupEtaRequestSeq||selectedPickupMode==="schedule")return;
    if(data?.available&&Number.isFinite(data.distanceKm)&&data.distanceKm>=0
       &&Number.isFinite(data.etaMin)&&data.etaMin>=0){
      const km=data.distanceKm,minutes=data.etaMin;
      lastVerifiedEtaResult={km,minutes,coarse:data.estimateType==="coarse"};
      lastVerifiedEtaKey=key;lastVerifiedEtaAt=Date.now();
      lastPickupEtaIsEstimate=true;
      etaRetryNotBefore=0;etaRetryIdentity="";
      paintPickupCard({presence:"online",title:"Conductor disponible para recogerte",
        subtitle:"Llegada estimada hasta tu punto de partida.",
        km,minutes,estimateNote:data.estimateType==="coarse"?"Distancia y tiempo aproximados.":"Tiempo y distancia aproximados.",
        checked:checkedLabel});
      lastPickupEtaMessage=$("pickup-presence-status")?.textContent||"";
    }else{
      const reason=data?.reason;
      if(reason==="not_available"||reason==="occupied"){
        forgetPickupEta();
        paintPickupCard({presence:"offline",title:"Conductor no disponible ahora",
          subtitle:"Podés programar el traslado o consultar otro horario.",
          checked:checkedLabel});
        return;
      }
      if(reason==="rate_limited"){
        // Espera prudente para no insistir sobre una cuota aún agotada.
        etaRetryIdentity=identity;
        etaRetryNotBefore=Math.max(etaRetryNotBefore,Date.now()+90000);
        showWaiting("Estamos actualizando tu tiempo estimado de llegada.");
      }else if(reason==="login_required"){
        showWaiting("Volvé a ingresar a tu cuenta para consultar la llegada.");
      }else if(reason==="route_unavailable"){
        showWaiting("Todavía no tenemos el tiempo estimado hasta ese origen.");
      }else{
        showWaiting("La llegada aún no está disponible. Vamos a volver a intentarlo.");
      }
      lastPickupEtaMessage=$("pickup-presence-status")?.textContent||"";
    }
  }).catch(()=>{
    if(seq!==pickupEtaRequestSeq||selectedPickupMode==="schedule")return;
    showWaiting("No pudimos actualizar el tiempo de llegada. Reintentaremos pronto.");
    lastPickupEtaMessage=$("pickup-presence-status")?.textContent||"";
  });
}

function setWhen(kind){
  // "Ahora" y "En 10 min" comparten el mismo origen: cambiar de pestaña
  // no debe disparar nuevas ETA ni eludir la cuota de privacidad.
  selectedPickupMode=kind;
  document.querySelectorAll("[data-when]").forEach(x=>x.classList.toggle("active",x.dataset.when===kind));
  const urgent=kind!=="schedule";
  // Solo las programadas tienen fecha/hora editables. Urgentes = consulta, no reserva.
  $("pickup-datetime-block").hidden=urgent;
  $("when-next").hidden=urgent;
  $("pickup-mode-caption").hidden=!urgent;
  $("pickup-mode-caption").textContent=kind==="10"
    ?"Dentro de 10 minutos · Consultar disponibilidad y llegada"
    :"Ahora · Consultar disponibilidad y llegada";
  // De momento el backend solo admite reservas programadas: 30 min de antelación por defecto.
  // No presentar «Ahora» y «En 10 min» como una reserva confirmable sin GPS/agenda real.
  $("urgent-whatsapp").hidden=!urgent;
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
  const when=currentUY(kind==="10"?10:2);
  $("pickup-date").value=when.day;$("pickup-time").value=when.time;
  $("availability").textContent="Consulta directa: no se envía una reserva automática. El conductor debe confirmar.";
  refreshBookingSteps();refreshPickupPresence();
}
function shiftView(name){
  for(const panel of document.querySelectorAll(".view"))panel.classList.toggle("active",panel.id==="view-"+name);
  for(const tab of document.querySelectorAll(".tab")){
    const active=tab.dataset.view===name;tab.classList.toggle("active",active);
    if(active)tab.setAttribute("aria-current","page");else tab.removeAttribute("aria-current");
  }
  if(name==="viajes")void loadTrips();
  if(name==="cuenta")renderAccount();
  window.scrollTo({top:Math.min(window.scrollY,150),behavior:"smooth"});
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
  const box=document.createElement("section");box.className="location-widget";mount.appendChild(box);
  const caption=text(box,"div",name==="origin"?"◉ ORIGEN":name==="destination"?"◆ DESTINO":"● PARADA "+(position+1),"location-title");
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
  placeholder.value="";placeholder.textContent="Otros departamentos";
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
  const input=document.createElement("input");input.type="search";
  input.placeholder="Calle, número o lugar…";
  input.autocomplete="off";input.setAttribute("aria-label","Lugar o dirección de "+name);
  row.appendChild(input);
  const search=button(row,"Buscar","tiny-btn location-search-action",()=>void lookup());
  search.setAttribute("aria-label","Buscar dirección o lugar"); 
  const geo=name==="origin"?button(controls,"⌖ Usar ubicación actual","tiny-btn",()=>void useCurrent()):null;
  const gpsHelp=name==="origin"?text(controls,"div","","gps-permission-help"):null;
  if(gpsHelp){gpsHelp.hidden=true;gpsHelp.setAttribute("role","alert");}
  const results=text(controls,"div","","results");
  const feedback=text(controls,"p","","geo-feedback");feedback.hidden=true;
  const source=text(controls,"p","","geo-attribution");source.hidden=true;
  function repaint(){
    clear(selectedBox);
    if(!selected){selectedBox.hidden=true;controls.hidden=false;return;}
    selectedBox.hidden=false;controls.hidden=true;
    const shown=text(selectedBox,"div","","location-chosen-content");
    text(shown,"strong",selected.text,"location-selected-label");
    text(shown,"small",selected.department?selected.department+" · Ubicación seleccionada":"Ubicación seleccionada","location-selected-hint");
    button(selectedBox,"Editar","tiny-btn location-edit",()=>{
      selected=null;selectedBox.hidden=true;controls.hidden=false;
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
    if(places.some(p=>p.id?.startsWith("osm-"))){
      source.hidden=false;
      source.textContent="Lugares: © OpenStreetMap contributors (ODbL). Elegí en el mapa el acceso exacto si es necesario.";
    }
    if(!unique.length){feedback.hidden=false;feedback.textContent="Sin coincidencias verificadas en "+dept+". Revisá calle y número o elegí otro departamento. No mostramos direcciones de otras zonas como si fueran correctas.";}
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
    feedback.hidden=false;feedback.textContent=quick.length?"Buscando otros lugares…":"Buscando lugares y direcciones en Uruguay…";
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
  text(km,"small","RECORRIDO");
  const distance=text(km,"strong","…");
  const min=text(metrics,"div","","route-metric");
  text(min,"small","TIEMPO APROX.");
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
      indicator.textContent="Ruta verificada";
      indicator.classList.add("is-verified");
      note.textContent="Importe orientativo, sin peajes ni extras. El conductor confirma el precio final.";
      detailCaption.textContent=data.source==="supabase_route_cache"
        ? "Ruta guardada, calculada por carretera. Datos cartográficos © OpenStreetMap contributors. No incluye tráfico en vivo."
        : "Ruta estimada por carretera. Datos cartográficos © OpenStreetMap contributors. No incluye tráfico en vivo.";
      clear(map);
      if(Array.isArray(data.geometry)&&data.geometry.length>1)addRoadDiagram(map,data.geometry);
    }else{
      latestRoad=null;
      distance.textContent="—";duration.textContent="—";reference.textContent="A confirmar";
      indicator.textContent="Cálculo pendiente";
      indicator.classList.remove("is-verified");
      note.textContent="El cálculo automático aún no está disponible para este recorrido. Podés ver la ruta en Maps.";
      detailCaption.textContent="El servicio principal de rutas necesita estar habilitado. La caché interna solo tiene recorridos ya verificados. No usamos distancias en línea recta para estimar precios.";
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
  if(logged){
    $("person-name").value=session.customer.full_name||"";
    $("person-phone").value=session.customer.phone||"";
  }
  renderAccount();renderStops();refreshBookingSteps();
}
function renderAccount(){
  const logged=authReady();
  $("account-session").hidden=!logged;$("account-form").hidden=logged;
  if(!logged)return;
  const c=$("account-session");clear(c);
  text(c,"h3","Sesión iniciada");
  text(c,"p",(session.customer.full_name||"Pasajero")+" · "+(session.customer.phone||""));
  button(c,"CERRAR SESIÓN","secondary full",async()=>{
    if(!window.confirm("¿Cerrar sesión en este dispositivo?"))return;
    try{await rpc("customer_logout",{p_session_token:session.token});}catch{}
    session=null;localStorage.removeItem(SESSION_KEY);syncAuthUI();tell("Sesión cerrada");
  });
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
    syncAuthUI();tell("Bienvenido: cuenta conectada con Traslados Conductor.");shiftView("reservar");
  }catch(e){showError(e);}finally{busy=false;loading(btn,false);}
}
function bookingData(){
  const date=$("pickup-date").value,time=$("pickup-time").value;
  const passengers=Number($("passengers").value);
  const origin=record.origin,destination=record.destination;
  const name=authReady()?(session.customer.full_name||"").trim():$("person-name").value.trim();
  const phone=authReady()?(session.customer.phone||"").trim():$("person-phone").value.trim();
  if(!name||name.length<2||phone.replace(/\D/g,"").length<8)throw new Error("Faltan nombre o teléfono válidos.");
  if(!date||!time)throw new Error("Elegí fecha y hora.");
  if(new Date(date+"T"+time+":00-03:00").getTime()<Date.now()-20000)throw new Error("El horario elegido quedó en el pasado.");
  if(!origin||!destination)throw new Error("Tenés que elegir direcciones verificadas para origen y destino.");
  if(origin.lat===destination.lat&&origin.lng===destination.lng)throw new Error("Origen y destino no pueden ser el mismo punto.");
  if(record.stops.some(s=>!s))throw new Error("Hay paradas sin dirección geográfica verificada.");
  if(record.stops.length&&!authReady())throw new Error("Para agregar paradas intermedias, ingresá a tu cuenta.");
  return{date,time,passengers,origin,destination,name,phone,comments:$("comments").value.trim(),stops:[...record.stops]};
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
  if(selectedPickupMode!=="schedule"){tell("Para recogidas inmediatas, consultá por WhatsApp. No enviamos una reserva automática.");return;}
  try{
    pendingReview=bookingData();
    const p=pendingReview;
    const target=$("booking-preview");clear(target);target.hidden=false;

    const title=text(target,"div","","preview-title");
    text(title,"span","PASO FINAL · REVISÁ TU TRASLADO","preview-eyebrow");
    text(title,"h3","Antes de enviar");
    text(title,"p","Comprobá el recorrido y el horario. El conductor confirmará tu solicitud.");

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
    $("review-booking").hidden=true;$("submit-actions").hidden=false;
    target.scrollIntoView({behavior:"smooth",block:"start"});
  }catch(e){pendingReview=null;suggestBookingStep(e);showError(e);}
}
function saveGuest(result){
  const old=jsonStorage(GUEST_KEY,[]).filter(x=>x.token!==result.public_token);
  old.unshift({code:result.code,token:result.public_token,savedAt:Date.now()});
  saveStorage(GUEST_KEY,old.slice(0,18));
}
async function submitBooking(){
  if(selectedPickupMode!=="schedule"){tell("Las recogidas urgentes se coordinan primero con el conductor.");return;}
  if(busy||!pendingReview)return;
  const btn=$("confirm-booking");busy=true;loading(btn,true);
  try{
    guardConnection();const p=pendingReview;
    let result;
    if(authReady()){
      const availability=await rpc("customer_check_availability_v11_4",
        window.TrasladosBookingContracts.checkAvailability(session.token,p));
      if(!availability?.available)throw new Error("Este horario no está disponible. Seleccioná otro horario para el viaje.");
      result=await rpc("customer_create_reservation_v12",
        window.TrasladosBookingContracts.registeredReservation(session.token,p));
    }else{
      result=await rpc("create_reservation",
        window.TrasladosBookingContracts.guestReservation(p));
      if(result?.public_token)saveGuest(result);
    }
    if(!result?.code)throw new Error("El servidor no devolvió el código de la reserva. Revisá Mis traslados antes de repetir.");
    clear($("booking-success"));
    const box=$("booking-success");box.hidden=false;
    text(box,"div","✓","mark");text(box,"h2","Solicitud enviada");
    text(box,"p","El conductor debe revisar y confirmar el traslado. No es una aceptación automática.");
    text(box,"div",result.code,"code");
    text(box,"p","Guardamos el acceso a tu reserva en este dispositivo. Con cuenta también la verás desde otro teléfono.");
    button(box,"VER MIS TRASLADOS","primary full",()=>shiftView("viajes"));
    $("booking-preview").hidden=true;$("review-booking").hidden=true;$("submit-actions").hidden=true;
    pendingReview=null;box.scrollIntoView({behavior:"smooth"});
  }catch(e){showError(e);}finally{busy=false;loading(btn,false);}
}
function statusName(s){return states[s]||s||"Sin estado";}
function reservationView(r,guestToken){
  const d=document.createElement("details");d.className="reservation";
  const top=document.createElement("summary");d.appendChild(top);
  const left=document.createElement("div");top.appendChild(left);
  text(left,"h4",(r.code||"Reserva")+" · "+(r.pickup_date||"")+" "+String(r.pickup_time||"").slice(0,5));
  text(left,"p",(r.origin_text||"Origen")+" → "+(r.destination_text||"Destino"));
  text(top,"span",statusName(r.status),"status-pill");
  text(d,"p","Pasajero: "+(r.passenger_name||r.customer_name||"") );
  if(r.stops?.length)text(d,"p","Paradas: "+r.stops.map(s=>s.address_text||"").join(" · "));
  const price=Number(r.quote_final_total||0);
  if(price>0)text(d,"p","Presupuesto del conductor: "+new Intl.NumberFormat("es-UY",{style:"currency",currency:"UYU",maximumFractionDigits:0}).format(price));
  else text(d,"p","Precio: pendiente de preparación y envío por el conductor.");
  if(r.quote_includes)text(d,"p","Incluye: "+r.quote_includes);
  text(d,"p","Seguimiento GPS del conductor: todavía no disponible en esta versión. Consultá los estados aquí.");
  const actions=document.createElement("div");actions.className="action-row";d.appendChild(actions);
  if(guestToken&&["PENDIENTE","ACEPTADA"].includes(r.status))button(actions,"Cancelar reserva","secondary",async()=>{
    if(!confirm("¿Cancelar esta reserva?"))return;
    try{await rpc("cancel_reservation_by_token",{p_token:guestToken});tell("Reserva cancelada");void loadTrips();}catch(e){showError(e);}
  });
  if(authReady()&&r.id&&r.quote_status==="ENVIADO"&&price>0&&["PENDIENTE","PRESUPUESTO_ENVIADO"].includes(r.status)){
    button(actions,"Aceptar presupuesto","primary",()=>void decideQuote(r.id,true));
    button(actions,"Rechazar","secondary",()=>void decideQuote(r.id,false));
  }
  if(authReady()&&r.id&&["PENDIENTE","ACEPTADA"].includes(r.status))button(actions,"Cancelar","secondary",async()=>{
    if(!confirm("¿Cancelar esta reserva?"))return;
    try{await rpc("customer_cancel_reservation",{p_session_token:session.token,p_reservation_id:r.id});tell("Reserva cancelada");void loadTrips();}catch(e){showError(e);}
  });
  return d;
}
async function decideQuote(id,accept){
  if(!confirm(accept?"¿Aceptás el presupuesto final enviado?":"¿Rechazás el presupuesto?"))return;
  try{await rpc("customer_quote_decision_v11_4",{p_session_token:session.token,p_reservation_id:id,p_accept:accept});tell("Respuesta registrada");void loadTrips();}
  catch(e){showError(e);}
}
async function loadTrips(){
  const list=$("trips-list"),status=$("trips-status");clear(list);
  status.textContent="Consultando tus traslados…";
  try{
    if(authReady()){
      const rows=await rpc("customer_list_reservations_v12",{p_session_token:session.token});
      const arr=Array.isArray(rows)?rows:[];
      if(!arr.length)text(list,"p","No tenés reservas vinculadas a esta cuenta.");
      for(const r of arr)list.appendChild(reservationView(r,null));
      status.textContent=arr.length+" reserva(s) de tu cuenta. Actualización a las "+new Date().toLocaleTimeString("es-UY")+".";
    }else{
      const guest=jsonStorage(GUEST_KEY,[]);if(!guest.length){
        text(list,"p","Todavía no guardaste reservas en este dispositivo. Si tenés una cuenta, ingresá para ver tu historial.");
      }
      const results=await Promise.allSettled(guest.map(x=>rpc("get_reservation_by_token",{p_token:x.token})));
      let count=0;
      results.forEach((r,i)=>{
        if(r.status==="fulfilled"&&r.value){list.appendChild(reservationView(r.value,guest[i].token));count++;}
      });
      status.textContent=count+" reserva(s) guardadas localmente, consultadas en el servidor.";
    }
  }catch(e){status.textContent="No se pudieron actualizar las reservas.";showError(e);}
}
// La instalación automática depende del navegador: no hay API para abrir su menú
// cuando beforeinstallprompt no se emitió. La alternativa siempre debe ser visible.
function isStandaloneTraslados(){
  // Cliente Android v14 abre exactamente esta web con WebView nativa.
  // Nunca ofrecer otra APK o instalación PWA desde dentro de esa misma APK.
  return Boolean(/TrasladosClienteNative\/14/i.test(navigator.userAgent||"")||
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
  const android=/Android/i.test(navigator.userAgent||"");
  let apkReady=false,installConfirmed=false;
  const isReady=()=>Boolean(installEvent);
  // No suponer que una URL existe: comprobar HEAD y tipo binario antes de
  // mostrar el botón de descarga; nunca dar un enlace al workflow de GitHub.
  async function checkVerifiedApk(){
    if(!android||isStandaloneTraslados())return;
    try{
      const response=await fetch(apkLink.href,{method:"HEAD",cache:"no-store"});
      const type=(response.headers.get("content-type")||"").toLowerCase();
      const length=Number(response.headers.get("content-length")||"0");
      apkReady=response.ok&&length>10000&&!/text\/html|application\/json|text\/plain/.test(type);
    }catch{apkReady=false;}
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
  void checkVerifiedApk();
  if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self){
    const register=()=>navigator.serviceWorker.register("./sw.js",{scope:"./"}).catch(()=>{});
    if(document.readyState==="complete")void register();
    else window.addEventListener("load",register,{once:true});
  }
}
function init(){
  session=jsonStorage(SESSION_KEY,null);
  bookingSteps=window.TrasladosBookingSteps.mount(document);
  for(const tab of document.querySelectorAll(".tab"))tab.addEventListener("click",()=>shiftView(tab.dataset.view));
  document.querySelectorAll("[data-open-login]").forEach(x=>x.addEventListener("click",()=>shiftView("cuenta")));
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
  $("edit-booking").addEventListener("click",()=>{pendingReview=null;$("booking-preview").hidden=true;$("review-booking").hidden=false;$("submit-actions").hidden=true;bookingSteps.open("route",{scroll:true});});
  $("confirm-booking").addEventListener("click",()=>void submitBooking());
  $("refresh-trips").addEventListener("click",()=>void loadTrips());
  $("mode-login").addEventListener("click",()=>setAccountMode(false));
  $("mode-register").addEventListener("click",()=>setAccountMode(true));
  $("account-submit").addEventListener("click",()=>void accountSubmit());
  window.addEventListener("online",updateConnectivity);window.addEventListener("offline",updateConnectivity);
  syncAuthUI();setAccountMode(false);updateConnectivity();refreshBookingSteps();installUI();
}
if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",init,{once:true});else init();
