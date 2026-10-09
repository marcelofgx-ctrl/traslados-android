# Mapa Trayectos — historial de versiones verificable
Estado a 09/10/2026 (R24.0). Mantener actualizado tras cada build y prueba de dispositivo.

| Versión | versionCode | Objetivo | Compilación | Prueba real |
|---|---:|---|---|---|
| R20 | 23 | Pulido premium petróleo/champagne, mapa y slider dual | Éxito, run 37719198104 | Usuario aprobó estética |
| R21 | 24 | Menú acciones rápidas y VCF; intento integración MP3 | Éxito, run 37775602458 | Menú abrupto, tarjeta WhatsApp fallaba |
| R21.1 | 25 | Menú compacto animado, chat por número y VCF separados | Éxito, run 37777016700 | Sin verificación completa |
| R21.2 | 26 | Tarjeta PNG con QR y vista previa | Éxito, run 37779090819 | Compartido necesita mejor UX |
| R21.3 | 27 | Logo C y foto incrustada en VCF | Éxito, run 37782391832 | WhatsApp no garantiza foto en burbuja |
| R21.4 | 28 | Abrir WhatsApp sin agendar; Cancelar último | Éxito, run 37785028964 | Usuario rechazó mensaje genérico; necesitaba tarjeta real |
| R21.5 | 29 | Tarjeta visual premium con logo C, chat a número no agendado, guardar pasajero con empresa opcional y enlaces confirmables | Éxito, run 37789496860 (firma APK v2; SHA256 9cef77c7dc1fbfe3b7a95f835fe8a2b1c0d146c76618e49b2b91c3ba9264b3b8) | PENDIENTE DE PRUEBA EN SAMSUNG |

| R22 | 30 | Recordatorios locales tipo chat, alertas Android, vaciar base SQLite tras backup ZIP opcional y doble confirmación, pausar/reanudar jornada con pause_ms, burbuja premium 52dp, corregido fallback Cabify | Éxito GitHub Actions run 37795345311, APK firmada SHA-256 45384acec978cd1389a5526e26fbf454fd08765cfdc30fbd8e7690bebc543509 | Pendiente pruebas en Samsung |
| R22.1 | 31 | Recupera los cinco MP3 originales subidos al chat, crea paquete ZIP inalterado, importación segura una vez en Mantenimiento con SHA-256 y reproducción local desde almacenamiento privado | Éxito, run 37797357725; SHA-256 APK 4d9aa3e4526ffa1fa6ffd9b349022fc88466a9151b4cf173733713bab6fa6a92; firma anterior idéntica | PENDIENTE IMPORTACIÓN EN SAMSUNG |

| R22.2 | 32 | Completar 13 sonidos originales; pruebas individualizadas de cada evento; pausa/reanudación, Cabify, botones, cierre; reproductor con referencia viva y registro de fallos | Éxito, run 37812971829, APK SHA-256 ca7095fab62d3e6ccb5ec33d2b4bd68dae09e1f044688f35aae55dafd8cd232d | Prueba en Samsung pendiente |

| R22.3 | 33 | Recordatorios Samsung: interpreta HH:mm sin «hoy», alarmas exactas con permiso, diagnósticos, prueba 1 minuto, notificación alta prioridad y globo overlay opcional con cola triangular | BUILD RELEASE EXITOSO: run 37817234425; SHA256 9edcd9c09c0f526d2149ddac6073b0b54702b3b90b4fb8d8fadfe282f7187dcb; firma v2 original | PENDIENTE DE PRUEBA EN SAMSUNG |

| R22.4 | 34 | Tablero jornada/servicio independiente. Indicadores jornada KM, CONDUC., DET., VIAJES completados; franja TIEMPO EFECTIVO hh:mm:ss; durante viaje KM, SERVICIO, MOV., DET. y franja jornada continua | GitHub Actions 37831895030 SUCCESS, APK firmada SHA-256 9de9af02e64b94f84d7c813a42c63243a63957204e4fad5b66dc9a1e8a33653a | Comportamiento en Samsung pendiente |

