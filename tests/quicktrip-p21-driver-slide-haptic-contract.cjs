"use strict";
// P2.1 — source contract only: no Android build, device GPS, backend writes, or APK.
const fs=require("node:fs"),assert=require("node:assert/strict");
const code=fs.readFileSync("conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java","utf8");
const checked=(label,condition)=>{assert.ok(condition,label);console.log("PASS QA DRIVER SLIDE",label);};
const accepted=code.slice(code.indexOf('private void changePickupPhase('),code.indexOf('private String installedConductorVersion('));
const status=code.slice(code.indexOf('private void changeStatus('),code.indexOf('private void changePickupPhase('));
const slide=code.slice(code.indexOf('private android.view.View tripSlideControl('),code.indexOf('private boolean isNetworkAvailable()'));
checked("Pickup-on-way uses the native slider",
 code.includes("bodyBox.addView(pickupSlideControl(r,false),lpMatch(dp(54),5,2));")&&
 !code.includes('primaryButton("↗ VOY EN CAMINO")'));
checked("Arrival uses the native slider, not a direct tap",
 code.includes("bodyBox.addView(pickupSlideControl(r,true),lpMatch(dp(54),5,2));")&&
 !code.includes('primaryButton("✓ LLEGUÉ AL ORIGEN")'));
checked("Start and finish retain the existing slide gestures",
 code.includes('bodyBox.addView(tripSlideControl(r,false),lpMatch(dp(54),6,2));')&&
 code.includes('bodyBox.addView(tripSlideControl(r,true),lpMatch(dp(54),6,2));'));
checked("Gesture needs at least 72% travel and cancels without status write",
 slide.includes('(knobLeft-slidePad())/travel>=.72f')&&
 slide.includes('case android.view.MotionEvent.ACTION_CANCEL:')&&
 slide.includes('animateKnob(slidePad(),false)'));
checked("Four actions dispatch ONLY to the original authenticated native transitions",
 slide.includes('if(pickupAction==1)changePickupPhase(reservation,"EN_CAMINO");')&&
 slide.includes('else if(pickupAction==2)changePickupPhase(reservation,"EN_PUNTO");')&&
 slide.includes('else changeStatus(reservation,finishMode?"FINALIZADA":"EN_VIAJE");')&&
 accepted.includes('Api.setPickupPhase(pin,id,phase)')&&status.includes('Api.setStatus(pin,id,to)'));
checked("Pickup vibration occurs ONLY after exact server acknowledgment",
 accepted.includes('if(!phase.equals(result.optString("pickup_phase")))')&&
 /else\s*\{\s*vibrateAcceptedPickupPhase\(\);/.test(accepted)&&
 slide.includes("Do not vibrate before the server acknowledges the transition.")&&
 !slide.includes("performHapticFeedback("));
checked("Vibration follows driver preference and works from Android 8",
 slide.includes('getBoolean("vibrate",true)')&&
 slide.includes('android.os.VibrationEffect.createOneShot(95')&&
 slide.includes('else v.vibrate(95);'));
checked("Existing trip confirmation vibrates after successful start/finish",
 status.includes('playTripActionCue(true)')&&status.includes('playTripActionCue(false)'));
checked("TalkBack uses confirmation, never silently changes a trip on tap",
 slide.includes('@Override public boolean performClick()')&&
 slide.includes('showDriverConfirm(title,question,"VOLVER","CONFIRMAR",this::completeSlide)'));
checked("In-flight guard and retry preserve original network safety",
 accepted.includes('driverStatusInFlight.add("phase-"+id)')&&
 slide.includes('postDelayed(()->{if(isAttachedToWindow()){locked=false;knobLeft=slidePad();invalidate();}},2200)'));
console.log("P2.1 native driver slider/haptic source contract passed; physical Samsung QA pending");
