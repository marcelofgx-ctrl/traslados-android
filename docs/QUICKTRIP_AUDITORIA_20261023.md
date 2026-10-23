# Quick Trip — auditoría de conductor único, GPS y pantalla compacta
Fecha: 23/10/2026. Investigación read-only en Supabase y propuesta aislada. NO deployment, NO APK, NO SQL en producción.

## Hechos confirmados
- Dos recogidas inmediatas ACEPTADA / EN_PUNTO, sin FINALIZADA ni CANCELADA: TR-261023-529FD (08:43) y TR-261023-9BD22 (16:11).
- TR-261023-4F192 (16:19) fue bloqueada por el servidor: HORARIO_YA_NO_DISPONIBLE, detalle OCCUPIED, HTTP 400. La app mostró erróneamente que se revisara la conexión.
- availability_has_conflict usa intervalos de hora programada más duración estimada. No bloquea un servicio matutino aún abierto durante la tarde.
- driver_live_presence: enabled=false, sin coordenadas actuales. reservation_live_position: cero filas. El histórico GPS de Mapa tiene puntos, pero no está conectado al seguimiento por reserva.
- mapa_reservation_position_v1 exige UNA sola reserva ACEPTADA/EN_CAMINO/EN_PUNTO o EN_VIAJE y devuelve no_unique_active_reservation en caso de múltiples. El mecanismo protege la privacidad, pero impide el seguimiento actual.
- TripTelemetryService registra el recorrido específico después de iniciar viaje; la recogida previa requiere presencia y GPS asociados a la única reserva activa.
- El nombre del pasajero sí existe en customer_name, pero aparece con baja jerarquía visual.

## Consolidación necesaria (etapas independientes)
1. Regla transaccional atómica en servidor para un solo servicio inmediato abierto por conductor, independiente del vencimiento de su duración estimada. Revisar todos los RPC de aceptación, presupuestos y status. No aplicar SQL sin autorización separada, pruebas y rollback.
2. Estado real DISPONIBLE / OCUPADO / SIN CONEXIÓN tanto en la app como en la web. No confundir jornada activa con conductor disponible, ni permitir segunda recogida desde un cliente alternativo.
3. Cerrar las reservas de prueba anteriores por transición explícita y autorizada; nunca cerrar automáticamente ni borrar historial para liberar disponibilidad.
4. Vincular un GPS existente con reserva única autorizada: TrackingService -> mapa_presence_ping_v1 -> mapa_reservation_position_v1 -> reservation_live_position -> customer_reservation_position_v1. Consentimiento explícito, coordenadas recientes y vigencia caducada bien visibles.
5. En la web, mostrar vehículo EN_CAMINO hacia el origen, EN_PUNTO al llegar, y recorrido contratado EN_VIAJE. El trazado estimado origen-destino no equivale a seguimiento GPS.
6. Mostrar error OCCUPIED como horario ocupado; no culpar a Internet cuando la API respondió correctamente.

## Pantalla única compacta
- Encabezado Quick Trip y chip de disponibilidad operativa REAL.
- Tres contadores pequeños + sincronización discreta.
- Prioridad a una solicitud actual con PASAJERO visible, origen, destino, distancia, minutos, precio y Aceptar/Rechazar (Aceptar deshabilitado si está ocupado).
- Servicio activo: una sola tarjeta resumen con pasajero y estado, desplegable con transición para llamada, WhatsApp, ruta, siguiente fase. Si hay anomalías, indicador de más servicios sin cerrar.
- Programados y Histórico cerrados por defecto. Sin overlays ni motores duplicados.

## Prototipo de rama
Solo código de presentación Android y política Java pura de capacidad, conservando RPC, GPS, cliente web, datos y versión instalada. El guard visual NO sustituye el bloqueo atómico de Supabase. No compilar APK, fusionar ni publicar hasta solicitud explícita.

## Criterios de aceptación
- Conductor ocupado no puede aceptar segunda inmediata aun desde otro teléfono; estado de web coherente.
- Un viaje que supera la ETA prevista sigue ocupado hasta cierre servidor explícito.
- GPS visible al pasajero solo con consentimiento y para su reserva, con antigüedad y precisión verificadas.
- Pantalla principal legible y operable sin scroll en el Samsung; acciones secundarias en desplegables.
- No se modifican ni borran reservas de prueba existentes sin autorización expresa.