## Notas importantes
- Todos los builds deben preservar el package uy.com.mapatrayectos y firma RELEASE previa para instalar encima.
- R21.5 está compilada y firmada. La apertura de WhatsApp, previsualización de imagen, inserción en Agenda y confirmación de URLs siguen sin prueba confirmada en Samsung.
- El código y un workflow exitoso NO verifican el envío real dentro de WhatsApp: queda sujeto a prueba en Samsung.
- Los recursos MP3 reales no fueron integrados: Pixabay devuelve 403 en GitHub CI; chimes de respaldo siguen.
- Las nuevas funciones de recordatorios y vaciar base con backup son ROADMAP, no R21.5.

## Pendientes bloqueados o no incluidos en R22
- Sonidos MP3 originales: la descarga de Pixabay devuelve 403 al automatizar; la app sigue con chimes sintetizados. No se ha seleccionado un sonido distintivo para Cabify o pausa.
- Sincronización completa de pickup/trip_stops en Supabase: requiere ampliar esquema/RPC remoto y validar permisos, no se incorporó a R22.
- El historial guarda campos de pausa pero falta un informe consolidado visual de horas laborales netas en Historial.
- La burbuja exterior recibió pulido visual, pero no se agregó el mini panel informativo flotante propuesto.
- Recordatorios dependen de permisos de notificaciones y del sistema de alarmas exactas; probar en Samsung.
- El borrado de R22 afecta solo SQLite local, nunca datos remotos en Supabase. No se realizó ningún borrado real en el teléfono del usuario.

## Paquete de identidad sonora R22.1
- Se localizaron en ChatGPT Library cinco MP3 que el usuario había subido: 607923 jornada, 443093 inicio de viaje genérico, 158193 Uber, 383749 cancelación, 607920 fin de viaje. Archivos originales intactos, sin compresión adicional, tamaños 80–257 KB.
- Se creó ZIP distribuido junto a la APK con nombres de recurso `sound_shift_start.mp3`, `sound_trip_start.mp3`, `sound_uber.mp3`, `sound_cancel.mp3`, `sound_trip_end.mp3`. Los bytes tienen hashes SHA-256 autenticados en SoundPack.java.
- Android R22.1 permite Mantenimiento → Sonidos originales → Instalar ZIP: elige una vez el ZIP mediante ACTION_OPEN_DOCUMENT. La app valida y copia los cinco MP3 en almacenamiento privado, persistentes al actualizar. Si el usuario no importa el pack, continúa con los chimes de R22.
- El APK por sí solo NO incluye los MP3 originales porque GitHub Actions no puede leer directamente la biblioteca privada de ChatGPT; se suministra audio ZIP complementario, evitando totalmente Pixabay.
- No se duplica el audio de Uber para Cabify ni se reasigna cancelación a pausa.
- Siguiente paso si se habilita transferencia binaria automatizada a GitHub: incluir los originales en src/main/res/raw para que el APK único no requiera la importación ZIP.

## Incidencia R22.1 — alternancia de MP3 y chimes (reporte de usuario 08/10/2026)
- El usuario instaló R22.1 e importó el ZIP; percibe unos eventos con MP3 y otros con «pip pip» sintetizados, y posible inconsistencia entre repeticiones.
- Verificado en `FeedbackReceiver.java`: solo tienen MP3 asignados SHIFT_START, SERVICE_START (genérico/Uber), TRIP_END (completado/cancelado). PICKUP, STOP_START, STOP_END, SHIFT_END reproducen chime sintetizado por diseño. SERVICE_START con Cabify busca sound_cabify no seleccionado/no incluido, luego chime; NO se reutiliza sound_uber.
- `playSelectedSound` cae a `playChime` si `SoundPack.play` devuelve false o si falla el MediaPlayer. Esto puede explicar el alternado en el MISMO evento, pero falta instrumentación y prueba de teléfono para confirmarlo.
- Corregir en próxima iteración tras acuerdo: menú de diagnóstico que pruebe los cinco MP3 individualmente, estado/errores sin silencios, mapping visible evento→audio, fallback distinguible, registro y protección de recursos del reproductor.
- No reasignar archivos elegidos a recogida, parada, Cabify ni fin de jornada sin consentimiento. MP3 de cancelación debe dispararse por trip_status=="cancelled".
- Estado: diagnóstico de código confirmado, corrección NO IMPLEMENTADA en R22.1; verificar comportamiento real en Samsung.


