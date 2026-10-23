# Quick Trip — contrato GPS privado por reserva (QA aislado, 23/10/2026)

**Estado:** contratos de prueba en PostgreSQL 16 local, **sin implementación en Supabase real**. Se usa la tabla sintética `qa_gps.reservations` en la base descartable `qt_capacity_test`. No se modificó el GPS Android ni la web.

## Identidades y autorización

- Conductora/conductor: `device_id` y credencial de dispositivo verificable; `driver_live_presence.enabled` es consentimiento explícito para publicar, no puede activarse por defecto.
- Pasajero: identidad de reserva UUID y token privado de alta entropía (o sesión real validada en backend). Nunca se debe confiar solamente en código público de reserva.
- Segmentar el seguimiento por UUID de la reserva, jamás por un conductor genérico ni por el último punto guardado globalmente.
- Ante dos reservas operativas simultáneas: **no publicar ni mostrar coordenadas**; devolver `no_unique_active_reservation` al publicador o `ambiguous_driver_service` al lector. Esta protección es distinta del índice único de capacidad, que evitaría nuevas duplicaciones después de su instalación.

## Flujo propuesto

GPS ya presente en `TrackingService` → publicación **con consentimiento, jornada activa, pausa desactivada y credencial validada** → asociación inequívoca con la reserva → posición con marca temporal y precisión → lectura privada de ese pasajero.

Las funciones locales ficticias `qa_gps.publish_fake` y `qa_gps.customer_position_fake` se encuentran en `local_gps_contract_fixture.sql`. No son una migración y NO deben copiarse tal cual a `public`. En producción deberán reutilizar `mapa_presence_ping_v1`, `mapa_reservation_position_v1` y `customer_reservation_position_v1` después de auditar su semántica y permisos.

**Riesgo identificado:** `mapa_presence_ping_v1` considera `loc_ok=false` cuando `p_trip_active=true`. No se debe vincular directamente el indicador de disponibilidad pública con la autorización privada de seguimiento durante `EN_VIAJE`; son dos necesidades distintas.

## Contrato de respuesta en UI

Una posición únicamente es visible cuando se cumplen TODOS estos requisitos:

- Reserva del pasajero autenticada; fase `EN_CAMINO`, `EN_PUNTO` o `EN_VIAJE`.
- No existe una segunda reserva operativa ambigua.
- Consentimiento de localización vigente, jornada activa, sin pausa y dispositivo asociado.
- GPS de antigüedad **máxima 75 segundos**, con recepción reciente, sin timestamps futuros mayores a 10 s y precisión `accuracy_m` **máxima 45 metros**.
- Coordenadas válidas (control de rango local, reemplazable por un validador geográfico adecuado).
- Si falla cualquiera de estas condiciones, respuesta `available:false` sin `driverLat` ni `driverLng`; nunca dibujar un vehículo como si su posición fuese reciente.

Estados para la pantalla: `EN_CAMINO` (coche→recogida), `EN_PUNTO` (conductor llegó) y `EN_VIAJE` (coche→destino). Una ruta dibujada entre origen y destino es **estimación**, no telemetría en vivo.

## Casos SQL de integración ficticios

El archivo `test_postgres_gps.py` ejecuta contra PostgreSQL local:
1. Token válido y token equivocado / corto / correspondiente a otra reserva.
2. Ubicación válida y mensaje sin posición cuando aún no se publicó.
3. GPS con 76 segundos, recepción vieja, timestamp futuro y precisión de 46 m.
4. Consentimiento desactivado, jornada terminada o pausada, credencial incorrecta y cambio de dispositivo.
5. Continuidad de GPS privado durante `EN_VIAJE`.
6. Dos reservas activas → fallo seguro tanto al publicar como al consultar.
7. Al finalizar → la lectura del pasajero deja de entregar coordenadas; antes de comenzar recogida → `preparing_pickup`.

**Requisitos pendientes para la implementación real:** consentimiento y permisos Android en Samsung, cadencia/consumo de batería, control de retención, seguridad de acceso a tokens, protección de múltiples dispositivos, semántica de `loc_ok`, roles y RLS en Supabase. Los datos de prueba jamás deben mezclarse con producción.

## Relación con bloqueo atómico y rollback

La capacidad se protege de manera independiente mediante el **índice único parcial** `qt_one_active_service_uq` de `draft_atomic_guard.sql`, que usa `pickup_mode` y el estado operativo, no `fare_mode` ni una duración estimada. El rollback de ese índice no borra posiciones ni reservas. La migración a Supabase real requiere autorización separada y auditoría del esquema real.
