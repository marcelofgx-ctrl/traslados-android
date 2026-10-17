"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const path=require("node:path");
const web=path.resolve(__dirname,"..");
function element(){
  return {children:[],dataset:{},attrs:{},isConnected:true,
    textContent:"",className:"",
    classList:{add(){}},
    append(node){this.children.push(node);},
    setAttribute(k,v){this.attrs[k]=v;}
  };
}
const window={};
vm.runInNewContext(fs.readFileSync(path.join(web,"reservation-live.js"),"utf8"),
  {window,document:{createElement:element},console});
const mod=window.TrasladosReservationLive;
assert.ok(mod&&typeof mod.mount==="function");
assert.deepEqual(Object.keys(mod.coordinates(-34.85,-56.15)).sort(),["lat","lng"]);
assert.equal(mod.coordinates(0,0),null);
assert.equal(mod.coordinates(null,undefined),null);
const app=fs.readFileSync(path.join(web,"app.js"),"utf8");
const html=fs.readFileSync(path.join(web,"index.html"),"utf8");
const css=fs.readFileSync(path.join(web,"premium-journey.css"),"utf8");
assert.match(html,/reservation-live\.js\?v=2/);
assert.match(app,/customer_reservation_position_v1|TrasladosReservationLive\?\.mount/);
assert.match(app,/firstConfirmation/);
assert.match(css,/reservation-live-map/);
let seen=[],routes=[];
const r={id:"ride-1",code:"T-1",status:"ACEPTADA",
  origin_lat:-34.79,origin_lng:-56.01,destination_lat:-34.84,destination_lng:-56.08};
const road={
  async estimate(points){routes.push(points);return {geometry:points.map(p=>[p.lat,p.lng]),
    distanceKm:7.4,durationMin:9};},
  verified(x){return Array.isArray(x.geometry);}
};
const renderer={render(){return true;}};
const tick=()=>new Promise(done=>setTimeout(done,30));
(async()=>{
  let host=element();
  mod.mount(host,{reservation:r,sessionToken:"auth-1",rpc:async (name,args)=>{
    assert.equal(name,"customer_reservation_position_v1");
    assert.equal(args.p_reservation_id,"ride-1");
    assert.equal(args.p_session_token,"auth-1");
    return {available:false,reason:"preparing_pickup"};
  },engine:road,renderer,api:"example",key:"public-key"});
  await tick();
  assert.equal(routes.length,1,"accepted ride must show verified booked route in same card");
  assert.equal(routes[0][0].lat,-34.79);
  assert.ok(host.children.some(n=>n.className==="reservation-live-map"));
  host=element();
  mod.mount(host,{reservation:{...r,pickup_phase:"EN_CAMINO"},sessionToken:"auth-1",
    rpc:async ()=>({available:true,driverLat:-34.81,driverLng:-56.04,gpsAgeSec:9}),
    engine:road,renderer,api:"example",key:"public-key"});
  await tick();
  assert.equal(routes.length,2,"authenticated live vehicle route must replace the booked route");
  assert.equal(routes[1][0].lat,-34.81);
  let unreachable=element();let contacted=false;
  mod.mount(unreachable,{reservation:{...r,status:"PENDIENTE"},rpc:async()=>{
    contacted=true;return {available:true,driverLat:0,driverLng:0};
  }});
  await tick();
  assert.equal(contacted,false,"no GPS fetch before acceptance");
  console.log("PASS accepted inline map, authorized vehicle route, no GPS for pending trip");
})().catch(e=>{console.error(e);process.exitCode=1;});
