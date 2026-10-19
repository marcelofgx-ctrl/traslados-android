"use strict";
/* Acordeón accesible para el formulario premium.
 * Solo controla presentación: nunca borra campos ni altera una reserva,
 * el motor vial, Supabase, disponibilidad o datos del conductor.
 */
(function(root){
  const ORDER=["route","when","passenger"];
  function mount(documentRoot){
    const elements=new Map();
    for(const node of documentRoot.querySelectorAll("[data-booking-step]")){
      const key=node.dataset.bookingStep;
      if(!ORDER.includes(key))continue;
      const toggle=node.querySelector("[data-step-toggle]");
      const content=node.querySelector("[data-step-body]");
      const summary=node.querySelector("[data-step-summary]");
      const check=node.querySelector("[data-step-check]");
      const extra=node.querySelector("[data-step-extra]");
      if(!toggle||!content||!summary||!check)throw new Error("Faltan controles accesibles del formulario");
      elements.set(key,{node,toggle,content,summary,check,extra});
    }
    if(elements.size!==ORDER.length)throw new Error("Faltan pasos del formulario");
    const progressText=documentRoot.getElementById("booking-progress-text");
    const progressBar=documentRoot.getElementById("booking-progress-bar");
    const progressFill=documentRoot.getElementById("booking-progress-fill");
    let current="route";
    const completed=new Set();
    function paint(){
      for(const [key,parts] of elements){
        const opened=key===current;
        parts.content.hidden=!opened;
        parts.toggle.setAttribute("aria-expanded",String(opened));
        parts.node.classList.toggle("is-open",opened);
        parts.node.classList.toggle("is-complete",completed.has(key));
        parts.check.textContent=completed.has(key)?"✓":"";
        parts.check.hidden=!completed.has(key);
      }
      const visibleStep=Math.max(1,ORDER.indexOf(current)+1);
      if(progressText)progressText.textContent="Paso "+visibleStep+" de 3 · "+completed.size+" completado"+(completed.size===1?"":"s");
      if(progressBar)progressBar.setAttribute("aria-valuenow",String(completed.size));
      if(progressFill)progressFill.style.width=(completed.size/ORDER.length*100)+"%";
    }
    function open(key,{scroll=false}={}){
      if(!elements.has(key))return;
      current=key;
      paint();
      if(scroll){
        const node=elements.get(key).node;
        node.scrollIntoView?.({behavior:"smooth",block:"start"});
      }
    }
    function toggle(key){
      if(!elements.has(key))return;
      current=current===key?null:key;
      paint();
    }
    function summary(key,description,done=false,extraText=""){
      const parts=elements.get(key);
      if(!parts)return;
      parts.summary.textContent=String(description||"");
      if(parts.extra){parts.extra.textContent=String(extraText||"");parts.extra.hidden=!extraText;}
      if(done)completed.add(key);else completed.delete(key);
      paint();
    }
    function active(){return current;}
    for(const [key,parts] of elements){
      parts.toggle.addEventListener("click",()=>toggle(key));
    }
    paint();
    return Object.freeze({open,toggle,summary,active});
  }
  root.TrasladosBookingSteps=Object.freeze({mount});
})(window);
