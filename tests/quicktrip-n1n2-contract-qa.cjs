"use strict";
/* Contract and presentation regression. Never connects to Supabase or sends bookings. */
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const root=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const policy=require("../web-pasajero/quicktrip-active-ride.js");
let pass=0;function check(name,ok){assert.ok(ok,name);pass++;console.log("PASS N1/N2 "+name);}
const one={id:"qa1",status:"ACEPTADA",pickup_phase:"EN_CAMINO",quote_final_total:764};
check("unique accepted booking",policy.select([one]).ride===one);
check("onboard booking",policy.select([{id:"qa2",status:"EN_VIAJE"}]).count===1);
check("pending never live",policy.select([{id:"q1",status:"PENDIENTE"}]).count===0);
check("finalized never live",policy.select([{id:"q2",status:"FINALIZADA"}]).count===0);
check("canceled never live",policy.select([{id:"q2",status:"CANCELADA"}]).count===0);
check("unknown never live",policy.select([{id:"q2",status:"CUSTOM"}]).count===0);
check("missing ID disallowed",policy.select([{status:"ACEPTADA"}]).count===0);
check("ambiguous live booking must NOT auto-select",policy.select([one,{id:"qa2",status:"EN_VIAJE"}]).ride===null);
check("pickup state from confirmed phase",policy.stage(one)==="Tu conductor está en camino");
check("onboard status from source",policy.stage({status:"EN_VIAJE"})==="Viaje en curso");
check("terminal state lacks status",policy.stage(null)==="");
const app=read("web-pasajero/app.js");
const focus=read("web-pasajero/quicktrip-map-focus.js");
const active=read("web-pasajero/quicktrip-active-ride.js");
const css=read("web-pasajero/quicktrip-n1n2.css");
const html=read("web-pasajero/index.html");
const sw=read("web-pasajero/sw.js");
check("map leaves toward viaje not history",app.includes('window.addEventListener("quicktrip:tracking-closed"')&&
 app.includes('shiftView("viaje");syncActiveTripUi();'));
check("Android back has its own overlay history marker",focus.includes("qtTrackingOverlayN1:true")&&
 focus.includes('root.addEventListener("popstate"')&&focus.includes("close(true)"));
check("phase change preserves current overlay",focus.includes("continuingSameTracking")&&
 focus.includes("if(!continuingSameTracking&&root.history?.pushState)"));
check("visual CSS contains active card, slider and map sheet",css.includes(".qt-live-knob")&&
 css.includes("#qt-focus[data-mode=\"tracking\"]")&&css.includes("qt-has-active-ride"));
check("slider only navigates and has keyboard and tap fallback",active.includes("actions.onOpen?.(r)")&&
 active.includes('e.key==="Enter"')&&active.includes("qt-live-tap")&&
 !/\.update_reservation\(|customer_cancel_reservation|set_status|navigator\.geolocation/.test(active));
check("one live access in history, no duplicate spotlight",!app.includes('const summary=text(top,"div","","trip-live-spotlight")')&&
 app.includes('"↗ Ver mapa en vivo"'));
check("GPS RPC untouched and running at four seconds",
 read("web-pasajero/reservation-live.js").includes("},4000)")&&
 read("web-pasajero/reservation-live.js").includes('rpc("customer_reservation_position_v1"'));
check("registered asset version bypasses old PWA cache",
 html.includes('quicktrip-active-ride.js?v=n1n2-20261029')&&
 html.includes('quicktrip-n1n2.css?v=n1n2-20261029')&&
 sw.includes("traslados-pwa-quicktrip-n1n2-qa-20261029"));
check("booking view remains available by explicit choice",active.includes('view.classList.toggle("qt-show-new-trip"')&&
 css.includes(".qt-show-new-trip"));
check("active presentation never adds new network clients",
 !/\bfetch\(|\brpc\(|new\s+WebSocket|sendBeacon\(|\bXMLHttpRequest/.test(active));
check("history display is still available",html.includes('data-trips-scope="history"'));
const output={suite:"Quick Trip N1+N2",passed:pass,failed:0,liveReservations:false,physicalSamsung:false,backendModified:false};
fs.mkdirSync(path.join(root,"artifacts/quicktrip-n1n2"),{recursive:true});
fs.writeFileSync(path.join(root,"artifacts/quicktrip-n1n2/contract-qa.json"),JSON.stringify(output,null,2)+"\n");
console.log("N1+N2 QA PASS "+pass+"/ "+pass);
