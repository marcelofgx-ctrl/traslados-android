"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const base=path.join(__dirname,"..");
const read=name=>fs.readFileSync(path.join(base,name),"utf8");
const index=read("index.html");
const css=read("map-first-review.css");
const toggle=read("map-first-review.js");
const route=read("premium-route.js");
assert(index.includes('href="./map-first-review.css?v=1"'));
assert(index.includes('src="./map-first-review.js?v=1"'));
assert(toggle.includes('searchParams.get("mapfirst")==="1"'));
assert(toggle.includes('body.classList.add("map-first-review")'));
assert(!/fetch\s*\(|XMLHttpRequest|supabase|booking[_-]rpc|localStorage|geolocation/i.test(toggle));
assert(css.includes('body.map-first-review.journey-mode-quick #view-viaje[data-premium-step="route"]'));
assert(!css.includes('#view-viajes')&&!css.includes('#view-reservar'));
assert(css.includes(".premium-route-canvas")&&css.includes(".premium-route-summary"));
assert(css.includes(".premium-route-fare")&&css.includes(".premium-route-trip-metrics"));
assert(css.includes(".premium-live-presence")&&css.includes("#origin-picker"));
assert(css.includes("order:2")&&css.includes(".premium-route-stops"));
assert(route.includes('root.innerHeight')&&route.includes('mapFirstHeight'));
assert(route.includes("root.TrasladosPremiumMap.render")&&route.includes("root.TrasladosPremiumMap.overview"),
  "Retain existing verified-road renderer and reference overview");
assert(route.includes("root.TrasladosPremiumMap.render")&&!route.includes("roadEngine.estimate("),
  "Use the existing geometry renderer without invoking another route-estimate request");
assert(!css.includes("position:fixed"),"Do not overlay the live booking submit controls");
console.log("PASS MAP-FIRST-REAL-001: isolated opt-in; live existing map/fare/pickup; no booking or GPS changes");

// V2 must change the page hierarchy, not merely recolor the existing form.
assert(css.includes("MAP-FIRST-V2-SCREEN-REORDER"));
assert(css.includes("#journey-field-host"));
assert(css.includes('display:none!important;\n}'));
assert(css.includes('.premium-live-presence{\n  order:2'));
assert(css.includes('.premium-actions{\n  order:3'));
assert(css.includes('grid-template-columns:repeat(2,minmax(0,1fr))'));
console.log("PASS MAP-FIRST-V2: map before availability, compact nav, two-column time choices");
