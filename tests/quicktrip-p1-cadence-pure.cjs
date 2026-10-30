"use strict";
// QA fallback for a legacy cadence smoke that pins an obsolete PWA v83 name.
// Checks the actual IMMUTABLE scheduler and its privacy-rounded ETA labels.
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm"),assert=require("node:assert/strict");
const source=fs.readFileSync(path.resolve(__dirname,"../web-pasajero/pickup-cadence.js"),"utf8");
const window={};
vm.runInNewContext(source,{window,Number,Math});
const c=window.TrasladosPickupCadence;
assert.equal(c.intervalMs("moving"),25000);
assert.equal(c.intervalMs("stopped"),45000);
assert.equal(c.intervalMs(undefined),45000);
assert.equal(c.arrivalLabel(2),"Hasta 5 min");
assert.equal(c.arrivalLabel(6),"5–10 min");
assert.equal(c.arrivalLabel(13),"10–15 min");
assert.equal(c.arrivalLabel(-1),"Por confirmar");
assert.equal(c.arrivalLabel(NaN),"Por confirmar");
assert(Object.isFrozen(c));
const app=fs.readFileSync(path.resolve(__dirname,"../web-pasajero/app.js"),"utf8");
assert(app.includes("window.TrasladosPickupCadence.intervalMs(driver.motionHint)"));
console.log("PASS P1 pickup cadence: original 25s moving / 45s stationary, privacy buckets and unchanged app binding");
