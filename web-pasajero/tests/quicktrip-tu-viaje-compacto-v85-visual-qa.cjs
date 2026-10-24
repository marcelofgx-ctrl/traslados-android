"use strict";
/*
 * QA visual REAL, rama aislada: Chromium abre el HTML operativo (sin mocks
 * de precio, GPS ni disponibilidad). Se bloquea cualquier tráfico externo.
 * Las capturas nunca representan una reserva creada ni Supabase validado.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {spawn}=require("node:child_process");
const {chromium}=require("playwright-core");
const output=process.env.QA_DIR||path.resolve("quicktrip-web-v85-capturas");
fs.mkdirSync(output,{recursive:true});

async function main(){
  const server=spawn("python3",["-m","http.server","8101","--bind","127.0.0.1"],{
    cwd:process.cwd(),stdio:"ignore"
  });
  let browser;
  const results=[],errors=[];
  try{
    for(let retry=0;retry<40;retry++){
      try{if((await fetch("http://127.0.0.1:8101/web-pasajero/")).ok)break;}
      catch{}
      if(retry===39)throw new Error("No arrancó servidor QA local");
      await new Promise(resolve=>setTimeout(resolve,150));
    }
    browser=await chromium.launch({
      headless:true,
      executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
      args:["--no-sandbox","--disable-dev-shm-usage"]
    });
    for(const width of [360,393,430]){
      const height=width===393?852:780;
      const page=await browser.newPage({
        viewport:{width,height},deviceScaleFactor:1,
        isMobile:true,hasTouch:true,reducedMotion:"reduce"
      });
      page.on("pageerror",error=>errors.push(width+": "+error.message));
      await page.route("**/*",async route=>{
        const url=new URL(route.request().url());
        if(url.hostname!=="127.0.0.1")return route.abort();
        if(url.pathname==="/web-pasajero/"||url.pathname==="/web-pasajero/index.html"){
          const response=await route.fetch();
          // Modo lectura: el motor real no puede escribir reservas durante el QA.
          const body=(await response.text()).replace("<head>",
            '<head><meta name="traslados-preview" content="read-only">');
          return route.fulfill({response,body});
        }
        return route.continue();
      });
      await page.goto("http://127.0.0.1:8101/web-pasajero/",{waitUntil:"load"});
      await page.locator("#view-inicio.active").waitFor({state:"visible"});
      await page.locator('#view-inicio [data-service="now"]').click();
      await page.locator("#view-viaje.active .qtc-app").waitFor({state:"visible",timeout:10000});
      const state=await page.evaluate(()=>{
        const rect=selector=>{
          const element=document.querySelector(selector);
          if(!element)return null;
          const b=element.getBoundingClientRect();
          return {top:Math.round(b.top),bottom:Math.round(b.bottom),
            height:Math.round(b.height),width:Math.round(b.width)};
        };
        const app=document.querySelector("#view-viaje.active .qtc-app");
        const map=app?.querySelector(".premium-route-cartography");
        const button=document.getElementById("urgent-send");
        return {
          header:rect("#view-viaje .qtc-header"),
          origen:rect("#view-viaje #origin-picker"),
          destino:rect("#view-viaje #destination-picker"),
          mapa:rect("#view-viaje .premium-route-cartography"),
          conductor:rect("#view-viaje .qtc-driver"),
          horario:rect("#view-viaje .qtc-schedule"),
          datos:rect("#view-viaje .qtc-contact"),
          solicitar:rect("#view-viaje #urgent-send"),
          footer:rect("#view-viaje .qtc-bottom-nav"),
          pageHeight:document.documentElement.scrollHeight,
          overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
          mapIsSameNativeNode:Boolean(map&&document.querySelectorAll(".premium-route-canvas").length===1),
          buttonWithinOriginalAction:Boolean(button?.closest("#urgent-actions")&&button.closest(".qtc-action-slot")),
          sourceText:document.querySelector("#qtc-summary-wait")?.textContent?.trim()||""
        };
      });
      assert(state.header&&state.header.height<=76,
        "Encabezado no compacto: "+JSON.stringify(state.header));
      assert(state.mapa&&state.mapa.height>=135&&state.mapa.height<=165,
        "Mapa real debe seguir visible y compacto: "+JSON.stringify(state.mapa));
      assert(state.origen&&state.destino&&state.origen.top<state.destino.top&&
        state.destino.top<state.mapa.top,"Origen, destino y mapa mantienen orden aprobado");
      assert(state.conductor&&state.horario&&state.datos&&state.footer,
        "Se perdieron servicios esenciales");
      assert(state.mapIsSameNativeNode,"No se debe duplicar mapa vial");
      assert(state.buttonWithinOriginalAction,"El botón debe pertenecer al motor de solicitud original");
      assert(state.overflow<=2,"Desbordamiento horizontal "+state.overflow);
      if(width===393){
        assert(state.solicitar&&state.solicitar.bottom<=state.footer.top-5,
          "En Samsung 393x852 Solicitar viaje queda tapado por barra inferior: "+
          JSON.stringify({solicitar:state.solicitar,footer:state.footer}));
      }
      const prefix="Tu-Viaje-v85-"+width;
      await page.screenshot({
        path:path.join(output,prefix+"-primer-pantallazo.png"),
        fullPage:false,animations:"disabled"
      });
      await page.screenshot({
        path:path.join(output,prefix+"-pantalla-completa.png"),
        fullPage:true,animations:"disabled"
      });
      if(width===393){
        const details=page.locator("#view-viaje .qtc-schedule");
        await details.locator("summary").click();
        assert.equal(await details.getAttribute("open"),"",
          "Horario debe desplegar controles reales");
        await page.screenshot({
          path:path.join(output,"Tu-Viaje-v85-393-horario-desplegado.png"),
          fullPage:true,animations:"disabled"
        });
        await details.locator("summary").click();
        const passenger=page.locator("#view-viaje .qtc-contact");
        await passenger.locator("summary").click();
        assert.equal(await passenger.getAttribute("open"),"",
          "Datos del pasajero deben desplegar formulario real");
        await page.screenshot({
          path:path.join(output,"Tu-Viaje-v85-393-datos-desplegados.png"),
          fullPage:true,animations:"disabled"
        });
      }
      results.push({width,height,...state});
      await page.close();
    }
    assert.deepEqual(errors,[],"Errores JS: "+errors.join("; "));
    const manifest={qa:"isolated-visual-only",result:"PASS",branch:
      "qa/quicktrip-tu-viaje-compacto-v85-20261024",
      liveBackend:false,realReservationSent:false,actualChromiumScreenshots:8,
      results,javaScriptErrors:errors};
    fs.writeFileSync(path.join(output,"QA-RESULTADOS-v85.json"),
      JSON.stringify(manifest,null,2)+"\n");
    console.log("PASS Tu Viaje compacto v85, 8 capturas reales Chromium, sin red externa");
    console.log(JSON.stringify(results,null,2));
  }finally{
    if(browser)await browser.close();
    server.kill("SIGTERM");
  }
}
main().catch(error=>{console.error("FAIL QA compacto",error);process.exitCode=1});
