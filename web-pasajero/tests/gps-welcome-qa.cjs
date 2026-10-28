"use strict";
// Isolated browser verification of consent-first GPS at Samsung 390/412px.
// Mock permissions/GPS and intercept all external requests; never write real data.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const {spawn}=require("node:child_process"),{chromium}=require("playwright-core");
const cwd=path.resolve(__dirname,"../.."),sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const server=spawn("python3",["-m","http.server","8294","--bind","127.0.0.1"],{cwd,stdio:"ignore"});
 let browser;
 try{
  let ready=false;
  for(let i=0;i<80;i++){
   try{if((await fetch("http://127.0.0.1:8294/web-pasajero/")).ok){ready=true;break}}catch{}
   await sleep(120);
  }
  assert(ready,"QA local server");
  browser=await chromium.launch({headless:true,
   executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
   args:["--no-sandbox","--disable-dev-shm-usage"]});
  const dir=path.join(cwd,"artifacts/gps-welcome");fs.mkdirSync(dir,{recursive:true});
  for(const width of [390,412]){
   for(const fixture of [
    {state:"prompt",outcome:"success"},
    {state:"prompt",outcome:"timeout"},
    {state:"prompt",outcome:"manual"},
    {state:"denied",outcome:"denied"},
    {state:"granted",outcome:"success"}
   ]){
    const ctx=await browser.newContext({viewport:{width,height:844},isMobile:true,
     hasTouch:true,reducedMotion:"reduce",serviceWorkers:"block"});
    let writes=0;
    await ctx.route("**/*",route=>{
     const req=route.request(),url=new URL(req.url());
     if(url.hostname==="127.0.0.1")return route.continue();
     if(url.hostname==="tile.openstreetmap.org")return route.fulfill({status:200,contentType:"image/png",
      body:Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==","base64")});
     const rpc=url.pathname.split("/").pop()||"";
     if(req.method()==="POST"&&/create_reservation|customer_request_pickup_fixed|cancel_reservation|customer_quote_decision/i.test(rpc))writes++;
     return route.abort();
    });
    await ctx.addInitScript(({state,outcome})=>{
     window.__qaGpsCalls=0;
     Object.defineProperty(navigator,"permissions",{configurable:true,value:{
      query:async({name})=>({state:name==="geolocation"?state:"prompt"})
     }});
     Object.defineProperty(navigator,"geolocation",{configurable:true,value:{
      getCurrentPosition(success,failure){
       window.__qaGpsCalls++;
       if(outcome==="success")success({coords:{latitude:-34.827,longitude:-56.012,accuracy:15},timestamp:Date.now()});
       else failure({code:outcome==="timeout"?3:1});
      },
      watchPosition(){return 111;},clearWatch(){}
     }});
    },fixture);
    const page=await ctx.newPage(),errors=[];page.on("pageerror",e=>errors.push(e.message));
    await page.goto("http://127.0.0.1:8294/web-pasajero/",{waitUntil:"load"});
    await page.waitForFunction(()=>Boolean(window.QuickTripGpsWelcome),{timeout:20000});
    const calls=()=>page.evaluate(()=>window.__qaGpsCalls);
    const gate=page.locator("#qt-gps-welcome"),allow=page.locator("#qt-gps-welcome-confirm");
    const manual=page.locator("#qt-gps-welcome-manual");
    if(fixture.state==="prompt"){
     await page.waitForFunction(()=>window.QuickTripGpsWelcome?.isOpen());
     assert.equal(await calls(),0,"Prompt must require a real user action");
     assert.equal(await allow.isVisible(),true);assert.equal(await manual.isVisible(),true);
     if(fixture.outcome==="manual"){
      await manual.click();assert.equal(await calls(),0);
     }else{
      await page.screenshot({path:path.join(dir,"prompt-"+fixture.outcome+"-"+width+".png"),
       fullPage:true,animations:"disabled"});
      await allow.click();
      await page.waitForFunction(()=>window.__qaGpsCalls===1);
      if(fixture.outcome==="success"){
       await page.waitForFunction(()=>!window.QuickTripGpsWelcome.isOpen(),{timeout:18000});
       await page.waitForFunction(()=>!!document.querySelector("#origin-picker .location-selected-label"),
        {timeout:18000});
       const address=await page.locator("#origin-picker .location-selected-label").textContent();
       assert(address&&address.length>=3,"GPS must select origin via original picker");
      }else{
       await page.waitForFunction(()=>document.querySelector("#qt-gps-welcome-title")?.textContent.includes("demorando"));
       assert.equal(await gate.isVisible(),true);
       await manual.click();assert.equal(await calls(),1,"No automatic retries");
      }
     }
    }else if(fixture.state==="denied"){
     await page.waitForFunction(()=>document.querySelector("#qt-gps-welcome-title")?.textContent.includes("bloqueada"));
     assert.equal(await calls(),0,"Denied permission cannot auto-request GPS");
     await page.screenshot({path:path.join(dir,"blocked-"+width+".png"),fullPage:true});
     await manual.click();
    }else{
     await page.waitForFunction(()=>!window.QuickTripGpsWelcome.isOpen()&&window.__qaGpsCalls===1,
      {timeout:18000});
     assert.equal(await gate.isVisible(),false);
    }
    await page.reload({waitUntil:"load"});
    await page.waitForFunction(()=>Boolean(window.QuickTripGpsWelcome),{timeout:20000});
    assert.equal(await gate.isVisible(),false,"Don't repeat welcome within same session");
    assert.equal(writes,0,"No backend mutation during QA");
    assert.deepEqual(errors,[],"Uncaught JS exceptions");
    console.log("PASS GPS welcome",width,fixture.state,fixture.outcome);
    await ctx.close();
   }
  }
 }finally{if(browser)await browser.close();server.kill("SIGTERM")}
})().catch(e=>{console.error(e);process.exitCode=1});
