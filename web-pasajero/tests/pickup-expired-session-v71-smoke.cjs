"use strict";
/* Regression PWA v71: a browser can display an old session even after the
 * test database has been cleared. 401 must never trap driver ETA forever. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,"..");
const etaSource=fs.readFileSync(path.join(root,"eta-engine.js"),"utf8");
const window={};
vm.runInNewContext(etaSource,{window,AbortController,setTimeout,clearTimeout,fetch});
const api=window.TrasladosETA;
const calls=[];
const respond=(status,data)=>({status,ok:status>=200&&status<300,json:async()=>data});
async function request(url,opts){
  assert(url.endsWith("/functions/v1/pickup-eta"));
  const payload=JSON.parse(opts.body);
  calls.push(payload);
  if(payload.sessionToken==="old-session"){
    return respond(401,{available:false,reason:"login_required"});
  }
  if(payload.sessionToken==="fresh-session")
    return respond(200,{available:true,distanceKm:3.3,etaMin:8});
  if(payload.sessionToken==="server-error")
    return respond(503,{available:false,reason:"temporarily_unavailable"});
  return respond(200,{available:true,distanceKm:3.7,etaMin:9,estimateType:"coarse"});
}
const query=(token,transport=request)=>api.pickup({
  api:"https://example.invalid",publishableKey:"public",originLat:-34.8,
  originLng:-56.1,sessionToken:token,request:transport
});
(async()=>{
  let data=await query("old-session");
  assert.equal(data.available,true,"guest ETA should work without account");
  assert.equal(data.sessionInvalid,true,"UI needs to clear revoked local session");
  assert.equal(data.etaMin,9);
  assert.equal(calls.length,2,"one rejected auth request + one public fallback");
  assert.equal(calls[0].sessionToken,"old-session");
  assert.equal(Object.hasOwn(calls[1],"sessionToken"),false,
    "guest retry may not send revoked token");
  calls.length=0;
  data=await query("old-session");
  assert.equal(data.available,true);
  assert.equal(data.sessionInvalid,true);
  assert.equal(calls.length,1,"subsequent ticks must NOT keep retrying 401");
  assert.equal(Object.hasOwn(calls[0],"sessionToken"),false);
  calls.length=0;
  data=await query("fresh-session");
  assert.equal(data.available,true);
  assert.equal(data.etaMin,8);
  assert.equal(data.sessionInvalid,undefined);
  assert.equal(calls.length,1,"renewed session must be eligible");
  calls.length=0;
  data=await query("server-error");
  assert.equal(data.reason,"temporarily_unavailable");
  assert.equal(calls.length,1,"do not bypass unrelated 503 or rate limits");
  const limited=[];
  const limitedTransport=async(url,opts)=>{
    limited.push(JSON.parse(opts.body));
    return limited.length===1?
      respond(401,{available:false,reason:"login_required"}):
      respond(429,{available:false,reason:"rate_limited"});
  };
  data=await query("expired-other",limitedTransport);
  assert.equal(data.sessionInvalid,true);
  assert.equal(data.reason,"rate_limited");
  assert.equal(limited.length,2,"never hammer public quota");
  const app=fs.readFileSync(path.join(root,"app.js"),"utf8");
  const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
  const sw=fs.readFileSync(path.join(root,"sw.js"),"utf8");
  assert(app.includes('function invalidatePassengerSession()'));
  assert(app.includes('localStorage.removeItem(SESSION_KEY)'));
  assert(app.includes('data?.sessionInvalid&&signedIn'));
  assert(app.includes('lastVerifiedEtaKey=pickupIdentityKey()'));
  assert(html.includes('id="account-session-expired"'));
  assert(html.includes('app.js?v=75')&&html.includes('eta-engine.js?v=3'));
  assert(sw.includes("traslados-cliente-pwa-v75"));
  console.log("PASS v71: revoked sessions recovered to public ETA, one fallback, no quota bypass and real local logout");
})().catch(e=>{console.error(e);process.exitCode=1});
