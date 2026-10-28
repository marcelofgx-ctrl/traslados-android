# Quick Trip — aceptación abre mapa, no pantalla vacía

Hallazgo de Samsung: view-seguimiento mostraba una pantalla vacía con
«El conductor aceptó tu viaje y el precio acordado» y un botón «Ver estado
en Mis traslados». La rama live solo permitía dibujar seguimiento si
tripBoard.metadata(r).tone==="moving". El backend registra «ACEPTADA» antes
de «EN_CAMINO»: la UI autoredirigía al seguimiento, pero el seguimiento
bloqueaba la representación con su early return.

Alcance QA:
- La aceptación confirmada se representa como una fase propia en el mapa
  de seguimiento. Siempre muestra origen y destino y una ruta vial real,
  o solo sus coordenadas verificadas si no existe ruta.
- En «preparando recogida» el mapa NO muestra vehículo hasta que se
  autoriza GPS por customer_reservation_position_v1, manteniendo privacidad.
- Al pasar a EN_CAMINO, EN_PUNTO o EN_VIAJE, la misma pantalla se
  actualiza sin volver a Mis traslados ni duplicar consultas cada 4 s.
- Mantiene navegación manual, botones inferiores, cálculo de rutas,
  precios y autenticación. Sin ediciones a Supabase, Android ni producción.

Pruebas: estado real ACEPTADA sin EN_CAMINO, mapa presente sin automóvil
ficticio, transición a EN_CAMINO con posición autorizada; dos tamaños
móviles, motores originales y pruebas de rutas/precios y regresión GPS.

Publicación: únicamente actualizar alias Testing tras CI verde y
verificación de Preview. Conservar deployment de alias anterior para
rollback inmediato. Main y Web pública sin cambios.
