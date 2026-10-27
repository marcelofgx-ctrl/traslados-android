import {handleTracking} from './tracking.mjs';
/* Quick Trip v90 QA only. Worker+D1 HTTP adapter. Never calls Supabase. */
const STATES = new Set(['PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA','EN_CAMINO','EN_PUNTO','EN_VIAJE','FINALIZADA']);
const DRIVER_STEPS = Object.freeze({
  PENDIENTE: new Set(['ACEPTADA','RECHAZADA']),
  ACEPTADA: new Set(['EN_CAMINO']),
  EN_CAMINO: new Set(['EN_PUNTO']),
  EN_PUNTO: new Set(['EN_VIAJE']),
  EN_VIAJE: new Set(['FINALIZADA'])
});
const PASSENGER_STEPS = Object.freeze({
  PENDIENTE: new Set(['CANCELADA']),
  ACEPTADA: new Set(['CANCELADA']),
  EN_CAMINO: new Set(['CANCELADA'])
});
const encoder = new TextEncoder();
const hex = bytes => Array.from(new Uint8Array(bytes), v => v.toString(16).padStart(2,'0')).join('');
const sha = async value => hex(await crypto.subtle.digest('SHA-256', encoder.encode(value)));
const nowISO = () => new Date().toISOString();
const fatal = (status,code) => Object.assign(new Error(code),{status,code});
const uuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

