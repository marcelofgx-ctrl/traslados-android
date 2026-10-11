"use strict";
/* Motor vial reutilizable: sin UI, sesión ni datos personales persistidos.
 * Solo acepta distancias y tiempos de carretera verificados.
 */
(function(root){
  const SHARED_ROUTER="https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate";
  const TTL=300000,cache=new Map();
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
    if(routeKey)cache.delete(routeKey);else cache.clear();
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
  async function estimate(points,{api,publishableKey,request=fetch}={}){
    const routeKey=key(points);
    const hit=peek(routeKey);
    if(hit)return hit;
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),7500);
    let data=null;
    try{
      const response=await request(SHARED_ROUTER+"?points="+encodeURIComponent(routeKey),{
        headers:{Accept:"application/json"},signal:controller.signal,cache:"no-store"
      });
      if(response.ok){
        const candidate=await response.json();
        if(verified(candidate))data=candidate;
      }
    }catch{
      // Sin distancia ficticia si el proveedor falla.
    }finally{clearTimeout(timer);}
    if(!data)data=await cachedRoadRoute(points,api,publishableKey,request);
    if(data){
      cache.set(routeKey,{data,expires:Date.now()+TTL});
      if(cache.size>65)cache.clear();
    }
    return data;
  }
  root.TrasladosRoutes=Object.freeze({key,verified,peek,invalidate,estimate});
})(window);
