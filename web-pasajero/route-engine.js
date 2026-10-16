"use strict";
/* Motor vial reutilizable: sin UI, sesión ni datos personales persistidos.
 * Solo acepta distancias y tiempos de carretera verificados.
 */
(function(root){
  const SHARED_ROUTER="https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate";
  const TTL=300000,cache=new Map(),failures=new Map();
  const reasons=new Set(["not_configured","temporarily_unavailable","no_route","invalid_points"]);
  function reason(value){return reasons.has(value)?value:"unverified_response";}
  function diagnostic(routeKey){return failures.get(routeKey)||null;}
  function key(points){
    if(!Array.isArray(points)||points.length<2||
       points.some(p=>!Number.isFinite(p?.lat)||!Number.isFinite(p?.lng)))
      throw new Error("El trayecto necesita origen y destino válidos.");
    return points.map(p=>p.lng.toFixed(6)+","+p.lat.toFixed(6)).join(";");
  }
  function verified(data){
    return data?.available===true
      &&Number.isFinite(data.distanceKm)&&data.distanceKm>0
      &&Number.isFinite(data.durationMin)&&data.durationMin>=0
      &&["openrouteservice","supabase_route_cache"].includes(data.source);
  }
  function peek(routeKey){
    const item=cache.get(routeKey);
    if(item&&item.expires>Date.now())return item.data;
    if(item)cache.delete(routeKey);
    return null;
  }
  function invalidate(routeKey){
    if(routeKey){cache.delete(routeKey);failures.delete(routeKey);}else{cache.clear();failures.clear();}
  }
  async function cachedRoadRoute(points,api,publishableKey,request){
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),3500);
    try{
      const response=await request(api+"/rest/v1/rpc/public_cached_route_preview_v1",{
        method:"POST",headers:{"apikey":publishableKey,"Content-Type":"application/json","Accept":"application/json"},
        body:JSON.stringify({p_points:points.map(p=>({lat:p.lat,lng:p.lng}))}),
        signal:controller.signal,cache:"no-store"
      });
      if(!response.ok)return null;
      const data=await response.json();
      return verified(data)&&data.source==="supabase_route_cache"?data:null;
    }catch{return null;}finally{clearTimeout(timer);}
  }
  async function referenceFare(data,api,publishableKey,request){
    if(!api||!publishableKey)return data;
    const next={...data};delete next.referenceFareUyu;
    const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),4500);
    try{
      const response=await request(api+"/rest/v1/rpc/public_reference_quote_v1?p_distance_km="+encodeURIComponent(data.distanceKm),{
        method:"GET",mode:"cors",credentials:"omit",cache:"no-store",signal:ctrl.signal,
        headers:{apikey:publishableKey,Accept:"application/json"}
      });
      if(!response.ok)return next;
      const quote=await response.json();
      if(quote?.available===true&&quote.currency==="UYU"&&Number.isFinite(quote.referenceFareUyu)&&quote.referenceFareUyu>0){
        next.referenceFareUyu=quote.referenceFareUyu;
        if(Number.isFinite(quote.rateUyuPerKm)&&quote.rateUyuPerKm>0)next.referenceRateUyuPerKm=quote.rateUyuPerKm;
      }
    }catch{/* Route stays verified even when price cannot be confirmed. */}
    finally{clearTimeout(timer);}
    return next;
  }
  async function estimate(points,{api,publishableKey,request=fetch}={}){
    const routeKey=key(points);
    const hit=peek(routeKey);
    if(hit)return hit;
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),7500);
    let data=null,problem="network_error";
    try{
      const response=await request(SHARED_ROUTER+"?points="+encodeURIComponent(routeKey),{
        headers:{Accept:"application/json"},signal:controller.signal,cache:"no-store"
      });
      let candidate=null;
      // A 503 from Cloudflare often includes a safe machine-readable reason.
      try{candidate=await response.json();}catch{}
      if(response.ok && verified(candidate))data=candidate;
      else if(candidate && typeof candidate==="object" && typeof candidate.reason==="string")
        problem=reason(candidate.reason);
      else problem=response.ok?"unverified_response":"http_error";
    }catch(e){
      problem=e?.name==="AbortError"?"timeout":"network_error";
    }finally{clearTimeout(timer);}
    if(!data)data=await cachedRoadRoute(points,api,publishableKey,request);
    if(data){
      data=await referenceFare(data,api,publishableKey,request);
      failures.delete(routeKey);
      cache.set(routeKey,{data,expires:Date.now()+TTL});
      if(cache.size>65)cache.clear();
    }else{
      // Never store coordinates, identifiers or API responses in diagnostics.
      failures.set(routeKey,{reason:problem,checkedAt:Date.now()});
      if(failures.size>65)failures.clear();
    }
    return data;
  }
  root.TrasladosRoutes=Object.freeze({key,verified,peek,invalidate,diagnostic,estimate});
})(window);
