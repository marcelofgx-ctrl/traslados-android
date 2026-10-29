# Quick Trip N1/N2 — Active journey as primary passenger UI (QA)
Base: `bdcc0925021c6888f966e849fa029a62d1fdfcf0` (validated passenger QA / Vercel preview).

## What changes
- The passenger's `Tu viaje` view shows one compact verified active ride with pickup/destination, fare supplied by the same authorized booking snapshot, stage and a swipe-to-open map rail.
- Slider is **navigation only**, never starts, cancels, accepts, pays for or modifies any ride. Tap fallback and keyboard activation are retained.
- After leaving full-screen tracking by UI back or Android browser Back, the view returns to `Tu viaje`, not booking history. A dedicated history state is used for the full-screen overlay and preserved on GPS leg changes.
- `Mis traslados` keeps all pending, scheduled and past trips but an active ride has only one compact map-access button. Redundant nested spotlight and repeated status/pricing guidance are removed.
- Existing booking form remains accessible through the explicit `Preparar otro viaje` action, even while one confirmed ride is ongoing.
- Slide-up/down map detail sheet and native map gesture/move/zoom are preserved. Dark teal/gold visual layer applies only to passenger QA.
- If more than one eligible current booking exists, no ride is arbitrarily selected. The passenger is offered the full authorized trip list instead.

## No change / protection
- Unmodified `web-pasajero/reservation-live.js` continues the existing authenticated `customer_reservation_position_v1` read every four seconds when the map is visible.
- No new Supabase SQL, migrations, GPS permissions, route engine, ETA pricing or mutation RPC.
- No Android APK, WebView app identity, native Conductor panel or main branch changes.
- No production Vercel deployment. Any QA preview is non-production only.
- Source has cache-busted JS/CSS and a fresh PWA cache name to avoid old Samsung assets.

## QA evidence
- `tests/quicktrip-n1n2-contract-qa.cjs`: pure decision contract and static source checks.
- `scripts/qa_quicktrip_n1n2_browser.cjs`: Chromium mobile 390x844 browser test. Uses a **synthetic presentation-only** row to inspect visual appearance and verify navigation. It makes **no booking** and **is not live GPS verification**.
- GitHub Actions attaches screenshots and the QA JSON report.

## Outstanding physical acceptance
On Samsung with a real accepted booking, confirm that initial acceptance opens the existing real-time map, swipe and tap controls return to that same live map, Android Back returns to `Tu viaje`, active status survives refresh, a second ride is never silently selected, and the real car GPS/ETA stays intact. Check landscape, accessibility, offline/stale and a completed ride. Do not publish to production before this review.
