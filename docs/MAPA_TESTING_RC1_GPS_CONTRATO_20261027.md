# Mapa Testing + Quick Trip Testing — RC1 real GPS

## Source of truth
- Source branch: qa/mapa-testing-rc1-real-gps-20261027, derived from RC1 f0a78afff22f14bef043fcf1215f7052e882984e.
- Stable Android/Web code snapshot: main 53dd427d5bda65e84f585b4db2c565227aff673c.
- Stable Cloudflare Worker rollback snapshot (separate repo): 196894db8b06539b51d38700c93ddc636a4da6ea.
- No QA commits are merged into main or any production Worker.
- No database migrations or Supabase modifications are made by the build.

## Architecture
- Android build type qa uses applicationId uy.com.mapatrayectos.qa and label **Mapa Testing**.
- This is the real original Mapa Trayectos launcher with **integrated original Conductor**, map, shift, history, bubble, and reservation monitor. It is NOT the isolated API console from the previous candidate.
- The existing Android private Mapa device credentials remain inside the QA package's own app sandbox. They do NOT share preferences with the stable application.
- The GPS receiver already existed: mapatrayectos.Api.publishPickupPresenceAsync. In the QA-only package, the existing TrackingService heartbeat additionally calls mapa_reservation_position_v1, using **existing Mapa device authentication** and strict GPS freshness/precision checks.
- The server RPC itself authorizes a linked device, presence consent, exactly one active reservation in pickup phase EN_CAMINO/EN_PUNTO or EN_VIAJE. It rejects other requests. There is NO invented driver.
- Quick Trip RC1 Web/PWA uses existing real reservation contracts, without mocked prices or fake reservations, and reservation-live.js calls customer_reservation_position_v1 with the authorized passenger token.
- Cloudflare's existing service is unchanged. D1 is **not** required for this path.

## Safety
- Android QA only publishes reservation GPS after explicit existing pickup-presence consent, active/non-paused shift, valid/accurate/fresh GPS, valid device registration and a server-authorized active reservation.
- Existing production APK is not replaced: separate Android package ID plus QA-only home icon and Android label. APK remains signed with the existing key but installed separately.
- **Important:** Testing Android/Web point to existing real Supabase. Creating a booking in this test UI writes a genuine booking. Only use during an expressly arranged QA test, with an authorized account and a willing conductor. Do not submit casual demo bookings.
- No service keys or PINs are embedded in new files. No new auth bypass or public GPS endpoint added.
- An existing Mapa pickup presence feature may still require device linking / PIN once as part of the legacy security model. It was not removed here, because doing so insecurely would break real driver authorization.
- GPS signal is hidden if no active authorized reservation or position older than 75 seconds, per current server function. Do not use a moving vehicle screen while driving.

## Before declaring Live functional
1. QA CI must pass web smoke, Chromium visual flows with mocked network, signed app, merged manifest and GPS policy gates.
2. HTTPS QA Preview project must be READY, show Quick Trip Testing and correct PWA name.
3. User must install Mapa Testing on Samsung, start a shift, explicitly enable presence, and pair device as required.
4. Authorized passenger creates a **real QA-designated reservation** from Quick Trip Testing, conductor accepts, progresses EN_CAMINO/EN_PUNTO then EN_VIAJE.
5. Confirm server authorization and real lat/lng appear on second device; confirm cancellation, stale GPS hiding, network dropout and finalization.
6. Until steps 3–5 pass, delivery is **RC1 candidate**, not physical Live release.

## Rollback
- Stable Android package uy.com.mapatrayectos remains untouched and usable.
- Stable passenger GitHub Pages URL remains unchanged.
- New QA Vercel project has its own domain and can be disabled without affecting production.
- To revert source, restore the recorded commits above. **This is source rollback only**; it does not roll back database content or completed reservations.
