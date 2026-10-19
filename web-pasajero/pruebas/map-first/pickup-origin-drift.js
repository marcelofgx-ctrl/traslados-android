"use strict";
/* PICKUP-ORIGIN-STALE-001: observe only after explicit GPS-origin selection.
 * A detected move never changes the pickup location without the user's tap.
 */
(function(root){
  function meters(a,b){
    if(!a||!b||![a.lat,a.lng,b.lat,b.lng].every(Number.isFinite))return NaN;
    const rad=Math.PI/180,dLat=(b.lat-a.lat)*rad,dLng=(b.lng-a.lng)*rad;
    const h=Math.sin(dLat/2)**2+Math.cos(a.lat*rad)*Math.cos(b.lat*rad)*Math.sin(dLng/2)**2;
    return 12742000*Math.asin(Math.min(1,Math.sqrt(Math.max(0,h))));
  }
  function displacement(origin,position,now=Date.now()){
    if(origin?.source!=="gps"||!position?.coords)return null;
    const {latitude,longitude,accuracy}=position.coords;
    const timestamp=Number(position.timestamp);
    if(![latitude,longitude,accuracy,timestamp].every(Number.isFinite)||
       accuracy<0||accuracy>80||timestamp>now+5000||now-timestamp>90000)return null;
    const distance=meters(origin,{lat:latitude,lng:longitude});
    return Number.isFinite(distance)&&distance>=250?Math.round(distance):null;
  }
  function mount(doc,{getOrigin,isActive,onRefresh}){
    const anchor=doc.getElementById("origin-picker");
    if(!anchor)throw Error("Falta selector del origen");
    const banner=doc.createElement("section");
    banner.className="pickup-origin-drift-notice";
    banner.hidden=true;banner.setAttribute("role","status");
    const title=doc.createElement("strong");
    title.textContent="Te alejaste del punto de recogida";
    const message=doc.createElement("p");
    message.textContent="Tu origen sigue siendo el que elegiste. ¿Querés actualizarlo usando tu GPS actual?";
    const actions=doc.createElement("div");actions.className="pickup-origin-drift-actions";
    const action=(caption,handler)=>{
      const button=doc.createElement("button");
      button.type="button";button.textContent=caption;
      button.addEventListener("click",handler);actions.append(button);
    };
    let watch=null,observing=false,candidateAt=0,ignored=false;
    function stop(){
      if(watch!==null){try{root.navigator?.geolocation?.clearWatch(watch);}catch{}watch=null;}
      candidateAt=0;
    }
    function sync(){
      const should=observing&&!ignored&&banner.hidden
        &&doc.visibilityState==="visible"&&isActive()&&getOrigin()?.source==="gps";
      if(!should){stop();return;}
      if(watch!==null||!root.navigator?.geolocation?.watchPosition)return;
      try{
        watch=root.navigator.geolocation.watchPosition(fix=>{
          if(doc.visibilityState!=="visible"||!isActive()||ignored||!banner.hidden){stop();return;}
          const distance=displacement(getOrigin(),fix);
          if(distance===null){candidateAt=0;return;}
          const now=Date.now();
          if(!candidateAt){candidateAt=now;return;}
          if(now-candidateAt<4000)return; // two readings, not one GPS glitch
          candidateAt=0;banner.hidden=false;stop();
        },()=>stop(),{enableHighAccuracy:false,maximumAge:30000,timeout:20000});
      }catch{stop();}
    }
    action("Actualizar origen",()=>{
      banner.hidden=true;ignored=false;stop();void onRefresh();
    });
    action("Mantener origen",()=>{
      banner.hidden=true;ignored=true;stop();
    });
    banner.append(title,message,actions);anchor.insertAdjacentElement("afterend",banner);
    doc.addEventListener("visibilitychange",sync);
    root.addEventListener("pagehide",stop);
    return Object.freeze({
      onOriginSelected(){
        stop();banner.hidden=true;ignored=false;
        observing=getOrigin()?.source==="gps";
        sync();
      },
      sync,stop
    });
  }
  root.TrasladosOriginDrift=Object.freeze({meters,displacement,mount});
})(window);
