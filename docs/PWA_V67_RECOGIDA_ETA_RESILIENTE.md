# PWA v67 — ETA de recogida resiliente y tarjeta sin leyendas

**12 de octubre de 2026 · PUBLICADA EN MAIN.** PR #68 (`e7b2f74c`), [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38308613485), [PWA CI SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38308613906), revisión UI y compatibilidad Android WebView v15 correctas. **Prueba táctil Samsung pendiente.**

## Diagnóstico con captura de Samsung (17:57)
- El pasajero ve «Conductor disponible para recogerte», origen Avenida Italia 5359 y una animación verde de recogida; la llegada permanece «Actualizando…», sin kilómetros/minutos.
- Comprobación SQL de solo lectura a las 17:59: jornada y presencia habilitadas, sin viaje/pausa/ocupación, GPS actualizado en ~4 segundos, precisión ~3,8 m, disponibilidad pública `available:true`. **No es un fallo acreditado del GPS ni de agenda**.
- ETA de recogida usa por separado la Edge `pickup-eta` v5 (Gate SQL con timeout 7s y proveedor vial HTTP con timeout 7,5s). La PWA anterior cancelaba su solicitud de ETA a los **9s** y actualizaba un contador de petición `pickupEtaRequestSeq` con cada consulta pública de 15s; una respuesta lenta legítima podía ignorarse. No afirmamos que ese mecanismo explique por sí solo todos los intentos fallidos ni que ORS esté siempre disponible: el resultado real debe verificarse en Samsung/Edge.
- La caption «Animación ilustrativa · No es seguimiento en vivo» duplicaba el contenido visual debajo del automóvil.

## Correcciones — solo PWA
1. `eta-engine.js`: timeout acotado a **18s** para permitir el gate SQL + la consulta vial legítima, con cancelación si excede el plazo. No aumenta frecuencia ni número de solicitudes.
2. `app.js`: clave única de identidad de pasajero+origen para deduplicar ETA **en curso**; un refresco de disponibilidad de 15s ya **no descarta** el resultado si sigue siendo la misma sesión y dirección. El resultado de una dirección o sesión anterior continúa rechazándose; las respuestas no verificadas jamás llenan kilómetros/minutos.
3. Si falla ETA, mostrar «No pudimos calcular la llegada. Reintentaremos» o «Por ahora no hay tiempo estimado de llegada. Reintentaremos», y mantenerlo durante el periodo de reintento. Si hay cuota agotada mantener un backoff de 90s. No repetir indefinidamente «Actualizando…» ocultando el error.
4. Eliminar **completamente** la leyenda debajo del recorrido ilustrativo, manteniendo el automóvil SVG menta, animación ida/vuelta y respeto por movimiento reducido. Se conserva el estado operativo en el título y los campos verificados, sin revelar coordenadas.
5. Nueva prueba determinista de red simulada `pickup-eta-stability-v67-smoke.cjs`: consulta lenta, dos refrescos de presencia, respuesta exitosa tardía, backend `route_unavailable`, timeout de red, cambio de origen durante vuelo y respeto al modo programado. Suite histórica y WebView mantienen compatibilidad.
6. Versiones: `app.js?v=67`, `styles.css?v=67`, `eta-engine.js?v=2`, caché `traslados-cliente-pwa-v67`. **No se recompilan** Cliente Premium v15, Conductor R5 ni Mapa R25.0.

## Límites y QA físico
- Esta modificación no genera reservas ni toca datos de producción, servidor Edge, SQL, claves o GPS. Los intervalos siguen **disponibilidad 15s**, ETA **25/45s** y cuota tras error **90s**, con una sola ETA concurrente por origen+sesión.
- Una falla real de proveedor vial debe reflejarse honestamente sin inventar distancia/tiempo. Revisar desde Samsung en Chrome y Cliente WebView con origen válido, confirmando que se muestran las cifras cuando el proveedor responde; en caso de fallo, aparece el aviso y reintenta.
- Probar en pantalla pequeña, sin leyenda debajo del automóvil, sin scroll horizontal ni vibración. FCM con Cliente cerrado sigue pendiente.
