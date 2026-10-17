"use strict";
// Behavioral integration with synthetic accounts/receipts. No live backend calls.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const web=path.join(__dirname,".."),app=fs.readFileSync(path.join(web,"app.js"),"utf8");
const section=(start,end)=>app.slice(app.indexOf(start),app.indexOf(end,app.indexOf(start)));
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return{promise,resolve,reject};};
function historyContext(token,receipts,server){
  const nodes=new Map();
  const ctx={window:{},navigator:{onLine:true},session:{token,customer:{id:"synthetic-owner"}},
    document:{querySelectorAll:()=>[]},Set,Map,Date,JSON,Promise,
    tripRows:[],tripLoaded:false,tripsLoading:false,tripsRefreshPending:false,tripSnap:null,
    tripQueryEpoch:0,tripRenderedSignature:null,tripsScopeChosen:false,
    activeTripChat:null,selectedPickupMode:"schedule",openTripCards:new Set(),
    GUEST_KEY:"receipts",authReady:()=>true,jsonStorage:()=>receipts,
    tripDisplayKey:rows=>JSON.stringify(rows),renderTripsBoard:()=>{},showTripNotice:()=>{},showError:()=>{},
    $:id=>{if(!nodes.has(id))nodes.set(id,{textContent:""});return nodes.get(id);},
    rpc:server};
  vm.createContext(ctx);
  for(const file of ["guest-history.js","trip-board.js"])vm.runInContext(fs.readFileSync(path.join(web,file),"utf8"),ctx);
  ctx.tripBoard=ctx.window.TrasladosTripBoard;
  vm.runInContext(section("async function loadTrips(","// La instalación automática"),ctx);
  return ctx;
}
(async()=>{
  const row={id:"owned-1",code:"QA-ONE",status:"ACEPTADA",fare_mode:"fixed",quote_final_total:256,
    pickup_date:"2026-10-17",pickup_time:"12:00",created_at:new Date().toISOString()};
  const guest={id:"legacy-1",code:"QA-LEGACY",status:"RECHAZADA"};
  let serverRows=[row];
  const server=async(name,payload)=>{
    if(name==="customer_list_reservations_v16"){
      assert(["web-session","webview-session"].includes(payload.p_session_token));
      return serverRows.map(r=>({...r}));
    }
    assert.equal(name,"get_reservation_by_token_v5");
    assert.equal(payload.p_token,"proof-only-on-web");return{...guest};
  };
  const webContext=historyContext("web-session",[{token:"proof-only-on-web"}],server);
  const apkContext=historyContext("webview-session",[],server);
  await Promise.all([webContext.loadTrips(),apkContext.loadTrips()]);
  assert.deepEqual(JSON.parse(JSON.stringify(webContext.tripRows[0])),JSON.parse(JSON.stringify(apkContext.tripRows[0])));
  assert.equal(webContext.tripRows.length,2,"Legacy receipt stays on its proving browser");
  assert.equal(apkContext.tripRows.length,1,"Separate WebView storage must not acquire unproved guest history");
  for(const status of ["ACEPTADA","EN_VIAJE","FINALIZADA"]){
    serverRows=[{...row,status}];
    await Promise.all([webContext.loadTrips(),apkContext.loadTrips()]);
    assert.equal(webContext.tripRows[0].status,status);assert.equal(apkContext.tripRows[0].status,status);
  }
  const first=deferred();let reads=0;
  const polling=historyContext("web-session",[],async()=>++reads===1?first.promise:serverRows);
  const oldPoll=polling.loadTrips({background:true});
  await polling.loadTrips({background:true}); // e.g. a confirmed send during that poll
  first.resolve([]);await oldPoll;await tick();
  assert.equal(reads,2,"Do not lose post-send refresh behind an in-flight poll");
  assert.equal(polling.tripRows[0].id,row.id);
  polling.rpc=async()=>{throw Error("offline");};
  await polling.loadTrips();
  assert.equal(polling.tripRows[0].id,row.id,"Failed refresh preserves known history");
  const late=deferred();polling.rpc=()=>late.promise;
  const stale=polling.loadTrips();polling.tripQueryEpoch++;
  late.resolve([{id:"wrong-session"}]);await stale;
  assert.equal(polling.tripRows[0].id,row.id,"Late result cannot cross account changes");

  const fields=new Map();const storage=new Map();let cleared=0,issued=0,transport;
  const draft={origin:{text:"Origen QA",lat:-34.9,lng:-56.1},destination:{text:"Destino QA",lat:-34.8,lng:-56},stops:[]};
  const booking={...draft,pickupMode:"now",passengers:1,name:"Pasajero QA",phone:"099000000",comments:"nota"};
  const road={available:true,source:"openrouteservice",distanceKm:6.4,durationMin:11,referenceRateUyuPerKm:40,referenceFareUyu:256};
  const sent=[];
  const ctx={window:{TrasladosOwnedPickup:{match:()=>null}},JSON,Set,Date,Promise,
    PREVIEW_READ_ONLY:false,selectedPickupMode:"now",busy:false,session:null,tripRows:[],
    premiumJourney:{current:()=>"confirm"},accountJourneyReturn:null,pendingReview:null,record:draft,
    originPicker:{resetSelection:()=>cleared++},destPicker:{resetSelection:()=>cleared++},
    renderStops:()=>{},renderRouteSummary:()=>{},routeStepAutoAdvanced:true,urgentAttempt:null,
    latestRoad:road,activeRouteKey:"old-route",authReady:()=>Boolean(ctx.session),
    shiftView:view=>ctx.view=view,tell:()=>{},loading:()=>{},guardConnection:()=>{},
    suggestBookingStep:()=>{},showError:()=>{},bookingData:()=>booking,verifiedFixedFareForBooking:()=>road,
    newClientRequestId:()=>"00000000-0000-4000-8000-"+String(++issued).padStart(12,"0"),
    saveGuest:r=>storage.set(r.code,r.public_token),
    $:id=>{if(!fields.has(id))fields.set(id,{value:"draft",hidden:false,textContent:""});return fields.get(id);},
    rpc:async(name,payload)=>{sent.push({name,payload});return transport();}};
  vm.createContext(ctx);vm.runInContext(fs.readFileSync(path.join(web,"booking-contracts.js"),"utf8"),ctx);
  vm.runInContext(section("function resetNewTripDraft(","function verifiedFixedFareForBooking("),ctx);
  vm.runInContext(section("function requireAccountForNewTrip(","async function submitBooking("),ctx);
  ctx.renderBookingSuccess=result=>{ctx.resetNewTripDraft();ctx.receipt=result;};
  await ctx.submitUrgentPickup();
  assert.equal(ctx.view,"cuenta");assert.equal(sent.length,0);
  assert.equal(ctx.accountJourneyReturn.stage,"confirm");
  assert.equal(draft.origin.text,"Origen QA","Account gate keeps draft");
  ctx.session={token:"synthetic-session",customer:{id:"synthetic-owner"}};
  const failure=deferred();transport=()=>failure.promise;
  const firstSend=ctx.submitUrgentPickup();await ctx.submitUrgentPickup();
  assert.equal(sent.length,1,"Double tap produces one in-flight request");
  failure.reject(Error("network response lost"));await firstSend;
  assert.equal(cleared,0);assert.equal(draft.origin.text,"Origen QA");
  transport=async()=>({code:"QA-ONE",public_token:"synthetic-proof",fare_mode:"fixed"});
  await ctx.submitUrgentPickup();
  assert.equal(sent.length,2);assert.equal(sent[0].payload.p_request_id,sent[1].payload.p_request_id,
    "Ambiguous retry reuses its idempotency key");
  assert.equal(sent[1].payload.p_session_token,"synthetic-session");
  assert.equal(sent[1].payload.p_expected_total,256);
  assert.equal(draft.origin,null);assert.equal(draft.destination,null);assert.equal(cleared,2);
  assert.equal(ctx.$("comments").value,"");assert.equal(ctx.$("passengers").value,"1");
  assert.equal(ctx.$("premium-person-email").value,"");
  assert.equal(storage.get("QA-ONE"),"synthetic-proof","Clearing draft retains receipt");
  console.log("PASS v77 parity: separate sessions, shared account history, states, proof isolation, queued refresh, stale-session discard, double tap and ambiguous retry, success-only cleanup");
})().catch(e=>{console.error(e);process.exitCode=1;});
