"use strict";
/* Quick Trip QA: early consent screen. GPS goes ONLY through the original
   origin picker; no duplicate geocoder, stored coordinates or backend writes. */
(function(root,doc){
  const STORE="quicktrip.gps.welcome.session.v1";
  let screen,title,description,steps,allow,manual,bridge,open=false,busy=false,mounted=false;
  const done=()=>{try{return root.sessionStorage.getItem(STORE)==="completed"}catch{return false}};
  const save=()=>{try{root.sessionStorage.setItem(STORE,"completed")}catch{}};
  function dismiss({saveChoice=false,focusOrigin=false}={}){
    open=false;busy=false;if(screen)screen.hidden=true;
    doc.body.classList.remove("qt-gps-gate-open");
    if(saveChoice)save();
    if(focusOrigin)doc.querySelector("#origin-picker input")?.focus({preventScroll:true});
  }
  function message(heading,copy,items=[]){
    title.textContent=heading;description.textContent=copy;
    steps.replaceChildren();
    for(const value of items){const li=doc.createElement("li");li.textContent=value;steps.append(li)}
    steps.hidden=!items.length;
    allow.textContent=items.length||/demorando|determinar|disponible/.test(heading)
      ?"↻ Volver a intentar":"⌖ Permitir ubicación";
  }
  function result(reply){
    busy=false;allow.disabled=false;
    if(reply?.ok){dismiss({saveChoice:true});return}
    const code=Number(reply?.code||0);
    if(code===1)message("Ubicación bloqueada por el navegador",
      "Activá el permiso para este sitio en Chrome o Samsung Internet.",[
      "Cerrá las burbujas flotantes que puedan tapar el permiso de Android.",
      "Entrá a los permisos de este sitio y permití Ubicación.",
      "Si hace falta, habilitá la ubicación precisa para el navegador en Ajustes de Android."
    ]);
    else if(code===3)message("La ubicación está demorando",
      "Activá el GPS y probá en un lugar con mejor señal.");
    else if(code===2)message("No pudimos determinar tu posición",
      "Revisá que el GPS del teléfono esté encendido y volvé a intentar.");
    else message("Ubicación no disponible",
      "Abrí Quick Trip en HTTPS con un navegador compatible o ingresá la dirección.");
    allow.focus({preventScroll:true});
  }
  function request(){
    if(!open||busy||!bridge?.start)return;
    if(!root.isSecureContext||!root.navigator?.geolocation){result({ok:false,code:0});return}
    busy=true;allow.disabled=true;description.textContent="Obteniendo la ubicación del teléfono…";
    steps.hidden=true;
    // Synchronous inside the user click: preserves Android's permission gesture.
    try{bridge.start(result)}catch{result({ok:false,code:0})}
  }
  function build(){
    if(screen)return;
    const add=(parent,tag,cls,id,text)=>{
      const el=doc.createElement(tag);if(cls)el.className=cls;
      if(id)el.id=id;if(text!==undefined)el.textContent=text;
      parent.append(el);return el;
    };
    screen=add(doc.body,"section",null,"qt-gps-welcome");screen.hidden=true;
    screen.setAttribute("role","dialog");screen.setAttribute("aria-modal","true");
    screen.setAttribute("aria-labelledby","qt-gps-welcome-title");
    const card=add(screen,"div","qt-gps-welcome-card");
    add(card,"p","qt-gps-welcome-brand",null,"⌖  QUICK TRIP · URUGUAY");
    const marker=add(card,"div","qt-gps-welcome-symbol",null,"◎");
    marker.setAttribute("aria-hidden","true");
    title=add(card,"h2",null,"qt-gps-welcome-title");
    description=add(card,"p","qt-gps-welcome-description");
    add(card,"p","qt-gps-welcome-note",null,
      "Solo proponemos tu origen. Podés corregirlo antes de solicitar el viaje.");
    steps=add(card,"ol","qt-gps-welcome-steps");steps.hidden=true;
    allow=add(card,"button","qt-gps-welcome-primary","qt-gps-welcome-confirm");
    allow.type="button";allow.addEventListener("click",request);
    manual=add(card,"button","qt-gps-welcome-manual","qt-gps-welcome-manual",
      "Escribir mi dirección");
    manual.type="button";
    manual.addEventListener("click",()=>dismiss({saveChoice:true,focusOrigin:true}));
    doc.addEventListener("keydown",e=>{
      if(!open)return;
      if(e.key==="Escape"&&!busy){e.preventDefault();dismiss({saveChoice:true,focusOrigin:true});return}
      if(e.key!=="Tab")return;
      const focusable=[allow,manual].filter(x=>!x.disabled);
      if(!focusable.length){e.preventDefault();return}
      const i=focusable.indexOf(doc.activeElement);
      if(e.shiftKey&&i<=0){e.preventDefault();focusable[focusable.length-1].focus()}
      else if(!e.shiftKey&&i===focusable.length-1){e.preventDefault();focusable[0].focus()}
    });
  }
  async function mount(next){
    if(mounted)return;mounted=true;bridge=next;
    if(!bridge?.start||bridge.hasOrigin?.()||done()||root.location.hash==="#instalar")return;
    build();open=true;screen.hidden=false;
    doc.body.classList.add("qt-gps-gate-open");
    message("Activá tu ubicación",
      "Para encontrar tu punto de recogida y mostrarlo en el mapa de Quick Trip.");
    allow.focus({preventScroll:true});
    let state="prompt";
    try{if(root.navigator?.permissions?.query)
      state=(await root.navigator.permissions.query({name:"geolocation"})).state
    }catch{/* Non-supporting browser: show consent button */}
    if(!open)return;
    if(state==="granted")request(); // Permission already authorized by the passenger.
    else if(state==="denied")result({ok:false,code:1});
    // state="prompt": NEVER request GPS before button click.
  }
  root.QuickTripGpsWelcome=Object.freeze({mount,isOpen:()=>open,dismiss});
})(window,document);
