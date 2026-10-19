# V5.2 — Android Atrás unificado y estados viales legibles

**Fecha:** 19/10/2026 · **Alcance:** `web-pasajero/pruebas/map-first/` exclusivamente.

## Hallazgos
Las pruebas previas resolvieron el caso básico Android Back → regresar a Inicio, pero el botón Atrás **interno** seguía ejecutando `premiumJourney.open(...)` y por lo tanto agregaba otro registro de historial. La siguiente pulsación física de Android Back podía devolver a la pantalla que acababa de abandonarse. Las capturas aportadas muestran también que el texto «Sin estimación» se rompe dentro de los cuadros angostos y que la flecha Atrás de la cabecera puede ser invisible.

## Correcciones
- Una única pila de estados para Android Back y Atrás interno en las etapas Cuándo / Tus datos / Confirmar. Si existe estado previo, Atrás interno usa `history.back()`, no una segunda transición con `pushState`.
- Cada entrada de historial contiene solo vista, etapa y profundidad, nunca dirección, usuario, teléfono ni datos privados.
- Pictograma Atrás explícito, con mayor contraste y superficie táctil.
- Resumen de ruta `data-verified="false"` usa disposición compacta en una columna principal y dos métricas anchas; evita cortar palabras a mitad.
- Sin cambios en ruta verificada, tarifa, GPS, disponibilidad real, búsqueda, horarios, backend ni datos.
- Nuevas pruebas para la secuencia combinada Atrás táctil → Atrás Android → Inicio, además de primer plano y BFCache. Se conservan controles V4 y V5 anteriores.
- PWA experimental V4.2 con SW único en su carpeta, caché renovada y parámetro QA `v5_2`. No se habilita envío de reservas.

## Pruebas físicas pendientes
Samsung Chrome / PWA instalada: abrir Recorrido → Cuándo → Tus datos, pulsar Atrás de pantalla, luego Atrás de Android: debe pasar a Recorrido y después Inicio, sin bucles. Cambiar a otra app y volver; km/min/precio deben recuperarse de rutas verificadas cuando el proveedor responda. Simular red fallida: mensaje sin estimación debe caber sin separar letras ni inventar cifras. Comprobar paso Confirmar.

Solo fusionar al ensayo cuando los controles G1/CI estén verdes. Nunca confundir ejecución de tests con prueba física completada.