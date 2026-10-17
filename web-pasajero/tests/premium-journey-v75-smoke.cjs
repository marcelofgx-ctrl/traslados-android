"use strict";
/* Validación local sin contactar Supabase ni crear solicitudes. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path");
const root=path.join(__dirname,"..");
const read=n=>fs.readFileSync(path.join(root,n),"utf8");
const html=read("index.html"),css=read("premium-journey.css"),code=read("premium-journey.js"),
      app=read("app.js"),sw=read("sw.js");
assert(html.includes('premium-journey.css?v=1')&&html.includes('premium-journey.js?v=1'));
assert(html.indexOf('premium-journey.js?v=1')<html.indexOf('app.js?v=80'),"Presentación disponible antes de la lógica");
for(const name of ["premium-journey.js","premium-journey.css","premium-skyline.svg"]){
  assert(sw.includes('"./'+name+'"'),"Recurso offline faltante: "+name);
}
for(const service of ["now","schedule","delivery"]){
  assert(html.includes('data-service="'+service+'"'),"No eliminar servicios de portada");
}
for(const step of ["route","when","passenger","confirm"]){
  assert(code.includes('"'+step+'"'),"Falta etapa "+step);
  assert(css.includes('data-premium-step="'+step+'"'),"Falta presentación de "+step);
}
assert(code.includes('complete("route")')&&code.includes('complete("passenger")'),"Etapas requieren datos");
assert(code.includes('journey-custom-time-label'),"La hora manual debe ser validada");
assert(!code.includes("customer_request_pickup_v1")&&!code.includes("rpc("),"Presentación no puede enviar solicitudes");
assert(app.includes('premiumJourney=window.TrasladosPremiumJourney.mount(document)'),"Montaje faltante");
assert(app.includes("if(quick)premiumJourney?.activate()"),"Activar al entrar en Tu viaje");
assert(app.includes('!document.body.classList.contains("journey-mode-quick")'),"No autoavanzar al escoger destino");
assert(app.includes('rpc("customer_request_pickup_fixed_v1"'),"Conservar recogida real con precio cerrado");
assert(app.includes("customer_create_reservation_v12"),"Conservar reservas");
assert(app.includes("customer_reservation_vehicle_v1"),"Mantener matrícula autorizada");
assert(app.includes("window.TrasladosOwnedPickup"),"Mantener detección de solicitud propia");
assert(html.includes('id="view-reservar"')&&html.includes('id="view-viajes"'));
assert(/#view-viaje\[data-premium-step="confirm"\].*\[data-booking-step\]/s.test(css));
assert(!code.includes("Math.random"),"Prohibidos ETA/estados simulados");
console.log("PASS premium draft: 3 servicios, 4 etapas, validaciones, contratos existentes, SW y sin mocks");
