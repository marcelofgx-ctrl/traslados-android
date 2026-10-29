# Quick Trip | P0 GPS privado | auditoría QA 29-10-2026

Base F1: 98049035ea227de3b10d659834c94c39e1e779d3. Código de Mapa QT R25.38: 38bc41349a9e3a799b54652e080ad867df60f3db; blob de Api.java: e83805a523b2c8b4700a41b6b830caf4d75e97e7. APK instalada identificada por texto de versión, no por hash.

## Alcance y fuentes

Solo QA: sin cambios a Android, web, backend real, SQL, main, producción o reservas. Las dos funciones SQL se capturaron desde pg_catalog.pg_proc con pg_get_functiondef en una consulta READ-ONLY. No se consultó ninguna fila de negocio. La referencia Java es únicamente el método publishPickupPresenceAsync de R25.38. Las pruebas funcionan offline y no incluyen datos personales ni secretos.

## Hallazgo P0-A: asociación global requiere UNA reserva elegible

La RPC publica GPS privado únicamente cuando el número global de reservas elegibles es exactamente uno. Considera elegibles los estados EN_VIAJE o ACEPTADA, ACEPTADA_CLIENTE, CONFIRMADA con pickup_phase EN_CAMINO/EN_PUNTO. Para 0 o 2+ devuelve ok=false, reason=no_unique_active_reservation. El conteo no está filtrado por un identificador de dispositivo/reserva individual. Por eso dos reservas elegibles impiden publicar y una reserva única ajena podría recibir una posición no vinculada inequívocamente. Es una debilidad de contrato, no evidencia de que haya ocurrido. La captura anterior de 3 servicios sin cerrar no demuestra que haya más de una elegible; no consultar ni cerrar reservas para verificarlo sin autorización separada.

## Hallazgo P0-B: Android R25.38 descarta el error

El código primero envía mapa_presence_ping_v1. Con resultado ok=true y consentimiento, jornada activa/no pausada, coordenadas válidas y precisión <=45 m, llama mapa_reservation_position_v1. No lee su JSON de respuesta ni clasifica reason; atrapa excepciones con catch(Exception ignored). Por eso la presencia pública y la ETA previa pueden funcionar y seguir sin GPS de reserva.

## Hallazgo P0-C: lectura de pasajero

customer_reservation_position_v1 autoriza por sesión dueña o token público de reserva, exige fase activa y lee reservation_live_position de la reserva exacta. También exige presencia enabled, jornada activa/no pausada, coincidencia de device_id y ambas marcas de tiempo (GPS y recepción) con antigüedad <=75 segundos; precisión <=45 m. Devuelve unauthorized, not_active, preparing_pickup, position_unavailable o available=true. La PWA muestra un mensaje genérico cuando no hay GPS.

## Hallazgo P0-D: respuesta optimista para GPS más viejo

El upsert impide sobrescribir un GPS con timestamp anterior, pero la función devuelve ok=true, updated=true incluso si el WHERE del UPSERT evita actualizar. No es confirmación fiable de una nueva muestra.

## Pruebas sintéticas

Ejecutar node tests/private-gps-p0-contract-qa.cjs. Verifica el código SQL capturado, el fragmento Android y la PWA F1. Simula 0, 1, 3 reservas elegibles; ACEPTADA sin iniciar recogida; dispositivo no autorizado; consentimiento; GPS viejo, futuro e impreciso; token/propiedad; fases; dispositivo distinto; interrupción de conexión y clasificador local de errores. Genera artifacts/private-gps-p0/diagnostic-report.json con CÓDIGOS FICTICIOS. CI conserva verificador F1. No ejecuta RPC ni crea reservas.

## Próximo lote propuesto, NO IMPLEMENTADO

1) Android QA R25.38: instrumentar la respuesta privada de la RPC y fallos de transporte en un diagnóstico local limitado a códigos de motivo, sin IDs, ubicaciones ni credenciales. Mantener GPS, polling y publicación intactos. Tests para razones y transiciones. Se requiere autorización explícita para cambiar Android y compilar APK.
2) Diseñar, sin aplicar SQL, el vínculo inequívoco reserva/conductor/dispositivo y una estrategia para varias reservas elegibles sin fugas entre pasajeros. Si fuese necesario modificar backend, pedir autorización aparte.
3) En paralelo como P0 distinto: mapa del pasajero queda vacío al iniciar viaje y título GPS REAL aun sin ubicación; no resolverlo fingiendo GPS.

La auditoría sola NO demuestra seguimiento GPS físico. No desplegar, fusionar ni publicar.
