# Traslados — disponibilidad automática por jornada, conducción reciente y ETA de recogida

**Acordado conceptualmente:** 09/10/2026, tras captura del formulario de recogida «Ahora / En 10 minutos». **Estado:** especificación técnica verificada contra el código; no se implementó aún el canal de presencia comercial ni la respuesta ETA en producción. Contexto único: [CONTEXTO_MAESTRO_PROYECTOS.md](CONTEXTO_MAESTRO_PROYECTOS.md).

## La regla simple que pidió el usuario

Mientras **Mapa Trayectos tenga jornada iniciada, actividad de conducción reciente y ubicación GPS válida**, el sistema debe considerar al conductor **en actividad y potencialmente disponible** y mostrar al pasajero, cuando elija A:
- kilómetros **por carretera** desde **ubicación actual del Conductor → origen A** (no A→B);
- minutos estimados para llegar a recogerlo;
- estado honesto «Disponible para consultas / Sujeto a confirmación» o «Ocupado / No disponible»;
- actualización automática, sin tener que introducir manualmente kilómetros.

Esto debe integrarse con **reservas programadas**, no asumir que una agenda vacía significa ausencia de viajes Uber/Cabify.

## Señales comprobadas en Mapa

`mapatrayectos/src/main/java/uy/com/mapatrayectos/TrackingService.java` ya dispone de:
- `shiftActive`: jornada iniciada/terminada.
- `shiftPaused`: jornada pausada/reanudada.
- `tripActive`: viaje registrado en Mapa (no observa otros proveedores).
- `vehicleMoving`, `filteredSpeed`, `lastAcceptedTs`, `lastAcceptedAccuracy`, `lastAccepted`: conducción y GPS validados por el filtro de ruido de Mapa.
- `broadcastState()`: propaga `shift_active`, `shift_paused`, `trip_active`, `vehicle_moving`, `has_location`, `lat`, `lon`, `accuracy`, `location_age_ms`.
- La sincronización histórica existente de `Api.syncShift/syncTrip` NO publica ubicaciones activas en tiempo real. **No reutilizar puntos de viajes finalizados como ubicación actual**.

## Política V1 propuesta para implementar

1. **Consentimiento una vez:** control visible «Compartir disponibilidad para recogidas» en Mapa (desactivado por defecto y revocable). La app conserva la elección localmente. Iniciar jornada NO debe habilitar ubicación comercial sin esa autorización previa.
2. **Heartbeat autenticado** a una tabla nueva privada `driver_live_presence`, cada ~20–30 s con jornada iniciada y permiso activado. No exponer lat/lon mediante SELECT/RPC públicas. Vincular **PIN de conductor válido** con dispositivo autorizado; la identidad actual `mapa_trayectos_devices` no certifica conductor.
3. **Condiciones de disponibilidad:** jornada activa, no pausada, no `tripActive`; GPS con antigüedad máxima de **90 s**, precisión de hasta **45 m** y última conducción comprobada ≤**5 min**. No exigir movimiento actual: detenido en un semáforo o esperando al pasajero no equivale a desconectado. Además, sin bloqueos de agenda o un servicio programado que impida alcanzar el origen A. No presentar como certeza una heurística para Uber/Cabify.
4. **Ocupado manual** de un toque para Uber/Cabify u otro traslado externo; prevalece sobre todos los indicadores automáticos. Al terminar ese viaje vuelve a evaluar la regla automática, sin borrar datos.
5. **Caducidad servidor:** sin heartbeat actualizado por 90 s, al cerrar/pausar jornada, falta de GPS, viaje registrado activo o desactivación del permiso, estado **no disponible** y retirada inmediata de ETA pública.
6. **Servidor de ETA privado:** solo el backend conoce la coordenada del Conductor; invoca el motor ORS **server-to-server** (clave privada actual de Cloudflare) para ruta Conductor→A. Devuelve al pasajero únicamente `available`, `distanceKm`, `etaMin`, `locationAgeSeconds` y mensaje de estado. Sin coordenadas, trayecto histórico ni PIN; sin estimación aérea ni valor de prueba. Proteger el acceso entre Cloudflare y Supabase con identidad de servidor de alcance mínimo y límites por usuario/origen/caché; **no entregar coordenadas mediante RPC anónima**.
7. **Reserva programada:** mostrar compatibilidad de agenda; solo ofrecer ETA de posición ACTUAL al pasajero si pide recogida inmediata, nunca extrapolar esa posición para una reserva futura. «Ahora / En 10 min» sigue como **solicitud sujeta a aprobación**: la fecha/horario exactos necesitan un nuevo flujo de aceptación inmediata porque Supabase actual exige `lead_time_min=30` para reservas programadas.
8. **Comportamiento seguro:** si el usuario está conduciendo en Uber/Cabify y no marcó «Ocupado», puede aparecer potencialmente disponible aunque no lo esté; por eso mostrar «Disponible para consultas» y **nunca** «Confirmado» antes de su aceptación. Notificaciones y consulta no implican reserva.

## Experiencia visual compacta

Cuando el pasajero marque origen A y elija «Ahora» o «En 10 min», incluir una línea destacada integrada en la tarjeta de horario, sin caja gigante repetida:

> **Conductor en actividad · disponibilidad a confirmar**  
> **3,8 km · 8 min para llegar** *(solo ejemplo visual; publicar exclusivamente el resultado REAL del motor)*  
> Solicitar recogida

Si sin heartbeat fresco: «Conductor sin ubicación de recogida disponible. Consultar por WhatsApp». Si ocupado: «Conductor ocupado. Elegir otra hora». Diseño petróleo/champagne existente, textos breves, nada de coordenadas exactas en la web.

## Plan de entrega y QA

**P0:** tabla privada, RPC publicación autenticada, consentimiento persistido, heartbeat con apagado; simulaciones de cierre/pausa/jornada activa y verificación de permisos. **P1:** puente de servidor seguro para ORS y ETA, más frontend PWA y Premium. **P2:** cruces con reservas programadas, ruta previa al próximo servicio, ocupación externa y solicitudes urgentes admitidas por conductor. Verificar en Samsung movimiento, estacionamiento, pantalla bloqueada, datos sin señal, reinicio y degradación GPS; compilar Mapa RELEASE conservando firma, SQLite, PIN y MP3.

**Estado al documentar:** ninguna tabla/lat/lon en vivo creada, ninguna APK compilada para esta funcionalidad, ningún ETA público activo. La revisión fue de diseño, no una entrega de seguimiento GPS.
