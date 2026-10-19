"use strict";
/* Public, read-only configuration shared with Android Mapa.
 * Only verified installation links; never booking or authentication logic.
 * Supabase is queried at most once per 30 min, in background; last good snapshot
 * remains valid offline. No passenger identity, cookies or tokens are sent.
 */
(function(root){
  const ENDPOINT="https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/public_app_config_v1";
  const PUBLISHABLE_KEY="sb_publishable_HnbMZW2dKpm6mBq-y5qkaA_Jlfx4BB9";
  const KEY="traslados.public.catalog.v1",TTL_MS=30*60*1000;
  const DEFAULT_APK="https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v16.apk";
  const DEFAULT_WEB="https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/";
  let pending=null,listeners=[];
  let lastAttempt=0;
  function readCache(){
    try {
      const item=JSON.parse(root.localStorage?.getItem(KEY)||"null");
      return valid(item?.snapshot)?item:null;
    }catch{return null;}
  }
  function allowedLink(kind,value){
    if(typeof value!=="string"||value.length>512)return false;
    try{
      const url=new URL(value);
      if(url.protocol!=="https:"||url.username||url.password||url.port||url.search||url.hash||
        url.hostname!=="marcelofgx-ctrl.github.io")return false;
      if(kind==="booking_url")return url.pathname==="/traslados-android/web-pasajero/";
      return kind==="client_apk_url" &&
        /^\/traslados-android\/web-pasajero\/downloads\/traslados-cliente-premium-v[1-9][0-9]*\.apk$/.test(url.pathname);
    }catch{return false;}
  }
  function valid(data){
    if(!data||typeof data!=="object"||Array.isArray(data)||
      data.schema_version!==1||!Number.isSafeInteger(data.revision)||data.revision<1)return false;
    const links=data.links;
    if(!links||!allowedLink("booking_url",links.booking_url)||
      !allowedLink("client_apk_url",links.client_apk_url))return false;
    const version=data.releases?.cliente?.version;
    // The database's current signed release is the only one we advertise.
    const release=String(version??"");
    const match=links.client_apk_url.match(/traslados-cliente-premium-v([0-9]+)\.apk$/);
    return Boolean(match&&match[1]===release);
  }
  function get(){
    const cached=readCache();
    const links=cached?.snapshot.links;
    return Object.freeze({
      revision:cached?.snapshot.revision||0,
      source:cached?"cache":"fallback",
      bookingUrl:links?.booking_url||DEFAULT_WEB,
      clientApkUrl:links?.client_apk_url||DEFAULT_APK,
      clientVersion:cached?.snapshot.releases?.cliente?.version||"16"
    });
  }
  function notify(){
    const state=get();
    for(const cb of [...listeners])try{cb(state);}catch{}
  }
  function subscribe(cb){
    if(typeof cb!=="function")return ()=>{};
    listeners.push(cb);
    return ()=>{listeners=listeners.filter(f=>f!==cb);};
  }
  async function refresh({force=false}={}){
    const cached=readCache(),now=Date.now();
    if(!force && ((cached&&now-cached.checkedAt<TTL_MS)||(lastAttempt&&now-lastAttempt<TTL_MS)))
      return get();
    if(pending)return pending;
    lastAttempt=now;
    pending=(async()=>{
      const controller=new AbortController();
      const timer=setTimeout(()=>controller.abort(),5500);
      try{
        const result=await fetch(ENDPOINT,{
          method:"POST",mode:"cors",cache:"no-store",credentials:"omit",
          headers:{"apikey":PUBLISHABLE_KEY,"Accept":"application/json",
            "Content-Type":"application/json"},body:"{}",signal:controller.signal
        });
        if(!result.ok)return get();
        const raw=await result.text();
        if(raw.length>16000)return get();
        const data=JSON.parse(raw);
        if(!valid(data))return get();
        if(cached && data.revision<cached.snapshot.revision)return get();
        try{root.localStorage?.setItem(KEY,JSON.stringify({
          snapshot:data,checkedAt:Date.now()
        }));}catch{}
        notify();
      }catch{
        // The last good snapshot or the safe signed-v16 fallback still applies.
      }finally{clearTimeout(timer);}
      return get();
    })().finally(()=>{pending=null;});
    return pending;
  }
  root.TrasladosPublicConfig=Object.freeze({get,refresh,subscribe,allowedLink});
})(window);
