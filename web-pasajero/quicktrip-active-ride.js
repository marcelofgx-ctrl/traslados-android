"use strict";
/* N1+N2: presentation of ONE already-authorized active booking. No RPCs,
 * write operations, location polling, price calculation or fictitious journey.
 * Keyboard/tap remains available alongside the optional swipe gesture.
 */
(function(root){
  const LIVE=new Set(["ACEPTADA","ACEPTADA_CLIENTE","CONFIRMADA","EN_VIAJE"]);
  function select(rows){
    const active=(Array.isArray(rows)?rows:[]).filter(r=>
      r&&r.id&&LIVE.has(String(r.status||"")));
    return {ride:active.length===1?active[0]:null,count:active.length};
  }
  function stage(ride){
    if(!ride)return "";
    if(ride.status==="EN_VIAJE")return "Viaje en curso";
    if(ride.pickup_phase==="EN_PUNTO")return "Tu conductor llegó";
    if(ride.pickup_phase==="EN_CAMINO")return "Tu conductor está en camino";
    return "Tu viaje fue aceptado";
  }
  function formatMoney(value){
    const n=Number(value);
    return Number.isFinite(n)&&n>0?
      "UYU "+new Intl.NumberFormat("es-UY",{maximumFractionDigits:0}).format(n):
      "Precio pendiente de confirmación";
  }
  const mk=(doc,tag,cls,txt)=>{
    const node=doc.createElement(tag);
    if(cls)node.className=cls;
    if(txt!==undefined)node.textContent=txt;
    return node;
  };
  let previousSignature="",expandedNewTrip=false;
  function render(rows,actions={}){
    const doc=root.document;
    if(!doc)return select(rows);
    const view=doc.getElementById("view-viaje");
    const host=doc.getElementById("quicktrip-live-trip");
    if(!view||!host)return select(rows);
    const picked=select(rows),r=picked.ride;
    const signature=JSON.stringify([picked.count,r?.id,r?.status,r?.pickup_phase,
      r?.origin_text,r?.destination_text,r?.quote_final_total]);
    view.classList.toggle("qt-has-active-ride",picked.count>0);
    if(picked.count===0)expandedNewTrip=false;
    view.classList.toggle("qt-show-new-trip",expandedNewTrip);
    host.hidden=picked.count===0;
    if(signature===previousSignature)return picked;
    previousSignature=signature;
    host.replaceChildren();
    if(!picked.count)return picked;
    const header=mk(doc,"header","qt-live-head");
    const heading=mk(doc,"div","qt-live-heading");
    heading.append(mk(doc,"span","qt-live-kicker","QUICK TRIP  ·  EN MOVIMIENTO"),
      mk(doc,"h2","qt-live-title",r?"Tu viaje activo":"Tus viajes activos"));
    header.append(heading,mk(doc,"span","qt-live-pulse","● EN DIRECTO"));
    host.append(header);
    if(!r){
      host.append(mk(doc,"p","qt-live-copy","Tenés más de un traslado activo. Elegí cuál querés consultar."));
      const choose=mk(doc,"button","qt-live-secondary","Ver mis traslados");
      choose.type="button";choose.addEventListener("click",()=>actions.onList?.());
      host.append(choose);
      return picked;
    }
    const status=mk(doc,"p","qt-live-status",stage(r));status.setAttribute("role","status");
    host.append(status);
    const route=mk(doc,"div","qt-live-route");
    const a=mk(doc,"div","qt-live-stop");
    a.append(mk(doc,"span","qt-live-pin qt-live-pin-origin","●"),
      mk(doc,"span","qt-live-stop-copy",String(r.origin_text||"Origen por confirmar")));
    const b=mk(doc,"div","qt-live-stop");
    b.append(mk(doc,"span","qt-live-pin qt-live-pin-destination","◆"),
      mk(doc,"span","qt-live-stop-copy",String(r.destination_text||"Destino por confirmar")));
    route.append(a,b);host.append(route);
    const footer=mk(doc,"div","qt-live-meta");
    footer.append(mk(doc,"strong","qt-live-fare",formatMoney(r.quote_final_total)),
      mk(doc,"span","qt-live-price-note","Importe informado en tu reserva"));
    host.append(footer);
    const rail=mk(doc,"div","qt-live-rail");
    rail.setAttribute("role","group");
    rail.setAttribute("aria-label","Deslizá para abrir el mapa de seguimiento");
    rail.append(mk(doc,"span","qt-live-rail-caption","Deslizá para ver el mapa"));
    const knob=mk(doc,"button","qt-live-knob","➜");
    knob.type="button";knob.setAttribute("aria-label","Abrir mapa de seguimiento");
    knob.setAttribute("title","Deslizá o presioná Enter para abrir el mapa");
    rail.append(knob);host.append(rail);
    let pressed=false,start=0,travel=0;
    const reset=()=>{pressed=false;travel=0;knob.style.transform="";rail.classList.remove("qt-sliding")};
    knob.addEventListener("pointerdown",e=>{
      if(e.pointerType==="mouse"&&e.button!==0)return;
      pressed=true;start=e.clientX;travel=0;
      knob.setPointerCapture?.(e.pointerId);rail.classList.add("qt-sliding");
    });
    knob.addEventListener("pointermove",e=>{
      if(!pressed)return;
      const max=Math.max(0,rail.clientWidth-knob.offsetWidth-12);
      travel=Math.min(max,Math.max(0,e.clientX-start));
      knob.style.transform="translateX("+travel+"px)";
    });
    knob.addEventListener("pointerup",()=>{
      if(!pressed)return;
      const max=Math.max(0,rail.clientWidth-knob.offsetWidth-12);
      const done=max>0&&travel>=max*.72;
      reset();
      if(done)actions.onOpen?.(r);
    });
    knob.addEventListener("pointercancel",reset);
    knob.addEventListener("keydown",e=>{
      if(e.key==="Enter"||e.key===" "){
        e.preventDefault();actions.onOpen?.(r);
      }
    });
    const controls=mk(doc,"div","qt-live-aux");
    const open=mk(doc,"button","qt-live-tap","Ver mapa sin deslizar");
    open.type="button";open.addEventListener("click",()=>actions.onOpen?.(r));
    const other=mk(doc,"button","qt-live-new-trip","Preparar otro viaje");
    other.type="button";other.setAttribute("aria-expanded",String(expandedNewTrip));
    other.addEventListener("click",()=>{
      expandedNewTrip=!expandedNewTrip;
      view.classList.toggle("qt-show-new-trip",expandedNewTrip);
      other.setAttribute("aria-expanded",String(expandedNewTrip));
      other.textContent=expandedNewTrip?"Ocultar nuevo viaje":"Preparar otro viaje";
    });
    controls.append(open,other);host.append(controls);
    return picked;
  }
  const api=Object.freeze({select,stage,render});
  root.QuickTripActiveRide=api;
  if(typeof module==="object"&&module.exports)module.exports=api;
})(typeof window!=="undefined"?window:globalThis);
