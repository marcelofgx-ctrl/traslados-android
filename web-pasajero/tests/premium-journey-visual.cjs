"use strict";
/* QA de layout real en Chromium: no contacta la DB ni envía reservas. */
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const {chromium}=require("playwright-core");
(async()=>{
  const executable=process.env.CHROME_PATH||"/usr/bin/google-chrome";
  const browser=await chromium.launch({headless:true,executablePath:executable,args:["--no-sandbox","--disable-dev-shm-usage"]});
  const dest=process.env.QA_DIR||path.join(__dirname,"../../premium-qa-screenshots");
  fs.mkdirSync(dest,{recursive:true});
  const widths=[360,393,412],errors=[];
  for(const width of widths){
    const page=await browser.newPage({viewport:{width,height:852},deviceScaleFactor:1,isMobile:true,hasTouch:true});
    page.on("pageerror",e=>errors.push(width+": "+e.message));
    await page.route(/https:\/\/(zetaudvvutlouiqxopvg\.supabase\.co|direcciones\.ide\.uy|traslados-web\.marcelof-gx\.workers\.dev)\/.*/,r=>r.abort());
    await page.goto("http://127.0.0.1:8099/web-pasajero/",{waitUntil:"domcontentloaded"});
    await page.waitForSelector('#view-inicio.active [data-service="now"]',{timeout:12000});
    const services=await page.locator('#view-inicio [data-service]').count();
    assert.equal(services,3,"Portada: tres servicios independientes");
    await page.screenshot({path:path.join(dest,"portada-"+width+".png"),fullPage:true});
    await page.locator('#view-inicio [data-service="now"]').click();
    await page.waitForSelector('#view-viaje[data-premium-step="route"]',{timeout:10000});
    assert.equal(await page.locator(".premium-step").count(),4);
    await page.screenshot({path:path.join(dest,"recorrido-"+width+".png"),fullPage:true});
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"route","El formulario no debe avanzar sin origen/destino");
    assert.equal(await page.locator(".premium-error").isVisible(),true);
    await page.locator('[data-booking-step="route"]').evaluate(el=>el.classList.add("is-complete"));
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"when");
    await page.locator(".premium-time-choice[data-pickup='10']").click();
    assert.equal(await page.locator("#journey-when-select").inputValue(),"10");
    assert.equal(await page.locator("#journey-clock-action").isVisible(),true,"Reloj 24 h debe estar visible");
    assert.equal(await page.locator(".premium-when-extras").count(),1,"Disponibilidad y pasajeros deben permanecer accesibles");
    assert.equal(await page.locator(".premium-when-extras").evaluate(e=>e.open),false,"Panel secundario plegado por defecto");
    await page.locator(".premium-when-extras>summary").click();
    assert.equal(await page.locator("#passengers").isVisible(),true,"Cantidad de pasajeros preservada");
    await page.locator(".premium-when-extras>summary").click();
        await page.screenshot({path:path.join(dest,"cuando-"+width+".png"),fullPage:true});
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"passenger");
    await page.screenshot({path:path.join(dest,"datos-"+width+".png"),fullPage:true});
    await page.locator('[data-booking-step="passenger"]').evaluate(el=>el.classList.add("is-complete"));
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"confirm");
    const iconSize=await page.locator("#view-viaje .request-hero-icon svg").evaluate(e=>e.getBoundingClientRect().width);
    assert(iconSize<=32,"SVG de confirmar desbordado: "+iconSize);
    assert.equal(await page.locator("#premium-confirm-fake-location").count(),0,"Nunca agregar ubicaciones ficticias");
        await page.screenshot({path:path.join(dest,"confirmar-"+width+".png"),fullPage:true});
    const overflow=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,viewport:innerWidth}));
    assert(overflow.scroll<=overflow.viewport+2,"Desbordamiento horizontal en "+width+": "+JSON.stringify(overflow));
    // Se comprueba el botón final, SIN pulsarlo; la QA nunca reserva.
    assert(await page.locator("#urgent-send").count()===1);
    await page.close();
  }
  await browser.close();
  assert.equal(errors.length,0,"Errores de JavaScript: "+errors.join("; "));
  console.log("PASS visual mobile 360/393/412: portada, recorrido, cuándo, datos, confirmar, cero envíos y sin desbordamientos");
})().catch(e=>{console.error(e);process.exitCode=1;});
