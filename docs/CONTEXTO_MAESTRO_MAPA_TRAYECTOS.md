# MAPA TRAYECTOS — CONTEXTO MAESTRO HISTÓRICO

**IMPORTANTE (corte R22.8):** Este archivo conserva información de varias versiones y algunos párrafos antiguos siguen diciendo "pendiente" para funciones ya implementadas. Para empezar una NUEVA SESIÓN, leer **primero** [RETOMAR_MAPA_TRAYECTOS.md](RETOMAR_MAPA_TRAYECTOS.md), después el historial y por último este contexto histórico. El código actualizado está en `main`. Última RELEASE verificada a este corte: R22.8 / versionCode 38 / Actions 37870800270.

## Proyecto y fuente principal
- Android nativo, package uy.com.mapatrayectos; módulo mapatrayectos.
- Repo: marcelofgx-ctrl/traslados-android (branch main).
- CI: .github/workflows/build-mapa-trayectos.yml, firmado con secretos GitHub.
- Base visual aprobada R20. R21.5 RELEASE compilada. R22 (versionCode30) incorpora recordatorios, limpieza local protegida y pausa de jornada: consultar GitHub Actions para compilar/firma y exigir prueba en dispositivo.
- Estilo petróleo profundo + champagne/dorado fino + crema. Paneles compactos, bordes finos, sombras suaves, escasa textura. Mantener la estética premium R20.

## Funciones operativas a preservar
- MapLibre/OpenFreeMap Liberty, GPS, seguimiento y modo libre, brújula, velocímetro.
- Jornada -> ir a recoger -> pasajero recogido -> viaje -> parada intermedia -> continuar -> finalizar.
- Slider con parada a mitad y final a la derecha; háptica de umbral distinta; sin duplicar botón de parada.
- Viajes Uber/Cabify/Personal/Otro; importes y cancelaciones.
- SQLite local TrackDb DB_VERSION=4, trip_stops y pickup; historial agrupado por día y filtros; backup/import ZIP; no borrados automáticos.
- Traza turquesa y retorno dorado (detección no universal de cualquier cruce).
- Backend Api.java Supabase: campos pickup/stops todavía no sincronizados íntegramente.
- MP3 deseados no plenamente integrados: chimes sintetizados de respaldo (Pixabay bloqueó descarga durante builds anteriores).

## Compartir datos del emprendimiento (R21.5)
- Menú superior ✦ compacto: Tarjeta visual, WhatsApp a número, Guardar pasajero, Enlaces y contacto.
- La tarjeta es PNG nativo diseñado con Canvas, logo C (automóvil+brújula+camino), teléfono +598 97 228 175 y QR https://wa.me/59897228175.
- Tarjeta visual: ACTION_SEND image/png/FileProvider a WhatsApp, destinatario seleccionado allí. Evitar llamarlo 'envío a número' porque WhatsApp no ofrece adjunto directo fiable a no agendados.
- WhatsApp a número: abre chat mediante enlace oficial con mensaje corto, no adjunta imagen. Permite teléfono no guardado, requerirá confirmación de envío.
- VCF con PHOTO incrustado; la vista previa en WhatsApp puede mostrar avatar gris hasta importar; es limitación del cliente WhatsApp.
- Guardar pasajero: nombre+teléfono obligatorios, empresa opcional; abrir app Agenda con ContactsContract.ACTION_INSERT, el usuario confirma. No se leen ni se guardan contactos silenciosamente.
- Enlaces web/descarga: direcciones previas (traslados-con-reserva.lovable.app y /descargas) no se verificaron como destinos públicos; exigir validación/corrección explícita antes de primer compartido, persistir confirmación local.
- En todos los diálogos 'Cancelar' es la última acción.

## Roadmap aprobado y no implementado
Consultar docs/mapa-trayectos-roadmap-2026-10-08.md:
1. Confirmación real en teléfono del flujo R21.5 WhatsApp/Contacts.
2. Recordatorios locales estilo conversación/chat en menú; texto, fecha/hora, confirmar, notificar fuera de la app, Android AlarmManager y persistencia, editar/posponer.
3. Ajustes: vaciar registros desde cero solo con backup previo opcional y doble confirmación, protección jornada activa, distinguir local de Supabase.
4. Pausar/reanudar jornada sin sumar minutos a DET.; horas efectivas e historial.
5. Burbuja flotante premium para volver desde apps externas.
6. Sonidos originales: 607923 jornada, 443093 iniciar viaje, 158193 Uber, 383749 cancelar, 607920 finalizar. Cabify y pausa SIN seleccionar; no duplicar Uber automáticamente.
7. Sincronización remota de pickup y trip_stops.

