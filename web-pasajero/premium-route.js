"use strict";
/* Route presentation only: reuses the real pickers, GPS and route engine.
 * Overview is geography, never a passenger/driver position or invented route. */
(function(root){
  function mount(doc,getState){
    const card=doc.querySelector('[data-booking-step="route"]');
    if(!card||typeof getState!=="function")throw Error("Missing route composer dependencies");
    const map=doc.createElement("section");map.className="premium-route-canvas";
    map.setAttribute("aria-label","Mapa de tu recorrido");
    const mapHost=doc.createElement("div");mapHost.className="premium-route-cartography";
    const caption=doc.createElement("p");caption.className="premium-route-map-note";
    const retry=doc.createElement("button");retry.type="button";retry.className="premium-route-retry";retry.textContent="Reintentar cálculo";retry.hidden=true;
    retry.addEventListener("click",()=>doc.querySelector("#route-overview .route-retry")?.click());
    // Presentation-only ticket from historical approved MAP-FIRST V5.
    // Values are always fed by the existing verified route state.
    const approved=new URLSearchParams(root.location.search).get("ui")==="v6";
    let summary=null,fareValue=null,kmValue=null,durationValue=null;
    if(approved){
      summary=doc.createElement("section");summary.className="premium-route-summary";
      summary.setAttribute("aria-label","Resumen verificado del viaje");
      const fare=doc.createElement("div");fare.className="premium-route-fare";
      const fareLabel=doc.createElement("small");fareLabel.textContent="PRECIO ORIENTATIVO DEL VIAJE";
      fareValue=doc.createElement("strong");fareValue.textContent="A confirmar";
      fare.append(fareLabel,fareValue);
      const tripMetrics=doc.createElement("div");tripMetrics.className="premium-route-trip-metrics";
      const km=doc.createElement("div"),duration=doc.createElement("div");
      const kmLabel=doc.createElement("small");kmLabel.textContent="DISTANCIA DEL VIAJE";
      kmValue=doc.createElement("strong");kmValue.textContent="—";
      const durationLabel=doc.createElement("small");durationLabel.textContent="DURACIÓN ESTIMADA";
      durationValue=doc.createElement("strong");durationValue.textContent="—";
      km.append(kmLabel,kmValue);duration.append(durationLabel,durationValue);
      tripMetrics.append(km,duration);summary.append(fare,tripMetrics);
    }
    map.append(mapHost,caption);
    if(summary)map.append(summary);
    map.append(retry);card.append(map);
    const stops=doc.createElement("button");stops.type="button";stops.className="premium-route-stops";
    stops.textContent="Agregar paradas (opcional)";
    const stopBody=doc.getElementById("stop-wrap"),login=doc.getElementById("login-for-stops");
    map.insertAdjacentElement("beforebegin",stops);
    let showStops=false,lastMap="";
    const controls=[];
    for(const id of ["origin-picker","destination-picker"]){
      const host=doc.getElementById(id),widget=host.querySelector(".location-widget");
      const deps=host.querySelector(".departments"),input=host.querySelector('input[type="search"]');
      const gps=host.querySelector(".location-use-current");
      if(gps){const short=doc.createElement("span");short.className="premium-gps-short-label";short.textContent="GPS";gps.append(short);}
      const toggle=doc.createElement("button");toggle.type="button";toggle.className="premium-department-toggle";
      deps.id="premium-departments-"+id;toggle.setAttribute("aria-controls",deps.id);toggle.setAttribute("aria-expanded","false");
      deps.insertAdjacentElement("beforebegin",toggle);
      toggle.addEventListener("click",()=>{
        const open=widget.classList.toggle("is-department-open");
        toggle.setAttribute("aria-expanded",String(open));
      });
      input.addEventListener("focus",()=>{
        if(!doc.body.classList.contains("journey-mode-quick"))return;
        for(const c of controls)if(c.widget!==widget){c.widget.classList.remove("is-editing","is-department-open");c.toggle.setAttribute("aria-expanded","false");}
        widget.classList.add("is-editing");
      });
      input.addEventListener("blur",event=>{
        if(!input.value.trim()&&!widget.contains(event.relatedTarget)&&!widget.classList.contains("is-department-open"))widget.classList.remove("is-editing");
      });
      controls.push({id,widget,deps,input,toggle,placeholder:input.placeholder});
    }
    stops.addEventListener("click",()=>{
      showStops=!showStops;card.classList.toggle("premium-stops-open",showStops);
      stops.setAttribute("aria-expanded",String(showStops));
      if(showStops){
        const target=stopBody.hidden?login:stopBody;
        target?.scrollIntoView?.({block:"nearest",behavior:"smooth"});
      }
    });
    function sync(){
      const state=getState(),quick=doc.body.classList.contains("journey-mode-quick");
      for(const c of controls){
        const selected=c.widget.classList.contains("is-selected");
        const department=c.deps.querySelector("select")?.value||c.deps.querySelector("button.selected")?.textContent||"Uruguay";
        c.toggle.textContent="Buscar en "+department+" · Cambiar";
        c.input.placeholder=quick?(c.id==="origin-picker"?"¿Dónde te buscamos?":"¿A dónde vamos?"):c.placeholder;
        if(selected){c.widget.classList.remove("is-editing","is-department-open");c.toggle.setAttribute("aria-expanded","false");}
      }
      const count=state.stops.filter(Boolean).length;
      stops.textContent=count?count+(count===1?" parada intermedia":" paradas intermedias")+" · Editar":"Agregar paradas (opcional)";
      stops.setAttribute("aria-expanded",String(showStops));
      if(!quick)return;
      const points=[state.origin,state.destination].filter(Boolean).map(p=>[p.lat,p.lng]);
      const geometry=state.geometry;
      const key=JSON.stringify(geometry||points)+":"+Math.round(mapHost.clientWidth||355);
      if(key!==lastMap){
        lastMap=key;
        if(geometry)root.TrasladosPremiumMap.render(mapHost,geometry,{height:340});
        else root.TrasladosPremiumMap.overview(mapHost,points,{height:340});
      }
      caption.textContent=geometry?"Tu recorrido por calles · sin tráfico en vivo":points.length===2?"Origen y destino elegidos · ruta pendiente de verificar":points.length?"Ubicación elegida · agregá el destino para ver la ruta":"Montevideo y Ciudad de la Costa · elegí tu recorrido";
      if(state.metrics){
        const m=state.metrics;
        caption.textContent=m.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km · "+m.durationMin+" min · "+(Number.isFinite(m.referenceFareUyu)&&m.referenceFareUyu>0?"$ "+m.referenceFareUyu.toLocaleString("es-UY",{maximumFractionDigits:2})+" orientativos":"Precio a confirmar");
      }
      const originalRetry=doc.querySelector("#route-overview .route-retry");
      retry.hidden=points.length!==2||!originalRetry||originalRetry.hidden;
      if(summary){
        const verified=Boolean(state.metrics&&Number.isFinite(state.metrics.distanceKm)&&state.metrics.distanceKm>0
          &&Number.isFinite(state.metrics.durationMin)&&state.metrics.durationMin>=0);
        summary.dataset.verified=String(verified);
        const m=state.metrics;
        if(verified){
          fareValue.textContent=Number.isFinite(m.referenceFareUyu)&&m.referenceFareUyu>0
            ?"$ "+m.referenceFareUyu.toLocaleString("es-UY",{maximumFractionDigits:2}):"A confirmar";
          kmValue.textContent=m.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km";
          durationValue.textContent=Math.round(m.durationMin)+" min";
          caption.textContent="Ruta estimada del viaje por calles · sin tráfico en vivo";
        }else{
          const error=points.length===2&&!retry.hidden;
          fareValue.textContent=error?"A confirmar":points.length===2?"Calculando…":"A confirmar";
          kmValue.textContent=error?"Sin estimación":points.length===2?"Calculando…":"—";
          durationValue.textContent=error?"Sin estimación":points.length===2?"Calculando…":"—";
          caption.textContent=error?"No pudimos verificar el recorrido. Reintentá el cálculo.":
            points.length===2?"Calculando recorrido…":"Elegí origen y destino para ver la ruta";
        }
      }
    }
    doc.addEventListener("traslados:route-updated",sync);
    const size=new ResizeObserver(()=>{lastMap="";sync();});size.observe(mapHost);
    return Object.freeze({sync});
  }
  root.TrasladosPremiumRoute=Object.freeze({mount});
})(window);
