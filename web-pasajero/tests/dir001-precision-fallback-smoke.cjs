"use strict";
/* DIR-001 follow-up: official IDE distinguishes exact and approximate positions.
 * Synthetic HTTP responses only; no Supabase, bookings or personal data.
 * Exercises SAME findAcross path as passenger picker in app.js. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm");
const read=n=>fs.readFileSync("web-pasajero/"+n,"utf8");
const app=read("app.js");
const s=app.indexOf("function picker(name,mount,position){");
const e=app.indexOf("\n// El motor vial no depende",s);
assert(s>=0&&e>s,"Real passenger picker must be identifiable");
assert(app.slice(s,e).includes("TrasladosAddress.findAcross(q,dept)"),
  "Regression test must target the address API actually called by passenger UI");
const norm=s=>String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
  .toLowerCase().replace(/[^a-z0-9]+/g," ").trim();
const city="Montevideo";
const approx={address:"Ejemplo 1234, Montevideo",departamento:city,localidad:"Cordón",
 type:"CALLEyPORTAL",lat:-34.900,lng:-56.178,stateMsg:"Aproximado"};
const precise={...approx,lat:-34.901,lng:-56.177,stateMsg:"Válido"};
const foreign={...approx,departamento:"Canelones",address:"Ejemplo 1234, Canelones",
 lat:-34.81,lng:-56.03,stateMsg:"Válido"};
function engine(fetch){
  const window={TrasladosGeo:{normalize:norm}};
  const context={window,fetch,Map,Set,Date,Number,String,Array,AbortController,
    setTimeout,clearTimeout,encodeURIComponent};
  vm.runInNewContext(read("address-engine.js"),context,{filename:"address-engine.js"});
  return window.TrasladosAddress;
}
const ok=rows=>({ok:true,json:async()=>rows});
const failed=()=>{throw Error("IDE unavailable");};
const endpoint=u=>String(u).includes("/direcUnica?")?"direcUnica":"candidates";
(async()=>{
  {
    const calls=[];
    const address=engine(async url=>{const ep=endpoint(url);calls.push(ep);
      return ok(ep==="candidates"?[foreign,approx]:[precise]);});
    const found=await address.findAcross("Ejemplo 1234",city);
    assert.deepEqual(calls,["candidates","direcUnica"],
      "Approximate in preferred department must NOT suppress the exact lookup");
    assert.equal(found.local[0].stateMsg,"Válido",
      "Precise result should be offered before same-address approximate candidate");
    assert.equal(found.other.length,1,"Other departments should remain available");
    assert.equal((await address.resolve(found.local[0],"Ejemplo 1234",city)).approximate,false);
    assert.equal((await address.resolve(approx,"Ejemplo 1234",city)).approximate,true);
  }
  {
    const address=engine(async url=>endpoint(url)==="direcUnica"?failed():ok([approx]));
    const found=await address.findAcross("Ejemplo 1234",city);
    assert.equal(found.local.length,1,
      "Provider exact-query failure must not erase an already verified approximate candidate");
    assert.equal(found.local[0].stateMsg,"Aproximado",
      "Must preserve explicit approximate precision for display in pickup UI");
    assert(found.warning,"Caller needs a warning when precise lookup could not be verified");
  }
  {
    const wrong={...precise,address:"Ejemplo 1235, Montevideo"};
    const address=engine(async url=>ok(endpoint(url)==="candidates"?[wrong]:[wrong]));
    const found=await address.findAcross("Ejemplo 1234",city);
    assert.equal(found.local.length,0,"Never substitute another house number");
  }
  {
    const address=engine(async url=>endpoint(url)==="candidates"?failed():ok([precise]));
    const found=await address.findAcross("Ejemplo 1234",city);
    assert.equal(found.local.length,1,
      "Exact fallback can recover a temporarily failing candidates endpoint");
  }
  {
    const calls=[];
    const address=engine(async url=>{calls.push(endpoint(url));return ok([precise]);});
    const found=await address.findAcross("Ejemplo 1234",city);
    assert.equal(found.local.length,1);
    assert.deepEqual(calls,["candidates"],
      "No duplicate exact lookup when candidates already contain a verified precise position");
  }
  console.log("PASS DIR-001 accuracy: same-dept approximate fallback, verified results, failed provider, exact house-number safety");
})().catch(e=>{console.error(e);process.exitCode=1;});