## Identidad sonora R22.2 — asignación completa aprobada a criterio del desarrollador
Se conservaron sin cambio los cinco audios elegidos por usuario. Se añadieron estos ocho:
- Inicio de jornada: 607923 (anterior)
- Iniciar viaje personal: 443093 (anterior)
- Iniciar viaje Uber: 158193 (anterior)
- Cancelación de viaje: 383749 (anterior)
- Finalizar viaje: 607920 (anterior)
- **Iniciar Cabify:** 494546
- **Recoger pasajero:** 124467
- **Iniciar parada:** 480571
- **Reanudar tras parada:** 485901
- **Finalizar jornada:** 580715
- **Pausar jornada:** 480567
- **Reanudar jornada:** 376885
- **Menú acciones rápidas (toque deliberado):** 323602; no reproducir un click por cada actualización GPS.
Los duplicados de Library no cuentan como audios diferentes. Se ignoró un MP3 ajeno al proyecto.

Estado: 13 archivos íntegros en `Mapa_Trayectos_R22_2_Sonidos_13_Originales.zip`, SHA256 del ZIP `76afe5a5d0d06bbc719b4c6a2a95cd1a208449e5adda0574e72c4a91bba0df0b`.
La APK firmada no contiene los 13 MP3: importar este ZIP desde Mantenimiento → Sonidos originales → INSTALAR ZIP después de actualizar APK. 
El viejo paquete de 5 sonidos continúa siendo reconocible en la app (5/13). Los 13 son necesarios para eliminar los antiguos chimes en todas las transiciones.

Reproductor R22.2: una instancia fuerte de MediaPlayer para todo audio, evita superposiciones y guarda último error; con 13/13 importados no debe sonar pip-pip por fallback silencioso. Se agregó panel con fila PROBAR/PENDIENTE de los 13 eventos. 
Fuera del dispositivo no puede certificarse la reproducción real.


### Entrega R22.2 en Google Drive (verificada)
- Cuenta: marcelof.gx@gmail.com.
- Carpeta: https://drive.google.com/drive/folders/105ACyatGtj239RA3avgHFlp2cCl3_8eI
- APK firmado 52.212.183 bytes: https://drive.google.com/file/d/1nwBpGxAyCwgJ8R2IT2Jx_ReNBzZz9Zw9/view
- ZIP 13 originales 1.712.154 bytes: https://drive.google.com/file/d/1bBXYtcE3UNlfFC5BXsX5_rD65AxdpQgK/view
- GitHub Actions R22.2: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37812971829
- Instalación: actualizar APK sobre R22.1, ir a Mantenimiento → Sonidos originales → INSTALAR ZIP, elegir el nuevo paquete de 13 (no el viejo de cinco), comprobar 13/13 y usar PROBAR en cada evento.
- El ZIP está fuera del instalador por limitación de transferencia binaria hacia GitHub; no confundir compilación verificada con sonidos probados en Samsung.

## Incidencia R22.3 — Samsung recordatorio sin aviso
- Usuario programó alerta sobre las 14:20 en R22.2 y no vio ninguna notificación. No tenemos acceso remoto al SharedPreferences del Samsung; no inventar confirmaciones de alarma disparada.
- Causa de código confirmada: ReminderActivity preseleccionaba «mañana a las 10:00» y solo parseaba HH:mm cuando venía acompañado de «hoy/mañana»; escribir solo «14:20» dejaba la fecha predeterminada.
- Otra causa confirmada: ReminderReceiver llamaba markFired incluso si ReminderStore.alert retornaba sin emitir notificación porque POST_NOTIFICATIONS estaba denegado. Este evento podía quedar consumido sin mostrarse.
- R22.3 corrige fecha y hora, añade prueba exacta de 65 segundos y un diagnóstico visual de notificaciones, alarma exacta y overlay.
- R22.3 notificación Android nueva categoría alta con Hecho y +10 min; popup estilo globo de historieta (cola triangular/ícono, posponer y hecho) mediante ReminderBubbleService shortService si Android deja crear superposición.
- Android 16 y Samsung pueden bloquear permisos o demorar alarmas sin SCHEDULE_EXACT_ALARM. Overlay no se promete por encima de todas las apps sin autorización. Fallback es notificación Android.
- Recordatorios persistidos en SharedPreferences siguen intactos al actualizar de R22.2 a R22.3.
- Validar dispositivo obligatoriamente: activar notificaciones y Alarmas y recordatorios, habilitar «Mostrar sobre otras aplicaciones» para globo, hacer «PROBAR ALERTA EN 1 MINUTO», dejar la app y bloquear pantalla, comprobar globo o aviso; probar «HECHO» y «+10 MIN» y otra alerta en hora real.
- Estado R22.3: compilación pendiente o en curso; no marcar funcionalidad verificada en Samsung hasta feedback real.

