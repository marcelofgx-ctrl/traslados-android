# Traslados Android — índice del repositorio

> **PUNTO DE ENTRADA ACTUAL (13/10/2026):** [REANUDAR TRASLADOS — contexto vivo, versiones, cambios y pendientes](docs/REANUDAR_TRASLADOS.md). **Leelo primero en cualquier sesión nueva.** PWA v68 · Cliente Premium v15 · Conductor 11.5-R6 · Mapa R25.0. Los textos inferiores que dicen «actual», «vigente» o mencionan v24/v26/R24.6 corresponden a cortes **históricos** y no deben usarse como estado actual sin verificar `main` y Actions.

---


> **CIERRE ACTUAL 10/10/2026 — LEER ANTES QUE LAS NOTAS HISTÓRICAS:** La única web/PWA de pasajeros aprobada es **https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/**, **PWA v26**. Cliente Android **APK Premium v14** (versionCode 202, WebView con la misma PWA); Mapa Trayectos **APK R24.6** (versionCode 48, firmada) con menú «Disponibilidad de recogida» indicador verde/rojo y opt-in + marcado ocupado. Descarga directa Mapa: **https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r24.6.apk**. Backend Supabase `public_driver_availability_v1` requiere consentimiento, turno y GPS reciente (90 s), movimiento en 5 min, sin viaje activo ni ocupación de agenda en una ventana aproximada de 60 min; NO lee automáticamente viajes externos Uber/Cabify. Edge `pickup-eta` v3 ofrece a visitantes y usuarios con sesión solo kilómetros redondeados (0,5 km) y minutos redondeados (5 min), calculados desde GPS real únicamente dentro del servidor; SQL de contexto huésped limitado a `service_role` con rate limits, no expone coordenadas. **Advertencia:** smoke Edge e2e pasó autorización, límites y privacidad, pero una ruta de prueba devolvió `route_unavailable`; no se debe anunciar ETA garantizada hasta verificar recorrido vial accesible en teléfono. Builds: Mapa [38094623514](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38094623514) SUCCESS, QA Edge [38094856829](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38094856829) SUCCESS, PWA build [38094982181](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38094982181) SUCCESS. Falta validación física Mapa/Cliente e integración end-to-end. Mantener inmutable link Pages y API Worker para rutas; NO cambiar web hacia Cloudflare.


> **Release pasajero vigente (10/10/2026):** web/PWA única y definitiva https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ (**v24**). APK nativa **Cliente Premium v14.0 / versionCode 202**, firmada mismo certificado; descarga https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v14.apk . Ambas usan exactamente la interfaz Pages, no frontend visual Workers. PWA urgente compacta; Mapa controla disponibilidad, no se simulan reservas inmediatas. [APK SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38093358000) · [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38093646637).


> **ENLACE ÚNICO PARA PASAJEROS (10/10/2026):** https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ . Web/PWA con diseño aprobado por el usuario; APK Cliente v13 carga ESTA web (no Cloudflare). Cloudflare Worker solo complementa con APIs de rutas, km/min/tarifa; no sustituir la web aprobada.


> **INICIO EN CUALQUIER SESIÓN:** [CONTEXTO MAESTRO DE TODOS LOS PROYECTOS](docs/CONTEXTO_MAESTRO_PROYECTOS.md) — Mapa, Conductor, Cliente APK, GitHub Pages/PWA, Cloudflare y Supabase. Contiene enlaces correctos, último estado comprobado y reglas de actualización. Leelo **antes** de los contextos históricos específicos.

Este repositorio contiene **más de una aplicación Android**. Antes de programar, elegí el módulo correcto.

## Mapa Trayectos — empezar aquí
- **[RETOMAR MAPA TRAYECTOS (contexto maestro actualizado)](docs/RETOMAR_MAPA_TRAYECTOS.md)**. Documento de referencia para continuar desde una nueva sesión de ChatGPT.
- [Historial de versiones](docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md).
- [Contexto histórico ampliado](docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md).
- [Roadmap histórico](docs/mapa-trayectos-roadmap-2026-10-08.md).
- Aplicación Android nativa en `mapatrayectos/`. Paquete `uy.com.mapatrayectos`.

**Instalación web y APK:** desde la sección «Instalar», Chrome ofrece primero la PWA si está disponible y la APK Android v14 aparece también como alternativa visible (no queda oculta por el instalador PWA). Verificado en CI y GitHub Pages. 

- Build firmado: [Mapa Trayectos GitHub Actions](.github/workflows/build-mapa-trayectos.yml).

**Corte documentado:** `0.1-R24.3`, `versionCode 45`, [build firmado exitoso 37924921923](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924921923). Este número no sustituye verificar `main` ni las compilaciones posteriores.

**R24.3 backup ZIP (Drive verificado, contiene APK):** https://drive.google.com/file/d/1aoPV7gQIAebhz57ZdpAdBcj9bqYmh-VP/view

**Importante:** R24.3 conserva R24.2 y corrige la ventana de presupuestos de Conductor integrado para desplazar campos por encima del teclado numérico: ajuste dinámico de altura según IME y scroll automático al foco. CI y firma APK OK; falta QA real en Samsung. **No hay push FCM auténticos, ni GPS live en Cliente, ni unificación de GPS.** No borrar datos.

## Web de pasajeros (reservas)

- **[ABRIR TRASLADOS — WEB DE PASAJEROS](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/)** — formulario de reservas y PWA para probar en el celular.
- [Código fuente de la web](web-pasajero/) y [guía de la PWA](web-pasajero/README.md).
- Este índice del repositorio permanece en `https://marcelofgx-ctrl.github.io/traslados-android/` para recuperar contexto, builds e instrucciones; **no es el formulario de pasajeros**.
- Solo GitHub Pages desde la rama `main` publica el sitio. El workflow `deploy-web-pasajero-pwa.yml` valida y empaqueta, pero no compite con una segunda publicación de Pages.

## Otros módulos
- `conductor/` conserva la APK Conductor independiente como respaldo. Sus fuentes también se empaquetan como módulo **interno** en Mapa Trayectos R24.0; no confundir la APK independiente con el nuevo acceso integrado.

## Instrucción mínima para otra sesión
> Retomá Mapa Trayectos en `marcelofgx-ctrl/traslados-android`, rama `main`. Primero leé `docs/CONTEXTO_MAESTRO_PROYECTOS.md`, después `docs/RETOMAR_MAPA_TRAYECTOS.md`, verificá el último commit y los GitHub Actions. No cambies el diseño aprobado, los sonidos de viajes ni datos existentes sin consultarme.
