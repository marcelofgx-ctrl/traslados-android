"use strict";
/* v64: illustrative movement is visible when available, never invented ETA or GPS. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,".."),read=p=>fs.readFileSync(path.join(root,p),"utf8");
const html=read("index.html"),css=read("styles.css"),app=read("app.js"),sw=read("sw.js");
assert(html.includes('app.js?v=76')&&html.includes('styles.css?v=76'));
assert(sw.includes("traslados-cliente-pwa-v76"));
assert(html.includes('id="pickup-route-animation"')&&html.includes('class="pickup-route-car"><svg'));
assert(!html.includes('id="pickup-route-caption"'));
assert(html.includes('aria-hidden="true" focusable="false"'));
assert(css.includes('#pickup-presence[data-phase="waiting"] .pickup-route-car'));
assert(css.includes('#pickup-presence[data-phase="ready"] .pickup-route-car'));
assert(css.includes('animation:pickupRouteTravel 3.7s ease-in-out infinite alternate'));
assert(css.includes('rgba(133,224,174,.72)')&&css.includes('color:#bff7d4'));
assert(css.includes('not([data-phase="waiting"]):not([data-phase="ready"]) .pickup-route-animation'));
assert(css.includes('#pickup-presence[data-phase="issue"] .pickup-route-car'));
assert(css.includes('@media(prefers-reduced-motion:reduce)'));
assert(css.includes('#pickup-presence[data-phase="ready"] .pickup-route-car{left:42%}'));
assert(!app.includes("driverLat")&&!app.includes("driverLng"));
const begin=app.indexOf("function paintPickupCard("),end=app.indexOf("function latestPickupEstimate(",begin);
assert(begin!==-1&&end>begin);
let notices=0;
const status={_value:"",get textContent(){return this._value;},set textContent(v){this._value=v;notices++;}};
const nodes={
  "pickup-presence":{dataset:{}},
  "pickup-presence-title":{textContent:""},"pickup-presence-subtitle":{textContent:""},
  "pickup-presence-metrics":{hidden:true},"pickup-distance":{textContent:""},
  "pickup-minutes":{textContent:""},"pickup-estimate-note":{textContent:"",hidden:true},
  "pickup-presence-meta":{textContent:""},"pickup-presence-status":status
};
const ctx={window:{TrasladosPickupCadence:{arrivalLabel:()=> "5–10 min"}},$:id=>nodes[id],Number};
vm.createContext(ctx);vm.runInContext(app.slice(begin,end),ctx);
ctx.paintPickupCard({presence:"online",title:"Conductor disponible",subtitle:"Actualizando la llegada…",
  checked:"La llegada se calcula por separado",phase:"waiting"});
assert.equal(nodes["pickup-presence"].dataset.phase,"waiting");
assert.equal(nodes["pickup-presence-metrics"].hidden,true);
assert.equal(nodes["pickup-distance"].textContent,"");
ctx.paintPickupCard({presence:"online",title:"Conductor disponible",subtitle:"Calculando llegada…",phase:"routing"});
ctx.paintPickupCard({presence:"online",title:"Conductor disponible",subtitle:"Llegada aproximada",
  km:3.4,minutes:8,phase:"ready",checked:"La llegada se calcula por separado"});
assert.equal(nodes["pickup-presence-metrics"].hidden,false);
assert.equal(nodes["pickup-distance"].textContent,"3,4 km");
const count=notices;
ctx.paintPickupCard({presence:"online",title:"Conductor disponible",subtitle:"Llegada aproximada",
  km:3.4,minutes:8,phase:"ready",checked:"La llegada se calcula por separado"});
assert.equal(notices,count,"No aria-live announcement on identical updates");
ctx.paintPickupCard({presence:"busy",title:"No disponible",phase:"blocked"});
assert.equal(nodes["pickup-presence-metrics"].hidden,true);
assert.equal(nodes["pickup-distance"].textContent,"");
ctx.paintPickupCard({presence:"unknown",title:"No se pudo comprobar",phase:"issue"});
assert.equal(nodes["pickup-presence-metrics"].hidden,true);
console.log("PASS v64 illustrative green route visible while waiting/ready; honest availability, reduced motion, no GPS leak");