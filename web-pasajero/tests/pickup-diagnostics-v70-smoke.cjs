"use strict";
/* PWA v70 regression: allowlisted, time-limited diagnostics survive reload
 * without persisting any user data or producing network requests. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const dir=path.join(__dirname,"..");
const code=fs.readFileSync(path.join(dir,"pickup-observability.js"),"utf8");
const store=new Map(),storage={
  getItem:key=>store.get(key)??null,setItem:(key,val)=>store.set(key,val),
  removeItem:key=>store.delete(key)
};
const create=()=>{
  const window={localStorage:storage,location:{search:""},console:{info:()=>{}}};
  vm.runInNewContext(code,{window,Date,Number,Set,JSON,URLSearchParams});
  return window.TrasladosPickupHealth;
};
let health=create();
health.recordContext("eta","login_required","issue","guest",1800);
health.recordContext("availability","available","ready","session",125);
health.record("eta","token=DO_NOT_SAVE",-50); // unknown value is replaced, never retained
const first=JSON.parse(health.exportSafe());
assert.equal(first.events.length,3);
assert.deepEqual(JSON.parse(JSON.stringify(first.events[0])),{
 step:"eta",outcome:"login_required",latencyMs:1800,
 at:first.events[0].at,phase:"issue",mode:"guest"
});
assert(!health.exportSafe().includes("DO_NOT_SAVE"));
assert(!health.exportSafe().includes("originLat"));
assert(!health.exportSafe().includes("sessionToken"));
health=create();
assert.equal(health.summary().length,3,"fresh WebView/page reload retains safe records");
for(let n=0;n<75;n++)health.recordContext("retry","cooldown","waiting","guest",25);
assert.equal(health.summary().length,60,"strict 60 event limit");
const key="traslados.passenger.pickup.diagnostics.v1";
const last=JSON.parse(store.get(key));
assert.equal(last.length,60);
assert(last.every(x=>["eta","availability","card","retry"].includes(x.step)));
const yesterday=new Date(Date.now()-25*60*60*1000).toISOString();
store.set(key,JSON.stringify([{step:"eta",outcome:"success",at:yesterday,latencyMs:22,raw:"secret"},
 {step:"eta",outcome:"login_required",at:new Date().toISOString(),latencyMs:13,
  mode:"guest",phone:"DO_NOT_KEEP",originLat:-34.2}]));
health=create();
assert.equal(health.summary().length,1,"expired events are purged");
assert(!store.get(key).includes("DO_NOT_KEEP"),"unknown fields are removed on restore");
health.clear();
assert.equal(health.summary().length,0);
assert(!store.has(key),"clear removes persisted events");
const html=fs.readFileSync(path.join(dir,"index.html"),"utf8");
const app=fs.readFileSync(path.join(dir,"app.js"),"utf8");
const sw=fs.readFileSync(path.join(dir,"sw.js"),"utf8");
assert(html.includes('id="pickup-diagnostics"'));
assert(html.includes('id="pickup-diagnostics-copy"'));
assert(html.includes('app.js?v=73')&&html.includes('pickup-observability.js?v=2'));
assert(sw.includes("traslados-cliente-pwa-v74"));
assert(app.includes('window.TrasladosPickupHealth?.recordContext?.("eta"'));
assert(app.includes('window.TrasladosPickupHealth?.recordContext?.("availability"'));
assert(app.includes("copyPickupDiagnostics"));
assert(app.includes("Disponibilidad comprobada ahora"));
assert(app.includes('La llegada se calcula por separado'));
console.log("PASS PWA v70: bounded 24h local diagnostics, no personal data, copy/clear and honest ETA status");
