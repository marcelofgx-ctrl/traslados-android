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
  // The local "signed in" label is NOT server-side proof: testing DB resets
  // can invalidate stored sessions. Never repeatedly send a rejected token.
  let rejectedSessionToken=null;
  async function pickup({api,publishableKey,originLat,originLng,sessionToken=null,request=fetch}){
    // Public fallback uses the SAME rate-limited trusted Edge Function.
    // One authorized attempt can fail with login_required, then at most one
    // visitor request. Later polls go directly to guest until re-login.
    const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),18000);
    try{
      const send=async(token)=>{
        const response=await request(api+"/functions/v1/pickup-eta",{
          method:"POST",mode:"cors",cache:"no-store",signal:ctrl.signal,
          headers:{"apikey":publishableKey,"Content-Type":"application/json"},
          body:JSON.stringify({
            ...(token?{sessionToken:token}:{}),originLat,originLng
          })
        });
        const data=await response.json().catch(()=>({available:false,reason:"temporarily_unavailable"}));
        return {data,status:response.status};
      };
      const previouslyInvalid=Boolean(sessionToken&&sessionToken===rejectedSessionToken);
      if(previouslyInvalid){
        const guest=await send(null);
        return {...guest.data,sessionInvalid:true,estimateType:guest.data.estimateType||"coarse"};
      }
      const first=await send(sessionToken);
      if(sessionToken&&first.status===401&&first.data?.reason==="login_required"){
        rejectedSessionToken=sessionToken;
        // A revoked account token cannot silently block public ETA.
        // No attempt is made to bypass visitor quotas or other Edge guards.
        const guest=await send(null);
        return {...guest.data,sessionInvalid:true,estimateType:guest.data.estimateType||"coarse"};
      }
      return first.data;
    }finally{clearTimeout(timer);}
  }
  root.TrasladosETA=Object.freeze({availability,pickup});
})(window);
