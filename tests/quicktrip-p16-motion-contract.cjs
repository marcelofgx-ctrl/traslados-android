"use strict";
/* QA P1.6: only CSS/native attributes; no route, GPS, ETA or booking changes. */
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const css=read("web-pasajero/quicktrip-p16-conductor-motion.css");
const html=read("web-pasajero/index.html"),sw=read("web-pasajero/sw.js");
const app=read("web-pasajero/app.js");
const check=(name,pass)=>{assert.ok(pass,name);console.log("PASS P16",name)};
check("CSS is loaded last without altering approved P1.3/P1.4 selectors",html.indexOf("quicktrip-p14-estimate-recovery.css")<html.indexOf("quicktrip-p16-conductor-motion.css")&&html.includes("quicktrip-p13-approved.css"));
check("CSS is cached for offline PWA without changing SW logic",sw.includes("./quicktrip-p16-conductor-motion.css")&&sw.includes('const CACHE="traslados-pwa-quicktrip-p14-recovery-20261030";'));
check("Only native live states animate (no fake ETA)",css.includes('[data-phase="checking"]')&&css.includes('[data-phase="routing"]')&&css.includes('[data-phase="waiting"]')&&!css.includes('[data-phase="ready"]')&&!css.includes('content:"Calculando'));
check("No new UI controls, CSS assets, external network, custom polling or hidden values",!/\b(?:fetch|rpc|setInterval|getCurrentPosition|geolocation|Math\.random)\b/.test(css)&&!/@import|url\(|#pickup-distance|#pickup-minutes|#urgent-route-recap|#urgent-send/.test(css));
check("Native source still owns all real availability/pickup ETA",app.includes("publicDriverAvailability(force)")&&app.includes("window.TrasladosETA.pickup({")&&app.includes("paintPickupCard({presence:"));
check("Elegant micro animations and reduced motion",css.includes("qt-p16-halo")&&css.includes("qt-p16-light-thread")&&css.includes("qt-p16-border-breathe")&&css.includes("prefers-reduced-motion:reduce"));
check("No DOM/hit-area modifications except scoped position/paint",!/(?:display|visibility|pointer-events):\s*(?:none|hidden)!important/.test(css)&&!css.includes("transform:translateY"));
console.log("PASS P1.6 source-only motion contract");
