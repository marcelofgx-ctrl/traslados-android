"use strict";
/* Independent visual study. Nothing here can query GPS, price, availability,
   account information or booking APIs. Static illustrative content only. */
(()=>{
  const screens=[...document.querySelectorAll("[data-screen]")];
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
    if(historyEntry&&location.hash!=="#paso-"+(current+1))history.pushState({v6step:current},"","#paso-"+(current+1));
    if(current===3)document.getElementById("selected-time").textContent=
      document.querySelector(".time-choice.is-picked")?.dataset.time||"Ahora";
    window.scrollTo({top:0,behavior:"instant"});
  }
  next.addEventListener("click",()=>{if(current<3)show(current+1)});
  back.addEventListener("click",()=>{if(current>0)show(current-1)});
  document.querySelector(".brand").addEventListener("click",event=>{event.preventDefault();show(0)});
  window.addEventListener("popstate",()=>show(indexFromHash(),{historyEntry:false}));
  window.addEventListener("hashchange",()=>show(indexFromHash(),{historyEntry:false}));
  theme.addEventListener("click",()=>{
    const dark=document.body.classList.toggle("theme-dark");
    theme.setAttribute("aria-pressed",String(dark));
    themeLabel.textContent=dark?"Marfil":"Nocturno";
    message.textContent=dark?"Variante petróleo · prototipo":"Variante marfil · prototipo";
  });
  for(const choice of document.querySelectorAll(".time-choice")){
    choice.addEventListener("click",()=>{
      for(const target of document.querySelectorAll(".time-choice")){
        const selected=target===choice;
        target.classList.toggle("is-picked",selected);
        target.setAttribute("aria-pressed",String(selected));
      }
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