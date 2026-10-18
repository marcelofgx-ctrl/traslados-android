"use strict";
/* STATUS-001: synthetic records, actual tripTimeline UI and the shared TripBoard.
 * No HTTP, Supabase writes, coordinates, PINs, or production reservations.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm");
const read=name=>fs.readFileSync("web-pasajero/"+name,"utf8");
const window={};
vm.runInNewContext(read("trip-board.js"),{window,Map,Set,Date,String,Number,Array});
const board=window.TrasladosTripBoard;
assert.equal(typeof board.timeline,"function","There must be one shared status/timeline projection");
function uiEvents(r){
  const source=read("app.js");
  const from=source.indexOf("function tripTimeline(parent,r){");
  const to=source.indexOf("\nfunction tripChatAuth(",from);
  assert(from>=0&&to>from,"Actual tripTimeline UI must be present");
  const text=(parent,tag,contents,cls)=>{
    const child={tag,contents:contents===undefined?"":String(contents),cls:cls||"",children:[]};
    parent.children.push(child);return child;
  };
  const context={tripBoard:board,text,tripDateTime:time=>time,statusName:status=>status};
  vm.createContext(context);
  vm.runInContext(source.slice(from,to),context,{filename:"app-tripTimeline-real.js"});
  const root={children:[]};context.tripTimeline(root,r);
  const flattened=(n)=>[n,...(n.children||[]).flatMap(flattened)];
  return flattened(root).filter(n=>n.cls==="trip-event").map(n=>{
    const first=n.children.find(c=>c.tag==="div");return first?.contents||n.contents;
  });
}
const fixed={
  id:"safe-synthetic-1",fare_mode:"fixed",status:"ACEPTADA",quote_status:"PRECIO_FIJO",
  created_at:"2026-10-18T09:00:00Z",quote_sent_at:"2026-10-18T09:01:00Z",
  quote_accepted_at:"2026-10-18T09:02:00Z",confirmed_at:"2026-10-18T09:02:00Z",
  pickup_phase:"EN_CAMINO",pickup_phase_updated_at:"2026-10-18T09:04:00Z"
};
assert.deepEqual(JSON.parse(JSON.stringify(board.timeline(fixed).events.map(e=>e.label))),[
 "Solicitud registrada","Conductor aceptó tu viaje","Conductor en camino"
]);
assert.deepEqual(uiEvents(fixed),["Solicitud registrada","Conductor aceptó tu viaje","Conductor en camino"],
 "Real passenger history must never show presupuesto for fixed fare");
const fixedPending={...fixed,status:"PENDIENTE",quote_status:"ENVIADO",quote_final_total:124,
  confirmed_at:null,quote_accepted_at:null,pickup_phase:"SIN_INICIAR",pickup_phase_updated_at:null};
assert.equal(board.metadata(fixedPending).tone,"pending",
 "Fixed fare must not request a second budget acceptance");
assert(!/presupuesto para responder/i.test(board.guidance(fixedPending)));
const legacy={...fixed,id:"safe-synthetic-2",fare_mode:"schedule",status:"ACEPTADA",
  quote_status:"ACEPTADO",pickup_phase:"SIN_INICIAR",pickup_phase_updated_at:null};
assert.deepEqual(uiEvents(legacy),["Solicitud registrada","Presupuesto enviado","Presupuesto aceptado"],
 "Old scheduled budget timeline must remain intact");
const noTime={...fixed,created_at:null,confirmed_at:null,quote_accepted_at:null,
  pickup_phase:"EN_PUNTO",pickup_phase_updated_at:null};
assert.deepEqual(uiEvents(noTime),[],"Do not fabricate event timestamps");
const finished={...fixed,status:"FINALIZADA",pickup_phase:"SIN_INICIAR",pickup_phase_updated_at:null};
assert(uiEvents(finished).some(x=>x==="Estado actual: Finalizada"),
 "Current terminal state may be shown without a fabricated finishing timestamp");
console.log("PASS STATUS-001: fixed/legacy, shared projection, actual passenger history, no invented times");
