import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import worker from '../src.mjs';

// Local stand-in for the D1 prepared statement API, backed by real SQLite.
const passenger='passenger-qa-test-token-'+ 'a'.repeat(48);
const driver='driver-qa-test-token-'+ 'b'.repeat(48);
const origin='https://quicktrip-qa.example.test';
const schema=readFileSync(new URL('../schema.sql',import.meta.url),'utf8');
class Statement {
  constructor(db,sql,args=[]){this.db=db;this.sql=sql;this.args=args;}
  bind(...args){return new Statement(this.db,this.sql,args);}
  async first(){return this.db.prepare(this.sql).get(...this.args)??null;}
  async all(){return {success:true,results:this.db.prepare(this.sql).all(...this.args)};}
  async run(){return {success:true,meta:this.db.prepare(this.sql).run(...this.args)};}
}
class LocalD1 {
  constructor(){this.db=new DatabaseSync(':memory:');this.db.exec(schema);}
  prepare(sql){return new Statement(this.db,sql);}
  close(){this.db.close();}
}
function harness(){
  const db=new LocalD1();
  const env={DB:db,QA_ALLOWED_ORIGIN:origin,QA_PASSENGER_TOKEN:passenger,QA_DRIVER_TOKEN:driver};
  let outgoing=0;
  const send=async (method,path,body=null,{role='passenger',from=origin,token}={})=>{
    const h={};if(from)h.origin=from;
    if(role!=='anonymous')h.authorization='Bearer '+(token??(role==='driver'?driver:passenger));
    if(body!==null)h['content-type']='application/json';
    const request=new Request('https://worker-qa.example.test'+path,{method,headers:h,
      body:body===null?undefined:JSON.stringify(body)});
    outgoing++;const response=await worker.fetch(request,env);
    return {status:response.status,headers:response.headers,body:await response.json()};
  };
  const request=(idempotencyKey=crypto.randomUUID(),fareUYU=126.27)=>({
    idempotencyKey,
    origin:{label:'Aeropuerto QA',lat:-34.84,lng:-56.02},
    destination:{label:'Centro QA',lat:-34.90,lng:-56.17},fareUYU
  });
  return {db,env,send,request,get outgoing(){return outgoing;}};
}

test('deny by default, wrong Origin and wrong credentials',async t=>{
  const h=harness();t.after(()=>h.db.close());
  assert.equal((await h.send('GET','/v1/driver/requests',null,{role:'anonymous'})).status,401);
  assert.equal((await h.send('GET','/v1/driver/requests',null,{role:'driver',from:'https://evil.test'})).status,403);
  assert.equal((await h.send('GET','/v1/driver/requests',null,{role:'driver',token:'wrong'})).status,401);
  const noConfig={...h.env,QA_DRIVER_TOKEN:''};
  const missing=await worker.fetch(new Request('https://worker-qa.example.test/health'),noConfig);
  assert.equal(missing.status,503);
});

test('CORS preflight exact origin; access restricted to roles',async t=>{
  const h=harness();t.after(()=>h.db.close());
  const pre=await worker.fetch(new Request('https://worker-qa.example.test/v1/requests',{method:'OPTIONS',headers:{origin,'access-control-request-method':'POST'}}),h.env);
  assert.equal(pre.status,204);assert.equal(pre.headers.get('access-control-allow-origin'),origin);
  const foreign=await worker.fetch(new Request('https://worker-qa.example.test/v1/requests',{method:'OPTIONS',headers:{origin:'https://evil.test'}}),h.env);
  assert.equal(foreign.status,403);assert.equal(foreign.headers.get('access-control-allow-origin'),null);
  assert.equal((await h.send('GET','/v1/driver/requests',null,{role:'passenger'})).status,403);
  assert.equal((await h.send('POST','/v1/requests',h.request(),{role:'driver'})).status,403);
});

