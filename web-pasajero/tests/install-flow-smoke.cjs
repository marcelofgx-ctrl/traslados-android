"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.resolve(__dirname,"..");
const js=fs.readFileSync(path.join(root,"app.js"),"utf8");
const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
const sw=fs.readFileSync(path.join(root,"sw.js"),"utf8");
const start=js.indexOf("// La instalación automática depende del navegador");
const end=js.indexOf("\nfunction init(){",start);
assert(start>=0&&end>start,"PWA install UI must be present");
assert(!html.includes("install-apk-option")&&!html.includes("install-apk-link"));
assert(!html.includes("traslados-cliente-premium-v"));
assert(!html.includes("public-config.js"));
assert(!js.slice(start,end).includes("apkLink"));
assert(!js.slice(start,end).includes("clientApkUrl"));
assert(html.includes('id="install-guide"')&&html.includes('id="install-copy-link"'));
const v=Number(html.match(/\.\/app\.js\?v=(\d+)/)?.[1]);
assert.equal(v,82);
assert(sw.includes("traslados-pwa-v"+v),"PWA cache matches frontend version");
function setup({standalone=false,agent="Mozilla/5.0 (Linux; Android 16) Chrome/140"}={}){
  const events={},el={},steps=[];
  let copied="",view="",downloads=0;
  for(const id of ["install-action","install-info","install-guide","install-guide-title",
    "install-guide-steps","install-guide-note","install-copy-link"]){
    el[id]={id,textContent:"",hidden:id==="install-guide",disabled:false,listeners:{},children:[],
      addEventListener(t,f){this.listeners[t]=f;},
      replaceChildren(){this.children=[];},
      appendChild(node){this.children.push(node);}
    };
  }
  const window={location:{href:"https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/"},
    isSecureContext:true,top:null,self:null,
    matchMedia:()=>({matches:standalone}),
    addEventListener:(event,fn)=>{events[event]=fn;}};
  window.top=window;window.self=window;
  const navigator={userAgent:agent,standalone:false,
    clipboard:{async writeText(s){copied=s;}}};
  const document={readyState:"complete"};
  const ctx={window,navigator,document,
    $:id=>el[id],text:(p,t,val)=>{const n={textContent:val};p.appendChild(n);return n;},
    shiftView:t=>{view=t;},URL,Boolean};
  vm.runInNewContext("let installEvent=null;\n"+js.slice(start,end)+"\ninstallUI()",ctx);
  return {el,events,click:()=>el["install-action"].listeners.click(),
    copy:()=>el["install-copy-link"].listeners.click(),
    getCopied:()=>copied,getView:()=>view,getDownloads:()=>downloads};
}
(async()=>{
  const a=setup();
  assert.equal(a.el["install-action"].textContent,"VER CÓMO INSTALAR");
  await a.click();
  assert.equal(a.el["install-guide"].hidden,false);
  assert(a.el["install-guide-steps"].children.length>=3);
  await a.copy();
  assert.match(a.getCopied(),/\/web-pasajero\/$/);
  const b=setup({standalone:true});
  assert.equal(b.el["install-action"].textContent,"ABRIR MIS TRASLADOS");
  await b.click();
  assert.equal(b.getView(),"viajes");
  const c=setup({agent:"Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Safari/605"});
  await c.click();
  assert.match(c.el["install-guide-title"].textContent,/Safari/);
  const d=setup();
  let prompted=0,prevented=0;
  d.events.beforeinstallprompt({
    preventDefault(){prevented++;},
    prompt:async()=>{prompted++;},
    userChoice:Promise.resolve({outcome:"accepted"})
  });
  assert.equal(d.el["install-action"].textContent,"INSTALAR TRASLADOS");
  await d.click();
  assert.equal(prompted,1);
  assert.equal(prevented,1);
  assert.equal(d.el["install-action"].textContent,"INSTALACIÓN SOLICITADA");
  const old=setup({agent:"Mozilla/5.0 (Android 16) TrasladosClienteNative/18"});
  assert.equal(old.el["install-action"].textContent,"ABRIR MIS TRASLADOS");
  assert.equal(old.getDownloads(),0);
  console.log("PASS unified web/PWA browser install, menu fallback, no Cliente APK");
})().catch(e=>{console.error(e);process.exitCode=1;});
