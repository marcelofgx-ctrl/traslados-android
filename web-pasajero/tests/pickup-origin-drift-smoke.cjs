"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const dir=path.resolve(__dirname,".."),read=p=>fs.readFileSync(path.join(dir,p),"utf8");
const listeners={};let watches=[],cleared=[],origin={lat:-34.84,lng:-56.03,source:"gps"},enabled=true;
const createNode=()=>({
 hidden:false,children:[],events:{},setAttribute(){},append(...x){this.children.push(...x);},
 addEventListener(name,fn){this.events[name]=fn;},insertAdjacentElement(_where,node){listeners.banner=node;}
});
const doc={visibilityState:"visible",getElementById:()=>createNode(),createElement:()=>createNode(),
 addEventListener(name,fn){listeners[name]=fn;}};
const win={addEventListener(name,fn){listeners[name]=fn;},
 navigator:{geolocation:{watchPosition(ok,fail,options){watches.push({ok,fail,options});return watches.length;},
 clearWatch(id){cleared.push(id);}}}};
const ctx={window:win};vm.runInNewContext(read("pickup-origin-drift.js"),ctx);
const api=ctx.window.TrasladosOriginDrift;
const now=Date.now();
const fix=(lat,accuracy=7,ts=now)=>({coords:{latitude:lat,longitude:origin.lng,accuracy},timestamp:ts});
assert.equal(api.displacement(origin,fix(origin.lat),now),null);
assert.equal(api.displacement(origin,fix(origin.lat+.00015),now),null);
assert(api.displacement(origin,fix(origin.lat+.006),now)>500);
assert.equal(api.displacement(origin,fix(origin.lat+.006,150),now),null);
assert.equal(api.displacement(origin,fix(origin.lat+.006,7,now-120000),now),null);
assert.equal(api.displacement({...origin,source:"search"},fix(origin.lat+.006),now),null);
let refreshes=0;
const controller=api.mount(doc,{getOrigin:()=>origin,isActive:()=>enabled,onRefresh:()=>{refreshes++;}});
controller.sync();assert.equal(watches.length,0,"no automatic GPS before user-selected origin");
controller.onOriginSelected();assert.equal(watches.length,1);
watches[0].ok(fix(origin.lat+.006));assert.equal(listeners.banner.hidden,true);
const clock=Date.now;Date.now=()=>now+5000;
watches[0].ok(fix(origin.lat+.006,7,now+5000));
Date.now=clock;
assert.equal(listeners.banner.hidden,false,"moved origin must prompt");
const actions=listeners.banner.children[2].children;
actions[1].events.click();assert.equal(listeners.banner.hidden,true);
controller.sync();assert.equal(watches.length,1,"keeping origin must not nag");
controller.onOriginSelected();assert.equal(watches.length,2,"new explicit GPS selection re-enables observation");
doc.visibilityState="hidden";listeners.visibilitychange();assert(cleared.includes(2));
doc.visibilityState="visible";listeners.visibilitychange();assert.equal(watches.length,3);
enabled=false;controller.sync();assert(cleared.includes(3),"submitted/other views stop watching");
enabled=true;controller.sync();assert.equal(watches.length,4);
watches[3].ok(fix(origin.lat+.006,7,now));
Date.now=()=>now+5000;
watches[3].ok(fix(origin.lat+.006,7,now+5000));
Date.now=clock;
actions[0].events.click();assert.equal(refreshes,1,"only click refreshes picker");
const app=read("app.js"),html=read("index.html"),sw=read("sw.js");
assert(html.indexOf("pickup-origin-drift.js")<html.indexOf("app.js?v="));
assert(sw.includes('"./pickup-origin-drift.js"'));
assert(app.includes("pickupOriginMonitor.onOriginSelected()"));
assert(app.includes("pickupOriginMonitor?.sync()"));
assert(app.includes('source:"gps"'),"must preserve GPS-origin contract");
console.log("PASS: movement warning, manual confirmation, retain origin, precision, privacy and visibility");
