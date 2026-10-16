"use strict";
// Regression: a real guest pickup receipt must stay visible after sign-in,
// and an unverified account claim must never delete its only access token.
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const app=fs.readFileSync(path.join(__dirname,"../app.js"),"utf8");
const start=app.indexOf("function claimSucceededForGuest(");
const end=app.indexOf("function saveGuest(result)",start);
assert(start>=0&&end>start,"Pure guest/registered merge helpers must exist");
const sandbox={};
vm.runInNewContext(app.slice(start,end),sandbox);
const {claimSucceededForGuest,mergeAccountAndGuestTrips}=sandbox;
assert.equal(typeof claimSucceededForGuest,"function");
assert.equal(typeof mergeAccountAndGuestTrips,"function");
const receiptA={code:"TR-A",token:"opaque-guest-A"};
const receiptB={code:"TR-B",token:"opaque-guest-B"};
assert.equal(claimSucceededForGuest(receiptA,{id:"id-A",code:"TR-A"}),true);
for(const claim of [null,undefined,{code:"TR-A"},{id:"id-A",code:"TR-C"},{}]){
  assert.equal(claimSucceededForGuest(receiptA,claim),false,
    "Unconfirmed or mismatched claim must preserve guest receipt");
}
const account=[{id:"id-A",code:"TR-A",status:"PENDIENTE"}];
const replies=[
  {status:"fulfilled",value:{id:"id-A",code:"TR-A",status:"PENDIENTE"}},
  {status:"fulfilled",value:{id:"id-B",code:"TR-B",status:"PENDIENTE",quote_status:"ENVIADO"}}
];
const merged=mergeAccountAndGuestTrips(account,[receiptA,receiptB],replies);
assert.equal(merged.length,2,"No duplicate account-owned trip");
assert.equal(merged[0].__guestToken,undefined,"Account-owned trip takes precedence");
assert.equal(merged[1].__guestToken,receiptB.token,"Guest receipt remains usable while signed in");
assert.equal(merged[1].quote_status,"ENVIADO","Quote status remains intact");
const guestOnly=mergeAccountAndGuestTrips([], [receiptB], [replies[1]]);
assert.equal(guestOnly.length,1);
assert.equal(guestOnly[0].code,"TR-B");
assert.equal(mergeAccountAndGuestTrips(account,[receiptB],[{status:"rejected",reason:new Error("network")}]).length,1);
assert(app.includes('rows=mergeAccountAndGuestTrips(rows,guests,result)'),
  "Both session and guest receipts must be combined in Mis traslados");
assert(app.includes('if(!claimSucceededForGuest(prior,claimed))unclaimed.push(prior)'),
  "Login must retain receipts on failed/ambiguous claim");
console.log("PASS guest booking persists through login, unclaimed receipt protected, no duplicate trips");
