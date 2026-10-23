# Quick Trip — auditoría del bloqueo operativo único (23/10/2026)

**Estado:** diseño de QA en el PR #197. **NO es una migración aprobada ni instalada.** PostgreSQL usado en pruebas: `qt_capacity_test`, con datos ficticios. No ejecutar archivos de esta carpeta contra Supabase real.

## Identificar viajes

El cliente actual diferencia las modalidades con `pickup_mode`:
- `now`: recogida inmediata.
- Texto numérico entre `1` y `120`: recogida diferida en minutos, tratada operativamente como inmediata.
- `schedule`: programada, no ocupa al conductor antes de iniciar la recogida.
- Valores heredados o desconocidos: **se bloquean conservadoramente** al intentar entrar a estado operativo.

`fare_mode='fixed'` solo define cómo se cobra; **no** identifica una solicitud inmediata. La compatibilidad del campo y de todas las etiquetas de estados debe reconfirmarse contra el esquema real antes de desplegar.

## Restricción propuesta: índice único parcial

Archivo: [draft_atomic_guard.sql](draft_atomic_guard.sql).

La versión inicial planteaba un trigger y un advisory lock. **Fue reemplazada** por una restricción nativa de PostgreSQL: índice único parcial `qt_one_active_service_uq` sobre una expresión booleana constante para reservas reales. No contiene funciones `SECURITY DEFINER`, triggers ni nuevos permisos de ejecución.

Una reserva cuenta como **servicio operativo** si:
- Estado `EN_VIAJE`, cualquiera sea su modalidad.
- O un estado abierto (por ejemplo `ACEPTADA`, `CONFIRMADA`, `ACEPTADA_CLIENTE`) y la recogida NO es `schedule`.
- O recogida `schedule` con fase `EN_CAMINO` o `EN_PUNTO`.

`PENDIENTE`, `FINALIZADA`, `CANCELADA` y `RECHAZADA` no ocupan capacidad. El intervalo estimado de viaje no libera capacidad. Una `schedule` aceptada que todavía no arrancó sí puede permanecer en agenda. El índice protege TODAS las escrituras a la tabla, incluso desde distintas RPC o sesiones, y evita depender de que cada cliente compruebe disponibilidad.

**Importante:** PostgreSQL devolverá `23505` / `qt_one_active_service_uq`. El Android y la web deberán convertirlo a «Conductor ocupado», preservando la respuesta original si hay otro error. El índice no sustituye la validación de horarios de reservas programadas.

## Rutas a auditar antes de una eventual migración

| RPC existente | Revisión necesaria |
|---|---|
| `driver_decide_fixed_pickup_v1` | Aceptación inmediata. Comprobar mensaje de `23505` y compatibilidad del bloqueo existente por horario |
| `driver_set_status_v2` | Ruta alternativa que modifica ACEPTADA, EN_VIAJE, FINALIZADA. La restricción debe cubrirla |
| `customer_quote_decision_v13` y `v10` | Aceptación de presupuestos. Mantener programadas sin ocupar capacidad antes de recogida |
| `driver_set_pickup_phase_v1` | Al pasar una programada a EN_CAMINO o EN_PUNTO debe adquirir capacidad |
| `customer_request_pickup_fixed_v1` | Solo crea PENDIENTE; no cambia disponibilidad ni obliga a aceptar |
| Funciones antiguas de aceptación y cancelación | Revisar GRANT reales y estados terminales; no dejar rutas fuera del índice |

## Concurrencia y rollback

La suite de PostgreSQL 16 **local aislado** se ejecuta desde `.github/workflows/quicktrip-atomic-contracts-qa.yml`: dos conexiones `psql` intentan aceptar reservas ficticias a la vez. Se espera exactamente un commit exitoso y un error de unicidad. También se comprueba que iniciar recogida programada adquiere capacidad, que cerrar libera capacidad y que restaurar el índice falla de forma segura si hay duplicados históricos.

El rollback está en [rollback_atomic_guard.sql](rollback_atomic_guard.sql): elimina **solo el índice**. Ningún dato de reserva se borra, cancela, finaliza o modifica. **No aplicar** rollback por defecto sin una autorización específica.

## Bloqueos pendientes para producción

1. Auditar esquema real, etiqueta de enum y valores históricos de `pickup_mode` sin efectuar cambios.
2. Resolver explícitamente las dos reservas antiguas abiertas. **No** cerrarlas automáticamente para poder instalar el índice.
3. Respaldar definiciones, permisos, registros necesarios para verificación y plan de restauración sin subir secretos.
4. Probar rollback con el mismo esquema real replicado de manera aislada y revisar errores en Android/PWA.
5. Solicitar autorización independiente antes de migrar Supabase o publicar aplicaciones.

**Fuentes:** lógica del cliente `web-pasajero/trip-board.js` y `booking-contracts.js`; procedimientos de Supabase revisados antes de esta etapa; ensayos aislados sobre `qa_capacity.fake_reservations`.
