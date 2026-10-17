"use strict";
/* Uses synthetic positions only. No network, real journeys or backend updates. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const dir=path.resolve(__dirname,"..");
function node(tag="div"){
  const e={tag,attrs:{},children:[],dataset:{},style:{},className:"",textContent:"",
    clientWidth:350,isConnected:true,
    classList:{add(){}},
    append(...nodes){this.children.push(...nodes);},
    replaceChildren(...nodes){this.children=[...nodes];},
    setAttribute(k,v){this.attrs[k]=String(v);},
    addEventListener(){},
  };
  return e;
}
const dom={createElement:node,createElementNS:(_ns,tag)=>node(tag),visibilityState:"visible"};
const win={navigator:{onLine:true}};
const context={window:win,document:dom,Number,Date,Math,Promise,Map,Set,Intl,console};
for(const file of ["premium-map.js","reservation-live.js"])
  vm.runInNewContext(fs.readFileSync(path.join(dir,file),"utf8"),context);
const map=win.TrasladosPremiumMap;
const follow=win.TrasladosReservationLive;
const r={id:"synthetic-trip",status:"ACEPTADA",pickup_phase:"EN_CAMINO",
  origin_lat:-34.79,origin_lng:-56.01,destination_lat:-34.84,destination_lng:-56.08};
const way={estimate:async points=>({geometry:points.map(x=>[x.lat,x.lng]),distanceKm:7.4,durationMin:9}),
  verified:x=>Array.isArray(x?.geometry)&&x.geometry.length>1};
function flatten(el){return [el,...(el.children||[]).flatMap(flatten)];}
function pickMap(host){return flatten(host).find(x=>x.className==="premium-real-map");}
function labels(host){return flatten(host).filter(x=>x.tag==="text").map(x=>x.textContent);}
const tick=()=>new Promise(resolve=>setTimeout(resolve,35));
async function show(row,response){
  const host=node();
  follow.mount(host,{reservation:row,sessionToken:"synthetic-session",
    rpc:async(name,args)=>{assert.equal(name,"customer_reservation_position_v1");
      assert.equal(args.p_session_token,"synthetic-session");return response;},
    engine:way,renderer:map,api:"synthetic",key:"synthetic"});
  await tick();
  return host;
}
(async()=>{
  const pickup=await show(r,{available:true,driverLat:-34.81,driverLng:-56.04,gpsAgeSec:9});
  assert.equal(pickup.dataset.liveDriver,"true","Live routing only with recent authorized GPS");
  assert.match(pickMap(pickup).attrs["aria-label"],/recogida/);
  assert(labels(pickup).includes("🚘")&&labels(pickup).includes("P")&&labels(pickup).includes("D"),
    "Pickup should locate car, passenger and final destination separately");
  assert(flatten(pickup).some(x=>x.textContent==="AUTO → RECOGIDA"));
  const ride=await show({...r,status:"EN_VIAJE",pickup_phase:"EN_PUNTO"},
    {available:true,driverLat:-34.81,driverLng:-56.04,gpsAgeSec:4});
  assert.equal(ride.dataset.liveDriver,"true");
  assert.match(pickMap(ride).attrs["aria-label"],/pasajero a bordo/);
  assert(labels(ride).includes("🚘")&&labels(ride).includes("O")&&labels(ride).includes("D"),
    "Onboard mode shows car headed to destination, plus original pickup");
  assert(flatten(ride).some(x=>x.textContent==="AUTO → DESTINO"));
  const stale=await show(r,{available:true,driverLat:-34.81,driverLng:-56.04,gpsAgeSec:150});
  assert.equal(stale.dataset.liveDriver,"false");
  assert(!labels(stale).includes("🚘"),"Stale GPS may never appear as a moving car");
  assert(labels(stale).includes("O")&&labels(stale).includes("D"));
  assert(flatten(stale).some(x=>x.textContent==="RECORRIDO PREVISTO"));
  const absent=await show(r,{available:false,reason:"preparing_pickup"});
  assert(!labels(absent).includes("🚘"),"No GPS means no car marker");
  assert.equal(follow.coordinates(0,0),null);
  console.log("PASS real GPS tracking phases: private live car→pickup, onboard car→destination, honest static fallback");
})().catch(error=>{console.error(error);process.exitCode=1;});