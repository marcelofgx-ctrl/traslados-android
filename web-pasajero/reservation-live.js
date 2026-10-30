"use strict";
/* Tracking inline for an authorized, accepted reservation. The location is
   NEVER inferred from a requested pickup, demo data or the public presence.
   Uses only customer_reservation_position_v1, and road routes verified by the
   existing engine. Shared by PWA, mobile web and native WebView. */
(function(root){
  const routeCache=new Map();
  const MAX_ROUTES=30;
  const activeStatus=new Set(["ACEPTADA","ACEPTADA_CLIENTE","CONFIRMADA","EN_VIAJE"]);
  const css=(node,name)=>{const e=document.createElement("div");e.className=name;node.append(e);return e;};
  function place(parent,tag,name,label){
    const e=document.createElement(tag);e.className=name;if(label!=null)e.textContent=label;parent.append(e);return e;
  }
  function coordinates(lat,lng){
    const a=Number(lat),b=Number(lng);
    return Number.isFinite(a)&&Number.isFinite(b)&&a>=-35.3&&a<=-30&&b>=-58.8&&b<=-52.9?{lat:a,lng:b}:null;
  }
  function keyOf(points){return points.map(p=>p.lat.toFixed(4)+","+p.lng.toFixed(4)).join("|");}
  async function verifiedRoute(points,engine,api,key){
    if(!engine||!engine.estimate||!engine.verified)return null;
    const cacheKey=keyOf(points);
    const existing=routeCache.get(cacheKey);
    if(existing&&Date.now()-existing.stamp<30000)return existing.route;
    const route=await engine.estimate(points,{api,publishableKey:key});
    if(!engine.verified(route)||!Array.isArray(route.geometry)||route.geometry.length<2)return null;
    routeCache.set(cacheKey,{stamp:Date.now(),route});
    if(routeCache.size>MAX_ROUTES)routeCache.delete(routeCache.keys().next().value);
    return route;
  }
  function formatKm(km){return Number(km).toLocaleString("es-UY",{maximumFractionDigits:1})+" km";}
  function mount(host,{reservation:r,guestToken=null,sessionToken=null,rpc,engine,api,key,renderer,onUpdate=null}={}){
    if(!host||!r||!activeStatus.has(String(r.status))||typeof rpc!=="function")return;
    let stopped=false,timer=null,contractRoute=null;
    const ride=String(r.status)==="EN_VIAJE";
    const notify=data=>{if(!stopped&&typeof onUpdate==="function")onUpdate(data);};
    const atPickup=!ride&&String(r.pickup_phase)==="EN_PUNTO";
    // Only the currently visible app surface may request private vehicle GPS.
    // Prevent parallel polls from the collapsed trips screen and tracking screen.
    function visible(){
      const view=host.closest?.(".view");
      return !stopped&&host.isConnected&&
        !(view&&root.QuickTripMapFocus?.isTrackingOpen?.())&&
        (!view||view.classList.contains("active"))&&
        document.visibilityState!=="hidden"&&root.navigator?.onLine!==false;
    }
    const from=coordinates(r.origin_lat,r.origin_lng);
    const to=coordinates(r.destination_lat,r.destination_lng);
    host.classList.add("reservation-live");
    host.setAttribute("aria-label","Mapa y seguimiento de este traslado");
    const heading=css(host,"reservation-live-heading");
    const icon=place(heading,"span","reservation-live-heading-icon","🚘");
    icon.setAttribute("aria-hidden","true");
    const headingCopy=css(heading,"reservation-live-heading-copy");
    place(headingCopy,"strong","",ride?"Tu viaje está en curso":atPickup?"Tu conductor llegó":"Seguimiento de tu recogida");
    const status=place(headingCopy,"small","reservation-live-status",ride?"Consultando recorrido":"Consultando posición del conductor");
    const mapHost=css(host,"reservation-live-map");
    mapHost.setAttribute("role","group");
    mapHost.setAttribute("aria-label","Cartografía de la recogida o viaje");
    const metrics=css(host,"reservation-live-metrics");
    const distanceBox=css(metrics,"reservation-live-metric");
    const distanceTitle=place(distanceBox,"small","","RECORRIDO PREVISTO");
    const distance=place(distanceBox,"strong","","—");
    const etaBox=css(metrics,"reservation-live-metric");
    const etaTitle=place(etaBox,"small","","DURACIÓN PREVISTA");
    const eta=place(etaBox,"strong","","—");
    const explanation=place(host,"p","reservation-live-note","Hasta recibir GPS válido, este mapa representa el trayecto contratado, no al vehículo.");
    const privacy=place(host,"small","reservation-live-freshness","");
    let routePromise=null,lastDriverPos=null;
    async function paint(points,kind){
      if(!points||points.length<2||!renderer?.render){return;}
      const mode=kind==="driver"?(ride?"ride":"pickup"):"contract";
      const cacheKey=mode+"|"+keyOf(points);
      if(host.dataset.mapRoute===cacheKey)return true;
      if(routePromise)return false;
      routePromise=verifiedRoute(points,engine,api,key).catch(()=>null);
      let result=await routePromise;routePromise=null;
      if(!visible())return false;
      if(!result){
        if(kind==="contract"){
          explanation.textContent="Recorrido vial pendiente. El mapa muestra únicamente el origen y destino seleccionados.";
          // Never leave the accepted passenger on a blank screen if the road
          // provider fails. Show verified address pins, never fake a road or
          // unauthorized driver position.
          const featured=Boolean(host.closest?.("#view-seguimiento"));
          const height=featured?Math.min(560,Math.max(320,Math.round((root.innerHeight||760)*0.55))):208;
          if(!mapHost.querySelector(".premium-real-map")&&renderer?.overview)
            renderer.overview(mapHost,[[from.lat,from.lng],[to.lat,to.lng]],{height});
        }
        return false;
      }
      if(kind==="contract")contractRoute=result;
      const overlay=host.closest?.("#qt-focus");
      const featured=Boolean(host.closest?.("#view-seguimiento")||overlay);
      const overlayTop=overlay?.querySelector?.(".qt-focus-sheet")?.getBoundingClientRect?.().top;
      const height=overlay?Math.round(Math.max(300,Math.min(root.innerHeight||760,(overlayTop||450)+26)))
        :featured?Math.min(560,Math.max(320,Math.round((root.innerHeight||760)*0.55))):208;
      const dual=kind==="driver"&&!ride&&Array.isArray(contractRoute?.geometry)&&
        contractRoute.geometry.length>=2;
      if(!renderer.render(mapHost,dual?contractRoute.geometry:result.geometry,{
        height,fullscreen:Boolean(overlay),
        routeMode:mode,
        secondaryGeometry:dual?result.geometry:null,
        driverPosition:dual?points[0]:null,
        // UI-only camera hint from the SAME private authenticated GPS fix.
        // Never changes coordinates, route, ETA, polling or marker semantics.
        cameraPosition:kind==="driver"?points[0]:null,
        previousCar:kind==="driver"?lastDriverPos:null,
        // Keep three distinct points for orientation while the route line
        // still follows the active leg (car→pickup or car→destination).
        extraMarkers:kind==="driver"?[ride?
          {lat:from.lat,lng:from.lng,label:"O"}:
          {lat:to.lat,lng:to.lng,label:"D"}]:[]
      }))return false;
      host.dataset.mapRoute=cacheKey;
      host.dataset.liveDriver=kind==="driver"?"true":"false";
      lastDriverPos=kind==="driver"?points[0]:null;
      const km=Number(result.distanceKm);
      const mins=Number(result.durationMin);
      if(kind!=="contract"){
        notify({stage:ride?"en_viaje":"recogida",gps:true,dual,
          distanceKm:Number(result.distanceKm),durationMin:Number(result.durationMin)});
        distanceTitle.textContent=ride?"AUTO → DESTINO":"AUTO → RECOGIDA";
        etaTitle.textContent=ride?"TIEMPO AL DESTINO":atPickup?"ESTADO DE RECOGIDA":"LLEGADA APROX.";
        if(Number.isFinite(km)&&km>=0)distance.textContent=formatKm(km);
        if(atPickup)eta.textContent="En el punto";
        else if(Number.isFinite(mins)&&mins>=0)eta.textContent=Math.max(1,Math.ceil(mins))+" min";
      }
      if(kind==="contract"){
        distanceTitle.textContent="RECORRIDO PREVISTO";
        etaTitle.textContent="DURACIÓN PREVISTA";
        distance.textContent=Number.isFinite(km)&&km>=0?formatKm(km):"—";
        eta.textContent=Number.isFinite(mins)&&mins>=0?Math.max(1,Math.ceil(mins))+" min":"—";
        // The booked road route is already verified and displayed. Publish its
        // independently VERIFIED trip metrics to the fullscreen sheet now,
        // even while private driver GPS is pending. These are NOT pickup ETA.
        if(Number.isFinite(km)&&km>0&&Number.isFinite(mins)&&mins>=0){
          notify({stage:"contract",gps:false,tripMetrics:true,
            distanceKm:km,durationMin:mins});
        }
      }
      return true;
    }
    function clearDriverRoute(){
      if(host.dataset.liveDriver!=="true")return;
      host.dataset.mapRoute="";
      host.dataset.liveDriver="false";
      lastDriverPos=null;
      // Never leave an obsolete car location visible when its authorization
      // expires or the updated road calculation fails.
      mapHost.replaceChildren();
      distanceTitle.textContent="RECORRIDO PREVISTO";
      etaTitle.textContent="DURACIÓN PREVISTA";
      distance.textContent="—";eta.textContent="—";
    }
    let updating=false;
    async function run(){
      if(updating||!visible())return;
      updating=true;
      try{
      if(!from||!to){
        status.textContent="Direcciones sin coordenadas verificadas";
        explanation.textContent="Consultá los detalles de origen y destino.";
        return;
      }
      // Map is ALWAYS inline within the same reservation card, even before
      // authorized vehicle GPS exists. This is the booked route only.
      if(!host.dataset.mapRoute)void paint([from,to],"contract");
      if(!r.id||(!sessionToken&&!guestToken)){
        status.textContent="Seguimiento disponible con tu cuenta";
        return;
      }
      try{
        const value=await rpc("customer_reservation_position_v1",{
          p_reservation_id:r.id,
          p_session_token:sessionToken||null,
          p_public_token:sessionToken?null:guestToken||null
        });
        if(!visible())return;
        const pos=value?.available?coordinates(value.driverLat,value.driverLng):null;
        const fresh=Number(value?.gpsAgeSec);
        if(!pos||!Number.isFinite(fresh)||fresh<0||fresh>75){
          if(host.dataset.liveDriver==="true"){
            clearDriverRoute();
            void paint([from,to],"contract");
          }
          host.dataset.gpsState="waiting";
          status.textContent=ride?"Posición del vehículo pendiente":
            atPickup?"Conductor llegó · posición GPS pendiente":
            value?.reason==="preparing_pickup"?"Viaje aceptado · preparando recogida":
              "Recogida confirmada · posición pendiente";
          explanation.textContent="Vista del origen y destino contratados. Todavía no tenemos una posición GPS reciente del auto.";
          privacy.textContent="La llegada del conductor no puede estimarse sin su ubicación verificada.";
          notify({stage:ride?"en_viaje":"recogida",gps:false,message:status.textContent});
          return;
        }
        const destination=ride?to:from;
        if(host.dataset.liveDriver!=="true"){
          distance.textContent="Calculando";eta.textContent="—";
        }
        status.textContent=ride?"En viaje · automóvil hacia el destino":
          atPickup?"Conductor en el punto de recogida":"Automóvil en camino a recogerte";
        explanation.textContent=ride?"Distancia del vehículo al destino por carretera.":
          atPickup?"Llegada registrada por el conductor. El GPS puede variar algunos metros.":
          "Distancia del vehículo a tu recogida por carretera.";
        privacy.textContent="● GPS verificado hace "+Math.round(fresh)+" s · consulta cada 4 s con el mapa abierto";
        host.dataset.gpsState="fresh";
        // If a prior contract-route request is still in-flight, paint the latest
        // driver route once it settles: never leave the static route over GPS.
        if(routePromise)try{await routePromise;}catch(_){}
        if(visible()){
          const mapped=await paint([pos,destination],"driver");
          if(visible()&&!mapped){
            clearDriverRoute();
            status.textContent="GPS recibido · ruta vial no disponible";
            explanation.textContent="Tenemos posición reciente del vehículo, pero no se pudo verificar el trayecto por calles.";
            distance.textContent="—";eta.textContent="—";
            void paint([from,to],"contract");
          }
        }
      }catch(e){
        if(!host.isConnected)return;
        if(host.dataset.liveDriver==="true"){
          clearDriverRoute();
          void paint([from,to],"contract");
        }
        status.textContent="Seguimiento temporalmente no disponible";
        explanation.textContent="Tu reserva sigue confirmada. Podés consultar su estado y mensajes.";
      }
      }finally{updating=false;}
    }
    // Booking status rarely changes during a drive. GPS must refresh even if
    // loadTrips skips repainting the card due to its stable status signature.
    // One timer per visible mounted card, automatically cleared on removal.
    if(typeof setInterval==="function"){
      timer=setInterval(()=>{
        if(!host.isConnected){clearInterval(timer);return;}
        if(!visible())return;
        void run();
      },4000);
    }
    // Defer until the document appends the newly constructed reservation node.
    Promise.resolve().then(run);
    return ()=>{stopped=true;if(timer!==null)clearInterval(timer);};
  }
  root.TrasladosReservationLive=Object.freeze({mount,coordinates});
})(window);