## Entrega R22.3
- GitHub Actions RELEASE firmada: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37817234425
- Artifact Mapa-Trayectos-v0.1-R22-3-RELEASE. Archivo: Mapa_Trayectos_v0.1_R22_3_RELEASE.apk.
- El vínculo de descarga ChatGPT de sesión se crea a partir del ZIP de GitHub; no guardar sandbox paths como persistentes en documento maestro.
- Se intentó crear carpeta de Drive bajo Versiones para R22.3, pero el conector de Google Drive bloqueó la operación. No está verificada una subida a Drive de R22.3.
- Requiere permisos de notificaciones, Alarmas y recordatorios (exactas) y sobre otras aplicaciones para el globo; no vender como resultado probado sin prueba de teléfono.


## Entrega R22.4 (08/10/2026)
- Release compilado y firma verificada en https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37831895030
- Artefacto GitHub Actions id 11574142808 (ZIP de build), APK 52,212,275 bytes, SHA-256 9de9af02e64b94f84d7c813a42c63243a63957204e4fad5b66dc9a1e8a33653a.
- Carpeta Google Drive creada: https://drive.google.com/drive/folders/1W1xWEkoRasrgw7kGTkmp_npYxhRf4JHQ
- **IMPORTANTE**: Transferencia de APK a Google Drive no completada porque la integración devolvió bloqueo; no aseverar que el archivo esté en esa carpeta. APK disponible como adjunto de conversación si el enlace sandbox es accesible y GitHub Actions ZIP como respaldo.
- Durante jornada SIN viaje se muestran: KM jornada, CONDUC. shiftMoving, DET. shiftStopped, VIAJES shiftCompletedTrips; franja TIEMPO EFECTIVO shiftMoving+shiftStopped. Durante viaje: KM de viaje, duración desde tripStarted, movimiento y detenido del viaje; franja con jornada continua.
- Contador de viajes se calcula en SQLite local por shift_id, ended_at_ms y trip_status completed. Cancelados excluidos.
- FORMATO HH:MM:SS e icono especializado VIAJES; mid sheet 272dp, full 332dp.
- Sin modificación de esquema SQL ni eliminación de datos; firma consistente con las RELEASE anteriores.
- Pruebas en teléfono pendientes. Sumar jornadas del mismo día calendario en Historial no implementado; no confundir 'jornada' con 'día'.


