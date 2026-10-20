"use strict";
/* Quick Trip V6 functional skin — presentation only. All booking, route,
   GPS, price, ETA, login and history listeners remain in the official PWA.
   Activated ONLY by ?ui=v6, never by localStorage or a cookie. */
(function(root){
  const params=new URLSearchParams(root.location.search);
  if(params.get("ui")!=="v6")return;
  const doc=root.document;
  const themeKey="quicktrip-v6-theme";
  const css=doc.createElement("link");
  css.rel="stylesheet";
  css.href="./quicktrip-functional-v6.css?v=1";
  doc.head.appendChild(css);
  function start(){
    if(!doc.getElementById("view-inicio")||!doc.getElementById("view-viaje")||
       !doc.querySelector('[data-service="now"]')||!doc.getElementById("urgent-send"))return;
    doc.body.classList.add("qt-functional-v6");
    doc.title="Quick Trip V6 · traslados funcionales";
    const identity=doc.querySelector(".header .identity");
    if(identity){
      identity.href="./?ui=v6";
      identity.setAttribute("aria-label","Quick Trip, volver al inicio");
      const img=identity.querySelector("img");
      if(img){
        img.src="./pruebas/visual-v6/quicktrip-logo.svg";
        img.alt="Quick Trip";
        img.width=180;img.height=57;
      }
      const originalCopy=identity.querySelector("span");
      if(originalCopy)originalCopy.hidden=true;
    }
    const brand=doc.querySelector("#view-viaje .premium-brand");
    if(brand){
      // The heading alone is visual; no booking component or listener is replaced.
      const logo=doc.createElement("img");
      logo.src="./pruebas/visual-v6/quicktrip-logo.svg";
      logo.width=206;logo.height=69;logo.alt="Quick Trip";
      brand.replaceChildren(logo);
    }
    const scheduling=doc.querySelector('[data-service="schedule"]');
    if(scheduling){
      scheduling.setAttribute("aria-label","Tu Viaje Programado: reservar fecha y hora");
      const name=scheduling.querySelector(".service-card-name");
      if(name)name.textContent="Tu Viaje Programado";
      const arc=scheduling.querySelector(".emblem-lettering textPath");
      if(arc)arc.textContent="TU VIAJE PROGRAMADO";
    }
    const eyebrow=doc.querySelector("#view-inicio .home-hero .eyebrow");
    if(eyebrow)eyebrow.textContent="✦ QUICK TRIP · URUGUAY";
    const primary=doc.querySelector('[data-service="now"] .service-card-description');
    if(primary)primary.textContent="Viajá ahora o en pocos minutos";
    const scheduleDesc=scheduling?.querySelector(".service-card-description");
    if(scheduleDesc)scheduleDesc.textContent="Elegí fecha y hora para tu viaje";
    const steps=doc.querySelectorAll("#view-viaje .premium-progress .premium-number");
    ["🚘","▦","♙","✓"].forEach((icon,index)=>{
      if(steps[index])steps[index].textContent=icon;
    });
    const reserveTab=doc.querySelector('.tab[data-view="reservar"]');
    if(reserveTab)reserveTab.textContent="Programar";
    // The live selectors, forms and buttons are all original production nodes.
    // Add an explicit real-backend disclosure, not a second submit handler.
    const notice=doc.createElement("p");
    notice.className="qt-functional-notice";
    notice.setAttribute("role","note");
    notice.textContent="Versión funcional en prueba. Las solicitudes enviadas llegan al conductor de verdad.";
    const home=doc.querySelector("#view-inicio .home-assurance");
    if(home)home.insertAdjacentElement("beforebegin",notice.cloneNode(true));
    const final=doc.getElementById("urgent-actions");
    if(final)final.appendChild(notice);
    const button=doc.createElement("button");
    button.type="button";
    button.className="qt-functional-theme-switch";
    button.id="qt-functional-theme-switch";
    button.setAttribute("aria-label","Cambiar entre modo claro y oscuro");
    const meta=doc.querySelector('meta[name="theme-color"]');
    function apply(mode){
      const light=mode==="light";
      doc.body.classList.toggle("qt-theme-light",light);
      doc.body.classList.toggle("qt-theme-dark",!light);
      doc.documentElement.style.colorScheme=light?"light":"dark";
      button.setAttribute("aria-pressed",String(light));
      button.setAttribute("aria-label",light?"Cambiar a modo oscuro":"Cambiar a modo claro");
      button.textContent=light?"☀":"☾";
      if(meta)meta.setAttribute("content",light?"#f6faf5":"#041e22");
    }
    let saved="dark";
    try{const mode=root.localStorage.getItem(themeKey);if(mode==="light"||mode==="dark")saved=mode;}catch(_){}
    apply(saved);
    button.addEventListener("click",()=>{
      const next=doc.body.classList.contains("qt-theme-light")?"dark":"light";
      apply(next);
      try{root.localStorage.setItem(themeKey,next);}catch(_){}
    });
    const header=doc.querySelector(".header-inner");
    const phone=header?.querySelector(".tel");
    if(header){
      const tools=doc.createElement("div");
      tools.className="qt-functional-header-actions";
      tools.appendChild(button);
      if(phone)tools.appendChild(phone); // Keep one working phone action.
      header.appendChild(tools);
    }
  }
  // app.js and premium-journey.js mount first; defer order preserves native handlers.
  // defer scripts run while readyState is often "interactive", BEFORE DOMContentLoaded.
  // app.js registered its initializer first: wait for that native mount to finish.
  if(doc.readyState==="complete")start();
  else doc.addEventListener("DOMContentLoaded",start,{once:true});
})(window);
