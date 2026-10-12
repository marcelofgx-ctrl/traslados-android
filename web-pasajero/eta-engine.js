"use strict";
/* Cliente de disponibilidad / recogida: transporte HTTP sin interfaz.
 * Las cuotas, el consentimiento, el estado y la publicación GPS se
 * determinan en backend. La UI decide cuándo consultar y cómo presentar ETA.
 */
(function(root){
  async function availability({api,publishableKey,request=fetch}){
    const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),6000);
    try{
      const response=await request(api+"/rest/v1/rpc/public_driver_availability_v1",{
        method:"POST",mode:"cors",cache:"no-store",signal:ctrl.signal,
        headers:{"apikey":publishableKey,"Content-Type":"application/json"},
        body:"{}"
      });
      if(!response.ok)return null;
      const v=await response.json();
      if(typeof v?.available!=="boolean"||
         !["available_for_requests","not_available"].includes(v.status))return null;
      // La web no recibe coordenadas, velocidad ni identificadores del conductor.
      return {available:v.available,status:v.status,checkedAt:v.checkedAt,
        motionHint:v.motionHint==="moving"?"moving":"stationary"};
    }catch{return null;}finally{clearTimeout(timer);}
  }
  async function pickup({api,publishableKey,originLat,originLng,sessionToken=null,request=fetch}){
    // SQL authorization and the trusted road provider each have bounded latency.
    // Leave enough time for both while the UI deduplicates in-flight calls.
    const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),18000);
    try{
      const response=await request(api+"/functions/v1/pickup-eta",{
        method:"POST",mode:"cors",cache:"no-store",signal:ctrl.signal,
        headers:{"apikey":publishableKey,"Content-Type":"application/json"},
        body:JSON.stringify({
          ...(sessionToken?{sessionToken}:{}),originLat,originLng
        })
      });
      return await response.json().catch(()=>({available:false,reason:"temporarily_unavailable"}));
    }finally{clearTimeout(timer);}
  }
  root.TrasladosETA=Object.freeze({availability,pickup});
})(window);
