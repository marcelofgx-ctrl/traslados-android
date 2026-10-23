"use strict";
// Presentation-only regression. No network, GPS or booking writes.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,"..");
const read=x=>fs.readFileSync(path.join(root,x),"utf8");
const script=read("app.js"),html=read("index.html"),css=read("styles.css"),sw=read("sw.js");
const start=script.indexOf("function tripNoticeAppearance("),end=script.indexOf("function showTripNotice(",start);
assert(start>=0&&end>start,"Pure visual notice mapper must be separate from event trigger");
const projection=script.slice(start,end);
const ctx={tripBoard:{
  isFixedFare:r=>r.fare_mode==="fixed"||r.quote_status==="PRECIO_FIJO",
  metadata:r=>({label:String(r.status||"Pendiente")})
}};
const project=change=>vm.runInNewContext(projection+"\ntripNoticeAppearance(change)",{...ctx,change});
const accepted=project({kind:"state",row:{status:"CONFIRMADA",fare_mode:"fixed",code:"QA-ACCEPTED"}});
assert.equal(accepted.title,"¡Tu viaje fue aceptado!");
assert.equal(accepted.tone,"accepted");
assert.equal(accepted.eyebrow,"¡BUENAS NOTICIAS!");
assert(accepted.description.includes("Tu conductor confirmó"));
assert(!accepted.description.includes("estado de"),"No technical notification copy");
const acceptedAgain=project({kind:"state",row:{status:"ACEPTADA",quote_status:"PRECIO_FIJO"}});
assert.equal(acceptedAgain.title,accepted.title,"Two real accepted status forms share warm copy");
const budget=project({kind:"state",row:{status:"CONFIRMADA",fare_mode:"schedule"}});
assert.equal(budget.title,"¡Tu traslado está confirmado!","Do not label scheduled quote flow as fixed-fare driver acceptance");
const moving=project({kind:"state",row:{status:"ACEPTADA",fare_mode:"fixed",pickup_phase:"EN_CAMINO"}});
assert.equal(moving.title,"Tu conductor va hacia vos","Do not announce acceptance twice on phase update");
const quote=project({kind:"quote",row:{status:"PENDIENTE",quote_final_total:1234}});
assert.equal(quote.title,"Tenés un presupuesto nuevo");
const chat=project({kind:"message",row:{status:"ACEPTADA"}});
assert.equal(chat.title,"Tenés un mensaje nuevo");
assert(html.includes('id="trip-alert-symbol"')&&html.includes('id="trip-alert-eyebrow"'));
assert(html.includes('styles.css?v=83')&&html.includes('app.js?v=83'),"Refresh visual bundle atomically");
assert(sw.includes("traslados-pwa-v82-quicktrip-warm-notice-v83-20261023"));
assert(script.includes('overlay.dataset.tone=appearance.tone'));
assert(script.includes('const appearance=tripNoticeAppearance(change)'));
assert(script.includes('const updates=tripSnap?tripBoard.notifications(tripSnap,rows):[];'),
  "Existing backend event engine remains unchanged");
assert(css.includes("@keyframes qt-trip-arrive")&&css.includes("prefers-reduced-motion:reduce"));
assert(!script.includes('"Tu traslado se actualizó"')&&!html.includes("Tu reserva se actualizó"));
console.log("PASS warm acceptance popup: correct states, graceful copy, animation and unchanged event engine");
