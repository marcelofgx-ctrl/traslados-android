"use strict";

/* Traslados con Reserva — web + installable PWA; same live backend as native Cliente/Conductor. */
const API="https://zetaudvvutlouiqxopvg.supabase.co";
const KEY="sb_publishable_HnbMZW2dKpm6mBq-y5qkaA_Jlfx4BB9";
const IDE="https://direcciones.ide.uy/api/v1/geocode";
const SESSION_KEY="traslados.passenger.session.v1";
const GUEST_KEY="traslados.passenger.guest-trips.v1";
const DEPTS=["Montevideo","Canelones","Artigas","Cerro Largo","Colonia","Durazno","Flores","Florida","Lavalleja","Maldonado","Paysandú","Río Negro","Rivera","Rocha","Salto","San José","Soriano","Tacuarembó","Treinta y Tres"];
const states={PENDIENTE:"Pendiente",PRESUPUESTO_ENVIADO:"Presupuesto enviado",ACEPTADA:"Aceptada",ACEPTADA_CLIENTE:"Aceptada por pasajero",CONFIRMADA:"Confirmada",EN_VIAJE:"En viaje",FINALIZADA:"Finalizada",CANCELADA:"Cancelada",RECHAZADA:"Rechazada",RECHAZADA_CLIENTE:"Rechazada por pasajero"};
const $=(id)=>document.getElementById(id);
const record={origin:null,destination:null,stops:[]};
const depts={origin:"Canelones",destination:"Montevideo"};
let session=null,registerMode=false,installEvent=null,pendingReview=null,busy=false,toastHandle=null;

