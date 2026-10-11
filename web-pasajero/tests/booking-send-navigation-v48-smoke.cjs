"use strict";
// Product regression: Urgent -> Programar is navigation only, not submission.
// Real scheduled submission requires review and RPC + returns a receipt code.
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path");
const root=path.join(__dirname,"..");
const js=fs.readFileSync(path.join(root,"app.js"),"utf8");
const html=fs.readFileSync(path.join(root,"index.html"),"utf8");
const css=fs.readFileSync(path.join(root,"styles.css"),"utf8");
assert(html.includes('id="urgent-switch-scheduled"'),"Urgent fallback still exists");
assert(html.includes("ELEGIR FECHA Y HORA"),"Urgent CTA must describe navigation");
assert(!html.includes("PROGRAMAR Y ENVIAR SOLICITUD"),"Must not pretend to send");
assert(html.includes('id="booking-send-error"')&&html.includes('role="alert"'),"Submission errors must stay visible");
assert(html.includes('id="booking-when-error"')&&html.includes('id="booking-fix-when"'));
assert(css.includes('.booking-send-error[hidden]'),"Honor visibility in premium style");
assert(js.includes('$("urgent-switch-scheduled").addEventListener("click",()=>{\n    // Este CTA NO envía solicitudes: únicamente abre fecha/hora programada.\n    setWhen("schedule");'),"Fallback only opens scheduling");
assert(js.includes('function bookingSendFailure(error)'),"Persistent error after RPC");
assert(js.includes('notice.textContent=scheduleIssue'),"Distinguish unavailable slot from ambiguous server outcome");
assert(js.includes('Revisá «Mis traslados» antes de intentarlo nuevamente.'),"Avoid accidental duplicate bookings");
assert(js.includes('function showPreview()')&&js.includes('clearBookingFeedback();'),"Review begins with clean feedback");
assert(js.includes('const availability=await rpc("customer_check_availability_v11_4"'),"Server checks schedule before booking");
assert(js.includes('result=await rpc("customer_create_reservation_v12"'),"Registered reservation RPC remains");
assert(js.includes('result=await rpc("create_reservation"'),"Guest reservation RPC remains");
assert(js.includes('text(box,"h2","Solicitud enviada")'),"Receipt requires actual response");
assert(js.includes('if(!result?.code)throw new Error'),"Must not show success without booking code");
assert(js.includes('}catch(e){bookingSendFailure(e);}finally{busy=false;loading(btn,false);}'),"RPC errors remain visible");
assert(js.includes('bookingSteps.open("when",{scroll:true});'),"Slot repair navigates to date/time when requested");
console.log("PASS booking flow: navigation is not submission; backend error visible and no false reservation success");
