"use strict";
// Lightweight DOM contract test of the ACTUAL route-summary function.
// No npm packages, network requests or customer data needed.
const {readFileSync}=require("node:fs");
const {runInNewContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const root=path.resolve(__dirname,"..");
const js=readFileSync(path.join(root,"app.js"),"utf8");
const html=readFileSync(path.join(root,"index.html"),"utf8");
const css=readFileSync(path.join(root,"styles.css"),"utf8");
const begin=js.indexOf("function renderRouteSummary(){");
const end=js.indexOf("\nlet originPicker,destPicker",begin);
assert(begin>=0&&end>begin,"Route function not found");
assert(html.includes('id="route-overview"'),"Missing view target");
assert(html.includes('id="pickup-presence-whatsapp"'),"Missing urgent consultation");
assert(css.includes(".route-compact-path"),"Missing compact styling");
assert(css.includes(".route-extra:not([open])"),"Route details must remain folded");
assert(html.includes('id="urgent-whatsapp"'),"Urgent pickup CTA missing");
assert(js.includes('selectedPickupMode!=="schedule"'),"Immediate booking must not use normal reservation RPC");
assert(js.includes('La recogida urgente')||js.includes('Las recogidas urgentes'),"Urgent bookings need explicit human confirmation");
assert(js.includes("const contact=\"https://wa.me/59897228175?text=\""),"WhatsApp must include origin and destination");


class E{
 constructor(tag="div"){this.tag=tag;this.nodes=[];this.textContent="";this.hidden=false;this.attributes={};}
 appendChild(x){this.nodes.push(x);return x;}
 replaceChildren(){this.nodes=[];this.textContent="";}
 setAttribute(k,v){this.attributes[k]=String(v);}
 get className(){return this._class||"";} set className(v){this._class=v;}
 walk(){return [this,...this.nodes.flatMap(x=>x.walk())];}
}
const host=new E("section");
const from={text:"Roque Saenz Peña 1711",lat:-34.83,lng:-55.95};
const to={text:"Aeropuerto Internacional de Carrasco",lat:-34.83696,lng:-56.01638};
const points=[from,to];
let response={available:false,reason:"not_configured"};
const ctx={
  Number,Map,AbortController,encodeURIComponent,
  setTimeout:()=>1,clearTimeout:()=>{},
  document:{createElement:tag=>new E(tag)},
  $:key=>key==="route-overview"?host:null,
  text:(parent,tag,words,cls)=>{const x=new E(tag);x.textContent=String(words??"");x.className=cls||"";parent.appendChild(x);return x;},
  clear:n=>n.replaceChildren(),
  selectedRoadPoints:()=>points,
  routeKeyOf:p=>p.map(x=>x.lng.toFixed(6)+","+x.lat.toFixed(6)).join(";"),
  refreshPickupPresence:()=>{},
  mapsLinkFromBooking:()=>"https://www.google.com/maps/dir/?api=1",
  addRoadDiagram:()=>{},
  roadCache:new Map(),
  routeSequence:0,activeRouteKey:"",latestRoad:null,
  SHARED_ROUTER:"https://example.invalid/route-estimate",
  cachedRoadRoute:async()=>null,
  fetch:async()=>response.available?{ok:true,json:async()=>response}:{ok:false,json:async()=>response}
};
runInNewContext(js.slice(begin,end),ctx);
function nodes(cls){return host.walk().filter(n=>n.className.split(" ").includes(cls));}
function val(cls){return nodes(cls).map(n=>n.textContent);}
async function settle(){await Promise.resolve();await new Promise(setImmediate);}
(async()=>{
  ctx.renderRouteSummary();
  await settle();
  assert.equal(nodes("route-metrics").length,1);
  assert.equal(nodes("route-metric").length,3);
  assert.deepEqual(val("route-metric").filter(x=>x==="Ver en Maps"),[]);
  assert.equal(nodes("route-google").length,1,"Just one Maps link");
  assert.equal(nodes("route-extra").length,1,"Details folded");
  assert.equal(nodes("route-extra")[0].attributes.open,undefined);
  assert.equal(ctx.latestRoad,null,"No fictional road data");
  assert.equal(nodes("route-fare-heading")[0].nodes[1].textContent,"Pendiente");
  // Existing Supabase ROAD route is reused only if Cloudflare did not provide a route.
  ctx.cachedRoadRoute=async()=>({
    available:true,source:"supabase_route_cache",distanceKm:18.1,durationMin:25,
    referenceFareUyu:720,geometry:[],calculatedAt:new Date().toISOString()
  });
  ctx.roadCache.clear();ctx.renderRouteSummary();
  await settle();
  assert.equal(nodes("route-metric")[0].nodes[1].textContent,"18,1 km");
  assert.equal(nodes("route-metric")[1].nodes[1].textContent,"25 min");
  assert.equal(nodes("route-fare-heading")[0].nodes[1].textContent,"$ 720");
  assert.equal(ctx.latestRoad.data.source,"supabase_route_cache");
  // ORS remains the primary provider when it is available.
    response={available:true,distanceKm:8,durationMin:14,referenceFareUyu:320,geometry:[],calculatedAt:new Date().toISOString()};
  ctx.roadCache.clear();ctx.renderRouteSummary();
  await settle();
  assert.equal(nodes("route-metric")[0].nodes[1].textContent,"8 km");
  assert.equal(nodes("route-metric")[1].nodes[1].textContent,"14 min");
  assert.equal(nodes("route-fare-heading")[0].nodes[1].textContent,"$ 320");
  assert(ctx.latestRoad&&ctx.latestRoad.data.referenceFareUyu===320);
  console.log("PASS PWA compact summary: route results / ORS missing / price / no invented values");
})().catch(e=>{console.error(e);process.exitCode=1;});
