# MAP-FIRST-REAL-001 — Implementación visual real encima de UX-RECORRIDO-001

Fecha: 18/10/2026 (UYT). **Rama de revisión, no publicada**.

## Fuente y alcance (G0–G1)
- Base exacta: PR #145 \`feat/pwa-ux-recorrido-001-v83-qa-20261018\`; no reimplementar sus correcciones de geometría, tarifa, paradas ni invalidación de cálculos antiguos.
- Prueba de referencia: PR #146 (HTML de demostración, **no se integra directamente** por incluir valores simulados).
- Un único producto pasajero Web/PWA; no crear APK Cliente. No tocar APK Mapa/Conductor, backend, Supabase, PIN, búsqueda de direcciones, agenda, tablas ni datos GPS.
- Código del experimento real: \`map-first-review.css\`, \`map-first-review.js\`, adaptación de altura para **mapa real** en \`premium-route.js\`, referencia de recursos en \`index.html\`, prueba estática y controles CI.

## Modo de prueba seguro
\`?mapfirst=1\` activa únicamente composición visual, sobre la Web/PWA pasajero, en el paso **Tu viaje → Recorrido**. Sin parámetro, diseño anterior conservado. El modo no realiza llamadas nuevas ni modifica reservas. El despliegue de GitHub Pages de producción sigue apuntando a main y no se toca. El parámetro no sustituye una plataforma de preview: hasta un despliegue aislado la funcionalidad estará disponible solo en artefacto de revisión o entorno local.

## Experiencia
1. Cabecera reducida con navegación de 4 pasos real (Recorrido / Cuándo / Tus datos / Confirmar).
2. Tarjeta original de disponibilidad y ETA real conductor→origen visible, si la API la entrega; estados no verificados sin cifra inventada.
3. Origen y destino compactos, usando widgets reales (GPS, picker, fallback de direcciones).
4. Mapa de viaje protagonista, entre origen/paradas/destino **solo con geometría vial validada** por \`roadEngine.verified\`. Sin vehículo simulado.
5. Resumen en banda con precio orientativo, km y minutos del viaje proporcionados por el servidor, o leyendas de fallo verificables. No confundir con ETA de recogida.
6. Paradas desplegables después del mapa; navegación real de pasos y botón Continuar sin modificación.
7. Ocultar tarjeta duplicada de ubicación, manteniendo acción GPS original en selector origen. Reservas programadas intactas.

## QA obligatorio (G3–G6)
- Modo \`mapfirst=1\` opt-in y selectores estrictos; no tocar otras vistas ni formularios.
- Pantallas 360/390/412 px y teclado de direcciones; sin botones o precio cortados.
- Sin origen/destino, proveedor 503, geometría válida y sin geometría, dos paradas y cambio de origen/destino: seguir contrato PR #145.
- Acciones Cuando→Tus datos→Confirmar intactas; no reserva real automática, no rutas GPS simuladas.
- CI review, packaging y prueba MAP-FIRST-REAL-001 verdes; QA Samsung real pendiente.
- Actualizar SW/cache y versionado de assets en PR final de integración, **nunca publicar ni hacer merge solo por CI verde**.

Estado: primera iteración visual de integración, a validar; no implica GPS comercial reparado ni mapa de seguimiento conductor en viaje activo.
