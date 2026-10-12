"use strict";
// Visual selected route fix and separation of trip quote from pickup ETA.
// This test does not access GPS, network, or create reservations.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path");
const root=path.join(__dirname,"..");
const read=n=>fs.readFileSync(path.join(root,n),"utf8");
const app=read("app.js"),css=read("styles.css"),html=read("index.html"),sw=read("sw.js");
assert(html.includes('app.js?v=61')&&html.includes('styles.css?v=61'));
assert(sw.includes('traslados-cliente-pwa-v61'));
assert(css.includes('/* PWA v47 — corrección de la tarjeta consolidada A/B en Samsung.'),"Versioned visual correction");
assert(css.includes('@media(hover:none)'),"Touch must clear sticky hover");
assert(css.includes('.booking-step-card[data-booking-step="route"].is-open .booking-step-content'),"Open route spacing");
assert(css.includes('padding-top:14px'),"Separate header and address summary");
assert(css.includes('.location-widget.is-selected .location-title'),"Selected state gives label consistent spacing");
assert(css.includes('.location-widget .location-selected::before'),"Keep subtle verified accent");
assert(css.includes('.location-selected .location-edit'),"Edit action contained");
assert(css.includes('overflow-wrap:anywhere'),"Long airport name must fit within card");
assert(app.includes('box.classList.remove("is-selected");selectedBox.hidden=true;'),"Edit restores default state");
assert(app.includes('text(km,"small","DISTANCIA DEL VIAJE")'));
assert(app.includes('text(min,"small","DURACIÓN DEL VIAJE")'));
assert(app.includes('No incluye el recorrido del conductor hasta recogerte'),"Passenger fare is not pickup distance");
assert(app.includes('referenceFareUyu'),"Keep verified backend reference price");
assert(app.includes('roadEngine.verified(data)'),"Only verified road estimates");
assert(app.includes('window.TrasladosETA.availability')&&app.includes('window.TrasladosETA.pickup'),"Pickup ETA remains independent");
assert(app.includes('getCurrentPosition(async p=>'),"Retain GPS opt-in");
console.log("PASS PWA v47: selected card spacing, touch hover, trip metrics, pickup ETA independence and unchanged pricing contract");
