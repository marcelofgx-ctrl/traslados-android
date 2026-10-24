# Quick Trip / Mapa QT — Plan técnico de migración de capacidad atómica
**Fecha de corte:** 24 de octubre de 2026  
**Estado:** SOLO REVISIÓN · NO EJECUTAR EN PRODUCCIÓN · DOCUMENTACIÓN  
**Proyecto Supabase:** Traslados, ref zetaudvvutlouiqxopvg  
**Repositorio:** https://github.com/marcelofgx-ctrl/traslados-android  
**PR de código:** https://github.com/marcelofgx-ctrl/traslados-android/pull/197 (abierto, borrador, no fusionado)  
**Base congelada de este plan:** b912768eb38cd2d6963b586fe8a89b2dd517e687  
**CI de PostgreSQL 16 aprobado:** https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40058257833  
**CI de PostgreSQL 17 aprobado:** https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40059052425 (commit 0c9c0774540ca8752d11e439b22cdae83e9163e1; pruebas ficticias de contratos y RPC representativas)  

> **Restricción estricta:** este archivo es una propuesta de operación FUTURA. No autoriza ni realiza DDL, DML, copias de datos reales, cierres/cancelaciones de reservas, cambios de roles, RPC, web, Android, GPS, CI productivo, fusiones o despliegues. Toda ejecución requiere una aprobación nueva y específica. No subir volcados, PIN, tokens, claves ni teléfonos al repositorio.

## 1. Objetivo y límites

Impedir **más de un servicio operativo real simultáneo** para el único conductor, incluso ante dos RPC o sesiones concurrentes, sin impedir que existan solicitudes pendientes o viajes programados aceptados que todavía no iniciaron recogida. Preservar los motores actuales de Mapa QT, las reservas programadas y la disponibilidad por franjas. El índice no libera capacidad por tiempo transcurrido, ETA ni duración estimada; solo por cambio a estado no operativo.

Quedan **fuera**: GPS privado, presencia pública, pantalla Conductor, PWA, cambios en la autenticación, reescritura de motores o implementación de correcciones de horario. Se describen las dependencias y condiciones bloqueantes, pero no se ejecuta ninguna.

**SQL candidato exacto:** qa/quicktrip-capacity/draft_atomic_guard.sql, revisión V3 del PR #197. **Rollback candidato:** qa/quicktrip-capacity/rollback_atomic_guard.sql. Este documento no crea un archivo de migración dentro de supabase/migrations.

## 2. Inventario observado en Supabase REAL — lecturas únicamente

Consultas al catálogo y tablas el 24/10/2026, **no es una instantánea inmutable**. La verificación de las **03:39 UTC** constató 24 reservas, **0 servicios operativos**, 63 eventos y ausencia del índice propuesto:

| Dato | Observado |
|---|---|
| Proyecto | ACTIVE_HEALTHY, PostgreSQL **17.11** (rama GA 17) |
| Pruebas de QA | PostgreSQL **16 y 17** en contenedores descartables, ambos con CI SUCCESS; **NO** ensayan toda la configuración Supabase real |
| public.reservations | **24** filas; RLS activo |
| Estados reservation_status | PENDIENTE, ACEPTADA, EN_VIAJE, FINALIZADA, CANCELADA, RECHAZADA, PRESUPUESTO_ENVIADO, ACEPTADA_CLIENTE, CONFIRMADA, RECHAZADA_CLIENTE |
| Modos de recogida | now, schedule y valores numéricos de 1 a 120, con restricción CHECK en producción |
| is_demo | falso en las 24 filas observadas |
| Índice qt_one_active_service_uq | **NO existe** en producción |
| Servicios reales que satisfacen el predicado | **2 en la lectura inicial; 0 en la verificación posterior** (ver apartado 2.1) |
| GPS privado en reservation_live_position | 0 posiciones; NO es criterio para instalar la restricción |
| Eventos históricos reservation_events | 59 en la primera lectura; **63** en la lectura posterior del 24/10 a las **03:39 UTC** |
| Triggers en reservations | reservation_schedule_request_policy_v1, reservations_event_insert, reservations_event_update, reservations_fixed_fare_guard_v1 |

**Dos reservas inicialmente abiertas (esta auditoría no las modificó):**

- TR-261023-529FD: ACEPTADA / EN_PUNTO / now.
- TR-261023-9BD22: ACEPTADA / EN_PUNTO / now.

**Resultado al corte inicial:** CREATE UNIQUE INDEX habría fallado porque dos filas reales compartían la clave booleana del índice parcial. **Ese bloqueo concreto dejó de observarse luego de un cambio externo durante la auditoría.** La migración sigue **NO-GO** por el resto de gates. No resolver duplicados cambiando estados, fechas, pickup_mode, is_demo ni eliminando filas. No adjudicar cuál traslado ocurrió realmente: eso requiere evidencia operativa y autorización del usuario.

