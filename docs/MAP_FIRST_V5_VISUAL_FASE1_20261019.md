# Rediseño Visual V5 — Fase 1 · Recorrido y Confirmar
Fecha: 19/10/2026 · **Solo PWA de pruebas**, no reservas reales.

## Diagnóstico
La mejora V1–V4 era perceptible solo en detalles: demasiadas tarjetas anidadas, diagonales repetidas, exceso de bordes y el mismo orden largo en cada paso. La fase 1 establece una composición realmente distinta sin tocar el motor funcional.

## Recorrido · primera impresión
- **Mapa real primero**, con los nodos originales y geometría del motor vial existente; JS mueve el elemento en el DOM en modo Tu viaje, no copia un mapa ni crea marcadores ficticios.
- Cartografía clara a ancho total, esquinas superiores suaves y pie de mapa con aviso explícito «sin tráfico en vivo».
- Banda **champagne** integrada debajo de la cartografía: tarifa real grande, km y minutos en dos indicadores sin tarjetas anidadas.
- Debajo, origen/destino editables en una sola lámina oscura sin dos cajas con borde; paradas plegadas y ETA de recogida diferenciada, proveniente del motor existente.
- Los estados de proveedor caído mantienen «A confirmar»/«Sin estimación» legibles; nunca se rellenan valores artificiales.
- El botón Continuar no cubre el mapa y conserva su manejador anterior.

## Confirmar · cambio de composición
- Sustituye la textura de «pase con perforaciones» por **dos zonas** en una sola estructura editorial: cabecera + precio sobre papel champagne y recorrido + horario/pasajeros sobre petróleo limpio.
- El precio grande es el **mismo elemento** `#urgent-route-recap` que recibe los cálculos; no hay una segunda tarifa.
- El itinerario completo conserva `.premium-confirm-route`, incluyendo paradas y control para editar.
- Mantiene la acción original `#urgent-send` y su validación, pero el modo de prueba impide generar reservas.
- El origen/destino y los datos cargados se mantienen al navegar hacia atrás.

## No tocar
NO modificar `web-pasajero/` oficial, endpoints Supabase, motor vial, buscador de direcciones, cálculos de km/tarifa/minutos, ETA, lógica de recogida, PWA estable, APK Mapa o historial.
El JS visual solo se carga con `?mapfirst=1` y meta `traslados-preview`.

## Criterios de aceptación y G0–G6
- V5 debe verse distinta de V4 **sin buscar diferencias**: mapa por encima de las direcciones; franja champagne clara integrada; Confirmar con cabecera/precio claro e itinerario oscuro compacto.
- Recorrido con 0, 1 y 2 paradas; cambio de origen/destino refresca mapa y precios de fuente real.
- Sin GPS/servicio vial: mostrar ausencia de estimación, no inventar dibujo, coste o ETA.
- Atrás táctil/Android (V5.2) sin salir de la PWA; volver y revisar que las cifras regresen.
- Pantallas 360/393/412 px: sin solapamiento con globito externo, barra inferior y teclado; si el globito se mueve, elementos críticos editables deben seguir visibles.
- Confirmar resume exactamente la misma tarifa y duración mostradas en Recorrido.
- Verificar aislamiento de la PWA instalable de ensayo respecto de la estable.
- Test `tests/map-first-v5-visual-phase1.cjs` en ambas pipelines, además de pruebas V4 y V5 anteriores.
- **QA visual física en Samsung pendiente**; controles de código NO constituyen validación de la estética real.
- Fase 2 futura: Cuándo + Tus datos + globito; fase 3: pruebas de integración y publicación productiva solo después de aprobación.

### Archivos nuevos
`map-first-visual-v5.js` y `map-first-visual-v5.css` contienen esta composición, separados del código productivo. No se agregan motores duplicados. Reglas base V3/V4 siguen como compatibilidad de las etapas 2/3; no extender esos CSS con retoques futuros: consolidación en sistema visual definitivo posterior a QA.