## R22.6 — Implementación del diseño definitivo aprobado (08/10/2026)
- Fuente: imagen aprobada por usuario 1000359867.png, **sin generar dibujos nuevos ni reinterpretar la composición**; nativo Android package `uy.com.mapatrayectos`.
- Código commit `6ac665219901bc20124a744af4f91698bbd77160`, versionCode 36 / versionName 0.1-R22.6. Compilación RELEASE GitHub Actions run 37850519100: build y apksigner verify exitosos.
- APK `Mapa_Trayectos_R22_6_RELEASE.apk`, 52 228 659 bytes. SHA256 `55d910d30cde87d0a0bbb81dc333f2616fb2118199cb50846c75c668617f7de4`. Certificado SHA256 `d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d` (mismo certificado de versiones previas).
- Tablero premium: las tres tarjetas Jornada, KM y Viajes conservan posición horizontal, espaciados petróleo/champagne y cifras; iconos Android Canvas `ShiftSummaryIconView` SUN, ROAD, CAR a la izquierda; cifra y etiqueta apiladas a la derecha. Mantiene las cuatro métricas de servicio y sliders nativos.
- Globito R22.6: `ReminderCallout` reutilizable, carita/emblema, globo marfil/contorno oro y flecha lateral triangular unida al mismo View; en aplicación MainActivity recibe el recordatorio y añade `ReminderCallout` a su FrameLayout alineado a coordenadas **reales** de botón ✦. Animación fade/slide 330 ms; acciones HECHO/+10 MIN; cierre tras 30s (la notificación persiste).
- Fuera de la app: `ReminderBubbleService` usa el mismo callout; si Android bloquea overlay se conserva la notificación del sistema. Si la burbuja flotante está muy a la izquierda la ventana debe sujetarse a los bordes, por lo que el triángulo puede no llegar físicamente al botón, limitación pendiente del modo externo.
- Sonido: síntesis local de 3 burbujas discretas con `AudioTrack` estático 24 kHz PCM; `ReminderReceiver.goAsync` conserva la ejecución ~0.92s para terminar reproducción; canal de alta prioridad nuevo `mapa_personal_reminders_v3_bubbles` sin segundo pitido Android. Estado del volumen/silencio y resultado AudioTrack en `reminder_audio_diagnostics`; prueba **PROBAR SONIDO DE BURBUJAS** en pantalla Recordatorios. NO se afirma que el sonido haya sido oído en Samsung hasta prueba en teléfono.
- No se borró SQLite, no se cambió DB_VERSION5 ni se intervinieron audios MP3 de viajes/WhatsApp, alarmas, Historial, ruta, Supabase o controles de viaje.
- Se descargó artefacto GitHub y se verificó SHA256 APK y firma. Carpeta Drive R22.6 creada: https://drive.google.com/drive/folders/173p-eE8f-YjJStuH25_klDkCQhU6FFon . ZIP de respaldo en Drive con APK y comprobantes: https://drive.google.com/file/d/1EqXJZV2keziBGVYGE1y94tvWZAmB2Xxt/view . La subida directa del archivo APK a Drive falló por sesión de archivos expirada; entregar enlace sandbox APK de forma principal y Drive ZIP como alternativa.
- PENDIENTE prueba real: apariencia de tarjetas en Samsung, piquito tocando el icono ✦ en primer plano, alerta cuando la app no está visible, volumen audible y permiso de alertas, accionar botones; no confundir compilación firmada con QA en teléfono.


## R22.8 — sonido original de recordatorios
- VersionCode 38 / versionName 0.1-R22.8; compilación firmada exitosa GitHub Actions run 37870800270, revisión 29dac4b5a1f990dc071fb87aae63e4e74836598e.
- MP3 exacto aportado por el usuario: 33062 bytes, ~2.04 segundos, SHA-256 5cc7d452cb5f265bf6544486c52f3e8b57d3a292e77af38cbb5e3df0e9875bb0. Texto Base64 en mapatrayectos/assets/reminder_bubbles_elevenlabs.mp3.base64; Android lo empaqueta bajo el nombre optimizado res/YZ.mp3. Validación de bytes dentro del APK por hash exitosa.
- ReminderSound utiliza MediaPlayer y archivo raw real; respeta volumen y modo de timbre, registra errores y espera finalización bajo Receiver.goAsync. Android NotificationChannel v3 sin sonido propio para evitar doble sonido.
- APK SHA-256 validada con SHA256SUMS de GitHub; firma Android verificada.
- Google Drive cuenta marcelof.gx@gmail.com: https://drive.google.com/file/d/1px_rPZu9iSP8g9keQsHgoBYwLPKy5z7k/view; carpeta: https://drive.google.com/drive/folders/1DD3cgIArfpskQDUfFPb6Acv50BUQeuwj. Tamaño 52262003 bytes.
- Instalación sobre R22.6/R22.7 conserva datos, sonidos y recordatorios. Sin nuevo ZIP.
- Pendiente prueba Samsung: entrar en ✦ > Recordatorios > Probar MP3 original de burbujas, habilitar volumen de notificaciones, después alerta en 1 minuto. Confirmar audio audible real; compilación no demuestra la salida acústica en el equipo.


