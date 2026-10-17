"use strict";
const fs=require("node:fs"),vm=require("node:vm"),assert=require("node:assert/strict"),path=require("node:path");
const app=fs.readFileSync(path.join(__dirname,"../app.js"),"utf8");
const marker='$("journey-clock-input").addEventListener("change",()=>{';
const start=app.indexOf(marker);
assert(start>=0);
const body=app.slice(start+marker.length,app.indexOf("\n  });",start));
function run(day,time,now) {
 const input={value:time,hidden:false},select={value:"custom"},label={hidden:true,textContent:""};
 const elements={"journey-clock-input":input,"journey-when-select":select,"journey-custom-time-label":label};
 const result={modes:[],messages:[],input,select,label};
 class Clock extends Date { static now(){return new Date(now).getTime();} }
 vm.runInNewContext("(function(){"+body+"})()",{
   $:id=>elements[id],currentUY:()=>({day}),Date:Clock,Math,selectedPickupMode:"now",
   setWhen:mode=>result.modes.push(mode),tell:msg=>result.messages.push(msg)
 });
 return result;
}
for(const [time,now,delay] of [
 ["14:35","2026-10-15T14:05:00-03:00","30"],
 ["23:59","2026-10-15T23:50:00-03:00","9"],
 ["00:15","2026-10-15T23:50:00-03:00","25"]
]){
 const r=run("2026-10-15",time,now);
 assert.deepEqual(r.modes,[delay],time);
 assert.equal(r.input.hidden,true);
 assert.equal(r.label.hidden,false);
 assert.equal(r.select.value,"custom");
 assert(r.label.textContent.includes(time));
}
for(const time of ["25:60","xx:yy","9:00"]){
 const r=run("2026-10-15",time,"2026-10-15T14:05:00-03:00");
 assert.deepEqual(r.modes,[]);
}
for(const time of ["18:00","14:00"]){
 const r=run("2026-10-15",time,"2026-10-15T14:05:00-03:00");
 assert.deepEqual(r.modes,[]);
 assert.equal(r.messages.length,1);
}
console.log("PASS: manual 24h time, selection closes, midnight rollover, invalid and out-of-range rejected; no requests.");
