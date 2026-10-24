# Quick Trip / Mapa QT — Plan técnico de migración de capacidad atómica
**Fecha de corte:** 24 de octubre de 2026  
**Estado:** SOLO REVISIÓN · NO EJECUTAR EN PRODUCCIÓN · DOCUMENTACIÓN  
**Proyecto Supabase:** Traslados, ref zetaudvvutlouiqxopvg  
**Repositorio:** https://github.com/marcelofgx-ctrl/traslados-android  
**PR de código:** https://github.com/marcelofgx-ctrl/traslados-android/pull/197 (abierto, borrador, no fusionado)  
**Base congelada de este plan:** b912768eb38cd2d6963b586fe8a89b2dd517e687  
**Último CI aislado aprobado:** https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40058257833  

> **Restricción estricta:** este archivo es una propuesta de operación FUTURA. No autoriza ni realiza DDL, DML, copias de datos reales, cierres/cancelaciones de reservas, cambios de roles, RPC, web, Android, GPS, CI productivo, fusiones o despliegues. Toda ejecución requiere una aprobación nueva y específica. No subir volcados, PIN, tokens, claves ni teléfonos al repositorio.

## 1. Objetivo y límites

Impedir **más de un servicio operativo real simultáneo** para el único conductor, incluso ante dos RPC o sesiones concurrentes, sin impedir que existan solicitudes pendientes o viajes programados aceptados que todavía no iniciaron recogida. Preservar los motores actuales de Mapa QT, las reservas programadas y la disponibilidad por franjas. El índice no libera capacidad por tiempo transcurrido, ETA ni duración estimada; solo por cambio a estado no operativo.

Quedan **fuera**: GPS privado, presencia pública, pantalla Conductor, PWA, cambios en la autenticación, reescritura de motores o implementación de correcciones de horario. Se describen las dependencias y condiciones bloqueantes, pero no se ejecuta ninguna.

**SQL candidato exacto:** qa/quicktrip-capacity/draft_atomic_guard.sql, revisión V3 del PR #197. **Rollback candidato:** qa/quicktrip-capacity/rollback_atomic_guard.sql. Este documento no crea un archivo de migración dentro de supabase/migrations.

## 2. Inventario observado en Supabase REAL — lecturas únicamente

Consultas al catálogo y tablas el 24/10/2026, **no es una instantánea inmutable**:

| Dato | Observado |
|---|---|
| Proyecto | ACTIVE_HEALTHY, PostgreSQL **17.11** (rama GA 17) |
| Base de QA previa | PostgreSQL **16**, contenedor descartable; necesita repetición en PG **17** |
| public.reservations | **24** filas; RLS activo |
| Estados reservation_status | PENDIENTE, ACEPTADA, EN_VIAJE, FINALIZADA, CANCELADA, RECHAZADA, PRESUPUESTO_ENVIADO, ACEPTADA_CLIENTE, CONFIRMADA, RECHAZADA_CLIENTE |
| Modos de recogida | now, schedule y valores numéricos de 1 a 120, con restricción CHECK en producción |
| is_demo | falso en las 24 filas observadas |
| Índice qt_one_active_service_uq | **NO existe** en producción |
| Servicios reales que satisfacen el predicado | **2** (bloqueo actual) |
| GPS privado en reservation_live_position | 0 posiciones; NO es criterio para instalar la restricción |
| Eventos históricos reservation_events | 59 filas |
| Triggers en reservations | reservation_schedule_request_policy_v1, reservations_event_insert, reservations_event_update, reservations_fixed_fare_guard_v1 |

**Dos reservas que NO se pueden alterar sin decisión separada:**

- TR-261023-529FD: ACEPTADA / EN_PUNTO / now.
- TR-261023-9BD22: ACEPTADA / EN_PUNTO / now.

**Resultado lógico si se intentara aplicar ahora:** falla CREATE UNIQUE INDEX porque las dos filas reales comparten la misma clave booleana del índice parcial. NO proceder. No resolver duplicados cambiando estados, fechas, pickup_mode, is_demo ni eliminando filas. No adjudicar cuál traslado ocurrió realmente: eso requiere prueba operativa y autorización del usuario.

