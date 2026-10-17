# Traslados · arquitectura canónica (17 de octubre de 2026)

## Decisión vigente de producto

**No existe más una APK Cliente en la arquitectura vigente.** No generar, vender, enlazar ni anunciar nuevas APKs Android para pasajeros. Los binarios históricos permanecen archivados para trazabilidad y usuarios que ya los instalaron, pero no son una cuarta superficie oficial.

Las superficies funcionales vigentes son:

1. **Página pública**: https://marcelofgx-ctrl.github.io/traslados-android/ · presentación, información y acceso a reservar.
2. **Web/PWA de pasajeros — única aplicación de clientes**: https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ · mismo código y backend tanto en Chrome como instalada desde menú > Instalar. La PWA tiene origen y sesión de navegador comunes con la página en Chrome, salvo la habitual separación de almacenamiento de navegadores distintos.
3. **Mapa Trayectos Android**: paquete oficial `uy.com.mapatrayectos`, edición R25.27 QA/code 79, GPS del conductor, históricos, menú Datos y exportaciones.
4. **Conductor Android**: paquete oficial `uy.com.traslados.conductor`, edición R9 QA/code 131, recibe y gestiona solicitudes/aceptación, estados, jornadas y avisos.

Backend compartido: Supabase Traslados (`zetaudvvutlouiqxopvg`). La web envía reservas reales y cuenta/historial autorizados. La aceptación es de Conductor, la ubicación es de Mapa. El sistema nunca finge haber recogido al pasajero por el mero hecho de aceptar una reserva.

## Web/PWA v80

La rama `main` sirve `web-pasajero/index.html` y `web-pasajero/sw.js` v80. La web en navegador y la PWA instalada cargan **el mismo conjunto de archivos**. La portada en raíz enlaza exclusivamente al producto de pasajeros, sin controles para descargar una APK Cliente ni navegación técnica GitHub.

- Origen/destino, km, precio y estado tienen mayor contraste y jerarquía.
- Al aceptar una reserva aparece el mapa **dentro de su propia tarjeta**. La ruta contratada no se presenta como posición del auto.
- Cuando la reserva pasa a EN_CAMINO/EN_PUNTO/EN_VIAJE y Mapa publica GPS reciente consentido, un RPC privado de lectura devuelve posición únicamente a su pasajero autorizado. La web actualiza GPS cada ~12 s cuando está visible; kilómetros y minutos se calculan con el motor vial, no con simulaciones.
- Supabase: `mapa_reservation_position_v1`, `customer_reservation_position_v1`, tabla protegida `reservation_live_position`. No hay SELECT público directo.
- PWA instalable mediante Android Chrome/Samsung Internet o iOS Safari; no requerir Play Store ni APK Cliente.
- La instalación del navegador puede no importar los datos locales que quedaron dentro de una APK Cliente histórica; verificar que los viajes vinculados estén en la **cuenta** antes de desinstalar nada. Las reservas invitadas pueden depender del almacenamiento original.

## APKs Android que se conservan

Mapa QA R25.27 (firma e ID oficiales, reemplaza R25.25/R25.26):
https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/mapa-gps-reserva-20261017/preview-artifacts/Mapa_Trayectos_R25_27_Seguimiento_Privado_REVIEW.apk

Conductor QA R9 (firma e ID oficiales, reemplaza R8):
https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/conductor-r9-entrega-peticiones-20261017/preview-artifacts/Traslados_Conductor_R9_Reservas_Alertas_REVIEW.apk

No desinstalar las apps anteriores antes de actualizar; se preservan su SQLite, configuración, preferencias, historiales y credenciales Android compatibles. No realizar purga de puntos GPS ni borrar registros de reservas. No confundir builds `.debug`/`.datosreview` con APKs de actualización oficiales.

### Estado de integración

- Web/PWA v80 integrada en `main` y verificada por CI: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39053681933
- Publicación original de web/PWA v80: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39053729589
- Mapa R25.27 compilación/firma y privada GPS verificada en CI: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39048213989 ; PR de integración #113 draft.
- Conductor R9 alertas y precio fijo compilación/firma verificada: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39050193874 ; PR #114 draft.
- Las APKs son QA firmadas, **no aprobadas todavía como versiones estables** sin pruebas de Samsung y servicio durante toda la jornada. Android 15/16 restringe el polling prolongado en segundo plano: FCM/push real sigue siendo un trabajo pendiente.
- PR #111 de actualizar APK Cliente y antiguos flujos de instalación web→Cliente nativo quedan **obsoletos**.

## Continuidad de pruebas y publicación

Antes de cambiar la portada o la PWA, probar búsqueda de direcciones, alta de cuenta, reserva precio fijo, recepción/aceptación en Conductor, cambios de estado, GPS Mapa→Supabase→PWA, control de expiración de GPS, chat, carga/historial, instalación PWA y permisos Samsung. Las pruebas deben validar el código real de la web, sin APK WebView duplicada. Nunca imprimir PIN, secretos ni posiciones personales en logs públicos.

**Regla para sesiones futuras**: entregar página pública, URL de web/PWA, enlace de Mapa y enlace de Conductor. Nunca ofrecer APK Cliente.