## Flujo de trabajo
1. Leer el estado actual de main y runs.
2. Cambios incrementales, no romper R20/GPS/DB.
3. Bump versionCode/versionName, ajustar guards de workflow y nombre artifact.
4. Comprobar run verde, firma RELEASE, SHA256, descargar APK y dar enlace.
5. Clasificar cada cambio: IMPLEMENTADO EN CÓDIGO, COMPILACIÓN VERIFICADA, COMPORTAMIENTO VERIFICADO EN TELÉFONO.
6. Documentar en PENDIENTES e HISTORIAL_VERSIONES. No asumir validación de dispositivo sin usuario.

## R22 — Diseño implementado en código, sujeto a tests
- Menú ✦: Recordatorios en pantalla nativa tipo chat. Los recordatorios tienen texto, fecha/hora confirmadas, lista, posponer/completar/eliminar, notificación local y rearmado tras reinicio. Permiso de notificaciones y pantalla para habilitar alarmas exactas cuando corresponda.
- Mantenimiento ⚒: Empezar de cero. Pregunta si hacer ZIP; si se acepta valida ZIP con DatabaseBackup.inspectBackup antes de permitir borrar. Segunda confirmación escribiendo BORRAR; sin jornada ni viaje activo. Borra sólo cuatro tablas SQLite de mapa en transacción, preservando esquema, servidor y recordatorios.
- Pausa jornada: no cuenta GPS, kilómetros ni minutos DET mientras está pausada, con pause_ms y pause_count en TrackDb DB_VERSION5; reanudar desde slider o mantenimiento y seguir jornada original.
- Burbuja flotante más pequeña con marco petróleo/champagne; se conservan arrastre y apertura del mapa.
- Sonidos Cabify: se retira fallback a Uber, todavía sin MP3 originales disponibles.
- Los registros ya sincronizados en Supabase NO se borran desde esta operación; la app no descarga viajes del servidor, por lo que la base local queda vacía aunque existan registros remotos. Migrar cloud requeriría autorización.
- No declarar como probados en teléfono recordatorios, backup/borrado o pausa hasta feedback del usuario.

## Entregables en Google Drive — fuente permanente (08/10/2026)
- Cuenta principal usada: marcelof.gx@gmail.com.
- Carpeta de la aplicación ya existente: https://drive.google.com/drive/folders/1rrEpWSYO3dt-Y5dXafP34cM6CQzzBYrK
- Carpeta Versiones: https://drive.google.com/drive/folders/1eaXoeNaRl9Peb8a7pyrOFrTWqj-xyjd6
- R22.1 (actual): https://drive.google.com/drive/folders/16HKGJuyCODAaPtQt0IMsQFfGvEMPy4pB
  - APK firmada: https://drive.google.com/file/d/1HWqQADONxv3jwhKp7xj5J9JwrlHkRn5S/view
  - ZIP cinco MP3 originales: https://drive.google.com/file/d/1UcH9TuWlFYtnRNnhvN5SCyllHSKwQgMQ/view
- R22 (previa): https://drive.google.com/drive/folders/1vCVuAtm83vhIEaiMglfbC3N_VPLbgCyE
  - APK R22: https://drive.google.com/file/d/1dYqEirelAKH8cczWi-ZEHjuXlt-1HEnb/view
- Organización decidida: Mapa Trayectos / Versiones / RNN.N, con instalador APK, paquetes auxiliares y versiones anteriores separados. Conservar carpetas Actual e Histórico preexistentes; no borrarlas sin aprobación.
- Cada futura APK: subir a Drive además de GitHub Actions y verificar nombre, tamaño y carpeta antes de entregar enlace.