## 3. Contrato inmutable que debe mantenerse

El índice único parcial está definido para reservas NO demo. Cuenta solo:

1. EN_VIAJE, con independencia de pickup_mode y pickup_phase.
2. ACEPTADA / ACEPTADA_CLIENTE / CONFIRMADA con pickup_mode distinto de schedule, incluidos los casos heredados/desconocidos (solo si son estados operativos).
3. ACEPTADA / ACEPTADA_CLIENTE / CONFIRMADA con pickup_mode schedule y pickup_phase EN_CAMINO o EN_PUNTO.

NO cuentan: PENDIENTE, PRESUPUESTO_ENVIADO, RECHAZADA_CLIENTE, RECHAZADA, CANCELADA y FINALIZADA. Una schedule aceptada en SIN_INICIAR puede permanecer en agenda. fare_mode=fixed indica tipo de tarifa, no modalidad de recogida. Nunca usar duración/ETA como liberación de capacidad.

**Riesgo de excepción demo:** is_demo=true queda fuera de la unicidad por diseño. Antes de instalar, auditar privilegios/flujo de escritura de is_demo para que una reserva real nunca pueda eludir el índice marcándose como demo. Una nueva etiqueta operativa del enum exige actualizar explícitamente el predicado y las pruebas ANTES de habilitarla.

## 4. Prevalidaciones obligatorias (sólo SELECT; ejecutar DE NUEVO inmediatamente antes de cualquier aprobación futura)

**P1. Identidad de base, esquema, versión, índice existente y estados:**

~~~sql
SELECT current_database(), current_setting('server_version'), now();
SELECT e.enumlabel FROM pg_enum e
JOIN pg_type t ON t.oid=e.enumtypid
JOIN pg_namespace n ON n.oid=t.typnamespace
WHERE n.nspname='public' AND t.typname='reservation_status'
ORDER BY e.enumsortorder;
SELECT to_regclass('public.qt_one_active_service_uq') AS current_index;
SELECT status::text, pickup_mode, pickup_phase, is_demo, count(*) AS n
FROM public.reservations
GROUP BY 1,2,3,4 ORDER BY 1,2,3,4;
~~~

**P2. Conteo de capacidad REAL, con predicado idéntico al índice V3 (no devuelve datos personales):**

~~~sql
SELECT count(*) AS occupied_real_count
FROM public.reservations
WHERE coalesce(is_demo,false)=false
AND (
  status='EN_VIAJE'
  OR (
    status IN ('ACEPTADA','ACEPTADA_CLIENTE','CONFIRMADA')
    AND (coalesce(pickup_mode,'') <> 'schedule'
         OR pickup_phase IN ('EN_CAMINO','EN_PUNTO'))
  )
);
~~~

**Condición estricta para proceder:** se RECOMIENDA cero servicios operativos durante ventana de mantenimiento (técnicamente una fila no contradice unicidad, pero el plan conservador exige cero y actividad detenida). Si el resultado es distinto de cero, DETENER. La foto actual es 2. Nunca modificar reservas como parte del preflight.

**P3. Inventario de superficie RPC, triggers, permisos y RLS:** volver a consultar pg_proc / pg_get_functiondef, pg_trigger, pg_policies, pg_indexes, roles y privilegios por columna. Comparar con manifiesto de respaldo. Verificar escritura por rutas conocidas y versiones heredadas. Confirmar ausencia de procedimientos/columnas nuevos entre el corte de auditoría y el despliegue.

**P4. Respaldo VALIDADO (no consta respaldo realizado en esta etapa):** confirmar en Dashboard > Database > Backups la retención y disponibilidad real de respaldos/PITR; tomar o confirmar punto de recuperación adecuado según el plan del proyecto. Además guardar una exportación segura (fuera de GitHub) de esquema, índices, restricciones, triggers, funciones, políticas, GRANT y manifiesto/versiones de migraciones; capturar copia protegida y cifrada de reservas/eventos y otras tablas relacionadas SOLO con autorización para respaldo real. Registrar checksums, responsable y procedimiento de restauración; ensayar restauración en un ambiente SEPARADO. Sin evidencia de restauración posible, NO APLICAR.

