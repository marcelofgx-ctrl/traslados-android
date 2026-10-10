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

## Ayuda GPS Android / Chrome — 10/10/2026

La captura real del usuario mostró el aviso de seguridad de Android **«Este sitio no puede solicitarte permiso. Cierra las burbujas o superposiciones»** al tocar «Usar ubicación actual» desde la PWA instalada. La fuente era este `web-pasajero/app.js`; el antiguo error de GPS era un `toast` efímero y genérico.

**Implementado en código (PWA v14):** `app.js` solicita el permiso mediante `navigator.geolocation.getCurrentPosition` tras pulsación explícita; distingue denegado (1), no disponible (2) y demora (3), explica cómo desactivar superposiciones y cómo conceder ubicación a Chrome en Android, y ofrece botones **Volver a intentar** y **Escribir dirección**. La guía premium está en `styles.css`, `index.html` apunta a recursos `?v=14` y `sw.js` usa caché `traslados-cliente-pwa-v14`. Smoke `tests/location-permission-smoke.cjs` se añadió al workflow de empaquetado y al de revisión. No toca reservas, tokens ni Supabase.

**Limitación:** una web/PWA no puede abrir de forma fiable ni configurar automáticamente el permiso de ubicación del sistema; debe pedirlo desde un gesto del usuario y guiar en caso de bloqueo por overlays. GitHub Actions de empaquetado y Pages reportaron SUCCESS en [38026118498](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38026118498) y [38026121606](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38026121606). La entrega HTTP y el diálogo de permisos en un Samsung real **siguen pendientes de comprobación**, y el usuario debe reabrir/actualizar la PWA.

**Paridad:** `traslados-web/src/components/UyLocationPicker.tsx` recibió la misma asistencia en el código Workers, con CI verde (compilaciones y TypeScript). **Su despliegue Cloudflare permanece manual y pendiente**; GitHub Pages no es la web pública principal.

## Instalación inteligente PWA o APK Android Cliente (10/10/2026, v19)

**Objetivo del pasajero:** al elegir **Instalar** en Chrome Android, utilizar `beforeinstallprompt` cuando realmente se ofrezca, sin asumir que su ausencia prueba incompatibilidad. Cuando el navegador no permite el instalador automático y existe un APK público comprobado, ofrecer **Descargar Traslados para Android** con archivo nativo verificado. Mantener «Ver cómo instalar» para quien prefiera la versión web y detectar modo `standalone` sin ofrecer descarga redundante.

**Implementación:** `app.js` `installUI()` usa `beforeinstallprompt` y `appinstalled`, reconoce Chrome Android frente a iOS y aplicación instalada, comprueba antes de mostrar la alternativa una solicitud `HEAD` a la **misma web** `./downloads/traslados-cliente-v11.5-r12.apk` (HTTP 200, tamaño superior a 100 kB y MIME no HTML), presenta una tarjeta premium con diferencias entre versión web y APK nativa Cliente 11.5-R12, y solo inicia la descarga por gesto explícito. Si la red falla o la APK no existe, la opción no se muestra. `index.html` incluye los elementos `install-apk-option`, `install-apk-link`, `install-apk-web-guide`; `styles.css` mantiene la estética aprobada. Recursos web **v19** y SW `traslados-cliente-pwa-v19`.

**Publicación de APK real:** la RELEASE Cliente R12 del run [38021100002](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38021100002) usa la llave permanente, fue verificada por apksigner (v2/v3 según log) y trae `SIGNING.txt` y `SHA256SUMS.txt`. El workflow nuevo `.github/workflows/publicar-cliente-apk-pwa.yml` recupera ese artifact de forma autenticada, verifica SHA256, la firma registrada y formato PK, y publica el APK original (144.012 bytes) en `web-pasajero/downloads/` de la rama main, junto con suma de verificación; [publicación #38072519365](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38072519365) **SUCCESS**. No se compiló una APK distinta sin clave de firma. No es APK de Conductor ni un atajo de Chrome.

**Verificaciones:** [CI de la PWA v19 #38072703486](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38072703486) **SUCCESS**; [Pages #38072733681](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38072733681) **SUCCESS**. `tests/install-flow-smoke.cjs` prueba instalación PWA, modo ya instalado, fallo del prompt, APK existente por HEAD, APK no existente e iOS. [Verificación pública #38072920196](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38072920196) **SUCCESS**: HTTP 200, `Content-Type: application/vnd.android.package-archive`, `Content-Length: 144012`, checksum SHA256 correcto. **Pendiente prueba física de instalación nativa en el Android de la madre del usuario**: permisos Android para APK externa y compatibilidad reales solo se comprueban en dispositivo.

**Web oficial:** se preparó en `traslados-web` `/api/public/cliente-apk` para servir la APK bajo el propio host Workers (proxy binario sin redirección visible a Pages), y en `CustomerShareTools.tsx` la misma prioridad de PWA y fallback firmado. CI **SUCCESS** para código, pero **NO** hay publicación Cloudflare confirmada; requiere el workflow manual canónico. El recurso de GitHub Pages **no debe confundirse con la web premium principal**; el pasajero puede ver `github.io` en la URL auxiliar, no en botones ni textos de la interfaz.

**Reglas:** nunca ofrecer APK inexistente ni archivos de conductor, no pedir desactivar Play Protect, no confundir PWA y aplicación nativa o prometer idénticas funciones, no modificar reservas/Supabase/GPS/Mapa. El usuario debe aceptar la instalación en Android.
