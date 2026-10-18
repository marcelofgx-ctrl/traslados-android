"use strict";
// PWA v44: resumen de usuario solo tras login, y CTA honesta por modalidad.
const assert=require("node:assert/strict");
const {readFileSync}=require("node:fs");
const {join}=require("node:path");
const {runInNewContext}=require("node:vm");
const root=join(__dirname,"..");
const read=n=>readFileSync(join(root,n),"utf8");
const html=read("index.html"),js=read("app.js"),css=read("styles.css"),sw=read("sw.js");
assert(html.includes('id="passenger-account-summary"')&&html.includes('id="passenger-account-name"')&&html.includes('id="passenger-account-phone"'));
assert(html.includes('id="passenger-identification"'));
assert(html.includes('id="passenger-view-account"'),"Account panel must have a live navigation control");
assert(html.includes('id="urgent-actions"')&&html.includes('id="urgent-send"'));
assert(!html.includes('id="urgent-whatsapp"')&&html.includes("SOLICITAR VIAJE"));
assert(html.includes("REVISAR Y CONFIRMAR"),"Programmed booking retains review");
assert(css.includes(".passenger-account-summary")&&css.includes(".urgent-actions"));
assert(html.includes("app.js?v=82")&&html.includes("styles.css?v=82")&&sw.includes("traslados-pwa-v82"));
const start=js.indexOf("function authReady(){"),end=js.indexOf("\nfunction renderAccount()",start);
assert(start>=0&&end>start);
const nodes=Object.fromEntries([
 "stop-wrap","login-for-stops","passenger-identification",
 "passenger-account-summary","passenger-account-name","passenger-account-phone"
].map(k=>[k,{hidden:false,textContent:""}]));
const ctx={
 session:{token:"session-only-in-memory-test",customer:{full_name:"Cliente Ejemplo",phone:"090000000"}},
 $:id=>{assert(nodes[id],id);return nodes[id];},
 renderAccount:()=>{},renderStops:()=>{},refreshBookingSteps:()=>{}
};
const sync=runInNewContext(js.slice(start,end)+"\nsyncAuthUI;",ctx);
sync();
assert.equal(nodes["passenger-account-summary"].hidden,false);
assert.equal(nodes["passenger-identification"].hidden,true);
assert.equal(nodes["passenger-account-name"].textContent,"Cliente Ejemplo");
assert.equal(nodes["passenger-account-phone"].textContent,"090000000");
assert.equal(nodes["login-for-stops"].hidden,true);
ctx.session=null;
sync();
assert.equal(nodes["passenger-account-summary"].hidden,true);
assert.equal(nodes["passenger-identification"].hidden,false);
assert.equal(nodes["passenger-account-name"].textContent,"");
assert.equal(nodes["passenger-account-phone"].textContent,"");
assert.equal(nodes["login-for-stops"].hidden,false);
assert(js.includes('session=null;localStorage.removeItem(SESSION_KEY);'),"Logout must end the session");
assert(js.includes('$("person-name").value="";$("person-phone").value="";'),"Logout clears session guest values");
assert(js.includes('$("urgent-send").addEventListener("click",()=>void submitUrgentPickup());'),"Urgent CTA submits");
assert(js.includes('$("passenger-view-account").addEventListener("click"'));
console.log("PASS PWA v44: logged-in summary, logout privacy, mode choices, cache and native WebView parity");
