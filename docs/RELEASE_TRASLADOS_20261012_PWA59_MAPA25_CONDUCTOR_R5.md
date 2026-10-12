# Release Traslados — PWA v59 / Conductor R5 / Mapa R25.0

**12/10/2026 · Producción GitHub · Estado: publicación técnica confirmada, QA físico Samsung pendiente.**

## Versiones y descarga
| Componente | Versión | Build / distribución |
|---|---|---|
| PWA Pasajero | **v59** | https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ |
| APK Cliente Premium WebView | **v15** | Sigue usando el mismo origen PWA; paridad validada, no necesita recompilación por esta actualización |
| Traslados Conductor independiente | **11.5-R5**, versionCode **127** | https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-conductor-r5.apk |
| Mapa Trayectos con Conductor integrado | **0.1-R25.0**, versionCode **52** | https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r25.0.apk |

Hashes SHA-256 de las APK firmadas:
- Mapa R25.0: `56638ffdc29e33e89739c70d4f594327daa052081be352f477fc1ce86970c5ce`.
- Conductor R5: `f2bb320f57df426f10311d679866340274da826959b3ab767caed34ba65eb66f`.

**Firma de Android original verificada por apksigner**: SHA-256 del certificado `d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d`. No se publicaron APK DEBUG como actualizaciones. Los nombres de versiones anteriores son distintos y se conservan.

## Implementación / comprobación técnica
- PR #59: https://github.com/marcelofgx-ctrl/traslados-android/pull/59 — PWA v59: agenda estable, estados y paneles interiores persistentes, privacidad al cambiar de sesión, chat cerrado/reubicación del botón de salir lejos del globito, cache bust `?v=59` y SW `traslados-cliente-pwa-v59`.
- PR #60: https://github.com/marcelofgx-ctrl/traslados-android/pull/60 — Mapa/Conductor: acceder a conversación desde aviso de solicitud y centro rápido; mensajes predefinidos como borradores editables, chat actualiza cada 15 s mientras está abierto, red no destruye conversación, navegación segura por ID de reserva, modo solo lectura en finalizados, textos `null` corregidos, aviso compacto, versión Android real.
- PR #61: https://github.com/marcelofgx-ctrl/traslados-android/pull/61 — publicación de Conductor firmado en descarga directa.
- PWA validation (v59) https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38199635441 — SUCCESS.
- Cliente WebView parity https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38199635473 — SUCCESS.
- Mapa RELEASE, firma y publicación https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38199748134 — SUCCESS.
- Conductor RELEASE y publicación directa https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38199912343 — SUCCESS.
- GitHub Pages despliegue de ambas APK https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38199980706 — SUCCESS.
- Los artefactos `web-pasajero/downloads/mapa-trayectos-r25.0.apk` y `traslados-conductor-r5.apk` existen en `main`, junto a sus .txt SHA256. R24.8 y R24.9 siguen allí como respaldo.

## Prueba Samsung obligatoria
1. Guardar el enlace de la APK anterior como rollback. Instalar Mapa R25.0 **encima** de Mapa R24.9, sin desinstalar ni borrar datos; Conductor R5 solo si se usa su app independiente.
2. Abrir Chrome, actualizar PWA v59 y también abrir Cliente v15. Verificar que el botón de cerrar chat esté despejado del globo flotante. Dejar Historial + detalles abiertos >40 s: no cerrar ni desplazar por polling.
3. Con una reserva propia existente, abrir Centro rápido del globito Mapa → Traslados → reserva → ✉ Conversación. Confirmar que no llega a otra reserva y que no se envía nada automáticamente.
4. En Conductor probar respuesta predefinida (revisar borrador, corregir, **Enviar**) y recepción en Cliente. El flujo debe pasar por `driver_trip_chat_send_v1` y `customer_trip_chat_v1` existentes; sin mensajes ficticios de backend.
5. Verificar que un mensaje del pasajero produce notificación Android Conductor cuando el monitor está activo, y que al tocarla se abre la conversación exacta. No prometer push con servicio inactivo ni con Cliente cerrado: requiere proyecto FCM/Web Push.
6. Cerrar conversación o cambiar usuario y comprobar ausencia de mensajes ajenos; finalizar/cancelar solo con permiso y verificar solo lectura.
7. Probar teclado, desplazamientos, dos estados (en movimiento/detenido); bloque de modo conducción obliga detenerse para abrir desde Centro rápido.
8. Si la actualización APK falla por firma o versión, **no desinstalar** la app instalada; registrar error y versión en Ajustes > Aplicaciones. Los SHA256 descargados permiten comprobar integridad.

**Límite:** compilación/CI y publicación no sustituyen ensayo táctil en Samsung ni envío/recepción en dos extremos reales; se mantienen como pendientes hasta recibir evidencia.
