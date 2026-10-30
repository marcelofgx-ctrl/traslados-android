# Quick Trip P1.2 — Sin botones de mapa, transición automática después de la aceptación

## Requisito de producto
La pantalla **Tu viaje**, antes de aceptar, concentra origen y destino, parada opcional, distancia/tiempo de recorrido verificado, tarifa, estado del conductor, distancia/ETA de recogida si están disponibles, horario y datos personales. **No muestra mapa duplicado, botón «ver mapa» ni gesto «deslizar para ver mapa».** La solicitud permanece pendiente hasta confirmación auténtica del conductor. Una vez que el servidor devuelve la transición a `ACEPTADA` (o estado elegible), el sistema **abre el fullscreen map negro automáticamente**, respetando autenticación y la selección única. Después de Atrás a Inicio, otra pulsación sobre Tu viaje retorna al mapa si hay una reserva activa, sin bucle.

## Alcance QA
Base `4c0a3b68d9069cc03c13d23e2de2d913f5003292`. Rama `qa/quicktrip-p12-auto-map-no-swipe-20261030`.
Se elimina el rail táctil y el botón manual redundante de `quicktrip-active-ride.js`; se eliminan sus selectores CSS obsoletos. Se elimina el acceso al mapa de una solicitud pendiente en el fallback visual `renderPremiumTracking` (no se abre GPS sin aceptación). Se conservan el resumen y los enlaces de navegación alternativos. `index.html` y `sw.js` reciben cache-busting para actualizar el Samsung.

Se preservan **sin cambio** `route-engine.js`, `premium-route.js`, `eta-engine.js`, `pickup-cadence.js`, `pickup-observability.js`, `reservation-live.js`, `quicktrip-map-focus.js`, `premium-map.js`, `pickup-origin-drift.js`, geocodificación, precios, RPC privada, SQL y Android. La detección de aceptación usa la lógica ya existente en `loadTrips` y `autoFollowAccepted` (no nueva polling ni reservas simuladas fuera de Chrome).

## Prueba y rollback
- Bloquear archivos no permitidos desde base, ejecutar pruebas independientes de kilómetros viales, tarifa fija, ETA/cadencia, y regresiones P1.1.
- Chrome 390x844: formulario y datos reales DOM; resumen activo sin slider y sin botón manual; ninguna solicitud pendiente o programada abre tracking; transición simulada de estado autorizado de PENDIENTE→ACEPTADA abre fullscreen una vez; FINALIZADA no lo abre; Atrás vuelve Inicio; Tu viaje reanuda la pantalla activa; prueba existente de seguimiento GPS con ruta blanca y métricas.
- Los fixtures de Chrome no son una aceptación real. Prueba física pendiente con Mapa QT conductor y otro teléfono: enviar solicitud, aceptar, apertura automática, mapa, distancia/ETA, volver Inicio→Tu viaje, finalizar.
- Se conserva Preview QA anterior para revertir alias sin fusionar, sin tocar main, producción ni Supabase.
