"use strict";
/* Prevent Samsung agenda "jumping" on periodic refresh, and prevent old account
 * results from appearing after logout. Pure/static checks; no RPC or booking. */
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const app=fs.readFileSync(path.join(__dirname,"../app.js"),"utf8");
const css=fs.readFileSync(path.join(__dirname,"../styles.css"),"utf8");
const block=app.match(/function tripDisplayKey\(rows\)\{[\s\S]*?\n\}/);
assert(block,"The board needs a stable display fingerprint");
const ctx={tripBoard:{metadata:r=>({tone:r.expired?"expired":"pending"})}};
vm.runInNewContext(block[0],ctx);
const base={id:"one",code:"T1",status:"PENDIENTE",origin_text:"Arenal Grande 2274",
  destination_text:"Aeropuerto Internacional de Carrasco",quote_final_total:890,stops:[]};
const key=ctx.tripDisplayKey([base]);
assert.equal(key,ctx.tripDisplayKey([{...base}]),"Unchanged data must keep DOM and focus");
assert.notEqual(key,ctx.tripDisplayKey([{...base,quote_final_total:990}]),"Price changes must repaint");
assert.notEqual(key,ctx.tripDisplayKey([{...base,origin_text:"Otra dirección"}]),"Route changes must repaint");
assert.notEqual(key,ctx.tripDisplayKey([{...base,expired:true}]),"Expiry must repaint without DB writes");
assert.notEqual(key,ctx.tripDisplayKey([base,{...base,id:"two"}]),"New reservation must repaint");
assert(app.includes("if(needsRender)renderTripsBoard();"),"Don't rebuild on every 20s poll");
assert(app.includes("info.open=openTripDetails.has(")&&app.includes("historical.open=openTripDetails.has("),
  "Both secondary disclosures must survive a real update");
assert(app.includes('list.querySelectorAll(".trip-card .trip-detail-disclosure[open]")'),
  "Snapshot which details are expanded before replacing cards");
assert.equal((app.match(/tripQueryEpoch\+\+;tripsLoading=false/g)||[]).length,2,
  "Both login and logout must invalidate old network work");
assert(app.includes("const requestEpoch=tripQueryEpoch,requestSession=authReady()?session.token:null;"),
  "Capture session before requesting rows");
assert(app.includes('p_session_token:requestSession'),"Request must use captured session");
assert.equal((app.match(/if\(requestEpoch!==tripQueryEpoch\)return;/g)||[]).length,2,
  "Old response and old error cannot mutate visible state");
assert(/if\(requestEpoch===tripQueryEpoch\)\{\s*tripsLoading=false/.test(app),
  "Stale request cannot unlock a newer request");
assert(css.includes(".trip-chat-dialog-header{flex-direction:row-reverse}"),
  "Samsung passenger chat close must be on the left, away from the native bubble");
console.log("PASS stable Samsung agenda refresh, preserved disclosures and private account switching");
