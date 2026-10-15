# Design QA — PR99 v18

Scope: compact immediate-trip route screen; original eight-screen reference sheet compared with real Chromium captures at 360/393/412. This is an isolated Android preview, not a production release.

Verified: combined address card, progressive department controls, accessible GPS action, expandable stops, actual catalog POI selection, map geographic overview without invented GPS or route, existing scheduled-flow controls preserved. Native Android35 navigation and production-network isolation pass. All mobile scene assertions and 47 local smoke scripts pass. Signed APK downloaded, checksum and packaged source verified.

Full-reference gate: BLOCKED. Recorrido composition is closer but typography/map styling/medallion assets still differ. Landing hero and tracking scenes are not approved matches. Tracking screenshots use explicit harness fixtures and do not prove real backend tracking. The isolated APK blocks remote route/ETA requests, so selected-route screenshots show pending verification; partial map tile loading was observed in CI. No real booking, live driver E2E or physical Samsung test was performed. No passenger unilateral finish action was added.

Evidence: Actions 38775390439 (mobile), 38775384621 (Android35), 38775384634 (signed APK). APK code206, source e8947b148225d4392b4631713a20d3b5d07e7bae, SHA256 b39a660d22a52c985a9daa0fb357da972e127f79acaa34610b04feb54e1145af.
