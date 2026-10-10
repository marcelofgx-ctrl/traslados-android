# Mapa → disponibilidad → recogida del pasajero (10/10/2026)

**Web oficial**: https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ (PWA v25).
**APK Cliente Android**: Cliente Premium v14, WebView que abre exactamente esa PWA.
**APK Mapa Trayectos**: R24.6 versionCode 48; opción «Disponibilidad de recogida» en el menú con punto verde/rojo. Verde indica consentimiento habilitado y no «ocupado manual», **no garantiza** que el conductor pueda recoger; rojo indica apagado u ocupado manual.

## Condiciones reales de disponibilidad

La RPC `public.public_driver_availability_v1` lee exclusivamente el heartbeat privado enviado desde Mapa, con consentimiento:
- opt-in explícito con PIN y conductor no marcado ocupado;
- jornada activa, no pausada, sin viaje en Mapa activo;
- última comunicación y GPS con menos de 90 segundos, precisión <=45 metros;
- conducción registrada en los últimos 5 minutos;
- agenda Supabase sin conflictos durante los próximos ~60 minutos, considerando reservas aceptadas/en viaje, retención de pendientes y bloqueos.
**No detecta automáticamente viajes externos Uber/Cabify**: marcar manualmente ocupado en Mapa cuando corresponda. Un conductor parado más de cinco minutos puede aparecer como no disponible aun teniendo jornada abierta; es la política de conducción reciente, no GPS en vivo continuo.

Se envían heartbeats desde Mapa aproximadamente cada 25 s con jornada activa. La PWA refresca en primer plano cada 40 s y usa cache por 25 s para disponibilidad y por 65 s para ETA, siempre con caducidad/seguridad del backend. «Tiempo real» significa **información fresca con latencia**, no rastreo segundo a segundo.

## Llegada estimada hasta el origen

PWA pide origen geográfico verificado antes de consultar, tanto para «Ahora» como «En 10 min».
Edge `pickup-eta` **v3** en Supabase, proyecto `zetaudvvutlouiqxopvg`, maneja sesiones y visitantes:

- Cuenta: `driver_pickup_eta_context_v1` valida sesión particular y limitación de acceso.
- Invitado: `driver_pickup_public_context_v1` valida presencia y conflictos, limita a 3 solicitudes/min y 45/h por visitante, 3 celdas distintas/h y topes globales. La función SQL permite ejecución **solo a service_role**, no a anon/authenticated.
- La lat/lng exacta del conductor se entrega únicamente entre esa función SQL y el Edge servidor para ejecutar rutas reales por carretera usando `/api/public/route-estimate` de Cloudflare. Jamás se devuelve el punto a la PWA; solo kilómetros múltiplos de 0,5 y minutos múltiplos de 5, con etiqueta `estimateType:"coarse"` y «Sujeto a confirmación».
- Si ORS no puede calcular ruta, devuelve `route_unavailable`, sin inventar valores ni distancia en línea recta.
- Si GPS expiró, jornada pausada, viaje activo o agenda ocupada, no publica ETA. Todo eso se comprueba en servidor.
- Desactivar desde Mapa revoca consentimiento y borra ubicación comercial en backend; si no tiene conexión, localmente detiene nuevos envíos y el estado viejo caduca en 90 s.

Estado: Mapa firmada y PWA compilada en Actions; QA Edge real fue creado en `.github/workflows/smoke-pickup-eta-v2.yml`. Faltan validar instalación física y una ruta real cuando el conductor esté verdaderamente disponible. No crear reservas de prueba sin aprobación explícita.

**Fuente Edge**: https://github.com/marcelofgx-ctrl/traslados-web/blob/main/supabase/functions/pickup-eta/index.ts.
