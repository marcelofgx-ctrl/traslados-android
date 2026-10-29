# Quick Trip passenger QA — P0 pickup availability/ETA visual guard
Date: 2026-10-29. Base passenger QA SHA: `148664fa20d2ce1906373dc3e692abfbcc516020`.

## Verified source regression
The N1/N2 presentation CSS hid the *entire* `.qtc-app` and `.journey-form-host` while an active booking was selected. `quicktrip-clean.js` moves the **original** `#pickup-presence` node into `.qtc-app .qtc-driver-slot`, so the same CSS accidentally obscured server availability, pickup distance and ETA even when the underlying modules were not changed.

The earlier visual UI additionally masked the server-owned `.pickup-card-header` in `checking/routing/waiting/unavailable` with a decorative search or negative card. The old independent "Conductor no disponible ahora" banner was stronger than the actual public availability response.

## Changes
- Never hide the complete app/form or operational driver container.
- Only fold the **new booking form** under the active-ride card; explicitly retain original operational driver/ETA node. The source controls visibility via `hidden`; we do not fake data or override it.
- Restore the single original source-owned pickup title across all states and remove duplicate decorative status banners.
- Distinguish unavailable pickup from network failure, preserving original message and retry.
- Keep existing map, route geometry, pricing, availability RPC, pickup-eta Edge function, GPS, auth, bookings, history and N1/N2 swipe logic unchanged.
- Cache-bust only passenger visual assets, using new PWA cache name.

## QA gate
The guarded workflow checks exact source whitelist, static invariants, syntax, and tests in real Chrome at Samsung viewport. Browser may call `paintPickupCard` **with synthetic presentation-only values** to inspect visible original DOM for available (distance+minutes), unavailable, network error, and while one active ride is displayed. No RPC or booking is exercised by these fixtures.
The live Supabase pickup-eta status and actual physical Samsung GPS/ETA must be verified separately. **A green visual CI must never be treated as proof of real driver availability**.

## Acceptance checklist for physical Samsung
- One app: verify origin, destination, map verified route, distance, duration and price.
- Before booking: verify backend availability (distinguish available, not available, cannot check), pickup distance and minutes when genuine ETA exists; retry when no estimate.
- During booking: preserve source values after collapsing, expanding, tapping/swiping full map, Android Back, and reopening.
- After confirmed pickup: real vehicle position and expected green/white legs; no duplicate markers/polling or misleading "Can pick me up" promises.
- Test mobile 390px, overlays and orientation; screenshots without demo overlays.
- Never production until these tests pass; no Supabase real writes, no main merge, no APK.
