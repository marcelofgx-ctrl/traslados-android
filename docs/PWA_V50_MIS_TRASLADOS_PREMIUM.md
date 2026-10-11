# Traslados PWA v50 — Mis traslados, definición funcional y despliegue

## Propósito
Sustituir 25 tarjetas enormes y repetitivas por una agenda personal compacta, elegante y funcional. La vista principal debe responder sin leer un párrafo: **qué día viajo, de dónde a dónde, en qué estado está mi solicitud, cuánto me presupuestaron y qué acción debo realizar ahora**.

## Pantalla cerrada (tarjeta)
- Fecha y hora en la cabecera (uruguaya, sin convertir UTC de la fecha civil), código secundario.
- Chip con punto y estado real: Pendiente, Presupuesto para responder, Confirmada, En viaje, Finalizada, Cancelada, Rechazada.
- Iconos Origen «◉» y Destino «◆», «✈» si aplica, texto breve visual con dirección íntegra en el desplegable.
- Importe del presupuesto **solo si fue enviado** y es positivo, no inventar cifras.
- «Responder» ante presupuesto; «Ver detalle» en otras situaciones.
- Se mantiene la misma identidad aprobada petróleo/dorado, textura, jerarquía y tactilidad.

## Pantalla desplegada
- A/B completos, paradas intermedias, fecha/hora, pasajeros, comentarios.
- Línea de progreso visual según **estado de la base**. Sólo se iluminan etapas realmente alcanzadas.
- Bloque Presupuesto: importe final enviado por Conductor, incluye y descripción.
- Movimientos: registro y envío/aceptación de presupuesto con marcas de tiempo **realmente provistas por el servidor**; no inventar tiempos de llegada, salida, inicio o fin si no existen en la RPC.
- Botones contextuales: aceptar/rechazar presupuesto o cancelar donde la RPC y el estado lo permitan. No añadir acciones engañosas.

## Navegación y agrupación
- Pestañas Próximos / Por responder / Historial / Todos.
- Agrupar por Día o Mes y filtrar por estado. Los grupos son plegables, primeras dos fechas abiertas por defecto.
- Plegado de una reserva se conserva al refrescar. Historial no contamina Próximos.
- Orden cronológico ascendente en Próximos, descendente en Todos/Historial.
- Próximos mantiene solicitudes vencidas que siguen pendientes en el backend; no se borran ni declaran finalizadas.
- Pantallas vacías específicas, botón Actualizar accesible y recuento de presupuestos que requieren respuesta.

## Flujo de presupuesto
Conductor manda presupuesto en la RPC existente -> cambia `quote_status=ENVIADO` y `quote_sent_at`.
Cliente autenticado o invitado ve botón Aceptar/Rechazar -> confirma la decisión -> RPC `customer_quote_decision_v13`.
La nueva RPC requiere sesión válida y reserva perteneciente al cliente **o** token público aleatorio secreto de una reserva invitada. Bloquea la fila y agenda, protege contra dobles aceptaciones y responde con el estado confirmado. Para urgencias evita reglas de antelación de las programadas pero rechaza un pedido excesivamente antiguo; no cambia cotizaciones existentes.
Si la RPC falla, no se anuncia aceptación. Si responde con código, refrescar lista y presentar estado resultante.

## Avisos reales dentro de la aplicación
- Mientras la PWA está abierta/visible, consulta incremental cada 20 s tras el primer acceso/consulta inicial y compara `quote_sent_at`, importe y estado de los mismos IDs.
- Popup de presupuesto al detectar un **nuevo** presupuesto; vista previa de importe y botón para abrir la reserva concreta.
- Vibración breve **optativa** mediante toggle, dependiente de soporte/permiso real del navegador.
- Modo primer acceso obtiene línea base sin notificaciones antiguas repetidas; no se vibra sin consentimiento, ni durante modo oculto.
- Una PWA en segundo plano o cerrada **NO** tiene push remoto implementado. Se requiere infraestructura de suscripciones, VAPID y entregas desde un entorno privado con opt-in y revocación. No prometer ni simular avisos en segundo plano hasta que eso exista.

## Conductor en camino / llegada
El backend actual expone `PENDIENTE, ACEPTADA, EN_VIAJE, FINALIZADA, CANCELADA, RECHAZADA` y estados auxiliares. No existe una RPC Conductor que envíe los eventos «En camino» o «Llegué» como cambios independientes.
El estilo incluye una viñeta pulsante **solo en estado de movimiento proveniente del servidor**. Para que se active antes del viaje es necesario instrumentar dichos eventos desde la app Conductor y mostrarlos en la RPC de Cliente. No inferir viaje, ocupación ni llegada de una posición GPS estática.

## Arquitectura y compatibilidad
- Nuevo módulo puro `web-pasajero/trip-board.js`: agrupación, filtros, estados y detección de cambios, separado de I/O.
- `app.js`: rendering, RPCs, polling en primer plano, navegación y consentimiento.
- PWA JS/CSS/SW v50; Cliente Premium Android v15 conserva WebView y firma, Mapa Trayectos R24.8 sin cambios.
- API y ETA de recogida del conductor siguen independientes y no se mezclan con tarifas ni el seguimiento de estado de una reserva.
- Backend en `marcelofgx-ctrl/traslados-web/supabase/migrations/20261011_customer_quote_decision_v13.sql`.

## QA / política de publicación
- CI ejecuta smoke del módulo puro, conservación de HTML/estados, revisión de reservas y compatibilidad Android DEBUG.
- Migración SQL aditiva y permisos por rol verificados sin insertar reservas falsas.
- Sólo declarar Pages/CI SUCCESS cuando se vea la ejecución efectiva. Pruebas en Samsung, emisión real de un presupuesto, vibración y aceptación real aún pendientes de ensayo autorizado.


## Resultado de publicación confirmado
- PR [#45](https://github.com/marcelofgx-ctrl/traslados-android/pull/45) fusionada en `main` (commit `cd532864`), tests PWA, revisión y Cliente v15 Android DEBUG **SUCCESS**. Pages [38136356048](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38136356048) **SUCCESS**.
- Backend PR [#10](https://github.com/marcelofgx-ctrl/traslados-web/pull/10) fusionada (commit `337aee78`); migración `customer_quote_decision_v13` aplicada con **SUCCESS** y roles registrados/invitados verificados read-only.
- Popup/vibración: solo en PWA abierta, opción vibración explícita; **no existe push de segundo plano**. Seguimiento de esa fase en [GitHub #48](https://github.com/marcelofgx-ctrl/traslados-android/issues/48).
- Comprobación física de recepción/aceptación de presupuesto en Samsung: **pendiente**. No se generaron reservas ficticias.
