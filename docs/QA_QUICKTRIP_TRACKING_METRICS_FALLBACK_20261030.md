# Quick Trip QA — Seguimiento: distancias del viaje y del conductor, sin confundirlas

**Fecha**: 2026-10-30. **Rama**: `qa/quicktrip-tracking-metrics-contract-fallback-20261030`. **Base**: `cd5d99e2ca21b2e720fdc37a7f694cbd44421cb3`.

## Diagnóstico confirmado en código

La solapa de `quicktrip-map-focus.js` empezaba con distancia y duración en «—» y solo las completaba en el callback `onUpdate` de una ruta **con GPS autorizado** del conductor. Sin embargo, `reservation-live.js` verificaba y dibujaba por separado la ruta **origen→destino** (blanca) sin notificar sus métricas a la solapa. El usuario podía ver la línea blanca y el precio cerrado, pero el área de métricas permanecer indefinidamente en «—» si no llegaba GPS fresco o fallaba el proveedor vial de la recogida.

No es correcto tomar el precio cerrado / tarifa por km como fuente alternativa de distancia; tampoco inferir una ETA del conductor usando la distancia total del viaje.

## Cambios mínimos

1. Al completar `renderer.render` de una ruta contratada verificada, publicar sus `distanceKm` y `durationMin` a la **misma** tarjeta mediante `notify({stage:"contract",gps:false,tripMetrics:true,...})`.
2. Mostrar esos números con títulos **DISTANCIA DEL VIAJE** y **DURACIÓN DEL VIAJE**. Solo la ruta de vehículo privado y autorizado puede reemplazarlos por **A RECOGIDA/LLEGADA ESTIMADA** o **AL DESTINO/TIEMPO AL DESTINO** según estado.
3. Si GPS deja de estar vigente, restaurar las métricas de la ruta blanca **con su etiqueta de viaje**, sin retener una ETA vieja. Si ni siquiera hay ruta verificada, mantener «—» y el mensaje existente.
4. No cambiar el motor `engine.estimate`, su verificación vial, la llamada `customer_reservation_position_v1`, los intervalos GPS, la cotización, el precio, la aceptación ni los permisos.

## QA

- Diff exclusivamente: 2 archivos de presentación del seguimiento + pruebas, workflow y este documento. Motores, SQL/Supabase, main, Android y producción intactos.
- Probar código y las suites originales de ruta, precio fijo, ETA, ritmo GPS, selección de viaje activo.
- Chrome a tamaño Samsung con RPC inyectada SOLO en memoria: (a) reserva aceptada sin GPS → ruta blanca verificada con distancia del viaje; (b) GPS fresco → distancia/ETA hasta origen; (c) pérdida de GPS → vuelta a métricas del viaje, sin posición inventada. Evidencias sintéticas, nunca publicarlas como prueba de vehículo real.
- Se requiere validación física posterior con conductor real y recepción de GPS privada. Solo mostrar kilometraje/ETA recogida cuando el servicio los confirme.

## Despliegue

Preview HTTPS QA aislado, sin producción, sin cambios de Supabase ni APK. Usar el alias QA CORS autorizado **solo tras CI verde**, conservando el preview anterior para rollback.
