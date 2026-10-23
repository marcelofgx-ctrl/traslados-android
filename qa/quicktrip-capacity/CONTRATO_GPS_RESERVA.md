# Contrato de seguimiento GPS por reserva (propuesta, NO implementado)

## Identidades y autorización
- Dispositivo Mapa QT: `device_id` UUID + secreto en almacenamiento seguro; autorización vigente de `driver_live_presence` y consentimiento expreso de compartir posición.
- Reserva: UUID no derivado de texto ni nombre; el pasajero consulta exclusivamente su reserva por sesión autenticada o token público de alta entropía.
- Una sola reserva inmediata operativa. El servidor NO infiere reserva desde un conjunto ambiguo; si hay 0 o >1 devuelve motivo seguro, sin coordenadas.

## Publicación propuesta
`TrackingService` existente -> GPS fresco -> `mapa_presence_ping_v1` (consentimiento/shift) -> `mapa_reservation_position_v1` -> `reservation_live_position`.
No inventar puntos ni iniciar un segundo motor GPS. Evaluar cómo `mapa_presence_ping_v1` hoy marca `loc_ok=false` si `p_trip_active=true`: es una incompatibilidad importante con seguimiento EN_VIAJE y requiere diseño de separación entre presencia pública y posición privada por reserva.

## Consulta de pasajero
`customer_reservation_position_v1(reservation_id,session_token,public_token)` responde:
- `{available:true,phase:'EN_CAMINO'|'EN_PUNTO'|'EN_VIAJE',driverLat,driverLng,gpsAgeSec,accuracyM}` si GPS <=75 s, precisión <=45 m, consentimiento activo, jornada activa y dispositivo correcto.
- `{available:false,reason:'unauthorized'|'not_active'|'preparing_pickup'|'position_unavailable',phase?}` en los demás casos. Nunca mostrar una coordenada anterior como posición actual.
- Pasajero ve posición y ruta CONDUCTOR->RECOGIDA en EN_CAMINO; al llegar EN_PUNTO; luego CONDUCTOR->DESTINO en EN_VIAJE. Ruta estimada origen-destino no es telemetría.
- Polling controlado, pausa al ocultar pestaña, control de caducidad, privacidad y cuotas; notificación de cambio de fase independiente del GPS.

## Criterios QA (ficticios)
- Token de otra reserva => unauthorized; GPS 76 s => position_unavailable; precisión 46 m => position_unavailable.
- Consentimiento revocado => position_unavailable y no publicar; turno pausado => no publicar.
- Dos reservas activas => no_unique_active_reservation y cero coordenadas; una => solo la autorizada.
- La app cerrada/reabierta mantiene seguimiento según permisos y servicio foreground existente; probar en Samsung antes de producción.
- Aceptar, rechazar, iniciar, finalizar son operaciones separadas, idempotentes, con control de concurrencia y estados auditables.

## Requisitos de despliegue futuro
Respaldo de definiciones y permisos RPC, transacción de migración, test de concurrencia real con dos conexiones en PostgreSQL local, prueba de rollback y verificación de GRANT/REVOKE y SECURITY DEFINER; luego aprobación expresa del usuario. Ningún cambio a datos reales en esta etapa.
