"use strict";
/* Keep the active address and real suggestions above the software keyboard. */
(function(root){
  let active=null,frame=0;
  const viewport=()=>root.visualViewport;
  function stop(){
    active=null;root.document.body.classList.remove("address-search-active");
  }
  function layout(){
    frame=0;
    if(!active)return;
    const {input,results}=active,doc=root.document;
    if(!input.isConnected||doc.activeElement!==input||input.offsetParent===null){stop();return;}
    if(root.innerWidth>650||viewport()?.scale>1.05){stop();return;}
    doc.body.classList.add("address-search-active");
    const v=viewport(),top=(v?.offsetTop||0)+16,bottom=(v?.offsetTop||0)+(v?.height||root.innerHeight)-12;
    const field=input.getBoundingClientRect();
    // Leave room for suggestions, not just for the text caret. No smooth scroll
    // races with the keyboard animation and no scroll on ordinary list gestures.
    if(field.top<top||field.bottom+120>bottom)
      root.scrollBy({top:field.top-top,behavior:"instant"});
    const listTop=results.getBoundingClientRect().top;
    results.style.maxHeight=Math.max(44,Math.min(260,bottom-listTop))+"px";
  }
  function schedule(){if(!frame)frame=root.requestAnimationFrame(layout);}
  root.addEventListener("resize",schedule);
  viewport()?.addEventListener("resize",schedule);
  viewport()?.addEventListener("scroll",schedule);
  root.document.addEventListener("focusin",event=>{
    if(active&&event.target!==active.input&&!active.results.contains(event.target))stop();
  });
  function attach(input,results){
    const entry={input,results};
    input.addEventListener("focus",()=>{active=entry;schedule();});
    input.addEventListener("input",()=>{if(root.document.activeElement===input){active=entry;schedule();}});
    input.addEventListener("blur",event=>{
      // A suggestion receives focus before click. Keep its position stable
      // until the selection commits (especially during remote resolution).
      if(active===entry&&!results.contains(event.relatedTarget))stop();
    });
    const observer=new MutationObserver(()=>{
      if(active!==entry)return;
      if(input.offsetParent===null)stop();else schedule();
    });
    observer.observe(results,{childList:true});
    // Removed stop pickers must not be retained by window resize handlers.
    // The only global reference is the focused entry, released on blur/layout.
  }
  root.TrasladosAddressViewport=Object.freeze({attach});
})(window);