**P5. Ensayo representativo:** restaurar esquema compatible y datos FICTICIOS/anonimizados en PostgreSQL **17**, nunca en producción. Repetir pruebas del PR #197 y demostrar: (a) duplicados hacen fallar la instalación sin cambiar datos; (b) cero operativos permite instalar; (c) dos aceptaciones concurrentes devuelven una confirmación y un 23505; (d) inicio de recogida programada adquiere capacidad; (e) cierre libera; (f) rollback conserva filas; (g) triggers y RPC representativos mantienen sus contratos. No utilizar tokens, PIN o datos reales sin nueva autorización.

**P6. Ventana y rollback aprobados:** detener temporalmente escrituras/solicitudes de reserva de forma controlada y verificable, sin dar por cerrados servicios. Fijar operador y responsable de decisión, acceso a respaldo, procedimiento de abortar y plan de observación. No habilitar proceso automático que cambie reservas previas.

## 5. Matriz de compatibilidad RPC (inspección de solo lectura)

| Ruta existente | Observación real / prueba exigida |
|---|---|
| driver_decide_fixed_pickup_v1 | Usa advisory lock y availability_route_check_v11_4 antes de aceptar; el índice debe defender la segunda aceptación incluso si fallan estimaciones. Revisar error 23505 por nombre exacto. |
| driver_set_status_v2 | UPDATE de estado bajo FOR UPDATE de la propia reserva; puede pasar PENDIENTE→ACEPTADA y ACEPTADA→EN_VIAJE / CANCELADA. La unicidad inter-filas queda a cargo del índice. |
| driver_set_pickup_phase_v1 | Actualiza EN_CAMINO/EN_PUNTO con FOR UPDATE; debe bloquear una schedule que comienza recogida mientras hay otra operativa. |
| customer_quote_decision_v13 | Usa advisory lock y disponibilidad; puede confirmar schedule sin ocupar físicamente capacidad. Verificar horarios superpuestos por mecanismo existente. |
| customer_quote_decision_v10 | **RIESGO HEREDADO:** todavía disponible con EXECUTE; la implementación observada no hace el mismo chequeo de disponibilidad ni advisory lock. El índice protege ocupación inmediata, pero NO resuelve conflictos de agenda futura. Requiere decisión de compatibilidad/autorización separada, nunca retirada silenciosa. |
| customer_request_pickup_fixed_v1 / customer_request_pickup_v1 | Crean solicitudes o hacen actualización idempotente; verificar que PENDIENTE permanezca admitida y que la eventual transición a operativa no eluda índice. |
| driver_send_quote_v10 / v10_7 | Actualizan presupuesto/estado, sin ocupar capacidad con presupuesto enviado. |
| customer_cancel_reservation / cancel_reservation_by_token | Pueden pasar ciertas reservas a CANCELADA y liberar capacidad; verificar el cierre correcto, trazabilidad y autorización. |
| create_reservation, customer_create_reservation y versiones, customer_claim_reservation | Revisar INSERT/UPDATE legados y sus GRANT; el índice debe proteger cualquier fila que entre a estado operativo, sin reescribirlas en esta etapa. |
| Trigger reservations_fixed_fare_guard_v1 | Protege precio e impone quote_status/confirmed_at en aceptación directa; ensayar conjuntamente con el nuevo índice. |
| Triggers de eventos / schedule | Verificar que se sigan grabando eventos válidos y aplicando la política schedule. |