### 2.1 Cambio concurrente detectado durante esta documentación (solo lectura)

En una consulta posterior del **24/10/2026, alrededor de 03:32 UTC**, se observó:

| Reserva | Estado inicial observado | Estado en última consulta | Eventos persistidos (UTC) |
|---|---|---|---|
| TR-261023-9BD22 | ACEPTADA / EN_PUNTO | **FINALIZADA** | ACEPTADA→EN_VIAJE: 03:31:21; EN_VIAJE→FINALIZADA: 03:31:23 |
| TR-261023-529FD | ACEPTADA / EN_PUNTO | **FINALIZADA** | ACEPTADA→EN_VIAJE: 03:31:27; EN_VIAJE→FINALIZADA: 03:31:29 |

En esa misma consulta hubo **24 reservas y 0 servicios operativos**; el índice continuaba ausente. Esta etapa usó únicamente SELECT y herramientas de lectura: **no cerró, canceló, modificó ni tocó reservas**. No se atribuye la acción a una persona o sistema concreto. Antes de cualquier decisión de producción, confirmar externamente la legitimidad de los cierres y repetir los preflights en ese instante. El cambio histórico no significa autorización para instalar el índice.

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

**Condición estricta para proceder:** se RECOMIENDA cero servicios operativos durante ventana de mantenimiento (técnicamente una fila no contradice unicidad, pero el plan conservador exige cero y actividad detenida). Si el resultado es distinto de cero, DETENER. **Cronología:** en la primera lectura hubo 2; en la segunda hubo **0**, después de que terceros/otro proceso (autor no determinado) registraran cierres alrededor de 03:31 UTC. Nunca modificar reservas como parte del preflight; una nueva lectura siempre prevalece sobre esta cronología.

**P3. Inventario de superficie RPC, triggers, permisos y RLS:** volver a consultar pg_proc / pg_get_functiondef, pg_trigger, pg_policies, pg_indexes, roles y privilegios por columna. Comparar con manifiesto de respaldo. Verificar escritura por rutas conocidas y versiones heredadas. Confirmar ausencia de procedimientos/columnas nuevos entre el corte de auditoría y el despliegue.

**P4. Respaldo VALIDADO (no consta respaldo realizado en esta etapa):** confirmar en Dashboard > Database > Backups la retención y disponibilidad real de respaldos/PITR; tomar o confirmar punto de recuperación adecuado según el plan del proyecto. Además guardar una exportación segura (fuera de GitHub) de esquema, índices, restricciones, triggers, funciones, políticas, GRANT y manifiesto/versiones de migraciones; capturar copia protegida y cifrada de reservas/eventos y otras tablas relacionadas SOLO con autorización para respaldo real. Registrar checksums, responsable y procedimiento de restauración; ensayar restauración en un ambiente SEPARADO. Sin evidencia de restauración posible, NO APLICAR.

