# Quick Trip QA · dos tramos y solapa funcional · 2026-10-28

**Base congelada:** `2e2e53db54806d6ae419aa76650ccf585b4dbb01`. La versión de Preview que precede este cambio seguirá identificada para rollback: `dpl_FC3E9CbjU4HtZDNG5GjUoYyvQFr6`.

- Mapa principal de **origen→destino**: línea blanca, geometría vial verificada, precio intacto.
- Solo para una reserva **aceptada**, con token de pasajero y GPS reciente (hasta 75 s), consultar `customer_reservation_position_v1` usando el contrato ya existente. Calcular **auto→origen** mediante el mismo motor vial y dibujarlo en **verde** como segunda polilínea real; el automóvil se dibuja en la posición GPS autorizada.
- Si el GPS no llega, se vence, el conductor aún no acepta o no se puede verificar su ruta: **no mostrar línea verde ni automóvil ficticio**. La línea blanca del viaje y los puntos O/D permanecen.
- Al iniciar `EN_VIAJE`, queda visible la ruta activa del auto hacia destino. El seguimiento utiliza el mismo temporizador de 4 s que ya existía; la vista modal suprime los sondeos duplicados del panel anterior y detiene su temporizador al cerrarse.
- Nueva acción **Abrir mapa grande · dos recorridos** desde la pantalla de seguimiento autorizada. El modal oscuro reutiliza exactamente la RPC existente, sin nuevas credenciales ni backend.
- La solapa inferior del mapa pasa de estática a **compacta / expandida**, mediante botón central o gesto vertical sobre su cabecera. Compacta muestra la opción y CTA; expandida muestra origen, destino, distancia, duración y los estados. El mapa de fondo jamás intercepta el gesto de la solapa.
- En modo previo a la reserva solo se enseña **origen→destino**: la ETA pública no divulga GPS del conductor y no autoriza deducir un tramo de aproximación.
- `quicktrip-map-focus.js` conserva toda la validación y acción original de `urgent-send`; el precio sale del servicio original, nunca se recalcula en este módulo. Sin cambios de Supabase, Cloudflare, Android, main o Web pública.

## Evidencias exigidas antes de publicar Testing

CI debe comprobar ruta blanca y verde distintas, posición GPS autorizada, privacidad negativa (sin token no abre), ausencia de vía ficticia en `EN_VIAJE`, solapa arriba/abajo, cero escrituras al backend, regresiones de rutas/precio y pantallas 390/412 px. Si falla, NO actualizar alias Testing.

**No declarar físico/final:** falta verificación real desde Samsung de conductor y pasajero en movimiento.
