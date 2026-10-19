"use strict";
/* Presentación independiente: app.js conserva GPS, rutas, PIN, idempotencia y RPC. */
(function(root){
  const STAGES=["route","when","passenger","confirm"];
  const TITLES={route:"¿Dónde te buscamos?",when:"¿Cuándo querés viajar?",passenger:"Tus datos",confirm:"Revisá tu viaje"};
  const NOTES={route:"Elegí origen, destino y paradas.",when:"Ahora o en las próximas 2 horas.",passenger:"Tus datos permiten coordinar el traslado.",confirm:"Todo listo para enviar tu solicitud."};
  function make(doc,tag,cls,content){
    const node=doc.createElement(tag);if(cls)node.className=cls;if(content!==undefined)node.textContent=content;return node;
  }
  function mount(doc){
    const screen=doc.getElementById("view-viaje");if(!screen)throw Error("Falta Tu viaje");
    let stage="route";
    const header=make(doc,"section","premium-journey-header");
    header.setAttribute("aria-label","Tu viaje, cuatro pasos");
    header.innerHTML='<div class="premium-brand"><span class="premium-car" aria-hidden="true"><svg viewBox="0 0 64 64" fill="none"><path d="M13 38l5-12c1-3 4-5 7-5h15c4 0 6 2 8 5l4 12" stroke="currentColor" stroke-width="2.7" stroke-linecap="round"/><path d="M11 38h42v10H11z" stroke="currentColor" stroke-width="2.7" stroke-linejoin="round"/><path d="M16 48v5m32-5v5M18 41h7m14 0h7M22 28h20" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/></svg></span><span><small>TRASLADOS</small><strong>Tu viaje</strong></span></div><ol class="premium-progress" aria-label="Pasos del viaje"></ol>';
    screen.querySelector(".journey-back").insertAdjacentElement("afterend",header);
    // Mover el estado real, no clonarlo: sus actualizaciones y acciones se conservan.
    const presence=doc.getElementById("pickup-presence");
    const presenceAnchor=doc.createComment("pickup-presence-original");
    presence.before(presenceAnchor);
    const presenceHost=make(doc,"section","premium-live-presence");
    presenceHost.setAttribute("aria-label","Disponibilidad del conductor");
    header.insertAdjacentElement("afterend",presenceHost);
    // Live summaries are from the chosen origin, verified road route and
    // server-returned pickup ETA. A selected origin is not a continuous GPS feed.
    const locationCard=make(doc,"section","premium-position-summary");
    locationCard.setAttribute("aria-label","Mi punto de recogida y distancias");
    const positionRow=make(doc,"div","premium-position-row");
    const positionCopy=make(doc,"div","premium-position-copy");
    positionCopy.append(make(doc,"small","","MI PUNTO DE RECOGIDA"));
    const positionLabel=make(doc,"strong","premium-position-label","Sin seleccionar");
    positionCopy.append(positionLabel);
    const locate=make(doc,"button","premium-position-gps","⌖ Usar mi GPS");
    locate.type="button";
    locate.setAttribute("aria-label","Usar mi ubicación actual como origen de recogida");
    locate.addEventListener("click",()=>doc.getElementById("pickup-use-location")?.click());
    positionRow.append(positionCopy,locate);
    const metrics=make(doc,"div","premium-position-metrics");
    const pickupDistance=make(doc,"span","premium-position-distance","Conductor → origen: pendiente");
    const rideDistance=make(doc,"span","premium-position-ride","Recorrido: pendiente");
    metrics.append(pickupDistance,rideDistance);
    locationCard.append(positionRow,metrics);
    presenceHost.insertAdjacentElement("afterend",locationCard);
    function syncLocation({origin=null,road=null,pickup=null}={}){
      positionLabel.textContent=origin?.text||"Sin seleccionar";
      positionLabel.dataset.source=origin?.source==="gps"?"gps":origin?"chosen":"none";
      positionCopy.querySelector("small").textContent=origin?.source==="gps"
        ?"ORIGEN SELECCIONADO CON GPS":"MI PUNTO DE RECOGIDA";
      locate.textContent=origin?"⌖ Actualizar GPS":"⌖ Usar mi GPS";
      const hasPickup=origin&&pickup&&Number.isFinite(pickup.km)&&Number.isFinite(pickup.minutes);
      pickupDistance.textContent=hasPickup
        ?"Conductor → origen: "+pickup.km.toLocaleString("es-UY",{maximumFractionDigits:1})+" km · "+Math.round(pickup.minutes)+" min aprox."
        :origin?"Conductor → origen: sin estimación verificada":"Conductor → origen: seleccioná tu punto";
      rideDistance.textContent=road&&Number.isFinite(road.distanceKm)
        ?"Recorrido: "+road.distanceKm.toLocaleString("es-UY",{maximumFractionDigits:1})+" km"
        :"Recorrido: pendiente de ruta verificada";
    }
    function placePresence(quick){
      if(quick)presenceHost.append(presence);
      else presenceAnchor.after(presence);
    }
    const progress=header.querySelector(".premium-progress");
    ["Recorrido","Cuándo","Tus datos","Confirmar"].forEach((label,index)=>{
      const item=make(doc,"li","premium-step"),circle=make(doc,"span","premium-number",String(index+1));
      circle.setAttribute("aria-hidden","true");item.append(circle,make(doc,"small","",label));progress.appendChild(item);
    });
    const intro=make(doc,"section","premium-intro"),title=make(doc,"h2","",TITLES.route),desc=make(doc,"p","",NOTES.route);
    intro.append(title,desc);screen.querySelector(".journey-hero").insertAdjacentElement("afterend",intro);
    const select=doc.getElementById("journey-when-select"),options=make(doc,"div","premium-time-options");
    options.setAttribute("role","group");options.setAttribute("aria-label","Elegir hora de recogida");
    [["now","Ahora"],["10","En 10 minutos"],["15","En 15 minutos"],["20","En 20 minutos"],["30","En 30 minutos"],["45","En 45 minutos"],["60","En 1 hora"]].forEach(([value,label])=>{
      const b=make(doc,"button","premium-time-choice");b.type="button";b.dataset.pickup=value;
      b.append(make(doc,"span","premium-clock-symbol",value==="now"?"ϟ":"◷"),make(doc,"strong","",label),make(doc,"span","premium-check","✓"));
      b.addEventListener("click",()=>{select.value=value;select.dispatchEvent(new Event("change",{bubbles:true}));updateTimes();});
      options.appendChild(b);
    });
    const custom=make(doc,"button","premium-time-choice premium-custom-time");
    custom.type="button";custom.append(make(doc,"span","premium-clock-symbol","◷"),make(doc,"strong","","Elegir otra hora"),make(doc,"span","premium-check","→"));
    custom.addEventListener("click",()=>doc.getElementById("journey-clock-action").click());options.appendChild(custom);
    // El reloj original conserva sus listeners y el selector nativo de 24 h.
    // Lo movemos fuera del contenedor oculto para que sea accesible y visible.
    const clockButton=doc.getElementById("journey-clock-action");
    clockButton.setAttribute("aria-label","Elegir hora de recogida, formato 24 horas");
    clockButton.textContent="◷";
    const timeLayout=make(doc,"div","premium-clock-layout");
    timeLayout.append(options,clockButton);
    doc.querySelector(".journey-time-controls").insertAdjacentElement("afterend",timeLayout);
    // Controles secundarios siguen presentes y funcionando, pero no desplazan
    // la selección horaria en la pantalla principal de "Cuándo".
    const extraWhen=make(doc,"details","premium-when-extras");
    const extraSummary=make(doc,"summary","","Disponibilidad y pasajeros · ver detalles");
    const extraHint=make(doc,"p","premium-when-extras-note","Consultá la disponibilidad y ajustá la cantidad de pasajeros antes de enviar.");
    extraWhen.append(extraSummary,extraHint);
    function mountWhenExtras(){
      const whenCard=doc.querySelector('[data-booking-step="when"]');
      if(!whenCard||whenCard.parentElement===extraWhen)return;
      if(extraWhen.parentElement!==whenCard.parentElement)
        whenCard.insertAdjacentElement("afterend",extraWhen);
      if(whenCard.parentElement!==extraWhen)extraWhen.appendChild(whenCard);
    }
    // Solo etiquetas realmente elegidas por el pasajero, nunca destinos fingidos.
    const routeCard=make(doc,"section","premium-confirm-route");
    routeCard.hidden=true;
    routeCard.setAttribute("aria-label","Recorrido a confirmar");
    function renderConfirmRoute(){
      const origin=doc.querySelector("#origin-picker .location-selected-label")?.textContent?.trim();
      const destination=doc.querySelector("#destination-picker .location-selected-label")?.textContent?.trim();
      routeCard.replaceChildren();
      if(!origin||!destination){routeCard.hidden=true;return;}
      routeCard.hidden=false;
      const heading=make(doc,"div","premium-confirm-heading");
      const edit=make(doc,"button","premium-confirm-edit","Editar recorrido");
      edit.type="button";edit.addEventListener("click",()=>open("route"));
      heading.append(make(doc,"span","","TU RECORRIDO"),edit);routeCard.append(heading);
      const stops=[...doc.querySelectorAll("#stops .location-selected-label")].map(n=>n.textContent.trim()).filter(Boolean);
      [[origin,"●","Origen"],...stops.map(v=>[v,"•","Parada"]),[destination,"●","Destino"]].forEach(([place,dot,type],idx)=>{
        const row=make(doc,"div","premium-confirm-place");
        row.dataset.kind=idx===0?"origin":idx===stops.length+1?"destination":"stop";
        row.append(make(doc,"span","premium-confirm-dot",dot));
        const copy=make(doc,"div","premium-confirm-copy");
        copy.append(make(doc,"small","",type),make(doc,"strong","",place));
        row.append(copy);routeCard.append(row);
      });
    }
    // Datos complementarios privados: nunca se agregan a la RPC de reserva.
    // Se guardan localmente ÚNICAMENTE con consentimiento, sin PIN ni itinerarios.
    const CONTACT_KEY="traslados.premium.contact.local.v1";
    const passengerBody=doc.querySelector('[data-booking-step="passenger"] [data-step-body]');
    const contactExtras=make(doc,"section","premium-contact-extras");
    contactExtras.setAttribute("aria-label","Datos complementarios y privacidad");
    const emailLabel=make(doc,"label","premium-email-label","Email (opcional)");
    const emailInput=make(doc,"input","premium-email-input");
    emailInput.id="premium-person-email";emailInput.type="email";emailInput.autocomplete="email";
    emailInput.inputMode="email";emailInput.maxLength=160;
    emailInput.placeholder="nombre@ejemplo.com";
    emailLabel.htmlFor=emailInput.id;
    emailLabel.append(emailInput);
    const consentLabel=make(doc,"label","premium-save-contact");
    const consent=make(doc,"input","premium-save-contact-check");
    consent.id="premium-save-contact";consent.type="checkbox";
    consentLabel.append(consent,make(doc,"span","","Recordar mis datos en este dispositivo"));
    const privacy=make(doc,"p","premium-contact-privacy",
      "Tus datos son para coordinar este traslado. El email no se envía al conductor ni al servidor. Solo lo recordaremos aquí si lo autorizás.");
    const consentNote=make(doc,"small","premium-contact-note",
      "Podés desactivar esta opción cuando quieras; borrará los datos guardados en este dispositivo.");
    contactExtras.append(emailLabel,consentLabel,privacy,consentNote);
    passengerBody.append(contactExtras);
    const personName=doc.getElementById("person-name"),personPhone=doc.getElementById("person-phone");
    const contactFields=[personName,personPhone,emailInput];
    function saveContact(){
      try{
        if(!consent.checked){localStorage.removeItem(CONTACT_KEY);return;}
        const email=emailInput.value.trim();
        if(email&&!emailInput.checkValidity())return;
        localStorage.setItem(CONTACT_KEY,JSON.stringify({
          name:personName.value.trim(),phone:personPhone.value.trim(),email
        }));
      }catch{} // Privacidad del navegador / sin cuota: nunca impedir reservar.
    }
    function hydrateContact(){
      try{
        const saved=JSON.parse(localStorage.getItem(CONTACT_KEY)||"null");
        if(!saved||typeof saved!=="object")return;
        consent.checked=true;
        if(!personName.value)personName.value=String(saved.name||"").slice(0,120);
        if(!personPhone.value)personPhone.value=String(saved.phone||"").slice(0,32);
        if(!emailInput.value)emailInput.value=String(saved.email||"").slice(0,160);
        for(const input of [personName,personPhone])input.dispatchEvent(new Event("input",{bubbles:true}));
      }catch{}
    }
    contactFields.forEach(input=>input.addEventListener("input",saveContact));
    consent.addEventListener("change",saveContact);
    hydrateContact();
    const requestHost=doc.getElementById("urgent-actions");
    requestHost.querySelector(".request-hero").insertAdjacentElement("afterend",routeCard);
    // Preserve the real submission control and its listeners. Put the optional
    // tracking explanation after the main action, instead of between route/fare.
    const requestDescription=doc.getElementById("urgent-actions-description");
    requestHost.querySelector(".booking-final-note").insertAdjacentElement("afterend",requestDescription);
    requestHost.querySelector("#urgent-route-recap .request-recap-metrics>span:last-child small").textContent="Precio del viaje";
    const actions=make(doc,"footer","premium-actions"),warning=make(doc,"p","premium-error"),back=make(doc,"button","premium-previous","← Atrás"),next=make(doc,"button","premium-next","Continuar →");
    back.type="button";next.type="button";warning.setAttribute("role","alert");warning.hidden=true;
    actions.append(warning,back,next);screen.querySelector("#journey-field-host").insertAdjacentElement("afterend",actions);
    function updateTimes(){
      for(const button of options.querySelectorAll("[data-pickup]")){
        const active=button.dataset.pickup===select.value;
        button.classList.toggle("is-selected",active);button.setAttribute("aria-pressed",String(active));
      }
      custom.classList.toggle("is-selected",select.value==="custom");
      custom.setAttribute("aria-pressed",String(select.value==="custom"));
    }
    function open(name){
      if(!STAGES.includes(name))return;stage=name;screen.dataset.premiumStep=name;
      title.textContent=TITLES[name];desc.textContent=NOTES[name];
      [...progress.children].forEach((item,index)=>{
        const current=STAGES.indexOf(name);item.classList.toggle("active",index===current);
        item.classList.toggle("done",index<current);
        if(index===current)item.setAttribute("aria-current","step");else item.removeAttribute("aria-current");
      });
      if(name!=="confirm"){
        const toggle=doc.querySelector('[data-booking-step="'+name+'"] [data-step-toggle]');
        if(toggle?.getAttribute("aria-expanded")!=="true")toggle?.click();
      }
      if(name==="when")mountWhenExtras();
      if(name==="confirm"){
        renderConfirmRoute();
        requestDescription.textContent="Recibirás un código para seguir la respuesta del conductor.";
        if(!doc.querySelector('meta[name="traslados-preview"]')){
          const label=doc.querySelector("#urgent-send .booking-cta-label");
          if(label)label.textContent="CONFIRMAR Y SOLICITAR VIAJE";
        }
      }
      warning.hidden=true;back.hidden=name==="route";next.hidden=name==="confirm";
      next.textContent=name==="passenger"?"Revisar mi viaje →":"Continuar →";
      updateTimes();
      root.TrasladosPreviewNavigationSession?.onStage(name);
      header.scrollIntoView?.({block:"start",behavior:"smooth"});
    }
    function complete(which){
      return doc.querySelector('[data-booking-step="'+which+'"]')?.classList.contains("is-complete")===true;
    }
    function forward(){
      warning.hidden=true;
      if(stage==="route"&&!complete("route")){
        warning.textContent="Completá origen, destino y todas las paradas antes de continuar.";warning.hidden=false;return;
      }
      if(stage==="when"&&select.value==="custom"&&doc.getElementById("journey-custom-time-label").hidden){
        warning.textContent="Elegí una hora válida dentro de las próximas dos horas.";warning.hidden=false;return;
      }
      if(stage==="passenger"&&emailInput.value.trim()&&!emailInput.checkValidity()){
        warning.textContent="Revisá el formato del email o dejalo vacío.";warning.hidden=false;
        emailInput.focus();return;
      }
      if(stage==="passenger"&&!complete("passenger")){
        warning.textContent="Completá nombre y teléfono, o revisá los datos de tu cuenta.";warning.hidden=false;return;
      }
      const i=STAGES.indexOf(stage);if(i<3)open(STAGES[i+1]);
    }
    back.addEventListener("click",()=>open(STAGES[Math.max(0,STAGES.indexOf(stage)-1)]));
    next.addEventListener("click",forward);select.addEventListener("change",updateTimes);
    doc.getElementById("journey-clock-input").addEventListener("change",updateTimes);
    if(doc.querySelector('meta[name="traslados-preview"]')){
      for(const id of ["urgent-send","confirm-booking"]){
        const b=doc.getElementById(id);b.disabled=true;
        const label=b.querySelector(".booking-cta-label");if(label)label.textContent="Vista de prueba · Sin envíos";
      }
    }
    return Object.freeze({activate:()=>{placePresence(true);open("route");},placePresence,open,syncLocation,current:()=>stage});
  }
  root.TrasladosPremiumJourney=Object.freeze({mount});
})(window);
