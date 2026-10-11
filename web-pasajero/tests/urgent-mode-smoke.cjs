"use strict";
// Regression: Pages y APK comparten EXACTAMENTE la misma lógica urgente/programada.
const {readFileSync}=require("node:fs");
const {runInNewContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const base=path.join(__dirname,"..");
const js=readFileSync(path.join(base,"app.js"),"utf8");
const etaSource=readFileSync(path.join(base,"eta-engine.js"),"utf8");
const html=readFileSync(path.join(base,"index.html"),"utf8");
const css=readFileSync(path.join(base,"styles.css"),"utf8");
assert(html.includes('id="pickup-datetime-block"')&&html.includes('id="pickup-mode-caption"'));
assert(html.includes('id="pickup-presence"')&&html.includes('id="urgent-whatsapp"'));
assert(css.includes("#pickup-datetime-block[hidden]"));
assert(etaSource.includes('public_driver_availability_v1')&&js.includes("window.TrasladosETA.availability"),"No perder el enlace con Mapa");

const start=js.indexOf("function setWhen(kind){");
const end=js.indexOf("\nfunction shiftView(",start);
assert(start>=0&&end>start);
const nodes={
  "pickup-datetime-block":{hidden:false},
  "pickup-mode-caption":{hidden:true,textContent:""},
  "urgent-actions":{hidden:true},
  "booking-final":{hidden:false},
  "review-booking":{hidden:false},
  "booking-preview":{hidden:true},
  "submit-actions":{hidden:true},
  "pickup-date":{value:""},
  "pickup-time":{value:""},
  "when-next":{hidden:false},
  availability:{textContent:""}
};
const tabs=["now","10","schedule"].map(kind=>({
  dataset:{when:kind},active:false,
  classList:{toggle(key,active){if(key==="active")this.active=active;}}
}));
const ctx={
  $:id=>{assert(nodes[id],id);return nodes[id];},
  document:{querySelectorAll:()=>tabs},
  currentUY:(minutes=0)=>({day:"2099-01-02",time:minutes===60?"18:00":minutes===10?"17:10":"17:02"}),
  refreshPickupPresence:()=>{},
  refreshBookingSteps:()=>{},
  clearBookingFeedback:()=>{},
  Date,console,
  pendingReview:null
};
const setWhen=runInNewContext("let selectedPickupMode='schedule';\n"+js.slice(start,end)+"\nsetWhen;",ctx);
setWhen("now");
assert.equal(nodes["pickup-datetime-block"].hidden,true);
assert.equal(nodes["pickup-mode-caption"].hidden,false);
assert.match(nodes["pickup-mode-caption"].textContent,/Ahora/);
assert.equal(nodes["review-booking"].hidden,true);
assert.equal(nodes["booking-final"].hidden,true);
assert.equal(nodes["urgent-actions"].hidden,false);
assert.match(nodes.availability.textContent,/no se envía una reserva automática/);
assert.equal(nodes["when-next"].hidden,true,"Do not show a non-working continue button for urgent trips");
setWhen("10");
assert.equal(nodes["pickup-datetime-block"].hidden,true);
assert.match(nodes["pickup-mode-caption"].textContent,/10 minutos/);
assert.equal(nodes["when-next"].hidden,true);
setWhen("schedule");
assert.equal(nodes["pickup-datetime-block"].hidden,false);
assert.equal(nodes["pickup-mode-caption"].hidden,true);
assert.equal(nodes["review-booking"].hidden,false);
assert.equal(nodes["booking-final"].hidden,false);
assert.equal(nodes["when-next"].hidden,false,"Scheduled trips may advance to contact details");
assert.equal(nodes["urgent-actions"].hidden,true);
assert.match(nodes.availability.textContent,/Reserva programada/);
console.log("PASS: urgente compacto y sin reserva falsa; programado vuelve a mostrar fecha/hora");
