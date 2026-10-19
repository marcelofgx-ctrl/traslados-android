"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const file=fs.readFileSync(path.resolve(__dirname,"../view-model.js"),"utf8");
const root={};vm.runInNewContext(file,{window:root});
const {state,render,demoFixture}=root.TrasladosV6Model;
const sample=demoFixture();
const v=state(sample);
assert.equal(v.price,"$ 660");
assert.equal(v.distance,"16,5 km");
assert.equal(v.duration,"25 min");
assert.equal(v.pickupEta,"~ 5 min");
assert.equal(v.origin,"Aeropuerto de Carrasco");
assert.equal(v.destination,"World Trade Center");
assert.equal(v.routeVerified,true);
assert.equal(v.pickupVerified,true);
function fixture(){return demoFixture();}
let s=fixture();s.route.quote.routeKey="different";let o=state(s);
assert.equal(o.routeVerified,false,"Stale route key must invalidate old fare");
assert.equal(o.price,"A confirmar");assert.equal(o.distance,"—");assert.equal(o.duration,"—");
s=fixture();s.route.stops=[{label:"",ready:false}];o=state(s);
assert.equal(o.routeVerified,false,"Partial intermediate stop invalidates quote");
assert.equal(o.pickupVerified,false,"Partial route must not claim conductor ETA");
s=fixture();s.route.quote.durationMin=null;o=state(s);
assert.equal(o.routeVerified,false,"null duration must never be coerced to zero");
s=fixture();s.route.quote.distanceKm=NaN;o=state(s);assert.equal(o.routeVerified,false);
s=fixture();s.route.quote.referenceFareUyu=NaN;o=state(s);
assert.equal(o.price,"A confirmar","A verified route with unknown fare cannot fabricate a price");
assert.equal(o.distance,"16,5 km");
s=fixture();s.pickup.originKey="old-origin";o=state(s);
assert.equal(o.pickupVerified,false,"Driver pickup key must match the selected origin");
assert.equal(o.pickupEta,"Sin estimación");
s=fixture();s.pickup.etaMin=null;o=state(s);assert.equal(o.pickupVerified,false);
s=fixture();s.route.origin.label="   ";o=state(s);
assert.equal(o.origin,"Elegí el origen");assert.equal(o.price,"A confirmar");
s=fixture();s.route.origin.label="<img src=x onerror=alert(1)>";o=state(s);
assert(o.origin.includes("<img"),"View model keeps text; renderer must use textContent, not HTML");
assert(!file.includes("innerHTML"),"No state values may be interpolated as HTML");
// Fake DOM contract: several surfaces share the same source of truth.
const nodes=new Map();
for(const id of ["route-origin","route-destination","trip-price","trip-distance","trip-duration","pickup-title","pickup-eta","pickup-detail","customer-name","customer-phone","selected-time"]){
  nodes.set('[data-v6-field="'+id+'"]',[{textContent:""},{textContent:""}]);
}
const priceState=[{dataset:{}},{dataset:{}}],pickupState=[{dataset:{}}];
nodes.set("[data-v6-quote-state]",priceState);
nodes.set("[data-v6-pickup-state]",pickupState);
const quote={textContent:""};
const doc={
  querySelectorAll:(sel)=>nodes.get(sel)||[],
  getElementById:(id)=>id==="quote-status"?quote:null
};
render(fixture(),doc);
assert.equal(nodes.get('[data-v6-field="trip-price"]')[0].textContent,"$ 660");
assert.equal(nodes.get('[data-v6-field="trip-price"]')[1].textContent,"$ 660");
assert.equal(priceState[0].dataset.v6QuoteState,"verified");
s=fixture();s.route.quote.routeKey="stale";s.pickup.originKey="other";
render(s,doc);
assert.equal(nodes.get('[data-v6-field="trip-price"]')[0].textContent,"A confirmar");
assert.equal(nodes.get('[data-v6-field="trip-price"]')[1].textContent,"A confirmar");
assert.equal(priceState[0].dataset.v6QuoteState,"pending");
assert.equal(priceState[1].dataset.v6QuoteState,"pending");
assert.equal(pickupState[0].dataset.v6PickupState,"pending");
assert(quote.textContent.includes("pendiente"));
console.log("PASS V6 contract: identical metrics in both steps, no stale route or pickup, no numeric coercion");
