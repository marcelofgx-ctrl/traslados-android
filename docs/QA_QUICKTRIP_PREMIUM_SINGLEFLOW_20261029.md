# Quick Trip / Web-PWA — premium single-flow QA (29 October 2026)

## Base and protections

Parent source: `cf1693d0e6c332127e766514112474bea6b1f0b4` (P0 ETA visibility). New QA branch only.
No changes to Android Mapa QT, main, real Supabase configuration/functions/SQL, transport RPC semantics, destination routing, fare source or GPS polling interval. No real reservations during CI.

## Approved passenger flow

- **Before accepting**: `Tu viaje` is a compact mobile-first booking surface, with source-controlled origin/destination, availability, pickup distance/ETA if available, time, optional passenger fields, true fare and native `SOLICITAR VIAJE`. The original preview map canvas **remains mounted** and available to its engine but occupies no space in the booking form. There is never a simulated driver/price/ETA.
- **Request pending**: existing confirmation stays on the same page, explicitly says the driver must respond, automatic polling reads the same authorized reservations. No premature tracking.
- **Driver accepted**: existing `autoFollowAccepted` opens the only fullscreen tracking map after server-confirmed immediate acceptance. Scheduled future bookings cannot hijack the ride.
- **While riding**: default camera zoom 16 follows last **authorized** GPS coordinate from `customer_reservation_position_v1`; only the map's camera changes, not GPS/road/ETA. A user who zooms or explores gets manual camera control; `◎` resumes live car follow; `◇` fits the real verified route. No extrapolation of position.
- **Back**: closes fullscreen map to Inicio, without reopen loop. Another deliberate tap on `Tu viaje` resumes the map if there is exactly one eligible, authorized active ride. No history detour. After finalization/cancellation, it goes to request form/history instead.
- **Multiple eligible active rides**: never guess which ride to track; show the existing selection/list surface.
- **Scheduled service**: stays independent and uses the original booking nodes, authentication and confirmation.

## Visual changes

Petroleum-green, gold, compact 390px-first form, reduced header and map-sheet, primary CTA remains single `#urgent-send`, no duplicate embedded map. Styling is scoped to `#view-viaje` and `#qt-focus[data-mode=tracking]`.

## Test gates

1. Immutable source scope, syntax and contract tests covering authenticated/guest selectors, scheduled vs immediate, accepted vs pending, ETA preservation, one map, PWA cache and native GPS-fed camera.
2. Chrome 390x844 UI fixture: actual UI node visibility with synthetic visual **only** availability/ETA, active card navigation, Back, schedule↔quick, manual zoom/recenter/fitted route. The screenshot is **not a live vehicle**.
3. Separate Samsung physical validation: real origin/destination route, kilometers, price, available-driver pickup ETA, submit confirmed, automatic map on acceptance, GPS refresh, map pan/zoom/recenter, Back→Inicio→Tu viaje→map, ride completion→history, reconnect/stale/permissions. Do **not** call these proved by Chrome.
4. QA-only Vercel Preview: verify the actual source SHA, preview target and root directory. Never promote or publish production.

## Rollback

The parent QA source remains on `qa/quicktrip-n1n2-p0-eta-visual-guard-20261029`. Restore the previous QA alias to its prior preview if physical testing detects a functional regression. Do not touch commercial production or native APK.
