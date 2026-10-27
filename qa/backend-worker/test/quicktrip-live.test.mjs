import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {handleTracking} from '../tracking.mjs';

const schema=readFileSync(new URL('../schema.sql',import.meta.url),'utf8');
const digest=async s=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s))),
  n=>n.toString(16).padStart(2,'0')).join('');
const id='15cd1e14-bbe2-4d5d-8c7a-c809ef5eb389';
const format={ origin:'https://quicktrip-qa.example.test', driver:'qa-test-driver', passenger:'qa-test-passenger' };
class Statement {
  constructor(db,sql,args=[]){this.db=db;this.sql=sql;this.args=args;}
  bind(...a){return new Statement(this.db,this.sql,a);}
  async first(){return this.db.prepare(this.sql).get(...this.args)??null;}
  async run(){return {success:true,meta:this.db.prepare(this.sql).run(...this.args)};}
}
class DB {
  constructor(){this.raw=new DatabaseSync(':memory:');this.raw.exec(schema);}
  prepare(sql){return new Statement(this.raw,sql);}
  close(){this.raw.close();}
}
async function harness(){
  const db=new DB(), passenger=await digest(format.passenger),driver=await digest(format.driver);
  const now=new Date().toISOString(),later=new Date(Date.now()+60_000).toISOString();
  db.raw.prepare(`INSERT INTO qa_requests(id,passenger_ref,idempotency_key,payload_hash,driver_ref,state,version,
    origin_label,destination_label,origin_lat,origin_lng,destination_lat,destination_lng,fare_uyu,
    last_actor_role,created_at,updated_at,expires_at)
    VALUES (?,?,?,?,?,'ACEPTADA',2,'Origen QA','Destino QA',-34.9,-56.1,-34.91,-56.13,400,'driver',?,?,?)`)
    .run(id,passenger,crypto.randomUUID(),'f'.repeat(64),driver,now,now,later);
  async function send(method,suffix,{body=null,role='driver',ref}={}){
    const who={role,ref:ref??(role==='driver'?driver:passenger)};
    const request=new Request('https://quicktrip-qa.example.test/v1/requests/'+id+'/'+suffix,
      {method,headers:body?{'content-type':'application/json'}:{},body:body?JSON.stringify(body):undefined});
    try{
      const result=await handleTracking({parts:['v1','requests',id,suffix],request,env:{DB:db},who,
        readJSON:async r=>r.json(),visible:(row,identity)=>{
          if(!row||(identity.role==='passenger'&&row.passenger_ref!==identity.ref)||
             (identity.role==='driver'&&row.driver_ref!==identity.ref)){
            const e=new Error('not_found');e.status=404;throw e;
          }
        }});
      return result;
    }catch(e){return {status:e.status||500,body:{error:e.code||e.message}};}
  }
  const sample=(seq=1,overrides={})=>({lat:-34.9,lng:-56.1,accuracyM:8,headingDeg:170,
    seq,measuredAt:new Date().toISOString(),...overrides});
  return {db,send,sample,passenger,driver};
}
const consent=(enabled,expectedVersion=2)=>({enabled,expectedVersion});

test('no position without request-level consent, and cross-role reads are denied',async t=>{
  const h=await harness();t.after(()=>h.db.close());
  assert.equal((await h.send('GET','location',{role:'passenger'})).body.available,false);
  assert.equal((await h.send('POST','location',{body:h.sample()})).status,409);
  assert.equal((await h.send('GET','location',{role:'driver'})).status,403);
  assert.equal((await h.send('GET','location',{role:'passenger',ref:'other'})).status,404);
  assert.equal((await h.send('POST','tracking-consent',{role:'passenger',body:consent(true)})).status,403);
  assert.equal((await h.send('POST','tracking-consent',{body:consent(true,1)})).status,409);
});

test('authorized fresh GPS updates and monotonic sequence; no historical trail',async t=>{
  const h=await harness();t.after(()=>h.db.close());
  assert.equal((await h.send('POST','tracking-consent',{body:consent(true)})).status,200);
  assert.equal((await h.send('POST','location',{body:h.sample(1)})).status,200);
  const current=await h.send('GET','location',{role:'passenger'});
  assert.equal(current.body.available,true);assert.equal(current.body.driverLat,-34.9);
  assert.equal(current.body.driverLng,-56.1);assert.equal(current.body.seq,1);
  assert.equal(current.body.qaOnly,true);assert.equal('driverRef' in current.body,false);
  assert.equal((await h.send('POST','location',{body:h.sample(1)})).status,409);
  assert.equal((await h.send('POST','location',{body:h.sample(2,{lat:-34.901})})).status,200);
  assert.equal(h.db.raw.prepare('SELECT count(*) n FROM qa_tracking_position').get().n,1);
  assert.equal((await h.send('GET','location',{role:'passenger'})).body.seq,2);
});

test('invalid and old GPS is refused; expiry hides the car',async t=>{
  const h=await harness();t.after(()=>h.db.close());
  await h.send('POST','tracking-consent',{body:consent(true)});
  assert.equal((await h.send('POST','location',{body:h.sample(1,{lat:9})})).status,422);
  assert.equal((await h.send('POST','location',{body:h.sample(1,{accuracyM:80})})).status,422);
  assert.equal((await h.send('POST','location',{body:h.sample(1,{measuredAt:'2026-99-99T99:99:99.000Z'})})).status,422);
  assert.equal((await h.send('POST','location',{body:h.sample(1,{measuredAt:new Date(Date.now()-90_000).toISOString()})})).status,422);
  assert.equal((await h.send('POST','location',{body:h.sample(1,{measuredAt:new Date(Date.now()+90_000).toISOString()})})).status,422);
  await h.send('POST','location',{body:h.sample()});
  h.db.raw.prepare('UPDATE qa_tracking_position SET expires_at=? WHERE request_id=?')
    .run(new Date(Date.now()-1000).toISOString(),id);
  assert.equal((await h.send('GET','location',{role:'passenger'})).body.available,false);
});

test('revocation and terminal transition clear location and prevent future writes',async t=>{
  const h=await harness();t.after(()=>h.db.close());
  await h.send('POST','tracking-consent',{body:consent(true)});
  await h.send('POST','location',{body:h.sample(1)});
  assert.equal((await h.send('POST','tracking-consent',{body:consent(false)})).status,200);
  assert.equal(h.db.raw.prepare('SELECT count(*) n FROM qa_tracking_position').get().n,0);
  assert.equal((await h.send('GET','location',{role:'passenger'})).body.available,false);
  assert.equal((await h.send('POST','location',{body:h.sample(2)})).status,409);
  await h.send('POST','tracking-consent',{body:consent(true)});
  await h.send('POST','location',{body:h.sample(3)});
  h.db.raw.prepare("UPDATE qa_requests SET state='FINALIZADA',version=3 WHERE id=?").run(id);
  assert.equal(h.db.raw.prepare('SELECT count(*) n FROM qa_tracking_position').get().n,0);
  assert.equal(h.db.raw.prepare('SELECT enabled FROM qa_tracking_consent').get().enabled,0);
  assert.equal((await h.send('GET','location',{role:'passenger'})).body.available,false);
  assert.equal((await h.send('POST','tracking-consent',{body:consent(true,3)})).status,409);
});
