"use strict";
/* Full-map mobile presentation of the EXISTING Quick Trip booking contract.
 * No API calls, passenger position polling, price formula or booking mutations.
 * Map geometry and quote are injected from the existing verified route engine.
 */
(function(root){
  const doc=root.document;
  let screen=null,host=null,state=null,open=false,lastMapKey="";
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
    back.setAttribute("aria-label","Volver a Tu viaje");back.onclick=close;
    const address=make("div","qt-focus-address");
    address.append(make("small","", "DESTINO"),make("strong","qt-focus-address-text","Elegí destino"));
    const brand=make("span","qt-focus-brand","Quick Trip");
    top.append(back,address,brand);
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
    actions.append(retry,submit);
    const note=make("p","qt-focus-note","Sin cobro anticipado · Precio sujeto a confirmación del conductor");
    sheet.append(handle,heading,option,stats,route,message,actions,note);
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
  function toggleSheet(){
    if(!screen)return;const expanded=screen.classList.toggle("qt-focus-expanded");
    const handle=screen.querySelector(".qt-focus-handle");
    handle?.setAttribute("aria-expanded",String(expanded));
  }
  function sync(next){
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
    setup();screen.hidden=false;open=true;lastMapKey="";
    doc.body.classList.add("qt-focus-open");
    sync(next);
    screen.querySelector(".qt-focus-back").focus({preventScroll:true});
    return true;
  }
  function close(){
    if(!screen)return;
    screen.hidden=true;open=false;lastMapKey="";
    doc.body.classList.remove("qt-focus-open");
    screen.classList.remove("qt-focus-expanded");
    screen.querySelector(".qt-focus-handle")?.setAttribute("aria-expanded","false");
    // Release map tiles/listeners while overlay is hidden.
    host.replaceChildren();
    doc.querySelector(".premium-route-expand")?.focus({preventScroll:true});
  }
  function bindOpenButton(button,getState){
    button.addEventListener("click",()=>{
      const next=getState();
      if(!show(next))button.setAttribute("aria-label","Seleccioná origen y destino para abrir el mapa");
    });
  }
  root.QuickTripMapFocus=Object.freeze({sync,show,close,bindOpenButton,isOpen:()=>open});
})(window);
