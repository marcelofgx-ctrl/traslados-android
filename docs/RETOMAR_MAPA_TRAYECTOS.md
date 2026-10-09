# MAPA TRAYECTOS — CONTEXTO DE REINICIO Y ENTREGA

**Fecha de corte:** 09/10/2026, versión R24.1 firmada y verificada, menú compacto y cierre sin jornada.  
**Documento principal para retomar otra sesión:** `docs/RETOMAR_MAPA_TRAYECTOS.md` en el repositorio.  
**Estado de entrega:** R24.1 compilada/firmada: conserva Conductor interno R24.0, reduce menú general, evita recorte del contador flotante y permite cerrar el mapa mediante pulsación prolongada si no hay jornada. No detiene el monitor de reservas. Pruebas reales Samsung y Supabase pendientes.

> **MENSAJE LISTO PARA PEGAR EN UNA NUEVA SESIÓN:**
>
> Quiero retomar mi proyecto **Mapa Trayectos**, aplicación Android nativa del repositorio GitHub `marcelofgx-ctrl/traslados-android`, rama `main`, módulo `mapatrayectos`. Antes de modificar nada, consultá `docs/RETOMAR_MAPA_TRAYECTOS.md`, `docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md`, `docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md`, `docs/mapa-trayectos-roadmap-2026-10-08.md`, los commits recientes y los runs de GitHub Actions. La última RELEASE verificada es **R24.1 / versionCode 43**, build `37918720480` SUCCESS, con menú ☰ compacto, globo flotante ligeramente mayor y badge de reservas sin recorte, y pulsación prolongada para cerrar la interfaz sin jornada sin apagar deliberadamente el monitor de reservas. Conductor sigue integrado desde R24.0; requiere QA Samsung. Conservá el diseño aprobado, las funciones de viajes, mis datos locales y las demás melodías. No confundas 'código implementado', 'compilación firmada' y 'probado en Samsung'. Continuá desde el último estado real de `main` y decime qué falta validar o corregir.

## 0. ÚLTIMA ENTREGA VERIFICADA — R24.1 (09/10/2026)

- **Solicitudes del usuario:** (a) menú general ☰ de R24.0 demasiado grande: reducir tamaño sin perder diseño petróleo/champagne, (b) globo flotante y su badge rojo de contador (ej. 2) necesitan margen para que cifra no se recorte, (c) al mantener pulsado el icono de la app cuando no hay jornada permitir cerrar aplicación. El usuario preguntó además si seguirían llegando push/avisos de reservas con app cerrada.
- **Criterio crucial:** R24.1 **NO** implementa Firebase Cloud Messaging/FCM (push remotos). El monitor real actual `uy.com.traslados.conductor.ReservationMonitorService` consulta Supabase aprox. cada 15 segundos y solo notifica mientras el servicio continúe funcionando. Cerrar la interfaz/burbuja debe **preservar** ese servicio cuando haya PIN. No prometer entrega si Android detiene dicho servicio, se fuerza detención del paquete, o se apaga dispositivo. FCM a implementar en una versión posterior, con tokens y eventos autorizados del backend, si se requiere garantizar mejor entrega con interfaz cerrada; incluso FCM no llega tras force-stop Android.
- **Código R24.1:** `MainActivity.showMainMenu` pasa de 262dp a 232dp y filas 57dp a 49dp, menos padding y tipografía apenas más chica, reanclado a ☰. `TrackingService.showBubbleIfAllowed` cambia ImageView de 52dp y badge pintado recortado a FrameLayout contenedor de 64dp con imagen circular de 56dp dentro y badge pintado en parent NO recortado (compuesto con margen seguro, hasta 9+). Persistencia separa posición ventana `window_x/window_y` de coordenadas reales de flecha `x/y`, usadas para recordatorios y Traslados.
- **Cierre:** mantener pulsada la burbuja flotante **800ms** sin arrastrar, haptic feedback, si `BubbleClosePolicy.mayClose` confirma ni `shift_active` ni `trip_active` según memoria del servicio y preferencias, se cierran burbuja y servicio Mapa (sin GPS) conservando monitor autenticado. Si hay jornada o viaje abierto, muestra mensaje de bloqueo y no cierra. También mantener pulsado icono amarillo ⌖ de la cabecera, pedir confirmación antes de cerrar la interfaz y su burbuja, sin recrearla en onPause. Monitor de Conductor no se fuerza a detener; `TransferAlerts.ensureMonitor` lo solicita si hay PIN válido. Sin PIN, no existe monitor.
- **Tests:** nuevo `BubbleClosePolicy.java` y `BubbleClosePolicyTest.java` con 6 escenarios, añadidos a CI junto a tests existentes de anclaje y política de burbuja. Build, sonido original MP3, conductor nativo, firma y empaquetado pasan GitHub Actions.
- **APK:** `0.1-R24.1` / `versionCode 43`, build **37918720480** **SUCCESS** https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37918720480 . SHA256 **59b72fb820c22fb4112a35b460f0c77c8976c05c5075fb972012855384033971**, **52.527.863 bytes**. Firma válida APK v2, certificado SHA256 **d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d**, compatible con R24.0. MP3 original de burbujas verificado dentro por Actions.
- **Drive verificado:** APK https://drive.google.com/file/d/1spEV5CniaJIm16xCIAjH6M8oDctae53w/view ; ZIP de comprobantes https://drive.google.com/file/d/1FA_ovCJMrNkR5mKcHMq42j8Svc4I3Bi3/view ; carpeta R24.1 https://drive.google.com/drive/folders/1jNh-H4wllPRhIPa8Q2DbcypNkEQknF2O .
- **QA SAMSUNG pendiente:** instalar APK encima de R24.0, nunca desinstalar. Verificar menú compacto sin superposición, cifra 2 y 9+ completas a ambos lados del globo, arrastrar y anclar flecha, mantener pulsado globito sin jornada para cerrar mapa, verificar que desaparezcan burbuja y notificación de mapa pero **siga activo el monitor de Traslados** (notificación «Monitor activo · esperando solicitudes»), recibir una reserva REAL nueva y ver notificación, abrir de nuevo el mapa. En segundo ensayo iniciar jornada y mantener pulsado: debe negar cierre sin afectar GPS. Probar también pulsación larga icono ⌖ de cabecera con confirmación. Android/Samsung puede terminar el monitor en segundo plano: NO garantizar avisos/push remotos sin FCM.
- **Backend y otros módulos:** no tocado Supabase, Conductor independiente, Cliente ni datos SQLite; no hay push FCM, solo polling de Conductor. No eliminar datos ni credenciales, no detener monitor sin solicitud explícita de desconexión.

