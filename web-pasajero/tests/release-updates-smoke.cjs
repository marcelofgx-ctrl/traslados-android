"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const path=require("node:path");
const root=path.resolve(__dirname,"..");
const source=fs.readFileSync(path.join(root,"release-updates.js"),"utf8");
const catalog=JSON.parse(fs.readFileSync(path.join(root,"releases.json"),"utf8"));
const calls=[];
const mockWindow={
  navigator:{userAgent:"Mozilla/5.0 TrasladosClienteNative/16"},
  sessionStorage:{getItem:()=>null,setItem(){}}
};
const nodes={
  "native-update-notice":{hidden:true},
  "native-update-title":{textContent:""},
  "native-update-download":{href:"",setAttribute(k,v){this[k]=v;}}
};
mockWindow.document={
  readyState:"loading",visibilityState:"visible",
  addEventListener(){},getElementById:(id)=>nodes[id]||null
};
const ctx={
  window:mockWindow,URL,Date,console,setInterval(){},
  fetch:async (url,options={})=>{
    calls.push([String(url),options.method||"GET"]);
    if(options.method==="HEAD")return {
      ok:true,headers:{get:key=>key==="content-length"?"18076":"application/vnd.android.package-archive"}
    };
    return {ok:true,text:async()=>JSON.stringify(catalog)};
  }
};
vm.runInNewContext(source,ctx,{filename:"release-updates.js"});
const api=mockWindow.TrasladosReleases;
assert.ok(api.valid(catalog));
assert.equal(api.nativeVersionCode("Mozilla TrasladosClienteNative/14"),202);
assert.equal(api.nativeVersionCode("Mozilla TrasladosClienteNative/15"),203);
assert.equal(api.nativeVersionCode("Mozilla TrasladosClienteNative/16"),204);
assert.equal(api.nativeVersionCode("Mozilla TrasladosClienteNative/17 TrasladosNativeCode/212"),212);
assert.equal(api.nativeVersionCode("Mozilla Chrome Android"),null);
assert.equal(api.nativeVersionCode("Mozilla TrasladosClienteNative/77 TrasladosNativeCode/277"),277);
const tampered=JSON.parse(JSON.stringify(catalog));
tampered.releases.cliente.file="../fake.apk";
assert.equal(api.valid(tampered),false);
tampered.releases.cliente.file="https://other.example/evil.apk";
assert.equal(api.valid(tampered),false);
tampered.releases.cliente.file=catalog.releases.cliente.file;
tampered.releases.cliente.packageName="uy.com.traslados.cliente.debug";
assert.equal(api.valid(tampered),false);
(async()=>{
  await api.checkNative();
  assert.equal(nodes["native-update-notice"].hidden,true,"Same native version must not prompt");
  mockWindow.navigator.userAgent="Mozilla TrasladosClienteNative/15";
  await api.checkNative();
  assert.equal(nodes["native-update-notice"].hidden,false,"Older signed native version must prompt");
  assert.match(nodes["native-update-title"].textContent,/Cliente v16/);
  assert.match(nodes["native-update-download"].href,/traslados-cliente-premium-v16\.apk$/);
  assert.ok(calls.some(([url,method])=>method==="HEAD"&&url.endsWith(".apk")));
  nodes["native-update-notice"].hidden=true;
  mockWindow.navigator.userAgent="Mozilla TrasladosClienteNative/77 TrasladosNativeCode/277";
  await api.checkNative();
  assert.equal(nodes["native-update-notice"].hidden,true,"High-code isolated preview cannot downgrade");
  const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
  const sw=fs.readFileSync(path.join(root,"sw.js"),"utf8");
  assert.ok(html.includes('id="native-update-notice"'));
  assert.ok(html.includes('href="./versiones.html"'));
  assert.ok(sw.includes("./release-updates.js"));
  assert.ok(!sw.includes("./releases.json"),"Always fetch release metadata from network");
  assert.ok(!html.includes('service_role'));
  console.log("PASS: stable Android updates, native code, strict catalog, no downgrade, official links");
})().catch(error=>{console.error(error);process.exitCode=1;});
