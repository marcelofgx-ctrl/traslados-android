"use strict";
const fs=require("node:fs");
const assert=require("node:assert/strict");
function read(x){assert(fs.existsSync(x),"Missing canonical source "+x);return fs.readFileSync(x,"utf8");}
assert(!fs.existsSync("seguimiento-review"),"Obsolete duplicate review website still tracked");
const html=read("web-pasajero/index.html");
const web=read("web-pasajero/app.js");
const live=read("web-pasajero/reservation-live.js");
const focus=read("web-pasajero/quicktrip-map-focus.js");
const mapa=read("mapatrayectos/src/main/java/uy/com/mapatrayectos/Api.java");
const conductor=read("conductor/src/main/java/uy/com/traslados/conductor/Api.java");
const tracker=read("mapatrayectos/src/main/java/uy/com/mapatrayectos/TrackingService.java");
assert(html.includes('src="./reservation-live.js')&&html.includes('src="./quicktrip-map-focus.js'),
  "Canonical passenger tracking dependencies missing");
assert(web.includes('customer_reservation_position_v1')===false,
  "Reservation GPS RPC belongs in stable reservation-live engine, not a new facade");
assert(live.includes('customer_reservation_position_v1')&&live.includes('gpsAgeSec'),
  "Private passenger tracking authorization contract missing");
assert(mapa.includes('mapa_presence_ping_v1')&&
  mapa.includes('getBoolean("enabled",false)')&&
  tracker.includes('publishPickupPresenceAsync'),
  "Existing Mapa consented public presence contract missing");
assert(conductor.includes('driver_set_pickup_phase_v1'),
  "Driver phase transition contract missing");
const webHost=web.match(/const API="([^"]+)"/)?.[1];
const mapaHost=mapa.match(/public static final String BASE="([^"]+)"/)?.[1];
const driverHost=conductor.match(/public static final String BASE="([^"]+)"/)?.[1];
assert(webHost&&webHost===mapaHost&&webHost===driverHost,
  "Frontend, Mapa and Conductor point to different Supabase endpoints");
assert(focus.includes('verifiedTwoLegRenderer')&&focus.includes('syncTrackingStage'),
  "Previously verified QA map transition layer must remain intact");
for(const f of ["web-pasajero/premium-map.js","web-pasajero/route-engine.js",
  "web-pasajero/pickup-cadence.js","web-pasajero/eta-engine.js",
  "web-pasajero/tests/continuous-follow-audit-qa.cjs"])read(f);
console.log("QA PASS: obsolete review copy absent; canonical source and route/GPS contracts intact.");
console.log("QA GAP (investigation only): Android source has consented presence publisher, not a direct reservation-position publisher.");
console.log("QA GAP (investigation only): no live backend contract or Samsung GPS was read; no external connections.");
