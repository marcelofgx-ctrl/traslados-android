# Quick Trip / Mapa QT — C1.1 Samsung QA

Source base: C1 SHA `ba08fdb6a19576e69d2968e6380a6c22c5bf2b61`.
Previous installed APK: P0.1, Android versionCode 92, source `b6ee0a56bfb8e3f1aad7f47804a48d4ab7cf369f`.

## Strict scope
- Unique active reservation recovery after Android process recreation, only from the same authenticated TransferAlerts snapshot (fresh <=45 seconds, valid ID, exactly one ACEPTADA or EN_VIAJE). Never select arbitrarily when ambiguous.
- The premium ride sheet retries its primary action only after a newer authenticated snapshot and only when the original controller is no longer busy. No new RPC or reservation source.
- UI polling is limited to the foreground map lifecycle. Window insets are requested after the sheet attaches. No GPS, ETA, fares, MapLibre route engines, backend, SQL, browser, or notification engine changes.
- `uy.com.mapatrayectos`, versionCode 93, original signing identity. Same APK, no independent conductor/passenger APK.
- Signed QA APK uploaded to workflow artifact, never deployed automatically or installed by GitHub Actions.
- Compile and sign only after offline policy checks pass. Evidence includes package, version, signature digest, SHA256 and source SHA.

## Physical Samsung acceptance (NOT YET VERIFIED)
1. Before update, check Mapa QT data remain present. Install QA 93 *over* existing 92 without uninstalling.
2. Accept a real booking; confirm one map sheet, real passenger/fare/origin/destination.
3. Perform `Ir a recoger`, `Llegué al origen`, `Iniciar viaje`, `Finalizar viaje` using original confirmations.
4. Confirm actual GPS, preserved route and fare, and completed booking history.
5. Collapse and expand sheet; move and zoom map; open lateral conductor panel; check safe areas, controls and orientation.
6. Background app and reopen it during one eligible active booking; confirm recovery without duplicate request.
7. Cancel a confirmation and retry safely; check unexpected network loss, stale snapshots, canceled booking and ambiguous multiple bookings.
8. Record Samsung screenshots and results. CI green is not a physical visual approval.

## Rollback
The original signed P0.1 APK and source are retained. Android does not normally install lower versionCode 92 over 93. For in-place rollback, rebuild the original P0.1 code on an isolated QA branch with the same signing certificate and a versionCode higher than 93, then verify; never uninstall as a first step.

## Explicit prohibitions
No main merge, production publishing, Supabase modification, live booking manipulation, synthetic GPS/route, automatic Samsung installation, release declaration or final QR commercial.
