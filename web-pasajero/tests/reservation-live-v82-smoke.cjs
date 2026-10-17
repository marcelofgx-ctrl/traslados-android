"use strict";
// Synthetic GPS only. Never calls Supabase or creates passenger reservations.
const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const base=path.resolve(__dirname,"..");
const source=fs.readFileSync(path.join(base,"reservation-live.js"),"utf8");
const app=fs.readFileSync(path.join(base,"app.js"),"utf8");
const style=fs.readFileSync(path.join(base,"premium-journey.css"),"utf8");
const callbacks=[];
let tabActive=false,connected=true,network=true,visibility="visible";
const activeView={classList:{contains:name=>name==="active"&&tabActive}};
function element(tag="div"){
  return {tag,children:[],attrs:{},dataset:{},isConnected:true,textContent:"",className:"",
    classList:{add(){}},append(...children){this.children.push(...children);},
    replaceChildren(...children){this.children=[...children];},
    setAttribute(k,v){this.attrs[k]=v;},
    closest(){return activeView;},
  };
}
const doc={createElement:element,get visibilityState(){return visibility;}};
const navigator={get onLine(){return network;}};
const ctx={window:{navigator},document:doc,console,setInterval:callback=>callbacks.push(callback),
  clearInterval:()=>{},Number,Date,Math,Promise,Map,Set,Intl};
vm.runInNewContext(source,ctx);
const follow=ctx.window.TrasladosReservationLive;
const trip={id:"QA-V82",status:"ACEPTADA",pickup_phase:"EN_CAMINO",
  origin_lat:-34.79,origin_lng:-56.01,
  destination_lat:-34.84,destination_lng:-56.08};
const sleep=()=>new Promise(resolve=>setTimeout(resolve,35));
function flatten(el){return [el,...el.children.flatMap(flatten)];}
function textOf(host){return flatten(host).map(e=>e.textContent).join(" | ");}
let rpcCount=0,poses=[{available:true,driverLat:-34.81,driverLng:-56.04,gpsAgeSec:6}];
let failingDriver=false;const modes=[];
const engine={
  async estimate(points){
    if(failingDriver&&points[0].lat===-34.82)throw Error("Road engine unavailable");
    return {geometry:points.map(p=>[p.lat,p.lng]),distanceKm:6.4,durationMin:11};
  },
  verified:result=>Array.isArray(result.geometry)&&result.geometry.length>1
};
const renderer={render(_host,_route,options){modes.push(options.routeMode);return true;}};
(async()=>{
  const host=element();
  host.isConnected=connected;
  follow.mount(host,{reservation:trip,sessionToken:"synthetic-session",rpc:async(name,args)=>{
    assert.equal(name,"customer_reservation_position_v1");
    assert.equal(args.p_session_token,"synthetic-session");
    rpcCount++;
    return poses[Math.min(rpcCount-1,poses.length-1)];
  },engine,renderer,api:"synthetic",key:"synthetic"});
  await sleep();
  assert.equal(rpcCount,0,"Inactive Mis traslados cannot poll GPS");
  tabActive=true;callbacks[0]();await sleep();
  assert.equal(rpcCount,1,"Active view polls authorized GPS");
  assert.equal(host.dataset.liveDriver,"true");
  assert(modes.includes("pickup"));
  tabActive=false;callbacks[0]();await sleep();
  assert.equal(rpcCount,1,"Hidden view cannot produce duplicate requests");
  visibility="hidden";tabActive=true;callbacks[0]();await sleep();
  assert.equal(rpcCount,1,"Background tab cannot poll GPS");
  visibility="visible";network=false;callbacks[0]();await sleep();
  assert.equal(rpcCount,1,"Offline device cannot poll GPS");
  network=true;poses.push({available:true,driverLat:-34.81,driverLng:-56.04,gpsAgeSec:90});
  callbacks[0]();await sleep();
  assert.equal(host.dataset.liveDriver,"false","Expired GPS clears the car");
  assert.equal(modes.at(-1),"contract","Fallback is booked route, not a stale vehicle");
  poses.push({available:true,driverLat:-34.82,driverLng:-56.05,gpsAgeSec:2});
  failingDriver=true;callbacks[0]();await sleep();
  assert.equal(host.dataset.liveDriver,"false","Road engine error cannot leave stale car marker");
  assert.match(textOf(host),/ruta vial no disponible/i);
  assert.equal(modes.at(-1),"contract","Failed vehicle route falls back to planned leg");
  failingDriver=false;
  const arrivedHost=element();
  follow.mount(arrivedHost,{reservation:{...trip,pickup_phase:"EN_PUNTO"},
    sessionToken:"synthetic-session",
    rpc:async()=>({available:true,driverLat:-34.81,driverLng:-56.04,gpsAgeSec:3}),
    engine,renderer,api:"synthetic",key:"synthetic"});
  await sleep();
  assert.match(textOf(arrivedHost),/Tu conductor llegó/);
  assert.match(textOf(arrivedHost),/En el punto/);
  assert.match(app,/function renderPremiumTracking\(\)[\s\S]*?TrasladosReservationLive\?\.mount\(mapHost/);
  assert.match(style,/#view-seguimiento \.reservation-live-map/);
  console.log("PASS v82: only active view polls private GPS, stale/routing failure clears driver map, detail reuses same tracker");
})().catch(error=>{console.error(error);process.exitCode=1;});
