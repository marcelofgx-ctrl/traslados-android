# PWA v56 — Reintento de cálculo vial y diagnóstico no invasivo (11/10/2026)

## Incidente
Captura de Samsung Chrome: origen **Arenal Grande 2274**, destino **Aeropuerto de Carrasco**, ambos seleccionados, pero la tarjeta de estimación muestra `Cálculo pendiente`, distancia y tiempo vacíos, importe `A confirmar`. No es apropiado inferir una distancia en línea recta ni fingir kilometraje por carretera.

## Arquitectura y causa todavía por confirmar
- `route-engine.js` consulta `/api/public/route-estimate` del Worker `traslados-web` con plazo de 7,5 s; ante fallo, `public_cached_route_preview_v1` del Supabase del proyecto tiene plazo de 3,5 s.
- Solo se aceptan `source:openrouteservice` y `source:supabase_route_cache`, con km y minutos numéricos comprobados. La caché no garantiza cubrir rutas arbitrarias.
- Si ambos fallan, la interfaz original devolvía el mensaje inespecífico sin opción de reintento.
- **No se ha demostrado aún la causa puntual de la captura**: podría ser backend/ORS, tiempo de espera, conectividad o ausencia del trayecto en la caché.

## Cambios (frontend, sin cambio en proveedores, tarifa ni backend)
- Fallos anonimizados `not_configured`, `temporarily_unavailable`, `no_route`, `invalid_points`, `timeout`, `network_error`, `http_error`, `unverified_response` guardados por clave de ruta solo en memoria. No se almacenan datos personales, direcciones, posiciones ni cuerpos de respuesta.
- Mensaje humano contextual; detalles plegables con el código del fallo para diagnóstico técnico.
- Botón **↻ Reintentar cálculo** sin cambiar origen/destino/paradas: invalida la caché local del intento anterior y vuelve a consultar el motor. Cuando resulta verificado el botón se oculta.
- Mantener el botón Maps y no poner precio ni distancia ficticios ante falla.
- **PWA v56**, scripts `app.js?v=56`, `route-engine.js?v=56`, CSS `v=56` y `sw.js` actualizado (caché v56).
- Pruebas del motor de rutas y de la tarjeta ampliadas para error, recuperación del servidor, botón de reintentar, fallback Supabase y precios reales.
- Workflow `qa-public-road-routing.yml` ampliado para correr también en PR de cambios del motor y comprobar Worker públicamente desde CI (tres casos y latencia). No almacena claves ni GPS del conductor.

## Compatibilidad y QA
No se cambia Mapa Trayectos R24.9, Conductor, Cliente v15 Android WebView (usa la misma PWA), reservas, agenda, Supabase, Cloudflare ni los puntos GPS.
Después de compilar y fusionar, verificar publicación de Pages, abrir en Samsung y hacer reintento para Arenal Grande→Aeropuerto. **Un CI verde solo prueba código y puede registrar un Worker externo caído; no equivale a un cálculo exitoso en ese recorrido.** Analizar informe del workflow QA antes de atribuir el origen del error.
