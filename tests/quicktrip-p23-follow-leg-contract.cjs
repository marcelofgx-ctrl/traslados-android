"use strict";
// QA P2.3 — source safety: only visual hierarchy, never approximate a car.
const fs=require("node:fs"),assert=require("node:assert/strict");
const read=x=>fs.readFileSync(x,"utf8");
const css=read("web-pasajero/quicktrip-p23-follow-leg-readability.css");
const html=read("web-pasajero/index.html"),sw=read("web-pasajero/sw.js");
const tracking=read("web-pasajero/reservation-live.js");
const focus=read("web-pasajero/quicktrip-map-focus.js");
function check(label,ok){assert.ok(ok,label);console.log("PASS P23",label);}
check("Original long address content kept, with a taller scrolling header",
  css.includes("max-height:min(42dvh,310px)!important")&&
  css.includes("overflow-y:auto!important")&&
  css.includes("white-space:normal!important")&&
  css.includes("text-overflow:clip!important"));
check("Reserve floating-button space without overlapping either address",
  css.includes("padding:12px 69px 12px 13px!important")&&
  css.includes("padding-right:61px!important"));
check("Only TWO verified route legs may soften the contracted trip",
  css.includes('[data-gps-verified="true"][data-follow-stage="pickup"]')&&
  css.includes('.qt-focus-map:has(.premium-map-approach) .premium-map-route')&&
  css.includes('.qt-focus-map .premium-map-approach'));
check("During the trip, do not soften main route or change GPS/polling",
  !css.includes('[data-follow-stage="ride"]')&&
  !/fetch\(|setInterval\(|watchPosition|navigator\.geolocation/.test(css)&&
  tracking.includes('rpc("customer_reservation_position_v1"')&&
  tracking.includes('},4000)')&&focus.includes('screen.dataset.followStage=onboard?'));
check("P2.3 loads after approved CSS and into separate PWA cache",
  html.indexOf("quicktrip-p23-follow-leg-readability.css")>
    html.indexOf("quicktrip-p18-fare-flow-icons.css")&&
  sw.includes('"./quicktrip-p23-follow-leg-readability.css"')&&
  sw.includes("traslados-pwa-quicktrip-p23-follow-leg-readability-20261102"));
console.log("P2.3 visual-only layout contract passed; physical Samsung QA pending.");
