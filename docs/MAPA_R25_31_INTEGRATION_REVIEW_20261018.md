# Mapa Trayectos R25.31 — revisión integrada (18/10/2026)

**NO es una publicación estable ni acredita QA Samsung.** Rama `integration/mapa-r25-31-review-20261018`, basada en `feature/mapa-r25-30-identidad-conductor-20261018` mediante PR #126 y #127. Conserva Mapa Trayectos como **única APK Android** (`applicationId uy.com.mapatrayectos`, `versionCode 83`; versión `0.1-R25.31-QA`).

## Funciones heredadas, sin reimplementarlas
Cadena #110→#113→#116→#120→#121: conservación de datos, GPS privado por reserva, Conductor integrado, recepción activa y vínculo seguro una vez con Keystore. Ninguna APK Cliente o Conductor independiente.

## Correcciones reunidas
- STATUS-001 (#126): un solo vocabulario en tarjetas del Conductor integrado para precio fijo vs presupuesto heredado, sin importe duplicado. Prueba JVM `ReservationQuoteLabelTest`.
- MON-001/AUTH-001 (#127): la identidad es obligatoria para leer caché local de peticiones y alertas; cerrar sesión/vincular limpia **solo vistas efímeras privadas** (y cursor de mensajes), nunca registros de viajes/GPS ni datos de Supabase. Respuestas del monitor de identidad antigua no deben anunciar reservas/chat. Test `scripts/test_mapa_session_privacy.py`.
- `validate-modular-mapa.yml` exige ambos tests y compilación de la **única** app.

## Estado de QA (en apertura)
- Pruebas originales en branches: #126 [SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39123367643), #127 [SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39123861864).
- **La integración requiere nuevo CI en SHA de esta rama.** No inferir éxito de los CI anteriores.
- Revisar firma Android previa y `versionCode` antes de producir APK firmada de QA. No cambiar descargas públicas ni enlaces de configuración Supabase.
- QA físico Samsung: actualización encima de Mapa R25.30 (sin desinstalar), vincular conductor/recuperar sesión, cerrar→no ver otras reservas, reconectar con red caída, una alerta por solicitud, fase auto→recogida, recogida, fase auto→destino, cierre de viaje e historial conservado.
- No publicar como estable hasta pasar QA físico y contratos Web/PWA/GPS. Actualizar memoria `project_ops` con SHA, build, firma y evidencias.
