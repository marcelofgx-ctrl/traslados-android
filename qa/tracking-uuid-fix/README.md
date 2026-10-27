# Quick Trip · Fix seguimiento GPS por reserva · 2026-10-27

Causa reproducida en PostgreSQL: `min(uuid)` no existe; la función `mapa_reservation_position_v1` ejecutaba `select count(*),min(id)`, lanzando SQLSTATE 42883. El puente `mapa_presence_ping_v1` captura la excepción y no interrumpe el heartbeat público, pero queda vacía `reservation_live_position`.

Corrección de una sola expresión: `min(id)` -> `(array_agg(id))[1]`. El conjunto tiene hasta 2 filas y el resultado solo se utiliza cuando hay exactamente una reserva activa. Verificación read-only: `array_agg(uuid)` devuelve un valor para el viaje activo.

**Límites y privacidad:** Sin cambios en la API del pasajero, GPS público, validación del dispositivo, reserva activa única, frescura, precisión, cancelación, RLS, token, Cloudflare, Web ni APK. Los datos GPS continúan restringidos a reserva autorizada y se ocultan por caducidad.

**Rollback:** volver a aplicar `mapa_reservation_position_v1.rollback.sql` si la regresión de publicación falla. Antes de restaurar, comprobar que el código de producción sigue siendo exactamente la versión correspondiente al parche; nunca sobrescribir otras intervenciones.

**No probado todavía:** mapa del conductor moviéndose en el Samsung del pasajero. No declarar cerrado sin prueba física de extremo a extremo.