## Versión R22.2 — Identidad sonora completa (08/10/2026)
- Compilación RELEASE verificada por GitHub run 37812971829, versionCode32, versionName 0.1-R22.2.
- Se identificaron 13 audios de notificación distintos proporcionados por el usuario (archivos duplicados descartados, se excluyó grabación ajena).
- Mapeo fijado por el usuario, preservado: jornada 607923, viaje personal 443093, Uber 158193, cancelación 383749, final viaje 607920.
- Complemento definido por el asistente con permiso del usuario: Cabify 494546, recogida 124467, iniciar parada 480571, finalizar parada 485901, terminar jornada 580715, pausar jornada 480567, reanudar jornada 376885, menús/acciones deliberadas 323602.
- SoundPack.java autentica hashes SHA-256 de los 13 MP3; ZIP ampliado puede reemplazar el paquete viejo de cinco sin perder datos de la app.
- FeedbackReceiver distingue pausa/reanudación y todos los eventos; SoundPack usa un único MediaPlayer de referencia fuerte para minimizar fallas y solapamientos. Cuando hay 13/13 instalados, no sustituye un MP3 fallido por pip-pip en silencio.
- Mantenimiento → Sonidos originales: lista desplegable de 13 eventos con PROBAR, diagnóstico si falla, INSTALAR ZIP.
- La APK NO contiene los MP3 binarios; el ZIP se instala una vez. Solo hay chimes si el paquete está incompleto o no se instaló. En menú, cue breve no corta una melodía importante en curso.
- Release Google Drive: https://drive.google.com/drive/folders/105ACyatGtj239RA3avgHFlp2cCl3_8eI
- APK https://drive.google.com/file/d/1nwBpGxAyCwgJ8R2IT2Jx_ReNBzZz9Zw9/view
- 13 MP3 https://drive.google.com/file/d/1bBXYtcE3UNlfFC5BXsX5_rD65AxdpQgK/view
- Estado: Implementado en código / compilación verificada / audios reales pendientes de prueba del usuario en Samsung.

## R22.3 — corrección de recordatorios aprobada por usuario (08/10/2026)
- Incidente REAL en Samsung: recordatorio para «14:20» no llegó. No hay acceso remoto al almacenamiento privado de la app. Código R22.2 parseaba HH:mm solo si incluía «hoy/mañana», y en silencio podía usar mañana 10:00.
- R22.3 versionCode 33, build RELEASE exitoso GitHub Actions run 37817234425, SHA256 9edcd9c09c0f526d2149ddac6073b0b54702b3b90b4fb8d8fadfe282f7187dcb, certificado release existente.
- Cambios código: hora sola «14:20» ahora hoy a esa hora (si ya pasó se pide otra fecha); calendario elegido explícitamente tiene prioridad; siempre muestra propuesta para confirmar. Prueba «PROBAR ALERTA EN 1 MINUTO» programa 65 s si notificaciones y alarmas exactas están autorizadas.
- Diagnostics: notificaciones Android, alarmas exactas, permiso superposición, próximo recordatorio, último aviso registrado y error. Notifications HIGH con acciones HECHO/+10 MIN. Si notificación bloqueada no se marca fired (corrección clave).
- Visual «globito de historieta»: ReminderBubbleService shortService overlay de 60 s con triangulito, identidad gráfica, Hecho/+10 MIN/Cerrar, anclaje aproximado a posición de burbuja y notificación Android como fallback permanente. Android/Samsung puede bloquear inicio de overlay en segundo plano; no prometer siempre visible sin permiso/prueba.
- REQUIERE PRUEBA EN TELÉFONO: Ajustes → notificaciones Mapa Trayectos, permiso «Alarmas y recordatorios», «Mostrar sobre otras aplicaciones» para globo. Ejecutar prueba en 1 minuto saliendo de app y bloqueando pantalla, comprobar presentación y acción +10.
- El usuario confirmó que sonido original R22.2 quedó bien y no desea nuevos cambios en identidad sonora.
- Intento de crear carpeta R22.3 en Google Drive fue bloqueado por seguridad de conexión, NO se ha confirmado un enlace de Drive R22.3; archivo GitHub Actions sí existente.

