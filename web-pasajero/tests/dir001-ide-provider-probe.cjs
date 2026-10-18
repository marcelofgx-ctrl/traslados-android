"use strict";
/* Diagnostic only: publicly listed government office address, read-only IDE.
 * No reservations, Supabase, personal input, API keys or coordinates in logs.
 * Never claim success for a synthetic response or a provider outage. */
const {setTimeout:delay}=require("node:timers/promises");
const API="https://direcciones.ide.uy/api/v1/geocode";
const q="Liniers 1280, Montevideo"; // public IDE office address (gub.uy)
const timeoutMs=7000;
const normalize=s=>String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase();
(async()=>{
 let anyVerified=false;
 for(const path of ["candidates","direcUnica"]){
  const started=Date.now();
  try{
   const ctrl=new AbortController();
   const timeout=setTimeout(()=>ctrl.abort(),timeoutMs);
   let resp;
   try{
    resp=await fetch(API+"/"+path+"?limit=12&q="+encodeURIComponent(q),{
      method:"GET",signal:ctrl.signal,headers:{"Accept":"application/json"},redirect:"error"});
   }finally{clearTimeout(timeout);}
   if(!resp.ok)throw new Error("HTTP "+resp.status);
   const rows=await resp.json();
   if(!Array.isArray(rows))throw new Error("provider did not return documented JSON array");
   const exact=rows.filter(row=>{
    const address=normalize(row?.address);
    return /\b1280\b/.test(address)&&address.includes("liniers")&&
      normalize(row?.departamento)==="montevideo";
   });
   const precise=exact.filter(row=>{
    const lat=Number(row.lat),lng=Number(row.lng);
    return Number.isFinite(lat)&&Number.isFinite(lng)&&
     lat>=-35.3&&lat<=-30&&lng>=-58.8&&lng<=-52.9&&
     !/aproxim/.test(normalize(row.stateMsg))&&String(row.type).toUpperCase()!=="CALLE";
   });
   anyVerified ||= precise.length>0;
   console.log("IDE_PROBE "+JSON.stringify({endpoint:path,http:resp.status,returned:rows.length,
     matchingDoor:exact.length,precise:precise.length,elapsedMs:Date.now()-started}));
  }catch(e){
   console.log("IDE_PROBE "+JSON.stringify({endpoint:path,status:"UNVERIFIED",
      reason:e.name==="AbortError"?"timeout":String(e.message||"network unavailable").slice(0,100),
      elapsedMs:Date.now()-started}));
  }
 }
 if(!anyVerified){
  console.error("IDE_PROBE_UNVERIFIED: no live exact geocoding verified; keep DIR-001 in QA");
  process.exitCode=1;
 }else{
  console.log("IDE_PROBE_PROVIDER_ONLY: live response verified; DOES NOT prove passenger UI/Samsung");
 }
})().catch(e=>{console.error("IDE_PROBE_ERROR "+e.name);process.exitCode=1;});
