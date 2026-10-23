# Auditoría de rutas de cambio de estado (lectura SQL, 23/10/2026)

| RPC | Acepta o modifica | Riesgo / tratamiento |
|---|---|---|
| driver_decide_fixed_pickup_v1 | PENDIENTE -> ACEPTADA para tarifa fija | Ya tiene advisory lock de disponibilidad; necesita guardia de **servicio abierto**, no solo solapamiento horario |
| driver_set_status_v2 | PENDIENTE -> ACEPTADA; ACEPTADA -> EN_VIAJE; EN_VIAJE -> FINALIZADA | No tiene advisory lock; ruta alternativa que puede aceptar un segundo servicio |
| customer_quote_decision_v13 | Aceptación de presupuesto | Tiene advisory lock; preservar lógica de programados y no imponer bloqueo indiscriminado |
| customer_quote_decision_v10 | Aceptación de presupuesto (legado) | Sin advisory lock; determinar si aún tiene GRANT público o cliente antes de despliegue |
| driver_send_quote_v10 / v10_7 | Emite presupuesto, no aceptación directa | No bloquear presupuestos solo por estar ocupado; revalidar al aceptar |
| driver_set_pickup_phase_v1 | EN_CAMINO / EN_PUNTO | No acepta otro servicio, pero debe conservar identidad de reserva |
| customer_request_pickup_fixed_v1 | Crea solicitud PENDIENTE | No debe cambiarse: el pasajero puede solicitar; mostrar ocupación sin afirmar disponibilidad |
| customer_cancel_reservation / cancel_reservation_by_token | Cancela | Permite liberar cupo únicamente con estado terminal confirmado |
| customer_claim_reservation | Vincula reserva | Revisar si actualiza estado; no debe eludir trigger |

## Hallazgo crítico de diseño
La propuesta inicial de trigger en `draft_atomic_guard.sql` usa `fare_mode='fixed'` como **aproximación** de servicio inmediato. **NO APTO PARA PRODUCCIÓN** hasta comprobar discriminador canónico de viaje programado vs inmediato y alcance de otros servicios abiertos. También requiere prueba real con dos conexiones PostgreSQL y revisión de locks / SECURITY DEFINER. Es borrador para análisis, no migración aprobada.

## Política correcta a consolidar
- La regla de un conductor bloquea un segundo **servicio inmediato** hasta FINALIZADA/CANCELADA/RECHAZADA, independientemente de duración estimada.
- Conservar programados y presupuestos sin bloquearlos por error; cuando se vuelvan servicios activos, verificar compatibilidad real con el servicio inmediato actual.
- La comprobación en UI es defensiva; el bloqueo debe ser servidor, transaccional y cubrir cualquier RPC, REST o cliente concurrente.
- Si existen dos aceptadas antiguas, no borrarlas ni finalizarlas de oficio. El trigger debe bloquear nuevos inicios y dejar corregir estados antiguos de forma explícita.

## Rollback
1. Respaldar previamente definición de funciones, triggers, grants, filas de QA y configuración de consentimiento; nunca respaldar secretos en Git.
2. Para el guard propuesto: `rollback_atomic_guard.sql` quita trigger y función, **sin tocar reservas**.
3. Verificar que RPCs anteriores sigan operativos y que no se alteró GPS, PWA ni historial.
4. Cualquier migración definitiva requiere autorización separada, pruebas de concurrencia con 2 sesiones y plan de restauración.
