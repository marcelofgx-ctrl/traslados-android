"use strict";
/* QA ONLY: Chromium ServiceWorker/CacheStorage on a throwaway localhost origin.
 * Read-only against repo; v91 is generated only inside OS temp files.
 * A cross-app cache wipe MUST be reported as a failed isolation check.
 */
const fs=require("node:fs"),path=require("node:path"),os=require("node:os");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const out=process.env.QA_DIR||"quicktrip-v90-pilot-evidence/pwa-cache";
fs.mkdirSync(out,{recursive:true});
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),"qt-v90-sw-"));
const base="http://127.0.0.1:8194/";
const report={kind:"real-service-worker",state:"RUNNING",cases:[],findings:[],external:[],screenshots:[],isolatedOrigin:base};
const check=(name,pass,detail)=>{report.cases.push({name,pass:Boolean(pass),detail:detail===undefined?null:detail});if(!pass)report.findings.push(name);};
const wait=ms=>new Promise(r=>setTimeout(r,ms));
let server,browser;
(async()=>{
 try{
  const src="web-pasajero/dist";
  if(!fs.existsSync(path.join(src,"sw.js")))throw Error("Expected existing QA stage build first");
  fs.cpSync(src,tmp,{recursive:true});
  const swPath=path.join(tmp,"sw.js"),cssPath=path.join(tmp,"quicktrip-canonical-v86.css");
  const original=fs.readFileSync(swPath,"utf8");
  if(!original.includes('traslados-pwa-qa-route-v90-20261025'))throw Error("Not the authorized v90 Service Worker");
  server=spawn("python3",["-m","http.server","8194","--bind","127.0.0.1","--directory",tmp],{stdio:"ignore"});
  for(let i=0;i<60;i++){try{if((await fetch(base+"offline.html")).ok)break;}catch{}if(i===59)throw Error("Local SW test server not ready");await wait(150);}
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/chromium",
   args:["--no-sandbox","--disable-dev-shm-usage","--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1"]});
  const ctx=await browser.newContext({serviceWorkers:"allow"});
  ctx.on("request",request=>{const u=new URL(request.url());if(u.hostname!=="127.0.0.1")report.external.push(u.origin);});
  const page=await ctx.newPage();
  page.on("pageerror",e=>report.findings.push("PAGE_ERROR:"+e.message));
  await page.goto(base+"offline.html",{waitUntil:"load"});
  const seeded=await page.evaluate(async()=>{
   const names=["traslados-pwa-foreign-scope-QA","traslados-pwa-old-owned-QA","unrelated-app-QA"];
   for(const name of names){
    const cache=await caches.open(name);
    await cache.put("./qa-seed.txt",new Response("QA_SEED:"+name));
   }
   return names;
  });
  check("PWA local seeded caches for unrelated scope and prior version",seeded.length===3,seeded);
  await page.evaluate(async()=>{
   await navigator.serviceWorker.register("./sw.js",{scope:"./"});
   await navigator.serviceWorker.ready;
  });
  await page.waitForFunction(()=>!!navigator.serviceWorker.controller &&
   navigator.serviceWorker.controller.state==="activated",null,{timeout:20000});
  const keys=await page.evaluate(()=>caches.keys());
  check("PWA v90 installed and precache registered",keys.includes("traslados-pwa-qa-route-v90-20261025"),keys);
  const unrelatedSurvived=keys.includes("traslados-pwa-foreign-scope-QA");
  check("PWA cross-scope cache isolation (foreign prefix must survive)",unrelatedSurvived,keys);
  check("PWA retires obsolete owned prefixed cache",!keys.includes("traslados-pwa-old-owned-QA"),keys);
  check("PWA preserves cache outside its namespace",keys.includes("unrelated-app-QA"),keys);
  await page.screenshot({path:path.join(out,"01-service-worker-registered.png"),fullPage:false});
  report.screenshots.push("01-service-worker-registered.png");
  // Synthetic next-version files exist ONLY in the OS temporary QA folder.
  fs.writeFileSync(cssPath,fs.readFileSync(cssPath,"utf8")+"\n/* QT_QA_V91_SYNTHETIC */\n");
  const next=original.replace("traslados-pwa-qa-route-v90-20261025","traslados-pwa-qa-route-v91-synthetic");
  if(next===original)throw Error("Failed to generate synthetic SW version");
  fs.writeFileSync(swPath,next);
  fs.utimesSync(swPath,new Date(Date.now()+5000),new Date(Date.now()+5000));
  await page.evaluate(async()=>{const reg=await navigator.serviceWorker.ready;await reg.update();});
  await page.waitForFunction(async()=>(await caches.keys()).includes("traslados-pwa-qa-route-v91-synthetic"),null,{timeout:25000});
  await page.waitForFunction(async()=>(await caches.keys()).every(k=>k!=="traslados-pwa-qa-route-v90-20261025"),null,{timeout:18000});
  const fresh=await page.evaluate(async()=>{
   const response=await fetch("./quicktrip-canonical-v86.css?v=91");
   return {ok:response.ok,updated:(await response.text()).includes("QT_QA_V91_SYNTHETIC"),keys:await caches.keys()};
  });
  check("PWA v90 to synthetic v91 activation and old version eviction",fresh.keys.includes("traslados-pwa-qa-route-v91-synthetic")&&
   !fresh.keys.includes("traslados-pwa-qa-route-v90-20261025"),fresh);
  check("PWA v91 delivers fresh stylesheet without stale v90",fresh.ok&&fresh.updated,fresh);
  await ctx.setOffline(true);
  const offline=await page.evaluate(async()=>{
   const result={controlled:Boolean(navigator.serviceWorker.controller)};
   for(const v of [90,91]){
    try{const response=await fetch("./quicktrip-canonical-v86.css?v="+v);
     result["v"+v]={status:response.status,updated:(await response.text()).includes("QT_QA_V91_SYNTHETIC")};
    }catch(e){result["v"+v]={error:String(e)};}
   }
   try{const response=await fetch("./offline.html");
    result.page={ok:response.ok};}catch(e){result.page={error:String(e)};}
   return result;
  });
  check("PWA offline controlled document and fallback",offline.controlled&&offline.page.ok,offline);
  check("PWA offline v91 stylesheet stays current",offline.v91?.status===200&&offline.v91.updated,offline);
  check("PWA offline older query does not resurrect old stylesheet",offline.v90?.status===200&&offline.v90.updated,offline);
  check("PWA no real outbound request",report.external.length===0,report.external);
  report.state=report.cases.every(c=>c.pass)&&!report.findings.length?"PASS":"FAIL";
  await ctx.close();
 }catch(e){report.state="FAIL";report.findings.push("HARNESS:"+String(e.stack||e));}
 finally{
  if(browser)await browser.close();
  if(server)server.kill("SIGTERM");
  fs.rmSync(tmp,{recursive:true,force:true});
  fs.writeFileSync(path.join(out,"PWA_CACHE_V90_RESULT.json"),JSON.stringify(report,null,2)+"\n");
  console.log("PWA CACHE "+report.state+" "+report.cases.filter(c=>c.pass).length+"/"+report.cases.length+"; findings="+report.findings.length);
  console.log("PWA CACHE CASES "+JSON.stringify(report.cases));
  console.log("PWA CACHE FINDINGS "+JSON.stringify(report.findings));
  if(report.state!=="PASS")process.exitCode=1;
 }
})();