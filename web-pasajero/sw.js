const CACHE="traslados-cliente-pwa-v5";
const files=["./","./offline.html","./styles.css","./app.js","./geo-search.js","./manifest.webmanifest","./icons/icon-192.png","./icons/icon-512.png","./icons/icon-maskable.png"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(files)).then(()=>self.skipWaiting()));});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener("fetch",e=>{
  const r=e.request;if(r.method!=="GET")return;
  const u=new URL(r.url);if(u.origin!==self.location.origin)return;
  if(r.mode==="navigate"){
    e.respondWith(fetch(r).catch(()=>caches.match("./offline.html")));return;
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
    }).catch(()=>caches.match(r)));return;
  }
});
// A passenger must never receive fabricated push or create bookings offline.
self.addEventListener("push",e=>{
  let p={};try{p=e.data?.json()||{};}catch{}
  if(!p.title||!p.body)return;
  e.waitUntil(self.registration.showNotification(String(p.title),{body:String(p.body),icon:"./icons/icon-192.png",data:{url:"./"},tag:"traslados-update"}));
});
self.addEventListener("notificationclick",e=>{e.notification.close();e.waitUntil(self.clients.openWindow("./"));});