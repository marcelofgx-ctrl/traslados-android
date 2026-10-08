# Mapa Trayectos — pendientes aprobados 08/10/2026

Base Android nativo: uy.com.mapatrayectos. R21.4 compilada. Preservar estilo premium y todos los datos operativos.
Orden acordado: (1) cerrar WhatsApp y tarjeta (2) recordatorios (3) reinicio de base con backup previo (4) restantes.

## P0 — WhatsApp / tarjeta visual (primero)
- La acción «Enviar tarjeta» debe compartir la imagen PNG real del emprendimiento con logo opción C, teléfono y QR mediante ACTION_SEND, image/png y FileProvider.
- No presentar mensaje de texto como si fuera una tarjeta gráfica.
- «Escribir a un número no agendado» es una acción distinta: abrir chat con enlace wa.me, texto breve opcional, NO prometer imagen adjunta al destinatario concreto.
- «Compartir contacto VCF» conserva logo C incrustado PHOTO; WhatsApp puede no mostrar foto en preview.
- Diálogo: imagen protagonista, compartir imagen primero, alternativa de escribir a número, Cancelar AL FINAL. Usuario confirma en WhatsApp; nunca envíos automáticos.
- Verificar comportamiento en dispositivo antes de dar por cerrado. No crear contactos sin permiso.

## P1 — Menú: recordatorios locales, tipo chat (aprobado; aún no implementado)
- Acción «Recordatorios» en menú flotante compacto, interfaz premium petróleo/champagne.
- Caja de texto de conversación: «Llamar a la contadora mañana a las 15:30». Capturar descripción, fecha y hora; proponer valores explícitos para confirmar antes de programar. Selector de fecha/hora si no se interpreta el texto.
- Guardar persistente en el móvil: id, texto, fecha/hora, zona, estado, creación. Ver próximos, editar, posponer, completar y eliminar.
- Notificaciones Android con canales y permisos; AlarmManager y permisos de alarma exacta cuando correspondan; rearme al reiniciar equipo/cambiar hora; respetar ahorro de batería.
- Debe avisar fuera de la app (sujeto a permisos) sin interferir con GPS/conducción. Nunca inferir fecha equivocada silenciosamente.

## P2 — Ajustes: comenzar base de cero (aprobado; aún no implementado)
- Nueva acción en Ajustes/Mantenimiento: «Vaciar la base de datos».
- Antes de borrar ofrecer «Crear backup ahora» / «Continuar sin backup» / «Cancelar».
- Si elige backup, exportar con DatabaseBackup a ZIP, comprobar éxito y permitir elegir ubicación; si falla, no borrar.
- Segunda confirmación explícita mostrando registros afectados e irreversibilidad. Rechazar si hay jornada o viaje activo.
- Cerrar servicio/SQLite, borrar WAL/SHM correctamente, limpiar estado operativo, regenerar SQLite con esquema intacto.
- Aclarar que inicialmente es SOLO base local, no Supabase: borrado remoto requiere confirmación aparte y prevención de resync de datos antiguos.
- Decidir qué sucede con recordatorios: por defecto preservar con confirmación adicional para borrarlos.
- Probar backup, restauración y reinicio; no borrar datos de usuarios durante pruebas.

## P3 — Otros pendientes del contexto R20
- Pausar/reanudar jornada; no mezclar pausa con DET.; mostrar tiempo laboral real.
- Burbuja flotante premium de estado para Uber/Cabify.
- MP3 elegidos sin binarios confirmados: 607923 inicio jornada, 443093 inicio viaje, 158193 Uber, 383749 cancelación, 607920 terminar viaje. Cabify y pausa por definir (no duplicar automáticamente Uber).
- Supabase: persistir pickup y trip_stops actualmente más completos en SQLite.

## Regla de estados
Separar implementado en código, compilación verificada, comportamiento verificado en Android.
La presente lista es requisito registrado, no declaración de funciones terminadas.
