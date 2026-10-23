"use strict";
// Visual-only regression for the broken Samsung passenger screenshots.
// No live requests, bookings, GPS or external network calls.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const read=n=>fs.readFileSync(path.join(root,n),"utf8");
const html=read("index.html"),script=read("quicktrip-clean.js"),
  css=read("styles.css"),skin=read("quicktrip-clean.css"),sw=read("sw.js");
assert(html.includes('styles.css?v=83'),"Load coherent updated base CSS");
assert(html.includes('quicktrip-clean.css?v=7'),"Load updated clean skin");
assert(html.includes('quicktrip-clean.js?v=6'),"Load updated clean UI script");
assert(html.includes('app.js?v=83'),"Do not change the working booking engine");
assert(sw.includes('traslados-pwa-v82-quicktrip-warm-notice-v83-20261023'),
  "Invalidate stale UI assets without changing the functional engine");
assert(sw.includes('"./quicktrip-clean.css"')&&sw.includes('"./quicktrip-clean.js"'));
assert(script.includes("function skinReady()"),"Guard against partial clean UI");
assert(script.includes("if(!skinReady())"),"Fail closed to original form if skin missing");
assert(script.includes('link.sheet.cssRules.length>0'),"Validate loaded stylesheet");
assert(script.includes('width="168" height="46"'),"Intrinsic Quick Trip logo cannot grow to 720px");
assert(script.includes('back.setAttribute("aria-label","Volver al inicio")'));
assert(script.includes('back.textContent=""'),"Avoid stacked text or doubled arrow");
assert(script.includes('Boolean(active&&doc.body.classList.contains("journey-mode-quick"))'),
  "Never hide functional booking controls when the quick view is not active");
assert(css.includes('#view-viaje .qtc-app img.qtc-logo'),"Base skin must cap oversized SVG");
assert(css.includes('#view-viaje .qtc-app .qtc-history-button'),"Base skin must style Historial");
assert(css.includes('#view-viaje .qtc-app .qtc-back-slot .journey-back'),"Bound back button");
assert(skin.includes('body.qt-clean .qtc-app .qtc-history-button'),"Premium skin still owns final style");
assert(!/\bfetch\s*\(|XMLHttpRequest|\.rpc\s*\(/.test(script),
  "Visual code must not duplicate network requests or booking RPCs");
console.log("PASS Quick Trip v83: coherent cache, bounded logo, back, Historial, fail-closed skin");