## 0B. ENTREGA PREVIA VERIFICADA — R24.0 (09/10/2026)

- **Objetivo acordado:** Mapa Trayectos es la app principal y su mapa conserva diseño R23.1. Traslados **Conductor** pasa a un módulo nativo INTERNO de la misma APK, accesible desde menú general ☰. El menú de acciones rápidas ✦ sigue independiente. Traslados **Cliente** sigue siendo APK externa para pasajeros. La APK Conductor previa se conserva solo de respaldo durante la migración.
- **Integración completa del módulo Conductor existente:** Gradle `mapatrayectos/build.gradle` tarea `prepareEmbeddedConductor` reutiliza fuentes reales del directorio `conductor/src/main/java/uy/com/traslados/conductor/` en cada build, creando fuentes compilables en `mapatrayectos/build/generated/embeddedConductor/java/`; importa `uy.com.mapatrayectos.R` y copia recursos gráficos y WAV a directorios generados, sin alterar archivos de Conductor. Se compilan `uy.com.traslados.conductor.MainActivity`, `Api`, `ReservationMonitorService`, `TripTelemetryService`, `DemandMapActivity`. Todos residen en el mismo APK/package Android `uy.com.mapatrayectos`.
- **Interfaz Mapa:** cabecera icono ⚒ sustituido por **☰ menú principal** con Mapa, Traslados Conductor, Recordatorios, Historial, Mantenimiento. Se mantiene ✦ original para acciones rápidas y su estética. Badge rojo muestra solicitudes pendientes junto a ✦.
- **Notificaciones solicitudes:** nuevo `TransferAlerts.java` consulta datos reales de reservas a través de `ReservationMonitorService`, comparte total pendiente y avisos recién llegados mediante broadcasts con package restringido. Dentro del mapa presenta `ReminderCallout` parametrizado con encabezado NUEVO TRASLADO, DESPUÉS / VER PEDIDO, anclado físicamente a ✦. Fuera de la app usa la burbuja flotante única de Mapa y tarjeta anclada; puede fallar overlay por restricciones Android, pero el aviso nativo Android del monitor continúa siendo respaldo. Contador en burbuja flotante. Sonidos originales de Conductor y de Mapa preservados.
- **Autenticación:** al entrar por primera vez al módulo integrado el conductor debe proporcionar PIN una vez dentro de Mapa, porque cada package Android tiene sus propios SharedPreferences. Tras login, `TransferAlerts.ensureMonitor` mantiene monitoreo de nuevas reservas aun al abrir el mapa (puede haber restricciones de Android sobre FGS). El módulo sigue usando **Supabase real**; no se modificó esquema ni datos en el backend.
- **Continuidad de GPS:** se conserva servicio `TripTelemetryService` existente de Conductor y servicio GPS de Mapa por separado para no perder exportación GPX ni funcionalidades. **Pendiente de consolidar** en un solo motor y vincular etapas de reserva a jornada de Mapa cuando confirmemos funcionamiento físico. La función de ubicación en vivo para pasajero de Cliente todavía NO se implementó en R24.
- **CI y pruebas:** GitHub Actions build `37887653127` **SUCCESS** tras arreglo de generación Java; `Build signed release`, verificación nativa de clases/recursos, MP3 original, firma Android y artefacto subido, todo SUCCESS. APK incluye clases dex de Conductor, monitor, API, mapa de demanda y telemetría.
- **APK release:** `Mapa_Trayectos_v0.1_R24_0_RELEASE.apk`, **52.527.863 bytes**, SHA-256 **80aa8378cf1b946e9d16f895d7b83a5827c03513ca84f7b1663a22c2af07b9e8**; Android v2 signature valid; certificado SHA256 **d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d** (=R23.1). En GitHub Actions nombre artefacto `Mapa-Trayectos-v0.1-R24-0-RELEASE`, ID **11596718363**.
- **Drive backup:** ZIP firmado con APK, checksum, sonido y certificado https://drive.google.com/file/d/17TlWjNWmt-KMp_eX_cNy1tIjG_neKj2C/view ; carpeta de versión https://drive.google.com/drive/folders/10S1FZRNi4m8vHOAuFIHwKa4VVyczx5tJ . La APK individual está disponible como archivo descargable del chat.
- **QA SAMSUNG REAL pendiente:** instalar R24.0 encima de R23.1 **sin desinstalar**; preservar SQLite/sounds. Confirmar mapa idéntico; menú ☰ abre Conductor interno sin selector de apps ni instalación externa; reingresar PIN si solicita; comprobar backend real y reservas previas, filtros/agenda, presupuesto/aceptación/estado, historial. Minimizar con y sin jornada y confirmar solo burbuja de Mapa; cuando hay pedido nuevo revisar badge, sonido y aviso unido al globo; comprobar restricciones Android/sin duplicaciones y telemetría. No usar simultáneamente el monitor de la APK Conductor independiente durante pruebas.
- **Estado real:** código integrado, compilado, firmado y respaldado ✅; aplicación completa/conectividad/push sobre Samsung aún no confirmada. No marcar como producción probada hasta QA física.

