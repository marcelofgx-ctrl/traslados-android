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
