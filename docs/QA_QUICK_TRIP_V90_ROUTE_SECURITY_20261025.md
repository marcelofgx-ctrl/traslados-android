# Quick Trip v90 — QA funcional, auditoría de rutas y conductor ficticio

Fecha de trabajo: 25-10-2026. Rama aislada: `qa/quicktrip-v90-route-sandbox-20261025`.
Base inalterable: `533effddfac8a9cfeb6ab19db92d9e06903d2917` (v89 aprobada visualmente).

## Auditoría de seguridad: NO consultar el Cloudflare Worker original
- `web-pasajero/route-engine.js` llama al servicio `https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate` y dispone de fallback a `/rest/v1/rpc/public_cached_route_preview_v1` en Supabase real. Además, `referenceFare` consulta `public_reference_quote_v1` en Supabase real. **No activar esos endpoints dentro de esta QA**: no se verificó que el Worker no consulte Supabase indirectamente.
- `web-pasajero/eta-engine.js` consulta `public_driver_availability_v1` y `pickup-eta` en Supabase real. No usar para pruebas de conductor ficticio.
- El servicio oficial OSRM documenta un endpoint de rutas `/route/v1/driving/{lon,lat;lon,lat}` con distancias en metros, duración en segundos y geometría opcional GeoJSON. El servidor público `router.project-osrm.org` es una DEMOSTRACIÓN sin garantías de disponibilidad y con política de uso ligero/no comercial. Sólo habilitado como ruta temporal para QA manual, **NO como motor de producción**.
- CSP de v90: `connect-src https://direcciones.ide.uy https://router.project-osrm.org`, con `form-action 'none'`. Supabase y Worker bloqueados por el navegador, sin proxies y sin acceso a datos de conductor. El único dato enviado a OSRM en una futura prueba manual son las coordenadas de origen y destino elegidas; nunca números de teléfono, nombres ni identificadores de pasajero.
- El cálculo de distancia/duración sólo se marca comprobado si OSRM responde `code=Ok`, el número de waypoints coincide con origen/paradas/destino y métricas numéricas dentro de límites. Una geometría GeoJSON inválida se omite en vez de inventarla. Si falla, se limpian los importes previos.
- **Precio**: `40 UYU/km` es un supuesto de QA definido para esta prueba; se multiplica por km devueltos por OSRM y se presenta como **precio ilustrativo, NO cotización oficial**. No se modifica tarifa ni contrato de reservas del proyecto real. No se habilita el botón de solicitud.
- **Conductor**: ejemplo 2,4 km / 7 min y disponible/no disponible, todos **explícitamente ficticios**. Son indicadores sintéticos de interfaz y NO valores verificados ni coordenadas de una persona real.

## Implementación aditiva en rama QA
- `web-pasajero/quicktrip-qa-route-v90.js`: adaptador separado; no reemplaza `TrasladosRoutes` ni `TrasladosETA`. Sólo consulta OSRM demo desde una sesión QA manual, rellena tarjeta rotulada, incluye un pequeño trazo de respuesta GeoJSON si hay geometría verificada, botón para conmutar conductor ficticio.
- `web-pasajero/quicktrip-qa-route-v90.css`: diseño contenido dentro de la pantalla compacta aprobada.
- `index.html`, `sw.js`, `manifest.webmanifest` y guard del empaquetado: sólo la configuración de la candidata QA. La etiqueta de aislamiento contiene ambos prefijos `v89` y `v90` para no desactivar el GPS intuitivo.
- Motores estables, diseño v89, base Supabase, Android y main no modificados.

## Pruebas y evidencia
- Workflow `.github/workflows/qa-quicktrip-v90-route-sandbox.yml` sólo genera artefactos de pruebas: no despliega, no fusiona ni genera APK. Bloquea cambios en 16 archivos estables y cualquier ampliación de CSP fuera de IDE+OSRM.
- Test Chromium en 393 y 360 píxeles con ubicaciones y conductor **ficticios**; intercepta OSRM con respuestas falsas controladas, comprueba conversión km/min/precio, mapa opcional de GeoJSON, simulación de conductor, falla cerrada y cero solicitudes reales a Supabase/Cloudflare.
- Un sondeo opcional de OSRM público usa **un único par sintético de coordenadas** de Montevideo; no consulta ninguna ubicación del usuario. La disponibilidad del servidor demo puede fallar: eso no prueba un bug de Quick Trip.
- NO afirmar “ruta real de un viaje” hasta tener un resultado externo exitoso en QA y prueba física posterior. La QA nunca certifica tarifa productiva ni disponibilidad de conductor real.

## Límites y entrega
Nada de producción, merges, base Supabase real, Cloudflare original, APK ni un nuevo Preview. Revisar las capturas e informes GitHub y pedir autorización por separado antes de probar cualquier Preview v90. Los previews v86–v89 se conservan.