## 0B. ENTREGA PREVIA R23.1 — 09/10/2026

- **Nuevo comportamiento:** al minimizar la aplicación sin jornada, aparece la burbuja de acceso flotante si Android concede permiso de superposición. No arranca GPS, no cuenta kilómetros ni inicia viajes. Con jornada activa mantiene el seguimiento. Al reabrir el mapa, historial o detalle, desaparece la burbuja. Su posición se actualiza cada 5 segundos para mantener anclados los recordatorios.
- **Implementación:** TrackingService usa `specialUse` en el servicio en primer plano cuando está inactivo, y `location` durante la jornada; permiso FOREGROUND_SERVICE_SPECIAL_USE y propiedad explicativa en AndroidManifest. Nuevo FloatingShortcutPolicy y prueba de 11 escenarios; ReminderAnchorGeometryTest continúa activo.
- **RELEASE:** `0.1-R23.1`, versionCode 41, commit `781e260e1dffbc8c61fdfd7615247945d8477c39`, CI GitHub Actions https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37881774310 — SUCCESS (pruebas, compilación, firma, MP3).
- **APK verificada:** 52.262.243 bytes; SHA-256 `3662ffc3ada0f2ae64a9b5b5bde424d907c7322ef360956bed06780013eb8332`; certificado de firma igual a R23.0. MP3 original intacto. No se modificaron datos, Supabase ni otros módulos.
- **Descarga Drive:** https://drive.google.com/file/d/1WLYfqWfyt-VDvdij_WSUc1MpR_0xTrsm/view ; ZIP https://drive.google.com/file/d/1Sczr1m1weZAKb0jMJZ1j4g6St0alxP-2/view ; carpeta https://drive.google.com/drive/folders/18QsCkg8UqirtLcaCwvy4g_SbJc0JnHmv .
- **PENDIENTE DE PRUEBA SAMSUNG:** instalar R23.1 encima de R23.0 sin desinstalar; abrir mapa con LISTO PARA JORNADA, verificar permiso ◎, minimizar con Inicio, comprobar burbuja y notificación permanente de acceso rápido, sin GPS activo; tocarla y volver al mapa. Repetir durante una jornada. Probar recordatorio en segundo plano y comprobar anclaje y MP3.
- **Límites:** Android puede detener el servicio por restricciones de batería o si se fuerza la detención; la disponibilidad real aún no fue probada en el teléfono.

