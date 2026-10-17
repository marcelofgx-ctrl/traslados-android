"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const sw=fs.readFileSync(path.join(__dirname,"..","sw.js"),"utf8");
const name=sw.match(/const CACHE="([^"]+)"/)?.[1];
assert(name&&name.includes("pwa-v77"),"Expected named frontend release cache");

const scope="https://example.test/traslados-android/web-pasajero/";
const handlers={},deleted=[],matches=[],puts=[];
const cache={
  addAll:async()=>{},
  match:async(request,opts={})=>{
    const url=typeof request==="string"?request:request.url;
    matches.push({url,ignoreSearch:!!opts.ignoreSearch});
    if(url==="./offline.html")return{kind:"offline-page"};
    if(url.includes("app.js")&&opts.ignoreSearch)return{kind:"cached-versionless-app"};
    return undefined;
  },
  put:async(request,copy)=>{puts.push({url:request.url,copy});}
};
const caches={
  keys:async()=>["unrelated-product-cache","traslados-cliente-pwa-v75-old",name],
  delete:async key=>{deleted.push(key);return true;},
  open:async key=>{assert.equal(key,name,"Only use the passenger cache");return cache;}
};
const self={
  location:{origin:new URL(scope).origin},
  registration:{scope},
  clients:{claim:async()=>{},openWindow:async()=>{}},
  skipWaiting:async()=>{},
  addEventListener:(type,callback)=>{handlers[type]=callback;}
};
const ctx={self,caches,URL,fetch:async()=>{throw Error("offline")}};
vm.runInNewContext(sw,ctx,{filename:"sw.js"});
async function dispatch(type,request){
  const jobs=[];
  const evt={
    request,
    waitUntil:promise=>jobs.push(Promise.resolve(promise)),
    respondWith:promise=>{evt.response=Promise.resolve(promise);}
  };
  handlers[type](evt);
  if(evt.response)await evt.response;
  await Promise.all(jobs);
  return evt.response;
}
(async()=>{
  await dispatch("activate");
  assert.deepEqual(deleted,["traslados-cliente-pwa-v75-old"],
    "Activating this PWA must never purge unrelated origin apps");

  const req={url:scope+"app.js?v=77",mode:"same-origin",method:"GET"};
  const offline=await dispatch("fetch",req);
  assert.equal((await offline).kind,"cached-versionless-app",
    "Offline request with ?v=77 must find the precached ./app.js");
  assert(matches.some(m=>m.url===req.url&&!m.ignoreSearch),
    "Prefer a precise current-version cache hit when present");
  assert(matches.some(m=>m.url===req.url&&m.ignoreSearch),
    "Use path-only matching solely as last resort offline");

  const navigation=await dispatch("fetch",{url:scope,mode:"navigate",method:"GET"});
  assert.equal((await navigation).kind,"offline-page");

  ctx.fetch=async()=>({ok:true,clone:()=>({kind:"network-copy"})});
  await dispatch("fetch",req);
  assert.equal(puts.length,1);
  assert.equal(puts[0].url,req.url,"Fresh versioned asset written back into app cache");
  console.log("PASS SW v77: cache ownership, offline query fallback, navigation and network refresh");
})().catch(err=>{console.error(err);process.exitCode=1;});
