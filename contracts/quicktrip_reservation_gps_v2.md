# Quick Trip — GPS por reserva: contrato v2 propuesto

**Solo diseño, sin ejecutar Supabase, sin APK ni publicación.**

## Componentes existentes y reutilizados
- Mapa QT: `TrackingService` es el único motor GPS; mantenerlo intacto.
- `mapa_presence_consent_v1`: consentimiento explícito y revocable del conductor.
- `mapa_presence_ping_v1`: presencia GPS y estado de la jornada.
- `mapa_reservation_position_v1`: último punto para una única reserva en curso.
- `customer_reservation_position_v1`: lectura del pasajero autorizada por sesión o token propio.
- `reservation_live_position`: última posición, no historial. No crear traza redundante.

## Propuesta mapa_reservation_position_v2
En lugar de buscar implícitamente la reserva activa, recibe además `p_reservation_id` (UUID). El servidor valida de nuevo, no confía en el UUID recibido.

Entradas: identificador/credencial de dispositivo vinculado, UUID de reserva, jornada activa, pausa, latitud, longitud, precisión y fecha GPS.

Requisitos transaccionales:
1. Consentimiento remoto habilitado; dispositivo autenticado; jornada activa y no pausada.
2. Fijación de GPS con antigüedad <= 75 segundos, hasta 10 segundos de tolerancia futura, precisión de 0 a 45 metros y coordenadas en Uruguay.
3. La reserva identificada pertenece al **único servicio operativo** y está `EN_VIAJE`, o estado `ACEPTADA`/`ACEPTADA_CLIENTE`/`CONFIRMADA` con `EN_CAMINO` / `EN_PUNTO`. Nunca elegir otra reserva automáticamente.
4. Actualizar solo una fila por reserva, y solo si la marca de tiempo GPS es más reciente.
5. Respuesta con `ok`, `reason`, `reservationId`, `serverAt`, **sin coordenadas ni credenciales**.
6. Autorización según esquema actual, RLS/grants verificados, ejecución restringida y `search_path` fijo; ningún procedimiento nuevo público sin control.

## Propuesta customer_reservation_position_v2
El pasajero consulta **solo la reserva que le corresponde** con su sesión o token aleatorio asociado a dicha reserva.

Respuesta `available: true`: fase, `reservationId`, `driverLat`, `driverLng`, `gpsAgeSec`, `accuracyM`, `serverAt`. Solo con GPS reciente, permiso vigente y reserva propia activa.

Respuesta `available: false`: motivo limitado y fase opcional, pero **nunca latitud ni longitud**.

Semántica:
- PENDIENTE: esperar aceptación, ocultar coche.
- ACEPTADA / SIN_INICIAR: confirmado, ubicación por compartir.
- EN_CAMINO: coche real → recogida, posición y antigüedad verificadas.
- EN_PUNTO: llegó; mostrar coche solo con punto vigente.
- EN_VIAJE: coche real → destino.
- FINALIZADA / RECHAZADA / CANCELADA: interrumpir localización.
- GPS vencido, jornada OFF, permiso revocado o dos servicios activos: ocultar marcador, mostrar razón; **jamás fingir posición con ruta estimada**.

## Integración y rollback futuro
Reutilizar el sondeo web existente y su motor ETA; Realtime privado puede evaluarse después. No crear otro servicio Android. No persistir ubicación en `localStorage` ni mostrar la de otros clientes. No modificar datos históricos. Una reversión desactiva v2 y retira solo los endpoints nuevos después de validar autorizaciones; nunca borra reservas.

**Bloqueadores actuales**: dos servicios sin cerrar; consentimiento remoto OFF; ninguna posición publicada por reserva. Exige pruebas físicas Samsung y autorización separada para cualquier migración real.
