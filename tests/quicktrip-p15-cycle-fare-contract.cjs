"use strict";
/* P1.5 offline contract: safe UI-only interval visibility and native fare. */
const fs=require("node:fs"),assert=require("node:assert/strict"),path=require("node:path");
const root=path.resolve(__dirname,".."),read=p=>fs.readFileSync(path.join(root,p),"utf8");
const app=read("web-pasajero/app.js"),ui=read("web-pasajero/pickup-cycle-ui.js");
const css=read("web-pasajero/quicktrip-p15-eta-cycle-fare.css");
const html=read("web-pasajero/index.html"),sw=read("web-pasajero/sw.js");
const check=(name,ok)=>{assert.ok(ok,name);console.log("PASS P15",name)};
check("Native ETA/availability control preserved",app.includes("window.TrasladosETA.pickup({")&&
 app.includes("publicDriverAvailability(force)")&&app.includes("etaRetryNotBefore"));
check("No invented or duplicated pickup engine",!/(?:\bfetch\s*\(|\brpc\s*\(|geolocation|watchPosition|getCurrentPosition|refreshPickupPresence\s*\()/.test(ui)&&
 !/Math\.random\(/.test(ui));
check("Cycle UI only: visual second clock, actual request interval untouched",
 ui.includes("win.setInterval(()=>")&&ui.includes("},1000)")&&
 app.includes("window.TrasladosPickupCadence.intervalMs(driver.motionHint)"));
check("Error reason safely allowlisted, no GPS/session data passed to the DOM",
 app.includes('host.dataset.etaFailureCode=failedEta?lastEtaFailureCode:""')&&
 app.includes("host.dataset.etaRetryEligibleAt=failedEta?String(Math.max(")&&
 app.includes('lastEtaFailureCode="network_error"')&&
 !ui.includes("originLat")&&!ui.includes("originLng")&&!ui.includes("session.token"));
check("A retry becomes eligible; no promise an immediate RPC was sent",
 ui.includes("Nuevo intento permitido en ")&&ui.includes("Esperando el siguiente control automático."));
check("Visual indicator hidden on ready, blocked, scheduled and owned",
 ui.includes('["checking","routing","waiting","issue"].includes(phase)')&&
 ui.includes('classList.contains("journey-mode-quick")'));
check("Original fare DOM stays in view within the same verified recap",
 css.includes("grid-column:1/-1!important")&&css.includes("overflow-wrap:anywhere!important")&&
 !/\b(?:124|752|18\.8)\b/.test(ui+css)&&
 html.includes('data-final-route-fare'));
check("Reduced motion accessibility and hidden-state CSS",
 css.includes("prefers-reduced-motion:reduce")&&css.includes("#qtc-p15-cycle[hidden]"));
check("P1.5 source load order, cache version and asset lists",
 html.indexOf("quicktrip-p13-approved.css")<html.indexOf("quicktrip-p15-eta-cycle-fare.css")&&
 html.indexOf("app.js?v=p15-cycle-20261031")<html.indexOf("pickup-cycle-ui.js?v=p15-20261031")&&
 sw.includes("traslados-pwa-quicktrip-p15-eta-cycle-20261031")&&
 sw.includes("./pickup-cycle-ui.js")&&sw.includes("./quicktrip-p15-eta-cycle-fare.css"));
fs.mkdirSync(path.join(root,"artifacts/quicktrip-p15"),{recursive:true});
fs.writeFileSync(path.join(root,"artifacts/quicktrip-p15/contract.json"),
 JSON.stringify({suite:"P1.5",synthetic:true,backendModified:false,
 driverGpsVerified:false,reservationsCreated:false},null,2)+"\n");
