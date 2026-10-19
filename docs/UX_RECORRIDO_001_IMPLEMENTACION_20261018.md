# UX-RECORRIDO-001 — implementación visual aislada (18/10/2026)

**PR de revisión, no publicado**. Contrato vigente: `docs/CONTRATO_PWA_RECORRIDO_PRECIO_MAPA_20261018.md`.

- Solo el paso **Tu viaje / Recorrido**, sin cambiar reservas, tarifa, GPS, auth ni backend.
- Tarjeta superior existente: única ETA y kilómetros **conductor→origen**.
- Tarjeta de origen GPS: solo dirección y botón. Los contadores duplicados quedan ocultos en modo «Tu viaje».
- Mapa: usa geometría del motor actual verificado y marcadores O / parada P / D. Si no hay geometría, muestra referencia sin polilínea inventada, distinguiendo ruta vial calculada sin geometría de proveedor fallido.
- Debajo del mapa: **precio orientativo** destacado solo si llega del servidor, **distancia/duración del viaje**; nada de precio a partir de línea recta.
- Ante cambios de origen, destino o paradas: descartar instantáneamente dibujo e importe anteriores mientras el motor calcula la nueva combinación; mantener el botón de reintentar ya existente.
- Garantías automatizadas `ux-recorrido-001-smoke.cjs` y la batería vigente Web/PWA. Sin número público nuevo hasta QA y versionado final con Service Worker (v82 actual).
- QA móvil Samsung pendiente: vista 360–412 px, carga/tensión del mapa, marcadores reales, fallos 503, mapa y km/precio/no duplicación. No se publicará hasta comprobar los pasos.
