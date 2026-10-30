"use strict";
/* P1.2 QA — exclusive journey flow. No backend, GPS, fare or route changes. */
const fs=require("node:fs"),assert=require("node:assert/strict"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const get=p=>fs.readFileSync(path.join(root,p),"utf8");
const card=get("web-pasajero/quicktrip-active-ride.js");
const css=get("web-pasajero/quicktrip-n1n2.css");
const app=get("web-pasajero/app.js");
const html=get("web-pasajero/index.html");
const sw=get("web-pasajero/sw.js");
const selector=require("../web-pasajero/quicktrip-active-ride.js");
let checks=0;
const ok=(label,condition)=>{assert.ok(condition,label);checks++;console.log("PASS P1.2",label)};
ok("No manual map rail / swipe knob / tap fallback in passenger active summary",
 !card.includes("qt-live-rail")&&!card.includes("qt-live-knob")&&!card.includes("qt-live-tap")&&
 !card.includes("Deslizá para ver el mapa")&&!card.includes("Ver mapa sin deslizar"));
ok("No obsolete visual swipe selectors or pointer capture",
 !css.includes(".qt-live-rail{")&&!css.includes(".qt-live-knob{")&&
 !card.includes("setPointerCapture")&&!card.includes("pointermove"));
ok("Summary still shows verified status, origin, destination and source fare",
 card.includes('qt-live-status')&&card.includes('r.origin_text')&&
 card.includes('r.destination_text')&&card.includes('formatMoney(r.quote_final_total)'));
ok("No second map introduced by summary and native booking fields retained",
 !card.includes("document.createElement(\"canvas\")")&&
 app.includes("window.TrasladosETA.pickup({")&&
 app.includes("window.TrasladosRoutes.verified(current)")&&
 app.includes('$("urgent-send")'));
ok("Only server-confirmed immediate acceptance can auto-enter fullscreen",
 app.includes("function autoFollowAccepted(rows,previous)")&&
 app.includes("const autoOpened=autoFollowAccepted(rows,oldTripRows)")&&
 app.includes("if(openFullscreenTracking(candidate))return true;")&&
 app.includes("window.QuickTripActiveRide?.select?.([r]).count!==1"));
ok("Tu viaje resumes accepted map; Back returns Inicio without reopening",
 app.includes("const active=currentVerifiedRide()")&&
 app.includes("openPremiumTracking(active);")&&
 app.includes('window.addEventListener("quicktrip:tracking-closed"')&&
 app.includes('shiftView("inicio");syncActiveTripUi();'));
ok("Pending reservation does not offer phantom GPS map entry",
 !app.includes('"↗ Abrir mapa grande · dos recorridos"'));
const now=new Date().toISOString(),live={id:"qa-only",status:"ACEPTADA",created_at:now,pickup_mode:"now"};
ok("Pending, terminal and future bookings excluded",
 selector.select([{...live,status:"PENDIENTE"}]).count===0&&
 selector.select([{...live,status:"FINALIZADA"}]).count===0&&
 selector.select([{...live,pickup_mode:"schedule"}]).count===0);
ok("One accepted immediate selected, two not arbitrarily selected",
 selector.select([live]).ride===live&&
 selector.select([live,{...live,id:"second"}]).ride===null);
ok("PWA cache revisions include modified user-facing JS/CSS",
 html.includes("quicktrip-active-ride.js?v=p12-direct-map-20261030")&&
 html.includes("quicktrip-n1n2.css?v=p12-direct-map-20261030")&&
 html.includes("app.js?v=p12-direct-map-20261030")&&
 sw.includes('traslados-pwa-quicktrip-p13-photo-approved-20261030'));
fs.mkdirSync(path.join(root,"artifacts/quicktrip-p12-auto-map"),{recursive:true});
fs.writeFileSync(path.join(root,"artifacts/quicktrip-p12-auto-map/p12-static-contract.json"),
 JSON.stringify({suite:"P1.2 direct map without swipe",passed:checks,checks,
 requiresSamsung:true,realGpsTested:false,realBookingsCreated:false},null,2)+"\n");
console.log("P1.2 STATIC "+checks+"/"+checks+" PASS");
