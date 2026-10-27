/* Quick Trip Live — QA only. Uses existing Worker authentication and D1; no production calls. */
const ACTIVE = "('ACEPTADA','EN_CAMINO','EN_PUNTO','EN_VIAJE')";
const MAX_AGE_MS = 45_000;
const MAX_SAMPLE_LAG_MS = 15_000;
const MAX_FUTURE_SKEW_MS = 2_000;
const fail = (status,code) => { const e=new Error(code); e.status=status; e.code=code; throw e; };
const finite = n => typeof n === 'number' && Number.isFinite(n);
const iso = s => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(s) && Number.isFinite(Date.parse(s)) && new Date(s).toISOString()===s;
const points = p => p && typeof p==='object' && !Array.isArray(p) &&
  finite(p.lat) && p.lat>=-35.5 && p.lat<=-30 && finite(p.lng) && p.lng>=-59.5 && p.lng<=-52 &&
  finite(p.accuracyM) && p.accuracyM>0 && p.accuracyM<=50 &&
  (p.headingDeg==null || (finite(p.headingDeg) && p.headingDeg>=0 && p.headingDeg<360)) &&
  Number.isSafeInteger(p.seq) && p.seq>0 && iso(p.measuredAt);

/** Return null for unrelated paths. Caller already enforced HTTPS, origin, config, Bearer auth and JSON errors. */
export async function handleTracking({parts,request,env,who,readJSON,visible}) {
  if (parts.length!==4 || parts[0]!=='v1' || parts[1]!=='requests' ||
      !/^[a-f\d]{8}-[a-f\d]{4}-[1-8][a-f\d]{3}-[89ab][a-f\d]{3}-[a-f\d]{12}$/i.test(parts[2]) ||
      !['location','tracking-consent'].includes(parts[3])) return null;
  const id=parts[2], resource=parts[3];
  if (!((resource==='tracking-consent' && request.method==='POST') ||
        (resource==='location' && ['GET','POST'].includes(request.method)))) fail(405,'method_not_allowed');
  const row=await env.DB.prepare('SELECT * FROM qa_requests WHERE id=?').bind(id).first();
  visible(row,who);
  const active=['ACEPTADA','EN_CAMINO','EN_PUNTO','EN_VIAJE'].includes(row.state);
  if(resource==='tracking-consent') {
    if(who.role!=='driver') fail(403,'driver_only');
    const payload=await readJSON(request);
    if(!payload || typeof payload.enabled!=='boolean' || !Number.isSafeInteger(payload.expectedVersion) || payload.expectedVersion<1)
      fail(400,'invalid_consent');
    if(!active || row.driver_ref!==who.ref) fail(409,'tracking_not_active');
    // Atomic authorization against the current request version and state.
    const consent=await env.DB.prepare(`INSERT INTO qa_tracking_consent(request_id,driver_ref,enabled,updated_at)
      SELECT id,driver_ref,?,? FROM qa_requests WHERE id=? AND driver_ref=? AND version=?
      AND state IN ${ACTIVE}
      ON CONFLICT(request_id) DO UPDATE SET driver_ref=excluded.driver_ref,
        enabled=excluded.enabled,updated_at=excluded.updated_at RETURNING enabled`)
      .bind(payload.enabled?1:0,new Date().toISOString(),id,who.ref,payload.expectedVersion).first();
    if(!consent) fail(409,'version_conflict');
    if(!payload.enabled) await env.DB.prepare('DELETE FROM qa_tracking_position WHERE request_id=?').bind(id).run();
    return {status:200,body:{qaOnly:true,trackingEnabled:!!consent.enabled}};
  }
  if(request.method==='POST') {
    if(who.role!=='driver') fail(403,'driver_only');
    if(!active || row.driver_ref!==who.ref) fail(409,'tracking_not_active');
    const p=await readJSON(request);
    if(!points(p)) fail(422,'invalid_gps');
    const age=Date.now()-Date.parse(p.measuredAt);
    if(age>MAX_SAMPLE_LAG_MS || age< -MAX_FUTURE_SKEW_MS) fail(422,'stale_gps');
    const received=new Date().toISOString();
    const expires=new Date(Date.now()+MAX_AGE_MS).toISOString();
    // One SQLite statement: new consent/state checks and monotonic sequencing.
    // Race-safe against consent revocation or request finalization.
    const saved=await env.DB.prepare(`INSERT INTO qa_tracking_position
      (request_id,seq,lat,lng,accuracy_m,heading_deg,measured_at,received_at,expires_at)
      SELECT r.id,?,?,?,?,?,?,?,? FROM qa_requests r
      JOIN qa_tracking_consent c ON c.request_id=r.id
      WHERE r.id=? AND r.driver_ref=? AND r.state IN ${ACTIVE} AND c.enabled=1
      ON CONFLICT(request_id) DO UPDATE SET
        seq=excluded.seq,lat=excluded.lat,lng=excluded.lng,accuracy_m=excluded.accuracy_m,
        heading_deg=excluded.heading_deg,measured_at=excluded.measured_at,
        received_at=excluded.received_at,expires_at=excluded.expires_at
      WHERE excluded.seq>qa_tracking_position.seq
        AND excluded.measured_at>=qa_tracking_position.measured_at RETURNING seq`)
      .bind(p.seq,p.lat,p.lng,p.accuracyM,p.headingDeg??null,p.measuredAt,received,expires,id,who.ref).first();
    if(!saved) fail(409,'tracking_disabled_or_old_sample');
    return {status:200,body:{qaOnly:true,acceptedSeq:saved.seq}};
  }
  if(who.role!=='passenger') fail(403,'passenger_only');
  // GPS is restricted to the request owner, accepted/active, consented, and fresh.
  // Return no coordinates for expired, cancelled, or disabled tracking.
  const position=await env.DB.prepare(`SELECT p.seq,p.lat,p.lng,p.accuracy_m,p.heading_deg,p.measured_at,
      p.received_at,p.expires_at FROM qa_tracking_position p
      JOIN qa_tracking_consent c ON c.request_id=p.request_id AND c.enabled=1
      JOIN qa_requests r ON r.id=p.request_id AND r.state IN ${ACTIVE}
      WHERE p.request_id=? AND r.passenger_ref=? AND p.expires_at>?`)
    .bind(id,who.ref,new Date().toISOString()).first();
  if(!position) return {status:200,body:{available:false,qaOnly:true}};
  return {status:200,body:{available:true,qaOnly:true,seq:position.seq,
    driverLat:position.lat,driverLng:position.lng,accuracyM:position.accuracy_m,
    headingDeg:position.heading_deg,measuredAt:position.measured_at,
    expiresAt:position.expires_at}};
}
