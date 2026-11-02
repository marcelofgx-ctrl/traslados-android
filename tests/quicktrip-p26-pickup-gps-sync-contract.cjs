"use strict";
// QA P2.6: one opt-in-preserving pickup GPS refresh after CONFIRMED
// EN_CAMINO / EN_PUNTO, without any GPS engine, interval or backend changes.
const fs=require("node:fs"),assert=require("node:assert/strict");
const read=p=>fs.readFileSync(p,"utf8");
const integrated=read("mapatrayectos/src/main/java/uy/com/mapatrayectos/conductorv0/ConductorLiveController.java");
const native=read("conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java");
const service=read("mapatrayectos/src/main/java/uy/com/mapatrayectos/TrackingService.java");
const status=read("mapatrayectos/src/main/java/uy/com/mapatrayectos/SystemStatusActivity.java");
function ok(name,pass){assert.ok(pass,name);console.log("PASS P26",name);}
const integratedAction=integrated.slice(integrated.indexOf("@Override public void onPickupPhase("),
  integrated.indexOf("@Override public void onPrepareQuote("));
const nativeAction=native.slice(native.indexOf("private void changePickupPhase("),
  native.indexOf("private String installedConductorVersion("));
ok("Integrated Conductor requests GPS only AFTER the pickup phase is confirmed",
  integratedAction.includes('success=phase.equals(result.optString("pickup_phase"));')&&
  integratedAction.includes("if(done){playTone(true);requestPrivatePickupGpsOnce();"));
ok("Legacy/native Conductor requests GPS only after exact server confirmation",
  nativeAction.includes('if(!phase.equals(result.optString("pickup_phase")))')&&
  nativeAction.includes("}else{\n                        vibrateAcceptedPickupPhase();\n                        requestIntegratedPickupGpsOnce();"));
ok("Private sync honors shift/paused/opt-in/busy state in both interfaces",
  ["shift_active","shift_paused","enabled","manually_busy"].every(v=>
     integrated.includes('getBoolean("'+v+'",false)')&&native.includes('getBoolean("'+v+'",false)')));
ok("Native standalone backup never attempts to start nonexistent Mapa service",
  native.includes('if(!"uy.com.mapatrayectos".equals(getPackageName()))return;')&&
  native.includes('sync.setClassName(getPackageName(),"uy.com.mapatrayectos.TrackingService")'));
ok("Use only existing one-shot TrackingService action",
  integrated.includes("setAction(TrackingService.ACTION_PRESENCE_SYNC)")&&
  native.includes('new Intent("uy.com.mapatrayectos.PRESENCE_SYNC")')&&
  service.includes('else if(ACTION_PRESENCE_SYNC.equals(a)){publishPickupPresence(System.currentTimeMillis());broadcastState();}'));
ok("No new periodic publisher, GPS reads, tokens or reservation writes",
  !/setInterval|requestLocationUpdates|gps_recorded_at|http[s]?:\/\//.test(integratedAction)&&
  !/setInterval|requestLocationUpdates|gps_recorded_at|http[s]?:\/\//.test(nativeAction));
ok("Existing private status screen exposes bounded GPS diagnostics",
 status.includes('PrivateGpsDiagnostic.message(Api.pickupPresenceEnabled())'));
console.log("P2.6 code contracts PASS; Samsung verification still required.");
