"use strict";
/*
 * Execute the REAL sw.js event handlers under an in-memory CacheStorage.
 * No browser network, no service worker deployment, no Supabase traffic.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const source=fs.readFileSync(path.resolve(__dirname,"../sw.js"),"utf8");
const origin="http://127.0.0.1:8126",scope=origin+"/web-pasajero/";
const handlers={},stores=new Map(),calls=[];
let networkDown=false;
function urlOf(r){return new URL(typeof r==="string"?r:r.url,scope).href;}
function response(tag){return {ok:true,tag,clone(){return response(this.tag)}};}
function storage(name){
  if(!stores.has(name))stores.set(name,new Map());
  const map=stores.get(name);
  return {
    async addAll(files){for(const f of files)map.set(urlOf(f),response("PRECACHE:"+f));},
    async put(r,res){map.set(urlOf(r),res.clone());},
    async match(r,opt={}){
      const exact=map.get(urlOf(r));
      if(exact)return exact;
      if(opt.ignoreSearch){
        const wanted=new URL(urlOf(r));wanted.search="";
        for(const [key,val] of map){const candidate=new URL(key);candidate.search="";if(candidate.href===wanted.href)return val;}
      }
      return undefined;
    }
  };
}
const caches={
  open:async name=>storage(name),
  keys:async()=>[...stores.keys()],
  delete:async name=>stores.delete(name)
};
const self={
 location:{origin},
 registration:{scope},
 addEventListener:(name,cb)=>{handlers[name]=cb;},
 skipWaiting:()=>Promise.resolve(),
 clients:{claim:()=>Promise.resolve()},
};
const fetch=async(req)=>{
  calls.push(urlOf(req));
  if(networkDown)throw Error("NETWORK_DOWN_TEST");
  return response("NETWORK:"+urlOf(req));
};
vm.runInNewContext(source,{
 self,caches,fetch,URL,Promise,console,JSON,String,
 setTimeout,clearTimeout
},{filename:"sw-real-v85.js"});
const makeEvent=(req)=>{
 const e={request:req,waiting:[],responded:null,
  waitUntil(p){this.waiting.push(Promise.resolve(p))},
  respondWith(p){this.responded=Promise.resolve(p)}};
 return e;
};
function req(path,method="GET",mode="same-origin"){return{url:new URL(path,scope).href,method,mode};}
async function flush(e){await Promise.all(e.waiting);}
(async()=>{
 const report={result:"IN_PROGRESS",cases:[],cacheVersion:null,productionWrites:0};
 assert(handlers.install&&handlers.activate&&handlers.fetch);
 stores.set("traslados-pwa-v83-old",new Map());
 stores.set("traslados-cliente-pwa-v77-old",new Map());
 stores.set("keep-unrelated-cache",new Map());
 const installing=makeEvent(null);handlers.install(installing);await flush(installing);
 let keys=await caches.keys();
 const current=keys.find(x=>x.includes("tu-viaje-compacto-v85"));
 assert(current,"CACHE v85 missing");
 assert((await storage(current).match("./quicktrip-clean.css")),"Stylesheet missing from offline install");
 assert((await storage(current).match("./app.js")),"Booking engine missing in precache");
 assert((await storage(current).match("./index.html"))===undefined,
  "Precache uses navigation scope, not index.html");
 report.cacheVersion=current;
 report.cases.push({name:"v85 precache of functional CSS/JS",status:"PASS"});
 const activate=makeEvent(null);handlers.activate(activate);await flush(activate);
 keys=await caches.keys();
 assert(!keys.includes("traslados-pwa-v83-old"));
 assert(!keys.includes("traslados-cliente-pwa-v77-old"));
 assert(keys.includes("keep-unrelated-cache"));
 assert(keys.includes(current));
 report.cases.push({name:"Activation removes stale PWA caches, preserves unrelated cache",status:"PASS"});
 const css=req("./quicktrip-clean.css?v=85");
 const fresh=makeEvent(css);handlers.fetch(fresh);assert(fresh.responded,"GET CSS not intercepted");
 const online=await fresh.responded;await flush(fresh);
 assert.equal(online.tag,"NETWORK:"+css.url);
 assert.equal((await storage(current).match(css)).tag,online.tag);
 networkDown=true;
 const fallback=makeEvent(req("./quicktrip-clean.css?v=86"));
 handlers.fetch(fallback);
 assert.equal((await fallback.responded).tag,online.tag,
 "Offline CSS must fall back to last good cached asset despite different query");
 report.cases.push({name:"Network-first asset plus version-query offline fallback",status:"PASS"});
 const offlineNav=makeEvent(req("./", "GET", "navigate"));handlers.fetch(offlineNav);
 const n=await offlineNav.responded;assert.equal(n.tag,"PRECACHE:./offline.html");
 report.cases.push({name:"Offline navigation displays honest offline page",status:"PASS"});
 for(const [path,method] of [["./rest/v1/rpc/customer_request_pickup_fixed_v1","POST"],
   ["https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/customer_request_pickup_fixed_v1","GET"]]){
   const outbound=makeEvent(req(path,method));
   handlers.fetch(outbound);
   assert.equal(outbound.responded,null,"Worker must NOT cache/intercept reservation or cross-origin data");
 }
 report.cases.push({name:"No interception/cache for POST reservations or external Supabase",status:"PASS"});
 report.requestsExecutedAgainstInternet=0;
 report.result="PASS";
 fs.mkdirSync(process.env.QA_DIR||"quicktrip-v85-edge-qa",{recursive:true});
 fs.writeFileSync(path.join(process.env.QA_DIR||"quicktrip-v85-edge-qa","CACHE-REAL-SW.json"),
  JSON.stringify(report,null,2)+"\n");
 console.log("PASS REAL sw.js isolated: "+report.cases.length+" cases\n"+JSON.stringify(report,null,2));
})().catch(e=>{console.error("FAIL SW CACHE QA",e);process.exitCode=1;});
