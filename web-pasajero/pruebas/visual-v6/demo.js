"use strict";
/* Independent visual study. Nothing here can query GPS, price, availability,
   account information or booking APIs. Static illustrative content only. */
(()=>{
  const screens=[...document.querySelectorAll("[data-screen]")];
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
  let current=0;
  const indexFromHash=()=>{
    const match=location.hash.match(/^#paso-([1-4])$/);
    return match?Number(match[1])-1:0;
  };
  function show(index,{historyEntry=true}={}){
    current=Math.max(0,Math.min(3,index));
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
  document.querySelector(".qt-brand").addEventListener("click",event=>{event.preventDefault();show(0)});
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
      message.textContent=control.dataset.demo+" se conectará después de aprobar el diseño.";
    });
  }
  show(indexFromHash(),{historyEntry:false});
})();