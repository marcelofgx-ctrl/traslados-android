# Quick Trip — auditoría del bloqueo operativo único (actualizada 24/10/2026)

**Estado:** diseño de QA en el PR #197. **NO es una migración aprobada ni instalada.** PostgreSQL usado en pruebas: `qt_capacity_test`, versiones **16 y 17**, con datos ficticios. No ejecutar archivos de esta carpeta contra Supabase real.

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

Una reserva cuenta como **servicio operativo** solo con estos estados explícitos:
- `EN_VIAJE` ocupa capacidad, con cualquier modalidad y fase.
- `ACEPTADA`, `ACEPTADA_CLIENTE` o `CONFIRMADA` ocupan capacidad cuando `pickup_mode` es `now`, numérico de 1 a 120 o heredado/desconocido.
- Estas mismas tres etiquetas con `pickup_mode='schedule'` solo ocupan capacidad en fase `EN_CAMINO` o `EN_PUNTO`. Hasta entonces, una programada confirmada puede permanecer en agenda.

**No operativos (enum real, auditado solo por lectura el 24/10/2026):** `PENDIENTE`, `PRESUPUESTO_ENVIADO`, `RECHAZADA_CLIENTE`, `RECHAZADA`, `CANCELADA`, `FINALIZADA`. No ocupan capacidad aunque el campo `pickup_phase` conserve por error un valor de recogida. La versión anterior usaba `status NOT IN (...)` y podía tratar erróneamente los estados `PRESUPUESTO_ENVIADO` y `RECHAZADA_CLIENTE` como servicios activos.

**Política ante nuevos estados del enum:** no se contarán automáticamente. Antes de introducir un nuevo estado **operativo** hay que auditar y ampliar expresamente el índice; de lo contrario la protección podría quedar incompleta para ese estado. Esto no debe confundirse con la política conservadora de `pickup_mode` heredado, que sí ocupa capacidad al entrar a un estado operativo. El intervalo estimado de viaje no libera capacidad. Una `schedule` aceptada que todavía no arrancó sí puede permanecer en agenda. El índice protege TODAS las escrituras a la tabla, incluso desde distintas RPC o sesiones, y evita depender de que cada cliente compruebe disponibilidad.

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

La suite de PostgreSQL 16 **aislado** se ejecutó exitosamente desde `.github/workflows/quicktrip-atomic-contracts-qa.yml` (run 40058257833). La nueva suite de PostgreSQL 17 **aislado** también concluyó SUCCESS (run 40059052425, commit 0c9c0774540ca8752d11e439b22cdae83e9163e1) e incluyó cinco funciones RPC representativas ficticias, carreras mixtas y confirmación SQLSTATE 23505. Estas RPC no reproducen autenticación, agenda, triggers ni RLS completos. El ensayo de PG16 verifica que: dos conexiones `psql` intentan aceptar reservas ficticias a la vez. Se espera exactamente un commit exitoso y un error de unicidad. También se comprueba que iniciar recogida programada adquiere capacidad, que cerrar libera capacidad y que restaurar el índice falla de forma segura si hay duplicados históricos. La ampliación del 24/10 incorpora una matriz con **las 10 etiquetas reales del enum**, estados rechazados/propuesta que no ocupan, reservas programadas aún no iniciadas, estados operativos alternativos y `EN_VIAJE` sin fase de recogida.

El rollback está en [rollback_atomic_guard.sql](rollback_atomic_guard.sql): elimina **solo el índice**. Ningún dato de reserva se borra, cancela, finaliza o modifica. **No aplicar** rollback por defecto sin una autorización específica.

## Bloqueos pendientes para producción (actualizado tras PG17)

1. **Estado real a última lectura (24/10/2026, 03:39 UTC):** 24 reservas, 0 servicios operativos y el índice aún ausente. Las reservas `TR-261023-529FD` y `TR-261023-9BD22` pasaron a FINALIZADA en una etapa previa, sin cambios por esta auditoría. Autor de esos cierres **no determinado**. Verificar su legitimidad, y repetir lectura inmediatamente antes de cualquier migración.
2. **Respaldo no verificado:** todavía no hay evidencia de copia utilizable, PITR ni restauración ensayada. No proceder por la sola existencia del proyecto activo.
3. **RPC v10 legada:** sigue ejecutable para anon/authenticated mediante SECURITY DEFINER, sin advisory ni la verificación moderna de horario. El índice protege ocupación concurrente, pero no agenda futura. Revisar contrato y dependencias.
4. **Permisos:** RLS para UPDATE de conductores con `is_driver(auth.uid())`, GRANT generalizado y columna `is_demo` editable para ese rol; el predicado excluye `is_demo=true`. Comprobar que escritores con acceso no puedan eludir la exclusividad. No cambiar RLS/GRANT en esta etapa.
5. **Clientes:** Android conserva backendCode pero no mapea expresamente el error nuevo; `web-pasajero/rpc-client.js` pierde `parsed.code`. Exigir prueba de `23505` del índice exacto, diferenciándolo de otras unicidades y del timeout.
6. **QA PG17 completada solo con fixtures:** faltan las funciones/triggers/RLS/GRANT reales replicadas en entorno aislado. No confundir dos RPC simuladas con pruebas sobre el servidor de producción.
7. Solicitar autorización independiente para respaldos, correcciones de seguridad/clientes, migración real, merge y publicación; ninguna está implícita.

**Fuentes:** lógica del cliente `web-pasajero/trip-board.js` y `booking-contracts.js`; procedimientos de Supabase revisados antes de esta etapa; ensayos aislados sobre `qa_capacity.fake_reservations`.

## Alcance y evidencia de esta etapa aislada

- Se modifica únicamente el borrador del índice y sus pruebas/documentación en el PR #197. No es una migración instalada, ni cambia GPS o reservas reales.
- Se mantiene el índice único parcial sobre expresión booleana para conductor único, la regla de `pickup_mode`, las transiciones y el rollback que solo elimina el índice.
- La suite sintética de PostgreSQL valida estados; **no** acredita compatibilidad definitiva de los RPC de producción ni autorización para desplegar.
- PR #195 y PR #197 continúan sin fusionar. Requieren aprobación posterior para cualquier cambio en Supabase real, Android o web.

## Dictamen actualizado y plan

**NO-GO para producción.** La prueba de PostgreSQL 17 elimina únicamente la incertidumbre de la versión principal en el índice/fixture; la infraestructura Supabase 17.11, respaldo recuperable, seguridad `is_demo` y compatibilidad extremo a extremo continúan pendientes.

La matriz de compuertas G1–G7, los hechos observados, las condiciones de rollback y los enlaces al CI están en [PLAN_MIGRACION_SUPABASE_SOLO_REVISION_20261024.md](PLAN_MIGRACION_SUPABASE_SOLO_REVISION_20261024.md). No se instaló índice, modificó reserva ni fusionó PR. Este documento es exclusivamente auditoría.
