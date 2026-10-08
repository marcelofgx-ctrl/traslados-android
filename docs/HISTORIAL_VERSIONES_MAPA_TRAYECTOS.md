# Mapa Trayectos — historial de versiones verificable
Estado a 08/10/2026. Mantener actualizado tras cada build y prueba de dispositivo.

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
