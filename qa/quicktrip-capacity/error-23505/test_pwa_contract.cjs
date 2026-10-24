"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const path=require("node:path");
const {isCapacityConflict,capacityMessageOrNull}=require("./capacity-error-contract.js");
const fixture=path.join(__dirname,"cases.tsv");
const cases=fs.readFileSync(fixture,"utf8").split(/\r?\n/).filter(l=>l && !l.startsWith("#"));
let count=0;
for(const line of cases){
  const p=line.split("\t");
  assert.equal(p.length,3,"fixture row");
  const code=p[0]==="-"?null:p[0],expected=p[2]==="BUSY";
  assert.equal(isCapacityConflict(code,p[1]),expected,line);
  const msg=capacityMessageOrNull(code,p[1]);
  assert.equal(msg===null,!expected,line);
  if(expected)assert.match(msg,/^Conductor ocupado:/);
  count++;
}
assert.equal(isCapacityConflict("23505",undefined),false);
assert.equal(isCapacityConflict(undefined,'constraint "qt_one_active_service_uq"'),false);

// Run the UNCHANGED actual PWA transport with a fake request inside a VM.
// Only the fake URL is used; no production HTTP call or browser context.
const transport=fs.readFileSync(path.join(__dirname,"../../../web-pasajero/rpc-client.js"),"utf8");
const app=fs.readFileSync(path.join(__dirname,"../../../web-pasajero/app.js"),"utf8");
const start=app.indexOf("function showError("),end=app.indexOf("function loading(",start);
assert.ok(start>0 && end>start,"actual showError function must be found");
const ctx={setTimeout,clearTimeout,AbortController,JSON,encodeURIComponent,Error,
  lastToast:null,tell(value){this.lastToast=value;}};
ctx.window=ctx;
vm.createContext(ctx);
vm.runInContext(transport,ctx,{filename:"web-pasajero/rpc-client.js"});
vm.runInContext(app.slice(start,end),ctx,{filename:"web-pasajero/app.js showError"});
assert.equal(typeof ctx.TrasladosRpc.call,"function");
assert.equal(typeof ctx.showError,"function");
const fakeUrl="https://fake.invalid";
const raw={code:"23505",message:'duplicate key value violates unique constraint "qt_one_active_service_uq"',
  details:"Key (coalesce)=(f) already exists",hint:null};
let requests=0;
const fake=async (url,options)=>{
  requests++;
  assert.equal(url,fakeUrl+"/rest/v1/rpc/driver_decide_fixed_pickup_v1");
  assert.equal(options.method,"POST");
  return {ok:false,status:409,text:async()=>JSON.stringify(raw)};
};
async function exercise() {
  let actual;
  try{await ctx.TrasladosRpc.call({api:fakeUrl,publishableKey:"TEST_ONLY",
       name:"driver_decide_fixed_pickup_v1",request:fake});}
  catch(e){actual=e;}
  assert.ok(actual instanceof Error,"actual transport should throw Error");
  assert.equal(actual.message,raw.message);
  assert.equal(actual.code,undefined,"current production PWA drops SQLSTATE code");
  assert.equal(requests,1,"request must not be retried");
  // The real showError is executed as-is, not replaced by the proposed mapper.
  ctx.showError(actual);
  assert.equal(ctx.lastToast,raw.message,"current PWA does not produce translated busy message");
  assert.notEqual(ctx.lastToast,capacityMessageOrNull(raw.code,raw.message));
  console.log("PASS PWA CURRENT: real rpc-client + showError reproduce missing code/translation, no retry");
  assert.equal(capacityMessageOrNull(raw.code,raw.message),require("./capacity-error-contract.js").BUSY_MESSAGE);
  const other={code:"23505",message:'duplicate key value violates unique constraint "reservations_code_key"'};
  assert.equal(capacityMessageOrNull(other.code,other.message),null);
  console.log("PASS PWA PROPOSED CONTRACT: "+count+" synthetic fixtures, no false busy classification");
  // Timeout must not silently repeat the booking request.
  let timeoutCalls=0;
  let timeoutErr;
  try{await ctx.TrasladosRpc.call({api:fakeUrl,publishableKey:"TEST_ONLY",
     name:"driver_decide_fixed_pickup_v1",request:async()=>{timeoutCalls++;let e=new Error("synthetic abort");e.name="AbortError";throw e;}});}
  catch(e){timeoutErr=e;}
  assert.equal(timeoutCalls,1);
  assert.match(timeoutErr.message,/Revisá Mis traslados antes de volver a enviar/);
  console.log("PASS PWA CURRENT: synthetic timeout makes 1 request and requires checking status");
}
exercise().catch(e=>{console.error(e.stack);process.exitCode=1;});
