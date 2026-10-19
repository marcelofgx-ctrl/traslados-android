"use strict";
/* MAP-FIRST V5: browser/Android Back history + BFCache foreground recovery.
 * Preview-only coordinator. It does not handle bookings, coords, identity,
 * pricing or road providers. The original app functions remain authoritative. */
(function(root){
  const FLAG="trasladosMapFirstNav";
  function create({history,doc,restoreView,restoreStage,recover,on=null}){
    if(!doc.querySelector('meta[name="traslados-preview"]'))return null;
    let mounted=false,restoring=false,current=null,recoverTimer=null,depth=0;
    const views=new Set(["inicio","viaje","reservar","viajes","cuenta","seguimiento","menu","instalar"]);
    const stages=new Set(["route","when","passenger","confirm"]);
    const activeView=()=>doc.querySelector(".view.active")?.id?.replace(/^view-/,"")||"inicio";
    const snapshot=(view,stage)=>{
      const v=views.has(view)?view:"inicio";
      return{view:v,stage:v==="viaje"&&stages.has(stage)?stage:"route"};
    };
    const inPreviewState=state=>state&&typeof state==="object"&&state[FLAG]&&views.has(state[FLAG].view);
    function record(state,replace=false){
      const old=history.state&&typeof history.state==="object"?history.state:{};
      // Keep one logical Android history per visible stage. Depth lets the
      // in-app Atrás button reuse browser history instead of pushing a new
      // backwards-looking state (which previously made Back oscillate).
      if(replace)depth=Number.isInteger(old?.[FLAG]?.depth)?old[FLAG].depth:0;
      else depth++;
      const next={...old,[FLAG]:{...state,depth}};
      try{
        if(replace)history.replaceState(next,"",root.location.href);
        else history.pushState(next,"",root.location.href);
      }catch{/* Browser privacy rules: never block travel form. */}
      current=state;
    }
    function onView(name){
      if(!mounted||restoring||!views.has(name))return;
      const next=snapshot(name,name==="viaje"?"route":null);
      if(current?.view===next.view)return;
      record(next);
      if(name==="viaje")scheduleRecover("re-enter");
    }
    function onStage(name){
      if(!mounted||restoring||activeView()!=="viaje"||!stages.has(name))return;
      if(current?.view==="viaje"&&current.stage===name)return;
      record(snapshot("viaje",name));
    }
    function scheduleRecover(reason){
      if(!mounted)return;
      if(recoverTimer!==null)root.clearTimeout(recoverTimer);
      recoverTimer=root.setTimeout(()=>{
        recoverTimer=null;
        if(doc.visibilityState==="hidden"||!root.navigator.onLine)return;
        if(activeView()==="viaje"||activeView()==="reservar")recover(reason);
      },180);
    }
    function pop(event){
      const state=inPreviewState(event.state)?event.state[FLAG]:snapshot("inicio");
      const next=snapshot(state.view,state.stage);
      depth=Number.isInteger(state.depth)?Math.max(0,state.depth):0;
      restoring=true;
      try{
        restoreView(next.view);
        if(next.view==="viaje")restoreStage(next.stage);
        current=next;
      }finally{restoring=false;}
      scheduleRecover("android-back");
    }
    function mount(){
      if(mounted)return;
      mounted=true;
      const state=snapshot(activeView(),doc.getElementById("view-viaje")?.dataset?.premiumStep);
      record(state,true);
      root.addEventListener("popstate",pop);
      root.addEventListener("pageshow",event=>{
        if(event.persisted)scheduleRecover("bfcache");
      });
      doc.addEventListener("visibilitychange",()=>{
        if(doc.visibilityState==="visible")scheduleRecover("foreground");
      });
      root.addEventListener("online",()=>scheduleRecover("online"));
    }
    function backWithinApp(){
      if(!mounted||restoring||depth<=0||current?.view!=="viaje"||current.stage==="route")return false;
      // Delegating to browser history prevents Back -> stage previously left
      // -> Back loops when mixing touchscreen and physical Android Back.
      history.back();
      return true;
    }
    return Object.freeze({mount,onView,onStage,backWithinApp,recoverNow:scheduleRecover,diagnostics:()=>({mounted,restoring,view:current?.view,stage:current?.stage,depth})});
  }
  root.TrasladosPreviewNavigation=Object.freeze({create});
})(window);