**Acceso:** las ocho RPC seleccionadas revisadas exponen EXECUTE a anon y authenticated y usan SECURITY DEFINER con validaciones propias. No retirar privilegios sin estudio de dependencias. public.reservations tiene RLS; la política UPDATE para authenticated permite al conductor identificado por is_driver(auth.uid()), sin restringir individualmente cambios a status, pickup_mode, pickup_phase o is_demo. Las concesiones SQL de UPDATE son amplias; RLS sigue limitando qué usuarios pueden modificar filas. **Riesgo:** un acceso legítimo de conductor o una rutina privilegiada podría alterar is_demo para eludir la restricción; endurecer controles requiere propuesta y aprobación independientes. El índice no es un reemplazo de autorización ni de transiciones válidas.

## 6. Errores, UI y contratos de retorno (sin modificar apps ahora)

- La excepción a reconocer es SQLSTATE **23505** con índice/constraint exacto **qt_one_active_service_uq**, NO cualquier 23505 de reservas (code/public_token/client_request_id tienen unicidad propia).
- Capa RPC, Android y PWA deben presentar **“Conductor ocupado; finalizá o cancelá el servicio anterior antes de aceptar otro.”** o texto equivalente, sin atribuirlo a Internet.
- Preservar errores independientes: HORARIO_YA_NO_DISPONIBLE / OCCUPIED de disponibilidad horaria, solicitud expirada, PIN/sesión inválida y falta de red. No confundir 23505 por otros índices con conductor ocupado.
- Antes de aprobar producción, probar el error con dos sesiones PostgreSQL 17 y verificar la respuesta completa en cada versión de cliente que permanece instalada. La migración no requiere reescribir Android, pero las interfaces deben manejar el nuevo caso sin perder estados ni avisar falsamente confirmación.
- Nunca publicar una reserva como aceptada si la transacción falla. Para fallos de red después de commit, releer el estado autoritativo antes de reintentar; proteger idempotencia.

## 7. Procedimiento propuesto de aplicación futura — BLOQUEADO / NO EJECUTAR

**A0 — Autorización nueva:** solicitar permiso inequívoco para Supabase REAL, con fecha, SQL/commit exacto, plan de respaldo y resolución independiente de reservas abiertas. Sin todas esas condiciones, FIN.

**A1 — Verificación doble:** repetir P1-P6 y contrastar conteos en lectura antes y durante la ventana. Si hay más de cero activos, SQL distinto, cambio de enum, backup sin comprobar o actividad que no se pueda pausar, ABORTAR antes de DDL.

**A2 — Mantenimiento corto:** limitar temporalmente las nuevas escrituras bajo un mecanismo ya aprobado; consultar sesiones y transacciones largas; no finalizar reservas por rutina. La tabla observada es pequeña pero no se promete ausencia de bloqueos.

**A3 — Instalación transaccional:** ejecutar EXCLUSIVAMENTE la versión de índice ya aprobada, con bloqueo de escritura apropiado, límites de espera y comprobación del predicado **dentro de la misma transacción que crea el índice**. Recomendación: construcción normal CREATE UNIQUE INDEX (no CONCURRENTLY) en mantenimiento; PostgreSQL permite esa creación transaccional y bloquea escrituras de otras sesiones mientras construye. Un índice CONCURRENTLY tiene otras semánticas, no puede estar dentro de transacción explícita y puede quedar inválido al fallar; NO cambiar a ese método sin una revisión separada.

**A4 — Validación inmediata:** comprobar que el índice existe y figura válido/listo en pg_index, que la definición coincide con el SQL aprobado, que los conteos de reservas/eventos antes y después no disminuyeron de manera inexplicable, y que los RPC, clientes y programados no presentan regresiones. Ejercitar concurrencia solo en entorno de prueba (nunca creando viajes falsos reales sin autorización).

**A5 — Reapertura controlada:** habilitar operativa normal únicamente si A4 pasa; observar 23505, disponibilidad y estabilidad. No publicar APK/PWA como efecto lateral de esta migración.

**ABORT:** ante anomalía anterior al COMMIT, hacer ROLLBACK de la transacción y verificar estado original. Documentar error; no modificar datos para forzar éxito.

## 8. Rollback operativo futuro (requiere autorización específica)

