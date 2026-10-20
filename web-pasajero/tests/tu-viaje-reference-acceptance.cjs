"use strict";
/* Frozen reference contract acceptance; no requests sent to live Supabase. */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const root=path.resolve(__dirname,"..");
const file=p=>fs.readFileSync(path.resolve(root,p),"utf8");
const html=file("index.html"),presentation=file("tu-viaje-reference.js");
const css=file("tu-viaje-reference.css"),route=file("premium-route.js");
const original=file("premium-journey.js"),home=file("service-home.js");
const refer=file("../docs/CONTRATO_VISUAL_TU_VIAJE_4_PANTALLAS_20261019.md");
new vm.Script(presentation);new vm.Script(route);
assert(html.includes('src="./tu-viaje-reference.js?v=2" defer'),"approved script is loaded");
assert(html.indexOf('app.js?v=82')<html.indexOf('tu-viaje-reference.js?v=2'),"original engine mounts first");
assert(html.indexOf('quicktrip-functional-v6.js?v=3')<html.indexOf('tu-viaje-reference.js?v=2'),"approved design overrides experimental V6");
assert(presentation.includes('params')===false || presentation.includes('searchParams'),"fully scoped to opt-in");
assert(presentation.includes('get("ui")!=="v6"'),"do not change the default PWA");
assert(!presentation.includes("stopImmediatePropagation"),"preview protection must not swallow real booking click");
assert(!presentation.includes("if(!doc.querySelector('meta[name=\"traslados-preview\"]'))return;"),"approved code is not preview-only");
for(const asset of ["map-first-review.css","map-first-v3.css","map-first-v4-confirm.css","map-first-visual-v5.css","map-first-v5-2.css"]){
 assert(presentation.includes(asset),"frozen visual reference style missing: "+asset);
 assert(fs.existsSync(path.join(root,"pruebas/map-first",asset)),"reference style not committed "+asset);
}
for(const label of ["TRASLADOS · TU PRÓXIMO VIAJE","Todo listo para solicitar","SIN ENVIAR",
"Comprobá los datos y continuá","TU RECORRIDO","Editar recorrido",
"Indicaciones, email y preferencias (opcional)"]){
 assert((presentation+original).includes(label),"approved visible label missing in original presentation: "+label);
}
assert(presentation.includes('header.innerHTML=')&&presentation.includes("<strong>Tu viaje</strong>"),"compact original Tu viaje header");
assert(presentation.includes('route.prepend(map)')===false,"map must never be reparented ABOVE inputs");
assert(presentation.includes('destination.after(map)'),"origin and destination BEFORE the SAME real cartography");
assert(presentation.includes("options.append(comment,extras)"),"original passenger field nodes retained");
assert(presentation.includes("ticket.append(masthead,hero,recap,tear,route,meta,action)"),"single live confirmation ticket");
assert(presentation.includes('ticket.prepend(spotlight)')&&presentation.includes('spotlight.append(head,hero,recap)'),
 "visual V5 original spotlight and request summary");
assert(presentation.includes('doc.body.classList.toggle("qt-functional-v6",!active)'),"do not mix experimental four-circle design with frozen steps");
assert(css.includes(".journey-hero,")&&css.includes("display:none!important"),"remove old oversized header");
assert(css.includes(".premium-brand::after")&&css.includes(".premium-progress{display:none!important"),"only one 1/4-4/4 progress badge");
assert(css.includes(".premium-position-summary,")&&css.includes("display:none!important"),"no redundant giant GPS card on top");
assert(css.includes(".premium-route-summary")&&css.includes(".premium-map-tiles"),"full map, fare and color");
assert(css.includes(".premium-actions{")&&css.includes("position:relative!important"),"CTA is in normal document flow, not overlaying controls");
assert(css.includes(".qt-v6-tab-icon")&&css.includes("display:none!important"),"single bottom nav icon");
assert(!/fetch\s*\(|XMLHttpRequest|\.rpc\s*\(/.test(presentation),"presentation MUST NOT fetch/write or create duplicate booking engines");
assert(route.includes("const approved=new URLSearchParams(root.location.search)")&&route.includes('get("ui")==="v6"'),
 "verified fare summary is isolated to approved opt-in");
assert(route.includes("summary.dataset.verified")&&route.includes("Number.isFinite(m.referenceFareUyu)"),
 "no fake price, fare only from existing verified route metrics");
assert(route.includes('kmValue.textContent=m.distanceKm.toLocaleString')&&route.includes('durationValue.textContent=Math.round(m.durationMin)'),
 "distance and time are live numbers");
assert(original.includes('next.addEventListener("click",forward)')&&original.includes('back.addEventListener("click"'),
 "preserve native four-step navigation");
assert(original.includes('requestHost.querySelector("#urgent-route-recap')&&home.includes("onSelect"),
 "preserve native confirmation data and three service cards");
assert(refer.includes("CONTRATO VISUAL BLOQUEADO")&&refer.includes("Checklist de aceptación"),"document is canonical source");

// Runtime regression: at first load the REAL route/map is mounted in
// #reserve-field-host, NOT under #view-viaje. Earlier code silently returned,
// so no approved headers, no CSS overrides and duplicate footer icons.
{
 const boot=presentation.split("/* Frozen preview composition")[0];
 const classes=new Set(["qt-functional-v6","journey-mode-quick"]);
 const header={innerHTML:""};
 const view={
  dataset:{premiumStep:"route"},
  classList:{contains:x=>x==="active"},
  querySelector:q=>q===".premium-brand"?header:null
 };
 let removed=0,observers=0;
 const doc={
  readyState:"complete",
  head:{links:[],appendChild(node){this.links.push(node)}},
  body:{
   dataset:{},
   classList:{
    add(...xs){xs.forEach(x=>classes.add(x))},
    toggle(x,on){if(on)classes.add(x);else classes.delete(x)}
   }
  },
  getElementById:x=>x==="view-viaje"?view:null,
  // The route exists, but OUTSIDE the active journey at initial mount.
  querySelector:q=>q===".premium-route-canvas"?{id:"real-shared-map"}:null,
  querySelectorAll:q=>q===".qt-v6-tab-icon"?[{remove(){removed++}},{remove(){removed++}},{remove(){removed++}}]:[],
  createElement:()=>({}),
 };
 const win={location:{search:"?ui=v6"}};
 const context={window:win,doc,URLSearchParams,
  MutationObserver:class{constructor(){observers++}observe(){}disconnect(){}}
 };
 win.document=doc;
 vm.runInNewContext(boot,context);
 assert(classes.has("tu-viaje-ref"),"approved skin must mount even when the real map is in the reserve host");
 assert(!classes.has("qt-functional-v6"),"old experimental rail must deactivate while Tu viaje is active");
 assert(header.innerHTML.includes("<strong>Tu viaje</strong>"),"compact approved header must replace Quick Trip logo");
 assert.equal(doc.body.dataset.tuViajeReference,"approved-1-4","skin must set the authoritative acceptance flag");
 assert.equal(removed,3,"V6-injected extra tab icons must be removed exactly once");
 assert.equal(doc.head.links.length,6,"all archived reference styles must be loaded");
 assert.equal(observers,1,"stage layout must continue to follow native step state");
}

console.log("PASS: approved exact Tu viaje reference 1/4–4/4, real price/maps/forms, original submit and no duplicate request");
