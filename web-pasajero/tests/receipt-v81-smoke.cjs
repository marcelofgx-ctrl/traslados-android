"use strict";
// Static contract checks: no live reservations, customer data or network operations.
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const root=path.join(__dirname,"..");
const read=name=>fs.readFileSync(path.join(root,name),"utf8");
const app=read("app.js"),css=read("styles.css"),html=read("index.html"),sw=read("sw.js");
const publicIndex=fs.readFileSync(path.join(root,"../index.html"),"utf8");
const a=app.indexOf("function renderBookingSuccess("),b=app.indexOf("\n/** New trips",a);
assert(a>=0&&b>a,"Receipt renderer must remain available");
const receipt=app.slice(a,b);
assert(receipt.includes('const dateLabel=reviewDate(result.pickup_date,result.pickup_time)'),
 "User must see formatted Uruguay pickup date instead of ISO date");
assert(!receipt.includes('+" h del "+result.pickup_date'),
 "Unformatted date must not return");
assert(receipt.includes('result.fare_mode==="fixed"')&&receipt.includes("quote_final_total"),
 "Fixed price remains tied to the server response");
assert(receipt.includes("todavía no es un viaje confirmado"),
 "Request cannot pretend to be accepted");
assert(receipt.includes('shiftView("viajes")'),"Link to My Trips is mandatory");
assert(css.includes("#booking-success.success .success-receipt"),"Compact mobile card must be styled");
assert(html.includes("./app.js?v=81")&&html.includes("./styles.css?v=81"));
assert(sw.includes('traslados-pwa-v81-dir001-fallback-20261018'),"SW must invalidate the cached address engine when DIR-001 changes");
assert(!publicIndex.includes("APK Cliente")&&!publicIndex.includes("la PWA"),"No technical passenger copy");
assert(publicIndex.includes('href="./web-pasajero/"')&&publicIndex.includes('href="./web-pasajero/#instalar"'),"Both public CTAs must work");
console.log("PASS receipt v81: fixed price, human date, request pending, compact UI, cache and public links");
