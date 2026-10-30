# Quick Trip P1.1 · Retorno al viaje / ETA de recogida (QA)

Base verificada: `d5f4066a7be73b4863e8d97a664b892043c42704`.
Rama aislada: `qa/quicktrip-p11-eta-reentry-clean-20261030`. Alcance autorizado: continuidad de la recogida al volver, limpiar dos frases redundantes. No tocar Supabase, base SQL, GPS privado, ETA Edge, funciones de búsqueda vial, precio, estado de reservas, Android o producción.

## Diagnóstico

El formulario conserva `record.origin` en la navegación SPA; la ruta, los kilómetros y la tarifa no se recalculan en `shiftView`. El reloj de recogida corre cada 15 segundos **solo en primer plano**, y cuando se volvía a `Tu viaje`, `shiftView` no solicitaba inmediatamente un refresco de `refreshPickupPresence`. El paso de vuelta podía mostrar datos viejos hasta el siguiente reloj o hasta el plazo permitido de la función ETA.

Si aparece `¿Dónde te buscamos?`, el origen NO tiene coordenadas elegidas. Es correcto no mostrar ETA al conductor; el usuario debe elegir el punto (sin inventar una geolocalización automática).

Además, dos frases ocupaban espacio: `pickup-estimate-note` («Estimación obtenida hace…») y `pickup-presence-meta` («Disponibilidad comprobada ahora…»). No son el resultado operativo. En P1.1 se esconden SOLO visualmente en el formulario de recogida. Título, subtítulo, errores, reintento, estado accesible y métricas reales `#pickup-distance`, `#pickup-minutes` quedan intactos.

## Corrección

- Un `refreshPickupOnReturn` visual aplica cuando una navegación interna entra en `view-viaje`, cuando la pestaña vuelve a primer plano, cuando una página revive desde bfcache y al reconectarse online.
- Solo consulta cuando el formulario inmediato está visible, no hay confirmación final abierta ni viaje ya aceptado para seguimiento; no fuerza consultas fuera de `refreshPickupPresence()`.
- Reutiliza `publicDriverAvailability` (caché 8 s), `pickupEtaPendingKey` (un único RPC en vuelo), y los intervalos `25 s` en movimiento y `45 s` detenido. Es una solicitud de refresco, **no una ruta alternativa** y no elude las cuotas/privacidad del servidor.
- Limita los eventos duplicados de navegación/foco dentro de una ventana de 1.2 s; el reloj existente sigue funcionando.
- Los dos mensajes se eliminan del plano visual con CSS, pero la región `role=status` conserva la accesibilidad.

## Gates

1. Diff estricto para proteger todos los motores y Supabase.
2. Contratos premium previos + P1, transporte de ruta, tarifa fija, adaptador ETA, GPS drift y ritmo del motor.
3. Chrome 390×844 con datos sintéticos: regresar `Inicio → Tu viaje`, retorno al primer plano, bfcache, online (sin duplicados), modo programado, ocultación selectiva de frases y visibilidad de metros/minutos reales en el mismo DOM.
4. **No confirma** que un conductor real esté disponible ni el rendimiento de la Edge Function. Validación posterior sobre Samsung con origen explícitamente seleccionado, espera al intervalo permitido, y recorrido real.

## Prohibido
No reservas reales de prueba durante CI; no nuevas lecturas privadas del GPS conductor; no cambios de base real, APK, main o producción. Si CI falla no actualizar el alias QA.
