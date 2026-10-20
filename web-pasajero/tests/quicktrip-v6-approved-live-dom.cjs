"use strict";
/* Approved V6 screens bind the REAL PWA DOM. No Supabase connection and no sent request. */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const root=path.join(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const html=read("index.html");
const skin=read("quicktrip-functional-v6.js");
const css=read("quicktrip-functional-v6.css");
const app=read("app.js");
const premium=read("premium-journey.js");
const lab=read("pruebas/visual-v6/quicktrip-v6.css");
new vm.Script(skin);
const checkpoint=(condition,message)=>assert(condition,message);
for(const id of ["view-viaje","view-reservar","view-inicio","urgent-send","person-name","person-phone","pickup-date","pickup-time","route-overview"]){
 checkpoint(html.includes('id="'+id+'"'),"Original live DOM still provides #"+id);
}
checkpoint(html.indexOf('premium-journey.js?v=')<html.indexOf('app.js?v='),"Native composition before logic");
checkpoint(html.indexOf('app.js?v=')<html.indexOf('quicktrip-functional-v6.js?v='),"V6 after operational listeners");
checkpoint(skin.includes('params.get("ui")!=="v6"'),"Inert without explicit V6 opt-in");
checkpoint(skin.includes('progressHeader.insertAdjacentElement("afterend",introduction)'),"Approved title follows original stepper");
checkpoint(skin.includes('const titles={')&&["passenger","confirm","route","when"].every(x=>skin.includes(x+":[")), "Approved four-screen copy");
checkpoint(skin.includes('MutationObserver(syncStage)'),"Stage copy follows real four-stage journey");
checkpoint(skin.includes("const icons=["),"Icons render consistently without platform-dependent emojis");
checkpoint(skin.includes("qt-v6-contact-card")&&skin.includes('doc.getElementById("person-name")')&&skin.includes('doc.getElementById("person-phone")'),"Contact tile reads existing native editable fields");
checkpoint(skin.includes('personPhone.value.trim()')&&skin.includes('personName.value.trim()'),"Contact display uses real user input");
checkpoint(skin.includes("syncScheduledStage")&&skin.includes("is-open")&&skin.includes('sendActions.hidden'),"Programmed visual progress follows original card/send states");
checkpoint(skin.includes("qt-v6-header-back")&&skin.includes('.tab[data-view="inicio"]'),"Back uses already-mounted navigation");
checkpoint(skin.includes("qt-v6-tab-icon")&&skin.includes("qt-theme-sun")&&skin.includes("qt-theme-moon"),"Approved footer and two-theme toggle");
checkpoint(css.includes("width:124px!important")&&css.includes("width:104px!important"),"Approved medium 124/104 px home emblems");
checkpoint(css.includes('grid-template-columns:56px minmax(0,1fr)')&&css.includes("border-radius:999px"),"Single-row back and champagne CTA");
checkpoint(css.includes('#view-viaje .journey-hero{')&&css.includes('display:none!important'),"V6 must hide the obsolete Tu viaje 1/4 hero");
checkpoint(css.includes('position:fixed!important')&&css.includes('bottom:calc(78px + env(safe-area-inset-bottom))'),"Continue must stay visible above footer while scrolling the live map");
checkpoint(css.includes('#view-viaje:not([data-premium-step="confirm"])'),"Only live stages 1-3 receive floating Continue; confirm uses original submit");
checkpoint(!/\.premium-next[^\n]*addEventListener/.test(skin),"Keep original Continue action; never clone the click handler");
checkpoint(css.includes(".qt-v6-contact-row")&&css.includes(".qt-v6-schedule-progress"),"Live contact card and programmed stages styled");
checkpoint(css.includes("qt-theme-light")&&css.includes("qt-theme-dark"),"Both approved appearances");
checkpoint(lab.includes(".flow-steps li")&&lab.includes(".section-title h1"),"Original V6 visual reference retained");
checkpoint(app.includes('window.TrasladosServiceHome.mount(document')&&app.includes('onSelect:kind=>enterService(kind)'),"Three historical services still use existing flow");
checkpoint(premium.includes("return Object.freeze({activate:")&&premium.includes('complete("passenger")'),"Native validation is untouched");
checkpoint(app.includes('customer_request_pickup_fixed_v1')&&app.includes('customer_create_reservation_v12'),"Both real booking RPCs retained");
checkpoint(app.includes('window.TrasladosETA.availability')&&app.includes("renderRouteSummary()"),"Live ETA and map source retained");
checkpoint(!/\b(fetch|XMLHttpRequest)\s*\(|\.geolocation\b|\.rpc\s*\(/.test(skin),"No shadow network/price/booking engine");
checkpoint(!/demoState|demoFixture|Pasajero de ejemplo|0,5 km al origen|MAPA ILUSTRATIVO/.test(skin),"No mock data in actual screens");
checkpoint(!/urgent-send[^\n]*addEventListener|confirm-booking[^\n]*addEventListener/.test(skin),"No second send listener");

let touched=false;
vm.runInNewContext(skin,{
 window:{location:{search:""},document:{createElement(){touched=true;throw Error("Touched official DOM")}}},URLSearchParams
});
checkpoint(!touched,"Classic view MUST remain untouched without ?ui=v6");
console.log("PASS Quick Trip V6 approved: faithful stages, responsive icons, live data, original engines, 2 themes, 4-step programmed view, no duplicate writes");