## 0B. ANTERIOR ENTREGA VERIFICADA — R23.0 (09/10/2026)

- **Problema real observado:** el usuario adjuntó captura de Samsung donde el globo de recordatorio estaba separado del icono ✦ (tarjeta aparentemente huérfana sobre el mapa). Solicitó que la cola permanezca pegada al icono ✦ con la app abierta o a la burbuja flotante cuando está minimizada; si no hay ancla, dejar solo la notificación de Android.
- **Arreglo implementado en main:** nuevo `ReminderAnchorGeometry.java` ubica la tarjeta mediante la geometría real del icono, orienta cola a derecha o izquierda según espacio, ajusta altura de la punta y **no** acepta posiciones que dejarían la cola huérfana. `ReminderCallout.setAnchorPlacement` reubica/flipea el triángulo dentro de la misma vista. `MainActivity.inAppPlacement` usa `getLocationOnScreen` de raíz e icono para eliminar errores de sistema de coordenadas y realinea al cambiar layout.
- **Burbuja exterior:** `TrackingService` publica coordenadas actuales y eventos de arrastre/ocultamiento; `ReminderBubbleService` escucha, sigue el icono en movimiento, cambia orientación si está a la izquierda, detecta ancla obsoleta/no visible y omite/elimina la tarjeta. La notificación Android sigue disponible. Sin jornada activa (y por tanto sin burbuja flotante), no aparece una tarjeta huérfana.
- **Pruebas:** `mapatrayectos/tests/ReminderAnchorGeometryTest.java`, compilación Javac separada en Actions, PASS: primer plano derecha; overlay izquierda/derecha, movimiento de burbuja, ajuste de altura y ausencia de visual cuando no se puede unir la cola. Esto **NO** es una prueba de pixeles reales del Samsung.
- **APK RELEASE firmada:** `0.1-R23.0`, versionCode **40**, workflow `.github/workflows/build-mapa-trayectos.yml`, run **37880538141** SUCCESS: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37880538141 . Los intentos `37880347098` y `37880443648` fallaron por nombres de parámetros Java en lambda; fueron corregidos en commit `24e60a5e2cbd63c9fb639a04998efa6ed08cba75` y la build final pasó.
- **Archivo verificado:** `Mapa_Trayectos_v0.1_R23_0_RELEASE.apk` de **52.262.039 bytes**, SHA-256 `cad0847510e40799db13d162ebe605e22cc2d76387e733ba1584c6abf0cb7703`. Comprobación de firma Android esquema v2, certificado SHA-256 `d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d` (idéntico a versión anterior). MP3 original ElevenLabs dentro de APK intacto: SHA-256 `5cc7d452cb5f265bf6544486c52f3e8b57d3a292e77af38cbb5e3df0e9875bb0`.
- **Google Drive verificado:** APK https://drive.google.com/file/d/1HFX76Ldit7hKNRMrTbcLyE_8WgmI2V0C/view ; ZIP con firma, checksum y sonidos https://drive.google.com/file/d/1M3q2VB5DbgnaoOBao7RQHxwphqZVpauX/view ; carpeta https://drive.google.com/drive/folders/1LK_CGnXSNuotOFKRIGwimnpCDFgy6yrB .
- **QA de próxima conversación:** instalar encima de R22.9 **sin desinstalar** para conservar SQLite/MP3 privados. Probar recordatorio con app abierta, flecha tocando icono ✦ sin separación, botón HECHO y +10 min; salir con jornada activa para mostrar bubble externa, moverla a ambos lados, disparar alarma de prueba y comprobar cola unida. Al cerrar flotante o sin permiso overlay debe quedar solo notificación. Comprobar audio ElevenLabs y restauración de volumen de alarmas; todavía no hay confirmación del usuario.
- **Alcance:** solo módulo `mapatrayectos` y su workflow. No se tocó `conductor/`, `cliente/`, SQLite, Supabase, datos o estética de controles.

## 0B. ANTERIOR ENTREGA VERIFICADA — R22.9 (08/10/2026)

