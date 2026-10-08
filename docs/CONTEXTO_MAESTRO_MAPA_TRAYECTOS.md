# MAPA TRAYECTOS — CONTEXTO MAESTRO VIVO
Actualizado 08/10/2026. Usar este archivo al iniciar una nueva sesión, junto a PENDIENTES y HISTORIAL_VERSIONES.

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
