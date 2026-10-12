"use strict";
// Negative public driver availability is not a proof of manual logout.
// Backend timestamp is not a GPS timestamp; GPS, occupancy and connectivity
// gates remain private and must not be inferred from the passenger interface.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path");
const dir=path.join(__dirname,"..");
const app=fs.readFileSync(path.join(dir,"app.js"),"utf8");
const html=fs.readFileSync(path.join(dir,"index.html"),"utf8");
const sw=fs.readFileSync(path.join(dir,"sw.js"),"utf8");
assert(html.includes("app.js?v=67")&&html.includes("styles.css?v=67"));
assert(sw.includes("traslados-cliente-pwa-v67"));
assert(app.includes('const checkedLabel=(elapsedCheck===null?'),"Checked-at stays server age, not GPS");
assert(!app.includes('const checkedLabel="Disponibilidad verificada"'));
assert(!app.includes('title:"Conductor no disponible ahora"'));
assert(app.includes('title:"Recogida inmediata sin confirmar"'));
assert(app.includes('presence:"busy",title:"Recogida inmediata sin confirmar"'));
assert(app.includes('publicPresenceCache=null;publicPresenceAt=0;'));
assert(app.includes(' · Se actualiza solo'));
assert(app.includes('},15000);'),"Foreground checks remain bounded");
assert(app.includes('window.TrasladosPickupCadence.intervalMs(driver.motionHint)'));
assert(app.includes('sessionToken:signedIn?session.token:null'),"Keep original auth behavior");
assert(!app.includes('driverLat')&&!app.includes('driverLng'),"Do not expose private precise driver coordinates");
assert(app.includes('window.TrasladosETA.pickup(')&&app.includes('window.TrasladosETA.availability('));
console.log("PASS PWA v48: honest server-check label, transient unknown status, GPS privacy, 15s cadence");
