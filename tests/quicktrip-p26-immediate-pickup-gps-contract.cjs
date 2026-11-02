"use strict";
// QA P2.6: one existing GPS presence heartbeat after the SERVER confirms
// EN_CAMINO / EN_PUNTO. No additional sampling, timers or DB changes.
const fs=require("node:fs"),assert=require("node:assert/strict");
const load=f=>fs.readFileSync(f,"utf8");
const embedded=load("mapatrayectos/src/main/java/uy/com/mapatrayectos/conductorv0/ConductorLiveController.java");
const driver=load("conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java");
const tracking=load("mapatrayectos/src/main/java/uy/com/mapatrayectos/TrackingService.java");
const gradle=load("mapatrayectos/build.gradle");
const ok=(what,test)=>{assert.ok(test,what);console.log("PASS P26",what)};
const embeddedPhase=embedded.slice(embedded.indexOf("@Override public void onPickupPhase("),
  embedded.indexOf("@Override public void onPrepareQuote("));
const driverPhase=driver.slice(driver.indexOf("private void changePickupPhase("),
  driver.indexOf("private String installedConductorVersion("));
const embeddedKick=embedded.slice(embedded.indexOf("private void requestPickupGpsAfterPhaseAck()"),
  embedded.indexOf("@Override public void onPickupPhase("));
const driverKick=driver.slice(driver.indexOf("private void requestPickupGpsAfterPhaseAck()"),
  driver.indexOf("private void changePickupPhase("));
ok("Embedded pickup confirms exact phase before calling single GPS sync",
  embeddedPhase.includes('success=phase.equals(result.optString("pickup_phase"));')&&
  embeddedPhase.includes("if(done){requestPickupGpsAfterPhaseAck();playTone(true);TransferAlerts.refreshAsync(activity);"));
ok("Integrated driver swipe kicks only AFTER server acknowledges and vibrates",
  driverPhase.includes('if(!phase.equals(result.optString("pickup_phase")))')&&
  /else\s*\{\s*vibrateAcceptedPickupPhase\(\);\s*requestPickupGpsAfterPhaseAck\(\);/.test(driverPhase));
ok("Sync requires existing consent and ACTIVE unpaused driver shift",
  embeddedKick.includes("Api.pickupPresenceEnabled()")&&
  embeddedKick.includes('getBoolean("shift_active",false)')&&
  embeddedKick.includes('getBoolean("shift_paused",false)')&&
  driverKick.includes('getBoolean("enabled",false)')&&
  driverKick.includes('getBoolean("shift_active",false)')&&
  driverKick.includes('getBoolean("shift_paused",false)'));
ok("Uses EXISTING explicit service command, no new GPS endpoints",
  tracking.includes('ACTION_PRESENCE_SYNC="uy.com.mapatrayectos.PRESENCE_SYNC"')&&
  tracking.includes('else if(ACTION_PRESENCE_SYNC.equals(a)){publishPickupPresence(System.currentTimeMillis());broadcastState();}')&&
  embeddedKick.includes("TrackingService.ACTION_PRESENCE_SYNC")&&
  driverKick.includes('new Intent("uy.com.mapatrayectos.PRESENCE_SYNC")')&&
  driverKick.includes('setClassName(getPackageName(),"uy.com.mapatrayectos.TrackingService")'));
ok("No standalone driver behavior change",
  driverKick.includes('if(!"uy.com.mapatrayectos".equals(getPackageName()))return;'));
ok("No new timers, location listeners, or additional RPCs",
  !/setInterval|postDelayed|requestLocationUpdates|watchPosition|mapa_reservation_position_v1/.test(embeddedKick+driverKick));
ok("Version 95 maintains original app identity and monotonic Samsung install",
  gradle.includes("applicationId 'uy.com.mapatrayectos'")&&
  gradle.includes("versionCode 95")&&
  gradle.includes("versionName 'MapaQT-R25.38-C1.1-P2.6-QA'"));
console.log("P2.6 source contracts passed; signed APK and physical Samsung acceptance pending.");