## R22.9 — burbujas audibles con volumen de alarmas temporal al menos 50% (08/10/2026)
- **RELEASE verificada:** versionCode 39, versionName 0.1-R22.9; GitHub Actions [37872681083](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37872681083) SUCCESS; paquete firmado v2, certificado igual a versiones anteriores.
- **Incidente real anterior:** el usuario confirmó que las alertas sí aparecían con app abierta y cerrada, pero el sonido de burbujas de R22.8 NO se escuchaba.
- **Código nuevo:** USAGE_ALARM en lugar de NOTIFICATION_EVENT. STREAM_ALARM se eleva temporalmente a >=50% si estaba más bajo; se restaura al acabar si el usuario no cambió el nivel entretanto. El reproductor usa ganancia interna 1.0, bloqueo de CPU MediaPlayer.setWakeMode con WAKE_LOCK ya existente, más permiso MODIFY_AUDIO_SETTINGS. El modo No molestar y las políticas de Android pueden restringir el sonido; se registra diagnóstico.
- **Integridad:** MP3 ElevenLabs exacto preservado sin edición (SHA-256 5cc7d452cb5f265bf6544486c52f3e8b57d3a292e77af38cbb5e3df0e9875bb0); no se modifican 13 sonidos originales de viajes, rutas, UI aprobada, SQLite ni Supabase.
- **Verificación de instalador:** APK 52.262.035 bytes, SHA-256 6a7ba50023ac91df11110134dd217dd946213631f1661d3e9ce37c5f53a5ec69; apksigner verificó firma. GitHub artifact Mapa-Trayectos-v0.1-R22-9-RELEASE.
- **Google Drive:** APK https://drive.google.com/file/d/1_OKMbJM5qNKVSjnocq4Et-5Lgzjvgy7q/view ; ZIP con comprobantes https://drive.google.com/file/d/13Xz4HOxQTkrxX-dnEoaOLY3QcoxU1mQZ/view ; carpeta https://drive.google.com/drive/folders/1e4_g4I0m4xFENE1sfa-tUrZaZ3f5OMoA (archivo y tamaño leídos nuevamente).
- **Falta probar en Samsung:** que se oiga el MP3 al pulsar PROBAR y en alarma de 1 minuto, incluso con app cerrada; que el volumen vuelva a su nivel previo. Reproducción en el equipo todavía NO confirmada.


## R23.0 — Recordatorios unidos al icono ✦ o burbuja flotante (09/10/2026)
- **Reporte del usuario en Samsung R22.9:** globo de notificación quedaba huérfano y apartado del botón ✦ cuando app abierta. Requerimiento: flecha físicamente unida al botón ✦ en primer plano y a la burbuja flotante real si app minimizada, sin tarjetas huérfanas.
- **Código R23.0:** `ReminderAnchorGeometry`, `ReminderCallout.setAnchorPlacement`, MainActivity coordenadas en pantalla y realineación al cambiar de layout; TrackingService transmite posiciones cuando bubble flota/se arrastra/desaparece; ReminderBubbleService orienta flecha a izquierda/derecha, sigue el movimiento, rechaza posición vieja y elimina overlay si se pierde ancla. Notificación de Android queda como fallback.
- **Prueba técnica:** `ReminderAnchorGeometryTest` ejecutada por GitHub Actions, resultado PASS. Compilación Android RELEASE run `37880538141` SUCCESS, SHA256 APK `cad0847510e40799db13d162ebe605e22cc2d76387e733ba1584c6abf0cb7703`, 52.262.039 bytes; firma anterior igual verificada; MP3 de burbujas original SHA `5cc7d452cb5f265bf6544486c52f3e8b57d3a292e77af38cbb5e3df0e9875bb0` incluido.
- **Versiones:** `0.1-R23.0`, versionCode 40. Primeros intentos R23.0 `37880347098` y `37880443648` fallaron por variables Java en la lambda del listener; arregladas en commit `24e60a5e2cbd63c9fb639a04998efa6ed08cba75` antes de build final verde.
- **Google Drive verificado:** APK https://drive.google.com/file/d/1HFX76Ldit7hKNRMrTbcLyE_8WgmI2V0C/view , ZIP de build https://drive.google.com/file/d/1M3q2VB5DbgnaoOBao7RQHxwphqZVpauX/view , carpeta R23.0 https://drive.google.com/drive/folders/1LK_CGnXSNuotOFKRIGwimnpCDFgy6yrB .
- **PENDIENTE REAL SAMSUNG:** inspección visual del anclaje abierto y minimizado, mover burbuja a ambos bordes y disparar recordatorio, fallback notificación sin burbuja; audio audible R22.9/R23.0 y restauración de volumen. Actualizar sin desinstalar para no borrar SQLite ni MP3 de viajes.


