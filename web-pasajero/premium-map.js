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
    // Fit both independently verified legs. The secondary route is ONLY
    // provided by authorized reservation tracking, never by public presence.
    const secondary=Array.isArray(options.secondaryGeometry)&&options.secondaryGeometry.length>=2
      ?options.secondaryGeometry.filter(p=>project(p)):null;
    const vehicle=options.driverPosition&&project([options.driverPosition.lat,options.driverPosition.lng])
      ?options.driverPosition:null;
    const fitExtra=extra.map(m=>[m.lat,m.lng])
      .concat(secondary||[],vehicle?[[vehicle.lat,vehicle.lng]]:[]);
    // UI camera only: the live passenger's natural framing follows the LAST
    // verified vehicle fix, never a predicted coordinate. Manual pan/zoom
    // replaces this camera until the user presses the recenter control.
    const carFocus=options.cameraPosition&&
      project([options.cameraPosition.lat,options.cameraPosition.lng])
      ?options.cameraPosition:vehicle;
    // P2.9 viewport-only framing. At pickup, include the *verified*
    // driver-to-pickup line instead of cropping the route around the car.
    // During the trip show roughly twice the street area of P2.8.
    // User pan/pinch still takes precedence over auto-follow.
    const liveCamera=options.followVehicle===true&&carFocus&&!camera.custom
      ?(()=>{
        const current=project([carFocus.lat,carFocus.lng]);
        if(!current)return null;
        if(options.routeMode==="pickup"&&secondary&&secondary.length>=2){
          const fitted=layout(secondary,width,height,
            [[carFocus.lat,carFocus.lng]],null);
          if(fitted)return {x:fitted.centerX/fitted.scale,
            y:fitted.centerY/fitted.scale,zoom:Math.min(14,fitted.zoom)};
        }
        return {x:current[0],y:current[1],zoom:14};
      })()
      :null;
    const view=layout(geometry,width,height,fitExtra,camera.custom||liveCamera);
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
    frame.dataset.followVehicle=String(Boolean(liveCamera));
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
      path(secondary?"#f5f7f8":live?"#56cba0":"#2b866e",5,"premium-map-route");
      if(secondary){
        const plotted=secondary.map(project).filter(Boolean).map(p=>[
          (p[0]*view.scale-view.centerX)+width/2,
          (p[1]*view.scale-view.centerY)+height/2
        ]);
        const pathString=plotted.map(p=>p.map(z=>z.toFixed(2)).join(",")).join(" ");
        for(const [klass,stroke,weight] of [
          ["premium-map-approach-shadow","#0d2931",10],
          ["premium-map-approach","#59dfb6",6]
        ]){
          const leg=document.createElementNS(NS,"polyline");
          leg.setAttribute("points",pathString);leg.setAttribute("fill","none");
          leg.setAttribute("stroke",stroke);leg.setAttribute("stroke-width",String(weight));
          leg.setAttribute("stroke-linejoin","round");leg.setAttribute("stroke-linecap","round");
          leg.setAttribute("class",klass);svg.append(leg);
        }
        // Light running along the VERIFIED car → pickup polyline, never a
        // simulated car position or a new GPS / ETA calculation.
        if(options.routeMode==="pickup"&&vehicle){
          const flow=document.createElementNS(NS,"polyline");
          flow.setAttribute("points",pathString);
          flow.setAttribute("fill","none");
          flow.setAttribute("stroke","#ddfff2");
          flow.setAttribute("stroke-width","2.8");
          flow.setAttribute("stroke-linecap","round");
          flow.setAttribute("stroke-linejoin","round");
          flow.setAttribute("class","premium-map-approach-flow");
          svg.append(flow);
        }
      }
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

    function drawCarMarker(p){
      // P2.9 white top-view sedan; the icon is not a synthetic GPS point.
      // Heading comes ONLY from two actual authorized GPS fixes.
      // No forward prediction or additional location polling.
      const g=document.createElementNS(NS,"g");
      g.setAttribute("class","premium-map-driver premium-map-driver-p29");
      g.setAttribute("transform","translate("+p[0]+" "+p[1]+")");
      const old=options.previousCar&&!options.overview
        ?project([options.previousCar.lat,options.previousCar.lng]):null;
      let dx=0,dy=0,pixels=0;
      if(old){
        dx=(old[0]*view.scale-view.centerX)+width/2-p[0];
        dy=(old[1]*view.scale-view.centerY)+height/2-p[1];
        pixels=Math.hypot(dx,dy);
        if(pixels>4&&pixels<160){
          // In screen coordinates, angle 0 points north; east is +90 degrees.
          camera.heading=Math.atan2(-dx,dy)*180/Math.PI;
        }
      }
      const shell=document.createElementNS(NS,"g");
      shell.setAttribute("transform","rotate("+(camera.heading||0)+")");
      function shape(tag,attrs){
        const el=document.createElementNS(NS,tag);
        for(const [key,value] of Object.entries(attrs))el.setAttribute(key,String(value));
        shell.append(el);return el;
      }
      // Discreet ground shadow, round white body, tyres, glass and details.
      shape("ellipse",{cx:0,cy:2,rx:15.5,ry:23,fill:"#03262c",opacity:.24});
      shape("rect",{x:-14.5,y:-12,width:5.5,height:11,rx:2.3,fill:"#202f36"});
      shape("rect",{x:9,y:-12,width:5.5,height:11,rx:2.3,fill:"#202f36"});
      shape("rect",{x:-14.5,y:5,width:5.5,height:11,rx:2.3,fill:"#202f36"});
      shape("rect",{x:9,y:5,width:5.5,height:11,rx:2.3,fill:"#202f36"});
      shape("path",{d:"M-10.5 -17 Q-9 -20 0 -20 Q9 -20 10.5 -17 L12.5 -6 L12.5 13 Q11 19 6.5 20 L-6.5 20 Q-11 19 -12.5 13 L-12.5 -6 Z",
        fill:"#ffffff",stroke:"#71858d","stroke-width":1.2,"stroke-linejoin":"round"});
      shape("path",{d:"M-8 -12 Q0 -15 8 -12 L9 -5 Q0 -3 -9 -5 Z",fill:"#4c7789"});
      shape("path",{d:"M-9 4 Q0 1 9 4 L8 13 Q0 15 -8 13 Z",fill:"#638999"});
      shape("path",{d:"M-9 -3 Q0 -1 9 -3",fill:"none",stroke:"#dfe8ed","stroke-width":1});
      shape("path",{d:"M-8 16 Q0 17 8 16",fill:"none",stroke:"#c0d0d7","stroke-width":1});
      shape("rect",{x:-9.5,y:-17,width:4.2,height:2,rx:.9,fill:"#fff2b7"});
      shape("rect",{x:5.3,y:-17,width:4.2,height:2,rx:.9,fill:"#fff2b7"});
      shape("rect",{x:-9,y:17,width:4.2,height:1.8,rx:.7,fill:"#e26f70"});
      shape("rect",{x:4.8,y:17,width:4.2,height:1.8,rx:.7,fill:"#e26f70"});
      g.append(shell);svg.append(g);
      if(old&&pixels>4&&pixels<160&&
         typeof g.animate==="function"&&
         !root.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches){
        // Interpolate between real fixes; never continue driving after GPS stops.
        g.animate([
          {transform:"translate("+((p[0]+dx).toFixed(2))+"px,"+((p[1]+dy).toFixed(2))+"px)"},
          {transform:"translate("+p[0]+"px,"+p[1]+"px)"}
        ],{duration:1100,easing:"ease-out",fill:"none"});
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
      if(vehicle){
        // Primary line is the booked route, so never paint a car at its origin.
        drawMarker(start,options.routeMode==="pickup"?"P":"O","#49e0a7");
        drawMarker(end,"D","#ef8f70");
        const p=project([vehicle.lat,vehicle.lng]);
        const at=[(p[0]*view.scale-view.centerX)+width/2,
                  (p[1]*view.scale-view.centerY)+height/2];
        drawCarMarker(at);
      }else{
        // No car without recent authorized driver GPS.
        drawMarker(start,options.routeMode==="pickup"?"P":"O","#49e0a7");
        drawMarker(end,options.routeMode==="pickup"?"P":"D","#ef8f70");
      }
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
    control("◎",options.followVehicle?"Recentrar y seguir el vehículo":"Centrar la ruta",
      "premium-map-control-reset",()=>applyCamera(null));
    if(options.followVehicle){
      control("◇","Mostrar todo el recorrido","premium-map-control-route-all",()=>{
        const fit=layout(geometry,width,height,fitExtra,null);
        if(fit)applyCamera({x:fit.centerX/fit.scale,y:fit.centerY/fit.scale,zoom:fit.zoom});
      });
    }
    control(camera.explore?"✓":"✥","Activar o desactivar arrastrar y pellizcar el mapa",
      "premium-map-control-explore",()=>{
        camera.explore=!camera.explore;
        // Exploring freezes the current viewport, preventing the verified
        // GPS poll from fighting a passenger's pan / pinch gestures.
        if(camera.explore&&!camera.custom){
          camera.custom={x:view.centerX/view.scale,y:view.centerY/view.scale,zoom:view.zoom};
        }
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
