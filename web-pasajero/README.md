# Traslados con Reserva — sitio web y PWA de pasajeros

Este directorio contiene una aplicación web estática, premium y gratuita: **la misma interfaz es la web y la PWA instalable** (no son dos aplicaciones con datos distintos). Android Cliente v11.5 R10 es una **APK nativa separada**. Las tres leen/escriben sobre el mismo Supabase operativo `zetaudvvutlouiqxopvg`.

## Funciones

- Solicitudes inmediatas o para dentro de 10 minutos (sujetas a validación/aceptación) y viajes programados.
- Reserva como invitado y cuenta con teléfono/PIN de seis dígitos.
- Búsqueda general por nombre de lugares (índice OSM de Uruguay con más de 22.000 puntos de interés) y por dirección con IDE Uruguay. Autocompletado 240 ms. Aeropuerto de Carrasco y Laguna del Sauce con resultados inmediatos. El departamento ordena resultados, nunca excluye el resto del país. GPS de origen con permiso.
- Usuarios autenticados: hasta ocho paradas con lat/lng reales vía `customer_create_reservation_v12`; validación de disponibilidad vía RPC. Invitados: `create_reservation` básico sin paradas.
- Historial y presupuesto según estados Supabase, respuesta aceptar/rechazar, cancelación con confirmación. No expone tokens, no inventa precio ni tiempos.
- PWA con manifest, iconos 192/512/maskable y service worker que solamente cachea shell. Sin conexión **nunca intenta crear reservas**.

## Seguridad y limitaciones

La clave integrada `sb_publishable_...` es **pública** y está diseñada para usarse en frontend. Jamás añadir la clave secreta `service_role`. Los procedimientos de Supabase aplican validación de sesiones; RLS protege las tablas. La sesión se guarda localmente en el dispositivo, sin mostrarla en URL. Si una APK debug anterior ya estaba instalada, una RELEASE de firma distinta puede exigir desinstalación: las reservas permanecen en Supabase.

**No hay seguimiento GPS live ni push garantizados para pasajeros hasta configurar servicios backend.** La PWA es instalada como sitio confiable HTTPS, no como APK WebView.

## Publicación gratis

El workflow de GitHub Actions `deploy-web-pasajero-pwa.yml` genera PNG 192/512/maskable, valida código JS y manifiesto, sube una copia completa ZIP y solicita GitHub Pages. Es necesario habilitar una vez **Settings → Pages → GitHub Actions** si Pages no estaba activo. URL esperada solo cuando GitHub reporte un despliegue exitoso.

## Fuente

`index.html`, `styles.css`, `app.js`, `sw.js`, `manifest.webmanifest`, `offline.html`. Sin React, npm, dependencias externas ni suscripción de terceros.


## Corrección urgente del formulario (09/10/2026)

- La captura de error `function gen_random_bytes(integer) does not exist` corresponde a **esta web de GitHub Pages**, no a la web principal del repositorio `traslados-web` publicada en Cloudflare.
- Se aplicó directamente en Supabase `zetaudvvutlouiqxopvg` la migración `fix_public_reservation_crypto_schema_20261009`. **No altera reservas ni datos existentes**: `public.generate_reservation_code()` ahora ejecuta `extensions.gen_random_bytes(3)` y `public.create_reservation(...)` ejecuta `extensions.gen_random_bytes(32)`. Ambas funciones tienen un `search_path` explícito a `public, extensions`. El generador fue probado con el rol `anon`; la reserva completa debe verificarse desde el teléfono.
- Se rediseñó la pantalla **Revisá antes de enviar**: recorrido destacado, fecha/hora y pasajeros compactos, presupuesto pendiente, enlace a Google Maps y datos del pasajero opcionales desplegables.
- El botón pasó a **Enviar solicitud al conductor**: la solicitud no confirma automáticamente la recogida ni cobra al cliente.
- Se cambió el service worker a `traslados-cliente-pwa-v2` y consulta la red antes que la caché cuando está conectado. Si el dispositivo continúa mostrando la interfaz anterior, cerrar/reabrir y actualizar el sitio.
- GitHub Actions `check-web-pasajero-review.yml` valida JS, interfaz, seguridad y caché en PR, mientras `deploy-web-pasajero-pwa.yml` publica al fusionar en `main`.

**Advertencia de arquitectura:** el proyecto mantiene dos frontends de pasajeros distintos que escriben en el mismo Supabase: esta PWA estática `https://marcelofgx-ctrl.github.io/traslados-android/` y la web premium `https://traslados-web.marcelof-gx.workers.dev`. Cambiar el diseño de uno no modifica automáticamente el otro. Debe decidirse cuál será el enlace público único, sin romper accesos a reservas anteriores.

Las burbujas flotantes de Uber/Mapa que pueden cubrir los botones en capturas Android son superposiciones de otras aplicaciones, **no elementos HTML de la PWA**.


## Arquitectura de búsqueda y rutas — corrección 09/10/2026

**Problema resuelto en código:** la PWA de GitHub Pages `/traslados-android/web-pasajero/` era distinta a la web premium Cloudflare y todavía consultaba únicamente `direcciones.ide.uy/api/v1/geocode/candidates?q=<texto>, <departamento>`. Escribir «Aeropuerto» con Canelones producía nombres de calles, no el aeropuerto. Tampoco había consulta de km/min de carretera en el formulario.

- `geo-search.js`: lugares por nombre, coincidencia por prefijo de palabra (por ejemplo, «Punta Carreta Shopping» encuentra «Punta Carretas Shopping»), índice `data/uy-pois.json` derivado de © OpenStreetMap contributors, ODbL. Carrasco aparece inmediatamente aunque falle IDE.
- `app.js`: buscador IDE **sin concatenar departamentos**, autocompletado, resultados de lugares y direcciones, tarjetas que se contraen al seleccionar, origen/destino priorizados.
- `app.js`: resumen de ruta km/min y esquema del trazado, utilizando el mismo motor del Worker de Cloudflare `/api/public/route-estimate`. Si `ORS_API_KEY` está ausente o la ruta falla, **no se inventan kilómetros**: se ofrece enlace a Google Maps con origen/destino/paradas para consultar la ruta real.
- `.github/workflows/sync-passenger-pois.yml`: mantiene el índice de lugares en la PWA, actualizado automáticamente cada lunes desde el repo `traslados-web`. No es necesario agregar lugares manualmente.
- `sw.js`: caché v3 y estrategia red-primero para JavaScript/CSS, sin enviar reservas sin conexión.
- `.github/workflows/smoke-public-passenger.yml`: prueba pública opcional de HTML, motor de búsqueda, trazado y presencia de los lugares en GitHub Pages.
- **Enlace directo a la PWA:** https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/
- **Índice estable del proyecto (no es la web de reservas):** https://marcelofgx-ctrl.github.io/traslados-android/
- **Web premium separada:** https://traslados-web.marcelof-gx.workers.dev/

**Pendiente de completar:** la clave gratuita de openrouteservice `ORS_API_KEY` en los secretos de Cloudflare para presentar km/min directamente en ambas webs; el GPS en vivo del conductor es otro componente todavía no activo. No modificar el backend de reservas para suplir rutas. Verificar con un teléfono real antes de anunciar una función de recogida inmediata.