## R23.1 — Burbuja flotante aun sin jornada, sin GPS (09/10/2026)
- Usuario confirmó minimizar en «LISTO PARA JORNADA», aprobó burbuja disponible siempre al minimizar con o sin jornada.
- TrackingService con FGS `specialUse` inactivo y `location` activo, overlay con autorización Android y posición actualizada cada 5 s; no GPS ni kilometraje sin jornada. Al volver al mapa/historial/detalle, se cierra la burbuja inactiva.
- `FloatingShortcutPolicyTest` y `ReminderAnchorGeometryTest` PASS; RELEASE `0.1-R23.1`, versionCode 41, CI GitHub Actions `37881774310` SUCCESS. APK 52.262.243 bytes, SHA256 `3662ffc3ada0f2ae64a9b5b5bde424d907c7322ef360956bed06780013eb8332`; misma firma que R23.0; sonidos originales preservados.
- APK Drive https://drive.google.com/file/d/1WLYfqWfyt-VDvdij_WSUc1MpR_0xTrsm/view ; carpeta https://drive.google.com/drive/folders/18QsCkg8UqirtLcaCwvy4g_SbJc0JnHmv .
- **QA pendiente en Samsung:** instalar encima de R23.0, conceder superposición con ◎, minimizar SIN jornada, comprobar burbuja y ausencia GPS, tocar para regresar; repetir CON jornada, alarmas y cola anclada. Samsung/Android puede restringir permanencia por batería.


## R24.0 — Traslados Conductor integrado dentro de Mapa (09/10/2026)
- **Objetivo:** una APK principal Mapa Trayectos con módulo Conductor real embebido. Cliente sigue siendo app separada; Conductor independiente queda de respaldo sin cambios.
- **Integración:** `prepareEmbeddedConductor` Gradle copia fuentes originales de `conductor/` al build de Mapa, importa R generado y copia WAV/íconos/texture; compilación completa de `MainActivity`, `Api`, `ReservationMonitorService`, `DemandMapActivity` y `TripTelemetryService` nativos. No son enlaces a otra APK ni WebViews.
- **UX:** menú general ☰ premium independiente del ✦ de acciones rápidas; solicitudes nuevas sincronizan badge en ✦ y burbuja flotante única, con tarjetas de NUEVO TRASLADO ancladas y entrada al módulo. Recordatorios y sonidos preexistentes intactos.
- **Compilación:** primera run R24 `37887559428` falló por comentario vacío como instrucción Java tras transformación del código; corregido en `9be9fd174641bd564c70e15c6ec201e592fbaf53`. Run definitiva **37887653127 SUCCESS** con tests/compilación/firma/sonidos/recursos verificados. `0.1-R24.0`, versionCode 42, APK 52.527.863 bytes SHA256 `80aa8378cf1b946e9d16f895d7b83a5827c03513ca84f7b1663a22c2af07b9e8`; certificado firma compatible con R23.1.
- **Backup Drive ZIP** https://drive.google.com/file/d/17TlWjNWmt-KMp_eX_cNy1tIjG_neKj2C/view , carpeta https://drive.google.com/drive/folders/10S1FZRNi4m8vHOAuFIHwKa4VVyczx5tJ . APK individual entregada por enlace sandbox de sesión.
- **Prueba pendiente Samsung:** entrar por menú ☰ a Conductor integrado, iniciar sesión PIN por primera vez, verificar datos reales Supabase y aceptación/presupuesto/agenda, probar contador y tarjeta de solicitud en abierto y minimizado, GPS/sonidos, comprobar no duplicaciones. GPS de Conductor y Mapa siguen siendo servicios distintos; consolidación y seguimiento en vivo del pasajero permanecen pendientes.
