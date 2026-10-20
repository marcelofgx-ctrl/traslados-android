"use strict";
/*
 * Quick Trip · Tu viaje — presentación V7 limpia.
 * Este archivo solo construye interfaz y REUBICA NODOS NATIVOS con sus listeners.
 * No crea reservas, no consulta geocodificadores, no calcula rutas ni ETA.
 * Las funciones app.js, premium-route.js y los motores siguen intactos.
 */
(function(win,doc){
  if(new URLSearchParams(win.location.search).get("ui")!=="clean")return;
  const byId=id=>doc.getElementById(id);
  const node=(tag,className,txt)=>{
    const n=doc.createElement(tag);
    if(className)n.className=className;
    if(txt!==undefined)n.textContent=txt;
    return n;
  };
  let shell=null,historyPanel=null,historyButton=null,historyBusy=false;
  let ownedRouteKey="",ownedCode="",mountedTrackingKey="";
  let paxAnchor=null,mapAnchorOriginal=null,stopsAnchorOriginal=null;
  const navigate=view=>doc.querySelector('.tab[data-view="'+view+'"]')?.click();

  function historyPlace(row){
    const lat=Number(row.destination_lat),lng=Number(row.destination_lng);
    const name=String(row.destination_text||"").trim();
    if(!name||!Number.isFinite(lat)||!Number.isFinite(lng))return null;
    if(lat < -35.3||lat > -30||lng < -58.8||lng > -52.9)return null;
    return {text:name,lat,lng,source:"history"};
  }
  function showHistoryMessage(message){
    historyPanel.replaceChildren(node("p","qtc-history-empty",message));
  }
  async function fillHistory(){
    if(historyBusy)return;
    historyBusy=true;
    showHistoryMessage("Consultando tus destinos…");
    try{
      // Leer ÚNICAMENTE el historial ya autorizado por el motor oficial:
      // mismas credenciales, tokens y reglas de la pantalla Mis traslados.
      if(typeof loadTrips==="function")await loadTrips({background:true});
      const rows=typeof tripRows!=="undefined"&&Array.isArray(tripRows)?tripRows:[];
      const unique=new Set(),places=[];
      for(const row of [...rows].reverse()){
        const place=historyPlace(row);
        if(!place)continue;
        const key=place.lat.toFixed(6)+","+place.lng.toFixed(6);
        if(unique.has(key))continue;
        unique.add(key);places.push(place);
        if(places.length>=8)break;
      }
      if(!places.length){
        showHistoryMessage("Todavía no hay destinos anteriores en tu historial.");
        return;
      }
      const list=node("div","qtc-history-list");
      for(const place of places){
        const btn=node("button","qtc-history-item");
        btn.type="button";
        btn.append(node("span","qtc-history-pin","↗"),node("span","",place.text));
        btn.addEventListener("click",()=>{
          // persist() pertenece al selector NATIVO: conserva recalculo y mapa.
          if(typeof destPicker==="undefined"||typeof destPicker.persist!=="function"){
            showHistoryMessage("El selector de destino no está disponible. Volvé a intentarlo.");
            return;
          }
          destPicker.persist(place);
          closeHistory();
        });
        list.append(btn);
      }
      historyPanel.replaceChildren(list);
    }catch{
      showHistoryMessage("No pudimos cargar el historial. Podés elegir un destino escribiéndolo.");
    }finally{historyBusy=false;}
  }
  function closeHistory(){
    if(!historyPanel)return;
    historyPanel.hidden=true;
    historyButton?.setAttribute("aria-expanded","false");
  }
  function installHistory(){
    const route=byId("booking-step-route");
    const destination=byId("destination-picker");
    if(!route||!destination||byId("qtc-history"))return;
    const box=node("div","qtc-history");box.id="qtc-history";
    historyButton=node("button","qtc-history-button","◷  Historial");
    historyButton.type="button";
    historyButton.setAttribute("aria-expanded","false");
    historyButton.setAttribute("aria-controls","qtc-history-panel");
    historyPanel=node("section","qtc-history-panel");
    historyPanel.id="qtc-history-panel";
    historyPanel.setAttribute("aria-label","Elegir uno de mis destinos anteriores");
    historyPanel.hidden=true;
    // Botón pequeño junto al título; el panel aparece DEBAJO del destino
    // sin cubrir ni la caja de búsqueda ni el botón GPS.
    box.append(historyPanel);
    destination.querySelector(".location-title")?.after(historyButton);
    destination.after(box);
    historyButton.addEventListener("click",()=>{
      const opening=historyPanel.hidden;
      historyPanel.hidden=!opening;
      historyButton.setAttribute("aria-expanded",String(opening));
      if(opening)void fillHistory();
    });
    doc.addEventListener("pointerdown",event=>{
      if(!box.contains(event.target)&&!historyButton.contains(event.target))closeHistory();
    },{passive:true});
    doc.addEventListener("keydown",event=>{
      if(event.key==="Escape")closeHistory();
    });
  }
  function build(){
    const screen=byId("view-viaje"),host=byId("journey-field-host");
    if(!screen||!host||!byId("origin-picker")?.querySelector(".location-widget")||
       !byId("destination-picker")?.querySelector(".location-widget")||
       !doc.querySelector(".premium-route-canvas")||!byId("urgent-send"))return false;
    if(shell)return true;
    // Guardar las posiciones NATIVAS antes de reubicar. Al volver a Tu reserva
    // los mismos nodos regresan a su lugar, sin tocar datos ni eventos.
    const pax=byId("passengers")?.closest("label");
    const originalMap=doc.querySelector(".premium-route-canvas");
    const originalStops=doc.querySelector(".premium-route-stops");
    if(pax&&!paxAnchor){paxAnchor=doc.createComment("qtc-pax-native");pax.before(paxAnchor);}
    if(originalMap&&!mapAnchorOriginal){mapAnchorOriginal=doc.createComment("qtc-map-native");originalMap.before(mapAnchorOriginal);}
    if(originalStops&&!stopsAnchorOriginal){stopsAnchorOriginal=doc.createComment("qtc-stops-native");originalStops.before(stopsAnchorOriginal);}
    shell=node("div","qtc-app");shell.id="qtc-app";
    shell.innerHTML=
      '<header class="qtc-header"><div class="qtc-brand"><div class="qtc-back-slot"></div>'+
      '<img class="qtc-logo" src="./quicktrip-logo-contrast.svg?v=1" alt="QUICK TRIP">'+
      '</div><div class="qtc-header-copy"><small>TRASLADOS · URUGUAY</small>'+
      '<h1>Tu viaje</h1><p>Simple. Directo. A tu ritmo.</p></div></header>'+
      '<div class="qtc-content">'+
      '<section class="qtc-places" aria-label="Elegí tu recorrido"></section>'+
      '<section class="qtc-driver" aria-label="Conductor y recogida">'+
      '<div class="qtc-section-name"><span class="qtc-live-dot"></span><strong>Tu conductor</strong>'+
      '<small>Disponibilidad y llegada reales</small></div>'+
      '<div class="qtc-driver-search" role="status" aria-live="polite" hidden>'+
      '<span class="qtc-search-orbit" aria-hidden="true"><span class="qtc-search-car">↗</span></span>'+
      '<span class="qtc-search-copy"><strong id="qtc-search-title">Buscando conductor…</strong>'+
      '<small id="qtc-search-subtitle">Consultando disponibilidad en tiempo real.</small></span>'+
      '<span class="qtc-search-dots" aria-hidden="true"><i></i><i></i><i></i></span></div>'+
      '<div class="qtc-driver-unavailable" role="status" hidden>'+ 
      '<strong>Conductor no disponible ahora</strong>'+ 
      '<small>La distancia y el tiempo de recogida aparecerán cuando el conductor esté disponible y tenga GPS actualizado. Podés enviar una solicitud para que la revise.</small></div>'+ 
      '<div class="qtc-owned-journey" role="status" aria-live="polite" hidden>'+ 
      '<strong id="qtc-owned-title"></strong><small id="qtc-owned-description"></small>'+ 
      '<button type="button" id="qtc-owned-details" hidden>Ver estado en Mis traslados →</button></div>'+ 
      '<div class="qtc-owned-tracking" aria-label="Seguimiento autorizado del vehículo" hidden></div>'+ 
      '<div class="qtc-driver-slot"></div></section>'+
      '<details class="qtc-schedule"><summary><span class="qtc-time-icon">◷</span>'+
      '<span class="qtc-time-copy"><strong id="qtc-when-title">Viajás ahora</strong>'+
      '<small>Podés cambiar el horario</small></span><span class="qtc-chevron">⌄</span></summary>'+
      '<div class="qtc-schedule-slot"></div></details>'+
      '<details class="qtc-contact"><summary><span>♙</span><strong>Tus datos</strong>'+
      '<small>Nombre, teléfono y pasajeros</small><span class="qtc-chevron">⌄</span></summary>'+
      '<div class="qtc-contact-slot"></div></details>'+
      '<section class="qtc-submit-region" aria-label="Resumen del viaje y solicitud">'+
      '<div class="qtc-summary-title"><span>Tu recorrido</span><small>Precio del viaje</small></div>'+
      '<p id="qtc-summary-wait">Elegí origen y destino para calcular kilómetros, duración y precio.</p>'+
      '<div class="qtc-action-slot"></div></section>'+
      '<div class="qtc-success-slot"></div></div>'+
      '<nav class="qtc-bottom-nav" aria-label="Servicios">'+
      '<button type="button" data-qtc-nav="now" class="qtc-current" aria-current="page"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M5 15l2-6h10l2 6v4H5zM8 9l2-3h4l2 3M7 16h2m6 0h2M7 19v2m10-2v2"/></svg><span>Tu viaje</span></button>'+
      '<button type="button" data-qtc-nav="package" disabled title="Próximamente"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 8l8-4 8 4v9l-8 4-8-4zM4 8l8 5 8-5M12 13v8M8 6l8 4"/></svg><span>Tu paquete</span></button>'+
      '<button type="button" data-qtc-nav="schedule"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="4" y="6" width="16" height="15" rx="2"/><path d="M4 11h16M8 3v6m8-6v6M9 15h2m2 0h2m-6 3h2"/></svg><span>Programar</span></button>'+
      '</nav>';
    host.prepend(shell);
    const back=screen.querySelector(".journey-back");
    if(back)shell.querySelector(".qtc-back-slot").append(back);
    const time=screen.querySelector(".journey-when-compact");
    if(time)shell.querySelector(".qtc-schedule-slot").append(time);
    shell.querySelector('[data-qtc-nav="schedule"]').addEventListener("click",()=>{
      doc.querySelector('[data-service="schedule"]')?.click();
    });
    // Las acciones de navegación son las originales, nunca endpoints nuevos.
    installHistory();
    byId("journey-when-select")?.addEventListener("change",syncWhenLabel);
    syncWhenLabel();
    byId("qtc-owned-details")?.addEventListener("click",()=>{
      if(typeof shiftView==="function")shiftView("viajes");
      else navigate("viajes");
    });
    doc.body.classList.add("qt-clean");
    return true;
  }

  // Visualizar la solicitud y su seguimiento en "Tu viaje", sin consultas
  // nuevas, estados inferidos ni motores duplicados. La propiedad del viaje
  // se comprueba con el mismo matcher y las mismas filas autorizadas de app.js.
  function syncOwnedJourney(driver,instant){
    const panel=driver.querySelector(".qtc-owned-journey");
    const tracking=driver.querySelector(".qtc-owned-tracking");
    if(!panel||!tracking)return false;
    const routeKey=[record.origin?.text||"",record.destination?.text||"",
      ...(Array.isArray(record.stops)?record.stops.map(stop=>stop?.text||""):[])].join("|");
    if(routeKey!==ownedRouteKey){
      ownedRouteKey=routeKey;ownedCode="";mountedTrackingKey="";
      tracking.replaceChildren();
    }
    const rows=typeof tripRows!=="undefined"&&Array.isArray(tripRows)?tripRows:[];
    const own=instant?win.TrasladosOwnedPickup?.match?.(
      rows,record.origin,record.destination,record.stops):null;
    if(own?.code)ownedCode=String(own.code);
    // Si pasa de ACEPTADA a EN_VIAJE o RECHAZADA, el matcher de solicitudes
    // abiertas ya no la devuelve. Seguir SOLAMENTE el código autorizado antes.
    const row=own||(instant&&ownedCode?
      rows.find(item=>String(item.code||"")===ownedCode):null);
    if(!instant||!row){
      panel.hidden=true;tracking.hidden=true;driver.dataset.rideStatus="none";
      shell.dataset.rideStage="none";
      if(mountedTrackingKey){tracking.replaceChildren();mountedTrackingKey="";}
      return false;
    }
    const status=String(row.status||"");
    const phase=String(row.pickup_phase||"");
    const rejected=["RECHAZADA","RECHAZADA_CLIENTE","CANCELADA"].includes(status);
    const accepted=["ACEPTADA","ACEPTADA_CLIENTE","CONFIRMADA","EN_CAMINO",
      "EN_PUNTO","EN_VIAJE"].includes(status);
    const arrived=phase==="EN_PUNTO"||status==="EN_PUNTO";
    const enRoute=phase==="EN_CAMINO"||status==="EN_CAMINO";
    const onBoard=status==="EN_VIAJE";
    const title=rejected?"Solicitud "+(status==="CANCELADA"?"cancelada":"rechazada"):
      onBoard?"Viaje en curso":arrived?"El conductor llegó":
      enRoute?"Conductor en camino":accepted?"Viaje aceptado":
      "Esperando respuesta del conductor";
    const description=rejected?"Revisá el resultado en Mis traslados. Podés solicitar un nuevo viaje.":
      onBoard?"Seguí el recorrido real hasta tu destino.":
      arrived?"Tu conductor informó que está en el punto de recogida.":
      accepted?"Distancia y llegada a tu origen según GPS reciente y ruta verificada.":
      "La solicitud fue recibida. El conductor todavía no la aceptó ni rechazó.";
    panel.hidden=false;
    panel.dataset.tone=rejected?"closed":accepted?"confirmed":"pending";
    const headline=byId("qtc-owned-title"),subtitle=byId("qtc-owned-description");
    if(headline&&headline.textContent!==title)headline.textContent=title;
    if(subtitle&&subtitle.textContent!==description)subtitle.textContent=description;
    const details=byId("qtc-owned-details");
    if(details)details.hidden=!rejected;
    driver.dataset.rideStatus=rejected?"closed":accepted?"accepted":"pending";

    // La vista autorizada YA existente gestiona customer_reservation_position_v1,
    // la edad del GPS, la ruta vial y su propia cadencia. Sin acceso anónimo a
    // coordenadas privadas ni "kilómetros" inventados en esta presentación.
    const token=typeof authReady==="function"&&authReady()?session?.token:null;
    const guest=row.__guestToken||null;
    const ready=accepted&&Boolean(token||guest)&&
      typeof rpc==="function"&&typeof roadEngine!=="undefined"&&
      typeof win.TrasladosReservationLive?.mount==="function";
    shell.dataset.rideStage=ready?"tracking":"none";
    if(!ready){
      tracking.hidden=true;
      if(mountedTrackingKey){tracking.replaceChildren();mountedTrackingKey="";}
      return true;
    }
    tracking.hidden=false;
    const identity=[String(row.id||row.code||""),status,phase,token||guest].join("|");
    if(identity!==mountedTrackingKey){
      tracking.replaceChildren();
      mountedTrackingKey=identity;
      win.TrasladosReservationLive.mount(tracking,{
        reservation:row,guestToken:guest,sessionToken:token,
        rpc,engine:roadEngine,api:API,key:KEY,renderer:win.TrasladosPremiumMap
      });
    }
    return true;
  }
  // Presentación únicamente: la comprobación del conductor y la ETA siguen
  // viniendo del motor existente. Nunca inventamos conductor ni tiempo.
  function syncDriverSearch(){
    if(!shell)return;
    const driver=shell.querySelector(".qtc-driver");
    const presence=byId("pickup-presence");
    const banner=driver?.querySelector(".qtc-driver-search");
    if(!driver||!presence||!banner)return;
    const owned=syncOwnedJourney(driver,doc.body.classList.contains("journey-mode-quick"));
    const phase=presence.dataset.phase||"";
    const mode=["checking","routing","waiting"].includes(phase)?phase:"";
    const instant=doc.body.classList.contains("journey-mode-quick");
    const searching=Boolean(!owned&&mode&&!presence.hidden&&instant);
    const noDriver=Boolean(!owned&&!presence.hidden&&instant&&
      presence.dataset.presence==="busy"&&phase==="blocked");
    const unavailable=driver.querySelector(".qtc-driver-unavailable");
    driver.dataset.searchState=searching?mode:"settled";
    driver.dataset.availability=noDriver?"unavailable":"normal";
    banner.hidden=!searching;
    if(unavailable)unavailable.hidden=!noDriver;
    if(searching){
      byId("qtc-search-title").textContent=mode==="checking"?"Buscando conductor…":"Calculando la llegada…";
      byId("qtc-search-subtitle").textContent=mode==="checking"
        ?"Consultando la disponibilidad real.":"Verificando la distancia y el tiempo hasta tu origen.";
    }
  }
  // No alteramos el valor ni el cálculo. El resumen del mapa toma el texto
  // verificado del renderizador vial y solo retiramos el adjetivo visual.
  function cleanMapCaption(){
    const note=doc.querySelector(".qtc-places .premium-route-map-note");
    if(!note)return;
    const value=note.textContent||"";
    const cleaned=value.replace(/\s+orientativos?\b/gi,"");
    if(cleaned!==value)note.textContent=cleaned;
  }
  function installVisualObservers(){
    const presence=byId("pickup-presence");
    if(presence){
      new MutationObserver(syncDriverSearch).observe(presence,{
        attributes:true,attributeFilter:["data-phase","data-presence","hidden"]
      });
    }
    const caption=doc.querySelector(".premium-route-map-note");
    if(caption){
      new MutationObserver(cleanMapCaption).observe(caption,{childList:true,characterData:true,subtree:true});
    }
    syncDriverSearch();cleanMapCaption();
  }
  function syncWhenLabel(){
    const select=byId("journey-when-select"),label=byId("qtc-when-title");
    if(!select||!label)return;
    const value=select.value;
    label.textContent=value==="now"?"Viajás ahora":
      value==="custom"?"Horario personalizado":
      /^[0-9]+$/.test(value)?"Viajás en "+value+" min":"Elegí cuándo viajás";
  }
  function reconcile(){
    if(!build())return;
    const active=byId("view-viaje")?.classList.contains("active");
    doc.body.classList.toggle("qt-clean-active",Boolean(active));
    if(!doc.body.classList.contains("journey-mode-quick")){
      // Retornar únicamente componentes visuales movidos, para preservar
      // también la pantalla independiente de viajes programados.
      const pax=byId("passengers")?.closest("label");
      const map=doc.querySelector(".premium-route-canvas");
      const stops=doc.querySelector(".premium-route-stops");
      if(paxAnchor?.isConnected&&pax&&pax.previousSibling!==paxAnchor)paxAnchor.after(pax);
      if(mapAnchorOriginal?.isConnected&&map&&map.previousSibling!==mapAnchorOriginal)mapAnchorOriginal.after(map);
      if(stopsAnchorOriginal?.isConnected&&stops&&stops.previousSibling!==stopsAnchorOriginal)stopsAnchorOriginal.after(stops);
      closeHistory();
      return;
    }
    const places=shell.querySelector(".qtc-places");
    const route=doc.querySelector('[data-booking-step="route"]');
    if(route&&route.parentElement!==places)places.append(route);
    const routeBody=byId("booking-step-route");
    const map=doc.querySelector(".premium-route-canvas");
    const destinationAnchor=routeBody?.querySelector("#destination-picker");
    if(map&&routeBody&&map.parentElement!==routeBody)routeBody.append(map);
    // Se conserva exactamente el mismo mapa; no hay un segundo renderizador.
    const stops=doc.querySelector(".premium-route-stops");
    if(stops&&routeBody&&stops.parentElement!==routeBody){
      const history=byId("qtc-history");
      if(history?.parentElement===routeBody)history.after(stops);
      else destinationAnchor?.after(stops);
    }
    const presence=byId("pickup-presence");
    const driver=shell.querySelector(".qtc-driver-slot");
    if(presence&&driver&&presence.parentElement!==driver)driver.append(presence);
    const passenger=doc.querySelector('[data-booking-step="passenger"]');
    const contact=shell.querySelector(".qtc-contact-slot");
    if(passenger&&contact&&passenger.parentElement!==contact)contact.append(passenger);
    const pax=byId("passengers")?.closest("label");
    if(pax&&contact&&pax.parentElement!==contact)contact.append(pax);
    const urgent=byId("urgent-actions"),submit=shell.querySelector(".qtc-action-slot");
    if(urgent&&submit&&urgent.parentElement!==submit)submit.append(urgent);
    const success=byId("booking-success"),successSlot=shell.querySelector(".qtc-success-slot");
    if(success&&successSlot&&success.parentElement!==successSlot)successSlot.append(success);
    updateSummary();syncDriverSearch();cleanMapCaption();
  }
  function updateSummary(){
    const recap=byId("urgent-route-recap"),wait=byId("qtc-summary-wait");
    if(wait&&recap)wait.hidden=!recap.hidden;
  }
  function boot(){
    if(!build())return;
    const host=byId("journey-field-host");
    new MutationObserver(reconcile).observe(host,{childList:true});
    new MutationObserver(reconcile).observe(byId("view-viaje"),{
      attributes:true,attributeFilter:["class"]
    });
    new MutationObserver(reconcile).observe(doc.body,{
      attributes:true,attributeFilter:["class"]
    });
    const recap=byId("urgent-route-recap");
    if(recap)new MutationObserver(updateSummary).observe(recap,{
      attributes:true,attributeFilter:["hidden"]
    });
    doc.addEventListener("traslados:route-updated",updateSummary);
    installVisualObservers();
    reconcile();
    // Abrir el servicio NATIVO por su acción ya registrada. Sin llamadas RPC.
    const open=doc.querySelector('[data-service="now"]');
    if(open&&!byId("view-viaje")?.classList.contains("active"))open.click();
    win.requestAnimationFrame(reconcile);
  }
  if(doc.readyState==="complete")boot();
  else win.addEventListener("load",boot,{once:true});
})(window,document);
