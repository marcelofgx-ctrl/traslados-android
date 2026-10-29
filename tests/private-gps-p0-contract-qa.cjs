"use strict";
/* P0 private vehicle GPS: OFFLINE contract audit. No live Supabase, no bookings,
 * no Android build, no credentials, no network access. SQL fixture sources were
 * captured using read-only pg_get_functiondef; driver Java excerpt is from R25.38.
 * All events are synthetic and carry reason codes only.
 */
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const home=path.resolve(__dirname,"..");
const base="tests/fixtures/private-gps-p0/";
const read=f=>fs.readFileSync(path.join(home,f),"utf8");
const publisher=read(base+"mapa_reservation_position_v1.sql");
const consumer=read(base+"customer_reservation_position_v1.sql");
const java=read(base+"Mapa_R25_38_publishPickupPresenceAsync.java.txt");
const f1=read("web-pasajero/reservation-live.js");
const policy={
 activeAccepted:new Set(["ACEPTADA","ACEPTADA_CLIENTE","CONFIRMADA"]),
 activeAny:new Set(["ACEPTADA","ACEPTADA_CLIENTE","CONFIRMADA","EN_VIAJE"]),
 pickupEligible:new Set(["EN_CAMINO","EN_PUNTO"]),
 ttlSec:75,maxFutureSec:10,maxAccuracyM:45
};
let cases=0;
const evidence=[];
function check(name,condition,details={}){
  assert(condition,name+" "+JSON.stringify(details));
  evidence.push({scenario:name,passed:true,...details});
  cases++;
  console.log("QA P0 PASS "+name);
}
function eligible(r){return r.status==="EN_VIAJE"||
  (policy.activeAccepted.has(r.status)&&policy.pickupEligible.has(r.pickupPhase));}
function validGeo(p){return Number.isFinite(p.lat)&&p.lat>=-35.3&&p.lat<=-30&&
  Number.isFinite(p.lng)&&p.lng>=-58.8&&p.lng<=-52.9;}
function publisherDecision({linked=true,consent=true,shift=true,paused=false,
      accuracy=4,gpsAgeSec=2,gpsFutureSec=0,lat=-34.82,lng=-56.02,
      reservations=[],existingNewer=false}={}){
  if(!linked||!consent)return {ok:false,reason:"unauthorized"};
  if(!shift||paused||!validGeo({lat,lng})||accuracy<0||accuracy>45)
    return {ok:false,reason:"gps_unavailable"};
  if(gpsAgeSec>75||gpsFutureSec>10)return {ok:false,reason:"stale_gps"};
  const eligibleBookings=reservations.filter(eligible);
  if(eligibleBookings.length!==1)return {ok:false,reason:"no_unique_active_reservation"};
  // The SQL UPSERT has a timestamp WHERE and the function still returns ok:true
  // when an older packet cannot replace the newer stored position.
  return {ok:true,updated:true,actuallyWritten:!existingNewer};
}
function passengerDecision({authorized=true,status="ACEPTADA",pickupPhase="EN_CAMINO",
   linkedPosition=true,presence=true,enabled=true,shift=true,paused=false,
   deviceMatches=true,ageSec=3,receivedAgeSec=2,futureSec=0,accuracy=4}={}){
  if(!authorized)return {available:false,reason:"unauthorized"};
  if(!policy.activeAny.has(status))return {available:false,reason:"not_active"};
  const phase=status==="EN_VIAJE"?"EN_VIAJE":pickupPhase;
  if(!["EN_CAMINO","EN_PUNTO","EN_VIAJE"].includes(phase))
    return {available:false,reason:"preparing_pickup"};
  if(!linkedPosition||!presence||!enabled||!shift||paused||
     !deviceMatches||ageSec>75||receivedAgeSec>75||futureSec>10||
     accuracy>45)return {available:false,reason:"position_unavailable"};
  return {available:true,phase};
}
function booking(status,pickupPhase){return {status,pickupPhase};}
function equals(o,expected){return JSON.stringify(o)===JSON.stringify(expected);}
function reason(o,r){return o.reason===r;}
const one=[booking("ACEPTADA","EN_CAMINO")];
const three=[booking("EN_VIAJE","SIN_INICIAR"),booking("CONFIRMADA","EN_PUNTO"),
  booking("ACEPTADA_CLIENTE","EN_CAMINO")];

