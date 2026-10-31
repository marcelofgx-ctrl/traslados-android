# Quick Trip P1.5 · Estado real de ETA, actividad y precio legible (QA aislada)
Fecha: 31/10/2026. Base P1.4: `b47296a981dd93ef593b8bcf19aa3a4adae4f4a9`. Rama: `qa/quicktrip-p15-eta-cycle-fare-20261031`.

## Evidencia Samsung recibida
El origen Roque Sáenz Peña 1711 y destino Aeropuerto de Carrasco generaron 3,1 km / 7 min / $124. La tarjeta de conductor muestra disponibilidad suficiente para intentar ETA pero luego «Llegada todavía sin estimar». Esto no demuestra si faltó GPS, hubo problema de red o falló el enrutador de recogida; el motivo real debe observarse sin exponer ubicación privada.

## P1.5: modificaciones limitadas
- Los motores `eta-engine.js`, `route-engine.js`, `pickup-cadence.js`, geocodificación, tarifa, seguimiento y reservas **no se modifican**. Tampoco `quicktrip-clean.js`, los CSS aprobados P1.3 y P1.4, Android o Supabase.
- El controlador existente `app.js` aporta SOLO código seguro y permitido de falla de ETA en `data-eta-failure-code`, sin posición, identidad ni sesión. También expone el momento más temprano de autorización para reintentar según el backoff existente; NO garantiza que se ejecute una consulta en ese segundo.
- La vista nueva `pickup-cycle-ui.js` lee esos estados, muestra un pulso CSS sutil en checking/routing/waiting/issue, y actualiza un reloj local de 1 s SIN nuevas llamadas a red. Nunca anuncia una ETA inventada ni anima como búsqueda estados ready/blocked/owned/scheduled. La disponibilidad sigue consultándose en el controlador original a 15 s; el enrutamiento ETA conserva 25/45 s y el enfriamiento de cuota de 90 s.
- `quicktrip-p15-eta-cycle-fare.css` usa el MISMO nodo de precio verificado dentro del resumen y evita recorte horizontal en pantallas Samsung estrechas de 360/390 px. La solicitud original sigue siendo única y el botón no se mueve a una posición flotante.
- Versiones y caché PWA renovadas. Los contratos anteriores se actualizaron exclusivamente para reconocer esta versión de assets; siguen comprobando el diseño y los motores originales.

## Pruebas
GitHub Actions ejecuta contratos QA de P1.3/P1.4 y E2E Chromium con HTTP SIMULADO: disponibilidad verdadera, ruta/fare calculados, falla `route_unavailable` de ETA, indicador y conteo, ancho del precio a 390/360. No usa GPS real, no envía reservas, no toca credenciales.

## Pendiente honesto
Para declarar reparada la distancia real del conductor se necesita evidencia de la propia sesión del Samsung: resultado seguro de `pickup-observability.js` y validación con conductor autorizado publicando GPS. Este lote NO modifica el backend ni afirma tener una ETA real.

**Prohibiciones**: merge, producción, Supabase real, nuevas reservas reales, Android instalado, APK, costos adicionales.