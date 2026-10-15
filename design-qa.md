# Design QA — PR99 v18

Scope: compact immediate-trip route screen; original eight-screen reference sheet compared with real Chromium captures at 360/393/412. This is an isolated Android preview, not a production release.

Verified: combined address card, progressive department controls, accessible GPS action, expandable stops, actual catalog POI selection, map geographic overview without invented GPS or route, existing scheduled-flow controls preserved. Native Android35 navigation and production-network isolation pass. All mobile scene assertions and 47 local smoke scripts pass. Signed APK downloaded, checksum and packaged source verified.

Full-reference gate: BLOCKED. Recorrido composition is closer but typography/map styling/medallion assets still differ. Landing hero and tracking scenes are not approved matches. Tracking screenshots use explicit harness fixtures and do not prove real backend tracking. The isolated APK blocks remote route/ETA requests, so selected-route screenshots show pending verification; partial map tile loading was observed in CI. No real booking, live driver E2E or physical Samsung test was performed. No passenger unilateral finish action was added.

Evidence: Actions 38775390439 (mobile), 38775384621 (Android35), 38775384634 (signed APK). APK code206, source e8947b148225d4392b4631713a20d3b5d07e7bae, SHA256 b39a660d22a52c985a9daa0fb357da972e127f79acaa34610b04feb54e1145af.


## UX v19: disponibilidad visible y errores seguros (15/10/2026)

- El estado real de recogida se mueve fuera del panel plegable y permanece visible durante los cuatro pasos de Tu viaje. Al volver a Tu reserva se restituye su nodo original. Disponible, sin disponibilidad inmediata y consulta no verificada son estados diferentes; un resultado público negativo no se presenta como una causa concreta.
- Errores remotos RPC marcados y traducidos a mensajes de producto, conservando validación local y mensajes de horario. El fallo ambiguo de envío indica revisar Mis traslados; elimina explicaciones de UUID/servidor y toast técnico duplicado. La idempotencia y los reintentos manuales conservan su lógica.
- La prueba v19 permite SOLO GET/OPTIONS sin parámetros hacia public_driver_availability_v1, función STABLE auditada y contrato HTTP GET comprobado. No recibe coordenadas ni identidad del conductor. Envíos desactivados tanto en interfaz como funciones y política nativa; autenticación, reservas, rutas/ETA remotas y contactos siguen bloqueados. La disponibilidad pública sí es real; ETA no se simula.
- Firma original, paquete original, code207 y hashes de recursos contrastados contra APK descargada: 904.403 bytes; SHA256 7fb219a43fc64e10db53f50282e210b1f67d5811fb27a8ff8d23f1182fdf2308; fuente e99097a8a5c17fa3aab3fe768832157b3cd9f47d. Compilación 15/10/2026 16:02:18 UYT: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790359376 . Próxima APK operativa requiere code >207.
- QA: 47 smoke locales PASS; errores internos sintéticos ocultos y validación accionable conservada; escenas 360/393/412 PASS https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790365901 . Se corrigió una regresión detectada: estilos de recorrido agrandaban Atrás y tapaban Continuar en Cuándo a 360px. No se quitaron aserciones.
- Android35 PASS incluyendo consulta pública real a través de GET/CORS dentro de WebView y bloqueo de escritura: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790155607 (misma lógica nativa/HTTP; antes del último ajuste CSS). Ejecución del APK con CSS final: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790359298 ; consultar resultado final. No prueba física Samsung ni reserva real. Main/web pública sin despliegue; producto visual completo sigue pendiente.
- APK: https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/tu-viaje-premium-visual-20261015/preview-artifacts/traslados-cliente-premium-pr99-v19-prueba.apk
