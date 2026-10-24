"use strict";
/* Valida visualmente la URL real proporcionada por GitHub Actions,
   el CSP no-backend y el modo de sólo lectura.
   Sólo recursos estáticos públicos del CDN, jamás Supabase. */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {chromium}=require("playwright-core");
const qaDir=process.env.QA_DIR||"quicktrip-v85-link-qa";
fs.mkdirSync(qaDir,{recursive:true});
const url=process.env.QA_PREVIEW_URL;
if(!url||!url.startsWith("https://rawgit.net/marcelofgx-ctrl/traslados-android/"))throw Error("Invalid URL under QA");
const report={
  exactPreviewUrl:url,backend:"CSP connect-src none; preview read-only",
  onlineValidation:"PENDING",browser: "Chromium Android device emulation",
  actualSupabaseRequestsCompleted:0,remoteBookingWrites:0,
  navigatedToProd:false,images:[],checks:[],unexpected:[],blocked:[],
  screenshot:null
};
const must=(ok,label,detail)=>{report.checks.push({label,pass:!!ok,detail});assert(ok,label+": "+String(detail||""))};
async function main(){
 let browser,page;
 try {
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",args:["--no-sandbox","--disable-dev-shm-usage"]});
  const context=await browser.newContext({viewport:{width:393,height:852},deviceScaleFactor:3,isMobile:true,hasTouch:true,
    userAgent:"Mozilla/5.0 (Linux; Android 16; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
    locale:"es-UY",serviceWorkers:"block",reducedMotion:"reduce"});
  // rawgit.net muestra una confirmación de terceros antes del HTML;
  // validamos explícitamente el documento final sin modificar el sitio.
  await context.addCookies([{name:"__Http-phish",value:"1",url:"https://rawgit.net/",
    httpOnly:true,secure:true,sameSite:"Lax"}]);
  page=await context.newPage();
  let anyExternal=0,realSupabaseSuccess=0;const externalCompleted=[];
  page.on("request",req=>{
    if(/supabase\.co|direcciones\.ide\.uy|workers\.dev|tile\.openstreetmap\.org/.test(req.url())){
      report.blocked.push({url:new URL(req.url()).hostname,method:req.method(),type:req.resourceType()});
    }
    if(!req.url().startsWith("https://rawgit.net/"))anyExternal++;
  });
  page.on("response",response=>{
    if(!response.url().startsWith("https://rawgit.net/"))externalCompleted.push(response.url());
    if(/supabase\.co|direcciones\.ide\.uy|workers\.dev/.test(response.url()))realSupabaseSuccess++;
  });
  page.on("pageerror",e=>report.unexpected.push("PAGE "+e.message));
  page.on("requestfailed",r=>report.unexpected.push("REQUEST FAILED "+r.url()+" "+r.failure()?.errorText));
  page.on("response",r=>{if(r.status()>=400)report.unexpected.push("HTTP "+r.status()+" "+r.url());});
  const res=await page.goto(url,{waitUntil:"domcontentloaded",timeout:45000});
  must(res&&res.status()===200,"HTTP 200 en enlace QA",res?.status());
  let visible=await page.locator("#qt-public-qa-banner").count();
  // rawgit.net muestra aviso de contenido de terceros la primera vez.
  // Pulsarlo únicamente si el destino sigue siendo ESTA rama y el aviso es exacto.
  if(!visible){
    const text=await page.locator("body").innerText();
    const notice="You are opening third-party content";
    if(!text.includes(notice))throw Error("Unexpected host content: "+text.slice(0,300));
    const continueControl=page.getByText("Continue to this repository",{exact:true});
    if((await continueControl.count())!==1)throw Error("Missing unique explicit provider consent control");
    await continueControl.click();
    await page.locator("#qt-public-qa-banner").waitFor({state:"visible",timeout:20000});
    must(page.url().startsWith(url),"Consent returns to exact QA document",page.url());
    visible=await page.locator("#qt-public-qa-banner").count();
    report.hostInterstitial="Consent screen encountered and passed by explicit button";
  }
  must(await page.locator("#qt-public-qa-banner").isVisible(),"Banner visible",url);
  const guards=await page.evaluate(()=>({
    preview:Boolean(document.querySelector('meta[name="traslados-preview"]')),
    csp:document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content||"",
    title:document.title,
    registerScript:Boolean(document.querySelector('script[src*="quicktrip-v85-resilience.js"]')),
    urgentCount:document.querySelectorAll("#urgent-send").length,
    externalScripts:[...document.scripts].map(x=>x.src).filter(x=>x&&!x.startsWith(location.origin)),
    manifest:document.querySelector('link[rel="manifest"]')?.getAttribute("href")
  }));
  must(guards.preview,"Modo readonly activo",guards);
  must(guards.csp.includes("connect-src 'none'"),"CSP bloquea llamadas externas",guards.csp);
  must(guards.csp.includes("form-action 'none'"),"CSP bloquea envio de formularios",guards.csp);
  must(guards.title.includes("QA"),"Titulo identifica QA",guards.title);
  must(guards.registerScript,"Resiliencia v85 cargada en el documento",guards.registerScript);
  // rawgit.net inyecta en el HTML una etiqueta de analítica Cloudflare.
  // Está FUERA de la política script-src 'self': nunca debe cargar realmente.
  must(guards.externalScripts.every(src=>src.startsWith("https://static.cloudflareinsights.com/beacon.min.js/")),
    "Única etiqueta extra es telemetría del alojamiento bloqueada por CSP",guards.externalScripts);
  must(guards.csp.includes("script-src 'self'"),"Scripts ajenos bloqueados por CSP",guards.csp);
  must(externalCompleted.length===0,"Cero respuestas de scripts/servicios externos",externalCompleted);
  must(guards.urgentCount===1,"Un único CTA original",guards.urgentCount);
  await page.locator('#view-inicio.active [data-service="now"]').click({timeout:16000});
  await page.locator('#view-viaje.active .qtc-app').waitFor({state:"visible",timeout:16000});
  await page.waitForFunction(()=>history.state?.quickTripV85View==="viaje",null,{timeout:8000});
  const state=await page.evaluate(()=>({view:document.querySelector(".view.active")?.id,
    ctaDisabled:document.querySelector("#urgent-send")?.disabled,
    logo:Boolean(document.querySelector(".qtc-brand"))}));
  must(state.ctaDisabled===true,"Solicitud de viaje deshabilitada",state);
  await page.screenshot({path:path.join(qaDir,"01-preview-qa-samsung-real-browser.png"),fullPage:true,animations:"disabled"});
  report.screenshot="01-preview-qa-samsung-real-browser.png";
  await page.goBack({waitUntil:"domcontentloaded",timeout:15000});
  must(await page.locator("#view-inicio.active").count()===1,"Atrás vuelve a portada",page.url());
  await page.screenshot({path:path.join(qaDir,"02-preview-qa-samsung-inicio.png"),fullPage:true,animations:"disabled"});
  report.images.push("02-preview-qa-samsung-inicio.png");
  // La CSP connect-src none impide fetch() incluso al mismo origen: correcto.
  // El navegador usa link[rel=manifest], no un fetch JS. El JSON original
  // se valida por separado en la etapa de GitHub Actions.
  const manifestHref=await page.locator('link[rel="manifest"]').getAttribute("href");
  must(manifestHref==="./manifest.webmanifest","Documento QA enlaza manifiesto propio",manifestHref);
  must(realSupabaseSuccess===0,"0 respuestas reales de Supabase",realSupabaseSuccess);
  report.actualSupabaseRequestsCompleted=realSupabaseSuccess;
  report.unexpectedNetworkRequests=anyExternal;
  report.onlineValidation="PASS";
  console.log("PASS QA URL VERIFICADA "+JSON.stringify(report,null,2));
  await context.close();
 }catch(e){
  report.onlineValidation="FAIL";
  report.failure=String(e.stack||e);
  if(page){
    try{
      report.domDebug=await page.evaluate(()=>({view:document.querySelector(".view.active")?.id,
        ready:document.readyState,api:typeof window.TrasladosServiceHome,
        domQtc:document.querySelector(".qtc-app")?.outerHTML.slice(0,120)||null}));
      await page.screenshot({path:path.join(qaDir,"ERROR-preview-qa-samsung.png"),fullPage:true,timeout:5000});
    }catch{}
  }
  console.error("FAIL QA LINK VALIDATION "+report.failure+" "+JSON.stringify(report.domDebug||{})+" "+JSON.stringify(report.unexpected.slice(-16)));
  process.exitCode=1;
 }finally{
  if(browser)await browser.close();
  fs.writeFileSync(path.join(qaDir,"RESULTADOS-ENLACE-QA-v85.json"),JSON.stringify(report,null,2)+"\n");
 }
}
main();