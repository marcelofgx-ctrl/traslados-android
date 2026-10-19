"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const dir=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(dir,p),"utf8");
const html=read("index.html");
const css=read("quicktrip-v6.css");
const ui=read("quicktrip-v6.js");
const logo=read("quicktrip-logo.svg");
new vm.Script(ui,{filename:"quicktrip-v6.js"});
const js=read("demo.js");
const model=read("view-model.js");
new vm.Script(model,{filename:"view-model.js"});
new vm.Script(js,{filename:"demo.js"});
assert.match(html,/<meta name="robots" content="noindex,nofollow">/);
assert.match(html,/Versión experimental · datos ilustrativos · sin reservas/);
assert.equal((html.match(/class="screen(?: is-active)?"/g)||[]).length,4,
  "Exactly four explicitly illustrative screens");
for(const id of ["paso-1","paso-2","paso-3","paso-4","next-button","back-button"])
  assert(html.includes('id="'+id+'"'),"Missing accessible layout component: "+id);
assert(html.includes('MAPA ILUSTRATIVO'));
const homeCss=read("home-v6.css");
assert(html.includes('id="qt-home"')&&html.includes('id="qt-home-title"'),"V6 home must exist");
assert.equal((html.match(/data-v6-service="/g)||[]).length,3,"Exactly three historical services");
for(const service of ['data-v6-service="now"','data-v6-service="schedule"','data-v6-service="delivery"'])
  assert(html.includes(service),"Missing service entry: "+service);
assert(html.includes("Tu Viaje")&&html.includes("Tu <em>Paquete</em>")&&html.includes("Programado"),"All three service labels");
assert(html.includes('id="qt-home-parcel-message"')&&html.includes("Todavía no se pueden solicitar envíos"),"Parcel cannot create a request");
assert(html.includes('href="./home-v6.css?v=1"'),"Home style loaded");
assert(homeCss.includes('../../premium-montevideo.webp')&&homeCss.includes('../../premium-skyline.svg'),"Historical local hero assets");
assert(!/url\\(\\s*https?:/.test(homeCss),"Home must not load external assets");
assert(js.includes('function showHome(')&&js.includes('show(0)')&&js.includes('programmed?"Otra hora":"Ahora"'),"Both travel services reuse one four-step flow");
assert(js.includes('if(index<0){showHome({historyEntry});return;}'),"Browser back must return to home");

assert(html.includes('Valores ilustrativos para evaluar el diseño'));
assert(js.includes('history.back()'),"Demo Back should use browser navigation");
assert(ui.includes('id="theme-switch"')&&ui.includes('id="progress"'),"New QuickTrip header and progress mounted");
assert(ui.includes("Quick Trip")&&logo.includes("Quick")&&logo.includes("go"),"Quick Trip brand");
assert(js.includes("quicktrip-v6-theme")&&js.includes("localStorage.setItem(themeKey,mode)"),"Persistent theme only");
assert(js.includes('theme-dark'),"Both light and dark prototypes should exist");
assert(html.includes('src="./view-model.js?v=1"'),"Model loaded before demo");
assert(html.indexOf("view-model.js?v=1")<html.indexOf("quicktrip-v6.js?v=1"));
assert(html.indexOf("quicktrip-v6.js?v=1")<html.indexOf("demo.js?v=3"));
assert(html.includes('data-v6-field="route-origin"')&&html.includes('data-v6-field="trip-price"'),"Same snapshot feeds both screens");
assert(css.includes("position:fixed;bottom:76px"),"Primary button must remain accessible above bottom nav");
assert(css.includes("padding-bottom:162px"),"Fixed CTA and nav have safe scroll clearance");
assert(css.includes('@media(max-width:375px)')&&css.includes('max-width:470px'),"Mobile viewport contract");
assert(css.includes('prefers-reduced-motion'),"Accessibility motion control");
assert(!/body\.map-first-v3|body\.mf-v5|#urgent-send|\.premium-route-canvas/.test(css),
  "No legacy CSS or prior layout inheritance allowed");
for(const source of [html,js,model,ui]){
  assert(!/\bfetch\s*\(|\bXMLHttpRequest\b|\.geolocation\b|\bsupabase\b|\brpc\s*\(/i.test(source),
    "Static layout must not query providers or send bookings");
  assert(!/sessionStorage|document\.cookie|create_reservation|confirm-booking|serviceWorker\.register/.test(source),
    "No persistent personal state, booking, PWA registration, or production API");
}
assert(!/<form\b/i.test(html),"Static V6 is a visual approval artifact, not a live form");
assert(!/<script[^>]*src=["']https?:/i.test(html),"No third-party JS in visual study");
assert(!/url\(\s*https?:/i.test(css),"No external image/font or fake live map");
assert(html.includes('quicktrip-v6.css?v=1')&&html.includes('quicktrip-v6.js?v=1'));
assert(css.includes("body.theme-light")&&css.includes("body.theme-dark"));
console.log("PASS QuickTrip V6 lab: four screens, persistent theme, responsive, no booking APIs");
