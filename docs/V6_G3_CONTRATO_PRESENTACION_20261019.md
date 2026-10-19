# V6 · G3 · Contrato de presentación único y seguridad visual
**19/10/2026 — Laboratorio aislado. NO es la PWA funcional.**

## Objetivo
La V6 nace con un único diseño en `web-pasajero/pruebas/visual-v6`. Este cambio prepara el paso de maqueta a UI real mediante **un contrato declarativo de lectura**, sin introducir una nueva lógica de precios, GPS, reservas o backend.

## Qué se consolidó
- `view-model.js` es un módulo puro: recibe un *snapshot* y devuelve datos de presentación normalizados. No usa DOM salvo en `render(snapshot,doc)`, que actualiza **solo `textContent` y estados visuales**.
- Las cuatro pantallas comparten origen/destino, precio, km/min y ETA del mismo `snapshot`. La maqueta mantiene datos **ilustrativos explícitos**, nunca datos del pasajero.
- Contrato de instantánea:
  - `route.key` identifica la selección completa (origen, destino, paradas).
  - `route.originKey` identifica el punto de recogida.
  - `route.origin.label`, `route.destination.label`, `route.stops[]` describen elecciones.
  - `route.quote.status === "verified"` **y** `route.quote.routeKey === route.key` habilitan la tarifa y las métricas; distancia positiva y minutos finitos obligatorios. No recalcular tarifa localmente.
  - `pickup.status === "verified"` **y** `pickup.originKey === route.originKey` habilitan tiempo/distancia de recogida. En caso contrario, «Sin estimación».
  - `customer.displayName/phoneLabel` y `schedule.label` vienen de la sesión y del horario seleccionado **sólo en la integración futura**.
- Se rechazan importes caducados, paradas incompletas, claves de ruta discordantes, números nulos/NaN y recogida que ya no corresponde al origen actual. No se inventan mapas, horas ni valores.
- CSS sigue contenido en un solo `styles.css` V6. Botón principal siempre visible en el borde inferior **con relleno de seguridad al final del contenido**. Cambio responsive al ancho móvil y estados de importe pendiente sin desbordar.

## Adapter G4 a preparar **después de aprobación visual**
Crear un lector limitado de los valores del motor existente de V5.3:
1. Cuando se seleccione origen/destino/paradas, leer el estado consolidado — nunca duplicar consultas al proveedor.
2. Sólo publicar un presupuesto si coincide con la clave del trayecto actual y es marcado como verificado por `TrasladosRoutes.verified`.
3. Recibir ETA sólo del sistema de presencia existente, respetando cuotas de consulta y el origen actual.
4. Usar sesión Supabase existente para nombre y contacto; no crear segundo login ni PIN nuevo.
5. Preservar los estados confirmados de jornada del conductor, horarios y reserva; conectar el botón Solicitar sólo con el mecanismo original bajo guardas y pruebas.
6. Tratar los fallos de red como ausencia de estimación, sin conservar el valor anterior.
7. Asegurar Web/PWA y Android back/resume con las pruebas ya existentes.

## Pruebas de este cambio
- `tests/static-v6.cjs`: componentes aislados, sin API, con 4 etapas y estilos responsive.
- `tests/view-model-v6.cjs`: mismo precio en Recorrido y Confirmar, rechazo de ruta vieja, parada incompleta, números inválidos y recogida de otro origen.
- `check-visual-v6-lab.yml`: dos pruebas Node que no compilan Android ni descargan paquetes.
- Captura y aprobación física Samsung pendientes; CI de código **no demuestra** calidad estética ni reemplaza revisión del usuario.

## Límites
Solo laboratorio. No tocar `web-pasajero/` pública, `pruebas/map-first/` V5.3, Mapa Trayectos, Supabase, rutas de producción, tarifas, historial ni reservas. No registrar SW/PWA nueva. Usuario aprueba estética **antes** de integrar funcionalidades o promover.
