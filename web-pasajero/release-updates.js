"use strict";
/* Public release catalog for website and native Cliente. No permissions, credentials or
 * background downloads. Android itself must confirm the APK installation. */
(function(root){
  const BASE="https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/";
  const TTL=30*60*1000;
  const LEGACY_CODES=Object.freeze({"14":202,"15":203,"16":204});
  let snapshot=null,lastCheck=0,pending=null;
  function nativeVersionCode(agent){
    const ua=String(agent||"");
    if(!/TrasladosClienteNative\/[0-9]+/i.test(ua))return null;
    const code=ua.match(/TrasladosNativeCode\/([0-9]+)/i);
    if(code){
      const n=Number(code[1]);
      return Number.isSafeInteger(n)&&n>0?n:null;
    }
    const legacy=ua.match(/TrasladosClienteNative\/([0-9]+)/i);
    return legacy?LEGACY_CODES[legacy[1]]||null:null;
  }
  function approvedRelease(item,kind){
    if(!item||typeof item!=="object"||Array.isArray(item)||
       !Number.isSafeInteger(item.versionCode)||item.versionCode<1||
       !/^[a-zA-Z0-9. -]{1,80}$/.test(item.versionName||"")||
       !/^[A-Za-z0-9 ._-]{1,80}$/.test(item.name||"")||
       !/^(?:v|R)[0-9]+(?:\.[0-9]+)*$/.test(item.displayVersion||"")||
       !/^[a-f0-9]{64}$/.test(item.sha256||""))return false;
    const names={
      cliente:["uy.com.traslados.cliente",/^traslados-cliente-premium-v[0-9]+\.apk$/],
      mapa:["uy.com.mapatrayectos",/^mapa-trayectos-r[0-9]+\.[0-9]+\.apk$/],
      conductor:["uy.com.traslados.conductor",/^traslados-conductor-r[0-9]+\.apk$/]
    };
    const expected=names[kind];
    return Boolean(expected&&item.packageName===expected[0]&&
      typeof item.file==="string"&&expected[1].test(item.file)&&
      !item.file.includes("..")&&!item.file.includes("%"));
  }
  function valid(data){
    if(!data||data.schemaVersion!==1||data.channel!=="stable"||
       !data.releases||typeof data.releases!=="object")return false;
    return ["cliente","mapa","conductor"].every(k=>approvedRelease(data.releases[k],k));
  }
  function urlFor(item){
    if(!item||!approvedRelease(item,
      item.packageName==="uy.com.traslados.cliente"?"cliente":
      item.packageName==="uy.com.mapatrayectos"?"mapa":"conductor"))return null;
    return new URL("downloads/"+item.file,BASE).href;
  }
  async function refresh(force=false){
    if(!force&&snapshot&&Date.now()-lastCheck<TTL)return snapshot;
    if(pending)return pending;
    lastCheck=Date.now();
    pending=(async()=>{
      try{
        const response=await fetch(new URL("releases.json",BASE),{cache:"no-store",credentials:"omit"});
        if(!response.ok)return snapshot;
        const raw=await response.text();
        if(raw.length>12000)return snapshot;
        const data=JSON.parse(raw);
        if(valid(data))snapshot=data;
      }catch{ /* Without connectivity, never invent available updates. */ }
      return snapshot;
    })().finally(()=>{pending=null;});
    return pending;
  }
  function current(){return snapshot;}
  async function availableApk(url){
    try{
      const response=await fetch(url,{method:"HEAD",cache:"no-store",credentials:"omit"});
      const type=(response.headers.get("content-type")||"").toLowerCase();
      const bytes=Number(response.headers.get("content-length")||"0");
      return response.ok&&bytes>10000&&!/text\/html|application\/json|text\/plain/.test(type);
    }catch{return false;}
  }
  async function checkNative(){
    const installed=nativeVersionCode(root.navigator?.userAgent);
    if(!installed)return;
    if(root.document?.visibilityState==="hidden")return;
    const notice=root.document?.getElementById("native-update-notice");
    const button=root.document?.getElementById("native-update-download");
    const headline=root.document?.getElementById("native-update-title");
    if(!notice||!button||!headline)return;
    const data=await refresh();
    const newest=data?.releases?.cliente;
    if(!approvedRelease(newest,"cliente")||newest.versionCode<=installed){
      notice.hidden=true;return;
    }
    if(root.sessionStorage?.getItem("traslados.release.defer."+newest.versionCode)==="1")return;
    const url=urlFor(newest);
    if(!url||!await availableApk(url)){notice.hidden=true;return;}
    headline.textContent="Actualización disponible: Cliente "+newest.displayVersion;
    button.href=url;
    button.setAttribute("download",newest.file);
    notice.hidden=false;
  }
  function start(){
    const later=root.document?.getElementById("native-update-later");
    const notice=root.document?.getElementById("native-update-notice");
    if(!notice||!later)return;
    later.addEventListener("click",()=>{
      const code=snapshot?.releases?.cliente?.versionCode;
      if(code)try{root.sessionStorage?.setItem("traslados.release.defer."+code,"1");}catch{}
      notice.hidden=true;
    });
    void checkNative();
    root.document.addEventListener("visibilitychange",()=>{
      if(root.document.visibilityState==="visible")void checkNative();
    });
    root.setInterval(()=>{
      if(root.document.visibilityState==="visible")void checkNative();
    },TTL);
  }
  root.TrasladosReleases=Object.freeze({
    nativeVersionCode,approvedRelease,valid,urlFor,current,refresh,availableApk,checkNative
  });
  if(root.document){
    if(root.document.readyState==="loading")
      root.document.addEventListener("DOMContentLoaded",start,{once:true});
    else start();
  }
})(window);
