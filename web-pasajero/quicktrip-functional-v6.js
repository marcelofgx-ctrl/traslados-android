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
  css.href="./quicktrip-functional-v6.css?v=3";
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
    // V6 screens are built around the ORIGINAL mounted components, never a
    // second mock screen or clone. Move only presentation nodes: live listeners
    // and the Supabase/road-route state stay attached to their native elements.
    const journey=doc.getElementById("view-viaje");
    const progressHeader=journey?.querySelector(".premium-journey-header");
    const introduction=journey?.querySelector(".premium-intro");
    if(progressHeader&&introduction)
      progressHeader.insertAdjacentElement("afterend",introduction);
    const steps=doc.querySelectorAll("#view-viaje .premium-progress .premium-number");
    const icons=[
      '<svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="M6 17l3-7h14l3 7v8H6zM10 10l2-4h8l2 4M9 19h5m4 0h5" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><circle cx="10" cy="25" r="2" fill="currentColor"/><circle cx="22" cy="25" r="2" fill="currentColor"/></svg>',
      '<svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><rect x="6" y="7" width="20" height="20" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M6 14h20M12 4v6m8-6v6M12 18v5m5-5v5m5-5v5" stroke="currentColor" stroke-width="1.5"/></svg>',
      '<svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><circle cx="16" cy="10" r="4" stroke="currentColor" stroke-width="1.7"/><path d="M7 27v-4a9 9 0 0 1 18 0v4H7z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></svg>',
      '<svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="M7 16l6 6L25 9" stroke="currentColor" stroke-width="2.7" stroke-linecap="round" stroke-linejoin="round"/></svg>'
    ];
    steps.forEach((node,index)=>{if(icons[index])node.innerHTML=icons[index];});
    const titles={
      route:["¿Dónde te buscamos?","Elegí origen, destino y paradas."],
      when:["¿Cuándo querés viajar?","Ahora o en las próximas 2 horas."],
      passenger:["Tus datos","Solo lo necesario para comunicarnos."],
      confirm:["Revisá tu viaje","Comprobá el recorrido y la tarifa antes de solicitar."]
    };
    const nativeTitle=journey?.querySelector(".premium-intro h2");
    const nativeNote=journey?.querySelector(".premium-intro p");
    function syncStage(){
      const stage=journey?.dataset.premiumStep;
      const copy=titles[stage];
      if(!copy)return;
      if(nativeTitle)nativeTitle.textContent=copy[0];
      if(nativeNote)nativeNote.textContent=copy[1];
    }
    if(journey){
      new MutationObserver(syncStage).observe(journey,{attributes:true,attributeFilter:["data-premium-step"]});
      syncStage();
    }
    // Exactly the original form inputs remain editable and authoritative.
    const passengerBody=journey?.querySelector('[data-booking-step="passenger"] [data-step-body]');
    const personName=doc.getElementById("person-name");
    const personPhone=doc.getElementById("person-phone");
    if(passengerBody&&personName&&personPhone){
      const card=doc.createElement("section");
      card.className="qt-v6-contact-card";
      card.setAttribute("aria-label","Resumen de contacto actual");
      const overline=doc.createElement("span");overline.className="qt-v6-overline";
      overline.textContent="DATOS DE CONTACTO";
      const row=doc.createElement("div");row.className="qt-v6-contact-row";
      const avatar=doc.createElement("span");avatar.className="qt-v6-contact-avatar";avatar.setAttribute("aria-hidden","true");
      const details=doc.createElement("div");details.className="qt-v6-contact-details";
      const name=doc.createElement("strong"),phone=doc.createElement("small");
      const done=doc.createElement("span");done.className="qt-v6-contact-check";done.setAttribute("aria-hidden","true");
      details.append(name,phone);row.append(avatar,details,done);card.append(overline,row);
      const edit=doc.createElement("button");edit.type="button";edit.className="qt-v6-contact-edit";
      edit.textContent="Revisar mis datos ↗";edit.addEventListener("click",()=>personName.focus({preventScroll:false}));
      card.append(edit);
      passengerBody.prepend(card);
      const syncContact=()=>{
        const fullName=personName.value.trim(),number=personPhone.value.trim();
        const complete=fullName.length>=2&&number.replace(/\D/g,"").length>=8;
        avatar.textContent=(fullName[0]||"•").toUpperCase();
        name.textContent=fullName||"Completá tus datos";
        phone.textContent=number||"Ingresá tu teléfono para coordinar";
        done.textContent=complete?"✓":"";
        card.dataset.complete=String(complete);
      };
      for(const input of [personName,personPhone]){
        input.addEventListener("input",syncContact);
        input.addEventListener("change",syncContact);
      }
      syncContact();
    }
    // Scheduled trips retain the original three native editing steps and the
    // fourth real confirmation action; V6 adds the APPROVED visual four-stage
    // progress without inserting new form or booking handlers.
    const scheduledSurface=doc.getElementById("view-reservar");
    const oldProgress=scheduledSurface?.querySelector(".booking-progress");
    if(scheduledSurface&&oldProgress){
      const title=scheduledSurface.querySelector(".section-heading h2");
      if(title)title.textContent="Tu Viaje Programado";
      const rail=doc.createElement("ol");
      rail.className="qt-v6-schedule-progress";
      rail.setAttribute("aria-label","Etapas de tu viaje programado");
      const stages=["Recorrido","Cuándo","Tus datos","Confirmar"];
      for(let i=0;i<stages.length;i++){
        const item=doc.createElement("li");
        const mark=doc.createElement("span");mark.className="qt-v6-schedule-number";
        mark.innerHTML=icons[i];
        const label=doc.createElement("small");label.textContent=stages[i];
        item.append(mark,label);rail.appendChild(item);
      }
      oldProgress.insertAdjacentElement("beforebegin",rail);
      const cards=[...scheduledSurface.querySelectorAll("[data-booking-step]")];
      const preview=doc.getElementById("booking-preview");
      const sendActions=doc.getElementById("submit-actions");
      function syncScheduledStage(){
        let active=Math.max(0,cards.findIndex(x=>x.classList.contains("is-open")));
        if((preview&&!preview.hidden)||(sendActions&&!sendActions.hidden))active=3;
        for(const [i,item] of [...rail.children].entries()){
          item.classList.toggle("is-current",i===active);
          item.classList.toggle("is-completed",i<active);
          if(i===active)item.setAttribute("aria-current","step");
          else item.removeAttribute("aria-current");
        }
      }
      for(const card of cards)new MutationObserver(syncScheduledStage).observe(card,{
        attributes:true,attributeFilter:["class"]
      });
      for(const node of [preview,sendActions]){
        if(node)new MutationObserver(syncScheduledStage).observe(node,{attributes:true,attributeFilter:["hidden"]});
      }
      syncScheduledStage();
    }
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
    const scheduledSend=doc.getElementById("submit-actions");
    if(scheduledSend)scheduledSend.appendChild(notice.cloneNode(true));
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
      button.innerHTML='<span class="qt-theme-sun" aria-hidden="true">☼</span><span class="qt-theme-moon" aria-hidden="true">☾</span>';
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
      const back=doc.createElement("button");
      back.type="button";back.className="qt-v6-header-back";
      back.textContent="‹";back.setAttribute("aria-label","Volver al inicio");
      back.addEventListener("click",()=>doc.querySelector('.tab[data-view="inicio"]')?.click());
      header.prepend(back);
      const journeyView=doc.getElementById("view-viaje");
      const scheduledView=doc.getElementById("view-reservar");
      function updateBack(){
        back.classList.toggle("qt-v6-back-inactive",
          !(journeyView?.classList.contains("active")||
            scheduledView?.classList.contains("active")));
      }
      for(const view of [journeyView,scheduledView]){
        if(view)new MutationObserver(updateBack).observe(view,{attributes:true,attributeFilter:["class"]});
      }
      updateBack();
    }
    const tabIcons={inicio:"⌂",viajes:"▤",cuenta:"☰"};
    for(const tab of doc.querySelectorAll(".tabs .tab")){
      const glyph=tabIcons[tab.dataset.view];
      if(!glyph)continue;
      const icon=doc.createElement("span");icon.className="qt-v6-tab-icon";
      icon.textContent=glyph;icon.setAttribute("aria-hidden","true");
      tab.prepend(icon);
    }
  }
  // app.js and premium-journey.js mount first; defer order preserves native handlers.
  // defer scripts run while readyState is often "interactive", BEFORE DOMContentLoaded.
  // app.js registered its initializer first: wait for that native mount to finish.
  if(doc.readyState==="complete")start();
  else doc.addEventListener("DOMContentLoaded",start,{once:true});
})(window);