check("sql-publisher-is-reservation-global-not-driver-bound",
  /from public\.reservations[\s\S]*limit 2/i.test(publisher)&&
  /if v_count<>1 then/.test(publisher)&&
  !/where\s+(?:r\.)?device_id\s*=\s*p_device_id/i.test(publisher));
check("sql-publisher-uses-device-secret-consent-and-upsert",
  publisher.includes("d.device_secret=p_device_secret")&&
  publisher.includes("p.enabled")&&
  publisher.includes("on conflict(reservation_id) do update"));
check("sql-publisher-returns-boolean-success-without-detecting-older-upsert",
  publisher.includes("gps_recorded_at<=excluded.gps_recorded_at")&&
  publisher.includes("'updated',true")&&
  !publisher.includes("GET DIAGNOSTICS"));
check("sql-consumer-authorizes-customer-or-guest-token",
  consumer.includes("availability_session_customer")&&
  consumer.includes("public_token=p_public_token")&&
  consumer.includes("customer_id=v_customer"));
check("sql-consumer-uses-correct-reservation-and-device",
  consumer.includes("where reservation_id=p_reservation_id")&&
  consumer.includes("v_presence.device_id is distinct from v_loc.device_id"));
check("sql-consumer-uses-75-second-privacy-window",
  /v_loc\.received_at<now\(\)-interval '75 seconds'/.test(consumer)&&
  /v_loc\.gps_recorded_at<now\(\)-interval '75 seconds'/.test(consumer));
check("R2538-publisher-calls-private-rpc-but-discardes-response",
  java.includes('postRpc("mapa_reservation_position_v1",privateBody);')&&
  !java.includes('JSONObject privateResult=new JSONObject(postRpc("mapa_reservation_position_v1"')&&
  java.includes("catch(Exception ignored)"));
check("R2538-publisher-gates-behind-public-presence-success",
  java.includes('result.optBoolean("ok",false)&&active&&!paused')&&
  java.includes("pickupPresenceEnabled()"));
check("PWA-poll-is-authorized-per-reservation",
  f1.includes('rpc("customer_reservation_position_v1"')&&
  f1.includes("p_reservation_id:r.id")&&
  f1.includes("p_public_token:sessionToken?null:guestToken||null"));
check("PWA-never-paints-stale-or-missing-driver-GPS",
  f1.includes("fresh>75")&&f1.includes('host.dataset.gpsState="waiting"'));

check("publisher-zero-eligible",reason(publisherDecision({reservations:[]}),"no_unique_active_reservation"));
check("publisher-two-eligible",reason(publisherDecision({reservations:three}),"no_unique_active_reservation"),
  {syntheticEligible:3});
check("publisher-one-eligible",publisherDecision({reservations:one}).ok===true);
check("publisher-accepted-but-not-enroute-is-not-eligible",reason(
  publisherDecision({reservations:[booking("ACEPTADA","SIN_INICIAR")]}),"no_unique_active_reservation"));
check("publisher-accepted-pending-plus-one-enroute-is-valid",publisherDecision({
  reservations:[booking("ACEPTADA","SIN_INICIAR"),...one]}).ok===true);
