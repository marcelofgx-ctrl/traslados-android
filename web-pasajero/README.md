# Traslados con Reserva — sitio web y PWA de pasajeros

Este directorio contiene una aplicación web estática, premium y gratuita: **la misma interfaz es la web y la PWA instalable** (no son dos aplicaciones con datos distintos). Android Cliente v11.5 R10 es una **APK nativa separada**. Las tres leen/escriben sobre el mismo Supabase operativo `zetaudvvutlouiqxopvg`.

## Funciones

- Solicitudes inmediatas o para dentro de 10 minutos (sujetas a validación/aceptación) y viajes programados.
- Reserva como invitado y cuenta con teléfono/PIN de seis dígitos.
- Geobúsqueda IDE Uruguay oficial por botón explícito; Montevideo y Canelones destacados + otros 17 departamentos. GPS de origen con permiso.
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
