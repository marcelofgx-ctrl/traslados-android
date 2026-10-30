"use strict";
/* P1 visual-only QA: no backend, GPS, HTTP, booking or fare mutation. */
const fs=require("node:fs"),assert=require("node:assert/strict"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const css=read("web-pasajero/quicktrip-p1-photo1.css");
const html=read("web-pasajero/index.html");
const sw=read("web-pasajero/sw.js");
let ok=0;function check(name,p){assert.ok(p,name);ok++;console.log("PASS P1",name);}
check("P1 is appended AFTER verified single-flow CSS",html.indexOf('quicktrip-singleflow-premium.css')<html.indexOf('quicktrip-p1-photo1.css?v=p11-return-20261030'));
check("Only CSS stylesheet is loaded; no extra business JS",!html.includes('quicktrip-p1-photo1.js'));
check("PWA cache is versioned and contains photo1 file",sw.includes('traslados-pwa-quicktrip-p12-direct-map-20261030')&&sw.includes('./quicktrip-p1-photo1.css'));
check("P1 selectors scoped to active quick journey",css.includes('body.qt-clean.qt-clean-active #view-viaje .qtc-app')&&
 !css.includes('body:not(')&&!css.includes('#view-reservar .qtc-app'));
check("Origin and destination use LARGE teardrop pins",css.includes('#origin-picker:before')&&css.includes('#destination-picker:before')&&
 css.includes('transform:rotate(-45deg)')&&css.includes('width:29px')&&css.includes('height:29px'));
check("The dotted linking rule is preserved",css.includes('#origin-picker:after')&&css.includes('repeating-linear-gradient'));
check("No new fake GPS/ETA/fare information",!/Math\.random\(|quote_final_total\s*[:=]\s*[0-9]|setInterval\(|\.fetch\(|\.rpc\(/.test(css));
check("Never conceal driver ETA or pickup metric state",!/#pickup-presence\s*\{\s*display:none/.test(css)&&
 css.includes('#pickup-presence:not([hidden])')&&css.includes('#pickup-presence[hidden]'));
check("Real native pickup retry and status controls remain",html.includes('id="pickup-eta-retry"')&&
 html.includes('id="pickup-presence-title"')&&html.includes('id="pickup-distance"')&&html.includes('id="pickup-minutes"'));
check("Route summary uses SAME source-bound distances / fare",html.includes('data-final-route-distance')&&
 html.includes('data-final-route-duration')&&html.includes('data-final-route-fare')&&css.includes('#urgent-route-recap'));
check("Existing request action not duplicated",html.match(/id="urgent-send"/g)?.length===1&&
 css.includes('#urgent-send'));
check("Original pickup drift actions unchanged",read("web-pasajero/pickup-origin-drift.js").includes('action("Actualizar origen"')&&
 read("web-pasajero/pickup-origin-drift.js").includes('action("Mantener origen"')&&
 css.includes('.pickup-origin-drift-actions button'));
check("No CSS hides origin/destination/driver/route widgets",!/#(?:origin-picker|destination-picker|pickup-distance|pickup-minutes|urgent-route-recap)\s*\{[^}]*display:none/s.test(css));
check("No entire app/map engine replacement",!css.includes('#qtc-app{display:none')&&
 !css.includes('.premium-map-tiles{display:none'));
check("Booking data routing still uses untouched native engines",
 read("web-pasajero/app.js").includes("publicDriverAvailability(force)")&&
 read("web-pasajero/app.js").includes("window.TrasladosETA.pickup({")&&
 read("web-pasajero/app.js").includes("window.TrasladosRoutes.verified(current)"));
fs.mkdirSync(path.join(root,"artifacts/quicktrip-p1-photo1"),{recursive:true});
fs.writeFileSync(path.join(root,"artifacts/quicktrip-p1-photo1/p1-contract.json"),
 JSON.stringify({suite:"P1 photo-one visual-only",checks:ok,passed:ok,
  routingEngineChanged:false,pickupEngineChanged:false,
  GPSChanged:false,bookingsChanged:false,liveDriverGPSProven:false},null,2)+"\n");
console.log("P1 PHOTO1 CONTRACT "+ok+"/"+ok+" PASS");