- **Reporte real Samsung R22.8:** notificación de recordatorio visible correctamente con app abierta o cerrada, pero SIN audio de burbujas. Por tanto, entrega de alertas sí probada por el usuario; audio audible NO.
- **Diagnóstico de código:** R22.8 no reproducía audio si teléfono estaba en silencio/vibración o notificaciones en volumen 0; MediaPlayer usaba `USAGE_NOTIFICATION_EVENT`. Su volumen 0.85 no elevaba el control físico del sistema.
- **Arreglo R22.9:** `ReminderSound.playBlocking` ahora usa `AudioAttributes.USAGE_ALARM`, `MediaPlayer.setVolume(1.0f,1.0f)` y `setWakeMode(PARTIAL_WAKE_LOCK)`. Antes de sonar sube solo `AudioManager.STREAM_ALARM` a **al menos 50%** si es inferior; al finalizar intenta restaurar el nivel anterior, sin pisar cambios manuales hechos mientras suena. `synchronized` evita cruces entre alarmas solapadas. Se agregó permiso normal `MODIFY_AUDIO_SETTINGS`. No modifica música, timbre, notificaciones, modo No molestar ni sonidos operativos de viajes.
- **Limitación:** Android puede negar subir el volumen o silenciar por No molestar/rutas Bluetooth; se registra diagnóstico sin prometer salida acústica. No hay confirmación de audio en Samsung todavía.
- **Código:** PR #6 fusionado, commit de merge `32fe456cbe2905dc2860445b11fb8459144adaaf`. `main`: `mapatrayectos/build.gradle` `versionCode 39`, `versionName 0.1-R22.9`.
- **Actions:** run `37872681083` COMPLETED/SUCCESS, `Mapa-Trayectos-v0.1-R22-9-RELEASE`. La firma v2 coincide con el certificado anterior (`d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d`).
- **APK:** `Mapa_Trayectos_v0.1_R22_9_RELEASE.apk`, 52.262.035 bytes, SHA-256 `6a7ba50023ac91df11110134dd217dd946213631f1661d3e9ce37c5f53a5ec69`. Dentro de la APK el MP3 sigue intacto, SHA-256 `5cc7d452cb5f265bf6544486c52f3e8b57d3a292e77af38cbb5e3df0e9875bb0`.
- **Drive verificado:** APK https://drive.google.com/file/d/1_OKMbJM5qNKVSjnocq4Et-5Lgzjvgy7q/view ; ZIP de artefacto con comprobantes https://drive.google.com/file/d/13Xz4HOxQTkrxX-dnEoaOLY3QcoxU1mQZ/view ; carpeta R22.9 https://drive.google.com/drive/folders/1e4_g4I0m4xFENE1sfa-tUrZaZ3f5OMoA.
- **QA al instalar:** instalar ENCIMA de versión previa sin borrar datos. Abrir ✦ > Recordatorios > PROBAR MP3 ORIGINAL DE BURBUJAS y escuchar el tono; comprobar diagnóstico y si restaura volumen de alarmas. Luego programar alerta de 1 minuto con app abierta y cerrada. Comprobar si alarma suena con modo normal y si se permite en silencio/vibración. No molestar puede restringir. No atribuir éxito acústico solo porque MediaPlayer terminó.

## 1. Fuentes de verdad y enlaces

- **Repositorio GitHub:** https://github.com/marcelofgx-ctrl/traslados-android
- **Rama:** `main`. Cada vez que se retome, leer su último commit: el código puede haber avanzado tras este documento.
- **Módulo Android:** `mapatrayectos/` (NO confundir con `conductor/`, que es otra aplicación).
- **Identificador de app:** `uy.com.mapatrayectos`.
- **Workflow de compilación firmada:** `.github/workflows/build-mapa-trayectos.yml`.
- **Historial acumulado:** `docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md`.
- **Contexto maestro histórico:** `docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md`.
- **Pendientes históricos:** `docs/mapa-trayectos-roadmap-2026-10-08.md`. Algunas tareas están marcadas antiguamente como no implementadas; contrastar siempre con `main` y este documento.
- **Última RELEASE verificada:** `0.1-R23.1`, `versionCode 41`, run de GitHub Actions **37881774310**, resultado `success`: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37881774310
- **APK R23.1 en Drive (verificada):** https://drive.google.com/file/d/1WLYfqWfyt-VDvdij_WSUc1MpR_0xTrsm/view
- **Carpeta Drive R23.1:** https://drive.google.com/drive/folders/18QsCkg8UqirtLcaCwvy4g_SbJc0JnHmv
- **Carpeta general Versiones en Drive:** https://drive.google.com/drive/folders/1eaXoeNaRl9Peb8a7pyrOFrTWqj-xyjd6
- **Web oficial del negocio que se comparte desde la app:** https://traslados-web.marcelof-gx.workers.dev/
- **Descarga web de la APK:** todavía NO hay ruta oficial validada. No inventar enlaces `/descargas` en la web de Cloudflare.

## 2. Estado verificado de R22.8

