"use strict";
// Chromium contexts model separate browser/WebView storage, not a physical APK.
// Every remote request is intercepted; all accounts and reservations are synthetic.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const {chromium}=require("playwright-core");
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||"/usr/bin/google-chrome",
    args:["--no-sandbox","--disable-dev-shm-usage"]});
  const output=process.env.QA_DIR||path.join(__dirname,"../../premium-qa-screenshots");fs.mkdirSync(output,{recursive:true});
  const rows=[],requests=new Map(),errors=[],pages=[];
  const road={available:true,source:"openrouteservice",distanceKm:6.4,durationMin:11,
    geometry:[[-34.82747,-55.9691],[-34.83,-55.99],[-34.83696,-56.01638]]};
  for(const label of ["web","native-user-agent"]){
    const context=await browser.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,
      serviceWorkers:"block",reducedMotion:"reduce",...(label==="web"?{}:{userAgent:"Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/143.0.0.0 Mobile Safari/537.36 TrasladosClienteNative/16"})});
    await context.route("**/*",async route=>{
      const request=route.request(),url=new URL(request.url());
      if(url.hostname==="127.0.0.1")return route.continue();
      if(url.pathname==="/api/public/route-estimate")return route.fulfill({json:road});
      if(url.hostname!=="zetaudvvutlouiqxopvg.supabase.co")return route.abort();
      const name=url.pathname.split("/").pop();
      const payload=request.method()==="POST"?request.postDataJSON():{};
      let result=null;
      if(name==="customer_login")result={session_token:"qa-session-"+label,customer:{id:"qa-customer",full_name:"Pasajero QA",phone:"099000000"}};
      else if(name==="customer_list_reservations_v16"){
        assert.equal(payload.p_session_token,"qa-session-"+label);result=rows;
      }else if(name==="get_reservation_by_token_v5")result=rows.find(r=>r.public_token===payload.p_token)||null;
      else if(name==="customer_request_pickup_fixed_v1"){
        assert.equal(payload.p_session_token,"qa-session-"+label,"New requests must have authenticated ownership");
        assert.equal(payload.p_expected_total,256);
        if(!requests.has(payload.p_request_id)){
          const row={id:"00000000-0000-4000-8000-000000000077",code:"QA-PARIDAD",status:"PENDIENTE",
            fare_mode:"fixed",quote_status:"PRECIO_FIJO",quote_final_total:256,pickup_mode:"now",
            created_at:new Date().toISOString(),pickup_date:"2026-10-17",pickup_time:"12:30",
            origin_text:payload.p_origin_text,destination_text:payload.p_destination_text,
            origin_lat:payload.p_origin_lat,origin_lng:payload.p_origin_lng,
            destination_lat:payload.p_destination_lat,destination_lng:payload.p_destination_lng,
            passengers:1,stops:[],public_token:"qa-receipt-proof",route_distance_km:6.4,route_duration_min:11};
          requests.set(payload.p_request_id,row);rows.push(row);
        }
        result=requests.get(payload.p_request_id);
      }else if(name==="public_reference_quote_v1")result={available:true,currency:"UYU",rateUyuPerKm:40,referenceFareUyu:256};
      else if(name==="public_cached_route_preview_v1")result={...road,source:"supabase_route_cache"};
      else if(name==="public_driver_availability_v1")result={available:true,status:"available_for_requests",checkedAt:new Date().toISOString()};
      else if(name==="pickup-eta")result={available:false,reason:"route_unavailable"};
      return route.fulfill({json:result});
    });
    const page=await context.newPage();pages.push(page);page.on("pageerror",error=>errors.push(error.message));
    await page.goto("http://127.0.0.1:8099/web-pasajero/",{waitUntil:"domcontentloaded"});
    await page.waitForSelector('#view-inicio.active [data-service="now"]');
    if(label==="web"){
      await page.evaluate(()=>{
        enterService("now");
        originPicker.persist({text:"Origen QA",lat:-34.82747,lng:-55.9691,department:"Canelones"});
        destPicker.persist({text:"Destino QA",lat:-34.83696,lng:-56.01638,department:"Canelones"});
      });
      await page.waitForFunction(()=>latestRoad?.data?.referenceFareUyu===256);
      await page.locator(".premium-next").click();await page.locator(".premium-next").click();
      await page.locator("#person-name").fill("Pasajero QA");await page.locator("#person-phone").fill("099000000");
      await page.locator("#comments").fill("Nota sintética");await page.locator(".premium-next").click();
      await page.locator("#urgent-send").click();
      await page.waitForSelector("#view-cuenta.active");assert.equal(requests.size,0);
    }else await page.evaluate(()=>shiftView("cuenta"));
    await page.locator("#login-phone").fill("099000000");await page.locator("#login-pin").fill("123456");
    await page.locator("#account-submit").click();
    await page.waitForFunction(()=>authReady());
    if(label==="web"){
      assert.equal(await page.locator("#view-viaje").getAttribute("data-premium-step"),"confirm");
      assert.equal(await page.locator("#origin-picker .location-selected-label").textContent(),"Origen QA");
      await page.locator("#urgent-send").click();await page.waitForSelector("#booking-success:not([hidden])");
      const clean=await page.evaluate(()=>({origin:record.origin,destination:record.destination,
        comments:$("comments").value,input:originPicker.input.value,account:authReady(),proofs:jsonStorage(GUEST_KEY,[]).length}));
      assert.deepEqual(clean,{origin:null,destination:null,comments:"",input:"",account:true,proofs:1});
    }
    await page.evaluate(()=>shiftView("viajes"));await page.waitForFunction(()=>tripLoaded&&tripRows.length===1);
    assert.equal(await page.evaluate(()=>tripRows[0].code),"QA-PARIDAD");
    assert.equal(await page.evaluate(()=>jsonStorage(GUEST_KEY,[]).length),label==="web"?1:0);
  }
  for(const status of ["ACEPTADA","EN_VIAJE","FINALIZADA"]){
    rows[0].status=status;
    await Promise.all(pages.map(page=>page.evaluate(()=>loadTrips({background:true}))));
    for(const page of pages)await page.waitForFunction(expected=>tripRows[0]?.status===expected,status);
  }
  const snapshots=await Promise.all(pages.map(page=>page.evaluate(()=>({id:tripRows[0].id,code:tripRows[0].code,
    status:tripRows[0].status,amount:tripRows[0].quote_final_total,origin:tripRows[0].origin_text,destination:tripRows[0].destination_text}))));
  assert.deepEqual(snapshots[0],snapshots[1]);assert.equal(requests.size,1,"Second storage container recovers the owned trip without creating another");
  for(const [index,page] of pages.entries()){
    await page.evaluate(()=>closeTripNotice());
    await page.locator('[data-trips-scope="history"]').click();
    assert.match(await page.locator("#trips-list").textContent(),/QA-PARIDAD/);
    assert.match(await page.locator("#trips-list").textContent(),/256/);
    await page.mouse.move(0,0);
    await page.screenshot({path:path.join(output,"paridad-historial-"+index+".png"),fullPage:true,animations:"disabled"});
  }
  await browser.close();assert.deepEqual(errors,[]);
  console.log("PASS browser parity: login returns to confirmation, receipt cleans only draft, separate stores show same account trip and final state; synthetic backend only");
})().catch(error=>{console.error(error);process.exit(1);});
