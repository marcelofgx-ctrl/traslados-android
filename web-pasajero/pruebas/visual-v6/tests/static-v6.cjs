"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const dir=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(dir,p),"utf8");
const html=read("index.html");
const css=read("styles.css");
const js=read("demo.js");
new vm.Script(js,{filename:"demo.js"});
assert.match(html,/<meta name="robots" content="noindex,nofollow">/);
assert.match(html,/Prototipo visual · datos de ejemplo · no reserva/);
assert.equal((html.match(/class="screen(?: is-active)?"/g)||[]).length,4,
  "Exactly four explicitly illustrative screens");
for(const id of ["paso-1","paso-2","paso-3","paso-4","next-button","back-button","theme-switch","progress"])
  assert(html.includes('id="'+id+'"'),"Missing accessible layout component: "+id);
assert(html.includes('MAPA ILUSTRATIVO'));
assert(html.includes('Valores ilustrativos para evaluar el diseño'));
assert(js.includes('history.back()'),"Demo Back should use browser navigation");
assert(js.includes('theme-dark'),"Both light and dark prototypes should exist");
assert(css.includes('@media(max-width:370px)')&&css.includes('max-width:480px'),"Mobile viewport contract");
assert(css.includes('prefers-reduced-motion'),"Accessibility motion control");
assert(!/body\.map-first-v3|body\.mf-v5|#urgent-send|\.premium-route-canvas/.test(css),
  "No legacy CSS or prior layout inheritance allowed");
for(const source of [html,js]){
  assert(!/\bfetch\s*\(|\bXMLHttpRequest\b|\.geolocation\b|\bsupabase\b|\brpc\s*\(/i.test(source),
    "Static layout must not query providers or send bookings");
  assert(!/localStorage|sessionStorage|document\.cookie|create_reservation|confirm-booking|serviceWorker\.register/.test(source),
    "No persistent personal state, booking, PWA registration, or production API");
}
assert(!/<form\b/i.test(html),"Static V6 is a visual approval artifact, not a live form");
assert(!/<script[^>]*src=["']https?:/i.test(html),"No third-party JS in visual study");
assert(!/url\(\s*https?:/i.test(css),"No external image/font or fake live map");
console.log("PASS V6 visual lab: four independent screens, two themes, responsive, no APIs or booking");