| Área | Estado técnico | Qué falta demostrar |
|---|---|---|
| Compilación Android nativa | R22.8 RELEASE compilada, firma Android verificada, SHA-256 APK comprobada | Instalación y comportamiento en Samsung |
| Audio recordatorios | MP3 original ElevenLabs incorporado en APK, identidad de bytes SHA-256 comprobada dentro del paquete | Que se escuche efectivamente al disparar una alarma en Samsung, con permisos y volumen correctos |
| Globo de recordatorio | `ReminderCallout` compacto (marfil, borde champagne, cola conectada, Hecho/+10 min), integrado en mapa y overlay externo opcional | Verificar posición exacta de la cola respecto del botón ✦ en distintas posiciones, primer/segundo plano |
| Tres tarjetas superiores del tablero inferior | Jornada, KM y Viajes, iconos dorados sol/carretera/automóvil (`ShiftSummaryIconView`) | Revisar proporción y legibilidad contra captura aprobada en Samsung |
| Estadísticas de jornada/viaje | Separadas, segundo a segundo, con pausa de jornada | Prueba con GPS real, jornada quieta y en movimiento, cierre/cancelación |
| Sonidos de viajes | Paquete de 13 MP3 originales importable una vez al teléfono, conservado al actualizar | Mantenerlos intactos; no mezclar con MP3 de recordatorios |
| Backup/reset | Limpieza de SQLite local con confirmación doble y copia ZIP opcional; nunca borrar Supabase sin autorización | Prueba controlada de backup/restore/confirmaciones sin afectar datos reales |

**Datos de la APK R22.8:** 52.262.003 bytes. El MP3 ElevenLabs dentro de la APK tiene 33.062 bytes y SHA-256 `5cc7d452cb5f265bf6544486c52f3e8b57d3a292e77af38cbb5e3df0e9875bb0`. Android lo empaquetó bajo un nombre optimizado, `res/YZ.mp3`; no codificar su nombre interno en lógica de reproducción: se usa `R.raw.reminder_bubbles_elevenlabs`. La compilación firmada de GitHub verificó el MP3 por tamaño y SHA dentro del ZIP APK. El run era `success`. No hace falta importar otro ZIP para los recordatorios.

**Firma:** las releases recientes usan el mismo certificado de firma de Android; comprobar en cada nueva versión por `apksigner`, nunca hacer instalaciones que obliguen a desinstalar y perder datos.

## 3. Diseño visual obligatorio (no reinterpretar)

El usuario aprobó expresamente una **captura definitiva R22.6**: mapa MapLibre de tonos claros; cabecera petróleo con bordes dorados, botones «SEGUIR», «HIST.», iconos discretos y acceso Uber; brújula/GPS/velocímetro; botón circular ✦ a la derecha; panel inferior premium petróleo/champagne con texturas suaves y deslizadores. **No generar nuevos bocetos ni cambiar la composición sin solicitud explícita**.

Elementos concretos aprobados:

1. **Tres tarjetas en una sola fila del tablero:** `JORNADA` (sol dorado), `KM` (carretera dorada), `VIAJES` (automóvil dorado). Icono a la izquierda, título pequeño y cifra grande a la derecha. Cuando existe un viaje, esta franja resume la jornada completa.
2. **Fila de cuatro métricas operativas** durante un viaje: KM del servicio, duración del servicio, tiempo en movimiento, tiempo detenido, en `HH:MM:SS` para relojes.
3. **Deslizador premium** Uber/Cabify/particular con estados reales de viaje: hacia recogida, pasajero recogido, viaje en curso, parada intermedia, continuar, finalizar o cancelar. Conservar identidad de plataforma, colores y háptica, sin rediseñar el flujo de manera arbitraria.
4. **Recordatorio tipo viñeta** marfil con emblema, título y botones «+10 MIN» y «HECHO». Cola triangular físicamente conectada al botón circular ✦ en primer plano; pequeña transición suave al aparecer/desaparecer. Usar el componente `ReminderCallout` y las coordenadas del botón `quickActionsAnchor`, no dos ventanas no relacionadas. Fuera de la app: superposición si hay permiso, notificación Android en todo caso.
5. La identidad del diseño se basa en una captura enviada por el usuario en la conversación anterior. Pedirla nuevamente solo si se necesita comparación visual exacta y no está disponible en el nuevo contexto; **no inventar** otra referencia gráfica.

## 4. Arquitectura y estado de funciones

**Componentes clave:**

