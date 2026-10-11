# Operación de respaldo y reinicio de reservas — 11/10/2026

## Autorización y alcance
El titular autorizó explícitamente eliminar las reservas de producción del proyecto **Traslados** tras preguntar por reiniciar la base. **NO** se reinició la base completa; se conservaron usuarios, configuración, funciones SQL, mapa y aplicaciones. No se afectó la base independiente **Oak & Ember**.

## Operación completada, 20:30 UYT
Proyecto Supabase: `zetaudvvutlouiqxopvg`.

Previo al borrado, en una **misma transacción** con bloqueo explícito de las cuatro tablas, se creó el esquema aislado `traslados_backup_20261011` y se copiaron íntegramente los siguientes registros:

| Tabla | Respaldados | Quedan operativos |
|---|---:|---:|
| `reservations` | 31 | 0 |
| `reservation_events` | 75 | 0 |
| `reservation_chat_messages` | 0 | 0 |
| `reservation_stops` | 0 | 0 |

La transacción comprobó la igualdad de conteos antes de borrar y verificó tablas operativas vacías después de borrar. El `DELETE FROM public.reservations` desencadenó exclusivamente las tres cascadas FK `ON DELETE CASCADE` verificadas (eventos, mensajes y paradas). Se hizo `COMMIT` solo tras superar todas las verificaciones.

**Privacidad:** el esquema de respaldo no está expuesto en Data API; `USAGE` fue denegado a `PUBLIC`, `anon` y `authenticated`; tablas sin privilegios públicos y con RLS habilitado. No se subieron datos de reservas, teléfonos, tokens, identificadores ni ubicaciones a GitHub.

Se conservaron y verificaron:
- **2** cuentas en `public.customers`.
- **115** viajes en `public.mapa_trayectos_trips`.
- Esquemas/funciones/roles y configuración de negocio sin cambios; la agenda efectiva seguía con atención **08:00–22:00 UYT**. No se borraron logs ni tokens de clientes.
- El proyecto Supabase Oak & Ember fue excluido.

## Verificación real tras limpieza
Consulta de solo lectura aproximadamente **20:30 UYT, 11/10/2026**:
- `public.reservations`, `reservation_events`, `reservation_chat_messages` y `reservation_stops`: **0** filas.
- `availability_reason('2026-10-11','21:19',29)`: `AVAILABLE`; a las 21:40: `AVAILABLE`.
- `availability_reason('2026-10-11','23:20',29)`: `OUTSIDE_SCHEDULE` por cierre 22:00 (sin relación con reservas anteriores).
- `availability_reason('2026-10-12','10:00',29)`: `AVAILABLE`.
- `public_driver_availability_v1()`: `available=true` en el instante de la lectura. No garantiza una ETA o disponibilidad futura: depende de GPS, jornada y otras comprobaciones en tiempo real.

**Importante:** estas consultas verifican agenda simplificada y presencia, pero no sustituyen `availability_route_check_v11_4` con coordenadas verificadas, ni una reserva de prueba completa; la creación de reservas NO se ejecutó para evitar introducir datos no autorizados.

## Recuperación
El esquema privado `traslados_backup_20261011` contiene una fotografía pre-limpieza. **No** restaurar mediante `INSERT ... SELECT *` apresuradamente: la tabla de reservas tiene triggers de auditoría y pueden generarse eventos duplicados. Ante una recuperación solicitada, documentar primero las reservas nuevas posteriores a la limpieza, validar integridad/FK, y realizar una transacción de restauración supervisada compatible con triggers. Conservar copia externa si se requiere protección ante pérdida completa del proyecto; esta copia dentro de la misma instancia es un respaldo de operación, no un backup ante pérdida del servidor.

## Prueba pendiente en Samsung
Actualizar `Mis traslados` para eliminar la vista cacheada; al estar vacías las tablas deben verse 0 reservas. Enviar voluntariamente una nueva solicitud a una hora dentro de 08:00–22:00 y **al menos 30 minutos por delante**, sujeto a buffers de ruta y validación final. Verificar interacción con presupuesto, Conductor y Cliente WebView v15. Evitar 23:20 a menos que se modifique explícitamente la configuración de horario. Mapa R24.8 y PWA v55 no fueron modificadas.
