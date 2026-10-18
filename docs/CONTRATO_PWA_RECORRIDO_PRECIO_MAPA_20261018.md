# UX-RECORRIDO-001 — Contrato de la pantalla «Tu viaje / Recorrido» v1.0

**Decisión funcional:** 18/10/2026 · **Estado:** APROBADO PARA IMPLEMENTACIÓN, **NO IMPLEMENTADO NI PUBLICADO POR ESTE DOCUMENTO**.
**Superficie:** una sola Web/PWA del pasajero (`web-pasajero/`). **No crear APK Cliente**. Capturas reales de Samsung 18/10/2026, 11:36 UYT; no subir capturas con ubicaciones privadas al repositorio.

## 1. Orden y propiedad visual — un dato, un lugar

| Posición | Componente único | Qué se muestra |
|---|---|---|
| 1 | **Recogida del conductor** | Estado verificado, distancia **conductor → origen** y ETA hasta recogida, con antigüedad y advertencia de estimación. **Una sola aparición**. |
| 2 | **Origen GPS** | Dirección seleccionada y botón «Actualizar GPS». **Sin repetir ETA del conductor ni estado de ruta**. |
| 3 | **Itinerario** | Origen, destino y paradas opcionales (agregar/editar/ordenar conservando los controles existentes). |
| 4 | **Mapa del viaje** | Solo recorrido vial **origen → paradas → destino** cuando hay geometría válida. Origen/destino, paradas y trazado visibles, encuadre útil en móvil. No dibujar automóvil ni simular GPS del conductor. |
| 5 | **Resumen del viaje, inmediatamente bajo el mapa** | **Precio orientativo** como valor visual predominante; **km del viaje** y **duración estimada** en segundo nivel. |
| 6 | **Continuar** | Acción existente, respetando las reglas de validación de reserva/precio; no modificar su semántica por estética. |

La recogida y el viaje son **dos trayectos distintos**. Nunca reutilizar los km/min de recogida como km/min del viaje, ni mezclar una tarifa con distancia al conductor. Mantener accesibilidad, formato Uruguay/es-UY y estilo petróleo/champagne.

## 2. Estados comprobables del mapa y del resumen

| Condición | Mapa | Resumen precio/km/tiempo |
|---|---|---|
| Falta origen o destino | Mapa geográfico **de referencia**, rotulado como tal; invitar a completar recorrido | No mostrar cifra ni ruta inventada |
| Geocodificación completa, calculando | «Calculando recorrido…»; conservar referencia, **sin traza falsa** | «Calculando…», sin estimaciones heredadas |
| Ruta **verificada** con geometría de calles utilizable | Dibujar polilínea real, O / paradas / D y ajustar encuadre; descripción «Ruta estimada del viaje» | Km y tiempo de **esa misma ruta**; precio solo si `referenceFareUyu` válido y positivo |
| Ruta verificada con km/min, **sin geometría dibujable** | Explicar «No se pudo dibujar el recorrido», no representar una línea recta o una traza simulada | Se pueden mostrar km/min verificados, rotulados como estimación; precio únicamente si verificado |
| Proveedor agotado, respuesta no válida o sin conexión | Referencia explícita «No pudimos verificar la ruta» y acción **Reintentar cálculo** | Precio «A confirmar» y km/min «Sin estimación»; nunca usar cálculo no validado |
| Cambian origen, destino o paradas | Invalidar **simultáneamente** geometría, km, duración y tarifa previas hasta nueva verificación | No dejar cifras de la ruta anterior |

**Fuentes de verdad:** `route-engine.js` (`verified`, `estimate`, `referenceFareUyu`, geometría), adaptador existente `app.js::renderRouteSummary` y `premium-route.js`/`premium-map.js`. La tarifa la determina la API de precios vigente, **no** una multiplicación local ni un valor introducido para embellecer. `eta-engine.js` y estado de conductor se mantienen independientes.

## 3. Unificación / limpieza obligatoria

- **Eliminar** duplicación del «0,5 km · 5 min» entre la tarjeta superior de recogida y el resumen «Origen seleccionado con GPS».
- **Eliminar** la línea de km/min/precio incrustada en el pie textual del mapa cuando exista el nuevo resumen dedicado.
- **Ocultar** `#route-overview` legado en modo premium sin duplicar lógica ni lanzar cálculos paralelos; conservarlo como adaptador para flujos antiguos donde corresponde.
- La tarjeta plegada `#route-folded-preview` y los resúmenes de **Confirmación** pueden repetir información al cambiar de paso (son otras pantallas), pero no deben **duplicar cifras simultáneamente en la pantalla Recorrido**.
- Mantener la posibilidad de reintento real `#route-overview .route-retry` sin segundo motor de búsqueda ni rutas ficticias.
- **No tocar** autenticación, disponibilidad comercial, PIN/vinculación, backend, GPS privado, agenda, estados de reservas, tarifa parametrizada ni APK Mapa.

## 4. Criterios de aceptación G3–G6

1. Regresión automática: con geometría vial verificada se ve polilínea real y marcadores O/D; con paradas, también sus marcadores; el mapa cubre el trayecto sin cortes.
2. Regresión: si el proveedor no entrega geometría válida, **no se dibuja una línea falsa**; se conserva referencia rotulada y reintento.
3. Regresión: el bloque **bajo el mapa** muestra exactamente un importe orientativo (o «A confirmar»), km y duración del **viaje**, y nunca la ETA de recogida.
4. Regresión: la ETA/distancia **conductor → origen** aparece una sola vez en el paso Recorrido; la tarjeta de GPS es solo dirección y actualización.
5. Regresión: al cambiar paradas/origen/destino, geometría, precio, km y minutos antiguos desaparecen mientras se recalcula.
6. Probar estados sin conductor disponible, red fallida, tarifa no devuelta, 2+ paradas, destino faltante, proveedor recuperado y actualización desde caché.
7. QA móvil Samsung: viewport 360–412 px, legibilidad de precio, contraste, scroll, botón Continuar y mapa sin cortes; comparar visualmente con el criterio de las dos capturas de 11:36.
8. **G4/G6:** PR funcional separado y limitado, tests JS/HTML/CSS + selector real, CI verde, versionado de assets/Service Worker, verificar GitHub Pages y PWA instalada, luego QA física. Solo entonces registrar la **versión PWA efectivamente publicada** (no inventar número hoy). No compilar APK Android por este cambio web.

## 5. Rama y dependencias

La cadena de PWA **#119 → #122 → #123 → #125** permanece en QA, no fusionada en el momento de redactar. **Implementar sobre el último HEAD compatible**, verificando su estado real antes de tocar `app.js`, `premium-route.js`, `premium-route.css`, `premium-map.js`, `index.html`, `sw.js` y pruebas. No duplicar arreglos existentes ni publicar las correcciones pendientes sin las puertas de su cadena.

**Estado de este contrato:** decisión de diseño versionada v1.0; implementación visual, mapa dibujado, nueva PWA y pruebas Samsung **PENDIENTES**. Toda futura modificación que altere estas reglas exige nueva revisión del contrato y evidencia de QA.
