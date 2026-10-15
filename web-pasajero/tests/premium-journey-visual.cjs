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
    const page=await browser.newPage({viewport:{width,height:852},deviceScaleFactor:1,isMobile:true,hasTouch:true,reducedMotion:"reduce"});
    await page.addInitScript(()=>{
      const timers=[],original=window.setInterval.bind(window);
      window.setInterval=(...args)=>{const id=original(...args);timers.push(id);return id;};
      window.__qaStopPolling=()=>timers.forEach(id=>clearInterval(id));
    });
    page.on("pageerror",e=>errors.push(width+": "+e.message));
    await page.route(/https:\/\/(zetaudvvutlouiqxopvg\.supabase\.co|direcciones\.ide\.uy|traslados-web\.marcelof-gx\.workers\.dev)\/.*/,r=>r.abort());
    await page.goto("http://127.0.0.1:8099/web-pasajero/",{waitUntil:"domcontentloaded"});
    await page.waitForSelector('#view-inicio.active [data-service="now"]',{timeout:12000});
    const services=await page.locator('#view-inicio [data-service]').count();
    assert.equal(services,3,"Portada: tres servicios independientes");
    // Esperar explícitamente el recurso visual local, sin depender de CDN.
    const localPhoto=await page.evaluate(async()=>{
      const background=getComputedStyle(document.querySelector("#view-inicio .home-hero")).backgroundImage;
      if(!background.includes("premium-montevideo.webp"))return false;
      const img=new Image();
      return await new Promise(resolve=>{
        img.onload=()=>resolve(img.naturalWidth>300);
        img.onerror=()=>resolve(false);
        img.src="./premium-montevideo.webp";
      });
    });
    assert.equal(localPhoto,true,"Portada sin imagen local verificada");
    const localCar=await page.evaluate(async()=>{
      const hero=document.querySelector("#view-inicio .home-hero");
      if(!getComputedStyle(hero,"::before").backgroundImage.includes("premium-car-crop.webp"))
        return false;
      const img=new Image();
      return await new Promise(resolve=>{
        img.onload=()=>resolve(img.naturalWidth>=300);
        img.onerror=()=>resolve(false);
        img.src="./premium-car-crop.webp";
      });
    });
    assert(localCar,"La foto del auto no está disponible como asset local");

    // El mapa cartográfico no debe quedar bloqueado por nuestra propia CSP.
    const csp=await page.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute("content");
    assert(csp?.includes("img-src 'self' data: https://tile.openstreetmap.org"),
      "CSP impide descargar mosaicos de OSM");
    const mapFixture=await page.evaluate(()=>{
      const host=document.createElement("div");host.style.width="355px";document.body.append(host);
      const road=[[-34.924,-56.157],[-34.91,-56.14],[-34.86,-56.09],[-34.83,-56.03]];
      const result=window.TrasladosPremiumMap?.render(host,road);
      const tiles=host.querySelectorAll(".premium-map-tiles img").length;
      const points=host.querySelector(".premium-map-route")?.getAttribute("points")||"";
      host.remove();
      return {result,tiles,points:points.length};
    });
    assert.equal(mapFixture.result,true,"Módulo cartográfico no cargó");
    assert(mapFixture.tiles>=1&&mapFixture.tiles<=12,"Demasiados mosaicos cartográficos");
    assert(mapFixture.points>30,"La ruta vial del fixture quedó vacía");
        await page.screenshot({path:path.join(dest,"portada-"+width+".png"),fullPage:true,animations:"disabled"});
    await page.locator('#view-inicio [data-service="now"]').click();
    await page.waitForSelector('#view-viaje[data-premium-step="route"]',{timeout:10000});
    assert.equal(await page.locator(".premium-step").count(),4);
    assert.equal(await page.locator("#view-viaje > .premium-live-presence #pickup-presence").isVisible(),true,"Disponibilidad visible sin desplegar campos");
    const searchWidth=await page.locator('#view-viaje #origin-picker input[type="search"]').evaluate(
      el=>el.getBoundingClientRect().width);
    assert(searchWidth>=165,"El buscador de origen queda demasiado estrecho: "+searchWidth+"px en "+width);
    assert.equal(await page.locator("#view-viaje #origin-picker .location-use-current").isVisible(),true,
      "El botón de GPS debe permanecer visible en Tu viaje");
    const compact=await page.locator("#booking-step-route").evaluate(el=>el.getBoundingClientRect().height);
    assert(compact<=190,"La tarjeta origen/destino vuelve a ocupar toda la pantalla: "+compact);
    assert.equal(await page.locator("#view-viaje #origin-picker .departments").isVisible(),false,"Departamentos secundarios plegados");
    assert.equal(await page.locator("#view-viaje .premium-route-cartography").isVisible(),true);
    assert.equal(await page.locator("#view-viaje .premium-route-canvas .premium-map-route").count(),0,"Mapa inicial no debe inventar rutas");
    await page.locator("#origin-picker input[type=search]").focus();
    await page.locator("#origin-picker .premium-department-toggle").click();
    assert.equal(await page.locator("#origin-picker .departments").isVisible(),true,"Cambiar departamento sigue disponible");
    await page.locator("#origin-picker .premium-department-toggle").click();
    await page.locator("#origin-picker input[type=search]").blur();
    await page.screenshot({path:path.join(dest,"recorrido-"+width+".png"),fullPage:true,animations:"disabled"});
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"route","El formulario no debe avanzar sin origen/destino");
    assert.equal(await page.locator(".premium-error").isVisible(),true);
    await page.locator("#origin-picker input[type=search]").fill("Aeropuerto Carrasco");
    await page.locator("#origin-picker .result").filter({hasText:"Terminal de pasajeros"}).first().click();
    // Reduced viewport models the space left by a software keyboard.
    await page.setViewportSize({width,height:452});
    await page.locator("#destination-picker input[type=search]").fill("Punta Carretas");
    await page.locator("#destination-picker .result").first().waitFor({state:"visible"});
    await page.waitForTimeout(100);
    const keyboardFit=await page.locator("#destination-picker").evaluate(el=>{
      const input=el.querySelector('input[type="search"]').getBoundingClientRect();
      const first=el.querySelector('.result').getBoundingClientRect();
      return {inputTop:input.top,inputBottom:input.bottom,resultBottom:first.bottom,height:visualViewport.height,scrollable:el.querySelector('.results').clientHeight};
    });
    assert(keyboardFit.inputTop>=0&&keyboardFit.inputBottom<=keyboardFit.height,"Campo tapado por teclado: "+JSON.stringify(keyboardFit));
    assert(keyboardFit.resultBottom<=keyboardFit.height,"Sugerencias tapadas por teclado: "+JSON.stringify(keyboardFit));
    assert.equal(await page.locator('.tabs').isVisible(),false,"No desperdiciar área de teclado con navegación inferior");
    await page.screenshot({path:path.join(dest,"destino-teclado-"+width+".png"),fullPage:false,animations:"disabled"});
    await page.locator("#destination-picker .result").filter({hasText:/Punta Carretas Shopping/i}).first().click();
    await page.setViewportSize({width,height:852});
    assert.equal(await page.locator('.tabs').isVisible(),true,"Menú vuelve al seleccionar destino");
    await page.evaluate(()=>window.scrollTo({top:0,behavior:"instant"}));
    assert(await page.locator("#origin-picker .location-selected-label").textContent());
    assert(await page.locator("#destination-picker .location-selected-label").textContent());
    await page.screenshot({path:path.join(dest,"recorrido-elegido-"+width+".png"),fullPage:true,animations:"disabled"});
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"when");
    await page.locator(".premium-time-choice[data-pickup='10']").click();
    assert.equal(await page.locator("#journey-when-select").inputValue(),"10");
    assert.equal(await page.locator("#journey-clock-action").isVisible(),true,"Reloj 24 h debe estar visible");
    const customTime=await page.evaluate(()=>{
      const parts=new Intl.DateTimeFormat("en-GB",{timeZone:"America/Montevideo",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(new Date(Date.now()+30*60000));
      return parts.find(p=>p.type==="hour").value+":"+parts.find(p=>p.type==="minute").value;
    });
    await page.locator("#journey-clock-action").click();
    await page.locator("#journey-clock-input").fill(customTime);
    await page.locator("#journey-clock-input").evaluate(el=>el.dispatchEvent(new Event("change",{bubbles:true})));
    assert.equal(await page.locator("#journey-when-select").inputValue(),"custom","La hora manual debe aplicarse");
    assert.equal(await page.locator("#journey-custom-time-label").isVisible(),true,"Hora manual seleccionada visible");
    assert.equal(await page.locator("#journey-clock-input").isVisible(),false,"El reloj debe cerrarse sin ReferenceError");
    await page.locator(".premium-time-choice[data-pickup='10']").click();
    assert.equal(await page.locator(".premium-when-extras").count(),1,"Disponibilidad y pasajeros deben permanecer accesibles");
    assert.equal(await page.locator(".premium-when-extras").evaluate(e=>e.open),false,"Panel secundario plegado por defecto");
    await page.locator(".premium-when-extras>summary").click();
    assert.equal(await page.locator("#passengers").isVisible(),true,"Cantidad de pasajeros preservada");
    await page.locator(".premium-when-extras>summary").click();
        await page.evaluate(()=>window.scrollTo({top:0,behavior:"instant"}));
    const ctaVisible=await page.evaluate(()=>{
      const next=document.querySelector("#view-viaje .premium-next").getBoundingClientRect();
      const nav=document.querySelector(".tabs").getBoundingClientRect();
      return {bottom:next.bottom,navTop:nav.top};
    });
    assert(ctaVisible.bottom<=ctaVisible.navTop-5,
      "CONTINUAR tapado por menú inferior "+width+": "+JSON.stringify(ctaVisible));
    const footerClear=await page.evaluate(()=>{
      const extras=document.querySelector("#view-viaje .premium-when-extras")?.getBoundingClientRect();
      const actions=document.querySelector("#view-viaje .premium-actions")?.getBoundingClientRect();
      return Boolean(extras&&actions&&actions.top>=extras.bottom-2);
    });
    assert(footerClear,"Los botones no pueden tapar las opciones de hora/pasajeros");
        await page.screenshot({path:path.join(dest,"cuando-"+width+".png"),fullPage:true,animations:"disabled"});
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"passenger");
    assert.equal(await page.locator("#premium-person-email").isVisible(),true,
      "Email opcional debe verse en Tus datos");
    assert.equal(await page.locator("#premium-save-contact").isVisible(),true,
      "Consentimiento para guardar localmente debe estar visible");
    await page.locator("#premium-person-email").fill("correo-no-valido");
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"passenger",
      "Email incorrecto debe impedir continuar");
    await page.locator("#premium-person-email").fill("prueba@example.org");
    await page.locator("#premium-save-contact").check();
    const savedEmail=await page.evaluate(
      ()=>JSON.parse(localStorage.getItem("traslados.premium.contact.local.v1")||"{}").email);
    assert.equal(savedEmail,"prueba@example.org","Consentimiento no guardó email localmente");
    await page.locator("#premium-save-contact").uncheck();
    assert.equal(await page.evaluate(
      ()=>localStorage.getItem("traslados.premium.contact.local.v1")),null,
      "Desactivar consentimiento debe eliminar datos locales");
        await page.screenshot({path:path.join(dest,"datos-"+width+".png"),fullPage:true,animations:"disabled"});
    await page.locator('[data-booking-step="passenger"]').evaluate(el=>el.classList.add("is-complete"));
    await page.locator(".premium-next").click();
    assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"confirm");
    assert.equal(await page.locator("#pickup-presence").isVisible(),true,"Confirmación conserva disponibilidad visible");
    await page.evaluate(()=>{const error=new Error("SQLSTATE XX000 server_secret_table unexpected_internal_detail");error.remote=true;showError(error);});
    assert(!/SQLSTATE|server_secret_table|unexpected_internal_detail/.test(await page.locator("#toast").textContent()),"No exponer detalles del backend");
    // Estado confirmado por respuesta pública de QA, sin coordenadas y sin reservas.
    await page.route("**/rest/v1/rpc/public_driver_availability_v1",r=>r.fulfill({json:{available:false,status:"not_available",checkedAt:new Date().toISOString()}}));
    await page.evaluate(()=>refreshPickupPresence(true));
    assert.match(await page.locator("#pickup-presence-title").textContent(),/Sin disponibilidad inmediata/);
    await page.route("**/rest/v1/rpc/public_driver_availability_v1",r=>r.fulfill({json:{available:true,status:"available_for_requests",checkedAt:new Date().toISOString()}}));
    await page.evaluate(()=>refreshPickupPresence(true));
    assert.match(await page.locator("#pickup-presence-title").textContent(),/Conductor disponible/);
    const iconSize=await page.locator("#view-viaje .request-hero-icon svg").evaluate(e=>e.getBoundingClientRect().width);
    assert(iconSize<=32,"SVG de confirmar desbordado: "+iconSize);
    assert.equal(await page.locator("#premium-confirm-fake-location").count(),0,"Nunca agregar ubicaciones ficticias");
        await page.screenshot({path:path.join(dest,"confirmar-"+width+".png"),fullPage:true,animations:"disabled"});
    const overflow=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,viewport:innerWidth}));
    assert(overflow.scroll<=overflow.viewport+2,"Desbordamiento horizontal en "+width+": "+JSON.stringify(overflow));
    // Se comprueba el botón final, SIN pulsarlo; la QA nunca reserva.
    assert(await page.locator("#urgent-send").count()===1);
    // Las últimas tres capturas son SOLO fixtures sintéticos en Chromium.
    // Nunca se guardan en Supabase ni aparecen en la PWA real.
    // Fixtures must not be replaced by the application's real background poll.
    // Keep every status assertion; only stop timers in this synthetic QA phase.
    await page.evaluate(()=>window.__qaStopPolling());
    for(const [kind,status,phase] of [
      ["buscando","PENDIENTE",""],
      ["en-camino","CONFIRMADA","EN_CAMINO"],
      ["en-viaje","EN_VIAJE",""]
    ]){
      const rendered=await page.evaluate(({status,phase})=>{
        const row={
          code:"QA VISUAL",status,pickup_phase:phase,
          pickup_mode:"now",created_at:new Date().toISOString(),
          pickup_date:"2026-10-15",pickup_time:"12:30",
          origin_text:"Origen de prueba – no es una reserva",
          destination_text:"Destino de prueba – no es una reserva",
          quote_status:"SIN_PRESUPUESTO",quote_final_total:null
        };
        if(typeof reservationView!=="function")return false;
        tripRows=[row];tripLoaded=true; // Datos sintéticos solo en este navegador QA.
        const card=reservationView(row);
        const board=document.querySelector("#trips-list");
        board.replaceChildren(card);
        document.querySelectorAll(".view").forEach(v=>v.classList.toggle("active",v.id==="view-viajes"));
        const notice=document.createElement("p");
        notice.className="premium-qa-fixture-label";
        notice.textContent="QA VISUAL · DATOS SINTÉTICOS · NO ES UN VIAJE REAL";
        notice.style.cssText="padding:9px;color:#ffdb9a;text-align:center;font-weight:800;font-size:11px;border:1px solid #cbae6d";
        board.prepend(notice);
        return card.dataset.tone;
      },{status,phase});
      assert.equal(rendered,status==="PENDIENTE"?"pending":"moving",
        "No se generó tarjeta de seguimiento "+kind);
      if(kind==="buscando")assert.equal(await page.locator(".premium-search").count(),1,
        "No aparece radar en espera");
      if(kind==="en-viaje")assert.equal(await page.locator(".premium-trip-timeline [aria-current='step']").count(),1,
        "La etapa de viaje no está marcada");
      assert.equal(await page.locator("#view-viajes .trip-cancel-action").count(),0,
        "No permitir cancelar/finalizar unilateralmente un viaje activo");
      await page.screenshot({path:path.join(dest,kind+"-"+width+".png"),
        fullPage:true,animations:"disabled"});
      if(kind!=="buscando"){
        const full=page.locator("#view-viajes .premium-open-tracking");
        assert.equal(await full.count(),1,"Falta acceso a seguimiento independiente");
        await full.click();
        assert.equal(await page.locator("#view-seguimiento.active").count(),1,
          "Seguimiento no abrió su pantalla");
        assert.equal(await page.locator("#view-seguimiento .premium-tracking-timeline [aria-current='step']").count(),1,
          "Fase activa inconsistente en pantalla completa");
        assert.equal(await page.locator("#view-seguimiento .premium-tracking-disclaimer").count(),2,
          "Falta advertencia de no mostrar GPS en vivo");
        assert.equal(await page.locator("#view-seguimiento button").filter({hasText:/Finalizar viaje/i}).count(),0,
          "El pasajero no puede finalizar el viaje");
        await page.evaluate(()=>window.scrollTo(0,0));
        const trackingFit=await page.evaluate(()=>{
          const controls=document.querySelector("#view-seguimiento .premium-tracking-actions")?.getBoundingClientRect();
          const nav=document.querySelector(".tabs")?.getBoundingClientRect();
          return {top:Math.round(controls?.top||0),bottom:Math.round(controls?.bottom||0),navTop:Math.round(nav?.top||0)};
        });
        assert(trackingFit.bottom<=trackingFit.navTop-4&&trackingFit.top>=0,
          "Acciones de seguimiento tapadas por menú o fuera de pantalla "+width+": "+JSON.stringify(trackingFit));
        await page.screenshot({path:path.join(dest,kind+"-pantalla-"+width+".png"),
          fullPage:true,animations:"disabled"});
        await page.locator("#premium-tracking-back").click();
      }
    }
    await page.close();
  }
  await browser.close();
  assert.equal(errors.length,0,"Errores de JavaScript: "+errors.join("; "));
  console.log("PASS visual mobile 360/393/412: portada, recorrido, cuándo, datos, confirmar, cero envíos y sin desbordamientos");
})().catch(e=>{console.error(e);process.exit(1);});

