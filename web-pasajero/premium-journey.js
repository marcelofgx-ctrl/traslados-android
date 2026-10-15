"use strict";
/* Presentación independiente: app.js conserva GPS, rutas, PIN, idempotencia y RPC. */
(function(root){
  const STAGES=["route","when","passenger","confirm"];
  const TITLES={route:"¿Dónde te buscamos?",when:"¿Cuándo querés viajar?",passenger:"Tus datos",confirm:"Tu traslado"};
  const NOTES={route:"Elegí origen, destino y paradas.",when:"Ahora por defecto. Hasta dos horas de anticipación.",passenger:"Tus datos permiten coordinar el traslado.",confirm:"Revisá tu recorrido antes de solicitar el viaje."};
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
    doc.querySelector(".journey-time-controls").insertAdjacentElement("afterend",options);
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
      warning.hidden=true;back.hidden=name==="route";next.hidden=name==="confirm";
      next.textContent=name==="passenger"?"Revisar mi viaje →":"Continuar →";
      updateTimes();header.scrollIntoView?.({block:"start",behavior:"smooth"});
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
      if(stage==="passenger"&&!complete("passenger")){
        warning.textContent="Completá nombre y teléfono, o revisá los datos de tu cuenta.";warning.hidden=false;return;
      }
      const i=STAGES.indexOf(stage);if(i<3)open(STAGES[i+1]);
    }
    back.addEventListener("click",()=>open(STAGES[Math.max(0,STAGES.indexOf(stage)-1)]));
    next.addEventListener("click",forward);select.addEventListener("change",updateTimes);
    doc.getElementById("journey-clock-input").addEventListener("change",updateTimes);
    return Object.freeze({activate:()=>open("route"),open,current:()=>stage});
  }
  root.TrasladosPremiumJourney=Object.freeze({mount});
})(window);
