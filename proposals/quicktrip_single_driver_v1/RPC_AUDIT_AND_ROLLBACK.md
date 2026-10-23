# Auditoría de vías RPC y activación futura (sin ejecutar)

## Alcance
Proyecto Supabase Traslados; las consultas de auditoría fueron **SELECT** sin cambios. El objetivo actual es **UN SOLO conductor**. La regla SQL se añade como índice de unicidad transaccional en `reservations`, no como validación que dependa de una pantalla Android.

## Vías que podrían reclamar o liberar la capacidad
| Endpoint | Acción real | Protección requerida |
|---|---|---|
| `driver_decide_fixed_pickup_v1` | PENDIENTE → ACEPTADA cuando el conductor acepta recogida directa; usa advisory lock y disponibilidad horaria | **Índice SQL** también evita una segunda ACEPTADA horas después |
| `driver_set_status_v2` | Permite PENDIENTE → ACEPTADA (y ACEPTADA → EN_VIAJE → FINALIZADA); autentica PIN | **Índice SQL** cubre la transición directa y el comienzo de un viaje programado |
| `customer_quote_decision_v13` | Pasajero acepta/rechaza presupuesto; ACEPTADA para `pickup_mode` now o schedule | **Índice SQL** impide otra inmediata pero permite futuras programadas, salvo recogida activa |
| `customer_quote_decision_v10` | Implementación anterior con estados ACEPTADA; revisar si sigue expuesta | Índice la protege si actualiza reservas; auditar grants/uso antes de desplegar |
| `customer_quote_decision_v11_3/v11_4` | Flujos heredados de presupuesto y agenda | Verificar delegación, permisos actuales y estados; la regla SQL cubre todos los writes |
| `driver_set_pickup_phase_v1` | ACEPTADA → EN_CAMINO → EN_PUNTO, sin cambiar status | Al activar EN_CAMINO un programado se ocupa el turno; el índice impide que se solape físicamente |
| `driver_send_quote_v10/v10_7` | Envía presupuestos; NO equivale a aceptación ni inicia viaje | Sin cambio de motor; no bloquear envío por la regla de operativa |
| `customer_create_reservation_v12` y legados | Crea PENDIENTE | Deben poder crear solicitudes aunque conductor ocupado, sin prometer disponibilidad ni aceptarlas automáticamente |
| `customer_cancel_reservation`, `cancel_reservation_by_token` | Cancelaciones autorizadas | Los estados terminales liberan capacidad; preservar historiales |
| `mapa_reservation_position_v1` | Publica GPS solo si encuentra una reserva activa única | Mantener v1 sin modificaciones hasta validar v2 por UUID |
| `customer_reservation_position_v1` | Expone último GPS autorizado al dueño de la reserva | Seguir comprobando token/sesión, consent, antigüedad y precisión |
| `public_driver_availability_v1` / `availability_route_check_v11_4` | Consulta estado y horario estimado | En etapa posterior mostrar OCUPADO por servicio abierto, sin mezclar estimación con cierre |
| `guard_fixed_fare_reservation_v1` | Procedimiento existente que examina tarifa fija | Revisar su invocación/trigger antes de activar en producción |

**Más fuerte que envolver tres RPC:** un único índice parcial se evalúa por PostgreSQL en cualquier `INSERT/UPDATE` que pretenda ocupar la capacidad, incluso si llega por una función antigua, REST o una transacción concurrente. Las comprobaciones horarias existentes siguen siendo útiles para agenda y reposicionamiento, pero no sustituyen el estado real del servicio.

## Predicado propuesto
Ocupa el único turno si `status='EN_VIAJE'`, o si `status='ACEPTADA'` y (`pickup_mode='now'` o `pickup_phase IN('EN_CAMINO','EN_PUNTO')`). Incluye programados efectivamente iniciados y permite programados futuros confirmados sin conducción activa. No usa reloj ni duración estimada.

## Datos reales bloqueantes
El control de solo lectura confirmó dos viajes con `pickup_mode='now'`, `status='ACEPTADA'` y `pickup_phase='EN_PUNTO'`: `TR-261023-529FD` y `TR-261023-9BD22`. Por ello **la propuesta debe fallar deliberadamente** si se intenta ejecutar hoy. No corregirlos automáticamente.

## Secuencia segura para una autorización FUTURA
1. Exportar esquema, definiciones RPC y permisos vigentes; identificar quién puede ejecutar cada transición (en especial procedimientos SECURITY DEFINER heredados).
2. Resolver explícitamente y documentar el estado final de las dos reservas sin cerrar **solo tras autorización del propietario**. No eliminar ni retrofechar.
3. Ejecutar `01_preflight_READ_ONLY.sql` y confirmar que hay 0 o 1 servicio operativo.
4. Bajo nueva autorización, ejecutar íntegramente `02_guard_PROPOSAL_NOT_EXECUTE.sql` en transacción con `lock_timeout`. Nunca usar `IF NOT EXISTS` que esconda una definición previa distinta.
5. Verificar índices, intentos concurrentes, cancelaciones y viajes programados en staging; auditar error SQLSTATE 23505, nombre del índice y mensaje amable `CONDUCTOR_OCUPADO`. **No exponer detalles internos del SQL al pasajero**.
6. Probar flujo Android/web con datos ficticios y dispositivo real tras instalar la versión específicamente aprobada. Durante una migración, mantener una ventana controlada de escrituras; no confiar en guardas de UI.
7. Guardar respaldo y estado Git para restaurar exacto.

## Rollback separado
`03_rollback_PROPOSAL_NOT_EXECUTE.sql` elimina **solo el índice**. No reescribe reservas ni elimina historia, pero al quitar el índice vuelve a permitir dos aceptaciones: debe acompañarse con deshabilitación controlada de aceptación en interfaz/RPC mientras se investiga. No hacer rollback automático por errores de usuario.

## Límites / nuevas autorizaciones pendientes
- Proyecto actual: **un** conductor. Si se suman conductores, el índice global no sirve; deberá existir una clave de conductor inmutable, siempre presente y validada (hoy `driver_id` puede ser NULL). No cambiar ese modelo ahora.
- Los RPC SECURITY DEFINER requieren control explícito de permisos: no crear funciones expuestas nuevas para saltar RLS. No cambiar grants en producción sin auditoría.
- Probar el mapeo de `23505` a un motivo entendible antes de instalar el índice; no reemplazarlo por “revisá la conexión”.


## Auditoría de permisos (solo lectura; 23/10/2026)
`has_function_privilege` confirma que tanto `anon` como `authenticated` pueden invocar las versiones actuales `driver_decide_fixed_pickup_v1`, `driver_set_status_v2`, `driver_set_pickup_phase_v1`, `customer_quote_decision_v13`, `mapa_reservation_position_v1` y `customer_reservation_position_v1`. Todas figuran como `SECURITY DEFINER`; también `driver_pin_valid` está expuesta a EXECUTE. **Esto no demuestra por sí solo acceso no autorizado:** las funciones verifican PIN, dispositivo o sesión internamente. Sí exige revisión de grants y protección contra uso abusivo antes de incorporar nuevos endpoints. No revocar permisos de producción sin auditar la PWA y el APK existentes, porque podría interrumpirse el servicio.

Un RPC v2 nuevo debe preferir privilegios mínimos, revocar ejecución a `PUBLIC` y entregar solo los permisos estrictamente necesarios. La firma de la API actual no se modifica en esta etapa.
