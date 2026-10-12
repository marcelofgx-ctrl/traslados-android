"use strict";
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const assert=require("node:assert/strict"),root=path.join(__dirname,"..");
const file=n=>fs.readFileSync(path.join(root,n),"utf8");
const log=[];
const window={location:{search:"?diagnostico=1"},console:{info:(...v)=>log.push(v)}};
vm.runInNewContext(file("pickup-observability.js"),{window,URLSearchParams,Date,Number,Set});
const health=window.TrasladosPickupHealth;
health.record("availability","unavailable",46);
health.record("eta","rate_limited",954);
health.record("eta","not_a_real_reason",999999);
health.record("secretToken=abc","success",20);
const entries=JSON.parse(JSON.stringify(health.summary()));
assert.equal(entries.length,3);
assert.deepEqual(entries.map(x=>[x.step,x.outcome,x.latencyMs]),[
  ["availability","unavailable",46],
  ["eta","rate_limited",954],
  ["eta","other",60000]]);
assert.equal(log.length,3);
assert(!JSON.stringify(entries).includes("abc"));
assert(entries.every(e=>Object.keys(e).sort().join(",")==="at,latencyMs,outcome,step"));
for(let i=0;i<40;i++)health.record("availability","available",2);
assert.equal(health.summary().length,30,"Short, bounded in-memory diagnostic history");
const html=file("index.html"),css=file("styles.css"),js=file("app.js"),sw=file("sw.js");
assert(html.includes('id="pickup-route-animation"')&&html.includes('id="pickup-route-caption"'));
assert(html.includes('pickup-observability.js?v=1'));
assert(html.includes("app.js?v=57")&&html.includes("styles.css?v=57"));
assert(sw.includes("traslados-cliente-pwa-v57")&&sw.includes("pickup-observability.js"));
assert(js.includes('window.TrasladosPickupHealth?.record?.("availability"'));
assert(js.includes('window.TrasladosPickupHealth?.record?.("eta"'));
assert(js.includes('presence:"busy",title:"Recogida inmediata no disponible"'));
assert(js.includes('phase:"blocked"')&&js.includes('phase:"checking"'));
assert(js.includes('phase:"ready"')&&js.includes('phase:"issue"'));
assert(js.includes('if(!driver.available){')&&js.includes("forgetPickupEta()"));
assert(css.includes('@keyframes pickupRouteTravel'));
assert(css.includes('#pickup-presence[data-phase="routing"] .pickup-route-car'));
assert(css.includes('prefers-reduced-motion:reduce'));
assert(!js.includes("driverLat")&&!js.includes("driverLng"));
console.log("PASS v54 diagnostics bounded/private, truthful availability animation and no GPS leak");
