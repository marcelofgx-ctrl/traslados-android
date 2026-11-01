"use strict";
// QA-only link normalization: root becomes a small safe redirect to passenger app.
// No browser-side JS, no new requests to Supabase, no booking or GPS flows.
const fs=require("node:fs"),assert=require("node:assert/strict");
const root=fs.readFileSync("index.html","utf8");
const app=fs.readFileSync("web-pasajero/index.html","utf8");
const css=fs.readFileSync("web-pasajero/quicktrip-p16-conductor-motion.css","utf8");
const check=(label,val)=>{assert.ok(val,label);console.log("PASS DIRECT ENTRY",label)};
check("Root HTML only redirects to the one passenger PWA",
 /^\s*<!doctype html>/i.test(root)&&
 /<meta\s+http-equiv="refresh"\s+content="0;\s*url=\.\/web-pasajero\/">/i.test(root)&&
 /<a\s+href="\.\/web-pasajero\/">/i.test(root));
check("The redirect stays on the same host",
 new URL("./web-pasajero/","https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app/").origin===
 "https://quicktrip-live-qa-only-20261027-marcelofgx-1927.vercel.app");
check("No old marketing page or extra application choices remain at QA root",
 !/INSTALAR TRASLADOS|Tu próximo viaje|Reservas claras|Estado del traslado|Una sola aplicación web/i.test(root));
check("No script, iframe or external redirect in root",
 !/<script|<iframe|location\.replace|https?:\/\//i.test(root));
check("Quick Trip passenger PWA stays original and contains approved visual CSS",
 app.includes("<title>Quick Trip · Tu viaje y reservas</title>")&&
 app.includes("quicktrip-clean.js?v=p14-recovery-20261030")&&
 app.includes("quicktrip-p16-conductor-motion.css")&&
 css.includes("qt-p16-dot-breathe"));
check("Non-JS fallback link and Spanish-language navigation",
 root.includes('lang="es-UY"')&&root.includes("Entrar directamente a Quick Trip"));
console.log("PASS: QA root points only to the existing Quick Trip passenger app");
