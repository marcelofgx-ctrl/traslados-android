"use strict";
/* Approved photo, no duplicate business engine. Strict source-level contract. */
const fs=require("node:fs"),assert=require("node:assert/strict"),path=require("node:path");
const root=path.resolve(__dirname,".."),read=p=>fs.readFileSync(path.join(root,p),"utf8");
const shell=read("web-pasajero/quicktrip-clean.js"),css=read("web-pasajero/quicktrip-p13-approved.css");
const html=read("web-pasajero/index.html"),sw=read("web-pasajero/sw.js");
const app=read("web-pasajero/app.js"),drift=read("web-pasajero/pickup-origin-drift.js");
let n=0;const check=(name,v)=>{assert.ok(v,name);n++;console.log("PASS PHOTO13",name)};
check("Approved CSS loaded LAST, after photo1 and no new operational JS",
 html.indexOf("quicktrip-p1-photo1.css")<html.indexOf("quicktrip-p13-approved.css")&&
 !html.includes("quicktrip-p13-approved.js"));
check("Fresh cache and shell script forced into PWA",
 sw.includes('traslados-pwa-quicktrip-p14-recovery-20261030')&&
 sw.includes("./quicktrip-p13-approved.css")&&
 sw.includes("./quicktrip-p14-estimate-recovery.css")&&
 html.includes("quicktrip-clean.js?v=p14-recovery-20261030"));
check("Same original origin/destination markers, without changing geocoding controls",
 css.includes("Retain EXACT P1 green/coral pointed location markers")&&
 css.includes("#origin-picker:before")&&css.includes("#destination-picker:before")&&
 !css.includes("lat:")&&!css.includes("lng:"));
check("Original route state and pickup-distance/ETA source elements kept",
 html.includes('id="origin-picker"')&&html.includes('id="destination-picker"')&&
 html.includes('id="pickup-distance"')&&html.includes('id="pickup-minutes"')&&
 html.includes('id="pickup-presence-status"'));
check("No cloned source-bound money, route kilometer or duration fields",
 html.includes("id=\"urgent-route-recap\"")&&
 html.includes('data-final-route-distance')&&html.includes('data-final-route-duration')&&
 html.includes('data-final-route-fare')&&
 shell.includes('const recap=byId("urgent-route-recap")'));
check("Same native request button with unchanged user-side booking action",
 html.match(/id="urgent-send"/g)?.length===1&&
 shell.includes('const urgent=byId("urgent-actions")')&&
 css.includes("#urgent-send"));
check("Native stop button, fields and login restored on scheduled navigation",
 shell.includes('class="qtc-stops-slot"')&&
 shell.includes('stopBodyAnchorOriginal.after(stopBody)')&&
 shell.includes('stopLoginAnchorOriginal.after(stopLogin)')&&
 shell.includes('stopsSlot.append(stops)')&&
 shell.includes('stopsSlot.append(stopBody)'));
check("Pickup GPS drift remains optional and same two real actions",
 drift.includes('action("Actualizar origen"')&&
 drift.includes('action("Mantener origen"')&&
 shell.includes("destinationAnchor.after(drift)")&&
 shell.includes("origin.after(drift)"));
check("Original availability, pickup ETA and verified road motor preserved",
 app.includes("publicDriverAvailability(force)")&&
 app.includes("window.TrasladosETA.pickup({")&&
 app.includes("window.TrasladosRoutes.verified(current)"));
check("No ad hoc GPS network, pricing or fake coordinates in P1.3 presentation",
 !/\bfetch\s*\(|\brpc\s*\(|\bsetInterval\s*\(|Math\.random\s*\(/.test(css)&&
 !/quote_final_total\s*[:=]\s*\d+/.test(shell));
check("No CSS hiding actual status and pickup metrics",
 !/#pickup-distance[^}]*display\s*:\s*none/i.test(css)&&
 !/#pickup-minutes[^}]*display\s*:\s*none/i.test(css));
check("Nonfloating request button, reserved footer clearance and accessible expanded fields",
 css.includes("position:static!important")&&
 css.includes("padding:9px 12px calc(86px")&&
 css.includes(".qtc-stops-slot #stop-wrap:not([hidden])")&&
 css.includes(".qtc-stops-slot #login-for-stops:not([hidden])"));
check("Auto-open on accepted, no pre-acceptance swipe map",
 app.includes("autoFollowAccepted(rows,oldTripRows)")&&
 !read("web-pasajero/quicktrip-active-ride.js").includes("qt-live-knob"));
fs.mkdirSync(path.join(root,"artifacts/quicktrip-p13-approved"),{recursive:true});
fs.writeFileSync(path.join(root,"artifacts/quicktrip-p13-approved/design-contract.json"),
 JSON.stringify({suite:"Approved Quick Trip P1.3 single-screen",checks:n,passed:n,
 changedEngines:false,liveAvailabilityVerified:false,livePickupEtaVerified:false,
 androidPhysicalTestRequired:true,reservationsCreated:false},null,2)+"\n");
console.log("QUICK TRIP PHOTO P1.3",n+"/"+n,"PASS");
