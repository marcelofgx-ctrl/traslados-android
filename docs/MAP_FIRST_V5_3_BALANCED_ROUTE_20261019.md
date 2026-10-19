# Visual V5.3 — Pantalla Recorrido equilibrada
Fecha: 19/10/2026. Sólo PWA experimental `web-pasajero/pruebas/map-first/`.

## Evidencia Samsung / problema
En Visual V5 Fase 1 publicada, la pantalla 1/4 muestra el mapa completo antes del origen y destino. Aunque los cálculos de ruta, precio y ETA funcionan, la cartografía domina casi toda la altura visible y obliga a bajar demasiado para editar direcciones y encontrar Continuar. La franja champagne de importe es muy alta. La experiencia visual resulta poco equilibrada.

## Cambio acotado
- Usar **los mismos** nodos HTML de origen y destino editables antes del mapa. No duplicar el geocodificador ni repetir selectores.
- `map-first-visual-v5.js`: mantener el elemento cartográfico real, recolocándolo después de `#destination-picker` dentro del mismo anfitrión en modo Tu viaje. Al cambiar a Tu reserva, dejar que el controlador V3 restaure su DOM. El mapa sigue representando datos verificados del motor existente.
- `premium-route.js`: sólo ajustar el tamaño de renderizado real del mapa en `mf-v5-phase1` a un intervalo de 204 a 250 px, según alto de pantalla. El renderer sigue ajustando todos los marcadores y la geometría, sin recortes artificiales ni falsos puntos.
- `map-first-visual-v5.css`: ordenar los paneles flex (direcciones → cartografía + importe → paradas opcionales). Reducir la banda de precio a aprox. 78 px, entradas mínimas a 57 px y pie de mapa. Mantener el destino legible incluso en dos líneas. No agregar una hoja nueva de retoques.
- El aviso de ETA, la lista real de paradas, la lógica Atrás, el botón Continuar, la cuenta, el historial y la pantalla Confirmar permanecen sin cambios.
- Refrescar PWA aislada con manifest `qa=v5_balanced`, nuevos assets y caché.
- Actualizar pruebas legadas para no depender de una query literal y reforzar contrato del nuevo orden y mapa real.

## Aceptación
1. Samsung 360–412px: en primer vistazo origen y destino visibles; cartografía proporcional sin recortar marcadores; banda clara de importe y distancia/tiempo coherentes. Continuar alcanzable sin pasar por varias pantallas completas.
2. Origen/destino modificados → conserva autocompletado, repinta geometría, importe y ETA apropiados.
3. Paradas 0/1/2: editor se abre/cierra, respeta orden e invalida presupuesto hasta completar puntos.
4. Estado sin ruta verificada no inventa importes; ausencia de GPS o error vial identificables.
5. Atrás Android + botón Atrás interno preservan los puntos y no producen cálculos antiguos.
6. Comprobar que el globito Samsung no tape Editar ni Continuar; de lo contrario se tratará en fase posterior, no desviar el alcance ahora.
7. **No reemplazar web oficial ni APK Mapa.** Requiere pruebas automáticas y validación visual Samsung antes de aprobar producción.

## Causa exacta de la regresión visual
El JS de fase 1 ejecutaba `route.prepend(map)`, situando físicamente el mapa por encima de origen y destino; y el motor de presentación asignaba una altura de 276–348 px. Ahora los dos puntos quedan antes del mismo mapa, renderizado a la altura real adecuada. Este cambio es específico de Recorrido, no reescribe el motor vial.
