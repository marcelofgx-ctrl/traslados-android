# PWA v48 — Intermitencia de presencia: presentación honesta y diagnóstico (11/10/2026)

## Captura Samsung y comparación con Supabase
- Captura ~01:52 America/Montevideo: PWA en «Ahora» indicaba «Conductor no disponible ahora», acompañado de «Disponibilidad verificada hace 0 s», aunque Mapa estaba con jornada iniciada.
- Consulta read-only a Supabase a las **01:54:26**: `enabled=true`, `shift_active=true`, `shift_paused=false`, `trip_active=false`, `manually_busy=false`, ubicación vigente, precisión 9,94 m, heartbeat edad 24,1 s, GPS edad 26,1 s, sin conflicto agenda. `public_driver_availability_v1()` devolvió **`available=true`** y `motionHint=stationary`.
- Segunda consulta read-only **01:56:00**: jornada activa, posición reciente (18 s) y heartbeat 17 s, resultado **`available_for_requests`**.
- **Importante:** no hay un registro histórico de cada pulso/validez GPS a las 01:52, por lo tanto NO se puede adjudicar con certeza el episodio a GPS, red, agenda u otra razón exacta.
- El procedimiento SQL actual `public_driver_availability_v1` **no exige movimiento reciente** como condición de disponibilidad. La edad de movimiento solo decide el hint de frecuencia; el conductor puede estar detenido y disponible.

## Hallazgos de código
1. La PWA interpreta `checkedAt` (marca temporal de la consulta al backend) como «Disponibilidad verificada hace X s». Eso puede confundirse con «GPS comprobado» cuando el servidor ha respondido `available=false`.
2. La disponibilidad real requiere: opt-in, jornada activa/no pausada, sin viaje/ocupado manualmente, heartbeat y GPS ≤90 s, precisión GPS ≤45 m, y agenda libre. Estado de jornada iniciada **por sí solo no garantiza** todas las condiciones.
3. `TrackingService.publishPickupPresence()` manda última ubicación aceptada si su edad es ≤90 s; de lo contrario el ping puede circular con lat/lng 0. La RPC `mapa_presence_ping_v1` borra ubicación comercial en ese ping si no es válida. Esto es una **posible causa** de fluctuación, no un diagnóstico demostrado de la captura.
4. Si la consulta de estado devuelve disponible pero Edge `pickup-eta` responde `not_available` en un instante posterior, se puede crear una diferencia transitoria entre dos comprobaciones. Eso puede pasar con cambios de frescura/estado entre peticiones.

## Corrección PWA v48
- Mostrar «Estado consultado hace X s · Actualización automática cada 15 s» en lugar de «Disponibilidad verificada hace 0 s». `checkedAt` NO es `gps_recorded_at`.
- Ante lectura negativa, mostrar neutralmente «Recogida no confirmada por ahora» y «Estamos comprobando si el conductor puede recibirte. El estado puede variar por GPS, conexión o agenda; reintentaremos automáticamente». Nunca fingir disponibilidad ni pedir otra ruta con GPS inválido.
- Si Edge ETA responde `not_available`/`occupied` después de una lectura de presencia positiva, no afirmar «Conductor desconectado». Invalidar la caché local y mostrar un estado neutral de revalidación para la próxima consulta. Mantener el backoff/cuotas existentes.
- Conservar 15 s presencia, 25/45 s ETA, roles y privacidad; no modificar los predicados/TTL de seguridad en Supabase ni el software de Mapa R24.8 en este ajuste.
- Web `app.js?v=48`, CSS `v48` (sin cambio visual de reglas), SW `traslados-cliente-pwa-v48`; APK Cliente Premium v15 consume la misma WebView.

## Siguiente trabajo para solucionar la causa, sin inventarla
- Implementar **observabilidad privada y acotada**, sin coordenadas, PIN, teléfonos ni tokens: clase de estado (edad aproximada del heartbeat, edad aproximada de GPS, validez de precisión, jornada/viaje/pausa, agenda), y código de rechazo de la RPC.
- Consultar esos motivos únicamente desde Mapa autenticado (no en la PWA pública) y registrar transiciones de forma temporal para correlacionar exactamente una caída visual con el pulso del conductor.
- Si se confirma agotamiento de GPS en segundo plano, investigar permisos de ubicación precisa/«permitir siempre», optimización de batería Samsung y revalidación explícita de ubicación en TrackingService. No fabricar ni prolongar artificialmente coordenadas caducadas. Si el problema es red o agenda, resolver esa causa específica.
- Validar con pruebas móviles Samsung de 15+ min estacionado, otros 15+ min en movimiento y fondo, con el pasajero refrescando «Ahora». No declarar E2E físico hasta hacerlo.

## Validaciones
- Tests PWA `public-driver-presence-smoke.cjs`, `presence-honest-v48-smoke.cjs` y regresiones; CI/Pages sujetos a comprobación de GitHub Actions.
