# Limpieza del historial Mapa Trayectos — 11/10/2026

## Autorización
El titular aclaró expresamente que **todos los recorridos, incluso los que registraban importes**, pertenecían a la fase de pruebas y podían eliminarse. Esta operación afecta el historial **Mapa Trayectos** del proyecto Supabase **Traslados** (`zetaudvvutlouiqxopvg`). No afecta a Oak & Ember.

## Alcance ejecutado
- Se respaldaron `mapa_trayectos_trips`, `mapa_trayectos_shifts` y `mapa_trayectos_points` en el esquema privado existente `traslados_backup_20261011`.
- Se borraron las filas operativas de esas tres tablas mediante una transacción PostgreSQL atómica con bloqueo, igualdad de conteos del backup y verificación de vaciado.
- Se dejaron intactos `mapa_trayectos_devices`, `driver_live_presence`, `driver_access`, usuarios, tarifas, ajustes, funciones RPC, agenda, proyectos y repositorios.
- Se conservaron los datos de prueba respaldados dentro de la misma instancia para reversión controlada; **este respaldo interno no reemplaza un backup externo**.
- Se revocó el acceso al respaldo desde `PUBLIC`, `anon` y `authenticated`; las tres tablas tienen RLS habilitada.

## Precaución para registros activos
La primera transacción se **abortó sin cambios** cuando Mapa sincronizó una jornada cerrada de aproximadamente 20:05–20:35 UYT. Se comprobó que no había puntos GPS nuevos asociados a jornadas abiertas y que las 4 jornadas abiertas antiguas no tenían actualizaciones recientes. Una segunda transacción, con comprobación estricta para detectar jornada abierta activa, terminó correctamente.

## Conteos confirmados tras reinicio (20:36 UYT)
| Datos | Respaldados | Operativos |
|---|---:|---:|
| `mapa_trayectos_trips` | **115** | **0** |
| `mapa_trayectos_shifts` | **40** | **0** |
| `mapa_trayectos_points` | **70.301** | **0** |
| `mapa_trayectos_devices` | no modificados | **13** |
| `reservations` | respaldadas previamente | **0** |
| `customers` | no modificados | **2** |

El conteo previo de GPS (69.497 puntos) y jornadas (39) creció durante la auditoría por sincronización de una jornada cerrada; la copia final contiene todos los registros detectados al momento de la transacción.

**Límite de garantía:** los conteos son una comprobación temporal. Si alguna APK conserva una cola local sin sincronizar, puede volver a insertar datos de prueba después del reinicio; verificar la lógica de reenvío y la cola local antes de considerar definitiva la limpieza en el dispositivo. No se reinició almacenamiento local de teléfonos ni se modificaron las APK.

## Comprobación funcional posterior
- Iniciar jornada en Mapa y generar un recorrido de prueba voluntariamente; confirmar que aparecen nuevas filas **solo** a partir de ese evento.
- Verificar que la publicación de ubicación y el motor de disponibilidad de Traslados Cliente siguen funcionando; son módulos independientes del historial histórico.
- No restaurar el respaldo mediante inserción sin planificar FK, integridad, eventos y cualquier nueva información introducida tras el reinicio.
- Revisar visualización local de histórico en APK: si queda contenido antiguo, puede venir de almacenamiento en el celular y no de Supabase.

## Documentación vinculada
- `docs/OPERACION_REINICIO_RESERVAS_2026-10-11.md`: respaldo de 31 reservas/75 eventos y posterior limpieza de reservas.
- `docs/CONTEXTO_MAESTRO_PROYECTOS.md`: estado maestro actualizado.
