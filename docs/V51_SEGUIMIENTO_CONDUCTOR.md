# Traslados v51 — Conductor en camino / llegó (11/10/2026)

## Implementación
Se agregaron estados operativos de recogida **independientes del ciclo de facturación o viaje**:
- SIN_INICIAR (predeterminado): traslado aceptado, sin salida comunicada.
- EN_CAMINO: Conductor tocó manualmente «Voy en camino» y Supabase validó PIN, reserva existente y estado ACEPTADA.
- EN_PUNTO: Conductor tocó manualmente «Llegué al origen», Supabase exigió transición desde EN_CAMINO.
- EN_VIAJE y FINALIZADA: continúan gestionados por `driver_set_status_v2`; la fase de recogida no altera esos estados.

Conductor nativo v11.5-R3 (versionCode 125) muestra acciones contextuales dentro de reservas ACEPTADAS. Después de llamar a `driver_set_pickup_phase_v1`, refresca la lista; en fallo no comunica éxito. API de Conductor usa `driver_list_reservations_v3` y conserva todas las propiedades previas del JSON. El monitor original de nuevas solicitudes sigue sin cambios de frecuencia ni canales.

La PWA Cliente v51 y Cliente Premium v15 WebView reciben `pickup_phase` y `pickup_phase_updated_at` desde `customer_list_reservations_v13` o `get_reservation_by_token_v2`. El chip «En camino» usa un pulso visual mínimo, respetando `prefers-reduced-motion`, **solo ante un estado confirmado por servidor**. El popup en primer plano avisa de estos cambios mientras la PWA está visible; no hay push en segundo plano aún.

## Seguridad y privacidad
- No inferimos «En camino» a partir del GPS, presencia, conducción estática ni ETA.
- RPC de cambio protegida por PIN del conductor. RPC de consulta del cliente hereda autorización de sesión o token aleatorio del invitado; no entrega latitud/longitud del conductor.
- Cada reserva exige aceptación previa y transición secuencial. No se cambia la lógica de agenda, presupuesto ni ubicación Mapa.
- Se mantienen `customer_list_reservations_v12`, `driver_list_reservations_v2` y las RPC anteriores por compatibilidad con APK antiguas.
- Migración `traslados-web/supabase/migrations/20261011_driver_pickup_phase_v1.sql` aplicada con éxito.

## CI y publicación
- Fuente Android versionada en `conductor/`; workflows de DEBUG y firma RELEASE v11.5-R3 con el mismo certificado histórico. El workflow DEBUG corre en pull requests para proteger la versión instalada.
- PWA con JS/CSS/SW v51, test `trip-phase-v51-smoke.cjs` y regresiones.
- No declarar APK firmada/distribuida sin ver APKSigner, build y certificado; no declarar Pages publicada sin SUCCESS.
- QA manual: en teléfono Conductor instalado con R3, reserva previamente aceptada, tocar «Voy en camino», observar chip en Cliente, tocar «Llegué», observar nuevo estado, luego iniciar y finalizar viaje normalmente; ensayar en segundo plano y con baja conectividad. Sin reservas ficticias en producción.
- Falta configurar infraestructura VAPID + suscripción opt-in y backend de envío para push de segundo plano; las vibraciones de Cliente en foreground ya están en v50.
