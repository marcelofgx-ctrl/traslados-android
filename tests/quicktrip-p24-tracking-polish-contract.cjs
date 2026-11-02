"use strict";
// P2.4 QA scope: animation and address disclosure do not alter GPS or booking.
const assert=require("node:assert/strict"),fs=require("node:fs");
const get=f=>fs.readFileSync(f,"utf8");
const focus=get("web-pasajero/quicktrip-map-focus.js");
const map=get("web-pasajero/premium-map.js");
const live=get("web-pasajero/reservation-live.js");
const css=get("web-pasajero/quicktrip-p24-tracking-polish.css");
const index=get("web-pasajero/index.html");
const sw=get("web-pasajero/sw.js");
const ok=(what,passes)=>{assert.ok(passes,what);console.log("PASS P24",what)};
ok("Auto-collapse after 3300ms is one-shot per booking and a user override persists",
 focus.includes('deadline:Date.now()+3300')&&
 focus.includes('routePreference.manual=true')&&
 focus.includes('if(!sameReservation)routePreference=')&&
 focus.includes('setRouteCompact(routePreference.compact)')&&
 focus.includes('scheduleRouteCollapse(r.id)')&&
 focus.includes('routeToggle.setAttribute("aria-expanded","true")'));
ok("Header disclosure is a real accessible button with two supported states",
 focus.includes('make("button","qt-focus-route-toggle"')&&
 focus.includes('routeToggle.addEventListener("click",()=>setRouteCompact(!routePreference.compact,true))')&&
 css.includes('.qt-route-compact .qt-focus-tracking-point')&&
 css.includes('display:none!important'));
ok("Verified pickup line gains animated overlay ONLY with a confirmed car fix",
 map.includes('if(options.routeMode==="pickup"&&vehicle)')&&
 map.includes('premium-map-approach-flow')&&
 css.includes('[data-gps-verified="true"][data-follow-stage="pickup"]')&&
 css.includes('animation:qt-p24-flow 2.15s linear infinite')&&
 css.includes('@media(prefers-reduced-motion:reduce)'));
ok("Animated flow does not simulate moving vehicle position",
 !/Math\.random|setInterval\(|watchPosition|navigator\.geolocation/.test(css)&&
 !/Math\.random|setInterval\(|watchPosition|navigator\.geolocation/.test(map)&&
 map.includes('if(vehicle){')&&map.includes('drawCarMarker(at);'));
ok("Top-down driver marker is positioned at the same authenticated GPS point",
 map.includes('g.setAttribute("transform","translate("+p[0]+" "+p[1]+")")')&&
 map.includes('if(options.previousCar&&!options.overview')&&
 map.includes('premium-map-driver')&&
 !map.includes('drawMarker(at,"🚘"'));
ok("Sheet and map use premium marker rather than emoji",
 focus.includes('make("span","qt-focus-car","")')&&
 css.includes('background:transparent url("data:image/svg+xml,'));
ok("Existing private reservation and true GPS route engine remain unchanged",
 live.includes('rpc("customer_reservation_position_v1"')&&
 live.includes('await paint([pos,destination],"driver")')&&
 live.includes('},4000)')&&focus.includes('syncTrackingStage(trackingCtx.reservation)'));
ok("QA PWA loads correctly versioned assets",
 index.includes("quicktrip-p24-tracking-polish.css?v=p24-20261102")&&
 index.includes("premium-map.js?v=p24-vehicle-motion-20261102")&&
 index.includes("quicktrip-map-focus.js?v=p24-auto-header-20261102")&&
 sw.includes("traslados-pwa-quicktrip-p24-tracking-polish-20261102"));
console.log("P2.4 UI-only QA source contract PASS; Samsung visual validation still required.");
