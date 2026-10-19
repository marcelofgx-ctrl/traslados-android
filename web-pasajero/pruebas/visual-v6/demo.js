"use strict";
/* Independent visual study. Nothing here can query GPS, price, availability,
   account information or booking APIs. Static illustrative content only. */
(()=>{
  const screens=[...document.querySelectorAll("[data-screen]")];
  const home=document.getElementById("qt-home");
  const main=document.getElementById("demo-content");
  const flow=document.querySelector(".flow-heading");
  const footer=document.querySelector(".bottom-action");
  const headerBack=document.getElementById("header-back");
  // One immutable-format presentation contract. Demo fixture stays illustrative;
  // the future G4 adapter will supply a verified snapshot instead of hardcoding DOM.
  const model=window.TrasladosV6Model;
  const demoState=model.demoFixture();
  model.render(demoState);
  const progress=document.getElementById("progress");
  const progressFill=document.getElementById("progress-fill");
  const counter=document.getElementById("step-counter");
  const next=document.getElementById("next-button");
  const back=document.getElementById("back-button");
  const message=document.getElementById("demo-message");
  const theme=document.getElementById("theme-switch");
  const themeLabel=document.getElementById("theme-label");
  let current=-1;
  const indexFromHash=()=>{
    const match=location.hash.match(/^#paso-([1-4])$/);
    return match?Number(match[1])-1:-1;
  };
  function showHome({historyEntry=true}={}){
    current=-1;
    home.hidden=false;
    flow.hidden=true;main.hidden=true;footer.hidden=true;headerBack.hidden=true;
    document.querySelectorAll(".qt-bottom-nav [data-demo]").forEach(button=>{
      if(button.dataset.demo==="Inicio")button.setAttribute("aria-current","page");
      else button.removeAttribute("aria-current");
    });
    if(historyEntry&&location.hash!=="#inicio")
      history.pushState({v6Demo:true,v6Home:true},"","#inicio");
    message.textContent="";
    window.scrollTo({top:0,behavior:"instant"});
  }
  function show(index,{historyEntry=true}={}){
    if(index<0){showHome({historyEntry});return;}
    current=Math.max(0,Math.min(3,index));
    home.hidden=true;flow.hidden=false;main.hidden=false;footer.hidden=false;headerBack.hidden=false;
    document.querySelectorAll(".qt-bottom-nav [data-demo]").forEach(button=>button.removeAttribute("aria-current"));
    for(const screen of screens){
      const active=Number(screen.dataset.screen)===current;
      screen.classList.toggle("is-active",active);
      screen.hidden=!active;
    }
    counter.innerHTML=String(current+1).padStart(2,"0")+' <i>/ 04</i>';
    progressFill.style.width=((current+1)/4*100)+"%";
    progress.setAttribute("aria-valuenow",String(current+1));
    progress.setAttribute("aria-label","Progreso del prototipo, paso "+(current+1)+" de 4");
    back.hidden=current===0;
    next.disabled=current===3;
    next.innerHTML=current===3
      ?"Solicitud deshabilitada · maqueta"
      :current===2?'Revisar viaje <span aria-hidden="true">→</span>'
      :'Continuar <span aria-hidden="true">→</span>';
    message.textContent="";
    document.querySelectorAll(".flow-steps li").forEach((node,i)=>{
      node.classList.toggle("is-current",i===current);
      node.classList.toggle("is-completed",i<current);
      if(i===current)node.setAttribute("aria-current","step");else node.removeAttribute("aria-current");
    });
    if(historyEntry&&location.hash!=="#paso-"+(current+1))history.pushState({v6Demo:true,v6step:current},"","#paso-"+(current+1));
    if(current===3)model.render(demoState);
    window.scrollTo({top:0,behavior:"instant"});
  }
  next.addEventListener("click",()=>{if(current<3)show(current+1)});
  back.addEventListener("click",()=>{
    if(current<=0)return;
    // Consume the actual browser history when the previous step was opened
    // inside this prototype; do not push an artificial back entry.
    if(history.state?.v6Demo===true)history.back();
    else show(current-1);
  });
  const brand=document.querySelector(".qt-brand");
  brand.setAttribute("href","#inicio");
  brand.setAttribute("aria-label","Quick Trip, volver al inicio");
  brand.addEventListener("click",event=>{event.preventDefault();showHome()});
  headerBack.addEventListener("click",()=>{
    if(current===0)showHome();
  });
  const parcelNotice=document.getElementById("qt-home-parcel-message");
  for(const button of document.querySelectorAll("[data-v6-service]")){
    button.addEventListener("click",()=>{
      const kind=button.dataset.v6Service;
      if(kind==="delivery"){
        parcelNotice.hidden=!parcelNotice.hidden;
        button.setAttribute("aria-expanded",String(!parcelNotice.hidden));
        return;
      }
      if(kind!=="now"&&kind!=="schedule")return;
      parcelNotice.hidden=true;
      document.querySelector('[data-v6-service="delivery"]').setAttribute("aria-expanded","false");
      const programmed=kind==="schedule";
      demoState.schedule.label=programmed?"Otra hora":"Ahora";
      for(const choice of document.querySelectorAll(".time-choice")){
        const picked=choice.dataset.time===demoState.schedule.label;
        choice.classList.toggle("is-picked",picked);
        choice.setAttribute("aria-pressed",String(picked));
      }
      document.getElementById("title-when").textContent=programmed?"¿Cuándo programamos?":"¿Cuándo querés viajar?";
      document.querySelector("#paso-1 .section-title p").textContent=programmed
        ?"Recorrido de tu viaje programado. Fecha y hora reales se activarán con los motores existentes."
        :"El recorrido, claro desde el principio.";
      model.render(demoState);
      show(0);
    });
  }
  window.addEventListener("popstate",()=>show(indexFromHash(),{historyEntry:false}));
  window.addEventListener("hashchange",()=>show(indexFromHash(),{historyEntry:false}));
  const themeKey="quicktrip-v6-theme";
  function applyTheme(mode){
    const dark=mode==="dark";
    document.body.classList.toggle("theme-dark",dark);
    document.body.classList.toggle("theme-light",!dark);
    document.documentElement.style.colorScheme=dark?"dark":"light";
    theme.setAttribute("aria-pressed",String(dark));
    theme.setAttribute("aria-label",dark?"Cambiar a modo claro":"Cambiar a modo oscuro");
    themeLabel.textContent=dark?"Oscuro":"Claro";
    const meta=document.querySelector('meta[name="theme-color"]');
    if(meta)meta.setAttribute("content",dark?"#041e22":"#f6faf5");
  }
  let saved="dark";
  try{const last=localStorage.getItem(themeKey);if(last==="light"||last==="dark")saved=last;}catch(_){}
  applyTheme(saved);
  theme.addEventListener("click",()=>{
    const mode=document.body.classList.contains("theme-dark")?"light":"dark";
    applyTheme(mode);
    try{localStorage.setItem(themeKey,mode);}catch(_){}
    message.textContent=mode==="dark"?"Modo oscuro":"Modo claro";
  });
  for(const choice of document.querySelectorAll(".time-choice")){
    choice.addEventListener("click",()=>{
      for(const target of document.querySelectorAll(".time-choice")){
        const selected=target===choice;
        target.classList.toggle("is-picked",selected);
        target.setAttribute("aria-pressed",String(selected));
      }
      demoState.schedule.label=choice.dataset.time;
      model.render(demoState);
      message.textContent=choice.dataset.time==="Otra hora"
        ?"Selección ilustrativa; el reloj real se conecta después del diseño.":"";
    });
  }
  for(const control of document.querySelectorAll("[data-demo]")){
    control.addEventListener("click",()=>{
      if(control.dataset.demo==="Inicio"){showHome();return;}
      message.textContent=control.dataset.demo+" se conectará después de aprobar el diseño.";
    });
  }
  show(indexFromHash(),{historyEntry:false});
})();