"use strict";
const {readFileSync}=require("node:fs");
const {runInNewContext}=require("node:vm");
const {strict:assert}=require("node:assert");
const path=require("node:path");
const base=path.join(__dirname,"..");
const js=readFileSync(path.join(base,"app.js"),"utf8");
const css=readFileSync(path.join(base,"styles.css"),"utf8");
const html=readFileSync(path.join(base,"index.html"),"utf8");
const sw=readFileSync(path.join(base,"sw.js"),"utf8");

const start=js.indexOf("function locationPermissionGuidance(errorCode){");
const stop=js.indexOf("\nfunction picker(name,mount,position){",start);
assert(start>=0&&stop>start,"Missing dedicated GPS permission guidance");
const permission=runInNewContext(js.slice(start,stop)+"\nlocationPermissionGuidance;");
for(const code of [0,1,2,3]){
  const guide=permission(code);
  assert(guide.title&&guide.description&&Array.isArray(guide.steps)&&guide.steps.length);
}
const denied=permission(1);
assert.match(denied.title,/Android|Chrome/);
assert.match(denied.steps.join(" "),/Mapa Trayectos/);
assert.match(denied.steps.join(" "),/Permisos.*Ubicación/);
assert.match(denied.steps.join(" "),/Ajustes del celular/);
assert.match(denied.description,/no puede cambiar el permiso automáticamente/);
assert.match(permission(3).title,/demorando/);
assert.match(permission(2).title,/posición/);

assert(js.includes('getCurrentPosition(async p=>'),"Permission must be requested from user action");
assert(js.includes('function showGpsPermissionHelp(errorCode)'),"Persistent permission help should replace temporary toast");
assert(js.includes('showGpsPermissionHelp(error?.code??0)'),"Map GPS error codes to dedicated help");
assert(js.includes('button(actions,"↻ Volver a intentar"'),"Retry must be interactive");
assert(js.includes('button(actions,"Escribir dirección"'),"Passenger needs manual fallback");
assert(js.includes('if(geo?.disabled)return'),"Repeated taps must not stack permission prompts");
assert(!js.includes('()=>tell("No se pudo obtener GPS. Revisá permisos.")'),"Do not regress to generic error");
assert(css.includes(".gps-permission-help[hidden]"),"Help must stay hidden until needed");
assert(html.includes('./app.js?v=16')&&html.includes('./styles.css?v=14'));
assert(sw.includes('traslados-cliente-pwa-v15'));
console.log("PASS: permission denied/unavailable/timeout, actionable Android help, GPS retry and manual origin; no auto-grant.");
