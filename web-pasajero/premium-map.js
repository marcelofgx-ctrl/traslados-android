"use strict";
/* Mapa real progresivo sin dependencias: usa SOLO geometría de ruta verificada.
   Los mosaicos OSM son opcionales; la polilínea conserva sus coordenadas reales.
   No calcula ETA, no lee GPS, ni guarda ubicaciones del conductor. */
(function(root){
  // UI camera is local to each map host, never stored with driver/customer data.
  const cameras=new WeakMap();
  const clamp=(value,min,max)=>Math.max(min,Math.min(max,value));
  function routeIdentity(geometry,mode){
    const pair=geometry[geometry.length-1]||[];
    const start=geometry[0]||[];
    const end=pair.map(n=>Number(n).toFixed(4)).join(",");
    const first=start.map(n=>Number(n).toFixed(4)).join(",");
    // Driver GPS changes at the start of the live leg; keep a manually
    // positioned viewport unless destination or phase changes.
    return (mode||"contract")+"|"+end+(mode==="pickup"||mode==="ride"?"":"|"+first);
  }
  function project(pair){
    if(!Array.isArray(pair)||pair.length!==2)return null;
    const lat=Number(pair[0]),lng=Number(pair[1]);
    if(!Number.isFinite(lat)||!Number.isFinite(lng)||lat < -85||lat>85||lng < -180||lng>180)return null;
    const phi=lat*Math.PI/180;
    return [(lng+180)/360,(1-Math.log(Math.tan(phi)+1/Math.cos(phi))/Math.PI)/2];
  }
  function layout(geometry,width=355,height=240,extraPoints=[],camera=null){
    if(!Array.isArray(geometry)||geometry.length<2||geometry.length>20000)return null;
    const points=geometry.map(project);
    if(points.some(p=>!p))return null;
    const fitPoints=points.concat(Array.isArray(extraPoints)?extraPoints.map(project).filter(Boolean):[]);
    const xs=fitPoints.map(p=>p[0]),ys=fitPoints.map(p=>p[1]);
    const minX=Math.min(...xs),maxX=Math.max(...xs);
    const minY=Math.min(...ys),maxY=Math.max(...ys);
    const deltaX=Math.max(0.0000001,maxX-minX),deltaY=Math.max(0.0000001,maxY-minY);
    const fitted=Math.floor(Math.log2(Math.min(
      Math.max(1,width-48)/(256*deltaX),Math.max(1,height-48)/(256*deltaY)
    )));
    const zoom=camera?clamp(camera.zoom,8,19):clamp(fitted,8,18);
    const scale=256*2**zoom;
    const centerX=(camera?camera.x:(minX+maxX)/2)*scale;
    const centerY=(camera?camera.y:(minY+maxY)/2)*scale;
    return {width,height,zoom,scale,centerX,centerY,
      points:points.map(p=>[(p[0]*scale-centerX)+width/2,(p[1]*scale-centerY)+height/2])};
  }
  function render(host,geometry,options={}){
    const width=Math.max(220,Math.min(750,Math.round(host.clientWidth||355)));
    const proposed=Number(options.height);
    const height=options.height!=null&&Number.isFinite(proposed)
      ?Math.max(160,Math.min(options.fullscreen?1500:560,Math.round(proposed)))
      :width<400?232:258;
    // Extra markers are real known route locations, not invented vehicle positions.
    const extra=Array.isArray(options.extraMarkers)?options.extraMarkers.filter(m=>
      m&&project([m.lat,m.lng])&&["O","P","D"].includes(m.label)):[];
    const identity=routeIdentity(geometry,options.overview?"overview":options.routeMode||"contract");
    let camera=cameras.get(host);
    if(!camera||camera.identity!==identity){
      camera={identity,custom:null,explore:false};
      cameras.set(host,camera);
    }
    const view=layout(geometry,width,height,extra.map(m=>[m.lat,m.lng]),camera.custom);
    if(!view)return false;
    host.replaceChildren();
    const frame=document.createElement("div");
    frame.className="premium-real-map";
    frame.setAttribute("role","group");
    const live=options.routeMode==="pickup"||options.routeMode==="ride";
    const knownOverview=Array.isArray(options.overviewPoints)?options.overviewPoints:[];
    frame.setAttribute("aria-label",options.overview?"Mapa de orientación con los puntos de origen y destino seleccionados, sin inventar una ruta vial":
      options.routeMode==="pickup"?"Posición GPS reciente del automóvil, ruta por calles hasta la recogida y destino marcado":
      options.routeMode==="ride"?"Posición GPS reciente del automóvil con pasajero a bordo, ruta por calles hasta el destino":
      "Mapa del recorrido contratado entre origen y destino; no muestra el automóvil");
    frame.style.height=height+"px";
    frame.dataset.zoom=String(view.zoom);
    frame.dataset.manual=String(Boolean(camera.custom));
    frame.dataset.explore=String(camera.explore);
    // Leave vertical swipes to page scrolling until Explorar is enabled.
    frame.style.touchAction=camera.explore?"none":"pan-y";
    const tiles=document.createElement("div");tiles.className="premium-map-tiles";
    const scale=view.scale;
    const x0=Math.floor((view.centerX-width/2)/256),x1=Math.floor((view.centerX+width/2)/256);
    const y0=Math.floor((view.centerY-height/2)/256),y1=Math.floor((view.centerY+height/2)/256);
    let n=0, loaded=0;
    // Only viewport tiles; enough tiles to cover all mobile/tablet edges.
    // Browser cache handles repeats; never prefetch outside the visible map.
    for(let y=y0;y<=y1;y++)for(let x=x0;x<=x1;x++){
      if(y<0||y>=2**view.zoom||n>=25)continue;
      n++;
      const img=document.createElement("img");img.loading="eager";img.decoding="async";
      img.alt="";img.referrerPolicy="strict-origin-when-cross-origin";
      img.src="https://tile.openstreetmap.org/"+view.zoom+"/"+((x%(2**view.zoom)+2**view.zoom)%(2**view.zoom))+"/"+y+".png";
      img.style.left=(x*256-(view.centerX-width/2))+"px";
      img.style.top=(y*256-(view.centerY-height/2))+"px";
      img.width=256;img.height=256;
      img.addEventListener("load",()=>{loaded++;frame.dataset.tiles=String(loaded);},{once:true});
      img.addEventListener("error",()=>{img.remove();frame.dataset.tileError="true";},{once:true});
      tiles.append(img);
    }
    const NS="http://www.w3.org/2000/svg";
    const svg=document.createElementNS(NS,"svg");
    svg.setAttribute("viewBox","0 0 "+width+" "+height);
    svg.setAttribute("preserveAspectRatio","none");
    svg.setAttribute("aria-hidden","true");svg.classList.add("premium-map-overlays");
    const sampled=view.points.filter((_,i)=>i===0||i===view.points.length-1||i%Math.max(1,Math.floor(view.points.length/750))===0);
    function path(stroke,strokeWidth,klass){
      const line=document.createElementNS(NS,"polyline");
      line.setAttribute("points",sampled.map(p=>p.map(z=>z.toFixed(2)).join(",")).join(" "));
      line.setAttribute("fill","none");line.setAttribute("stroke",stroke);
      line.setAttribute("stroke-width",String(strokeWidth));
      line.setAttribute("stroke-linejoin","round");line.setAttribute("stroke-linecap","round");
      line.setAttribute("class",klass);svg.append(line);
    }
    if(!options.overview){
      path("#042e46",9,"premium-map-route-shadow");
      path(live?"#56cba0":"#2b866e",5,"premium-map-route");
    }
    const start=view.points[0],end=view.points[view.points.length-1];
    function drawMarker(p,name,color,isCar=false){
      const circle=document.createElementNS(NS,"circle");
      circle.setAttribute("cx",String(p[0]));circle.setAttribute("cy",String(p[1]));
      circle.setAttribute("r",isCar?"15":"10");circle.setAttribute("fill",color);
      circle.setAttribute("stroke",isCar?"#f5d08f":"#052b35");
      circle.setAttribute("stroke-width",isCar?"3":"3");
      svg.append(circle);
      const label=document.createElementNS(NS,"text");
      label.setAttribute("x",String(p[0]));label.setAttribute("y",String(p[1]+(isCar?5:3.5)));
      label.setAttribute("text-anchor","middle");
      label.setAttribute("fill","#062630");
      label.setAttribute("font-size",isCar?"17":"10");
      label.setAttribute("font-weight","800");
      label.textContent=name;svg.append(label);
      // Smooth only BETWEEN two verified GPS positions, never predict beyond
      // the last received fix or animate a car without passenger authorization.
      if(isCar&&options.previousCar&&!options.overview&&
         typeof circle.animate==="function"){
        const old=project([options.previousCar.lat,options.previousCar.lng]);
        if(old){
          const at=[(old[0]*view.scale-view.centerX)+width/2,
                    (old[1]*view.scale-view.centerY)+height/2];
          const dx=at[0]-p[0],dy=at[1]-p[1],pixels=Math.hypot(dx,dy);
          if(pixels>3&&pixels<170){
            const frames=[{transform:`translate(${dx}px,${dy}px)`},
                          {transform:"translate(0px,0px)"}];
            for(const part of [circle,label])
              part.animate(frames,{duration:1500,easing:"ease-out",fill:"none"});
          }
        }
      }
    }
    // In preview mode show only verified pickup/destination COORDINATES.
    // A pending or failed routing engine must never draw a fabricated road.
    if(options.overview){
      const plotted=knownOverview.map(pair=>project(pair)).filter(Boolean);
      for(let i=0;i<plotted.length&&i<2;i++){
        const point=plotted[i];
        const position=[(point[0]*view.scale-view.centerX)+width/2,
          (point[1]*view.scale-view.centerY)+height/2];
        drawMarker(position,i===0?"O":"D",i===0?"#38b98b":"#ed8270");
      }
    }else{
      // The car glyph is NEVER rendered without a fresh authorized GPS fix.
      if(live)drawMarker(start,"🚘","#f6e0ae",true);
      else drawMarker(start,"O","#49e0a7");
      drawMarker(end,options.routeMode==="pickup"?"P":"D","#ef8f70");
      for(const marker of extra){
      const projected=project([marker.lat,marker.lng]);
      if(!projected)continue;
      const at=[(projected[0]*view.scale-view.centerX)+width/2,
                (projected[1]*view.scale-view.centerY)+height/2];
      drawMarker(at,marker.label,marker.label==="D"?"#ef8f70":"#49e0a7");
      }
    }
    const credit=document.createElement("span");credit.className="premium-map-credit";
    credit.textContent="© OpenStreetMap contributors";
    const controls=document.createElement("div");
    controls.className="premium-map-controls";
    controls.setAttribute("aria-label","Controles del mapa");
    function control(text,label,klass,action){
      const btn=document.createElement("button");
      btn.type="button";btn.textContent=text;
      btn.className="premium-map-control "+klass;
      btn.setAttribute("aria-label",label);
      btn.addEventListener("click",event=>{event.stopPropagation();action();});
      controls.append(btn);return btn;
    }
    function applyCamera(next){
      camera.custom=next;
      cameras.set(host,camera);
      render(host,geometry,options);
    }
    function zoomBy(step){
      const normalized=camera.custom||{
        x:view.centerX/view.scale,y:view.centerY/view.scale,zoom:view.zoom
      };
      applyCamera({...normalized,zoom:clamp(normalized.zoom+step,8,19)});
    }
    control("+","Acercar mapa","premium-map-control-zoom-in",()=>zoomBy(1));
    control("−","Alejar mapa","premium-map-control-zoom-out",()=>zoomBy(-1));
    control("◎","Centrar la ruta","premium-map-control-reset",()=>applyCamera(null));
    control(camera.explore?"✓":"✥","Activar o desactivar arrastrar y pellizcar el mapa",
      "premium-map-control-explore",()=>{
        camera.explore=!camera.explore;
        cameras.set(host,camera);
        render(host,geometry,options);
      });
    const hint=document.createElement("span");
    hint.className="premium-map-gesture-hint";
    hint.textContent=camera.explore?"Mové o acercá con dos dedos · tocá ✓ para salir":"";
    hint.hidden=!camera.explore;
    const badge=document.createElement("div");
    badge.className="premium-map-view-label";
    badge.textContent=live?"● GPS del conductor":options.overview?
      knownOverview.length>=2?"● Origen  ·  Destino":"● Mapa de referencia":"● Ruta por calles";
    frame.append(tiles,svg,credit,badge,controls,hint);host.append(frame);
    // In explore mode drag/pinch changes only the viewport. Coordinates, the
    // authorized vehicle fix and route metrics are never modified.
    const pointers=new Map();
    let gesture=null,moveX=0,moveY=0,pinchRatio=1;
    function pointerPair(){
      const values=[...pointers.values()];
      return values.length<2?0:Math.hypot(values[0].x-values[1].x,values[0].y-values[1].y);
    }
    frame.addEventListener("pointerdown",event=>{
      if(!camera.explore||event.target.closest?.(".premium-map-controls"))return;
      if(event.pointerType==="mouse"&&event.button!==0)return;
      pointers.set(event.pointerId,{x:event.clientX,y:event.clientY,startX:event.clientX,startY:event.clientY});
      if(typeof frame.setPointerCapture==="function")frame.setPointerCapture(event.pointerId);
      if(pointers.size===1){
        gesture={kind:"pan"};moveX=0;moveY=0;
      }else if(pointers.size===2){
        gesture={kind:"pinch",length:pointerPair()};pinchRatio=1;
        tiles.style.transform="";svg.style.transform="";
      }
    });
    frame.addEventListener("pointermove",event=>{
      const p=pointers.get(event.pointerId);if(!p)return;
      p.x=event.clientX;p.y=event.clientY;
      if(gesture?.kind==="pan"&&pointers.size===1){
        moveX=p.x-p.startX;moveY=p.y-p.startY;
        const shift="translate("+moveX+"px,"+moveY+"px)";
        tiles.style.transform=shift;svg.style.transform=shift;
      }else if(gesture?.kind==="pinch"&&pointers.size===2){
        pinchRatio=clamp(pointerPair()/Math.max(12,gesture.length),.5,3);
        const scaling="scale("+pinchRatio+")";
        tiles.style.transform=scaling;svg.style.transform=scaling;
      }
    });
    function finish(event){
      if(!pointers.has(event.pointerId))return;
      pointers.delete(event.pointerId);
      if(pointers.size)return;
      if(gesture?.kind==="pinch"){
        const difference=Math.log2(pinchRatio);
        if(Math.abs(difference)>.15)zoomBy(difference>0?Math.max(1,Math.round(difference)):Math.min(-1,Math.round(difference)));
      }else if(gesture?.kind==="pan"&&(Math.abs(moveX)>5||Math.abs(moveY)>5)){
        const base=camera.custom||{x:view.centerX/view.scale,y:view.centerY/view.scale,zoom:view.zoom};
        applyCamera({...base,x:base.x-moveX/view.scale,y:base.y-moveY/view.scale});
      }
      gesture=null;
    }
    frame.addEventListener("pointerup",finish);
    frame.addEventListener("pointercancel",finish);
    return true;
  }
  function overview(host,points=[],options={}){
    const valid=Array.isArray(points)?points.filter(p=>project(p)):[];
    let bounds=[[-34.95,-56.4],[-34.72,-55.93]];
    if(valid.length===1){const [lat,lng]=valid[0];bounds=[[lat-.025,lng-.035],[lat+.025,lng+.035]];}
    else if(valid.length>1)bounds=valid;
    return render(host,bounds,{...options,overview:true,overviewPoints:valid.slice(0,2)});
  }
  root.TrasladosPremiumMap=Object.freeze({project,layout,render,overview});
})(window);
