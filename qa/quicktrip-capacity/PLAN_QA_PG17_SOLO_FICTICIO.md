# Quick Trip — QA de capacidad PostgreSQL 17 en GitHub Actions (datos ficticios)
**24/10/2026 | Solo entorno descartable, NO migración ni despliegue.**

## Alcance autorizado
- Rama exclusiva: qa/quicktrip-capacidad-pg17-20261024, partiendo del plan documentado en b5c83f4514d7fb322eccb97ca7cddb824caa2bf5.
- Reutilizar SIN MODIFICAR el SQL borrador V3, rollback, fixture y pruebas de PostgreSQL 16 del PR #197. Ejecutarlos esta vez contra contenedor postgres:17-alpine.
- Crear solo esta documentación, un SQL de *RPC representativas ficticias*, un test Python PG17 y su workflow exclusivo.
- No conectarse a Supabase real; no credenciales reales ni datos personales. Nada de APK, fusión, Android, web o SQL productivo.

## Diseño de los ensayos
1. Bloqueo de alcance de cuatro archivos y comprobación obligatoria de base qt_capacity_test, host local y versión PG17.
2. Fixture qa_capacity.fake_reservations, con el predicado exacto del índice V3 aplicado solamente mediante sustitución textual de public.reservations por qa_capacity.fake_reservations.
3. Suite previa: 10 estados del enum, modalidad now, diferida 1..120, schedule, datos heredados, demo, cierre, concurrencia de dos transacciones independientes, rollback.
4. RPC ficticias, SOLO en qa_capacity:
   - driver_decide_fixed_pickup_replica: advisory lock de agenda y FOR UPDATE.
   - customer_quote_decision_v13_replica: advisory lock, FOR UPDATE, aceptación programada.
   - customer_quote_decision_v10_replica: aceptación legacy sin advisory lock ni disponibilidad.
   - driver_set_status_v2_replica: FOR UPDATE, transiciones y cierre.
   - driver_set_pickup_phase_v1_replica: recogida programada adquiere capacidad.
5. Carrera de dos sesiones PostgreSQL 17: RPC conductor contra RPC v10; **exactamente** un COMMIT y un error **23505** en qt_one_active_service_uq.
6. Rollback: DROP INDEX exclusivamente en qa_capacity, preservar número de filas, demostrar que las duplicaciones pueden volver, intento de reinstalación falla con duplicados y reinstalación pasa cuando se corrigen *solo filas sintéticas*.
7. Contratos puros Python que inspeccionan el SQL y rollback.

## Qué NO demuestra
Estas RPC son REPLICAS **parciales, no las funciones de Supabase**: no ejecutan validaciones reales de PIN/sesión, GRANT/RLS, geometría, agenda ni expiración; no demuestran que una versión de Android/PWA gestione 23505 correctamente. PG17 de contenedor no equivale a verificar todos los parches/configuraciones de Supabase PostgreSQL 17.11. Los cambios de reservas en producción observados en la auditoría anterior no forman parte de estas pruebas. La migración real sigue bloqueada por sus prevalidaciones, respaldo comprobado, revisión de RPC y autorización específica.

## Evidencia
La acción Quick Trip Capacity PG17 Disposable RPC QA (NO PRODUCTION) se ejecuta automáticamente al hacer push a la rama aislada. Consultar job, SHA y registros de PASS; la ausencia de comprobación no debe comunicarse como éxito. El workflow no tiene secretos ni comandos de comunicación con la base de producción.