function jsonStorage(key,defaultValue){try{return JSON.parse(localStorage.getItem(key)||"null")??defaultValue;}catch{return defaultValue;}}
function saveStorage(key,v){try{localStorage.setItem(key,JSON.stringify(v));}catch{}}
function text(parent,tag,contents,cls){const e=document.createElement(tag);if(cls)e.className=cls;if(contents!==undefined)e.textContent=String(contents);parent.appendChild(e);return e;}
function button(parent,contents,cls,fn){const b=text(parent,"button",contents,cls);b.type="button";if(fn)b.addEventListener("click",fn);return b;}
function clear(n){n.replaceChildren();}
function tell(message){const el=$("toast");clearTimeout(toastHandle);el.textContent=message;el.hidden=false;toastHandle=setTimeout(()=>el.hidden=true,5500);}
function showError(e,fallback="No se pudo completar la acción"){tell(e instanceof Error?e.message:fallback);}
function loading(el,value){if(!el)return;el.disabled=value;el.dataset.original??=el.textContent;if(value)el.textContent="Procesando…";else{el.textContent=el.dataset.original;}}
function guardConnection(){if(!navigator.onLine)throw new Error("Estás sin Internet; no enviamos reservas offline.");}
async function rpc(name,payload={}){
  guardConnection();
  const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),18000);
  try{
    const res=await fetch(API+"/rest/v1/rpc/"+encodeURIComponent(name),{
      method:"POST",mode:"cors",headers:{"apikey":KEY,"Content-Type":"application/json","Accept":"application/json"},
      body:JSON.stringify(payload),signal:controller.signal,cache:"no-store"
    });
    const content=await res.text();let parsed;
    try{parsed=content?JSON.parse(content):null;}catch{parsed=content;}
    if(!res.ok)throw new Error(parsed?.message||parsed?.error||"Servicio temporalmente no disponible");
    return parsed;
  }catch(e){if(e?.name==="AbortError")throw new Error("La conexión tardó demasiado. Revisá Mis traslados antes de volver a enviar para evitar duplicados.");throw e;}
  finally{clearTimeout(timer);}
}
function updateConnectivity(){const offline=!navigator.onLine;$("offline-band").hidden=!offline;$("confirm-booking").disabled=offline;if(offline)tell("Sin conexión: no podés enviar reservas hasta reconectarte.");}
function currentUY(offsetMinutes=0){
  const dt=new Date(Date.now()+offsetMinutes*60000);
  const parts=Object.fromEntries(new Intl.DateTimeFormat("en-US",{timeZone:"America/Montevideo",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(dt).filter(x=>x.type!=="literal").map(x=>[x.type,x.value]));
  return{day:`${parts.year}-${parts.month}-${parts.day}`,time:`${parts.hour}:${parts.minute}`};
}
function setWhen(kind){
  document.querySelectorAll("[data-when]").forEach(x=>x.classList.toggle("active",x.dataset.when===kind));
  if(kind==="schedule"){$("pickup-date").focus();return;}
  const when=currentUY(kind==="10"?10:2);
  $("pickup-date").value=when.day;$("pickup-time").value=when.time;
  $("availability").textContent=kind==="now"?"Solicitud para ahora: requiere disponibilidad y confirmación del conductor.": "Solicitud para dentro de 10 minutos: el conductor puede indicar otro horario.";
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
function picker(name,mount,position){
  const box=document.createElement("div");mount.appendChild(box);
  text(box,"strong",name==="origin"?"⌖ Origen · ¿dónde te buscamos?":name==="destination"?"⚑ Destino":"● Parada "+(position+1));
  const dep=document.createElement("div");dep.className="departments";box.appendChild(dep);
  for(const opt of ["Montevideo","Canelones"]){
    const b=button(dep,opt,"",()=>chooseDept(opt));
    b.classList.toggle("selected",(depts[name]||"Canelones")===opt);
  }
  const select=document.createElement("select");select.setAttribute("aria-label","Otros departamentos de Uruguay");
  const prompt=document.createElement("option");prompt.text="Otros 17 departamentos";prompt.value="";select.appendChild(prompt);
  for(const d of DEPTS.slice(2)){const o=document.createElement("option");o.value=d;o.textContent=d;select.appendChild(o);}
  dep.appendChild(select);
  const row=document.createElement("div");row.className="loc-search";box.appendChild(row);
  const input=document.createElement("input");input.type="search";input.placeholder="Calle, número, localidad o aeropuerto…";
  input.autocomplete="street-address";input.setAttribute("aria-label","Dirección de "+name);row.appendChild(input);
  const search=button(row,"Buscar","tiny-btn",()=>void lookup());row.appendChild(search);
  const geo=name==="origin"?button(box,"⌖ Usar ubicación actual","tiny-btn",()=>void useCurrent()):null;
  const results=document.createElement("div");results.className="results";box.appendChild(results);
  const chosen=document.createElement("div");chosen.className="location-selected";chosen.hidden=true;box.appendChild(chosen);
  const key=name==="origin"||name==="destination"?name:"stop"+position;
  let dept=depts[key]||(name==="destination"?"Montevideo":"Canelones");
  let selected=null;
  const persist=(loc)=>{
    selected=loc;
    if(name==="origin"||name==="destination"){record[name]=loc;depts[name]=loc.department||dept;}
    else record.stops[position]=loc;
    chosen.hidden=false;chosen.textContent="✓ "+loc.text+" · "+loc.lat.toFixed(5)+", "+loc.lng.toFixed(5);
    clear(results);
  };
  const state=()=>selected;
  const chooseDept=(d)=>{
    dept=d;depts[key]=d;selected=null;chosen.hidden=true;
    if(name==="origin"||name==="destination")record[name]=null;else record.stops[position]=null;
    for(const b of dep.querySelectorAll("button"))b.classList.toggle("selected",b.textContent===d);
    select.value=DEPTS.slice(2).includes(d)?d:"";
    clear(results);
  };
  select.addEventListener("change",()=>{if(select.value)chooseDept(select.value);});
  const lookup=async()=>{
    const q=input.value.trim();if(q.length<4){tell("Escribí por lo menos cuatro caracteres de la dirección.");return;}
    loading(search,true);clear(results);
    text(results,"small","Buscando direcciones en IDE Uruguay…");
    try{
      const resp=await fetch(IDE+"/candidates?limit=15&q="+encodeURIComponent(q+", "+dept),{cache:"no-store"});
      if(!resp.ok)throw new Error("El servicio de direcciones no respondió");
      const arr=await resp.json();
      if(!Array.isArray(arr))throw new Error("Respuesta de direcciones inválida");
      clear(results);
      const filtered=arr.filter(x=>x&&x.address).sort((a,b)=>String(b.departamento||"").toLowerCase()===dept.toLowerCase()?1:0);
      if(!filtered.length){text(results,"small","No hubo coincidencias. Añadí localidad y numeración o usá tu ubicación.");return;}
      for(const item of filtered.slice(0,8)){
        const candidate=button(results,(item.address||"")+" · "+(item.departamento||""),"result",async()=>{
          try{
            let lat=Number(item.lat),lng=Number(item.lng);
            if(!isUruguayCoords(lat,lng)){
              const r=await fetch(IDE+"/direcUnica?limit=5&q="+encodeURIComponent(item.address),{cache:"no-store"});
              const possibilities=r.ok?await r.json():[];
              const match=Array.isArray(possibilities)?possibilities.find(x=>isUruguayCoords(Number(x.lat),Number(x.lng))):null;
              if(!match)throw new Error("Esta dirección no tiene coordenadas verificables. Probá otro resultado.");
              lat=Number(match.lat);lng=Number(match.lng);
            }
            persist({text:item.address,lat,lng,department:item.departamento||dept});
          }catch(e){showError(e);}
        });
        const extra=text(candidate,"small","Elegir ubicación verificada");
      }
    }catch(e){clear(results);showError(e);}
    finally{loading(search,false);}
  };
  const useCurrent=async()=>{
    if(!navigator.geolocation){tell("El dispositivo no ofrece GPS.");return;}
    tell("Solicitando permiso para tu ubicación…");
    navigator.geolocation.getCurrentPosition(async(p)=>{
      const lat=p.coords.latitude,lng=p.coords.longitude;
      if(!isUruguayCoords(lat,lng)){tell("La ubicación detectada no está en Uruguay.");return;}
      let address="Ubicación actual (GPS)";
      let selectedDept=dept;
      try{
        const r=await fetch(IDE+"/reverse?limit=1&latitud="+lat+"&longitud="+lng);
        if(r.ok){const arr=await r.json();if(Array.isArray(arr)&&arr[0]?.address){address=arr[0].address;selectedDept=arr[0].departamento||dept;}}
      }catch{}
      persist({text:address,lat,lng,department:selectedDept});
    },()=>tell("No se pudo obtener GPS. Revisá los permisos de ubicación."),{enableHighAccuracy:true,timeout:14000,maximumAge:15000});
  };
  if(name==="stop"&&record.stops[position])persist(record.stops[position]);
  return{state,chooseDept,input,persist,box};
}
let originPicker,destPicker,stopPickers=[];
function renderStops(){
  const host=$("stops");clear(host);stopPickers=[];
  for(let i=0;i<record.stops.length;i++){
    const row=document.createElement("div");row.className="stop-row";host.appendChild(row);
    button(row,"Quitar","tiny-btn",()=>{record.stops.splice(i,1);renderStops();});
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
  renderAccount();renderStops();
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
function showPreview(){
  try{
    pendingReview=bookingData();const p=pendingReview;
    const target=$("booking-preview");clear(target);target.hidden=false;
    text(target,"h3","Revisá antes de enviar");
    for(const [label,value] of [
      ["Pasajero",p.name],["Teléfono",p.phone],["Fecha",p.date+" · "+p.time],["Personas",p.passengers],
      ["Origen",p.origin.text+" ("+p.origin.department+")"],
      ["Paradas",p.stops.length?p.stops.map(x=>x.text).join(" → "):"Sin paradas"],
      ["Destino",p.destination.text+" ("+p.destination.department+")"],
      ["Precio","A definir por el conductor; todavía no cobrás ni confirmás viaje"]
    ]){
      const row=document.createElement("div");row.className="preview-row";
      text(row,"span",label);text(row,"span",value);target.appendChild(row);
    }
    $("review-booking").hidden=true;$("submit-actions").hidden=false;
    target.scrollIntoView({behavior:"smooth",block:"start"});
  }catch(e){pendingReview=null;showError(e);}
}
function saveGuest(result){
  const old=jsonStorage(GUEST_KEY,[]).filter(x=>x.token!==result.public_token);
  old.unshift({code:result.code,token:result.public_token,savedAt:Date.now()});
  saveStorage(GUEST_KEY,old.slice(0,18));
}
async function submitBooking(){
  if(busy||!pendingReview)return;
  const btn=$("confirm-booking");busy=true;loading(btn,true);
  try{
    guardConnection();const p=pendingReview;
    let result;
    if(authReady()){
      const availability=await rpc("customer_check_availability_v11_4",{
        p_session_token:session.token,p_pickup_date:p.date,p_pickup_time:p.time,
        p_trip_duration_min:null,p_origin_lat:p.origin.lat,p_origin_lng:p.origin.lng,
        p_destination_lat:p.destination.lat,p_destination_lng:p.destination.lng
      });
      if(!availability?.available)throw new Error("Este horario no está disponible. Seleccioná otro horario para el viaje.");
      result=await rpc("customer_create_reservation_v12",{
        p_session_token:session.token,p_pickup_date:p.date,p_pickup_time:p.time,
        p_passengers:p.passengers,p_comments:p.comments,
        p_origin_text:p.origin.text,p_origin_lat:p.origin.lat,p_origin_lng:p.origin.lng,
        p_origin_department:p.origin.department,
        p_destination_text:p.destination.text,p_destination_lat:p.destination.lat,p_destination_lng:p.destination.lng,
        p_destination_department:p.destination.department,
        p_stops:p.stops.map((x,i)=>({position:i+1,address_text:x.text,lat:x.lat,lng:x.lng,department:x.department})),
        p_passenger_name:null,p_passenger_phone:null
      });
    }else{
      result=await rpc("create_reservation",{
        p_customer_name:p.name,p_customer_phone:p.phone,p_pickup_date:p.date,p_pickup_time:p.time,
        p_passengers:p.passengers,p_comments:p.comments,
        p_origin_text:p.origin.text,p_origin_lat:p.origin.lat,p_origin_lng:p.origin.lng,
        p_destination_text:p.destination.text,p_destination_lat:p.destination.lat,p_destination_lng:p.destination.lng
      });
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
  status.textContent="Consultando Supabase…";
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
function installUI(){
  window.addEventListener("beforeinstallprompt",(e)=>{e.preventDefault();installEvent=e;$("install-action").disabled=false;$("install-info").textContent="Listo para instalar. Tocá el botón superior para agregar Traslados al inicio.";});
  window.addEventListener("appinstalled",()=>{installEvent=null;$("install-action").textContent="YA INSTALADA";tell("Traslados se instaló correctamente.");});
  $("install-action").addEventListener("click",async()=>{
    if(window.matchMedia("(display-mode: standalone)").matches){tell("Ya estás usando la app web instalada.");return;}
    if(installEvent){installEvent.prompt();await installEvent.userChoice;installEvent=null;return;}
    tell("En Chrome: menú ⋮ → Instalar aplicación. En iPhone: Compartir → Agregar a inicio.");
  });
  if(window.matchMedia("(display-mode: standalone)").matches)$("install-action").textContent="YA INSTALADA";
  if("serviceWorker" in navigator&&window.isSecureContext&&window.top===window.self){
    window.addEventListener("load",()=>navigator.serviceWorker.register("./sw.js",{scope:"./"}).catch(()=>{}));
  }
}
function init(){
  session=jsonStorage(SESSION_KEY,null);
  for(const tab of document.querySelectorAll(".tab"))tab.addEventListener("click",()=>shiftView(tab.dataset.view));
  document.querySelectorAll("[data-open-login]").forEach(x=>x.addEventListener("click",()=>shiftView("cuenta")));
  document.querySelectorAll("[data-when]").forEach(b=>b.addEventListener("click",()=>setWhen(b.dataset.when)));
  setWhen("schedule");const tomorrow=currentUY(60);
  $("pickup-date").value=tomorrow.day;$("pickup-date").min=currentUY().day;$("pickup-time").value=tomorrow.time;
  originPicker=picker("origin",$("origin-picker"),0);
  destPicker=picker("destination",$("destination-picker"),0);
  $("add-stop").addEventListener("click",()=>{if(record.stops.length>=8){tell("Hasta 8 paradas");return;}record.stops.push(null);renderStops();});
  $("review-booking").addEventListener("click",showPreview);
  $("edit-booking").addEventListener("click",()=>{pendingReview=null;$("booking-preview").hidden=true;$("review-booking").hidden=false;$("submit-actions").hidden=true;});
  $("confirm-booking").addEventListener("click",()=>void submitBooking());
  $("refresh-trips").addEventListener("click",()=>void loadTrips());
  $("mode-login").addEventListener("click",()=>setAccountMode(false));
  $("mode-register").addEventListener("click",()=>setAccountMode(true));
  $("account-submit").addEventListener("click",()=>void accountSubmit());
  window.addEventListener("online",updateConnectivity);window.addEventListener("offline",updateConnectivity);
  syncAuthUI();setAccountMode(false);updateConnectivity();installUI();
}
if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",init,{once:true});else init();