## Tablero de doble nivel R22.4
- Código actualizado: R22.4 (versionCode 34), sucesora R22.3; release mediante GitHub Actions run 37831735682. Hasta verificar compilación no declarar APK éxito.
- Cuando hay jornada activa y NO viaje: indicadores principales KM jornada, CONDUC. (shiftMoving), DET. (shiftStopped), VIAJES (solo servicios completados, excluye cancelados). Tiempo EFECTIVO se muestra en una franja champagne: shiftMoving+shiftStopped. Horas siempre HH:MM:SS; las pausas voluntarias quedan fuera de tiempo efectivo.
- Durante viaje: indicadores grandes KM servicio, duración SERVICIO (tripStarted hasta ahora), MOV. viaje, DET. viaje. Franja compacta de JORNADA permanece visible con HH:MM:SS efectivo + KM + viajes completados. Al finalizar retorna a jornada sin perder acumulados.
- MainActivity adapta etiquetas e iconos (TRIPS para contador completados, MOVING/STOPPED según contexto); mantiene panel premium petróleo/champagne, textura, deslizador; nueva altura mid 272dp y full 332dp para franja de jornada sin superponer UI.
- TrackDb.completedTripsForShift consulta SQLite directamente por shift_id y trip_status completed + ended_at_ms; TrackingService recalcula al finalizar cada servicio y al reiniciar; publica shift_completed_trips en ACTION_STATE. No cambia DB_VERSION=5.
- Tiempos dibujados a HH:MM:SS; fuente adaptada a cuatro columnas, service emite cada segundo incluso sin GPS.
- Alcance: métricas por JORNADA activa, no sumatoria de distintas jornadas de un día calendario. Consolidación por fecha queda como mejora futura de Historial; no presentarla como implementada.
- Verificación requerida en Samsung: jornada quieta 90 segundos (DET. debe avanzar), movimiento seguro (CONDUC. debe avanzar), pausar/reanudar (efectivo permanece fijo durante pausa), iniciar Uber/Cabify/particular, completar/cancelar (VIAJES cuenta solo completados), revisar visualización 4 columnas en Maroñas. No conducir manipulando pantalla.

## Web de Traslados — enlace oficial comunicado por el usuario (08/10/2026)
- Enlace de la web de reservas suministrado expresamente: https://traslados-web.marcelof-gx.workers.dev/
- En R22.7 versionCode37, QuickActionsMenu.java cambia BOOKING_URL desde el placeholder Lovable a esta URL. Para móviles con SharedPreferences `share_links` previamente confirmados sobre el URL antiguo, migrar solamente el valor por defecto anterior, sin sobreescribir URL personalizadas. Compartir web desde ✦ → Enlaces y contacto → WEB DE RESERVAS.
- No inferir ni compartir un enlace de descarga de APK bajo la nueva web: la ruta de descargas debe confirmarse por separado. Mantener la verificación editable de `download_url`.
- No afirmar verificada la respuesta HTTP pública: la consulta web de esta sesión devolvió error de lectura/caché. URL procede directamente del usuario.
- R22.7 modifica sitio de reservas y no el código de viajes, recordatorios o sonidos. Si todavía no se ejecutó y verificó la firma no declarar release terminada.

## Sonido original de recordatorios — R22.8
- El usuario subió el MP3 de ElevenLabs con burbujas delicadas para los recordatorios. Nombre recibido: ElevenLabs_Pequeñas_burbujas_de_jabón_explotando_en_un_suave_brillo,_delicadas,_como_de_juguete.mp3, 33062 bytes, duración 2.037551 s, SHA-256 `5cc7d452cb5f265bf6544486c52f3e8b57d3a292e77af38cbb5e3df0e9875bb0`.
- Original exacto codificado en `mapatrayectos/assets/reminder_bubbles_elevenlabs.mp3.base64` (texto). El workflow `.github/workflows/build-mapa-trayectos.yml` lo decodifica en `src/main/res/raw/reminder_bubbles_elevenlabs.mp3` y verifica SHA y tamaño antes de compilar, y también verifica el SHA extraído desde la APK final. NO requiere importar otro ZIP.
- `ReminderSound.playBlocking` reproduce el `R.raw.reminder_bubbles_elevenlabs` real por `MediaPlayer` con AudioAttributes de notificación, verifica inicio y completion, espera como máximo 4.5 segundos desde un hilo dedicado (ReminderReceiver.goAsync), respeta silencio/vibración y volumen de notificaciones. Guarda diagnóstico local para probar en Samsung.
- `ReminderActivity` botón «PROBAR MP3 ORIGINAL DE BURBUJAS» y diagnóstico. El canal Android de notificación v3 permanece silencioso para evitar pitido doble; los sonidos previos de Uber/Cabify/viajes no se modifican.
- Versión Android `0.1-R22.8` versionCode38, compilación firmada. No afirmar probado auditivamente en dispositivo hasta prueba en Samsung. Previas R22.6 y R22.7 no tienen este MP3 embebido.
- R22.7 incluyó la URL web oficial entregada por usuario; R22.8 se basa en código main anterior, no elimina esa URL. Usuario había pedido no distribuir como última la R22.7; entregar R22.8 solamente tras firma y revisión.
- Enlaces de Drive de releases deben comprobarse con Google Drive, no suponer subidas. Carpeta creada R22.8 https://drive.google.com/drive/folders/1DD3cgIArfpskQDUfFPb6Acv50BUQeuwj
