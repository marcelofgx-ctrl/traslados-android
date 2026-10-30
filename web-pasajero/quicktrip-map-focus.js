"use strict";
/* Full-map mobile presentation of the EXISTING Quick Trip booking contract.
 * No API calls, passenger position polling, price formula or booking mutations.
 * Map geometry and quote are injected from the existing verified route engine.
 */
(function(root){
  const doc=root.document;
  let screen=null,host=null,state=null,open=false,lastMapKey="";
  let trackingCtx=null,trackingStop=null,previewSubmit=null;
  // A tracking overlay has one browser-history entry so Android Back
  // closes the map to the active ride, not the previous history screen.
  const notifyTrackingClosed=()=>root.dispatchEvent(
    new root.CustomEvent("quicktrip:tracking-closed"));
  const valid=p=>p&&Number.isFinite(Number(p.lat))&&Number.isFinite(Number(p.lng))&&
    Number(p.lat)>=-35.3&&Number(p.lat)<=-30&&Number(p.lng)>=-58.8&&Number(p.lng)<=-52.9;
  const safeText=(p,fallback)=>String(p?.text||p?.label||fallback);
  const UYU=n=>new Intl.NumberFormat("es-UY",{style:"currency",currency:"UYU",minimumFractionDigits:0,maximumFractionDigits:0}).format(n);
  const make=(tag,cls,txt)=>{const e=doc.createElement(tag);if(cls)e.className=cls;if(txt!==undefined)e.textContent=txt;return e;};
  const byId=id=>doc.getElementById(id);
  function setup(){
    if(screen)return;
    screen=make("section","qt-focus");screen.id="qt-focus";screen.hidden=true;
    screen.setAttribute("role","dialog");screen.setAttribute("aria-modal","true");
    screen.setAttribute("aria-label","Mapa de tu viaje Quick Trip");
    host=make("div","qt-focus-map");host.setAttribute("aria-label","Mapa interactivo de origen y destino");
    const top=make("header","qt-focus-top");
    const back=make("button","qt-focus-back","←");back.type="button";
    back.setAttribute("aria-label","Volver a Tu viaje");back.onclick=()=>close();
    const address=make("div","qt-focus-address");
    address.append(make("small","", "DESTINO"),make("strong","qt-focus-address-text","Elegí destino"));
    const brand=make("span","qt-focus-brand","Quick Trip");
    const trackingRoute=make("div","qt-focus-tracking-route");
    trackingRoute.setAttribute("aria-label","Origen y destino de la reserva");
    for(const [kind,label] of [["origin","ORIGEN"],["destination","DESTINO"]]){
      const point=make("div","qt-focus-tracking-point");
      point.dataset.point=kind;
      const pin=make("span","qt-focus-tracking-pin",kind==="origin"?"●":"◆");
      pin.setAttribute("aria-hidden","true");
      const copy=make("div","qt-focus-tracking-copy");
      copy.append(make("small","",label),make("strong","qt-focus-tracking-"+kind+"-text","—"));
      point.append(pin,copy);trackingRoute.append(point);
    }
    top.append(back,address,brand,trackingRoute);
    const sheet=make("section","qt-focus-sheet");
    const handle=make("button","qt-focus-handle");handle.type="button";
    handle.setAttribute("aria-label","Expandir o reducir detalles del viaje");
    handle.setAttribute("aria-expanded","false");
    handle.append(make("span","qt-focus-grab"));
    handle.addEventListener("click",()=>toggleSheet());
    const heading=make("div","qt-focus-sheet-heading");
    heading.append(make("small","qt-focus-kicker","TU VIAJE · URUGUAY"),
      make("h2","", "Elegí tu viaje"));
    const option=make("section","qt-focus-option");
    const car=make("span","qt-focus-car","🚗");car.setAttribute("aria-hidden","true");
    const copy=make("div","qt-focus-car-copy");
    copy.append(make("strong","", "Quick Trip"),make("small","qt-focus-car-detail","Traslado privado"));
    const fare=make("strong","qt-focus-fare","A confirmar");
    option.append(car,copy,fare);
    const stats=make("div","qt-focus-stats");
    for(const [name,cls] of [["Distancia","qt-focus-distance"],["Duración","qt-focus-time"]]){
      const cell=make("div","qt-focus-stat");
      cell.append(make("small","",name.toUpperCase()),make("strong",cls,"—"));stats.append(cell);
    }
    const route=make("div","qt-focus-points");
    route.append(make("p","qt-focus-origin",""),make("p","qt-focus-destination",""));
    const message=make("p","qt-focus-message","Calculando recorrido por calles...");
    const actions=make("div","qt-focus-actions");
    const retry=make("button","qt-focus-retry","↻ Reintentar ruta");retry.type="button";retry.hidden=true;
    retry.onclick=()=>{close();byId("route-overview")?.querySelector(".route-retry")?.click()};
    const submit=make("button","qt-focus-submit","Continuar con mi viaje →");submit.type="button";
    submit.onclick=()=>{
      if(submit.disabled)return;
      // The user's original booking form owns personal data checks,
      // session, price verification, submit and error handling.
      close();
      const original=byId("urgent-send");
      const person=byId("person-name"),phone=byId("person-phone");
      if(person&&!person.value.trim()||phone&&!phone.value.trim()){
        const customer=byId("qtc-app")?.querySelector(".qtc-contact");
        if(customer){customer.open=true;customer.scrollIntoView({block:"center",behavior:"smooth"});}
        else byId("booking-step-passenger")?.scrollIntoView({block:"center"});
        return;
      }
      original?.click();
    };
    previewSubmit=submit.onclick;
    actions.append(retry,submit);
    const legend=make("div","qt-focus-legend");
    legend.hidden=true;
    const approach=make("span","qt-focus-legend-approach","● Verde · conductor a recogida");
    const booked=make("span","qt-focus-legend-booked","● Blanco · recorrido del viaje");
    legend.append(approach,booked);
    const note=make("p","qt-focus-note","Sin cobro anticipado · Precio sujeto a confirmación del conductor");
    const trackingExtra=make("section","qt-focus-tracking-extra");
    trackingExtra.setAttribute("aria-label","Más información del viaje");
    trackingExtra.append(make("strong","qt-focus-tracking-stage","Viaje aceptado"),
      make("p","qt-focus-tracking-vehicle","Vehículo confirmado"));
    const contacts=make("nav","qt-focus-tracking-contacts");
    const call=make("a","qt-focus-tracking-contact","☎ Llamar");call.href="tel:+59897228175";
    const whatsapp=make("a","qt-focus-tracking-contact","◉ WhatsApp");
    whatsapp.href="https://wa.me/59897228175";
    whatsapp.target="_blank";whatsapp.rel="noopener noreferrer";
    const chat=make("button","qt-focus-tracking-contact","✉ Chat");chat.type="button";
    chat.onclick=()=>trackingCtx?.onChat?.();
    contacts.append(call,whatsapp,chat);
    trackingExtra.append(contacts);
    sheet.append(handle,heading,option,legend,stats,route,message,actions,note,trackingExtra);
    // Drag the handle or header on mobile to expand / collapse the bottom
    // sheet. Never bind the gesture to the map itself.
    let gestureStart=null;
    function startSheetGesture(e){
      if(e.pointerType==="mouse"&&e.button!==0)return;
      gestureStart={y:e.clientY,id:e.pointerId};
      // Keep receiving pointerup even when the finger leaves the small
      // drag handle/header. Otherwise an actual swipe silently does nothing.
      e.currentTarget?.setPointerCapture?.(e.pointerId);
    }
    function endSheetGesture(e){
      if(!gestureStart||e.pointerId!==gestureStart.id)return;
      const dy=e.clientY-gestureStart.y;gestureStart=null;
      if(Math.abs(dy)>=42){
        e.preventDefault();
        setSheet(dy<0);
      }
    }
    for(const dragSurface of [handle,heading]){
      dragSurface.addEventListener("pointerdown",startSheetGesture);
      dragSurface.addEventListener("pointerup",endSheetGesture);
      dragSurface.addEventListener("pointercancel",()=>{gestureStart=null;});
    }
    screen.append(host,top,sheet);doc.body.append(screen);
    screen.addEventListener("keydown",e=>{
      if(e.key==="Escape"){e.preventDefault();close()}
      if(e.key==="Tab"){
        const inputs=[...screen.querySelectorAll("button:not([disabled]):not([hidden])")];
        const visible=inputs.filter(el=>el.getClientRects().length);
        if(!visible.length)return;
        if(e.shiftKey&&doc.activeElement===visible[0]){e.preventDefault();visible.at(-1).focus()}
        else if(!e.shiftKey&&doc.activeElement===visible.at(-1)){e.preventDefault();visible[0].focus()}
      }
    });
  }
  function setSheet(expanded){
    if(!screen)return;
    screen.classList.toggle("qt-focus-expanded",Boolean(expanded));
    screen.querySelector(".qt-focus-handle")?.setAttribute("aria-expanded",String(Boolean(expanded)));
    screen.dataset.sheet=expanded?"expanded":"compact";
    if(!trackingCtx&&open){lastMapKey="";sync(state);}
  }
  function toggleSheet(){setSheet(!screen?.classList.contains("qt-focus-expanded"));}
  function sync(next){
    if(trackingCtx)return; // Live tracking owns its authorized map.
    state=next||null;if(!open||!screen||!state)return;
    const origin=state.origin,destination=state.destination;
    const points=[origin,destination].filter(valid);
    const good=Boolean(state.metrics&&Array.isArray(state.geometry)&&state.geometry.length>=2&&
      Number.isFinite(state.metrics.distanceKm)&&state.metrics.distanceKm>0&&
      Number.isFinite(state.metrics.durationMin)&&state.metrics.durationMin>=0);
    const fare=Number(state.metrics?.referenceFareUyu);
    screen.querySelector(".qt-focus-address-text").textContent=safeText(destination,"Destino por elegir");
    screen.querySelector(".qt-focus-origin").textContent="● "+safeText(origin,"Origen pendiente");
    screen.querySelector(".qt-focus-destination").textContent="◆ "+safeText(destination,"Destino pendiente");
    screen.querySelector(".qt-focus-distance").textContent=good?
      state.metrics.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km":"—";
    screen.querySelector(".qt-focus-time").textContent=good?Math.round(state.metrics.durationMin)+" min":"—";
    screen.querySelector(".qt-focus-fare").textContent=good&&fare>0?UYU(fare):"A confirmar";
    const submit=screen.querySelector(".qt-focus-submit"),retry=screen.querySelector(".qt-focus-retry");
    submit.disabled=!(good&&fare>0);
    const awaiting=points.length===2&&!good;
    retry.hidden=!awaiting;
    screen.querySelector(".qt-focus-message").textContent=good
      ?"Ruta real por calles · tiempo estimado sin tráfico en vivo"
      :awaiting?"No hay ruta verificada todavía. Reintentá sin cambiar direcciones.":"Elegí origen y destino";
    const geometry=good?state.geometry:null;
    const key=(geometry?JSON.stringify(geometry):JSON.stringify(points))+
      ":"+Math.round(host.clientWidth||390);
    if(key!==lastMapKey&&root.TrasladosPremiumMap){
      lastMapKey=key;host.replaceChildren();
      // Fit the route in the VISIBLE map ABOVE the bottom sheet, not in the
      // portion hidden behind the fare card. Avoid blank extra height at
      // device pixel ratios and varying Android Chrome toolbar sizes.
      const sheetTop=screen.querySelector(".qt-focus-sheet")?.getBoundingClientRect().top;
      const visibleHeight=Number.isFinite(sheetTop)&&sheetTop>200
        ?sheetTop:Math.round(root.innerHeight*.58);
      const height=Math.round(Math.min(root.innerHeight,Math.max(300,visibleHeight+26)));
      if(geometry)root.TrasladosPremiumMap.render(host,geometry,{height,fullscreen:true});
      else root.TrasladosPremiumMap.overview(host,points.map(p=>[p.lat,p.lng]),{height,fullscreen:true});
    }
  }
  function show(next){
    if(!next?.origin||!next?.destination||!valid(next.origin)||!valid(next.destination))return false;
    if(trackingCtx)close(true);
    setup();screen.hidden=false;open=true;lastMapKey="";
    screen.dataset.mode="preview";setSheet(false);
    screen.querySelector(".qt-focus-legend").hidden=true;
    screen.querySelector(".qt-focus-sheet-heading h2").textContent="Elegí tu viaje";
    screen.querySelector(".qt-focus-kicker").textContent="TU VIAJE · URUGUAY";
    screen.querySelector(".qt-focus-submit").textContent="Continuar con mi viaje →";
    screen.querySelector(".qt-focus-submit").onclick=previewSubmit;
    doc.body.classList.add("qt-focus-open");
    sync(next);
    screen.querySelector(".qt-focus-back").focus({preventScroll:true});
    return true;
  }
  function close(silent=false){
    if(!screen)return;
    const wasTracking=Boolean(trackingCtx);
    if(trackingStop)trackingStop();
    trackingStop=null;trackingCtx=null;
    screen.hidden=true;open=false;lastMapKey="";
    delete screen.dataset.mode;
    doc.body.classList.remove("qt-focus-open");
    screen.classList.remove("qt-focus-expanded");
    screen.querySelector(".qt-focus-handle")?.setAttribute("aria-expanded","false");
    // Release map tiles/listeners while overlay is hidden.
    host.replaceChildren();
    (wasTracking?doc.getElementById("quicktrip-live-trip"):
      doc.querySelector(".premium-route-expand"))?.focus?.({preventScroll:true});
    if(wasTracking&&!silent){
      // Only pop the marker created by this screen, never another page entry.
      if(root.history?.state?.qtTrackingOverlayN1===true)root.history.back();
      notifyTrackingClosed();
    }
  }
  function bindOpenButton(button,getState){
    button.addEventListener("click",()=>{
      const next=getState();
      if(!show(next))button.setAttribute("aria-label","Seleccioná origen y destino para abrir el mapa");
    });
  }

  // Presentation-only adapter: combine independently VERIFIED route geometries
  // for the booked trip and for the authorized driver position.
  // The private GPS permission check and routing engines remain unchanged.
  function verifiedTwoLegRenderer(options,r){
    const renderer=options.renderer,engine=options.engine;
    const start={lat:Number(r.origin_lat),lng:Number(r.origin_lng)};
    const end={lat:Number(r.destination_lat),lng:Number(r.destination_lng)};
    let bookedGeometry=null,pending=null,serial=0;
    const geometryOk=route=>engine?.verified?.(route)===true&&
      Array.isArray(route.geometry)&&route.geometry.length>=2&&
      route.geometry.every(p=>Array.isArray(p)&&p.length===2&&
        Number.isFinite(Number(p[0]))&&Number.isFinite(Number(p[1])));
    function bookRoute(){
      if(!pending&&valid(start)&&valid(end)&&typeof engine?.estimate==="function"){
        pending=Promise.resolve().then(()=>engine.estimate([start,end],{
          api:options.api,publishableKey:options.key
        })).then(route=>{
          if(geometryOk(route))bookedGeometry=route.geometry;
          return bookedGeometry;
        }).catch(()=>null);
      }
      return pending;
    }
    return {
      overview:(...args)=>renderer.overview?.(...args),
      render(mapHost,geometry,opts={}){
        const step=++serial;
        if(opts.routeMode==="contract"&&Array.isArray(geometry)&&geometry.length>=2)
          bookedGeometry=geometry;
        const pickup=opts.routeMode==="pickup";
        const needsOverlay=pickup&&!opts.secondaryGeometry;
        const verifiedBook=needsOverlay&&bookedGeometry;
        const next=verifiedBook?{
          ...opts,secondaryGeometry:geometry
        }:opts;
        const result=renderer.render(mapHost,verifiedBook?bookedGeometry:geometry,
          {...next,followVehicle:true});
        if(needsOverlay&&!bookedGeometry){
          void bookRoute()?.then(route=>{
            const follow=mapHost.closest(".reservation-live");
            // Do not redraw an unauthorized, stale or superseded GPS response.
            if(!route||serial!==step||!mapHost.isConnected||
               follow?.dataset.liveDriver!=="true"||
               follow?.dataset.gpsState!=="fresh"||
               !trackingCtx||String(trackingCtx.reservation.id)!==String(r.id))return;
            const merged=renderer.render(mapHost,route, {
              ...opts,secondaryGeometry:geometry,followVehicle:true
            });
            if(merged){
              screen.querySelector(".qt-focus-message").textContent=
                "GPS verificado · dos recorridos reales";
              const approach=screen.querySelector(".qt-focus-legend-approach");
              if(approach)approach.hidden=false;
            }
          });
        }
        return result;
      }
    };
  }

  function syncTrackingStage(r){
    const onboard=r.status==="EN_VIAJE";
    const arrived=r.pickup_phase==="EN_PUNTO";
    const moving=r.pickup_phase==="EN_CAMINO";
    screen.querySelector(".qt-focus-kicker").textContent=
      onboard?"EN VIAJE · GPS REAL":"RECOGIDA · GPS REAL";
    screen.querySelector(".qt-focus-sheet-heading h2").textContent=
      onboard?"Viaje en curso":arrived?"En tu recogida":
      moving?"Conductor en camino":"Viaje aceptado";
    screen.querySelector(".qt-focus-tracking-stage").textContent=
      onboard?"Durante el viaje":arrived?"Conductor en el punto de recogida":
      moving?"Conductor en camino":"Viaje aceptado · preparando recogida";
    screen.querySelector(".qt-focus-car-detail").textContent=
      onboard?"En viaje":arrived?"Conductor llegó":"Recogida confirmada";
    // The compact sheet is the canonical visible status. On arrival the
    // stable tracker retains an old pickup label; its duplicate is suppressed
    // by the QA presentation stylesheet without changing GPS or polling.
    screen.dataset.followStage=onboard?"ride":arrived?"arrived":"pickup";
  }
  function showTracking(options){
    const r=options?.reservation;
    if(!r||!["ACEPTADA","ACEPTADA_CLIENTE","CONFIRMADA","EN_VIAJE"].includes(String(r.status))||
       !r.id||!options?.rpc||!options?.renderer||
       !(options.sessionToken||options.guestToken)||!root.TrasladosReservationLive?.mount)return false;
    const origin={lat:r.origin_lat,lng:r.origin_lng},destination={lat:r.destination_lat,lng:r.destination_lng};
    if(!valid(origin)||!valid(destination))return false;
    // A new travel leg deliberately fits the new route, but must not close
    // the passenger's already expanded details for this SAME reservation.
    const retainSheet=Boolean(open&&trackingCtx&&screen?.dataset.sheet==="expanded"&&
      String(trackingCtx.reservation.id)===String(r.id));
    const continuingSameTracking=Boolean(open&&trackingCtx);
    if(open)close(true);
    setup();open=true;trackingCtx=options;
    screen.hidden=false;screen.dataset.mode="tracking";lastMapKey="";
    doc.body.classList.add("qt-focus-open");
    setSheet(retainSheet);
    const onboard=String(r.status)==="EN_VIAJE";
    syncTrackingStage(r);
    screen.querySelector(".qt-focus-tracking-origin-text").textContent=
      safeText({text:r.origin_text},"Origen pendiente");
    screen.querySelector(".qt-focus-tracking-destination-text").textContent=
      safeText({text:r.destination_text},"Destino pendiente");
    screen.querySelector(".qt-focus-tracking-vehicle").textContent=
      "Vehículo confirmado · ubicación disponible solo con GPS autorizado";
    screen.querySelector(".qt-focus-tracking-contacts button").disabled=
      typeof options.onChat!=="function";
    screen.querySelector(".qt-focus-address small").textContent=onboard?"DESTINO":"RECOGIDA";
    screen.querySelector(".qt-focus-address-text").textContent=safeText(
      {text:onboard?r.destination_text:r.origin_text},"Tu dirección");
    screen.querySelector(".qt-focus-fare").textContent=Number(r.quote_final_total)>0
      ?UYU(Number(r.quote_final_total)):"Importe confirmado pendiente";
    screen.querySelector(".qt-focus-origin").textContent="● "+safeText({text:r.origin_text},"Origen");
    screen.querySelector(".qt-focus-destination").textContent="◆ "+safeText({text:r.destination_text},"Destino");
    const tripDistance=screen.querySelector(".qt-focus-distance");
    const tripTime=screen.querySelector(".qt-focus-time");
    const statisticLabels=screen.querySelectorAll(".qt-focus-stat small");
    const setMetricLabels=(a,b)=>{
      if(statisticLabels[0])statisticLabels[0].textContent=a;
      if(statisticLabels[1])statisticLabels[1].textContent=b;
    };
    let bookedMetrics=null;
    // Never label the full origin→destination road distance as a driver pickup
    // distance. Contract metrics become visible as soon as the VERIFIED white
    // route is drawn. Authenticated GPS can later replace them with live leg
    // metrics, with different labels and no fabricated values.
    const presentMetrics=(data,source)=>{
      const km=Number(data?.distanceKm),mins=Number(data?.durationMin);
      if(!Number.isFinite(km)||km<0||!Number.isFinite(mins)||mins<0)return false;
      setMetricLabels(source==="contract"?"DISTANCIA DEL VIAJE":
        onboard?"AL DESTINO":"A RECOGIDA",
        source==="contract"?"DURACIÓN DEL VIAJE":
          onboard?"TIEMPO AL DESTINO":"LLEGADA ESTIMADA");
      tripDistance.textContent=km.toLocaleString("es-UY",{maximumFractionDigits:1})+" km";
      tripTime.textContent=Math.max(1,Math.ceil(mins))+" min";
      screen.dataset.metricsSource=source;
      return true;
    };
    tripDistance.textContent="—";
    tripTime.textContent="—";
    setMetricLabels("DISTANCIA DEL VIAJE","DURACIÓN DEL VIAJE");
    screen.dataset.metricsSource="pending";
    screen.querySelector(".qt-focus-message").textContent="Esperando la posición GPS autorizada del conductor…";
    const submit=screen.querySelector(".qt-focus-submit");
    submit.disabled=false;submit.textContent="Volver al inicio";submit.onclick=()=>close();
    screen.querySelector(".qt-focus-retry").hidden=true;
    const legend=screen.querySelector(".qt-focus-legend");legend.hidden=false;
    legend.querySelector(".qt-focus-legend-approach").hidden=true;
    host.replaceChildren();
    const twoLegRenderer=verifiedTwoLegRenderer(options,r);
    const trackingStopFn=root.TrasladosReservationLive.mount(host,{
      reservation:r,guestToken:options.guestToken,sessionToken:options.sessionToken,
      rpc:options.rpc,engine:options.engine,api:options.api,
      key:options.key,renderer:twoLegRenderer,
      onUpdate:data=>{
        if(!trackingCtx||String(trackingCtx.reservation.id)!==String(r.id))return;
        if(data.tripMetrics===true&&data.stage==="contract"){
          // Initial booked route: real road distance and duration, not GPS ETA.
          const km=Number(data.distanceKm),min=Number(data.durationMin);
          if(Number.isFinite(km)&&km>0&&Number.isFinite(min)&&min>=0){
            bookedMetrics={distanceKm:km,durationMin:min};
            if(screen.dataset.metricsSource!=="live")presentMetrics(bookedMetrics,"contract");
          }
          return;
        }
        screen.querySelector(".qt-focus-message").textContent=data.gps?
          onboard?"GPS verificado · conductor rumbo al destino":
            data.dual?"GPS verificado · dos recorridos reales":
              "GPS verificado · verificando recorrido hasta recogida":
          data.message||"Esperando GPS reciente para mostrar el vehículo";
        legend.querySelector(".qt-focus-legend-approach").hidden=onboard||!data.gps;
        if(data.gps&&presentMetrics(data,"live"))return;
        // GPS missing/stale or live route cannot be verified: never retain an
        // obsolete driver-to-pickup ETA. Fall back to the *labeled* booked trip.
        if(bookedMetrics)presentMetrics(bookedMetrics,"contract");
        else{
          tripDistance.textContent="—";
          tripTime.textContent="—";
          setMetricLabels("DISTANCIA DEL VIAJE","DURACIÓN DEL VIAJE");
          screen.dataset.metricsSource="pending";
        }
      }
    });
    trackingStop=typeof trackingStopFn==="function"?trackingStopFn:null;
    if(!continuingSameTracking&&root.history?.pushState){
      root.history.pushState({...root.history.state,qtTrackingOverlayN1:true},"",root.location.href);
    }
    screen.querySelector(".qt-focus-back").focus({preventScroll:true});
    return true;
  }
  function updateTracking(r){
    if(!trackingCtx||!r||String(trackingCtx.reservation.id)!==String(r.id))return false;
    if(r.status===trackingCtx.reservation.status&&
      r.pickup_phase===trackingCtx.reservation.pickup_phase)return false;
    const previous=trackingCtx.reservation;
    if(previous.status!=="EN_VIAJE"&&r.status!=="EN_VIAJE"){
      // Same pickup leg: keep the live tracker, camera and sheet mounted.
      // Only its display label changes. The real GPS refresh timer continues.
      trackingCtx={...trackingCtx,reservation:r};
      syncTrackingStage(r);
      return true;
    }
    // Switching to the destination leg requires the existing live engine to
    // re-evaluate the authorized destination; the map will re-fit that leg.
    return showTracking({...trackingCtx,reservation:r});
  }
  root.addEventListener("popstate",()=>{
    if(open&&trackingCtx){
      close(true); // Browser/Android already popped our own map history entry.
      notifyTrackingClosed();
    }
  });
  root.QuickTripMapFocus=Object.freeze({
    sync,show,showTracking,updateTracking,close,bindOpenButton,
    isOpen:()=>open,isTrackingOpen:()=>Boolean(trackingCtx)
  });
})(window);
