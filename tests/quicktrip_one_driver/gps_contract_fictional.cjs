"use strict";
const assert=require("node:assert/strict");
const {active,proposedPublish,proposedPassengerRead}=require("../../contracts/quicktrip_reservation_gps_v2_policy.cjs");
const now=Date.parse("2026-10-23T18:20:00.000Z");
const uuid="00000000-0000-4000-8000-000000000001";
const fixture={
 deviceAuthorized:true,consent:true,shiftActive:true,shiftPaused:false,
 openServiceCount:1,reservationMatches:true,ownsReservation:true,
 reservationId:uuid,status:"ACEPTADA",phase:"EN_CAMINO",
 previousGpsAtMs:now-15000
};
const fix={
 driverLat:-34.837,driverLng:-56.025,
 accuracyM:7.5,gpsAtMs:now-1000,receivedAtMs:now-500
};
function deny(fn,ctx,position,reason){
  const value=fn({...fixture,...ctx},position||fix,now);
  assert.equal(value.ok??value.available,false,JSON.stringify({ctx,reason,value}));
  assert.equal(value.reason,reason);
  assert(!("driverLat" in value||"driverLng" in value||"deviceSecret" in value),
    "Rejected reads must never return any coordinates or secret");
}
assert.equal(active("ACEPTADA","EN_CAMINO"),true);
assert.equal(active("ACEPTADA","EN_PUNTO"),true);
assert.equal(active("EN_VIAJE","SIN_INICIAR"),true);
assert.equal(active("ACEPTADA","SIN_INICIAR"),false);
assert.equal(active("FINALIZADA","EN_CAMINO"),false);
assert.equal(proposedPublish(fixture,fix,now).ok,true);
const passenger=proposedPassengerRead(fixture,fix,now);
assert.equal(passenger.available,true);
assert.equal(passenger.reservationId,uuid);
assert.equal(passenger.gpsAgeSec,1);
assert.equal(passenger.phase,"EN_CAMINO");
assert.equal(passenger.driverLat,fix.driverLat);
assert(!("deviceSecret" in passenger||"pin" in passenger));
deny(proposedPublish,{deviceAuthorized:false},fix,"DEVICE_UNAUTHORIZED");
deny(proposedPublish,{consent:false},fix,"CONSENT_DISABLED");
deny(proposedPublish,{shiftPaused:true},fix,"SHIFT_PAUSED");
deny(proposedPublish,{openServiceCount:2},fix,"OTHER_ACTIVE_SERVICE");
deny(proposedPublish,{reservationMatches:false},fix,"OTHER_ACTIVE_SERVICE");
deny(proposedPublish,{status:"ACEPTADA",phase:"SIN_INICIAR"},fix,"NOT_ACTIVE");
deny(proposedPublish,{}, {...fix,gpsAtMs:now-76000},"GPS_STALE_OR_IMPRECISE");
deny(proposedPublish,{}, {...fix,accuracyM:60},"GPS_STALE_OR_IMPRECISE");
deny(proposedPublish,{}, {...fix,driverLat:41.9},"GPS_STALE_OR_IMPRECISE");
deny(proposedPublish,{previousGpsAtMs:now-500},fix,"POSITION_NOT_NEWER");
deny(proposedPassengerRead,{ownsReservation:false},fix,"unauthorized");
deny(proposedPassengerRead,{openServiceCount:2},fix,"position_unavailable");
deny(proposedPassengerRead,{reservationMatches:false},fix,"position_unavailable");
deny(proposedPassengerRead,{consent:false},fix,"position_unavailable");
deny(proposedPassengerRead,{shiftPaused:true},fix,"position_unavailable");
deny(proposedPassengerRead,{status:"FINALIZADA"},fix,"not_active");
deny(proposedPassengerRead,{status:"ACEPTADA",phase:"SIN_INICIAR"},fix,"not_active");
deny(proposedPassengerRead,{}, {...fix,gpsAtMs:now-76000},"position_unavailable");
deny(proposedPassengerRead,{}, {...fix,accuracyM:90},"position_unavailable");
const inbound=proposedPassengerRead({...fixture,status:"EN_VIAJE",phase:"EN_PUNTO"},fix,now);
assert.equal(inbound.available,true);assert.equal(inbound.phase,"EN_VIAJE");
console.log("PASS: 24 fake GPS checks, no cross-reservation disclosure, no revoked/stale position, no production network");
