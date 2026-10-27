'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const meta={ 'quicktrip-qa-isolation':'v90-route-sandbox-test','traslados-preview':'read-only' };
global.document={
  querySelector:s=>{const k=s.match(/meta\[name="([^"]+)"\]/)?.[1];return meta[k]?{content:meta[k]}:null;},
  createElement:()=>({dataset:{}})
};
const live=require('../quicktrip-live-qa.js');
const id='15cd1e14-bbe2-4d5d-8c7a-c809ef5eb389';
const record={id,state:'EN_CAMINO',origin:{lat:-34.9,lng:-56.1},destination:{lat:-34.91,lng:-56.13}};
const token='q'.repeat(40);
const position=(seq=1)=>({qaOnly:true,available:true,seq,driverLat:-34.9001,driverLng:-56.1002,
  accuracyM:12,measuredAt:new Date().toISOString(),expiresAt:new Date(Date.now()+40_000).toISOString()});

test('only isolated QA preview, never production or insecure hosts',()=>{
  delete meta['quicktrip-qa-isolation'];
  assert.throws(()=>live.createRpc({backendURL:'https://quicktrip-qa.example.test/',tokenProvider:async()=>token,fetchImpl:async()=>{}}),/qa_preview_only/);
  meta['quicktrip-qa-isolation']='v90-route-sandbox-test';
  for(const backendURL of ['https://zetaudvvutlouiqxopvg.supabase.co/','https://production.example.com/',
     'http://quicktrip-qa.example.test/','https://quicktrip-qa.example.test/path']){
    assert.throws(()=>live.createRpc({backendURL,tokenProvider:async()=>token,fetchImpl:async()=>{}}),/qa_(endpoint_|https_required)/);
  }
});

test('private, no-cache GET with token provider; maps only verified position',async()=>{
  let calls=0;
  const rpc=live.createRpc({backendURL:'https://quicktrip-qa.example.test/',tokenProvider:async()=>token,
    fetchImpl:async(url,opts)=>{
      calls++;
      assert.equal(url,`https://quicktrip-qa.example.test/v1/requests/${id}/location`);
      assert.equal(opts.cache,'no-store');assert.equal(opts.credentials,'omit');
      assert.equal(opts.headers.Authorization,'Bearer '+token);
      return {ok:true,json:async()=>position()};
    }});
  const p=await rpc('customer_reservation_position_v1',{p_reservation_id:id});
  assert.equal(p.available,true);assert.equal(p.driverLat,-34.9001);assert.equal(calls,1);
  await assert.rejects(()=>rpc('driver_list_reservations_v3',{p_reservation_id:id}),/qa_tracking_contract_only/);
});

test('old, low-quality, out-of-sequence and unavailable GPS fail closed',async()=>{
  const values=[position(2),position(1),{...position(3),accuracyM:120},
    {...position(4),expiresAt:new Date(Date.now()-1000).toISOString()}, {qaOnly:true,available:false}];
  const rpc=live.createRpc({backendURL:'https://quicktrip-qa.example.test/',tokenProvider:async()=>token,
    fetchImpl:async()=>({ok:true,json:async()=>values.shift()})});
  assert.equal((await rpc('customer_reservation_position_v1',{p_reservation_id:id})).available,true);
  for(let i=0;i<4;i++)assert.equal((await rpc('customer_reservation_position_v1',{p_reservation_id:id})).available,false);
});

test('state adapter uses actual accepted request and shared rendering module',()=>{
  assert.equal(live.normalized({...record,state:'PENDIENTE'}),null);
  assert.equal(live.normalized(record).pickup_phase,'EN_CAMINO');
  assert.equal(live.normalized({...record,state:'EN_PUNTO'}).pickup_phase,'EN_PUNTO');
  assert.equal(live.normalized({...record,state:'EN_VIAJE'}).status,'EN_VIAJE');
  let opts;
  global.TrasladosReservationLive={mount:(_node,config)=>{opts=config}};
  const host={replaceChildren(node){this.child=node}};
  const container=live.mount(host,{request:record,backendURL:'https://quicktrip-qa.example.test/',
    tokenProvider:async()=>token,fetchImpl:async()=>({ok:true,json:async()=>position()}),renderer:{render(){}}});
  assert.equal(host.child,container);assert.equal(opts.reservation.status,'ACEPTADA');
  assert.equal(typeof opts.rpc,'function');assert.equal(opts.sessionToken,'QA_ADAPTER_AUTHORIZED_BY_RPC');
});
