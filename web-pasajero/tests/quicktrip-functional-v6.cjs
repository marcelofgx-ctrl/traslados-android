"use strict";
/* V6 first functional slice: true PWA controls and data, opt-in skin only. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const dir=path.join(__dirname,".."),read=p=>fs.readFileSync(path.join(dir,p),"utf8");
const html=read("index.html");
const skin=read("quicktrip-functional-v6.js");
const css=read("quicktrip-functional-v6.css");
const app=read("app.js"),home=read("service-home.js"),journey=read("premium-journey.js");
const demo=read("pruebas/visual-v6/home-v6.css");
new vm.Script(skin);
assert(html.includes('src="./quicktrip-functional-v6.js?v=1" defer'));
assert(html.indexOf('app.js?v=82')<html.indexOf('quicktrip-functional-v6.js?v=1'),
 "Mount existing PWA BEFORE the optional skin");
for(const engine of ["geo-search.js","address-engine.js","route-engine.js","eta-engine.js","booking-contracts.js","rpc-client.js","reservation-live.js","service-home.js","premium-journey.js","app.js"]){
 assert(html.includes('src="./'+engine),"Missing existing operational engine "+engine);
}
assert(html.includes('data-service="now"')&&html.includes('data-service="schedule"')&&html.includes('data-service="delivery"'));
assert(home.includes('select(kind)'),"Existing buttons must still use original callbacks");
assert(app.includes('onSelect:kind=>enterService(kind)'),"Real booking flow must stay mounted");
assert(app.includes('customer_request_pickup_fixed_v1')&&app.includes('customer_create_reservation_v12'),
 "Live RPC paths, both immediate and programmed, remain intact");
assert(app.includes('window.TrasladosETA.availability')&&app.includes('renderRouteSummary()'),
 "ETA and route calculations still owned by the existing engine");
assert(journey.includes('return Object.freeze({activate:'),"The four native stages are still powered by the original UI");
assert(skin.includes('params.get("ui")!=="v6"'),"V6 should never switch on by accident");
assert(skin.includes('quicktrip-v6-theme')&&skin.includes('qt-theme-light')&&skin.includes('qt-theme-dark'));
assert(skin.includes('Versión funcional en prueba. Las solicitudes enviadas llegan al conductor de verdad.'));
assert(!/demoFixture|MAPA ILUSTRATIVO|Pasajero de ejemplo|0,5 km al origen|\$ 124/.test(skin),
 "Never source fake trip details from old visual lab");
assert(!/\b(fetch|XMLHttpRequest)\s*\(|\.geolocation\b|\.rpc\s*\(/.test(skin),
 "Skin must not make direct network/geolocation/booking calls");
assert(!/urgent-send[^\n]*addEventListener|confirm-booking[^\n]*addEventListener/.test(skin),
 "Never wrap or duplicate reservation submission listeners");
assert(css.includes("body.qt-functional-v6.qt-theme-light")&&css.includes("body.qt-functional-v6.qt-theme-light"));
assert(css.includes("width:124px!important")&&css.includes("width:104px!important"));
assert(demo.includes("width:124px;height:124px")&&demo.includes("width:104px;height:104px"));
assert(!/url\(\s*https?:/.test(css),"No third-party assets or font loads in skin");

// Inert-by-default VM test: with no ?ui=v6, NO DOM may be accessed.
let touched=false;
const inert={location:{search:""},document:{createElement(){touched=true;throw Error("DOM touched");}}};
vm.runInNewContext(skin,{window:inert,URLSearchParams});
assert.equal(touched,false,"Normal official PWA unaffected");

// Opt-in must only load same-origin CSS; it must not start a second app.
const appended=[],events=new Map();
const doc={
 readyState:"loading",
 head:{appendChild(node){appended.push(node)}},
 createElement(tag){return {tag,rel:"",href:""}},
 addEventListener(name,handler){events.set(name,handler)},
 getElementById(){throw Error("Native DOM must not be changed before it mounts")}
};
const preview={location:{search:"?ui=v6"},document:doc};
vm.runInNewContext(skin,{window:preview,URLSearchParams});
assert.equal(appended.length,1);
assert.equal(appended[0].rel,"stylesheet");
assert.equal(appended[0].href,"./quicktrip-functional-v6.css?v=1");
assert(events.has("DOMContentLoaded"),"Skin must mount after native app.js");
 // defer executes with readyState=interactive; it must also wait for app.js
const interactiveEvents=new Map(),secondDoc={
 readyState:"interactive",head:{appendChild(){}},
 createElement(tag){return {tag,rel:"",href:""}},
 addEventListener(name,handler){interactiveEvents.set(name,handler)},
 getElementById(){throw Error("Premature V6 mount while native app has not initialized")}
};
vm.runInNewContext(skin,{window:{location:{search:"?ui=v6"},document:secondDoc},URLSearchParams});
assert(interactiveEvents.has("DOMContentLoaded"),"Interactive/defer phase must wait for native handlers");
console.log("PASS V6 functional: guarded opt-in, real engine reuse, themes, medium orbs, no mock data or duplicate writes");
