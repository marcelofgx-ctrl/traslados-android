"use strict";
/* Quick Trip Live QA adapter. Deliberately not loaded by production or v90 HTML.
 * Explicit opt-in only; reuses ReservationLive + existing verified route renderer.
 * No writes, no token embedded, no polling of private GPS without accepted request.
 */
(function(root,factory){
  const api=factory(root);
  if(typeof module==='object' && module.exports) module.exports=api;
  else root.QuickTripLiveQA=api;
})(typeof window!=='undefined'?window:globalThis,function(root){
  const states=new Set(['ACEPTADA','EN_CAMINO','EN_PUNTO','EN_VIAJE']);
  function qaPage(){
    const doc=root.document;
    return !!doc?.querySelector('meta[name="quicktrip-qa-isolation"]')?.content.includes('v90-route-sandbox') &&
      doc.querySelector('meta[name="traslados-preview"]')?.content==='read-only';
  }
  function endpointOf(value){
    let url;try{url=new URL(value)}catch{throw Error('qa_endpoint_required')}
    const local=['localhost','127.0.0.1'].includes(url.hostname);
    if(!url.hostname.includes('quicktrip-qa') && !url.hostname.includes('quicktrip-live-qa') && !local)
      throw Error('qa_endpoint_only');
    if(url.protocol!=='https:' && !(local&&url.protocol==='http:'))throw Error('qa_https_required');
    if(url.username||url.password||url.search||url.hash||url.pathname!=='/')throw Error('qa_endpoint_invalid');
    return url.origin;
  }
  function normalized(record){
    if(!record || !states.has(record.state) || typeof record.id!=='string')return null;
    const from=record.origin,to=record.destination;
    const valid=p=>p&&typeof p.lat==='number'&&typeof p.lng==='number'&&
      Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&p.lat>=-35.5&&p.lat<=-30&&p.lng>=-59.5&&p.lng<=-52;
    if(!valid(from)||!valid(to))return null;
    return {
      id:record.id,
      status:record.state==='EN_VIAJE'?'EN_VIAJE':'ACEPTADA',
      pickup_phase:record.state==='EN_CAMINO'?'EN_CAMINO':record.state==='EN_PUNTO'?'EN_PUNTO':null,
      origin_lat:from.lat,origin_lng:from.lng,destination_lat:to.lat,destination_lng:to.lng
    };
  }
  function createRpc({backendURL,tokenProvider,fetchImpl=root.fetch}={}){
    if(!qaPage())throw Error('qa_preview_only');
    const base=endpointOf(backendURL);
    if(typeof tokenProvider!=='function'||typeof fetchImpl!=='function')throw Error('qa_secure_client_required');
    let lastSeq=0;
    return async function rpc(name,{p_reservation_id:requestId}={}){
      if(name!=='customer_reservation_position_v1'||typeof requestId!=='string'||
         !/^[0-9a-f-]{36}$/i.test(requestId))throw Error('qa_tracking_contract_only');
      const token=await tokenProvider();
      if(typeof token!=='string'||token.length<32||token.length>240)throw Error('qa_token_missing');
      const response=await fetchImpl(`${base}/v1/requests/${encodeURIComponent(requestId)}/location`,{
        method:'GET',headers:{Authorization:'Bearer '+token,Accept:'application/json'},
        credentials:'omit',cache:'no-store',referrerPolicy:'no-referrer',mode:'cors'
      });
      if(!response.ok)throw Error('qa_tracking_http_'+response.status);
      const body=await response.json();
      if(!body?.qaOnly||body.available!==true)return {available:false};
      const age=Math.max(0,Math.ceil((Date.now()-Date.parse(body.measuredAt))/1000));
      const remaining=Date.parse(body.expiresAt)-Date.now();
      const seq=body.seq;
      if(!Number.isSafeInteger(seq)||seq<lastSeq || !Number.isFinite(age)||
        Date.parse(body.measuredAt)>Date.now()+2_000 || age>45 || remaining<=0 ||
        !(body.accuracyM>0&&body.accuracyM<=50) ||
        !Number.isFinite(body.driverLat)||!Number.isFinite(body.driverLng))return {available:false};
      lastSeq=seq;
      return {available:true,driverLat:body.driverLat,driverLng:body.driverLng,gpsAgeSec:age};
    };
  }
  function mount(host,{request,backendURL,tokenProvider,engine,api,key,renderer,fetchImpl}={}){
    if(!qaPage())throw Error('qa_preview_only');
    const reservation=normalized(request);
    if(!host||!reservation||!root.TrasladosReservationLive?.mount)throw Error('qa_tracking_not_ready');
    const rpc=createRpc({backendURL,tokenProvider,fetchImpl});
    // Replace detached child on remount, allowing the existing poller to stop.
    const container=root.document.createElement('div');
    container.dataset.quicktripLiveQa='true';
    host.replaceChildren(container);
    // A non-secret sentinel only satisfies the existing private-tracking guard.
    // Actual per-passenger authorization stays exclusively in tokenProvider.
    root.TrasladosReservationLive.mount(container,{
      reservation,sessionToken:'QA_ADAPTER_AUTHORIZED_BY_RPC',rpc,engine,api,key,renderer
    });
    return container;
  }
  return Object.freeze({createRpc,mount,normalized});
});
