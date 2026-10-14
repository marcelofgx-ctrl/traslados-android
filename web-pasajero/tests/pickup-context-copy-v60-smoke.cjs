"use strict";
/* PWA v60: QA of compact real-state pickup card (no real bookings or GPS). */
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,"..");
const read=f=>fs.readFileSync(path.join(root,f),"utf8");
const app=read("app.js"),html=read("index.html"),css=read("styles.css"),sw=read("sw.js");
assert(html.includes("app.js?v=70")&&html.includes("styles.css?v=70"));
assert(sw.includes("traslados-cliente-pwa-v70"));
assert(html.includes("DISTANCIA AL ORIGEN")&&html.includes("LLEGADA ESTIMADA"));
assert(css.includes('not([data-phase="waiting"]):not([data-phase="ready"]) .pickup-route-animation'));
assert(!app.includes("Actualización automática cada 15 s."));
assert(app.includes(" · La llegada se calcula por separado"));
assert(app.includes('if(!driver.available){')&&app.includes("forgetPickupEta()"));
assert(app.includes('phase:"blocked"')&&app.includes('phase:"issue"'));
assert(app.includes('window.TrasladosPickupCadence.intervalMs(driver.motionHint)'));
assert(!app.includes("driverLat")&&!app.includes("driverLng"));
const begin=app.indexOf("function paintPickupCard("),end=app.indexOf("function latestPickupEstimate(",begin);
assert(begin>=0&&end>begin);
let announcements=0;
const screenreader={_value:"",get textContent(){return this._value;},
  set textContent(value){this._value=value;announcements++;}};
const nodes={
 "pickup-presence":{dataset:{}},
 "pickup-presence-title":{textContent:""}, "pickup-presence-subtitle":{textContent:""},
 "pickup-presence-metrics":{hidden:true},"pickup-distance":{textContent:""},
 "pickup-minutes":{textContent:""},"pickup-estimate-note":{textContent:"",hidden:true},
 "pickup-presence-meta":{textContent:""},"pickup-presence-status":screenreader
};
const context={window:{TrasladosPickupCadence:{arrivalLabel:()=> "5–10 min"}},
 $:id=>nodes[id],Number};
vm.createContext(context);vm.runInContext(app.slice(begin,end),context);
const online={presence:"online",title:"Conductor disponible para recogerte",
  subtitle:"Llegada aproximada hasta tu origen.",km:3.5,minutes:10,
  estimateNote:"Distancia y tiempo aproximados. La recogida requiere confirmación.",
  checked:"Disponibilidad comprobada ahora · La llegada se calcula por separado",phase:"ready"};
context.paintPickupCard(online);
assert.equal(nodes["pickup-distance"].textContent,"3,5 km");
assert.equal(nodes["pickup-minutes"].textContent,"5–10 min");
assert.equal(nodes["pickup-presence-metrics"].hidden,false);
assert(!html.includes('id="pickup-route-caption"'));
assert(screenreader.textContent.includes("La recogida requiere confirmación"));
context.paintPickupCard(online);
assert.equal(announcements,1,"Do not reannounce unchanged passenger status on each poll");
context.paintPickupCard({presence:"busy",title:"Recogida inmediata sin confirmar",
 subtitle:"Podés enviar tu solicitud para que el conductor la revise.",
 checked:"Disponibilidad comprobada ahora · La llegada se calcula por separado",phase:"blocked"});
assert.equal(nodes["pickup-presence-metrics"].hidden,true,"Hide previous ETA when unavailable");
assert.equal(nodes["pickup-distance"].textContent,"");
assert(!screenreader.textContent.includes("3,5 km"));
context.paintPickupCard({presence:"unknown",title:"No pudimos comprobar la disponibilidad",
 subtitle:"Reintentaremos la consulta.",phase:"issue"});
assert.equal(nodes["pickup-presence-metrics"].hidden,true);
assert(!screenreader.textContent.includes("5–10 min"));
const browser={};vm.runInNewContext(read("trip-board.js"),{window:browser,Map,Set,Date,Number,Array,String});
const board=browser.TrasladosTripBoard;
const confirmed={status:"ACEPTADA",quote_status:"ACEPTADO",pickup_phase:"SIN_INICIAR"};
assert.match(board.guidance(confirmed),/confirmado/);
assert.match(board.guidance({...confirmed,pickup_phase:"EN_CAMINO"}),/en camino a tu origen/);
assert.match(board.guidance({...confirmed,pickup_phase:"EN_PUNTO"}),/llegó al punto de recogida/);
assert.equal(board.guidance({...confirmed,pickup_phase:"EN_CAMINO",status:"EN_VIAJE"}),"Tu traslado está en curso.","In-ride must override stale pickup phase");
assert.match(board.guidance({status:"PENDIENTE",quote_status:"ENVIADO",quote_final_total:490,pickup_date:"2026-12-12",pickup_time:"11:00:00"}),/presupuesto para responder/);
assert(!/(?:\bGPS\b|\d+\s*(?:km|min)\b)/i.test(board.guidance({...confirmed,pickup_phase:"EN_CAMINO"})),
  "Do not invent a GPS or arrival time for an unverified reservation stage");
assert(app.includes("const guidance=tripBoard.guidance(r);"),"Reservation view must use modular real-phase guidance");
console.log("PASS v60 contextual pickup, one-time accessibility announcement, stale ETA removal, CSS and cache");
