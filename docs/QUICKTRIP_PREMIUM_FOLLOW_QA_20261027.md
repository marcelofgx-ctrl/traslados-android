# Quick Trip · seguimiento profesional RC1 QA · 27/10/2026
Base: main 53dd427d5bda65e84f585b4db2c565227aff673c.

## Aprobado para QA
- Cuando una solicitud pendiente pasa a aceptada, la Web/PWA abre automáticamente la vista de seguimiento una única vez por sesión y reserva, siempre que el pasajero esté en pantalla y autorizado.
- Si la PWA se reabre con una reserva reciente (menos de 90 minutos), puede recuperar el seguimiento sin abrir reservas antiguas.
- Origen y destino arriba; mapa vial con textura visual y altura 55% de la pantalla (máximo 560px), ETA y kilómetros en tarjetas grandes.
- El GPS privado se consulta cada 4 segundos únicamente con la vista visible, conexión y comprobante autorizado; la cadencia real de transmisión del Android puede ser menor. Se muestran los segundos reales del último GPS. Nunca se predice posición ni se dibuja vehículo con GPS vencido.
- El marcador se interpola solo desde una ubicación verificada anterior hacia la nueva, si la distancia de pantalla es plausible; no adelanta ni inventa ubicación.
- Consulta de estados de solicitud cada 4 segundos al esperar una aceptación, aunque el pasajero permanezca en Tu Viaje.
- No cambia rutas, geocodificación, motor de reservas, Supabase, Cloudflare, APK estable, producción ni Web pública.

## No declarar terminado
- Solo CI con simulación de red controlada y screenshots de Chrome; hace falta verificar en un segundo teléfono un viaje real aceptado, GPS en movimiento, pérdida de señal, cierre de solicitud y el regreso manual.
- Sin prueba física no publicar en producción ni fusionar.
