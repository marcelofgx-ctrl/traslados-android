"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const root=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const html=read("index.html");
const js=read("map-first-v4-confirm.js");
const css=read("map-first-v4-confirm.css");
const manifest=JSON.parse(read("manifest.webmanifest"));
const sw=read("sw.js");
assert(html.includes('meta name="traslados-preview"'),"Review-only guard must be present");
assert(html.includes('map-first-v4-confirm.js?v=1'));
assert(html.includes('map-first-v4-confirm.css?v=1'));
assert(html.indexOf('src="./app.js?v=82"')<html.indexOf('src="./map-first-v4-confirm.js'));
for(const s of [".premium-confirm-route","#urgent-route-recap","#urgent-send","#urgent-send-error","#urgent-actions-description"])
  assert(js.includes(s),"Original component is moved, not recreated: "+s);
assert(js.includes("ticket.append(masthead,hero,recap,tear,route,meta,action)"));
assert(js.includes('new URL(root.location.href).searchParams.get("mapfirst")!=="1"'));
assert(js.includes('doc.querySelector(\'meta[name="traslados-preview"]\')'));
assert(!/\bfetch\s*\(|\bXMLHttpRequest\b|\bgeolocation\b|\bsupabase\b|create_reservation/i.test(js),
  "V4 presentation must not fetch, write, or invent GPS/fare/booking state");
assert(!js.includes("innerHTML=")||js.includes('brand.innerHTML='),"Static brand markup only");
assert(css.includes("#urgent-route-recap .request-recap-metrics>span:last-child strong"),
  "The original fare node must be the large figure");
assert(css.includes('.mf-v4-ticket-perforation'),"Ticket composition must be present");
assert(css.includes('.mf-v4-ticket-action #urgent-send'),"Reuse original send action");
assert(css.includes("#urgent-actions.mf-v4-confirm[hidden]{display:none!important}"));
assert(manifest.start_url.includes("mapfirst=1&qa=v4"));
assert(manifest.scope==="./");
assert(sw.includes("traslados-map-first-preview-v4-20261019"));
assert(sw.includes("url.pathname.startsWith(scopePath)"),"SW must stay in preview scope");
assert(!/map-first-v4-confirm/.test(read("map-first-v3.js")),"Do not mix V3 and V4 implementations");
console.log("PASS V4: ticket hierarchy, original price/route/action, independent app, read-only");
