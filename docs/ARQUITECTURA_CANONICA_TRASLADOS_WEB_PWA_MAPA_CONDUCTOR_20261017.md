# Traslados · arquitectura canónica (17 de octubre de 2026)

## Decisión vigente de producto

**ÚNICA APK ANDROID VIGENTE: MAPA TRAYECTOS.** No generar ni presentar APK Conductor independiente o APK Cliente. Las versiones históricas permanecen archivadas, sin constituir aplicaciones oficiales activas. Esta decisión reemplaza la enumeración previa de productos.

Las superficies funcionales vigentes son:

1. **Página pública**: https://marcelofgx-ctrl.github.io/traslados-android/ · presentación, información y acceso a reservar.
2. **Web/PWA de pasajeros — única aplicación de clientes**: https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ · mismo código y backend tanto en Chrome como instalada desde menú > Instalar. La PWA tiene origen y sesión de navegador comunes con la página en Chrome, salvo la habitual separación de almacenamiento de navegadores distintos.
3. **Mapa Trayectos Android — única APK**: paquete `uy.com.mapatrayectos`. Conduce GPS, histórico, datos, reservas, alerta flotante, aceptación/rechazo, fases, navegación y comunicaciones mediante módulos internos. R25.28/code80 es QA en [PR #116](https://github.com/marcelofgx-ctrl/traslados-android/pull/116), todavía **no** fusionada a producción.

Backend compartido: Supabase Traslados (`zetaudvvutlouiqxopvg`). La web envía reservas reales y cuenta/historial autorizados. La aceptación y el GPS funcionan dentro de Mapa (el código de gestión está embebido, no se empaqueta como segunda APK). El sistema nunca finge haber recogido al pasajero por el mero hecho de aceptar una reserva.


## Control de compilación — decisión 17/10/2026

Se deshabilitan de `main` los workflows de compilación independiente Conductor (`build-conductor-source.yml` y `build-conductor-release.yml`). La integración de Gradle y eliminación definitiva de los objetivos `:cliente`/`:conductor` está en la rama **draft** [Mapa PR #116](https://github.com/marcelofgx-ctrl/traslados-android/pull/116), con CI de Mapa verificado; **no afirmar que esa parte está fusionada en main hasta su integración**. La única revisión Android recomendada es Mapa R25.28 con aceptación/rechazo directo, bajo QA física. La APK R10 Conductor difundida por error queda obsoleta como entrega.

## Web/PWA v80

La rama `main` sirve `web-pasajero/index.html` y `web-pasajero/sw.js` v80. La web en navegador y la PWA instalada cargan **el mismo conjunto de archivos**. La portada en raíz enlaza exclusivamente al producto de pasajeros, sin controles para descargar una APK Cliente ni navegación técnica GitHub.

- Origen/destino, km, precio y estado tienen mayor contraste y jerarquía.
- Al aceptar una reserva aparece el mapa **dentro de su propia tarjeta**. La ruta contratada no se presenta como posición del auto.
- Cuando la reserva pasa a EN_CAMINO/EN_PUNTO/EN_VIAJE y Mapa publica GPS reciente consentido, un RPC privado de lectura devuelve posición únicamente a su pasajero autorizado. La web actualiza GPS cada ~12 s cuando está visible; kilómetros y minutos se calculan con el motor vial, no con simulaciones.
- Supabase: `mapa_reservation_position_v1`, `customer_reservation_position_v1`, tabla protegida `reservation_live_position`. No hay SELECT público directo.
- PWA instalable mediante Android Chrome/Samsung Internet o iOS Safari; no requerir Play Store ni APK Cliente.
- La instalación del navegador puede no importar los datos locales que quedaron dentro de una APK Cliente histórica; verificar que los viajes vinculados estén en la **cuenta** antes de desinstalar nada. Las reservas invitadas pueden depender del almacenamiento original.

## APK Android vigente y versiones históricas

Mapa QA R25.27 (firma e ID oficiales, reemplaza R25.25/R25.26):
https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/mapa-gps-reserva-20261017/preview-artifacts/Mapa_Trayectos_R25_27_Seguimiento_Privado_REVIEW.apk

**Histórico, no instalar ni continuar:** Conductor QA R9 (se compiló cuando todavía se ofrecía una APK independiente):
https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/conductor-r9-entrega-peticiones-20261017/preview-artifacts/Traslados_Conductor_R9_Reservas_Alertas_REVIEW.apk

No desinstalar las apps anteriores antes de actualizar; se preservan su SQLite, configuración, preferencias, historiales y credenciales Android compatibles. No realizar purga de puntos GPS ni borrar registros de reservas. No confundir builds `.debug`/`.datosreview` con APKs de actualización oficiales.

### Estado de integración

- Web/PWA v80 integrada en `main` y verificada por CI: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39053681933
- Publicación original de web/PWA v80: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39053729589
- Mapa R25.27 compilación/firma y privada GPS verificada en CI: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39048213989 ; PR de integración #113 draft.
- Conductor R9: build histórico de arquitectura anterior, no es producto vigente: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39050193874 ; PR #114 cerrado por reemplazo en Mapa #116.
- La APK Mapa de QA sigue **pendiente de validación física Samsung** antes de establecer una versión estable. Android 15/16 restringe el polling prolongado en segundo plano: FCM/push real sigue siendo un trabajo pendiente.
- PR #111 de actualizar APK Cliente y antiguos flujos de instalación web→Cliente nativo quedan **obsoletos**.

## Continuidad de pruebas y publicación

Antes de cambiar la portada o la PWA, probar búsqueda de direcciones, alta de cuenta, reserva precio fijo, recepción/aceptación en Conductor, cambios de estado, GPS Mapa→Supabase→PWA, control de expiración de GPS, chat, carga/historial, instalación PWA y permisos Samsung. Las pruebas deben validar el código real de la web, sin APK WebView duplicada. Nunca imprimir PIN, secretos ni posiciones personales en logs públicos.

**Regla para sesiones futuras**: entregar página pública, URL web/PWA y **solo** enlace de APK Mapa. Nunca ofrecer APK Cliente ni APK Conductor.
