# Arquitectura mosaico modular — Mapa Trayectos + Conductor
Fecha: 16/10/2026. Rama `feature/mapa-driver-mosaico-modular-20261016`.
[PR #100](https://github.com/marcelofgx-ctrl/traslados-android/pull/100), basado en PR #99 (precio fijo).
**Estado: implementado y compilado en rama de prueba, NO publicado, no fusionado a main.**

## Decisión
Una **APK Mapa Trayectos** para el conductor y navegación, con componentes internos independientes. Conductor independiente continúa como respaldo mientras se valida. Es un monolito modular, no microservicios ni varias apps obligatorias. No sustituye Supabase ni cambia Cliente/PWA.

## Qué eliminamos
- La tarea `prepareEmbeddedConductor` que copiaba todas las clases Java, cambiaba imports, interceptaba métodos de ciclo de vida y reescribía llamadas al globito en pleno build.
- La necesidad de que Mapa incluya una copia transformada del código de Conductor.
- La dependencia del módulo compartido respecto a la carpeta antigua `conductor/src/main/java`.

## Estructura comprobada
```
mapatrayectos/          → APK Mapa R25.27 (versionCode 79)
  MapaHostApplication   → implementación del Host, burbuja/alertas únicas
conductor/              → APK independiente R10 (versionCode 132)
  AndroidManifest       → icono de lanzador y empaquetado propios
feature/driver/         → Biblioteca Android compartida
  src/main/java/...     → Api, MainActivity, DemandMapActivity,
                          ReservationMonitorService, TripTelemetryService,
                          DriverHostBridge
  src/main/res/...      → WAV, iconos, texturas y estilos propios
core-reservations/      → Lógica Java pura reutilizada por Mapa
```
- Los dos módulos de aplicación dependen de `implementation project(':feature:driver')`. La biblioteca Android **compila una sola fuente canónica**, sin código de Conductor generado.
- `DriverHostBridge` expone cuatro llamadas: `onPendingSnapshot`, `onNewRequest`, `onDriverVisible` y `onDriverHidden`. No importa clases de Mapa.
- Al iniciarse el proceso de Mapa, `MapaHostApplication` instala el host, conserva `TransferAlerts` y el único overlay de `TrackingService`. El monitor de reservas de Conductor no inicia su segundo globito cuando está embebido. En APK Conductor independiente funciona su propio overlay.
- Recursos renombrados sin modificar bytes: `conductor_app_icon.png`, `conductor_bg_texture.png`. `ConductorAppTheme` mantiene el aspecto dentro de Mapa y de Conductor.

## Evidencia y descargas de prueba
- [Build conjunto posterior al traslado físico de archivos](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38890754047): **SUCCESS**; compila dos RELEASE, comprueba applicationId, versionCode y firmante original, genera hashes.
- [Mapa R25.27 mosaico](https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/mapa-driver-mosaico-modular-20261016/preview-artifacts/mapa-trayectos-mosaico-modular-r25.27.apk) — SHA256 `73526dfd9b43a234010c99416fe520ccd69af945b8b5f34e527acde333900b46`.
- [Conductor R10 respaldo](https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/mapa-driver-mosaico-modular-20261016/preview-artifacts/traslados-conductor-mosaico-modular-r10.apk) — SHA256 `98dd75cd7d2bfd217066c107b9c91e369fdfee83b50c453e21259dfef6ff509c`.
- Regresiones estáticas `mapatrayectos/tests/mosaic-driver-contract-smoke.py` y `mapatrayectos/tests/chat-flow-contract-smoke.py`; CI `validate-modular-mapa.yml` y `build-conductor-source.yml` actualizadas para la estructura canónica.

## Publicación y controles pendientes
1. **No publicar hasta probar en Samsung** actualización Mapa sobre R25.26 sin desinstalar: ajustes, permisos de superposición, registros de jornada y datos de la base local intactos.
2. Dentro de Mapa, abrir Conductor, cerrar con botón Inicio, validar **un solo globito**, notificaciones con pantalla encendida y en segundo plano, tonos y vibración.
3. Cliente Premium v26 solicita → Conductor integrado acepta o rechaza → Cliente muestra estado y precio fijo → IR A RECOGER → EN PUNTO → EN VIAJE → FINALIZAR.
4. Medir memoria RSS/PSS, batería y consumo GPS/sync en casos parado/conduciendo; comparar con R25.26. **Compilar correctamente no demuestra rendimiento mejorado.**
5. El workflow RELEASE histórico de main está fijado a R25.25 y a la tarea antigua. Adaptarlo para la versión que se vaya a publicar **solo tras la validación física y revisión del PR #100**.
6. Comprobar todos los CI de la rama antes de fusionar. No tocar URLs de producción, Supabase, cuentas, datos ni claves de firma.

## Continuidad
Retomar desde [PR #100](https://github.com/marcelofgx-ctrl/traslados-android/pull/100). La web principal y APKs públicas permanecen en versiones anteriores. El backend de precio fijo del PR #99 sí está preparado, y la nueva arquitectura no altera ese contrato.
