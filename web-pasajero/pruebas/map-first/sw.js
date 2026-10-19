"use strict";
/* Scope-local install support for Map-First V3 review. The stable PWA,
 * Supabase APIs, pickup GPS and reservation flow are never cached/intercepted. */
const CACHE="traslados-map-first-preview-v3-20261019";
const PREFIX="traslados-map-first-preview-";
const SHELL=["./map-first-v3.js","./map-first-v3.css","./manifest.webmanifest","./icons/icon-192.png","./icons/icon-512.png"];
const scopePath=new URL(self.registration.scope).pathname;
self.addEventListener("install",event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL)).then(()=>self.skipWaiting()));
});
self.addEventListener("activate",event=>{
  event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith(PREFIX)&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));
});
self.addEventListener("fetch",event=>{
  const req=event.request;
  if(req.method!=="GET")return;
  const url=new URL(req.url);
  if(url.origin!==self.location.origin||!url.pathname.startsWith(scopePath))return;
  // All navigations are network-first; no old booking screen replay.
  if(req.mode==="navigate"){
    event.respondWith(fetch(req).catch(()=>caches.match("./offline.html")));
    return;
  }
  // Same-origin static files only; never persist API/driver/user/location responses.
  if(!/\.(?:css|js|png|webp|svg|webmanifest)$/.test(url.pathname))return;
  event.respondWith(fetch(req).then(response=>{
    if(response.ok&&response.type==="basic"){
      const copy=response.clone();
      event.waitUntil(caches.open(CACHE).then(cache=>cache.put(req,copy)));
    }
    return response;
  }).catch(()=>caches.match(req)));
});
