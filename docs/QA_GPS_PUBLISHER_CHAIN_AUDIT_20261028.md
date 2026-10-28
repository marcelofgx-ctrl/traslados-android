# QA — Depuración de seguimiento Quick Trip (28/10/2026)

**Alcance:** limpieza no destructiva para producción; esta rama nunca se fusiona o despliega sin revisión.  
**Base inmutable/rollback:** `bdcc0925021c6888f966e849fa029a62d1fdfcf0`.  
**Web canónica:** `web-pasajero/`. **Única APK:** `mapatrayectos/` con Conductor integrado.

## Limpieza aislada
Se retira únicamente el árbol `seguimiento-review/` (36 archivos históricos, ~2,8 MB de copias antiguas de la web, incluido un enlace a una APK Cliente independiente). No está dentro de la raíz del proyecto Vercel Quick Trip QA (`web-pasajero/`) y no es la pantalla de seguimiento vigente. Los archivos permanecen recuperables en Git a través del SHA base. No se borra `web-pasajero/pruebas/map-first/`: las validaciones `check-web-pasajero-review.yml` y `deploy-web-pasajero-pwa.yml` aún lo ejecutan. Tampoco se tocan los artefactos APK históricos, porque todavía hay referencias en documentación, configuración y pruebas.

## Brecha funcional P0 por investigar — NO reparada mediante esta limpieza
* La web de pasajeros `web-pasajero/app.js` usa el Supabase operativo `zetaudvvutlouiqxopvg.supabase.co`; incluso el Preview QA apunta a ese backend. Un CI con RPC interceptadas no es una prueba de conectividad real.
* La recogida **antes** de aceptar usa `public_driver_availability_v1` y `TrasladosETA.pickup`. El estado público combina consentimiento, jornada, actividad, edad GPS y agenda; el frontend no recibe coordenadas privadas.
* El seguimiento **de una reserva aceptada** usa la RPC privada `customer_reservation_position_v1` (ID de reserva + sesión o token invitado), y solo muestra el vehículo con `available=true`, coordenadas válidas y `gpsAgeSec <=75`.
* La APK Mapa publica `mapa_presence_ping_v1` desde `mapatrayectos/Api.java`, **solo** si `pickup_presence.enabled` fue habilitado (valor predeterminado `false`), con ubicación GPS fresca. Ese paquete no muestra una llamada directa a `mapa_reservation_position_v1`.
* El módulo `conductor/Api.java` actualiza estados/fases pero no contiene una llamada directa a `mapa_reservation_position_v1`; la telemetría de `TripTelemetryService` se guarda localmente. La documentación de arquitectura menciona el RPC privado de publicación, pero en el código revisado no se identifica el puente que lo invoque. **Esto es una brecha de contrato a verificar, no prueba de que el backend esté incorrecto.** Es posible que el backend derive posiciones desde otro flujo; no se inspeccionó la base real.
* **Diagnóstico recomendado (solo lectura, autorización separada para datos reales):** 1) verificar el origen de los datos Mapa instalados (versión, consentimiento, jornada, permisos GPS, última publicación, sin secretos); 2) comprobar si hay una posición por reserva publicada y su frescura, autenticación del pasajero y `reason` del RPC; 3) contrastar los contratos SQL de `marcelofgx-ctrl/traslados-web` con las llamadas Android. Recién entonces decidir si corresponde reparar publisher Android, backend o sesión. Nunca inventar coordenadas ni forzar `available=true`.

## Contratos preservados
No se tocaron `web-pasajero/app.js`, `reservation-live.js`, `quicktrip-map-focus.js`, `mapatrayectos/`, `conductor/`, Supabase, `main`, Vercel ni reservas. No se ofrece APK Conductor o Pasajero independiente. No borrar migraciones, reservas, registros GPS ni historial de usuario. No compilar APK.
