"use strict";
const { readFileSync }=require("node:fs");
const { join }=require("node:path");
const { runInNewContext }=require("node:vm");
const { strict:assert }=require("node:assert");
const root=join(__dirname,"..");
const js=readFileSync(join(root,"app.js"),"utf8");
const html=readFileSync(join(root,"index.html"),"utf8");
const css=readFileSync(join(root,"styles.css"),"utf8");
const sw=readFileSync(join(root,"sw.js"),"utf8");
const start=js.indexOf("// La instalación automática depende del navegador");
const end=js.indexOf("\nfunction init(){",start);
assert(start>=0&&end>start,"Function installUI must have explicit browser fallback");
assert(html.includes('id="install-guide"')&&html.includes('id="install-copy-link"'));
assert(css.includes(".install-guide[hidden]"));
assert(html.includes("./app.js?v=19")&&html.includes("./styles.css?v=19"));
assert(sw.includes("traslados-cliente-pwa-v19"));
assert(html.includes('id="install-apk-option" hidden')&&html.includes("traslados-cliente-v11.5-r12.apk"));
assert(css.includes(".install-apk-option[hidden]"));

function setup({standalone=false,agent="Mozilla/5.0 (Linux; Android 16) Chrome/140",apkAvailable=false}={}){
  const events={};let copied="",visited="",apkClicks=0;
  const element=(id)=>({
    id,textContent:"",hidden:id==="install-guide",disabled:false,children:[],listeners:{},
    addEventListener(type,handler){this.listeners[type]=handler;},
    replaceChildren(){this.children=[];},
    appendChild(node){this.children.push(node);},
  });
  const elements=Object.fromEntries([
    "install-action","install-info","install-guide","install-guide-title",
    "install-guide-steps","install-guide-note","install-copy-link",
    "install-apk-option","install-apk-link","install-apk-web-guide"
  ].map(id=>[id,element(id)]));
  elements["install-apk-link"].href="https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-v11.5-r12.apk";
  elements["install-apk-link"].click=()=>{apkClicks++;};
  const browser={
    matchMedia:()=>({matches:standalone}),
    location:{href:"https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/"},
    addEventListener:(type,handler)=>{events[type]=handler;},
  };
  const navigator={
    userAgent:agent,standalone:false,
    clipboard:{async writeText(str){copied=str;}}
  };
  const ctx={
    window:browser,navigator,document:{readyState:"complete"},
    $:id=>elements[id],text:(parent,tag,value)=>{const node=element(tag);node.textContent=value;parent.appendChild(node);return node;},
    shiftView:to=>{visited=to;},URL,Boolean,
    fetch:async(url,opts)=>({
      ok:apkAvailable&&opts.method==="HEAD"&&String(url).endsWith(".apk"),
      headers:{get:k=>k==="content-type"?"application/vnd.android.package-archive":k==="content-length"?"144012":""}
    }),
  };
  const fn=runInNewContext("let installEvent=null;\n"+js.slice(start,end)+"\ninstallUI;",ctx);
  fn();
  return {elements,events,click:async()=>elements["install-action"].listeners.click(),
    copy:async()=>elements["install-copy-link"].listeners.click(),
    getCopied:()=>copied,view:()=>visited,apkClicks:()=>apkClicks};
}

(async()=>{
  const settled=async()=>{await Promise.resolve();await new Promise(setImmediate);};
  const noEvent=setup();
  await settled();
  assert.equal(noEvent.elements["install-action"].textContent,"VER CÓMO INSTALAR");
  await noEvent.click();
  assert.equal(noEvent.elements["install-guide"].hidden,false,"Fallback should be persistent");
  assert.equal(noEvent.elements["install-guide-steps"].children.length,3,"Actionable three steps");
  assert.match(noEvent.elements["install-guide-title"].textContent,/Chrome/);
  assert.match(noEvent.elements["install-guide-steps"].children.map(x=>x.textContent).join(" "),/Instalar y crear acceso directo/);
  assert.match(noEvent.elements["install-guide-note"].textContent,/Crear acceso directo/);
  await noEvent.copy();
  assert.match(noEvent.getCopied(),/github\.io\/traslados-android\/web-pasajero\/$/);
  const apkFallback=setup({apkAvailable:true});
  await settled();
  assert.equal(apkFallback.elements["install-action"].textContent,"DESCARGAR TRASLADOS PARA ANDROID");
  assert.equal(apkFallback.elements["install-apk-option"].hidden,false,"APK should be visible only after real HEAD success");
  await apkFallback.click();
  assert.equal(apkFallback.apkClicks(),1,"APK must download only after explicit tap");
  const noAndroid=setup({agent:"Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Safari/605",apkAvailable:true});
  await settled();
  assert.equal(noAndroid.elements["install-apk-option"].hidden,true,"APK should be hidden for iPhone");
  const installable=setup({apkAvailable:true});let promptCalls=0,prevented=false;
  await settled();
  installable.events.beforeinstallprompt({
    preventDefault(){prevented=true;},
    async prompt(){promptCalls++;},
    userChoice:Promise.resolve({outcome:"accepted"})
  });
  assert(prevented,"Browser controls auto installation prompt");
  assert.equal(installable.elements["install-action"].textContent,"INSTALAR TRASLADOS");
  await installable.click();
  assert.equal(promptCalls,1,"Native installer must run on user click");
  assert.match(installable.elements["install-info"].textContent,/Confirmaste/);
  assert.equal(installable.apkClicks(),0,"Prefer PWA when Chrome offers installation");
  const installed=setup({standalone:true,apkAvailable:true});
  await settled();
  assert.equal(installed.elements["install-apk-option"].hidden,true,"No APK offered inside already installed PWA");
  assert.equal(installed.elements["install-action"].textContent,"ABRIR MIS TRASLADOS");
  await installed.click();
  assert.equal(installed.view(),"viajes","Already installed must open trips instead of failing");
  const failed=setup();
  failed.events.beforeinstallprompt({
    preventDefault(){},async prompt(){throw Error("blocked")},
    userChoice:Promise.resolve({outcome:"dismissed"})
  });
  await failed.click();
  assert.equal(failed.elements["install-guide"].hidden,false,"Failed installer must show persistent alternative");
  const ios=setup({agent:"Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Safari/605"});
  await ios.click();
  assert.match(ios.elements["install-guide-title"].textContent,/Safari/);
  console.log("PASS: PWA priority; signed APK HEAD fallback; manual download; no broken link; installed/iOS/Chrome states.");
})().catch(e=>{console.error(e);process.exitCode=1;});