- `MainActivity.java`: mapa, cabecera, tablero, tarjeta Jornada/KM/Viajes, métricas, estado de viaje, botón ✦, `ReminderCallout` en primer plano.
- `TrackingService.java`: GPS, tiempos de jornada/trip, pausa/reanudación, seguimiento y notificación de estado.
- `TrackDb.java`: SQLite local, actualmente `DB_VERSION=5`; viajes, jornadas, paradas, recogidas y métricas; contar viajes **completados**, no cancelados.
- `TripFlowDialogs.java` / `TripDetailActivity.java` / `HistoryActivity.java`: transición entre etapas, datos e historial; no perder navegación, distinción de tipos de viajes o trazas.
- `QuickActionsMenu.java`: tarjeta premium con QR, WhatsApp a número, guardar pasajero en Agenda mediante confirmación nativa, recordatorios, enlaces. `BOOKING_URL` es la web oficial Cloudflare indicada arriba. `DOWNLOAD_URL` provisional es distinto: **no asumir que funciona**.
- `BusinessCardImage.java`: PNG de contacto premium, QR al WhatsApp del conductor; compartir PNG por WhatsApp es distinto de abrir chat a número no agendado; VCF alternativo.
- `ReminderActivity.java` / `ReminderStore.java` / `ReminderReceiver.java` / `ReminderActionReceiver.java` / `ReminderBootReceiver.java`: editor tipo chat, alarma/confirmación de fecha-hora, prueba 1 minuto, diagnóstico de permisos, recepción, posponer/completar, rearmar al reinicio.
- `ReminderCallout.java` / `ReminderBubbleService.java`: globo flotante asociado al botón ✦. `ReminderSound.java`: MP3 exacto para recordatorios.
- `SoundPack.java` / `FeedbackReceiver.java`: sonidos para acciones de viajes y notificaciones operativas; separados del MP3 ElevenLabs de recordatorios.
- `Api.java`: Supabase existente. **No** se ha certificado sincronización exhaustiva de paradas/puntos de recogida del SQLite al servidor; no borrado remoto ni migraciones sin autorización.
- `DatabaseBackup.java`: ZIP de seguridad local, import/export; la acción «Empezar de cero» es para datos **locales** y requiere confirmaciones. Recordatorios se guardan por separado y no deben borrarse accidentalmente.

**Identidad comercial y compartición:** contacto del conductor *Marcelo Fernández*, teléfono `+598 97 228 175`, tarjeta con diseño logo opción C, QR a `https://wa.me/59897228175`. Web oficial Cloudflare arriba. No mezclar con proyecto «Traslados Conductor» del mismo repositorio.

**Datos y métricas:** Jornada activa: KM totales, tiempo circulando, tiempo detenido y total efectivo (circulación + detenido, sin pausas). Durante servicio: KM y duración del servicio, movimiento y detenido de ese servicio, y resumen continuo de jornada. **Jornada** no significa necesariamente «todo un día calendario»: dos turnos en el mismo día todavía requieren consolidación por fecha en el historial. La integridad del conteo real se prueba en teléfono; no inferirla de una captura.

## 5. Sonidos y recordatorios: diferencia fundamental

- **Sonidos de acciones/viajes:** una biblioteca de 13 audios cargados en R22.2 y mantenidos en almacenamiento privado del teléfono. Ya fueron importados en el Samsung y el usuario confirmó que las melodías estaban funcionando. No desinstalar ni borrar datos por cambiar APK.
- **MP3 especial de recordatorios R22.8:** archivo aportado directamente por el usuario en formato MP3 («Pequeñas burbujas de jabón...», ElevenLabs), alojado en el repositorio como `mapatrayectos/assets/reminder_bubbles_elevenlabs.mp3.base64`. El workflow decodifica a `src/main/res/raw/reminder_bubbles_elevenlabs.mp3` para cada build. `ReminderSound` usa `MediaPlayer`, espera finalización sin bloquear el hilo principal y registra resultado en preferencias `reminder_audio_diagnostics`.
- Canal de notificaciones de Android `mapa_personal_reminders_v3_bubbles` sin tono extra de sistema; no duplicar el pitido «pip pip». Si el móvil está en silencio/vibración o volumen notificaciones cero, el reproductor lo señala y no reproduce contra preferencias.
- La app debe avisar también fuera de pantalla con notificación; el overlay requiere permisos Samsung/Android y puede estar restringido. **No afirmar que se oyó** el MP3 en el Samsung: aún no hay feedback final de prueba de R22.8.

**Prueba guiada después de instalar R22.8:**

1. Abrir menú ✦ -> Recordatorios -> `PROBAR MP3 ORIGINAL DE BURBUJAS`. Escuchar con el volumen de notificaciones activado, dispositivo no silenciado.
2. Leer cuadro de diagnóstico del sonido y permisos de notificaciones y alarmas exactas.
3. Programar «prueba en 1 minuto» y salir de la app. Comprobar recepción Android y audio. Probar con app al frente y en segundo plano.
4. Verificar piquito unido a ✦ y acciones «HECHO» / «+10 MIN», incluso superposición si hay permiso.
5. Verificar que los otros sonidos de viaje siguen inalterados.

