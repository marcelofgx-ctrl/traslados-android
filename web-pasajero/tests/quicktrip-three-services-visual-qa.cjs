"use strict";
// Capturas visuales reales de la PWA. Bloquea TODO tráfico externo, sin reservas reales.
// La vista previa agrega un meta read-only solo en memoria del navegador (no en Git).
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const dest=process.env.QA_DIR||path.resolve("quicktrip-web-qa-screenshots");
fs.mkdirSync(dest,{recursive:true});
async function run(){
  const server=spawn("python3",["-m","http.server","8099","--bind","127.0.0.1"],{cwd:process.cwd(),stdio:"ignore"});
  let browser;
  const results=[],allErrors=[];
  try{
    for(let n=0;n<35;n++){
      try{const res=await fetch("http://127.0.0.1:8099/web-pasajero/");if(res.ok)break;}catch{}
      if(n===34)throw Error("No pudo arrancar servidor web de QA");
      await new Promise(r=>setTimeout(r,180));
    }
    browser=await chromium.launch({
      headless:true,
      executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
      args:["--no-sandbox","--disable-dev-shm-usage"]
    });
    for(const width of [393,1280]){
      const page=await browser.newPage({viewport:{width,height:width===393?852:900},deviceScaleFactor:1,isMobile:width===393,hasTouch:width===393,reducedMotion:"reduce"});
      page.on("pageerror",e=>allErrors.push(width+": "+e.message));
      await page.route("**/*",async route=>{
        const url=new URL(route.request().url());
        if(url.hostname!=="127.0.0.1"){return route.abort();}
        if(url.pathname==="/web-pasajero/"||url.pathname==="/web-pasajero/index.html"){
          const response=await route.fetch();
          const body=(await response.text()).replace("<head>","<head><meta name=\"traslados-preview\" content=\"read-only\">");
          return route.fulfill({response,body});
        }
        return route.continue();
      });
      await page.goto("http://127.0.0.1:8099/web-pasajero/",{waitUntil:"load"});
      await page.waitForTimeout(500);
      assert.equal(await page.locator("#view-inicio.active").count(),1,"Portada debe permanecer visible al iniciar");
      assert.equal(await page.locator("#view-viaje.active").count(),0,"No abrir Tu Viaje automáticamente");
      const list=await page.locator("#view-inicio [data-service]").evaluateAll(nodes=>nodes.map(n=>n.dataset.service));
      assert.deepEqual(list,["now","delivery","schedule"],"Orden de las opciones");
      await page.screenshot({path:path.join(dest,`01-portada-${width}.png`),fullPage:true,animations:"disabled"});
      await page.locator('#view-inicio [data-service="delivery"]').click();
      assert(await page.locator("#service-coming-soon").isVisible(),"Tu Paquete debe informar que no está disponible");
      assert.equal(await page.locator("#view-reservar.active").count(),0,"Tu Paquete no puede iniciar reserva");
      await page.screenshot({path:path.join(dest,`02-tu-paquete-${width}.png`),fullPage:true,animations:"disabled"});
      await page.locator('#view-inicio [data-service="now"]').click();
      await page.locator("#view-viaje.active").waitFor({state:"visible",timeout:10000});
      assert.equal(await page.locator("#view-viaje.active").count(),1);
      await page.screenshot({path:path.join(dest,`03-tu-viaje-${width}.png`),fullPage:true,animations:"disabled"});
      await page.locator("#view-viaje .journey-back").first().click();
      await page.locator("#view-inicio.active").waitFor({state:"visible",timeout:10000});
      await page.locator('#view-inicio [data-service="schedule"]').click();
      await page.locator("#view-reservar.active").waitFor({state:"visible",timeout:10000});
      assert(await page.locator("#booking-service-caption").textContent().then(t=>t.includes("VIAJE PROGRAMADO")));
      await page.screenshot({path:path.join(dest,`04-viaje-programado-${width}.png`),fullPage:true,animations:"disabled"});
      const overflows=await page.evaluate(()=>Math.max(0,document.documentElement.scrollWidth-innerWidth));
      assert(overflows<=2,"Desbordamiento horizontal: "+overflows+"px");
      results.push({width,portada:"PASS",paquete:"PASS",viaje:"PASS",programado:"PASS",outbound:"BLOCKED",overflow:overflows});
      await page.close();
    }
    assert.deepEqual(allErrors,[],"Excepciones JavaScript: "+allErrors.join("; "));
    fs.writeFileSync(path.join(dest,"QA-RESULTADOS.json"),JSON.stringify({result:"PASS",screenshots:8,checks:results,javaScriptErrors:allErrors},null,2)+"\n");
    console.log("PASS Quick Trip PWA v84, 8 capturas y navegación real sin Internet externo ni Supabase.");
  }finally{
    if(browser)await browser.close();
    server.kill("SIGTERM");
  }
}
run().catch(e=>{console.error("FAIL QA",e);process.exitCode=1});
