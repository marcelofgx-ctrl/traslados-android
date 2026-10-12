"use strict";
// v55: pure model and DOM contract only; NO network, real reservations or backend writes.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,".."),read=name=>fs.readFileSync(path.join(root,name),"utf8");
const window={};vm.runInNewContext(read("trip-board.js"),{window,Date,Number,Array,String,Set,Map});
const board=window.TrasladosTripBoard;
const now=Date.parse("2026-10-11T23:07:00Z"); // 20:07 UYT
const quote={
 id:"example",code:"QA-EXPIRED",status:"PENDIENTE",quote_status:"ENVIADO",
 quote_final_total:973,quote_includes:"null",
 pickup_date:"2026-10-11",pickup_time:"08:15:40",
 created_at:"2026-10-11T11:15:00Z"
};
const oldScheduled={...quote,code:"QA-SCHEDULE",pickup_date:"2026-10-09",
 pickup_time:"12:30:00",quote_final_total:980,created_at:"2026-10-09T11:39:00Z"};
assert.equal(board.expiredQuote(quote,now),true,"Today at 08:15 must be stale at 20:07");
assert.equal(board.expiredQuote(oldScheduled,now),true,"Old scheduled quote cannot be accepted");
assert.equal(board.expiredQuote({...quote,pickup_mode:"now"},Date.parse("2026-10-11T12:44:00Z")),false,"Immediate pickup valid until backend 90-min deadline");
assert.equal(board.expiredQuote({...quote,pickup_mode:"now"},Date.parse("2026-10-11T12:46:00Z")),true,"Immediate expires after 90 minutes");
assert.equal(board.expiredQuote({...quote,pickup_mode:"10"},Date.parse("2026-10-11T12:46:00Z")),true,"10-min mode shares 90-min backend limit");
assert.equal(board.expiredQuote({...oldScheduled,pickup_mode:"schedule"},now),true);
assert.equal(board.expiredQuote({...quote,pickup_date:"2026-10-12"},now),false,"Future legacy quote stays actionable");
assert.equal(board.expiredQuote({...quote,pickup_date:null},now),false,"Unknown dates do not invent expiration");
assert.equal(board.expiredQuote({...quote,status:"ACEPTADA"},now),false,"Accepted trip remains accepted");
assert.equal(board.expiredQuote({...quote,quote_status:"RECHAZADO"},now),false);
assert.equal(board.metadata({...quote,pickup_mode:"now"}).tone,"expired");
assert.equal(board.isHistory({...quote,pickup_mode:"now"}),true);
assert.equal(board.notifications(board.snapshot([quote]),[{...quote,quote_sent_at:"2026-10-11T22:00:00Z"}]).length,0,"No fresh-quote alert for stale pickup");
const future={...quote,pickup_date:"2026-11-20",pickup_time:"14:00:00"};
assert.equal(board.metadata(future).tone,"attention");
const app=read("app.js"),html=read("index.html"),css=read("styles.css"),sw=read("sw.js");
assert(app.includes('tripBoard.expiredQuote(r)'));
assert(app.includes('if(!expired&&price>0'),"Never render expired accept/reject actions");
assert(app.includes('Solicitar nuevo traslado'),"Expired request offers a non-mutating path to reserve");
assert(app.includes('const includes=String(r.quote_includes??"").trim()'));
assert(app.includes('/^(null|undefined|n\\/a|-)$/i.test(includes)'),"Literal null stays invisible");
assert(app.includes('HORARIO_YA_NO_DISPONIBLE|HORARIO_NO_DISPONIBLE'));
assert(app.includes('La solicitud inmediata venci[oó]'));
assert(html.includes("app.js?v=63")&&html.includes("styles.css?v=63")&&html.includes("trip-board.js?v=5"));
assert(sw.includes("traslados-cliente-pwa-v63"));
assert(css.includes('.trip-card[data-tone="expired"]'));
assert(css.includes('.trip-expired-note'));
console.log("PASS v55 expired quotes, backend 90-min policy, date safety, no null labels or raw error codes");
