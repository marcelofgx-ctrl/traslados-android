# MAPA TRAYECTOS — CONTEXTO DE REINICIO Y ENTREGA

**Fecha de corte:** 08/10/2026 (compilación R22.8 verificada al cierre de la conversación).  
**Documento principal para retomar otra sesión:** `docs/RETOMAR_MAPA_TRAYECTOS.md` en el repositorio.  
**Estado de entrega:** Código y compilación R22.8 verificados; varios comportamientos requieren prueba en Samsung.

> **MENSAJE LISTO PARA PEGAR EN UNA NUEVA SESIÓN:**
>
> Quiero retomar mi proyecto **Mapa Trayectos**, aplicación Android nativa del repositorio GitHub `marcelofgx-ctrl/traslados-android`, rama `main`, módulo `mapatrayectos`. Antes de modificar nada, consultá `docs/RETOMAR_MAPA_TRAYECTOS.md`, `docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md`, `docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md`, `docs/mapa-trayectos-roadmap-2026-10-08.md`, los commits recientes y los runs de GitHub Actions. La última RELEASE verificada es **R22.8 / versionCode 38**, build `37870800270`, que incluye mi MP3 ElevenLabs de burbujas para recordatorios. Conservá el diseño aprobado, las funciones de viajes, mis datos locales y las demás melodías. No confundas 'código implementado', 'compilación firmada' y 'probado en Samsung'. Continuá desde el último estado real de `main` y decime qué falta validar o corregir.

## 1. Fuentes de verdad y enlaces

- **Repositorio GitHub:** https://github.com/marcelofgx-ctrl/traslados-android
- **Rama:** `main`. Cada vez que se retome, leer su último commit: el código puede haber avanzado tras este documento.
- **Módulo Android:** `mapatrayectos/` (NO confundir con `conductor/`, que es otra aplicación).
- **Identificador de app:** `uy.com.mapatrayectos`.
- **Workflow de compilación firmada:** `.github/workflows/build-mapa-trayectos.yml`.
- **Historial acumulado:** `docs/HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md`.
- **Contexto maestro histórico:** `docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md`.
- **Pendientes históricos:** `docs/mapa-trayectos-roadmap-2026-10-08.md`. Algunas tareas están marcadas antiguamente como no implementadas; contrastar siempre con `main` y este documento.
- **Última RELEASE verificada:** `0.1-R22.8`, `versionCode 38`, run de GitHub Actions **37870800270**, resultado `success`: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37870800270
- **APK en Drive (verificada):** https://drive.google.com/file/d/1px_rPZu9iSP8g9keQsHgoBYwLPKy5z7k/view
- **Carpeta Drive R22.8:** https://drive.google.com/drive/folders/1DD3cgIArfpskQDUfFPb6Acv50BUQeuwj
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