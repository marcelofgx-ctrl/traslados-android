# PWA v82 — Consolidación controlada de correcciones (18/10/2026)

## Propósito

Reunir en una sola candidata de la Web/PWA de pasajeros las seis ramas **sin rehacer componentes**:
**#119 → #122 → #123 → #125 → #140 → #141**.

Último commit funcional de la cadena: `c4cd6cc724eafe7570a95e8aa9fb487824992c8f`.
Esta rama contiene las mismas modificaciones de la cadena, más exclusivamente el
**versionado coherente v82 de la caché y los recursos modificados**.
PR de integración contra `main` para inspección y pruebas del conjunto.

## Contratos funcionales preservados

- GPS real y fases del seguimiento pasajero #119.
- DIR-001: búsquedas de direcciones/alturas y fallback con precisión #122/#125.
- STATUS-001: una sola semántica de estados de reserva, evitando presupuestos heredados #123.
- PICKUP-ORIGIN-STALE-001: aviso de desplazamiento >250 m con confirmación explícita; nunca
  modifica origen ni reserva enviada automáticamente #140.
- PICKUP-RECALC-001: cambio de origen elimina ETA/errores antiguos y solicita cálculo
  nuevo; cambiar solo destino no toca ETA conductor→origen; reintento limitado por
  cuota y cálculo por carretera verificado #141.

## Control de instalación/Service Worker

`app.js?v=82`, `styles.css?v=82`, CSS premium actualizadas y las referencias
de los módulos modificados (`address-engine`, `reservation-live`, `trip-board`)
se actualizan junto con `CACHE=traslados-pwa-v82-consolidado-20261018`.
`sw.js` precachea `pickup-origin-drift.js`, y la hoja de comprobaciones SHA-256
del ZIP contempla el nuevo módulo y CSS.

**G4 necesario:** ejecutar los controles de review, empaquetado, búsqueda, contratos,
reservas, ruta y seguimiento sobre este SHA; no dar por aprobada una secuencia de
cambios únicamente porque pasaron los PR individuales.

**G5 pendiente:** Samsung real para origen Aeropuerto → World Trade Center,
GPS de pasajero y conductor, ETA, reintento y trayectoria de viaje, reservas reales
con seguridad de no duplicar; verificar una actualización PWA instalada y una
navegación en navegador. No borrar datos ni permisos del teléfono.

**G6 condicionado:** GitHub Pages usa `main`; un merge despliega la nueva PWA.
No declarar estable ni afirmar ETA/ruta real sin evidencia de su proveedor y QA física.
No generar APK Conductor/Cliente ni Mapa por cambios exclusivos de la web.

El mapa del **viaje**, la jerarquía de precio/km/tiempo y limpieza visual siguen
definidos en UX-RECORRIDO-001 y **no son parte de esta consolidación funcional**.
