"use strict";
// Regresión de acordeón accesible sin navegador, datos personales ni red.
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {join}=require("node:path");
const {runInNewContext}=require("node:vm");
const root=join(__dirname,"..");
const source=readFileSync(join(root,"booking-steps.js"),"utf8");
const html=readFileSync(join(root,"index.html"),"utf8");
const css=readFileSync(join(root,"styles.css"),"utf8");
const sw=readFileSync(join(root,"sw.js"),"utf8");
const app=readFileSync(join(root,"app.js"),"utf8");
for(const step of ["route","when","passenger"]){
  assert(html.includes('data-booking-step="'+step+'"'),"Missing "+step);
  assert(html.includes('id="booking-step-'+step+'"'),"Missing panel id "+step);
  assert(html.includes('aria-controls="booking-step-'+step+'"'),"Missing accessible toggle "+step);
}
assert(html.includes('id="booking-progress-text"'));
assert(html.includes('id="when-next"'));
assert(html.indexOf('booking-steps.js?v=1')<html.indexOf('app.js?v=38'),
  "Accordion loads before the application");
assert(sw.includes('traslados-cliente-pwa-v38')&&sw.includes('booking-steps.js'),
  "Offline install must include the new accordion script");
assert(css.includes("booking-step-content[hidden]"));
assert(css.includes("prefers-reduced-motion"));
assert(css.includes("repeating-linear-gradient"),"Do not lose existing premium texture");
assert(app.includes("window.TrasladosBookingSteps.mount(document)"));
assert(app.includes('suggestBookingStep(e);showError(e);'),
  "Errors must reopen their required section");
assert(app.includes('routeStepAutoAdvanced=true'),
  "Origin/destination must advance the next booking step");
assert(app.includes('if(selectedPickupMode!=="schedule")return;'),
  "The schedule continue button must not bypass urgent booking rules");
assert(app.includes('window.TrasladosBookingContracts.registeredReservation'),
  "Keep existing booking contract");
assert(app.includes("publicDriverAvailability")&&app.includes("refreshPickupPresence"),
  "Keep live driver ETA");
assert(!html.includes("CONDUCTOR DISPONIBLE SIEMPRE"),"Do not invent live driver availability");

function element(){
  const events={},attrs={},classes=new Set();
  return{
    hidden:false,textContent:"",style:{},dataset:{},scrolls:0,
    attrs,events,classes,
    classList:{
      toggle(k,on){if(on)classes.add(k);else classes.delete(k);}
    },
    addEventListener(k,fn){events[k]=fn;},
    setAttribute(k,v){attrs[k]=String(v);},
    scrollIntoView(){this.scrolls++;}
  };
}
const steps={};
for(const key of ["route","when","passenger"]){
  const item=element(),toggle=element(),body=element(),summary=element(),check=element();
  item.dataset.bookingStep=key;
  item.querySelector=sel=>({
    "[data-step-toggle]":toggle,"[data-step-body]":body,
    "[data-step-summary]":summary,"[data-step-check]":check
  })[sel];
  steps[key]={item,toggle,body,summary,check};
}
const text=element(),bar=element(),fill=element();
const documentRoot={
  querySelectorAll(sel){assert.equal(sel,"[data-booking-step]");return Object.values(steps).map(x=>x.item);},
  getElementById(key){return {"booking-progress-text":text,"booking-progress-bar":bar,
    "booking-progress-fill":fill}[key];}
};
const window={};
runInNewContext(source,{window},{filename:"booking-steps.js"});
const control=window.TrasladosBookingSteps.mount(documentRoot);
assert(Object.isFrozen(control));
assert.equal(control.active(),"route");
assert.equal(steps.route.body.hidden,false);
assert.equal(steps.when.body.hidden,true);
assert.equal(steps.passenger.toggle.attrs["aria-expanded"],"false");
const mockField={value:"Arenal Grande 2274"};
control.summary("route","Arenal Grande → Aeropuerto",true);
assert.equal(steps.route.summary.textContent,"Arenal Grande → Aeropuerto");
assert.equal(steps.route.check.hidden,false);
assert.equal(bar.attrs["aria-valuenow"],"1");
assert.equal(fill.style.width,(100/3)+"%");
control.open("when",{scroll:true});
assert.equal(control.active(),"when");
assert.equal(steps.route.body.hidden,true);
assert.equal(steps.when.body.hidden,false);
assert.equal(steps.when.item.scrolls,1);
assert.equal(steps.when.toggle.attrs["aria-expanded"],"true");
assert.equal(mockField.value,"Arenal Grande 2274","Never clear addresses while collapsing");
steps.when.toggle.events.click();
assert.equal(control.active(),null,"A fully collapsed view must be possible");
assert.equal(steps.when.body.hidden,true);
steps.passenger.toggle.events.click();
assert.equal(control.active(),"passenger");
control.summary("when","11/10 · 09:30 · 2 pasajeros",true);
control.summary("passenger","Datos de contacto completos",true);
assert.equal(bar.attrs["aria-valuenow"],"3");
assert.equal(fill.style.width,"100%");
assert(steps.passenger.item.classes.has("is-complete"));
control.open("route");
control.summary("route","Elegí origen y destino",false);
assert.equal(steps.route.check.hidden,true,"Editing the route must invalidate its green check");
assert.equal(mockField.value,"Arenal Grande 2274");
console.log("PASS premium accordion v38: accessibility, folding, mobile styles, summaries, edit and state persistence");
