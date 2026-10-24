"use strict";
// QA de los tres accesos: solo presentación/navegación, sin backend.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const root=path.resolve(__dirname,".."),read=f=>fs.readFileSync(path.join(root,f),"utf8");
const html=read("index.html"),clean=read("quicktrip-clean.js"),home=read("service-home.js"),
  css=read("quicktrip-home-qa.css"),sw=read("sw.js"),app=read("app.js");
const order=[...html.matchAll(/data-service="(now|delivery|schedule)"/g)].map(x=>x[1]);
assert.deepEqual(order,["now","delivery","schedule"],"Orden aprobado de tres accesos");
assert.equal((html.match(/class="service-card service-card-/g)||[]).length,3);
assert(html.includes("Tu viaje <em>programado</em>"),"Nombre correcto de programados");
assert(html.includes("Tu <em>paquete</em>")&&html.includes("PRÓXIMAMENTE"));
assert(html.includes("aria-controls=\"service-coming-soon\""),"Paquetería informa sin abrir reservas");
assert(home.includes('if(kind==="delivery")')&&home.includes("return;"),"Paquete no solicita viaje");
assert(!clean.includes("open.click()"),"No debe abrir Tu Viaje automáticamente");
assert(clean.includes("reconcile();"),"Conservar la preparación visual original");
assert(app.includes('window.TrasladosServiceHome.mount(document,{'),"Conservar motor de navegación nativo");
assert(html.includes('quicktrip-home-qa.css?v=84'));
assert(html.includes('quicktrip-clean.js?v=7'));
assert(sw.includes("quicktrip-three-services-v84-20261024"));
assert(sw.includes('"./quicktrip-home-qa.css"'));
assert(css.includes('premium-montevideo.webp'),"Ciudad local, sin CDN");
assert(!/fetch\s*\(|XMLHttpRequest|\.rpc\s*\(/.test(css+clean),"Nada de llamadas de backend nuevas");
console.log("PASS Quick Trip v84: tres entradas, primer inicio seguro, paquetería informativa, motores sin alterar");
