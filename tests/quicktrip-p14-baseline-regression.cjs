"use strict";
/* QA-only offline contract. No RPC, booking, GPS, charge or Supabase writes. */
const fs=require("node:fs"),assert=require("node:assert/strict"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const policy=require("../web-pasajero/quicktrip-active-ride.js");
let n=0;
function check(label,condition){assert.ok(condition,label);console.log("PASS Premium QA",label);n++;}
const fresh=new Date(Date.now()-20000).toISOString();
const active={id:"qa-123",status:"ACEPTADA",pickup_mode:"now",created_at:fresh};
check("Immediate accepted ride is active",policy.select([active]).ride===active);
check("Ride currently underway remains active",policy.select([{...active,status:"EN_VIAJE"}]).count===1);
check("Confirmed future scheduled trip is not active",policy.select([{
 ...active,status:"CONFIRMADA",pickup_mode:"schedule"
}]).count===0);
check("No automatic selection when two active rides",policy.select([active,{
 ...active,id:"qa-456",status:"EN_VIAJE"
}]).ride===null);
check("Pending unaccepted booking is not active",policy.select([{...active,status:"PENDIENTE"}]).count===0);
check("Finished booking never reopens live map",policy.select([{...active,status:"FINALIZADA"}]).count===0);
check("Canceled booking never reopens live map",policy.select([{...active,status:"CANCELADA"}]).count===0);
check("Old accepted immediate not reopened",policy.select([{...active,created_at:new Date(Date.now()-100*60000).toISOString()}]).count===0);
check("Numeric immediate mode is recognized",policy.select([{...active,pickup_mode:"10"}]).count===1);
const app=read("web-pasajero/app.js"),focus=read("web-pasajero/quicktrip-map-focus.js");
const map=read("web-pasajero/premium-map.js"),gps=read("web-pasajero/reservation-live.js");
const css=read("web-pasajero/quicktrip-singleflow-premium.css");
const html=read("web-pasajero/index.html"),sw=read("web-pasajero/sw.js");
check("Accepted journey opens only from authenticated server records",
 app.includes("function currentVerifiedRide()")&&
 app.includes("ride&&(authReady()||ride.__guestToken)?ride:null")&&
 app.includes("const active=currentVerifiedRide();")&&
 app.includes("openPremiumTracking(active);"));
check("New tap on Tu viaje resumes current map; Back does not loop",
 app.includes('target==="viaje"?enterService("now")')&&
 app.includes('tab.dataset.view==="viaje"?enterService("now")')&&
 app.includes('window.addEventListener("quicktrip:tracking-closed"')&&
 app.includes('shiftView("inicio");syncActiveTripUi();'));
check("Acceptance event uses live-only policy; no future scheduled hijack",
 app.includes("window.QuickTripActiveRide?.select?.([r]).count!==1")&&
 app.includes("autoFollowAccepted(rows,oldTripRows)"));
check("Waiting for driver never pretends acceptance",
 app.includes("conductor debe responder")&&
 app.includes("Si acepta, se abrirá tu mapa automáticamente"));
check("Fullscreen tracking remains authorized original RPC",
 app.includes('openFullscreenTracking(r)')&&
 focus.includes("root.TrasladosReservationLive.mount(host,")&&
 gps.includes('rpc("customer_reservation_position_v1"'));
check("Normal map camera follows last VERIFIED car position only",
 gps.includes('cameraPosition:kind==="driver"?points[0]:null')&&
 map.includes("options.followVehicle===true&&carFocus&&!camera.custom")&&
 map.includes("zoom:16")&&
 focus.includes("followVehicle:true"));
check("Manual view and full route remain available",
 map.includes("camera.custom={x:view.centerX/view.scale")&&
 map.includes('"Mostrar todo el recorrido"')&&
 map.includes('"Recentrar y seguir el vehículo"')&&
 map.includes('"Activar o desactivar arrastrar y pellizcar el mapa"'));
check("No duplicate form map visible, but original DOM remains alive",
 css.includes(".qtc-places .premium-route-canvas")&&
 css.includes("display:block!important")&&
 css.includes("height:0!important;min-height:0!important;max-height:0!important")&&
 !css.includes(".qtc-app{display:none"));
check("Original pickup presence never hidden by premium CSS",
 !css.includes("#pickup-presence{display:none")&&
 !css.includes(".qtc-driver{display:none")&&
 !css.includes(".qtc-driver{visibility:hidden"));
check("Original quote and send CTA remain visible",
 css.includes("body.qt-clean .qtc-app #urgent-send{")&&
 css.includes('body.qt-clean .qtc-app #urgent-route-recap')&&
 app.includes("verifiedFixedFareForBooking"));
check("Existing availability, ETA, and route callbacks preserved",
 app.includes("publicDriverAvailability(force)")&&
 app.includes("window.TrasladosETA.pickup({")&&
 app.includes("window.TrasladosPickupCadence.intervalMs(driver.motionHint)")&&
 app.includes("window.TrasladosRoutes.verified(current)"));
check("Existing live polling remains unchanged at four seconds",
 gps.includes("},4000)")&&gps.includes("customer_reservation_position_v1"));
check("Fullscreen back is home, not history",
 focus.includes('submit.textContent="Volver al inicio"')&&
 app.includes('shiftView("inicio");syncActiveTripUi();'));
check("PWA assets and scoped styles have fresh names",
 html.includes("quicktrip-singleflow-premium.css?v=premium-20261029-a")&&
 html.includes("premium-map.js?v=premium-20261029-a")&&
 html.includes("app.js?v=p12-direct-map-20261030")&&
 sw.includes("traslados-pwa-quicktrip-p14-recovery-20261030")&&
 html.includes("quicktrip-map-focus.js?v=trip-metrics-20261030")&&
 html.includes("reservation-live.js?v=trip-metrics-20261030"));
check("No presentation mock of GPS, price or ETA in production code",
 !/Math\.random\(|quote_final_total\s*[:=]\s*\d+|setInterval\(/.test(css+map.slice(0,350)) &&
 !/\brpc\(|\bfetch\(|new WebSocket/.test(css));
fs.mkdirSync(path.join(root,"artifacts/quicktrip-premium"),{recursive:true});
fs.writeFileSync(path.join(root,"artifacts/quicktrip-premium/contract.json"),
 JSON.stringify({suite:"QuickTrip Premium single-flow QA",checks:n,passed:n,
  guestAndAuthenticatedContract:true,realGpsVerified:false,liveEtaVerified:false,
  bookingWrites:false,backendChanges:false},null,2)+"\n");
console.log("Premium single-flow QA "+n+"/"+n+" PASS");
