const CACHE="traslados-pwa-quicktrip-p13-photo-approved-20261030";
const CACHE_PREFIX="traslados-cliente-pwa-";
const CURRENT_PREFIX="traslados-pwa-";
const files=["./","./offline.html","./styles.css","./app.js","./premium-route.js","./address-viewport.js","./premium-route.css","./premium-map.js","./premium-montevideo.webp","./premium-car-crop.webp","./premium-skyline.svg","./geo-search.js","./address-engine.js","./route-engine.js","./eta-engine.js","./booking-contracts.js","./rpc-client.js","./booking-steps.js","./service-home.js","./owned-pickup.js","./pickup-cadence.js","./pickup-origin-drift.js","./pickup-observability.js","./trip-board.js","./reservation-live.js","./manifest.webmanifest","./icons/icon-192.png","./icons/icon-512.png","./icons/icon-maskable.png","./guest-history.js","./journey-compat-v7.js","./quicktrip-clean.js","./quicktrip-clean.css","./quicktrip-singleflow-premium.css","./quicktrip-p1-photo1.css","./quicktrip-p13-approved.css","./quicktrip-logo-contrast.svg","./quicktrip-active-ride.js","./quicktrip-n1n2.css","./quicktrip-map-focus.js","./quicktrip-map-focus.css"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(files)).then(()=>self.skipWaiting()));});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE&&(k.startsWith(CACHE_PREFIX)||k.startsWith(CURRENT_PREFIX))).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener("fetch",e=>{
  const r=e.request;if(r.method!=="GET")return;
  const u=new URL(r.url);if(u.origin!==self.location.origin)return;
  if(r.mode==="navigate"){
    e.respondWith(fetch(r).catch(()=>caches.open(CACHE).then(c=>c.match("./offline.html"))));return;
  }
  if(files.some(path=>new URL(path,self.registration.scope).pathname===u.pathname)){
    // Network first for code and styles. New deployments must not stay stuck
    // behind the previous PWA cache when a passenger is trying to book.
    e.respondWith(fetch(r).then(response=>{
      if(response.ok){
        const copy=response.clone();
        e.waitUntil(caches.open(CACHE).then(c=>c.put(r,copy)));
      }
      return response;
    }).catch(()=>caches.open(CACHE).then(c=>c.match(r).then(hit=>hit||c.match(r,{ignoreSearch:true})))));return;
  }
});
// A passenger must never receive fabricated push or create bookings offline.
self.addEventListener("push",e=>{
  let p={};try{p=e.data?.json()||{};}catch{}
  if(!p.title||!p.body)return;
  e.waitUntil(self.registration.showNotification(String(p.title),{body:String(p.body),icon:"./icons/icon-192.png",data:{url:"./"},tag:"traslados-update"}));
});
self.addEventListener("notificationclick",e=>{e.notification.close();e.waitUntil(self.clients.openWindow("./"));});
