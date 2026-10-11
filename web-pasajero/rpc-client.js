"use strict";
/* Transporte RPC de Supabase sin UI, tokens persistidos ni reintentos:
 * el llamado a reservas NO se debe repetir automáticamente ante timeout,
 * porque el servidor pudo haberlo procesado.
 * El llamador controla conexión y sesión; aquí solo se hace el POST.
 */
(function(root){
  async function call({api,publishableKey,name,payload={},request=fetch}){
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),18000);
    try{
      const res=await request(api+"/rest/v1/rpc/"+encodeURIComponent(name),{
        method:"POST",mode:"cors",
        headers:{"apikey":publishableKey,"Content-Type":"application/json","Accept":"application/json"},
        body:JSON.stringify(payload),signal:controller.signal,cache:"no-store"
      });
      const content=await res.text();let parsed;
      try{parsed=content?JSON.parse(content):null;}catch{parsed=content;}
      if(!res.ok)throw new Error(parsed?.message||parsed?.error||"Servicio temporalmente no disponible");
      return parsed;
    }catch(e){
      if(e?.name==="AbortError")throw new Error("La conexión tardó demasiado. Revisá Mis traslados antes de volver a enviar para evitar duplicados.");
      throw e;
    }finally{clearTimeout(timer);}
  }
  root.TrasladosRpc=Object.freeze({call});
})(window);
