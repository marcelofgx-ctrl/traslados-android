# Quick Trip · P1 Photo 1 · QA visual-only (2026-10-29)

## Approved visual source
The FIRST image in the two-photo proposal is the approved reference. This is a **visual refinement** of the last working premium single-flow QA source, not a rewrite of Quick Trip. User specifically requested that route distance/time, fare, availability, private driver GPS and pickup ETA never regress.

## Baseline and isolation
- Source: `cc44b2f0d0312cac62bee2f9f550c45d418a2acb`.
- QA: `qa/quicktrip-p1-photo1-visual-only-20261029`.
- CSS-only UI changes: `web-pasajero/quicktrip-p1-photo1.css`. Existing `index.html` includes this stylesheet LAST and `sw.js` uses a new QA cache namespace.
- **Unchanged byte-for-byte:** `app.js`, `quicktrip-clean.js`, `eta-engine.js`, `route-engine.js`, `premium-route.js`, `premium-map.js`, `reservation-live.js`, `pickup-cadence.js`, `pickup-observability.js`, `pickup-origin-drift.js`, booking/state/auth/route RPCs, Supabase functions and Android.
- No main merge, production deploy, Supabase mutation or new APK.

## Photo 1 design implementation
- Close, visually connected origin/destination with large pointed teardrop markers and deliberate small line spacing.
- Preserve the actual `origin-picker` and `destination-picker` controls and manual editing. No substitute text, GPS value or geocoding widget.
- Original two-action GPS drift notice stays intact; only its padding and composition are reduced.
- The original `#pickup-presence` availability + pickup distance + ETA now sits in a contained, subtle high-contrast conductor block; all source state/unknown/available/retry/hidden rules respected. Never fake distance, ETA or a connected driver.
- Secondary schedule/passenger/stops controls are compact yet maintain accessible click targets.
- Price, trip kilometers and duration are the ORIGINAL source-bound `data-final-route-*` values; the fare is visually above the metrics. Never a copied or separately recalculated quote.
- Preserve original `#urgent-send` and footer. Reserve bottom scroll room instead of allowing the fixed CTA to cover the price.
- The fullscreen GPS follow map, camera and all gestures remain completely unchanged.

## Enforced acceptance gate
1. GitHub diff/allowlist: no changes outside CSS, index cache reference, PWA cache, QA tests/docs/workflow. Abort when any source-engine file differs from baseline.
2. Offline pure test suites: existing road routing, quote/fixed fare, ETA adapter, pickup cadence and GPS origin-drift contract tests.
3. Chrome 390×844 visual execution: native availability + pickup metrics; original route map remains mounted (though folded); one CTA; teardrop pins, driver card, drift action choices, price/distance/duration source DOM, no horizontal overflow; active-ride → home → scheduling navigation, map camera and original GPS controls.
4. QA screenshots and exact SHA from CI. **Chrome uses only synthetic presentation values.** They do not verify real driver availability or permissioned private GPS.
5. Physical Samsung: real origin→destination, kilometers/minutes, price, driver availability, GPS pickup distance/ETA, request acceptance, live map follow/zoom/back, completion; connectivity/CORS and scheduled service.

## Delivery rule
Do NOT call production ready or merge into main unless the real full circuit passes on Samsung with actual eligible driver GPS. Preview must remain QA-only and use an existing CORS-allowed QA origin (stable alias). Keep previous SHA and deployment for rollback.
