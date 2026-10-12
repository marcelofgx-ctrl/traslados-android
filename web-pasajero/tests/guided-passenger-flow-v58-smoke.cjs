"use strict";
/* PWA v58: protect passenger journey decisions and test the simplified
 * presentation without making reservations, fetching user data or writing DB. */
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,".."),read=name=>fs.readFileSync(path.join(root,name),"utf8");
const html=read("index.html"),app=read("app.js"),css=read("styles.css"),sw=read("sw.js");
const window={};
vm.runInNewContext(read("trip-board.js"),{window,Map,Set,Date,Number,Array,String});
const b=window.TrasladosTripBoard;
const finalized={id:"1",code:"QA-FINAL",status:"FINALIZADA",quote_status:"ACEPTADO",
  quote_final_total:890,pickup_date:"2026-10-11",pickup_time:"21:44:00",origin_text:"Arenal Grande 2274",
  destination_text:"Aeropuerto Internacional de Carrasco - Terminal de pasajeros"};
const quote={...finalized,id:"2",code:"QA-QUOTE",pickup_date:"2026-11-01",
  status:"PENDIENTE",quote_status:"ENVIADO",quote_sent_at:"2026-10-11T23:00:00Z"};
const pending={...quote,id:"3",code:"QA-PEND",quote_status:"SIN_PRESUPUESTO",quote_final_total:null};
const moving={...quote,id:"4",code:"QA-MOVING",status:"ACEPTADA",quote_status:"ACEPTADO",
  pickup_phase:"EN_CAMINO"};
assert.equal(b.metadata(finalized).tone,"complete");
assert.equal(b.metadata(quote).tone,"attention");
assert.equal(b.metadata(pending).tone,"pending");
assert.equal(b.metadata(moving).tone,"moving");
assert.equal(b.groupReservations([finalized],{scope:"upcoming"}).length,0);
assert.equal(b.groupReservations([finalized],{scope:"history"}).length,1);
assert.equal(b.groupReservations([quote,pending],{scope:"attention"}).reduce((n,x)=>n+x.items.length,0),1);
assert.equal(b.groupReservations([finalized,quote],{scope:"all"}).reduce((n,x)=>n+x.items.length,0),2);

assert(html.includes('data-trips-scope="upcoming"'));
assert(html.includes('data-trips-scope="attention"'));
assert(html.includes('data-trips-scope="history"'));
assert(html.includes('class="trips-advanced"')&&html.includes('data-trips-scope="all"'));
assert(html.includes('id="trips-status-filter"')&&html.includes('id="trips-group-by"'));
assert(html.includes('id="trips-alert-sound"')&&html.includes('id="trips-alert-vibrate"'));
assert(html.includes('id="trips-focus"'),"Explain next step outside filters");
assert(html.includes('id="trip-chat-sheet"')&&html.includes('aria-modal="true"'));
assert(html.includes('id="trip-chat-dialog-close"'),"Conversation must have explicit close");
assert(app.includes('tripsScope=actionable?"attention":upcoming?"upcoming":"history"'),
  "When only historical trips exist, show them instead of an empty upcoming tab");
assert(app.includes('tripsFilter="all";$("trips-status-filter").value="all"'),
  "Quick tab changes must not be silently constrained by old advanced filters");
assert(app.includes('function closeTripChat()')&&
  (app.match(/closeTripChat\(\);\$\("trip-chat-dialog-content"\)\.replaceChildren\(\);tripQueryEpoch\+\+;tripsLoading=false;tripRows=\[\];tripLoaded=false;tripSnap=null/g)||[]).length===2,
  "Login and logout must close conversations and invalidate prior responses");
assert(app.includes('if(activeTripChat.closed){')&&app.includes('tripChatDrafts.delete(r.code)'),
  "A closed chat is read-only; even an old draft must be deleted");
assert(app.includes('if(closed!==activeTripChat.closed)openTripChat'),
  "Terminal state from backend must force the open chat into archive mode");
assert(app.includes('text(bubble,"span",item.body||"")'),"Customer messages are text, not HTML");
assert(app.includes('tripProgress(historyBody,meta)')&&app.includes('tripTimeline(historyBody,r)'),
  "Progress and complete event history are preserved in on-demand details");
assert(app.includes('tripLine(infoBody,"◉",r.origin_text||"Origen")')&&
  app.includes('tripLine(infoBody,"◆",r.destination_text||"Destino")'),"Full trip record is preserved");
assert(app.includes('customer_quote_decision_v13')&&app.includes('customer_trip_chat_send_v1')&&
  app.includes('customer_list_reservations_v15')&&app.includes('get_reservation_by_token_v4'),
  "Server contracts are unchanged");
assert(css.includes('.trip-chat-sheet')&&css.includes('.trip-chat-archived')&&
  css.includes('.trip-detail-disclosure'));
assert(css.includes('.trip-card:not([open]) .trip-route-label')&&
  css.includes('-webkit-line-clamp:unset'),"Long airport destinations must remain readable");
assert(html.includes("app.js?v=58")&&html.includes("styles.css?v=58")&&
  sw.includes("traslados-cliente-pwa-v58"),"Release must bust stale Samsung caches");
assert(!/serviceWorker\.ready\.then\(reg=>reg\.pushManager\.subscribe/.test(app),
  "No fake push with app closed");
console.log("PASS PWA v58: passenger states, smart initial tab, closed chat, on-demand details and contracts");