check("publisher-unauthorized-device",reason(publisherDecision({reservations:one,linked:false}),"unauthorized"));
check("publisher-consent-revoked",reason(publisherDecision({reservations:one,consent:false}),"unauthorized"));
check("publisher-paused",reason(publisherDecision({reservations:one,paused:true}),"gps_unavailable"));
check("publisher-bad-accuracy",reason(publisherDecision({reservations:one,accuracy:48}),"gps_unavailable"));
check("publisher-invalid-geo",reason(publisherDecision({reservations:one,lat:0}),"gps_unavailable"));
check("publisher-stale-75s",reason(publisherDecision({reservations:one,gpsAgeSec:76}),"stale_gps"));
check("publisher-future-timestamp",reason(publisherDecision({reservations:one,gpsFutureSec:11}),"stale_gps"));
check("publisher-older-point-rejected-but-returns-ok",
  equals(publisherDecision({reservations:one,existingNewer:true}),{ok:true,updated:true,actuallyWritten:false}));

check("passenger-unauthorized-reservation",reason(passengerDecision({authorized:false}),"unauthorized"));
check("passenger-awaits-enroute",reason(passengerDecision({pickupPhase:"SIN_INICIAR"}),"preparing_pickup"));
check("passenger-ineligible-status",reason(passengerDecision({status:"CANCELADA"}),"not_active"));
check("passenger-accepts-enroute-with-recent-GPS",passengerDecision({}).available===true);
check("passenger-accepts-onboard-with-recent-GPS",passengerDecision({status:"EN_VIAJE"}).available===true);
check("passenger-no-position",reason(passengerDecision({linkedPosition:false}),"position_unavailable"));
check("passenger-different-device",reason(passengerDecision({deviceMatches:false}),"position_unavailable"));
check("passenger-consent-off",reason(passengerDecision({enabled:false}),"position_unavailable"));
check("passenger-no-active-shift",reason(passengerDecision({shift:false}),"position_unavailable"));
check("passenger-GPS-older-than-75",reason(passengerDecision({ageSec:76}),"position_unavailable"));
check("passenger-server-receipt-older-than-75",reason(passengerDecision({receivedAgeSec:76}),"position_unavailable"));
check("passenger-accuracy-too-low",reason(passengerDecision({accuracy:50}),"position_unavailable"));

const previousPublic={ok:true};
function diagnose(privateResult,error){
  if(error)return {phase:"private_transport_failure",reason:"transport_exception"};
  if(!privateResult||privateResult.ok!==true)return {
    phase:"private_rpc_rejected",reason:privateResult?.reason||"unknown_rejection"};
  return {phase:"private_rpc_acknowledged",reason:"ok"};
}
check("diagnostic-classifies-private-rejection",equals(
  diagnose({ok:false,reason:"no_unique_active_reservation"}),{
  phase:"private_rpc_rejected",reason:"no_unique_active_reservation"}));
check("diagnostic-classifies-private-transport-exception",equals(
  diagnose(null,new Error("synthetic")),{
  phase:"private_transport_failure",reason:"transport_exception"}));
check("diagnostic-public-presence-is-not-private-success",
  previousPublic.ok===true&&diagnose({ok:false,reason:"unauthorized"}).phase!=="private_rpc_acknowledged");

const out=path.join(home,"artifacts/private-gps-p0");
fs.mkdirSync(out,{recursive:true});
const report={
  name:"Quick Trip private GPS P0 source-and-mock audit",
  source:"R25.38 Android method + SQL pg_get_functiondef metadata snapshots",
  execution:"offline, synthetic only",
  rowDataRead:false,reservationsCreated:0,productionChanges:0,apkBuilt:false,
  cases,passed:cases,failed:0,
  conclusions:[
    "The private SQL publisher requires exactly one globally eligible reservation.",
    "The R25.38 Java publisher ignores the private RPC logical response and catches exceptions without diagnostics.",
    "Guest/session consumer requires authorization, a matching device and GPS received and recorded within 75 seconds.",
    "Earlier accepted-but-not-enroute bookings do not count as eligible.",
    "UPSERT ignoring older GPS can still return ok=true updated=true."
  ],
  evidence
};
fs.writeFileSync(path.join(out,"diagnostic-report.json"),JSON.stringify(report,null,2)+"\n");
console.log("QA P0 SUMMARY "+cases+" PASS, 0 FAIL, 0 REAL REQUESTS");
