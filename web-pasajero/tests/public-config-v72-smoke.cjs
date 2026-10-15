"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,"..");
const source=fs.readFileSync(path.join(root,"public-config.js"),"utf8");
const app=fs.readFileSync(path.join(root,"app.js"),"utf8");
const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
const sw=fs.readFileSync(path.join(root,"sw.js"),"utf8");
const url="https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/";
function snapshot(version,revision,customUrl){
 return {
  schema_version:1,revision,
  links:{booking_url:url,client_apk_url:customUrl||
    url+"downloads/traslados-cliente-premium-v"+version+".apk"},
  releases:{cliente:{version:String(version)},mapa:{version:"R25.24"}}
 };
}
function scenario({data=snapshot(16,2),initial={},broken=false}={}){
 const stored={...initial}, requests=[],subs=[];
 const storage={
  getItem:key=>stored[key]??null,
  setItem:(key,value)=>{stored[key]=String(value);}
 };
 const browser={localStorage:storage};
 const ctx={
  window:browser,URL,AbortController,Date,JSON,Number,String,Boolean,Object,Array,
  setTimeout,clearTimeout,
  fetch:async(resource,options)=>{
    requests.push({resource,options});
    if(broken)throw Error("offline");
    return {ok:true,text:async()=>JSON.stringify(data)};
  }
 };
 vm.runInNewContext(source,ctx,{filename:"public-config.js"});
 return {api:browser.TrasladosPublicConfig,requests,stored,storage,subs};
}
(async()=>{
 const a=scenario();
 assert.equal(a.api.get().clientVersion,"16","safe signed APK when no cache");
 assert.equal(a.api.get().source,"fallback");
 assert.equal(a.api.allowedLink("client_apk_url",
   "https://evil.example/downloads/traslados-cliente-premium-v99.apk"),false);
 assert.equal(a.api.allowedLink("client_apk_url",
   url+"downloads/traslados-cliente-premium-v16.apk?redirect=https://bad"),false);
 assert.equal(a.api.allowedLink("client_apk_url",
   url+"downloads/traslados-cliente-premium-v16.apk"),true);
 let published=0;const stop=a.api.subscribe(()=>published++);
 await a.api.refresh();
 assert.equal(a.requests.length,1,"one background RPC");
 assert.equal(a.requests[0].options.credentials,"omit","no account/session credentials");
 assert.equal(a.requests[0].options.body,"{}","read-only no identifying parameters");
 assert(a.requests[0].resource.endsWith("/rpc/public_app_config_v1"));
 assert.equal(a.api.get().revision,2);
 assert.equal(published,1);
 await a.api.refresh();
 assert.equal(a.requests.length,1,"TTL prevents repeated database access");
 stop();
 const v17=scenario({data:snapshot(17,3),initial:a.stored});
 assert.equal(v17.api.get().clientVersion,"16","cache reused immediately before update");
 await v17.api.refresh({force:true});
 assert.equal(v17.api.get().clientVersion,"17","next signed release via Supabase without PWA rebuild");
 assert(v17.api.get().clientApkUrl.endsWith("premium-v17.apk"));
 const offline=scenario({broken:true,initial:v17.stored});
 await offline.api.refresh({force:true});
 assert.equal(offline.api.get().clientVersion,"17","offline last known good");
 const attack=scenario({data:snapshot(19,4,"https://evil.example/client-v19.apk"),initial:v17.stored});
 await attack.api.refresh({force:true});
 assert.equal(attack.api.get().clientVersion,"17","reject foreign host/URL");
 const mismatch=scenario({data:snapshot(19,4,url+"downloads/traslados-cliente-premium-v20.apk"),initial:v17.stored});
 await mismatch.api.refresh({force:true});
 assert.equal(mismatch.api.get().clientVersion,"17","reject URL/version mismatch");
 const old=scenario({data:snapshot(16,1),initial:v17.stored});
 await old.api.refresh({force:true});
 assert.equal(old.api.get().revision,3,"old revision cannot roll back cache");
 assert(html.includes('<script src="./public-config.js?v=1" defer></script>'));
 assert(html.indexOf("public-config.js?v=1")<html.indexOf("app.js?v=74"));
 assert(html.includes("install-apk-hint")&&!html.includes("Cliente Premium v15, firmada"));
 assert(sw.includes('traslados-cliente-pwa-v74')&&sw.includes("./public-config.js"));
 assert(app.includes("const catalog=window.TrasladosPublicConfig;"));
 assert(app.includes("catalog?.subscribe?.(applyPublishedApk)")&&app.includes("void catalog?.refresh?.()"));
 assert(app.includes("if(apkLink.href!==candidate)return;"),"stale HEAD cannot reinstate old APK");
 console.log("PASS v72: public config cache/TTL/rollback/offline/security + dynamic APK install UI contract");
})().catch(error=>{console.error(error);process.exitCode=1;});
