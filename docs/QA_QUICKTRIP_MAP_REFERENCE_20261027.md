# Quick Trip · Mapa de referencia profesional (QA)

Base: qa/quicktrip-map-pan-zoom-20261027 @ 253eb03bb02c7f98b4b5e83ff2c06b5720075931.

**Objetivo visual:** composición compacta y cartografía protagonista como las referencias entregadas: calles y lugares legibles, origen/destino, controles flotantes mínimos, scroll natural y seguimiento separado.

**Cambios de presentación y mapa únicamente:**
- Mapa de Tu viaje 340px (antes 318px). Panel de botones compacto de 46px, una columna (+/−, centrar y explorar), píldora de estado arriba.
- Cuando el motor aún no tiene ruta verificada, mostrar **solo dos puntos geográficos reales O/D**; nunca inventar una polilínea ni distancia.
- Cartografía de fondo con color uniforme cuando falla una tesela; no ocultar el crédito © OpenStreetMap.
- Controles de zoom, arrastre y restauración existentes; sin ayuda permanente cubriendo el mapa.
- Cámara/zoom manual local a cada mapa. No se escribe GPS ni se altera ningún dato de reservas.
- Conservar los motores actuales de búsqueda, tarifas, rutas, ETA, reservas, y autorización de seguimiento GPS.
- Revisión de caché QA separada. No editar Android, Supabase, Cloudflare, main o Web comercial.

**Validación obligatoria:** Chromium 390/412px; O/D cuando la ruta no se pudo verificar; cero líneas ficticias; zoom, pan, reset, scroll vertical y ausencia de desbordamiento; no escrituras al backend; regresión de reserva, ETA y seguimiento.

**Rollback:** volver a la rama de origen/commit 253eb03bb02c7f98b4b5e83ff2c06b5720075931; el alias Testing no se modifica antes del éxito de CI. QA no garantiza exactitud píxel a píxel de imágenes de referencia ni sustitución de librerías cartográficas.
