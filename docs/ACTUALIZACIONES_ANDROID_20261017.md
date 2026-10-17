# Versionado y actualizaciones Android — 17/10/2026

## Alcance
Trabajo aislado sobre `main` (no modificar ni fusionar PR #109 Cliente/Web ni PR #110 Mapa/Datos).
La fuente oficial de distribución es GitHub Pages:
`https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/versiones.html`.

**Objetivo:** una aplicación oficial por función en Android; actualizaciones descargadas con
consentimiento; versiones de revisión fuera del catálogo oficial.

## Identidad Android y causa de iconos duplicados
- Cliente oficial `uy.com.traslados.cliente` (v16: code 204); pruebas `.debug`
  usan otro identificador y se instalan junto a la oficial.
- Mapa oficial `uy.com.mapatrayectos` (R25.25: code 77).
  R25.26 Datos Review: `uy.com.mapatrayectos.datosreview`: **dos apps a propósito**.
- Conductor independiente `uy.com.traslados.conductor` (R8: code 130).

Si coinciden **identificador + firma**, y `versionCode` crece, Android permite
actualizar la APK sin instalar otra aplicación. Si cambia alguno, no es una
actualización sobre la misma instalación. No desinstalar antes de respaldar SQLite,
historial y preferencias locales de Mapa.

## Circuito nuevo
1. Construir APK **RELEASE** con su package ID original, keystore/certificado
   original y `versionCode` ascendente. Nunca distribuir una DEBUG a usuarios.
2. QA de Android y seguridad (comparar package, versión, certificado, prueba de
   instalación en dispositivo, permisos, login, GPS y persistencia de datos).
3. Publicar el binario estable en `web-pasajero/downloads/`, manteniendo el
   patrón: `traslados-cliente-premium-vNN.apk`,
   `mapa-trayectos-rNN.NN.apk`, `traslados-conductor-rNN.apk`.
4. `.github/workflows/publish-stable-android-updates.yml` se ejecuta con el
   commit de la APK a `main`, verifica SHA256, package ID, versión y firma
   Android contra la versión oficial anterior; **solo si todo coincide y
   `versionCode` aumenta**, actualiza `web-pasajero/releases.json`.
   Si falta APK o difiere el certificado, aborta sin anunciarla.
5. La web lee `releases.json` sin caché para mostrar el enlace de descarga
   estable en `versiones.html`. Cliente APK, cuando la web se abre o vuelve
   a primer plano, compara la versión nativa con el catálogo. Solo muestra
   «ACTUALIZAR» si hay una **versión más nueva** y el archivo está accesible.
   La acción descarga el archivo mediante Android: **nunca se instala
   silenciosamente**, el usuario debe aceptar.
6. La Web/PWA sigue recibiendo sus pantallas desde GitHub Pages; el
   Service Worker controla el código web, no instala ni actualiza APK Android.

## Convivencia con infraestructura anterior
La RPC pública de Supabase `public_app_config_v1` ya ofrece enlace
`client_apk_url` y versión para el instalador; se conserva para evitar
regresiones. Al publicar una release, sincronizar también esa RPC/configuración
si se desea que el botón de instalación inicial apunte a la misma versión.
`releases.json` es la fuente verificable para anuncios de actualización y
centro oficial de las tres APK. **Nunca** publicar previsualizaciones desde la
configuración pública.

El Cliente v14/v15/v16 se reconoce mediante su antiguo user agent y mapeo
de `versionCode`. En builds futuros se escribe automáticamente el
`versionCode` real en user agent: `TrasladosNativeCode/N`, sin un puente
JavaScript a Android ni acceso a permisos nuevos.

## Limitaciones explícitas
- Avisos de Cliente solo al abrir/volver a la app y con ella visible, **no push**
  cuando la app está cerrada. Refresco periódico con app visible: 30 minutos.
- `versiones.html` ya permite descargar estables de Cliente, Mapa y
  Conductor. Un detector de actualizaciones **dentro** de Mapa/Conductor
  requiere integrar una pantalla nativa y probarla por separado (no incluido).
- `releases.json` jamás conserva secretos, GPS o datos de pasajeros.
- Las APK de prueba pueden convivir con la instalación principal y no se
  actualizan automáticamente a una release: su package ID es intencionalmente
  distinto.
- Publicación y adopción final pendientes del QA físico Samsung y
  resolución de los iconos duplicados a partir de capturas reales.

## Pruebas
`node web-pasajero/tests/release-updates-smoke.cjs`

`python3 scripts/sync_stable_release_catalog.py --mode check`

`python3 scripts/sync_stable_release_catalog.py --mode check --android-tools`
(requiere `aapt` y `apksigner` del SDK Android)

`.github/workflows/verify-stable-android-updates.yml` cubre la validación en PR.

## No tocar
- PR #110: exportación histórica sin borrar puntos GPS ni datos.
- PR #109: paridad funcional Cliente/Web y UI premium.
- Proyecto Supabase Oak & Ember.
- Producción hasta completar validación y merge controlado.