**Ruta R1 — Fallo durante transacción de creación:** ROLLBACK; no debería existir el índice creado dentro de la transacción fallida. Confirmar ausencia, invariantes de reservas y registros, notificar incidente.

**Ruta R2 — Índice creado y confirmado, pero provoca regresión:** pausar escrituras de reserva, capturar diagnósticos y solicitar autorización explícita para usar SOLO qa/quicktrip-capacity/rollback_atomic_guard.sql (DROP INDEX IF EXISTS public.qt_one_active_service_uq). Confirmar to_regclass IS NULL, que las filas/eventos se conservan y que la aplicación no depende de ese índice en un estado mixto. **Retirar el índice vuelve a permitir aceptaciones simultáneas:** mantener protección operativa alternativa o suspender nuevas aceptaciones hasta corregir. No restaura los rechazos ni las solicitudes que hayan ocurrido durante la vigencia del índice.

**Ruta R3 — Recuperación de base:** restaurar desde respaldo/PITR es una operación distinta, potencialmente destructiva para escrituras posteriores al punto de recuperación y requiere decisión de incidente separada. NO ejecutarla como rollback rutinario de este índice.

**Nunca** modificar/cancelar/finalizar viajes reales, poner is_demo=true o editar pickup_mode para fabricar el preflight deseado.

## 9. Riesgos, responsables y gates

| Severidad | Riesgo | Bloqueo o mitigación antes de producción |
|---|---|---|
| BLOQUEANTE | Dos servicios abiertos reales | Resolución separada autorizada por el usuario, con evidencia de viaje; revalidar ocupación=0 |
| BLOQUEANTE | No hay prueba PG17 ni recuperación validada | QA en PG17 con esquema representativo, backup disponible y restauración ensayada |
| ALTA | RPC v10 aún habilitada y sin disponibilidad moderna | Auditar llamadas activas y decidir mantener, adaptar o revocar solo en etapa aparte |
| ALTA | Posible bypass is_demo / UPDATE directo de conductor | Revisar GRANT, RLS, columnas y rutas que escriben is_demo; demostrar que no se elude |
| ALTA | UI atribuye 23505 a red u otro fallo | Pruebas de mapeo estricto por índice y reconciliación de respuesta |
| MEDIA | Escrituras concurrentes durante CREATE INDEX normal | Ventana de mantenimiento, lock/statement timeouts y abortar si no hay exclusividad |
| MEDIA | Diferencia PG16 de CI y PG17 real | Repetir integración/rollback en PG17 antes del cambio |
| MEDIA | Estados enum nuevos, agenda programada o heredados | Reauditar enum, CHECK, flujos y preflight; conservar calendario independiente |

**Responsabilidad actual:** esta etapa solo permite preparar y revisar la documentación. **Próxima decisión futura:** autorizar por separado (1) saneamiento manual de reservas (si corresponde), (2) entorno de QA PG17 y respaldo verificable si genera costo/acceso, (3) correcciones de RPC/permisos/clientes si se aprueban, (4) ejecución de la migración real. No agrupar autorizaciones como si fueran una sola.

## 10. Evidencias y enlaces

- Rama base de contratos aprobados por CI: https://github.com/marcelofgx-ctrl/traslados-android/commit/b912768eb38cd2d6963b586fe8a89b2dd517e687
- Prueba de concurrencia y rollback PG16 ficticio: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40058257833
- SQL borrador: qa/quicktrip-capacity/draft_atomic_guard.sql
- Rollback: qa/quicktrip-capacity/rollback_atomic_guard.sql
- Manual PostgreSQL 17, CREATE INDEX: https://www.postgresql.org/docs/17/sql-createindex.html
- Supabase Database Backups: https://supabase.com/docs/guides/platform/backups
- Supabase PostgREST Error Codes: https://supabase.com/docs/guides/api/rest/postgrest-error-codes

**Resultado de esta fase:** plan verificable, sin migración SQL aplicada ni datos productivos modificados. El estado es **NO-GO para ejecutar** hasta resolver todos los gates bloqueantes con aprobación posterior.
