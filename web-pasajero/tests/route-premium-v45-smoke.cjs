"use strict";
// Paso 1 PWA v45: comprobaciones estructurales de diseño, accesibilidad
// y compatibilidad. Nunca genera reservas ni accede a GPS/red.
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const path=require("node:path");
const root=path.join(__dirname,"..");
const read=name=>readFileSync(path.join(root,name),"utf8");
const html=read("index.html"),js=read("app.js"),css=read("styles.css"),sw=read("sw.js");
for(const key of ["origin-picker","destination-picker","route-overview","stop-wrap","login-for-stops"]){
  assert(html.includes('id="'+key+'"'),"Preserve route flow "+key);
}
assert(html.includes('id="pickup-presence"'),"Preserve live pickup");
assert(html.includes('id="booking-final"'),"Preserve booking submit flow");
assert(html.includes('src="./app.js?v=75"')&&html.includes('href="./styles.css?v=75"'),
  "Both asset versions must be coherent");
assert(sw.includes('traslados-cliente-pwa-v75'),"Refresh SW for embedded client");
assert(html.includes("Elegí el recorrido, revisá la estimación"),"Booking intro should be brief and contextual");
assert(/class="wayline" aria-hidden="true"/.test(html),"Route connector is decorative");
assert(js.includes('box.dataset.locationKind=name'),"Style origin vs destination without hardcoding location data");
assert(js.includes('name==="origin"?"ORIGEN":name==="destination"?"DESTINO"'),
  "Accessible route labels, decorative shapes in CSS only");
assert(js.includes('input.enterKeyHint="search"'),"Show mobile search keyboard action");
assert(js.includes('results.id="location-results-"+key')&&js.includes('input.setAttribute("aria-controls",results.id)'),
  "Programmatic relationship for suggestions");
assert(js.includes('"tiny-btn location-use-current"'),"GPS action remains distinct");
assert(js.includes('box.classList.toggle("is-selected",Boolean(selected))'),"Chosen location gets visual state");
assert(js.includes("window.TrasladosAddress.findAcross(q,dept)")&&js.includes("window.TrasladosGeo.find(q,null)"),
  "Keep independent address/POI engines");
assert(js.includes("window.TrasladosAddress.resolve(p,q,p.departamento||dept)"),"Keep geocoding verification");
assert(js.includes("getCurrentPosition(async p=>"),"Keep opt-in GPS");
assert(js.includes("renderRouteSummary()")&&js.includes("window.TrasladosRoutes"),"Keep road estimates");
assert(js.includes("window.TrasladosBookingContracts.registeredReservation")&&js.includes("window.TrasladosBookingContracts.guestReservation"),
  "Keep real booking RPCs");
assert(css.includes('grid-template-columns:minmax(0,1fr) 47px 47px'),
  "Route field must be visibly larger than search");
assert(css.includes('grid-template-columns:minmax(0,1fr) 44px 44px'),
  "Preserve narrow mobile layout");
assert(css.includes('grid-template-columns:minmax(0,1fr) 44px'),
  "Preserve 320px viewport");
assert(css.includes('.location-widget[data-location-kind="destination"] .location-title::before'),
  "A/B need distinct understated markers");
assert(css.includes('.location-search-action::before'),"Search icon should not rely on external asset");
assert(css.includes('.location-use-current'),"Keep GPS affordance compact");
assert(css.includes('.wayline span::before')&&css.includes('.wayline span::after'),
  "Keep a fine route connector");
assert(css.includes(".results button.result::after"),"Search suggestions need a selection affordance");
assert(css.includes("prefers-reduced-motion:reduce"),"Respect reduced motion");
assert(css.includes("repeating-linear-gradient"),"Preserve premium texture");
assert(!html.includes("CONDUCTOR DISPONIBLE SIEMPRE"),"Never fabricate availability");
console.log("PASS v46 route premium: layout, accessibility, GPS, engines, responsive widths, no backend regression");
