"use strict";
/* Client-only pickup scheduling: backend controls authorization, quotas,
 * freshness and real GPS. Never infer live location from these constants.
 */
(function(root){
  function intervalMs(motionHint){
    return motionHint==="moving"?25000:45000;
  }
  // Edge intentionally rounds ETA up to a 5-minute privacy bucket.
  // Display it as a range rather than a falsely precise exact arrival.
  function arrivalLabel(minutes){
    if(!Number.isFinite(minutes)||minutes<=0)return "Por confirmar";
    const upper=Math.max(5,Math.ceil(minutes/5)*5);
    if(upper<=5)return "Hasta 5 min";
    return (upper-5)+"–"+upper+" min";
  }
  root.TrasladosPickupCadence=Object.freeze({intervalMs,arrivalLabel});
})(window);