**P5. Ensayo representativo — PARCIALMENTE CUMPLIDO:** se ejecutaron contra PostgreSQL **17-alpine** pruebas ficticias de predicado, 10 estados, solicitudes programadas, duplicados, dos conexiones, SQLSTATE **23505**, rollback y cinco RPC **simuladas parcialmente** (workflow #40059052425: SUCCESS). Esto **no** ejecuta las RPC originales ni sus autenticaciones, permisos, disponibilidad y triggers reales. Falta un ensayo de integración con esquema equivalente al real, catálogo de funciones/triggers/RLS/GRANT sin secretos, compatibilidad de errores Android/PWA y restauración verificada. Nunca utilizar PIN, sesiones, tokens ni datos personales de producción sin autorización específica.

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


## 5.1. Hallazgos complementarios verificados por lectura — 24/10/2026

**Base y permisos:** proyecto \`zetaudvvutlouiqxopvg\` ACTIVE_HEALTHY, PostgreSQL 17.11. Hay 24 reservas, 63 eventos tras el cambio histórico ya documentado y cero servicios operativos en la lectura 03:39 UTC. \`qt_one_active_service_uq\` aún NO existe. RLS está habilitado en \`public.reservations\`; la política \`drivers update reservations\` (rol authenticated) comprueba \`is_driver(auth.uid())\` en USING y WITH CHECK. Existe GRANT amplio UPDATE para authenticated y privilegio de columna \`is_demo\`; esto **no significa acceso libre para usuarios anónimos**, ya que RLS también aplica a DML directo, pero sí obliga a revisar a los usuarios que califican como conductor. Los GRANT SQL para anon/authenticated/service_role son amplios y no reemplazan RLS ni controles internos de funciones SECURITY DEFINER.

**RPC concretas:** se verificaron las firmas de \`driver_decide_fixed_pickup_v1\`, \`driver_set_status_v2\`, \`driver_set_pickup_phase_v1\`, \`customer_quote_decision_v10\`, \`customer_quote_decision_v13\`, \`customer_request_pickup_fixed_v1\` y \`customer_request_pickup_v1\`. Todas las examinadas tienen SECURITY DEFINER y EXECUTE efectivo para anon/authenticated, por lo que su seguridad depende de las validaciones internas. \`driver_decide_fixed_pickup_v1\` y \`customer_quote_decision_v13\` poseen advisory lock, FOR UPDATE y chequeo de disponibilidad; \`driver_set_status_v2\` y \`driver_set_pickup_phase_v1\` usan FOR UPDATE, no advisory lock; \`customer_quote_decision_v10\` no usa advisory lock, FOR UPDATE ni availability_route_check_v11_4. El índice evitaría dos ocupaciones simultáneas por cualquiera de ellas, pero **no corrige agenda programada superpuesta** de rutas legacy.

**Clientes existentes (lectura del código en base del PR #197):**
- Android \`conductor/Api.java\` conserva \`ApiException.backendCode\` y respuesta JSON del servidor; \`publicMessage()\` distingue el error horario y fallos de red, pero **no** transforma aún \`23505\` + nombre del índice en texto específico de ocupación.
- PWA \`web-pasajero/rpc-client.js\` conserva solo \`parsed.message\` cuando falla un RPC y **descarta \`parsed.code\`**; \`app.js/showError()\` no distingue el \`23505\` de este índice. Tampoco todas las versiones instaladas de los clientes están auditadas.
- Exigir una comprobación de extremo a extremo: error con \`code=23505\` y constraint \`qt_one_active_service_uq\` → "Conductor ocupado", **sin** atribuirlo a Internet. Cualquier otro 23505 debe retener su diagnóstico propio, y timeout sin confirmación obliga a releer estado antes de reenviar.

**Cobertura real de pruebas:**
- PG16: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40058257833 (success).
- PG17: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40059052425 (success, commit \`0c9c0774540ca8752d11e439b22cdae83e9163e1\`). El test verificó simultaneidad de dos RPC *simuladas* de conductor y cliente legacy, exactamente un commit y una unicidad \`23505\`, las 10 etiquetas de estado, \`schedule\`, retirada/reinstalación del índice y cero pérdidas de filas ficticias.
- **Fuera de cobertura**: ejecución de funciones auténticas con autenticación y disponibilidad, seguridad bajo RLS y GRANT reales, pruebas integradas de las cuatro triggers, mensajes del Samsung/PWA instalada y restauración del respaldo.

## 9.1. Matriz de autorización futura — compuertas independientes

| Compuerta | Evidencia actual | Criterio de aprobación separado |
|---|---|---|
| G1 — Preflight operativo | 24 reservas, **0 operativas** en lectura 03:39 UTC; 2 cambios de cierre históricos con autor no identificado | Nueva consulta idéntica durante mantenimiento, ocupación=0, legitimidad de cierres verificada; no alterar reservas para cumplirla |
| G2 — Respaldo y restauración | **NO VERIFICADO:** las herramientas de lectura disponibles no exponen inventario de backups, PITR, fecha válida ni prueba de restauración | Responsable confirma en Dashboard > Database > Backups existencia/retención, fecha, procedimiento y **restauración probada en ambiente separado**; copia privada y checksums con autorización distinta |
| G3 — RPC y agenda heredada | PG17 replicas PASS; \`customer_quote_decision_v10\` sigue ejecutable y no hace availability_route_check_v11_4 | Inventario de clientes/versiones activas, prueba RPC real en entorno aislado equivalente; decisión explícita sobre compatibilidad v10 sin revocar nada automáticamente |
| G4 — Seguridad | RLS activo; UPDATE amplio para conductor autenticado; \`is_demo\` excluye índice | Prueba de que ningún escritor autorizado puede eludir capacidad cambiando \`is_demo\`, ni modificar indiscriminadamente \`pickup_mode/status\`; revisión del mínimo privilegio |
| G5 — Manejo de error y UX | PostgreSQL emite 23505 y HTTP PostgREST 409 documentado; Android/PWA no distinguen estrictamente este índice | Matriz de códigos y constraint en versiones instaladas, sin regresión; autorización independiente para cambios del cliente si se requieren |
| G6 — Aplicación/rollback | Índice y rollback solo ensayados con fixtures | Ventana de mantenimiento, responsable de recuperación, límites de lock, monitoreo, SQL/commit congelado y autorización expresa para DDL real |
| G7 — Publicación | No autorizada y no necesaria para esta documentación | Aprobación independiente posterior para APK o PWA, nunca implícita por instalar índice |

**Dictamen:** **NO-GO / pendiente de G1–G6**. G1 tiene fotografía favorable pero no decisión definitiva; PG17 sintético aprobado reduce riesgo técnico, sin cerrar G2–G5. El equipo no debe tratar un botón de aprobación documental como autorización para modificar Supabase real.

**Respaldo:** la documentación de Supabase explica respaldos automáticos según plan y PITR opcional, pero **no se conoce el plan de este proyecto ni su inventario de backups**. No inferir que exista copia restaurable a partir del estado ACTIVE_HEALTHY ni del plan de suscripción ChatGPT. Consultar https://supabase.com/docs/guides/platform/backups.

**Errores:** Supabase/PostgREST documenta la violación \`23505\` como HTTP \`409\`, pero el mapeo al nombre exacto del índice y el mensaje del cliente no se han ensayado con las RPC reales. Referencia https://supabase.com/docs/guides/api/rest/postgrest-error-codes.

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
| BLOQUEANTE HASTA REVALIDAR | Dos servicios estuvieron abiertos, luego se marcaron FINALIZADA durante la auditoría | Confirmar legitimidad y trazabilidad de los cierres; volver a verificar ocupación=0 inmediatamente antes de un despliegue futuro, sin modificar reservas |
| BLOQUEANTE | Pruebas PG17 sintéticas aprobadas, **pero NO existe evidencia de recuperación validada ni integración completa** | Validar respaldo + restauración realista y compatibilidad de RPC/triggers/RLS/GRANT sin tocar reservas |
| ALTA | RPC v10 aún habilitada y sin disponibilidad moderna | Auditar llamadas activas y decidir mantener, adaptar o revocar solo en etapa aparte |
| ALTA | Posible bypass is_demo / UPDATE directo de conductor | Revisar GRANT, RLS, columnas y rutas que escriben is_demo; demostrar que no se elude |
| ALTA | UI atribuye 23505 a red u otro fallo | Pruebas de mapeo estricto por índice y reconciliación de respuesta |
| MEDIA | Escrituras concurrentes durante CREATE INDEX normal | Ventana de mantenimiento, lock/statement timeouts y abortar si no hay exclusividad |
| MEDIA | PG17 de CI no equivale a Supabase 17.11 con funciones/triggers y configuración real | Validar esquema y funciones equivalentes en PostgreSQL 17 separado, manteniendo QA aislado |
| MEDIA | Estados enum nuevos, agenda programada o heredados | Reauditar enum, CHECK, flujos y preflight; conservar calendario independiente |

**Responsabilidad actual:** esta etapa solo permite preparar y revisar la documentación. **Próxima decisión futura:** autorizar por separado (1) saneamiento manual de reservas (si corresponde), (2) entorno de QA PG17 y respaldo verificable si genera costo/acceso, (3) correcciones de RPC/permisos/clientes si se aprueban, (4) ejecución de la migración real. No agrupar autorizaciones como si fueran una sola.

## 10. Evidencias y enlaces
- CI PostgreSQL 17 aislado: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40059052425 (SUCCESS)
- Rama QA PG17: https://github.com/marcelofgx-ctrl/traslados-android/tree/qa/quicktrip-capacidad-pg17-20261024


- Rama base de contratos aprobados por CI: https://github.com/marcelofgx-ctrl/traslados-android/commit/b912768eb38cd2d6963b586fe8a89b2dd517e687
- Prueba de concurrencia y rollback PG16 ficticio: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40058257833
- SQL borrador: qa/quicktrip-capacity/draft_atomic_guard.sql
- Rollback: qa/quicktrip-capacity/rollback_atomic_guard.sql
- Manual PostgreSQL 17, CREATE INDEX: https://www.postgresql.org/docs/17/sql-createindex.html
- Supabase Database Backups: https://supabase.com/docs/guides/platform/backups
- Supabase PostgREST Error Codes: https://supabase.com/docs/guides/api/rest/postgrest-error-codes

**Resultado de esta fase:** plan verificado y actualizado con PG17 exitoso, sin migración SQL aplicada ni datos productivos modificados POR ESTE TRABAJO. Las reservas cambiadas externamente conservan autor **no determinado**. El estado sigue **NO-GO para ejecutar**, ahora por ausencia de evidencia de respaldo restaurable, compatibilidad RPC/cliente y seguridad del campo `is_demo`, además del ensayo integral del esquema y la aprobación específica. Cero operativos es condición necesaria del preflight conservador, nunca autorización suficiente.
