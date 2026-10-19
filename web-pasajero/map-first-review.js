"use strict";
/* Gated review-only layout; never impersonates GPS, driver presence or fare.
 * Query opt-in prevents accidental exposure even if these files are deployed. */
(function(root){
  const requested=new URL(root.location.href).searchParams.get("mapfirst")==="1";
  if(!requested)return;
  root.document.body.classList.add("map-first-review");
  root.document.body.dataset.mapFirstReview="1";
})(window);
