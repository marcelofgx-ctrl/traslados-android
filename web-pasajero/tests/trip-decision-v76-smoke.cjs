"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const root=path.join(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const window={};
vm.runInNewContext(read("trip-board.js"),{window,Map,Set,Date,String,Array,Number});
const board=window.TrasladosTripBoard;
const immediate={
  id:"a",code:"TR-A",pickup_mode:"now",fare_mode:"fixed",
  status:"PENDIENTE",quote_status:"PRECIO_FIJO",
  origin_text:"ROQUE SAENZ PEÑA 1711",
  destination_text:"Aeropuerto de Carrasco",
  quote_final_total:124
};
const base=board.snapshot([immediate]);
const accepted={...immediate,status:"ACEPTADA"};
const rejected={...immediate,status:"RECHAZADA"};
assert.equal(board.driverDecision(accepted).result,"accepted");
assert.equal(board.driverDecision(accepted).title,"¡Tu solicitud fue aceptada!");
assert.equal(board.driverDecision(rejected).result,"rejected");
assert.equal(board.driverDecision(rejected).title,"Tu solicitud fue rechazada");
assert.equal(board.driverDecision(immediate),null,"A pending trip is not a driver decision");
const notifyA=board.notifications(base,[accepted]);
const notifyR=board.notifications(base,[rejected]);
assert.equal(notifyA.length,1,"Exactly one accepted state notification");
assert.equal(notifyA[0].kind,"decision");
assert.equal(notifyA[0].decision.result,"accepted");
assert.equal(notifyR.length,1,"Exactly one rejected state notification");
assert.equal(notifyR[0].kind,"decision");
assert.equal(notifyR[0].decision.result,"rejected");
assert.equal(board.notifications(board.snapshot([accepted]),[accepted]).length,0);
assert.equal(board.notifications(board.snapshot([rejected]),[rejected]).length,0);
const underway={...accepted,pickup_phase:"EN_CAMINO"};
const changes=board.notifications(board.snapshot([accepted]),[underway]);
assert.equal(changes.length,1);
assert.equal(changes[0].kind,"state","GPS phase must not produce second acceptance");
assert.equal(board.notifications(board.snapshot([accepted]),[{...accepted,status:"CONFIRMADA"}])[0].kind,"state",
  "An already accepted trip must not produce a second acceptance dialog");
const scheduled={...immediate,pickup_mode:"schedule",status:"ACEPTADA",quote_status:"ACEPTADO"};
assert.equal(board.driverDecision(scheduled).title,"Tu traslado está confirmado",
  "Do not attribute a budget passenger acceptance to the driver");
const quote={...immediate,quote_status:"ENVIADO",quote_final_total:200,quote_sent_at:"2026-10-23T08:00:00Z"};
assert(!board.notifications(base,[quote]).some(x=>x.kind==="decision"),
  "New budget is not acceptance/rejection");
const html=read("index.html"),css=read("styles.css"),app=read("app.js"),sw=read("sw.js");
for(const id of ["trip-alert-details","trip-alert-code","trip-alert-origin","trip-alert-destination",
                 "trip-alert-amount","trip-alert-overlay"]){
  assert(html.includes('id="'+id+'"'),"Missing real reservation detail: "+id);
}
assert(html.includes("app.js?v=76")&&html.includes("trip-board.js?v=7"));
assert(css.includes("@keyframes qtNoticeArrival"),"Visible opening animation");
assert(css.includes('prefers-reduced-motion:reduce'),"Reduced-motion accessibility");
assert(css.includes('.trip-alert-overlay[data-outcome="rejected"]'),"Rejected notice style");
assert(app.includes('overlay.dataset.outcome=decision?.result||"update"'),"Outcome grounded in server status");
assert(app.includes('decision.title'),"No generic update title for a driver decision");
assert(app.includes('typeof navigator.vibrate==="function"'),"Vibration optional / guarded");
assert(app.includes('document.visibilityState!=="visible"'),"No forced foreground from background");
assert(sw.includes("pwa-v76"),"PWA cache revision");
console.log("PASS PWA v76 real driver decisions, contextual animated modal, opt-in haptics");
