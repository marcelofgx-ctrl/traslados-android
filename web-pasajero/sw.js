const CACHE="traslados-pwa-qa-route-v90-20261025";
// CacheStorage is shared by all service workers on an origin.
// This QA preview may evict only prior caches from its own route-sandbox family.
const QA_CACHE_PREFIX="traslados-pwa-qa-route-";
const files=["./index.html","./offline.html","./styles.css","./app.js","./premium-route.js","./address-viewport.js","./premium-route.css","./premium-map.js","./premium-montevideo.webp","./premium-car-crop.webp","./premium-skyline.svg","./geo-search.js","./address-engine.js","./route-engine.js","./eta-engine.js","./booking-contracts.js","./rpc-client.js","./booking-steps.js","./service-home.js","./owned-pickup.js","./pickup-cadence.js","./pickup-origin-drift.js","./pickup-observability.js","./trip-board.js","./reservation-live.js","./manifest.webmanifest","./icons/icon-192.png","./icons/icon-512.png","./icons/icon-maskable.png","./guest-history.js","./journey-compat-v7.js","./quicktrip-clean.js","./quicktrip-v85-resilience.js","./quicktrip-canonical-v86.css","./quicktrip-approved-v85-qa.js","./quicktrip-qa-samsung-v87.css","./quicktrip-qa-samsung-v87.js","./quicktrip-qa-gps-ux-v89.css","./quicktrip-qa-gps-ux-v89.js","./quicktrip-qa-route-v90.css","./quicktrip-qa-route-v90.js","./quicktrip-logo-contrast.svg"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(files)).then(()=>self.skipWaiting()));});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE&&k.startsWith(QA_CACHE_PREFIX)).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener("fetch",e=>{
  const r=e.request;if(r.method!=="GET")return;
  const u=new URL(r.url);if(u.origin!==self.location.origin)return;
  if(r.mode==="navigate"){
    // Acquire the versioned handle before any request can outlive this worker.
    const cacheRef=caches.open(CACHE);
    e.respondWith(cacheRef.then(cache=>fetch(r).catch(()=>cache.match("./offline.html"))));return;
  }
  if(files.some(path=>new URL(path,self.registration.scope).pathname===u.pathname)){
    // A retired SW must not reopen an evicted cache after a pending response.
    const cacheRef=caches.open(CACHE);
    e.respondWith(cacheRef.then(cache=>fetch(r).then(response=>{
      if(response.ok){
        const copy=response.clone();
        e.waitUntil(caches.keys().then(keys=>
          keys.includes(CACHE)?cache.put(r,copy):undefined
        ).catch(()=>undefined));
      }
      return response;
    }).catch(async()=>{
      const cache=await cacheRef;
      // No usar ignoreSearch: puede seleccionar la primera copia precargada,
      // aunque exista una versión posterior obtenida con ?v=XX.
      const exact=await cache.match(r);
      if(exact)return exact;
      const wanted=new URL(r.url);
      const candidates=(await cache.keys()).filter(request=>{
        const url=new URL(request.url);
        return url.origin===wanted.origin&&url.pathname===wanted.pathname;
      }).sort((a,b)=>{
        const version=request=>{
          const raw=new URL(request.url).searchParams.get("v");
          return /^\d+$/.test(raw||"")?Number(raw):-1;
        };
        return version(b)-version(a);
      });
      for(const candidate of candidates){
        const hit=await cache.match(candidate);
        if(hit)return hit;
      }
      return undefined;
    })));return;
  }
});
// A passenger must never receive fabricated push or create bookings offline.
self.addEventListener("push",e=>{
  let p={};try{p=e.data?.json()||{};}catch{}
  if(!p.title||!p.body)return;
  e.waitUntil(self.registration.showNotification(String(p.title),{body:String(p.body),icon:"./icons/icon-192.png",data:{url:"./index.html"},tag:"traslados-update"}));
});
self.addEventListener("notificationclick",e=>{e.notification.close();e.waitUntil(self.clients.openWindow("./index.html"));});
