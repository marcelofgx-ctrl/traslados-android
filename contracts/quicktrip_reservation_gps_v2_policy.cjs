"use strict";
/**
 * EXPLICITLY NON-PRODUCTION reference policy, no network, no storage, no GPS.
 * Exercises the proposed v2 privacy contract using fictional records only.
 * Real authorization must happen in Postgres, not in browser or Android JS.
 */
const active=(status,phase)=>status==="EN_VIAJE"||
  (["ACEPTADA","ACEPTADA_CLIENTE","CONFIRMADA"].includes(status)&&
   ["EN_CAMINO","EN_PUNTO"].includes(phase));
const invalid=(reason,phase)=>Object.freeze({
  available:false,reason,...(phase?{phase}:{})
});
const finite=n=>typeof n==="number"&&Number.isFinite(n);
function recentPosition(fix,nowMs) {
  if(!fix||!finite(fix.driverLat)||!finite(fix.driverLng)||
     !finite(fix.accuracyM)||!finite(fix.gpsAtMs)||!finite(fix.receivedAtMs))return false;
  if(fix.driverLat < -35.3||fix.driverLat > -30||
     fix.driverLng < -58.8||fix.driverLng > -52.9)return false;
  if(fix.accuracyM<0||fix.accuracyM>45)return false;
  if(fix.gpsAtMs < nowMs-75000||fix.gpsAtMs > nowMs+10000)return false;
  if(fix.receivedAtMs < nowMs-75000||fix.receivedAtMs > nowMs+10000)return false;
  return true;
}
function proposedPublish(context,fix,nowMs){
  if(!context.deviceAuthorized)return {ok:false,reason:"DEVICE_UNAUTHORIZED"};
  if(!context.consent)return {ok:false,reason:"CONSENT_DISABLED"};
  if(!context.shiftActive||context.shiftPaused)return {ok:false,reason:"SHIFT_PAUSED"};
  if(context.openServiceCount!==1||!context.reservationMatches)
    return {ok:false,reason:"OTHER_ACTIVE_SERVICE"};
  if(!active(context.status,context.phase))return {ok:false,reason:"NOT_ACTIVE"};
  if(!recentPosition(fix,nowMs))return {ok:false,reason:"GPS_STALE_OR_IMPRECISE"};
  if(finite(context.previousGpsAtMs)&&context.previousGpsAtMs>=fix.gpsAtMs)
    return {ok:false,reason:"POSITION_NOT_NEWER"};
  return {ok:true,stored:true,reservationId:context.reservationId};
}
function proposedPassengerRead(context,fix,nowMs){
  if(!context.ownsReservation)return invalid("unauthorized");
  if(!active(context.status,context.phase))return invalid("not_active",context.phase);
  if(!context.consent||!context.shiftActive||context.shiftPaused)
    return invalid("position_unavailable",context.phase);
  if(context.openServiceCount!==1||!context.reservationMatches)
    return invalid("position_unavailable",context.phase);
  if(!recentPosition(fix,nowMs))return invalid("position_unavailable",context.phase);
  return {
    available:true,reservationId:context.reservationId,
    phase:context.status==="EN_VIAJE"?"EN_VIAJE":context.phase,
    driverLat:fix.driverLat,driverLng:fix.driverLng,
    gpsAgeSec:Math.max(0,Math.floor((nowMs-fix.gpsAtMs)/1000)),
    accuracyM:fix.accuracyM
  };
}
module.exports={active,recentPosition,proposedPublish,proposedPassengerRead};
