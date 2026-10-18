"use strict";
/* PWA v63 continuity of v62 art regression: real source, no screenshots, no network, no bookings. */
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const path=require("node:path");
const read=n=>readFileSync(path.join(__dirname,"..",n),"utf8");
const html=read("index.html"),css=read("styles.css"),sw=read("sw.js"),home=read("service-home.js");
const region=html.split('<div class="service-list"')[1]?.split('<p id="service-coming-soon"')[0]||"";
assert(region,"Service chooser must be visible and independently scoped");
assert.equal((region.match(/class="service-emblem service-emblem-/g)||[]).length,3,"Three bespoke, not generic icons");
assert.equal((region.match(/<textPath href="#service-arc-/g)||[]).length,3,"Curved lettering on every emblem");
assert.equal((region.match(/class="service-card-name"/g)||[]).length,3);
for(const [tag,label] of [["journey","TU VIAJE"],["calendar","TU RESERVA"],["parcel","TU PAQUETE"]]){
  assert(region.includes("service-arc-"+tag),"Unique arc for "+tag);
  assert(region.includes(label),"Circular title missing for "+label);
}
assert(region.includes("Tu <em>viaje</em>")&&region.includes("Tu <em>reserva</em>")&&region.includes("Tu <em>paquete</em>"));
assert(region.includes('data-service="now"')&&region.includes('data-service="schedule"')&&region.includes('data-service="delivery"'));
assert(region.includes('aria-controls="service-coming-soon"')&&region.includes('aria-expanded="false"'));
assert(!/<script|<iframe|<image|<foreignObject/i.test(region),"Decorations must be local and inert");
assert.equal((region.match(/xmlns="http:\/\/www.w3.org\/2000\/svg"/g)||[]).length,3);
assert.equal((region.match(/aria-hidden="true"><svg /g)||[]).length,3,"Emblems are decorative, not duplicative labels");
assert(css.includes(".service-emblem")&&css.includes(".emblem-lettering")&&css.includes(".service-card-name em"));
assert(css.includes(".service-card:focus-visible")&&css.includes("outline:3px solid"));
assert(css.includes("@media(max-width:365px)")&&css.includes("grid-template-columns:69px minmax(0,1fr)"));
assert(css.includes("@media(prefers-reduced-motion:reduce)")&&css.includes(".service-card-icon"));
assert(!/fetch\(|createReservation|rpc\(|navigator\.geolocation/.test(home),"Homepage must never reach back-end/GPS");
assert(html.includes('styles.css?v=82')&&html.includes('app.js?v=82')&&html.includes('service-home.js?v=3'));
assert(sw.includes('traslados-pwa-v82')&&sw.includes("./service-home.js"));
assert(html.includes('id="view-reservar"')&&html.includes('id="trip-chat-sheet"'),"Operational screens remain intact");
console.log("PASS PWA v63: 3 bespoke orbital emblems, short card copy, disclosure, responsive accessible design and stable contracts");
