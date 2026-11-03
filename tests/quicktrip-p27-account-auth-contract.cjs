"use strict";
// P2.7 account-first passenger UI. Source checks are non-networked and readonly.
const fs=require("node:fs"),assert=require("node:assert/strict");
const html=fs.readFileSync("web-pasajero/index.html","utf8");
const app=fs.readFileSync("web-pasajero/app.js","utf8");
const css=fs.readFileSync("web-pasajero/quicktrip-p27-auth-gate.css","utf8");
const sw=fs.readFileSync("web-pasajero/sw.js","utf8");
const ok=(name,yes)=>{assert.ok(yes,name);console.log("PASS P27",name)};
ok("Guest sees a real action to sign in rather than merely a name/phone form",
 html.includes('id="passenger-auth-gate"')&&html.includes('id="passenger-auth-action"')&&
 html.includes("Iniciá sesión para pedir tu viaje")&&
 app.includes('$("passenger-identification").hidden=true;')&&
 app.includes('$("passenger-auth-gate").hidden=logged;'));
ok("Guest and signed-in CTA states are accurately labeled",
 app.includes('logged?"SOLICITAR VIAJE":"INGRESAR PARA SOLICITAR"')&&
 app.includes('logged?"REVISAR Y CONFIRMAR":"INGRESAR PARA SOLICITAR"'));
ok("Guest request button and passenger gate both require a real account",
 app.includes('$("passenger-auth-action").addEventListener("click",()=>requireAccountForNewTrip());')&&
 /async function submitUrgentPickup\(\)\{[\s\S]*?if\(!requireAccountForNewTrip\(\)\)return;/.test(app)&&
 /async function submitBooking\(\)\{[\s\S]*?if\(!requireAccountForNewTrip\(\)\)return;/.test(app));
ok("Schedule review itself cannot solicit guest details",
 /function showPreview\(\)\{[\s\S]*?if\(!requireAccountForNewTrip\(\)\)return;/.test(app));
ok("No booking payload is built for a guest",
 /function bookingData\(\)\{[\s\S]*?if\(!authReady\(\)\)throw new Error\("Iniciá sesión antes de solicitar tu viaje."\)/.test(app));
ok("Account transition keeps the planned journey, but does not auto-send after login",
 app.includes('accountJourneyReturn={view:selectedPickupMode==="schedule"?"reservar":"viaje"')&&
 app.includes('if(resume?.review)showPreview();')&&
 app.includes('if(resume?.view==="viaje"&&resume.stage)premiumJourney?.open(resume.stage);'));
ok("PWA cache and styles stay in sync without GPS/route changes",
 html.includes("app.js?v=p27-auth-first-20261103")&&
 html.includes("quicktrip-p27-auth-gate.css?v=p27-20261103")&&
 sw.includes('traslados-pwa-quicktrip-p27-auth-first-20261103')&&
 sw.includes('"./quicktrip-p27-auth-gate.css"')&&
 css.includes(".passenger-auth-gate[hidden]"));
console.log("P27 source contracts passed; backend remains unmodified.");
