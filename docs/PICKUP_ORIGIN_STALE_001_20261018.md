# PICKUP-ORIGIN-STALE-001 — Aviso de desplazamiento del pasajero

**18/10/2026 · Propuesta funcional en QA sobre la cadena #119 → #122 → #123 → #125. No publicada.**

## Causa raíz demostrada

La PWA actualiza disponibilidad cada 15 segundos y ETA del conductor hacia el **origen seleccionado** cada ~25 segundos si conduce o ~45 segundos si está detenido. `getCurrentPosition` solo se pide al tocar «Usar/Actualizar GPS» y no hay GPS continuo del pasajero. Por lo tanto, una ETA que dice «hace 18 s» puede continuar midiendo hacia la dirección que quedó seleccionada antes de que el pasajero se trasladara.

## Contrato y protecciones

- Solo tras tocar voluntariamente «Usar/Actualizar GPS» comienza `watchPosition` durante la preparación de una solicitud, **con formulario visible**. No se activa al abrir la PWA, ni para ubicaciones elegidas mediante búsqueda.
- Con GPS actual de precisión ≤80 m, umbral ≥250 m desde el origen elegido y dos lecturas separadas por ≥4 segundos, muestra el aviso **«Te alejaste del punto de recogida»**. Si no hay GPS válido, no hay mensaje ni alteración de la reserva.
- **Actualizar origen** ejecuta el selector GPS real con una nueva lectura y geocodificación, invalidando claves anteriores de ETA y rutas; los precios solo provienen del cálculo vial verificado vigente. **Mantener origen** descarta el aviso para esa selección, sin volver a insistir.
- Ningún cambio silencioso de recogida. Detener `watchPosition` al salir de la pantalla, ocultar pestaña, durante envío de reserva o después de confirmar. La consulta de GPS observada se queda en memoria de la página: **no se guardan ni envían coordenadas del seguimiento**.
- Se conserva el botón manual si watch no está disponible o pierde permiso. El backend y la posición del conductor no se modifican.
- No reescribir el contrato UX-RECORRIDO-001, ni el motor de búsquedas, ni el GPS de Mapa.

## G3–G6

El test `pickup-origin-drift-smoke.cjs` comprueba movimiento real, ruido/precisión, selección manual, retención/actualización, segundo muestreo, ocultamiento y bloqueo de solicitud. Gate añadido al workflow `check-web-pasajero-review.yml` además de las pruebas existentes.

Pendiente probar en Samsung con la web visible y una solicitud aún sin enviar, comparar con PWA instalada y validar que no consume ubicación en segundo plano. **No fusionar la cadena PWA ni declarar publicación por pasar pruebas sintéticas.** 
