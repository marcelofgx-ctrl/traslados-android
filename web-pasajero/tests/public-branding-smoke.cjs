"use strict";
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {join}=require("node:path");
const root=join(__dirname,"..");
const html=readFileSync(join(root,"index.html"),"utf8");
const app=readFileSync(join(root,"app.js"),"utf8");
const manifest=readFileSync(join(root,"manifest.webmanifest"),"utf8");
for(const [label,body] of [["HTML",html],["manifest",manifest]]){
  assert(!/github(?:\.com|\.io)|GitHub|Cloudflare|Supabase|\bPWA\b|Lovable|Replit/i.test(body),
    label+" must not disclose development platform to customers");
}
assert(!app.includes('status.textContent="Consultando Supabase'),
  "Booking status should be presented as a customer action");
assert(!app.includes('"Ruta por calles calculada por openrouteservice/HeiGIT'),
  "Do not display routing providers in the itinerary");
assert(!app.includes('"Ruta por calles calculada anteriormente y conservada en nuestro sistema (OSRM)'),
  "Do not display routing engine names in the itinerary");
assert(html.includes("TRASLADOS"),"Branded customer page must remain present");
assert(html.includes("INSTALAR TRASLADOS"),"Install action must stay available");
assert(html.includes("© 2026"),"Customer footer must be retained");
assert(/OpenStreetMap contributors/.test(app),
  "The map data copyright attribution must be retained");
console.log("PASS passenger-facing branding: Traslados only, no developer links or provider labels");
