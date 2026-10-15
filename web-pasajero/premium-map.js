"use strict";
/* Mapa real progresivo sin dependencias: usa SOLO geometría de ruta verificada.
   Los mosaicos OSM son opcionales; la polilínea conserva sus coordenadas reales.
   No calcula ETA, no lee GPS, ni guarda ubicaciones del conductor. */
(function(root){
  function project(pair){
    if(!Array.isArray(pair)||pair.length!==2)return null;
    const lat=Number(pair[0]),lng=Number(pair[1]);
    if(!Number.isFinite(lat)||!Number.isFinite(lng)||lat < -85||lat>85||lng < -180||lng>180)return null;
    const phi=lat*Math.PI/180;
    return [(lng+180)/360,(1-Math.log(Math.tan(phi)+1/Math.cos(phi))/Math.PI)/2];
  }
  function layout(geometry,width=355,height=240){
    if(!Array.isArray(geometry)||geometry.length<2||geometry.length>20000)return null;
    const points=geometry.map(project);
    if(points.some(p=>!p))return null;
    const xs=points.map(p=>p[0]),ys=points.map(p=>p[1]);
    const minX=Math.min(...xs),maxX=Math.max(...xs);
    const minY=Math.min(...ys),maxY=Math.max(...ys);
    const deltaX=Math.max(0.0000001,maxX-minX),deltaY=Math.max(0.0000001,maxY-minY);
    const zoom=Math.max(8,Math.min(15,Math.floor(Math.log2(Math.min(
      Math.max(1,width-66)/(256*deltaX),Math.max(1,height-54)/(256*deltaY)
    )))));
    const scale=256*2**zoom;
    const centerX=(minX+maxX)/2*scale,centerY=(minY+maxY)/2*scale;
    return {width,height,zoom,scale,centerX,centerY,
      points:points.map(p=>[(p[0]*scale-centerX)+width/2,(p[1]*scale-centerY)+height/2])};
  }
  function render(host,geometry){
    const width=Math.max(290,Math.min(750,Math.round(host.clientWidth||355)));
    const height=width<400?232:258;
    const view=layout(geometry,width,height);
    if(!view)return false;
    host.replaceChildren();
    const frame=document.createElement("div");
    frame.className="premium-real-map";
    frame.setAttribute("role","img");
    frame.setAttribute("aria-label","Mapa cartográfico del trayecto por calles, con origen y destino");
    frame.style.height=height+"px";
    const tiles=document.createElement("div");tiles.className="premium-map-tiles";
    const scale=view.scale;
    const x0=Math.floor((view.centerX-width/2)/256),x1=Math.floor((view.centerX+width/2)/256);
    const y0=Math.floor((view.centerY-height/2)/256),y1=Math.floor((view.centerY+height/2)/256);
    let n=0, loaded=0;
    // Máximo 12 imágenes, solo en pantalla y sin descargas masivas.
    for(let y=y0;y<=y1;y++)for(let x=x0;x<=x1;x++){
      if(y<0||y>=2**view.zoom||n>=12)continue;
      n++;
      const img=document.createElement("img");img.loading="lazy";img.decoding="async";
      img.alt="";img.referrerPolicy="strict-origin-when-cross-origin";
      img.src="https://tile.openstreetmap.org/"+view.zoom+"/"+((x%(2**view.zoom)+2**view.zoom)%(2**view.zoom))+"/"+y+".png";
      img.style.left=(x*256-(view.centerX-width/2))+"px";
      img.style.top=(y*256-(view.centerY-height/2))+"px";
      img.width=256;img.height=256;
      img.addEventListener("load",()=>{loaded++;frame.dataset.tiles=String(loaded);},{once:true});
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
    path("#042e46",9,"premium-map-route-shadow");
    path("#6ce4ff",5,"premium-map-route");
    const start=view.points[0],end=view.points[view.points.length-1];
    for(const [p,name,color] of [[start,"O","#49e0a7"],[end,"D","#ef6e65"]]){
      const circle=document.createElementNS(NS,"circle");
      circle.setAttribute("cx",String(p[0]));circle.setAttribute("cy",String(p[1]));
      circle.setAttribute("r","10");circle.setAttribute("fill",color);
      circle.setAttribute("stroke","#052b35");circle.setAttribute("stroke-width","3");
      svg.append(circle);
      const label=document.createElementNS(NS,"text");
      label.setAttribute("x",String(p[0]));label.setAttribute("y",String(p[1]+3.5));
      label.setAttribute("text-anchor","middle");
      label.setAttribute("fill","#062630");
      label.setAttribute("font-size","10");label.setAttribute("font-weight","800");
      label.textContent=name;svg.append(label);
    }
    const credit=document.createElement("span");credit.className="premium-map-credit";
    credit.textContent="© OpenStreetMap contributors";
    frame.append(tiles,svg,credit);host.append(frame);
    return true;
  }
  root.TrasladosPremiumMap=Object.freeze({project,layout,render});
})(window);
