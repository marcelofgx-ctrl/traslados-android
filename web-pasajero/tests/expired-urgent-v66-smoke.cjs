"use strict";
/* Pure deterministic regression, no production reservations or external calls. */
const {strict:assert}=require("node:assert"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const root=path.join(__dirname,".."),read=name=>fs.readFileSync(path.join(root,name),"utf8");
const win={},created="2026-10-12T16:38:37Z";
vm.runInNewContext(read("trip-board.js"),{window:win,Date,Number,Array,String,Set,Map});
const b=win.TrasladosTripBoard;
const r={status:"PENDIENTE",quote_status:"SIN_PRESUPUESTO",code:"QA-URGENCIA",
  pickup_date:"2026-10-12",pickup_time:"13:38:37.279746",created_at:created,
  origin_text:"Origen verificado",destination_text:"Destino verificado"};
const t=x=>Date.parse(x);
assert.equal(b.isImmediateRequest(r),true,"Secure RPC omits pickup_mode");
assert.equal(b.expiredImmediate(r,t("2026-10-12T18:08:36Z")),false,"Before 90 min the request stays pending");
assert.equal(b.expiredImmediate(r,t("2026-10-12T18:08:38Z")),true,"90 min expires unconfirmed urgent slot");
assert.equal(b.expiredQuote(r,t("2026-10-12T18:09:00Z")),false,"No quote was sent");
assert.equal(b.expiredImmediate({...r,pickup_mode:"schedule"},t("2026-10-12T18:09:00Z")),false);
assert.equal(b.isImmediateRequest({...r,pickup_time:"14:18:37"}),false,"Scheduled +40 min does not imitate immediate");
assert.equal(b.expiredImmediate({...r,status:"ACEPTADA"},t("2026-10-12T19:00:00Z")),false);
assert.equal(b.expiredImmediate({...r,status:"FINALIZADA"},t("2026-10-12T19:00:00Z")),false);
assert.equal(b.expiredImmediate({...r,created_at:"bad"},t("2026-10-12T19:00:00Z")),false);
assert.equal(b.expiredQuote({...r,quote_status:"ENVIADO"},t("2026-10-12T18:09:00Z")),true,
 "Late quote cannot be accepted past backend deadline");
const immediateQuote={...r,quote_status:"ENVIADO",quote_final_total:950,pickup_time:"13:48:37"};
assert.equal(b.expiredQuote(immediateQuote,t("2026-10-12T18:08:38Z")),true,
 "10-min pickup uses same 90-minute deadline from creation");
assert.equal(b.expiredQuote(immediateQuote,t("2026-10-12T18:08:36Z")),false);
const scheduled={...r,pickup_time:"14:18:37",quote_status:"SIN_PRESUPUESTO"};
assert.equal(b.isImmediateRequest(scheduled),false);
assert.equal(b.expiredImmediate(scheduled,t("2026-10-12T19:00:00Z")),false);
const html=read("index.html"),app=read("app.js"),sw=read("sw.js");
assert(html.includes("app.js?v=66")&&html.includes("styles.css?v=66"));
assert(html.includes("trip-board.js?v=6")&&sw.includes("traslados-cliente-pwa-v66"));
assert(app.includes("const immediateExpired=tripBoard.expiredImmediate(r);"));
assert(app.includes("const expired=quoteExpired||immediateExpired;"));
assert(app.includes("if(!expired&&price>0"),"Never accept an expired urgent quote");
assert(app.includes('setWhen(tripBoard.isImmediateRequest(r)?"now":"schedule")'),
  "New trip CTA prepares form without creating it");
assert(app.includes('shiftView("reservar");bookingSteps.open("when",{scroll:true})'));
console.log("PASS v66: immediate 90-min deadline, late quote safety, fresh-booking CTA");