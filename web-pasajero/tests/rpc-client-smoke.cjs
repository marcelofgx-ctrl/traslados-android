"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const dir=path.join(__dirname,"..");
const read=name=>fs.readFileSync(path.join(dir,name),"utf8");
const root={}, timers=[];
let cleaned=0,posts=0;
vm.runInNewContext(read("rpc-client.js"),{
  window:root,AbortController,Error,JSON,encodeURIComponent,
  setTimeout:(fn,ms)=>{timers.push({fn,ms});return timers.length;},
  clearTimeout:()=>cleaned++
});
assert(Object.isFrozen(root.TrasladosRpc));
const html=read("index.html");
assert(html.indexOf("rpc-client.js")<html.indexOf("./app.js?v="));
assert(read("sw.js").includes('"./rpc-client.js"'));
assert(read("app.js").includes("guardConnection();\n  return window.TrasladosRpc.call({api:API,publishableKey:KEY,name,payload});"));
const p={api:"https://example.invalid",publishableKey:"public",name:"create_reservation",payload:{p_passengers:2}};
(async()=>{
  const data=await root.TrasladosRpc.call({...p,request:async(url,options)=>{
    posts++;
    assert.equal(url,"https://example.invalid/rest/v1/rpc/create_reservation");
    assert.equal(options.method,"POST");assert.equal(options.mode,"cors");
    assert.equal(options.cache,"no-store");assert.equal(options.headers.apikey,"public");
    assert.equal(options.headers.Accept,"application/json");
    assert.deepEqual(JSON.parse(options.body),p.payload);
    assert(options.signal instanceof AbortSignal);
    return {ok:true,text:async()=>JSON.stringify({id:17})};
  }});
  assert.equal(data.id,17);assert.equal(posts,1);
  assert.equal(timers[0].ms,18000);assert.equal(cleaned,1);
  assert.equal(await root.TrasladosRpc.call({...p,request:async()=>({ok:true,text:async()=>""})}),null);
  await assert.rejects(root.TrasladosRpc.call({...p,request:async()=>({
    ok:false,text:async()=>JSON.stringify({message:"HORARIO_NO_DISPONIBLE"})
  })}),/HORARIO_NO_DISPONIBLE/);
  await assert.rejects(root.TrasladosRpc.call({...p,request:async()=>({
    ok:false,text:async()=>""})}),/Servicio temporalmente no disponible/);
  let attempts=0;
  const pending=root.TrasladosRpc.call({...p,request:async(_,opts)=>{
    attempts++;
    return new Promise((_,reject)=>opts.signal.addEventListener("abort",()=>{
      const error=new Error("cancelled");error.name="AbortError";reject(error);
    }));
  }});
  timers.at(-1).fn();
  await assert.rejects(pending,/Revisá Mis traslados antes de volver a enviar para evitar duplicados/);
  assert.equal(attempts,1,"Never retry uncertain booking submissions");
  assert.equal(cleaned,5,"Always clear timeouts");
  console.log("PASS RPC transport: payload, errors, timeout, no retry, web cache");
})().catch(e=>{console.error(e);process.exitCode=1;});