// Constant-time-ish digest comparison: never early-return on mismatched bytes.
async function sameToken(received, expected) {
  if(typeof expected !== 'string' || expected.length < 32 || typeof received !== 'string') return false;
  const a = new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(received)));
  const b = new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(expected)));
  let diff = 0;
  for(let i=0;i<32;i++) diff |= a[i] ^ b[i];
  return diff === 0;
}
function cors(origin,env) {
  const permitted = typeof env.QA_ALLOWED_ORIGIN === 'string' && env.QA_ALLOWED_ORIGIN.startsWith('https://')
    && new URL(env.QA_ALLOWED_ORIGIN).origin === env.QA_ALLOWED_ORIGIN;
  const accepted = permitted && origin === env.QA_ALLOWED_ORIGIN;
  return {permitted,accepted};
}
function json(body,status=200,origin=null,env=null) {
  const h = new Headers({'content-type':'application/json; charset=utf-8','cache-control':'no-store',
    'x-content-type-options':'nosniff','referrer-policy':'no-referrer',
    'content-security-policy':"default-src 'none'; frame-ancestors 'none'"});
  if(env && cors(origin,env).accepted) {
    h.set('access-control-allow-origin',origin);
    h.set('vary','Origin');
    h.set('access-control-allow-headers','authorization, content-type');
    h.set('access-control-allow-methods','GET, POST, OPTIONS');
  }
  return new Response(JSON.stringify(body),{status,headers:h});
}
function checkedString(s,max=160) {return typeof s === 'string' && s.trim().length>0 && s.trim().length<=max ? s.trim() : null;}
function coords(lat,lng) {return Number.isFinite(lat)&&Number.isFinite(lng)&&lat>=-35.5&&lat<=-30&&lng>=-59.5&&lng<=-52;}
function sanitize(p) {
  if(!p || typeof p !== 'object' || Array.isArray(p)) throw fatal(400,'invalid_payload');
  const originLabel=checkedString(p.origin?.label),destinationLabel=checkedString(p.destination?.label);
  const a=p.origin, b=p.destination;
  if(!originLabel||!destinationLabel||!coords(a.lat,a.lng)||!coords(b.lat,b.lng)
    || typeof p.fareUYU !== 'number' || !Number.isFinite(p.fareUYU)
    || p.fareUYU < 0 || p.fareUYU > 500000 || !uuid(p.idempotencyKey)) throw fatal(400,'invalid_payload');
  const fare=Math.round(p.fareUYU*100)/100;
  // Only user-selected QA fixture coordinates. No background GPS / identities.
  return {origin:{label:originLabel,lat:a.lat,lng:a.lng},destination:{label:destinationLabel,lat:b.lat,lng:b.lng},
    fareUYU:fare,idempotencyKey:p.idempotencyKey.toLowerCase()};
}
function render(row){return {
  id:row.id,state:row.state,version:row.version,origin:{label:row.origin_label,lat:row.origin_lat,lng:row.origin_lng},
  destination:{label:row.destination_label,lat:row.destination_lat,lng:row.destination_lng},
  fareUYU:row.fare_uyu,createdAt:row.created_at,updatedAt:row.updated_at,
  expiresAt:row.expires_at,qaOnly:true
};}
async function readJSON(request){
  if(!/^application\/json(?:\s*;|$)/i.test(request.headers.get('content-type')||''))throw fatal(415,'json_required');
  if(Number(request.headers.get('content-length')||0)>12000)throw fatal(413,'body_too_large');
  const s=await request.text();if(s.length>12000)throw fatal(413,'body_too_large');
  try{return JSON.parse(s);}catch{throw fatal(400,'invalid_json');}
}
async function auth(request,env) {
  const h=request.headers.get('authorization')||'';
  if(!h.startsWith('Bearer ')||h.length>256)throw fatal(401,'unauthorized');
  const token=h.slice(7);
  const [pass,driver]=await Promise.all([sameToken(token,env.QA_PASSENGER_TOKEN),sameToken(token,env.QA_DRIVER_TOKEN)]);
  if(pass===driver)throw fatal(401,'unauthorized');
  return {role:pass?'passenger':'driver',ref:await sha(token)};
}
function visible(row,identity){
  if(!row)throw fatal(404,'not_found');
  if(identity.role==='passenger'&&row.passenger_ref!==identity.ref)throw fatal(404,'not_found');
  if(identity.role==='driver'&&row.state==='PENDIENTE'&&row.expires_at<=nowISO())throw fatal(404,'not_found');
  if(identity.role==='driver'&&row.state!=='PENDIENTE'&&row.driver_ref!==identity.ref)throw fatal(404,'not_found');
}
async function handle(request,env) {
  const url=new URL(request.url),origin=request.headers.get('origin');
  if(url.protocol!=='https:' && !['localhost','127.0.0.1'].includes(url.hostname)) throw fatal(400,'https_required');
  if(origin && !cors(origin,env).accepted)throw fatal(403,'origin_denied');
  if(request.method==='OPTIONS') {
    if(!origin)throw fatal(403,'origin_denied');
    return new Response(null,{status:204,headers:{'access-control-allow-origin':origin,
      'access-control-allow-headers':'authorization, content-type',
      'access-control-allow-methods':'GET, POST, OPTIONS', 'vary':'Origin',
      'access-control-max-age':'300','cache-control':'no-store'}});
  }
  if(!env.DB||typeof env.DB.prepare!=='function'||!cors(null,env).permitted ||
    !env.QA_PASSENGER_TOKEN||!env.QA_DRIVER_TOKEN ||
    env.QA_PASSENGER_TOKEN===env.QA_DRIVER_TOKEN ||
    env.QA_PASSENGER_TOKEN.length<32||env.QA_DRIVER_TOKEN.length<32)
    throw fatal(503,'qa_not_configured');
  if(url.pathname==='/health'&&request.method==='GET')return {status:200,body:{ok:true,qaOnly:true}};
  if(url.search)throw fatal(400,'query_not_supported');
  const who=await auth(request,env);
  const parts=url.pathname.split('/').filter(Boolean);
  if(parts[0]!=='v1')throw fatal(404,'not_found');
  const tracked=await handleTracking({parts,request,env,who,readJSON,visible});
  if(tracked)return tracked;
  if(parts.length===2&&parts[1]==='requests'&&request.method==='POST'){
    if(who.role!=='passenger')throw fatal(403,'passenger_only');
    const body=sanitize(await readJSON(request));
    const fingerprint=await sha(JSON.stringify({origin:body.origin,destination:body.destination,fareUYU:body.fareUYU}));
    const current=nowISO(),id=crypto.randomUUID(),expires=new Date(Date.now()+20*60_000).toISOString();
    const prior=await env.DB.prepare('SELECT * FROM qa_requests WHERE passenger_ref=? AND idempotency_key=?')
      .bind(who.ref,body.idempotencyKey).first();
    if(prior){
      if(prior.payload_hash!==fingerprint)throw fatal(409,'idempotency_conflict');
      return {status:200,body:{request:render(prior),created:false}};
    }
    const count=await env.DB.prepare("SELECT count(*) AS n FROM qa_requests WHERE passenger_ref=? AND created_at >= ?")
      .bind(who.ref,new Date(Date.now()-60*60_000).toISOString()).first();
    if((count?.n||0)>=12)throw fatal(429,'qa_rate_limit');
    const inserted=await env.DB.prepare(`INSERT INTO qa_requests
      (id,passenger_ref,idempotency_key,payload_hash,origin_label,destination_label,
       origin_lat,origin_lng,destination_lat,destination_lng,fare_uyu,last_actor_role,created_at,updated_at,expires_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(passenger_ref,idempotency_key) DO NOTHING RETURNING *`)
      .bind(id,who.ref,body.idempotencyKey,fingerprint,body.origin.label,body.destination.label,
        body.origin.lat,body.origin.lng,body.destination.lat,body.destination.lng,body.fareUYU,
        'passenger',current,current,expires).first();
    if(inserted)return {status:201,body:{request:render(inserted),created:true}};
    const existing=await env.DB.prepare('SELECT * FROM qa_requests WHERE passenger_ref=? AND idempotency_key=?')
      .bind(who.ref,body.idempotencyKey).first();
    if(!existing||existing.payload_hash!==fingerprint)throw fatal(409,'idempotency_conflict');
    return {status:200,body:{request:render(existing),created:false}};
  }
  if(parts.length===3&&parts[1]==='driver'&&parts[2]==='requests'&&request.method==='GET'){
    if(who.role!=='driver')throw fatal(403,'driver_only');
    const rows=await env.DB.prepare(`SELECT * FROM qa_requests
      WHERE (state='PENDIENTE' AND expires_at>?) OR driver_ref=?
      ORDER BY created_at DESC LIMIT 25`).bind(nowISO(),who.ref).all();
    return {status:200,body:{requests:(rows.results||[]).map(render),qaOnly:true}};
  }
  if(parts.length>=3&&parts[1]==='requests'&&uuid(parts[2])){
    const id=parts[2];
    const row=await env.DB.prepare('SELECT * FROM qa_requests WHERE id=?').bind(id).first();
    visible(row,who);
    if(parts.length===3&&request.method==='GET'){
      const ev=await env.DB.prepare('SELECT version,actor_role,from_state,to_state,created_at FROM qa_request_events WHERE request_id=? ORDER BY version ASC LIMIT 15')
        .bind(id).all();
      return {status:200,body:{request:render(row),events:ev.results||[]}};
    }
    if(parts.length===4&&parts[3]==='transition'&&request.method==='POST'){
      const payload=await readJSON(request);
      if(!payload||typeof payload!=='object'||!STATES.has(payload.to)||!Number.isSafeInteger(payload.expectedVersion)
        ||payload.expectedVersion<1)throw fatal(400,'invalid_transition');
      const choices=who.role==='driver'?DRIVER_STEPS:PASSENGER_STEPS;
      if(!choices[row.state]?.has(payload.to))throw fatal(409,'transition_not_allowed');
      if(row.expires_at<=nowISO() && row.state==='PENDIENTE')throw fatal(409,'request_expired');
      if(row.version!==payload.expectedVersion)throw fatal(409,'version_conflict');
      const assigned=(who.role==='driver'&&row.state==='PENDIENTE')?who.ref:row.driver_ref;
      const updated=await env.DB.prepare(`UPDATE qa_requests SET state=?,version=version+1,driver_ref=?,last_actor_role=?,updated_at=?
        WHERE id=? AND state=? AND version=? AND (state<>'PENDIENTE' OR expires_at>?)
          AND (driver_ref IS NULL OR driver_ref=?) RETURNING *`)
        .bind(payload.to,assigned,who.role,nowISO(),id,row.state,payload.expectedVersion,nowISO(),who.ref).first();
      if(!updated)throw fatal(409,'concurrent_update');
      return {status:200,body:{request:render(updated),qaOnly:true}};
    }
  }
  throw fatal(404,'not_found');
}
export default {
  async fetch(request,env){
    const origin=request.headers.get('origin');
    try{
      const v=await handle(request,env);
      if(v instanceof Response)return v;
      return json(v.body,v.status,origin,env);
    }catch(error){
      if(!error.status){console.error('QA Worker internal error:',error?.name||'error');}
      return json({error:error.code||'internal_error',qaOnly:true},error.status||500,origin,env);
    }
  }
};
