# Quick Trip — Especificación final P1.3 · Pantalla «Tu viaje» aprobada
**Fecha:** 30/10/2026 · **Referencia:** última imagen premium elegida por el usuario · **Base:** `9c6a4625062cfcb02558960aa7d476ecdc4873c4` · **Rama:** `qa/quicktrip-p13-approved-mock-compact-20261030`.

## Propósito
Presentar en una sola pantalla compacta la solicitud de viaje, sin mapa duplicado, botones «Ver mapa» ni deslizamientos de seguimiento. Mantener TODAS las funciones de la Web/PWA probadas previamente. Los campos desplegables y el teclado pueden necesitar scroll; el estado plegado se ajusta al alto del teléfono sin recortar información ni superponer el botón.

## Contrato campo por campo

| Bloque visual | Dato | Fuente real única | Sin dato / Error | Regla |
|---|---|---|---|---|
| ORIGEN, pin verde | Dirección seleccionada, municipio | `picker("origin")`, `record.origin`, IDE/GPS permitido por el usuario | Buscar origen / GPS explícito | NO inferir coordenadas, NO desplazar origen automáticamente |
| DESTINO, pin coral | Dirección seleccionada | `picker("destination")`, `record.destination`, buscador | Buscar destino | No cambiar la identidad de los pines |
| Aviso GPS | Se alejó del punto elegido | `pickup-origin-drift.js`, monitor del origen | Oculto cuando no aplica | Mantener acciones originales «Actualizar origen» y «Mantener origen» |
| TU CONDUCTOR | Disponible/conectado/ocupado/desconocido | `publicDriverAvailability(force)`, `public_driver_availability_v1` | Mostrar estado auténtico, no conductor ficticio | Una única tarjeta `#pickup-presence` |
| Aprox. al origen | Km conductor → pasajero | `pickup-eta`, posición GPS vigente y ruta de recogida verificada | «—», calculando o reintento | Jamás mezclar con kilómetros de origen→destino |
| Llegada estimada | Minutos de conductor → origen | mismo `pickup-eta`, cadencia existente | Sin estimar si la consulta falla | No inventar ETA ni extrapolar una ubicación |
| Horario de recogida | Ahora / otro horario | `#journey-when-select` y opciones originales | Estado original de selección | Desplegable conserva validaciones |
| Tus datos | Nombre, teléfono, pasajeros y notas | campos originales `person-name`, `person-phone`, `passengers` | Solicitar los datos faltantes | No duplicar campos ni identidad |
| Agregar paradas | Paradas elegidas y campos | `.premium-route-stops`, `#stop-wrap`, `#login-for-stops` | Mantener bloqueo de login si aplica | Mover DOM *original* bajo «Tus datos», y restaurarlo al programar |
| Tu recorrido · DISTANCIA | Km origen → destino incluyendo paradas | motor vial `TrasladosRoutes.verified(current)`, atributo `[data-final-route-distance]` | «—» hasta verificar | Nunca usar distancia del conductor |
| Tu recorrido · DURACIÓN | Minutos origen→destino | mismo motor vial verificado `[data-final-route-duration]` | «—» si no hay ruta válida | No usar ETA de recogida |
| Precio del viaje | Precio cerrado UYU | cotización original `[data-final-route-fare]`, política de tarifa vigente | «A confirmar» / deshabilitar envío si falta verificación | No recalcular con CSS ni desde el GPS del conductor |
| SOLICITAR VIAJE | Acción única de envío | `#urgent-send` y controlador existente | Deshabilitado según reglas reales; error auténtico | Un botón, no crear otro ni solicitar reservas en QA |

## Jerarquía visual de la referencia
1. Encabezado compacto con logo Quick Trip, regreso y «Tu viaje».
2. Dos tarjetas conectadas ORIGEN y DESTINO, pines grandes verde/coral **idénticos al diseño aprobado**.
3. Aviso GPS discreto, solo si aplica, bajo destino.
4. Tarjeta contenida «Tu conductor» con estado y dos métricas reales en dos columnas.
5. Tres plegables alineados: Horario, Tus datos, Agregar paradas.
6. Resumen con «Tu recorrido» a la izquierda, **precio real destacado a la derecha** y distancia/duración en dos celdas inferiores.
7. Botón dorado original debajo del resumen, **en flujo normal**, sin cubrir nada ni duplicarse.
8. Paleta petróleo oscuro/dorado sobrio, márgenes coherentes, bordes tenues; no mapa secundario ni burbujas agregadas.

## Transiciones posteriores
**Enviar** → queda pendiente, el pasajero ve estado auténtico. **Aceptación real única** → abre automáticamente el único mapa negro de seguimiento. **Atrás** → Inicio; otro toque a «Tu viaje» con una sola reserva activa válida → mapa. Reservas programadas, canceladas, finalizadas o ambiguas no disparan apertura ficticia.

## Estado y garantías
Se reutilizan TODOS los motores desde la base sin alterarlos: `route-engine.js`, `premium-route.js`, `eta-engine.js`, `pickup-cadence.js`, `pickup-observability.js`, `reservation-live.js`, `premium-map.js`, `quicktrip-map-focus.js`, `booking-contracts.js`, `app.js`, funciones Supabase, SQL, Android. Solo cambia la **composición visual**, hoja CSS y referencias de caché PWA. El usuario no solicitó despliegue de producción.

## Puertas de aprobación
- GitHub diff estricto sobre base, prohibiendo cualquier cambio de motor/producción.
- Contratos existentes: ruta vial, tarifa fija, ETA, disponibilidad, cadencia, deriva GPS, entrada y salida del mapa, viaje programado.
- Chrome Samsung 390×844, pantalla plegada y alerta; confirmar orden, pines, elementos DOM únicos, tarifa encima de las dos métricas, botón no superpuesto, retorno «Programar»↔«Tu viaje» con paradas originales, sin mapa duplicado ni desbordamiento horizontal.
- Evidencia QA con SHA exacto, link CI, preview HTTPS **solo si pruebas verdes**.
- La prueba de navegador con estados sintéticos NO confirma disponibilidad real de un conductor, ETA GPS ni aceptación entre dos teléfonos. Eso requiere ensayo en Samsung usando el circuito real. Si falla, preservar el Preview QA anterior y revertir alias, no `main`.

**Regla:** no declarar listo para producción sin prueba de reserva real, kilómetros/minutos origen/destino, tarifa, conductor/ETA privada y aceptación→mapa.
