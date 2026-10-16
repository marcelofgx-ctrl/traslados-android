"use strict";
// PWA v74 — chat consultado en primer plano, sin repintado y estados operativos evidentes.
const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const root=path.join(__dirname,"..");
const read=name=>fs.readFileSync(path.join(root,name),"utf8");
const js=read("app.js"),css=read("styles.css"),html=read("index.html"),sw=read("sw.js");
assert(html.includes('app.js?v=75')&&html.includes('styles.css?v=75'));
assert(sw.includes('traslados-cliente-pwa-v75'));
const chat=js.slice(js.indexOf('function closeTripChat()'),js.indexOf('function armTripSound()'));
assert(chat.includes('},6000);'),"Open conversation should poll every six seconds");
assert(chat.includes('document.visibilityState!=="visible"'),"No rapid background polling");
assert(chat.includes('navigator.onLine===false'),"Offline must not waste network calls");
assert(chat.includes('clearInterval(tripChatPollTimer)'),"Closing the dialog must end polling");
assert(chat.includes('String(activeTripChat?.id)!==String(r.id)'),"Discard previous conversation responses");
assert(chat.includes('if(feed.dataset.signature===signature)return;'),"No repaint when no new messages");
assert(chat.includes('if(initial||following)feed.scrollTop=feed.scrollHeight;'),"Don't drag reader away from earlier messages");
assert(chat.includes('if(feed.isConnected&&!feed.dataset.signature)'),"Do not delete existing chat on temporary failures");
assert(js.includes('const cadence=activeTrip?8000:20000;'),"Faster status refresh only for relevant foreground reservations");
assert(js.includes('live.dataset.stage=onBoard?"trip":arrived?"arrived":"approach";'));
assert(js.includes('live.setAttribute("role","status")'),"Accessible status for screen readers");
assert(css.includes(".trip-live-spotlight")&&css.includes('.trip-live-copy strong'));
assert(css.includes('prefers-reduced-motion:reduce'),"Animations respect reduced motion");
const ctx={window:{}};vm.runInNewContext(read("trip-board.js"),ctx);
const board=ctx.window.TrasladosTripBoard;
const base={id:"abc",code:"TEST",status:"ACEPTADA",quote_status:"ACEPTADO",
  pickup_date:"2026-10-15",pickup_time:"00:05",origin_text:"Costa Urbana",
  destination_text:"Aeropuerto Internacional de Carrasco"};
const toPickup={...base,pickup_phase:"EN_CAMINO",pickup_phase_updated_at:"2026-10-15T03:15:00Z"};
const arrived={...base,pickup_phase:"EN_PUNTO"};
assert.equal(board.metadata(toPickup).label,"Conductor en camino");
assert.equal(board.metadata(arrived).label,"Conductor llegó");
assert.equal(board.metadata({...base,status:"EN_VIAJE"}).label,"En viaje");
assert.notEqual(board.metadata(base).tone,"moving","Never invent movement without confirmed backend phase");
console.log("PASS v74: foreground-only chat polling, no redundant reflow, live journey spotlight, server-authoritative phases");
