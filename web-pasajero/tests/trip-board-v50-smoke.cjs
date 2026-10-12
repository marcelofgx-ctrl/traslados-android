"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,"..");
const read=n=>fs.readFileSync(path.join(root,n),"utf8");
const win={};
vm.runInNewContext(read("trip-board.js"),{window:win,Map,Set,Date,String,Array,Number});
const board=win.TrasladosTripBoard;
const rows=[
{id:"a",code:"TR-A",pickup_date:"2026-10-23",pickup_time:"08:00:00",status:"PENDIENTE",quote_status:"SIN_PRESUPUESTO",created_at:"2026-10-01T12:00:00Z"},
{id:"b",code:"TR-B",pickup_date:"2026-10-18",pickup_time:"11:30:00",status:"FINALIZADA",quote_status:"ACEPTADO",quote_final_total:950},
{id:"c",code:"TR-C",pickup_date:"2026-10-23",pickup_time:"10:00:00",status:"PENDIENTE",quote_status:"ENVIADO",quote_final_total:790,quote_sent_at:"2026-10-10T14:00:00Z"},
{id:"d",code:"TR-D",pickup_date:"2026-11-01",pickup_time:"12:00:00",status:"ACEPTADA",quote_status:"ACEPTADO",quote_final_total:880},
{id:"e",code:"TR-E",pickup_date:"2026-10-18",pickup_time:"09:00:00",status:"EN_VIAJE"},
{id:"f",code:"TR-F",pickup_date:"2026-09-18",pickup_time:"17:00:00",status:"RECHAZADA"}
];
assert.equal(board.metadata(rows[0]).label,"Pendiente");
assert.equal(board.metadata(rows[1]).label,"Finalizada");
assert.equal(board.metadata(rows[2]).label,"Presupuesto para responder");
assert.equal(board.metadata(rows[3]).label,"Confirmada");
assert.equal(board.metadata(rows[4]).label,"En viaje");
assert.equal(board.metadata({status:"EN_CAMINO"}).label,"Conductor en camino");
assert.equal(board.metadata({status:"PENDIENTE",quote_status:"ENVIADO",quote_final_total:null}).tone,"quote");
const upcoming=board.groupReservations(rows,{scope:"upcoming",groupBy:"day"});
assert.equal(upcoming.length,3);
assert.deepEqual(JSON.parse(JSON.stringify(upcoming.map(g=>g.key))),["2026-10-18","2026-10-23","2026-11-01"]);
assert.deepEqual(JSON.parse(JSON.stringify(upcoming.find(x=>x.key==="2026-10-23").items.map(x=>x.id))),["a","c"]);
const byMonth=board.groupReservations(rows,{scope:"all",groupBy:"month"});
assert.equal(byMonth.length,3);
assert.equal(byMonth[0].key,"2026-11");
assert.equal(board.groupReservations(rows,{scope:"history",groupBy:"day"}).reduce((n,g)=>n+g.items.length,0),2);
assert.equal(board.groupReservations(rows,{scope:"attention",groupBy:"day"}).reduce((n,g)=>n+g.items.length,0),1);
assert.equal(board.groupReservations(rows,{scope:"all",status:"confirmed"}).reduce((n,g)=>n+g.items.length,0),1);
const baseline=board.snapshot(rows);
assert.equal(board.notifications(baseline,rows).length,0,"No duplicate alerts on unchanged refresh");
const change={...rows[0],quote_status:"ENVIADO",quote_final_total:660,quote_sent_at:"2026-10-11T11:00:00Z"};
const changes=board.notifications(baseline,[change]);
assert.equal(changes.length,1);
assert.equal(changes[0].kind,"quote");
assert.equal(board.notifications(baseline,[{...rows[2],quote_status:"ENVIADO",quote_final_total:790}]).length,0);
assert.equal(board.notifications(baseline,[{id:"new",code:"TR-NEW",status:"PENDIENTE",quote_status:"ENVIADO",quote_final_total:500,quote_sent_at:"2026-10-11T11:00:00Z"}]).length,0,"Do not alert on first observation");
const html=read("index.html"),app=read("app.js"),styles=read("styles.css"),sw=read("sw.js");
assert(html.includes('id="trips-group-by"')&&html.includes('value="month"'));
assert(html.includes('id="trips-status-filter"')&&html.includes('data-trips-scope="attention"'));
assert(html.includes('id="trips-alert-vibrate"')&&html.includes('id="trip-alert-overlay"'));
assert(html.includes('src="./trip-board.js?v=6"'));
assert(html.includes('app.js?v=66')&&html.includes('styles.css?v=66')&&sw.includes('traslados-cliente-pwa-v66'));
assert(sw.includes("./trip-board.js"),"New module precached for offline view");
assert(app.includes('customer_list_reservations_v15')&&app.includes('get_reservation_by_token'));
assert(app.includes('customer_quote_decision_v13'),"Real acceptance backend");
assert(app.includes('tripBoard.notifications(tripSnap,rows)'),"Change-based alerts only");
assert(app.includes('document.visibilityState==="visible"'),"No polling while hidden");
assert(app.includes('},20000);'),"Bounded 20-second foreground refresh");
assert(app.includes('if($("trips-alert-vibrate").checked&&navigator.vibrate)'),"Opt-in vibration only");
assert(app.includes('tripRows=[];tripLoaded=false;tripSnap=null'),"Account switch resets sensitive display data");
assert(!app.includes('navigator.serviceWorker.ready.then(reg=>reg.pushManager.subscribe'),"No fake background push");
assert(styles.includes('.trip-card[data-tone="moving"] .trip-status-dot'));
assert(styles.includes('prefers-reduced-motion:reduce'));
console.log("PASS PWA v50: grouping, state filtering, budgets, no repeat alerts, consent and session privacy");