test('create once, replay safely, conflicting payload fails, event is atomic',async t=>{
  const h=harness();t.after(()=>h.db.close());
  const p=h.request();
  const a=await h.send('POST','/v1/requests',p);
  assert.equal(a.status,201);assert.equal(a.body.request.state,'PENDIENTE');assert.equal(a.body.created,true);
  assert.equal(a.body.request.qaOnly,true);
  const b=await h.send('POST','/v1/requests',p);
  assert.equal(b.status,200);assert.equal(b.body.created,false);assert.equal(a.body.request.id,b.body.request.id);
  const bad=await h.send('POST','/v1/requests',h.request(p.idempotencyKey,300));
  assert.equal(bad.status,409);
  const events=h.db.db.prepare('SELECT * FROM qa_request_events').all();
  assert.equal(events.length,1);assert.equal(events[0].to_state,'PENDIENTE');
  assert.equal((await h.send('GET','/v1/requests/'+a.body.request.id)).status,200);
});

test('passenger → driver end-to-end transitions, version and event history',async t=>{
  const h=harness();t.after(()=>h.db.close());
  const initial=await h.send('POST','/v1/requests',h.request());
  const id=initial.body.request.id;
  const list=await h.send('GET','/v1/driver/requests',null,{role:'driver'});
  assert.equal(list.status,200);assert.equal(list.body.requests.length,1);
  const steps=['ACEPTADA','EN_CAMINO','EN_PUNTO','EN_VIAJE','FINALIZADA'];
  for(let i=0;i<steps.length;i++){
    const result=await h.send('POST',`/v1/requests/${id}/transition`,{to:steps[i],expectedVersion:i+1},{role:'driver'});
    assert.equal(result.status,200,JSON.stringify(result.body));
    assert.equal(result.body.request.version,i+2);
    assert.equal(result.body.request.state,steps[i]);
  }
  const view=await h.send('GET','/v1/requests/'+id);
  assert.equal(view.status,200);assert.equal(view.body.events.length,6);
  assert.equal(view.body.events[0].from_state,null);
  assert.equal(view.body.events[5].to_state,'FINALIZADA');
  assert.equal((await h.send('POST',`/v1/requests/${id}/transition`,{to:'FINALIZADA',expectedVersion:5},{role:'driver'})).status,409);
});

test('reject, invalid transition, expired request and single event',async t=>{
  const h=harness();t.after(()=>h.db.close());
  const a=await h.send('POST','/v1/requests',h.request());const id=a.body.request.id;
  assert.equal((await h.send('POST',`/v1/requests/${id}/transition`,{to:'EN_VIAJE',expectedVersion:1},{role:'driver'})).status,409);
  const reject=await h.send('POST',`/v1/requests/${id}/transition`,{to:'RECHAZADA',expectedVersion:1},{role:'driver'});
  assert.equal(reject.status,200);
  assert.equal((await h.send('POST',`/v1/requests/${id}/transition`,{to:'ACEPTADA',expectedVersion:1},{role:'driver'})).status,409);
  assert.equal(h.db.db.prepare('SELECT COUNT(*) n FROM qa_request_events WHERE request_id=?').get(id).n,2);
  const late=await h.send('POST','/v1/requests',h.request());const lateId=late.body.request.id;
  h.db.db.prepare('UPDATE qa_requests SET expires_at=? WHERE id=?').run('2000-01-01T00:00:00.000Z',lateId);
  assert.equal((await h.send('POST',`/v1/requests/${lateId}/transition`,{to:'ACEPTADA',expectedVersion:1},{role:'driver'})).status,404);
  const current=await h.send('GET','/v1/driver/requests',null,{role:'driver'});
  assert.equal(current.body.requests.some(x=>x.id===lateId),false);
});

test('passenger may cancel only before pickup; no driver token leaked',async t=>{
  const h=harness();t.after(()=>h.db.close());
  const a=await h.send('POST','/v1/requests',h.request());const id=a.body.request.id;
  const cancel=await h.send('POST',`/v1/requests/${id}/transition`,{to:'CANCELADA',expectedVersion:1});
  assert.equal(cancel.status,200);
  assert.equal(cancel.body.request.state,'CANCELADA');
  const output=JSON.stringify(cancel.body);
  assert.equal(output.includes(passenger),false);assert.equal(output.includes(driver),false);
});