## 6. Pendientes para siguientes versiones — prioridad, no promesas

**P0 - QA real en Samsung:** reproducción audible MP3 R22.8, entrega fiable de alarmas y globito, funcionamiento cuando pantalla bloqueada, permisos de batería/alarmas y que la franja premium no tape mapa ni botones. Si falla, recolectar captura/registro, no suponer causa.

**P1 - Diseño y contadores:** validar tarjetas JORNADA/KM/VIAJES exactamente contra la captura aprobada, tamaños de fuente, métricas al pausar jornada e iniciar/cerrar viaje, conteo completados/cancelados. Verificar que los relojes actualicen cada segundo y que la pausa no sume DET.

**P2 - Backup/historial:** probar ZIP de exportación y restauración, acciones de limpieza de base en datos de prueba; sumar varias jornadas del mismo día de calendario si se solicita.

**P3 - Supabase y distribución:** revisar persistencia rica de `pickup` y `trip_stops` en servidor, respetando contratos/RPC y permisos; definir página oficial de descarga APK en web Cloudflare, con enlace validado. Considerar menú de sonido personalizable solo si usuario lo pide después; el archivo ElevenLabs ya está embebido.

**P4 - Audio de mensajería interna:** el usuario propuso que el globito ✦ hable para mensajes relevantes, confirmaciones y recordatorios; **no afirmar que todas esas alertas internas estén implementadas**. Requiere definir un router de eventos con prioridades para no producir mensajes excesivos.

## 7. Procedimiento obligatorio para trabajar otra vez

1. Usar herramientas de GitHub conectadas y leer el **commit actual de `main`**, este contexto, historial y archivos del módulo. Las versiones consignadas son **punto de corte**, no permiso para ignorar avances posteriores.
2. Revisar estado de los GitHub Actions más recientes. R22.8 corresponde a `37870800270`, último build exitoso comprobado al cierre. Comprobar si hay releases posteriores y darles prioridad si existen.
3. Cambiar **solo** módulo `mapatrayectos` y sus recursos/workflow, salvo instrucción explícita. Evitar modificar `conductor/` y otros proyectos del repositorio.
4. Mantener visual aprobado (petróleo, champagne, tarjetas con iconos dorados, globo con flecha y deslizadores); nunca sustituirlo por mockups nuevos sin permiso.
5. Para cada incremento: actualizar `versionCode`, `versionName`, verificaciones del workflow y nombre del artefacto. En `main`, los checks `grep` suelen quedar obsoletos y provocar falsos fallos; revisarlos.
6. Lanzar Actions y **esperar run verde**. Inspeccionar `SIGNING.txt`, `SHA256SUMS.txt`, existencia y tamaño del APK. Si se modifica audio, confirmar los bytes **dentro** de la APK, no solamente en fuente. En R22.8 el recurso está bajo `res/YZ.mp3` por optimización AAPT, no `res/raw/...`.
7. Subir instalador a Drive en `Mapa Trayectos/Versiones/RXX.X` y comprobar enlace/tamaño. La cuenta usada es `marcelof.gx@gmail.com`. Si subida falla, usar el artefacto verificado de GitHub y explicar el inconveniente; **nunca inventar un enlace de Drive**.
8. Separar claramente: **implementado en código / compilado y firmado / probado en teléfono**. Nunca prometer resultado físico antes de recibir pruebas del Samsung. No borrar SQLite, SharedPreferences ni Supabase sin autorización.
9. Actualizar este documento, el contexto maestro (cabecera) y el historial al cerrar cada entrega. Cada sesión debe poder retomarse desde GitHub.

## 8. Cómo retomar sin arrastrar toda la conversación

**Desde otra sesión**: enviar solamente el mensaje del recuadro al comienzo del documento o escribir:

> «Retomá Mapa Trayectos desde `marcelofgx-ctrl/traslados-android`, rama `main`. Leé primero `docs/RETOMAR_MAPA_TRAYECTOS.md` y el último historial de versiones. Estoy en R22.8; comprobá si hay algo más reciente y seguí desde ahí. No cambies el diseño aprobado ni otros módulos».

**Si GitHub no está conectado en la nueva sesión:** conectar GitHub o subir este archivo Markdown/PDF. Se puede retomar con el documento, pero para modificar y compilar el proyecto se necesita acceso efectivo al repositorio.

---

**Criterio final:** GitHub es la fuente de verdad del código; este archivo contiene el contexto vivo y límites de diseño; el historial describe versiones; Drive archiva APK instalables. Ninguno de estos medios demuestra por sí solo que el audio o las alarmas funcionen en un teléfono real.