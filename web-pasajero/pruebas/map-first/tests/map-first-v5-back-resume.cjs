"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const dir=path.resolve(__dirname,"..");
const read=n=>fs.readFileSync(path.join(dir,n),"utf8");
const html=read("index.html");
const app=read("app.js");
const journey=read("premium-journey.js");
const navSource=read("map-first-v5-navigation.js");
const sw=read("sw.js");
const manifest=JSON.parse(read("manifest.webmanifest"));
assert(html.includes('meta name="traslados-preview"'),"No QA booking bypass");
assert(html.includes('map-first-v5-navigation.js?v=1'),"V5 coordinator is loaded");
assert(html.includes('app.js?v=83')&&html.includes('premium-journey.js?v=2'));
assert(html.indexOf('src="./app.js?v=83"')<html.indexOf('src="./map-first-v5-navigation.js?v=1"'));
assert(app.includes("previewNavigation?.onView(name)"),"View transitions must be recorded");
assert(app.includes("recoverRoadAfterReturn(\"enter-service\")"),"App re-entry must retry failed road data");
assert(app.includes("if(routeRequestInFlightKey===key)return;"),"Do not duplicate in-flight road calls");
assert(app.includes("roadEngine.invalidate(key);")&&app.includes("renderRouteSummary();"));
assert(journey.includes("TrasladosPreviewNavigationSession?.onStage(name)"));
assert(manifest.start_url.endsWith("?mapfirst=1&qa=v5"));
assert(sw.includes("traslados-map-first-preview-v5-20261019"));
assert(sw.includes("url.pathname.startsWith(scopePath)"));
const events={};let selected="inicio",step="route",calls=[],recoveries=0, visible="visible";
let historyIndex=-1, stack=[];
const root={
  location:{href:"https://example.com/traslados-android/web-pasajero/pruebas/map-first/?mapfirst=1&qa=v5"},
  navigator:{onLine:true},
  addEventListener:(kind,handler)=>(events[kind]??=[]).push(handler),
  clearTimeout(){},setTimeout(fn){fn();return 1;}
};
root.history={
  state:null,
  replaceState(next){this.state=next;stack[historyIndex<0?++historyIndex:historyIndex]=next;},
  pushState(next){this.state=next;stack=stack.slice(0,historyIndex+1);stack.push(next);historyIndex++;},
  back(){if(historyIndex===0)return;historyIndex--;this.state=stack[historyIndex];(events.popstate||[]).forEach(cb=>cb({state:this.state}));}
};
const doc={
  visibilityState:"visible",
  querySelector(sel){
    if(sel==='meta[name="traslados-preview"]')return{};
    if(sel===".view.active")return {id:"view-"+selected};
    return null;
  },
  getElementById(id){if(id==="view-viaje")return {dataset:{premiumStep:step}};return null;},
  addEventListener:(kind,cb)=>(events[kind]??=[]).push(cb)
};
vm.runInNewContext(navSource,{window:root});
const nav=root.TrasladosPreviewNavigation.create({
  history:root.history,doc,
  restoreView:name=>{selected=name;calls.push("view:"+name);},
  restoreStage:name=>{step=name;calls.push("stage:"+name);},
  recover:reason=>{recoveries++;calls.push("recover:"+reason);}
});
assert(nav);
nav.mount();
assert.equal(stack.length,1);
selected="viaje";nav.onView("viaje");step="when";nav.onStage("when");step="passenger";nav.onStage("passenger");
assert.equal(stack.length,4,"Home + ride + two journey steps");
root.history.back();assert.equal(selected,"viaje");assert.equal(step,"when");
root.history.back();assert.equal(step,"route");
root.history.back();assert.equal(selected,"inicio","Android Back returns home, not outside app");
selected="viaje";nav.onView("viaje");assert.equal(stack.length,2,"New journey entry survives after Android Back");
(events.pageshow||[]).forEach(fn=>fn({persisted:true}));
assert(recoveries>0,"BFCache restoration triggers route recovery");
const prior=recoveries;doc.visibilityState="hidden";(events.visibilitychange||[]).forEach(fn=>fn());
assert.equal(recoveries,prior,"Never call routes while hidden");
doc.visibilityState="visible";(events.visibilitychange||[]).forEach(fn=>fn());
assert(recoveries>prior,"Foreground triggers re-evaluation");
selected="inicio";nav.onView("inicio");root.history.back();assert.equal(selected,"viaje","From home, Android Back returns to app history, not browser exit");
console.log("PASS V5 Back: 4 journey stages, Android history, re-entry, BFCache, foreground, offline guards");
