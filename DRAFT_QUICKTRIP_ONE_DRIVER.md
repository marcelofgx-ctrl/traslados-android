# Quick Trip — un servicio operativo y GPS por reserva

**Rama experimental aislada. No aplicar en Supabase real, no hacer merge, APK ni despliegue.**

## Entregables
- [Consulta previa (solo lectura)](proposals/quicktrip_single_driver_v1/01_preflight_READ_ONLY.sql): detecta servicios sin cerrar.
- [Índice transaccional PROPUESTO, NO EJECUTAR](proposals/quicktrip_single_driver_v1/02_guard_PROPOSAL_NOT_EXECUTE.sql): garantiza una sola recogida operativa incluso bajo concurrencia, sin usar ETA como finalización.
- [Rollback PROPUESTO, NO EJECUTAR](proposals/quicktrip_single_driver_v1/03_rollback_PROPOSAL_NOT_EXECUTE.sql): elimina únicamente el índice, sin borrar reservas ni GPS.
- [Inventario de RPC y plan de despliegue futuro](proposals/quicktrip_single_driver_v1/RPC_AUDIT_AND_ROLLBACK.md).
- [Contrato GPS v2 asociado a reserva](contracts/quicktrip_reservation_gps_v2.md) y [política de privacidad ficticia](contracts/quicktrip_reservation_gps_v2_policy.cjs).
- [Prueba de carrera entre dos aceptaciones](tests/quicktrip_one_driver/run_postgres_fictional.sh) contra [datos 100% inventados](tests/quicktrip_one_driver/fake_schema.sql), con PostgreSQL en GitHub Actions.
- [Pruebas GPS sin red](tests/quicktrip_one_driver/gps_contract_fictional.cjs).

## Qué protege exactamente la regla
Solo el conductor de Quick Trip:
- Todo `EN_VIAJE` ocupa la capacidad.
- Todo `ACEPTADA`, `ACEPTADA_CLIENTE` o `CONFIRMADA` con `pickup_mode='now'` ocupa la capacidad hasta el cierre real, incluso si el recorrido lleva horas de retraso.
- Un programado `ACEPTADA` ocupa el turno al iniciar `EN_CAMINO` / `EN_PUNTO`, pero puede quedar programado en agenda sin bloquear anticipadamente los viajes inmediatos.
- El índice único hace fracasar una segunda aceptación de cualquier RPC o dispositivo, con SQLSTATE `23505`; el cliente deberá convertirlo en un mensaje claro de conductor ocupado.

**Bloqueo actual en la base real:** `TR-261023-529FD` y `TR-261023-9BD22` siguen ambos ACEPTADA / EN_PUNTO. Por diseño el SQL de instalación rechazará activar la regla hasta que el propietario autorice regularizarlos. Ningún archivo de este proyecto los modifica.

La protección efectiva es PostgreSQL. Una comprobación Android (PR #195) solo mejora la experiencia, **no sustituye la transacción**.

## Ejecución autorizada ahora
Únicamente tests offline y revisión. Ningún permiso de producción es requerido ni entregado al workflow. El contrato GPS v2 no está integrado en el APK ni en Supabase.

Próxima aprobación separada: revisión de la propuesta, plan de saneamiento explícito de dos reservas antiguas, staging, permisos RPC, desarrollo de error `23505` amigable y ensayos de seguimiento real con consentimiento del conductor.
