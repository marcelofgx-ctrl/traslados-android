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
const routeSource=readFileSync(path.join(root,"route-engine.js"),"utf8");
const etaSource=readFileSync(path.join(root,"eta-engine.js"),"utf8");
const begin=js.indexOf("function renderRouteSummary(){");
const end=js.indexOf("\nlet originPicker,destPicker",begin);
assert(begin>=0&&end>begin,"Route function not found");
assert(html.includes('id="route-overview"'),"Missing view target");
assert(html.includes('id="urgent-send"'),"Missing immediate backend request");
assert(etaSource.includes('/functions/v1/pickup-eta')&&js.includes("window.TrasladosETA.pickup"),"Missing authenticated ETA integration");
assert(js.includes('sessionToken:signedIn?session.token:null')&&etaSource.includes('sessionToken'),"The customer session must be validated server-side");
assert(js.includes('La recogida requiere confirmación.')&&js.includes('Distancia y tiempo aproximados.'),"ETA must be shown as approximate and pending confirmation");
assert(html.includes('id="pickup-presence-status"'),"Missing live status region");
assert(css.includes(".route-summary-heading"),"Missing premium compact heading");
assert(css.includes(".route-summary-indicator"),"Missing status styling");
assert(!js.slice(begin,end).includes("route-compact-path"),"Do not repeat A/B already shown in selectors");
assert(css.includes(".route-extra:not([open])"),"Route details must remain folded");
assert(!html.includes('id="urgent-whatsapp"'),"Remove WhatsApp CTA from reservation form");
assert(js.includes('selectedPickupMode!=="schedule"'),"Immediate booking must not use normal reservation RPC");
assert(js.includes("Recibimos tu pedido de recogida. El conductor debe responder"),"Urgent pickup must require human acceptance");
assert(js.includes('rpc("customer_request_pickup_v1"'),"Urgent pickup must reach backend");


class E{
 constructor(tag="div"){this.tag=tag;this.nodes=[];this.textContent="";this.hidden=false;this.attributes={};this.classList={add:(name)=>{const names=new Set(this.className.split(/\s+/).filter(Boolean));names.add(name);this.className=[...names].join(" ");},remove:(name)=>{this.className=this.className.split(/\s+/).filter(x=>x!==name).join(" ");}};}
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
let cacheFallback={available:false};
const routeWindow={};
runInNewContext(routeSource,{
  window:routeWindow,Number,Map,Date,AbortController,encodeURIComponent,setTimeout,clearTimeout,
  fetch:async url=>{
    if(String(url).includes("public_reference_quote_v1"))return {ok:true,json:async()=>({available:true,currency:"UYU",referenceFareUyu:720,rateUyuPerKm:40})};
    const data=String(url).includes("/public_cached_route_preview_v1")?cacheFallback:response;
    return {ok:data.available===true,json:async()=>data};
  }
});
const roadEngine=routeWindow.TrasladosRoutes;
assert(roadEngine?.verified&&roadEngine?.estimate,"Shared routing module must load");
const ctx={
  API:"https://example.invalid",KEY:"test-public",
  Number,Map,AbortController,encodeURIComponent,
  setTimeout:()=>1,clearTimeout:()=>{},
  document:{createElement:tag=>new E(tag)},
  $:key=>key==="route-overview"?host:null,
  text:(parent,tag,words,cls)=>{const x=new E(tag);x.textContent=String(words??"");x.className=cls||"";parent.appendChild(x);return x;},
  clear:n=>n.replaceChildren(),
  selectedRoadPoints:()=>points,
  routeKeyOf:p=>p.map(x=>x.lng.toFixed(6)+","+x.lat.toFixed(6)).join(";"),
  refreshPickupPresence:()=>{},
  refreshBookingSteps:()=>{},
  mapsLinkFromBooking:()=>"https://www.google.com/maps/dir/?api=1",
  addRoadDiagram:()=>{},
  roadEngine,
  record:{stops:[]},
  routeSequence:0,activeRouteKey:"",latestRoad:null
};
runInNewContext(js.slice(begin,end),ctx);
function nodes(cls){return host.walk().filter(n=>n.className.split(" ").includes(cls));}
function val(cls){return nodes(cls).map(n=>n.textContent);}
async function settle(){await Promise.resolve();await new Promise(setImmediate);}
(async()=>{
  ctx.renderRouteSummary();
  await settle();
  assert.equal(nodes("route-metrics").length,1);
  assert.equal(nodes("route-summary-heading").length,1);
  assert.equal(nodes("route-compact-path").length,0,"Do not duplicate origin/destination");
  assert.equal(nodes("route-metric").length,3);
  assert.deepEqual(val("route-metric").filter(x=>x==="Ver en Maps"),[]);
  assert.equal(nodes("route-google").length,1,"Just one Maps link");
  assert.equal(nodes("route-extra").length,1,"Details folded");
  assert.equal(nodes("route-extra")[0].attributes.open,undefined);
  assert.equal(ctx.latestRoad,null,"No fictional road data");
  assert.equal(nodes("route-fare-heading")[0].nodes[1].textContent,"A confirmar");
  assert.equal(nodes("route-retry").length,1,"Retry is visible when no verified route exists");
  assert.equal(nodes("route-retry")[0].hidden,false);
  assert.match(val("route-status").join(" "),/habilitación/,"Unconfigured server should be explained without inventing a road estimate");
  // Existing Supabase ROAD route is reused only if Cloudflare did not provide a route.
  cacheFallback=({
    available:true,source:"supabase_route_cache",distanceKm:18.1,durationMin:25,
    referenceFareUyu:720,geometry:[],calculatedAt:new Date().toISOString()
  });
  nodes("route-retry")[0].onclick();
  await settle();
  assert.equal(nodes("route-retry")[0].hidden,true,"A verified route hides the retry control");
  assert.equal(nodes("route-metric")[0].nodes[1].textContent,"18,1 km");
  assert.equal(nodes("route-metric")[1].nodes[1].textContent,"25 min");
  assert.equal(nodes("route-fare-heading")[0].nodes[1].textContent,"$ 720");
  assert.equal(ctx.latestRoad.data.source,"supabase_route_cache");
  // ORS remains the primary provider when it is available.
    response={available:true,source:"openrouteservice",distanceKm:8,durationMin:14,referenceFareUyu:320,geometry:[],calculatedAt:new Date().toISOString()};
  roadEngine.invalidate();ctx.renderRouteSummary();
  await settle();
  assert.equal(nodes("route-metric")[0].nodes[1].textContent,"8 km");
  assert.equal(nodes("route-metric")[1].nodes[1].textContent,"14 min");
  assert.equal(nodes("route-fare-heading")[0].nodes[1].textContent,"$ 320");
  assert(ctx.latestRoad&&ctx.latestRoad.data.referenceFareUyu===320);
  assert(roadEngine.peek(ctx.activeRouteKey)?.referenceFareUyu===320,"Verified quote must be cached temporarily");
  // A newly added stop has no coordinates: the previous quote must vanish.
  ctx.record.stops=[null];
  ctx.renderRouteSummary();
  assert.equal(ctx.latestRoad,null,"Incomplete intermediate stop invalidates verified quotes");
  assert.equal(ctx.activeRouteKey,"","Do not retain stale route cache identity");
  assert.equal(nodes("route-metric").length,0,"No stale price when a stop awaits verification");
  assert.match(val("route-status").join(" "),/dirección de todas las paradas/);
  console.log("PASS PWA compact summary: route results / ORS missing / price / no invented values");
})().catch(e=>{console.error(e);process.exitCode=1;});
