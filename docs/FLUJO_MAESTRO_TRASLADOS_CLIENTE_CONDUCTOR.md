# TRASLADOS — Mapa maestro del producto y flujo integral

> **12/10/2026 · PRODUCCIÓN PWA v64 (ACTUAL):** [PR #65](https://github.com/marcelofgx-ctrl/traslados-android/pull/65) fusionado en `main` (`cfb85e07`). [GitHub Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38277644999), [PWA CI SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38277646051), revisión UI y paridad Android WebView v15 SUCCESS. Ajuste Samsung de tarjeta **Conductor disponible · Actualizando la llegada…**: animación de auto SVG verde menta con desplazamiento suave ida/vuelta también en estados **waiting** y **ready**, no solo checking/routing. Etiqueta «Animación ilustrativa · No es seguimiento en vivo», respeto a movimiento reducido y estados de red/bloqueo; sin simular GPS, inventar ETA ni adelantar las consultas. `app.js?v=64`, `styles.css?v=64`, caché PWA v64. **Mapa R25.0, Conductor 11.5-R5, Cliente Premium v15 firmadas y backend sin cambios**; **QA físico Samsung continúa pendiente**, FCM cliente cerrado sigue pendiente. Registro: [PWA_V64_ANIMACION_ESPERA_SAMSUNG.md](PWA_V64_ANIMACION_ESPERA_SAMSUNG.md). La v63 queda histórica.


> **12/10/2026 · PRODUCCIÓN PWA v63:** mejora del aviso «Tu paquete» en móvil. [PR #64](https://github.com/marcelofgx-ctrl/traslados-android/pull/64) fusionado a `main` (`c423b189`), [GitHub Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38274850653) y [CI PWA SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38274851041), CI revisión y paridad Cliente Premium v15 Android WebView SUCCESS. Al desplegar «Tu paquete» el aviso se hace visible con scroll solo necesario, indicador ＋/− y respeto a movimiento reducido; sin crear reserva ni tocar backend. `service-home.js?v=3`, caché `traslados-cliente-pwa-v63`, parámetros CSS/App `v63` (mismo código funcional). Mapa R25.0, Conductor 11.5-R5 y APK Cliente v15 firmadas sin cambios; **QA táctil en Samsung pendiente**, FCM con cliente cerrado pendiente. Registro: [PWA_V63_AVISO_PAQUETERIA_SAMSUNG.md](PWA_V63_AVISO_PAQUETERIA_SAMSUNG.md). El corte v62 queda histórico.


> **12/10/2026 · ACTUALIZACIÓN DE PRODUCCIÓN:** PWA **v62** — identidad orbital premium de servicios, fusionada [PR #63](https://github.com/marcelofgx-ctrl/traslados-android/pull/63) a `main` (`337c0226`) con CI web, regresión visual y paridad **Cliente Premium v15 WebView** OK. Portada con título «Tu viaje, bien llevado», emblemas SVG propios de ruta/reloj/paquete, nombres **Tu viaje**, **Tu reserva**, **Tu paquete** repetidos en texto curvo sobre medallas circulares, tarjetas legibles táctiles con microinteracción y `prefers-reduced-motion`. **Mensajería permanece próximamente**, disclosure informativo sin RPC. Caché **v62**, `service-home.js?v=2`, CSS/App `v62`. Mantiene reservas/ETA/chat/backend idénticos, sin recompilar Mapa Trayectos **R25.0**, Conductor **R5** ni APK Cliente **v15**; **QA táctil en Samsung permanece pendiente**, y FCM con app cerrada pendiente. Registro: [PWA_V62_ARTE_SERVICIOS_ORBITALES.md](PWA_V62_ARTE_SERVICIOS_ORBITALES.md). Las versiones v59–v61 son cortes históricos.


> **12/10/2026 · CORTE HISTÓRICO (v61):** PWA **v61** ya PUBLICADA por [PR #62](https://github.com/marcelofgx-ctrl/traslados-android/pull/62), fusionada en `main` (`4adf39c`). [GitHub Pages](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38256236300) **SUCCESS**, CI PWA e interfaz y paridad Cliente v15 **SUCCESS**. Portada independiente con **Viajar ahora**, **Programar un viaje** y **Mensajería y paquetería — Próximamente** (no crea solicitudes); navegación Inicio/Reservar/Mis traslados/Mi cuenta, instalación accesible desde Inicio y cuenta, formulario enfocado y texto contextual de disponibilidad/ETA. Se conserva **Cliente Premium APK v15 WebView** sin reinstalación ni cambio de firma; **Mapa Trayectos R25.0** y **Conductor R5** sin cambios. Módulo puro `service-home.js`, CSS/JS/SW v61, contratos y mensajería intactos. **QA táctil Samsung y flujo completo bidireccional pendientes**; push con Cliente cerrado sigue sin FCM. Registro canónico: [PWA_V61_PORTADA_SERVICIOS_Y_RESERVA.md](PWA_V61_PORTADA_SERVICIOS_Y_RESERVA.md). El manifiesto de v59 permanece como rollback histórico.
> **12/10/2026 · CORTE HISTÓRICO ANTERIOR (v59):** PR #59 + #60 + #61 **FUSIONADOS**; PWA **v59**, Conductor independiente firmado **11.5-R5 / versionCode 127**, Mapa Trayectos integrado firmado **0.1-R25.0 / versionCode 52**, Cliente Premium WebView **v15** compatible. RELEASE y certificados SHA-256 verificados, APK en descargas directas Pages conservando R24.8/R24.9. Conversación abierta desde avisos y centro rápido del globito, respuestas rápidas editables solo con Enviar explícito, polling 15 s Conductor foreground, sin push FCM real para Cliente cerrado. **Prueba táctil Samsung y extremo a extremo pendiente**. Documento canónico con enlaces, hashes, rollback y QA: [RELEASE_TRASLADOS_20261012_PWA59_MAPA25_CONDUCTOR_R5.md](RELEASE_TRASLADOS_20261012_PWA59_MAPA25_CONDUCTOR_R5.md). Las notas anteriores v58/PR borrador son HISTÓRICAS y quedaron reemplazadas por este estado.

**Fecha de consolidación:** 11–12/10/2026 · **Rama:** `feat/pwa-v58-guided-passenger-journey`
**Responsabilidad:** documento de referencia para futuras sesiones y cambios. A partir de ahora NO proponer cambios de una pantalla sin comprobar sus efectos sobre el flujo global.

> **Estado de verificación 11/10 ~22:30 UYT:** PR #58 fusionado en `main` (`a6d4c89`); PWA CI [38191119097](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38191119097) y Pages [38191118475](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38191118475) SUCCESS. **No hay QA física Samsung v58 completada**. Matriz y pasos: [QA_SAMSUNG_PWA_V58_ESTABILIDAD.md](QA_SAMSUNG_PWA_V58_ESTABILIDAD.md). PR [#59](https://github.com/marcelofgx-ctrl/traslados-android/pull/59) es **DRAFT, NO DESPLEGADO**: preserva detalles durante refresco, omite rerender sin novedades, invalida consultas previas al cambiar de cuenta y borra chat sensible del DOM; pruebas CI agregadas. No fusionar antes de QA/gestión de caché.

## 1. Qué estamos construyendo
Traslados es un servicio privado de transporte de **un conductor** y sus pasajeros. No es un clon multi-conductor de Uber. El sistema debe responder, con el menor número de decisiones visibles posible:
1. **¿Qué está pasando con mi traslado?**
2. **¿Tengo algo que hacer ahora?**
3. **¿Cómo me comunico y consulto el detalle?**

### Piezas operativas
- **Web pública de pasajeros / PWA:** `traslados-android/web-pasajero/`, GitHub Pages, paleta petróleo/dorado, lógica JS modular y SW. URL canónica `https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/`.
- **APK Cliente Premium v15:** Android WebView del mismo sitio, no recrear CSS ni flujo alternativo. Cada despliegue de PWA se refleja allí sin exigir una APK nueva; QA físico pendiente.
- **APK Conductor 11.5-R4:** presupuesto/aceptar, gestionar estados, contacto y conversación; lee Supabase con su PIN. Mantener control manual de precio definitivo.
- **APK Mapa Trayectos R24.9:** jornadas, viajes, posición, historial GPS local SQLite, sincronización cloud, presencia/ETA y panel privado `Estado del sistema`. Es app de conductor, NO cliente.
- **Backend** `marcelofgx-ctrl/traslados-web` y Supabase **Traslados** `zetaudvvutlouiqxopvg`. Contratos SQL/RPC y Edge; autenticación guest token/customer session/driver PIN. **NO** modificar el proyecto Oak & Ember.
- **Dos memorias de recorridos:** SQLite en teléfono y PostgreSQL en nube; históricos GPS/completos pueden archivarse en fase futura, sin borrar esperas ni rastros durante una jornada.

## 2. Recorrido unificado de cliente y conductor
| Momento | Autoridad operativa | Cliente ve | Interacción principal |
|---|---|---|---|
| Crear solicitud | Web/Cliente valida formulario, **Supabase** registra | A→B y paradas, horario, ocupantes, km/min/válor orientativo solo si ruta real | **Enviar solicitud** (Now/10) o **Revisar y confirmar** (programada) |
| Solicitud recibida | Registro `PENDIENTE` en Supabase | «Recibimos tu solicitud. Esperamos al conductor» y código | Consultar estado; conversar/cancelar solo si permitido |
| Presupuesto | Conductor establece `quote_status=ENVIADO` | Monto definitivo destacado una vez, sin repetir a lo largo de la ficha | **Aceptar** o **Rechazar presupuesto** |
| Aceptación | RPC `customer_quote_decision_v13` verifica agenda, horario, rol y estado | «Confirmada» solo si servidor devuelve éxito | Consultar recogida, contacto |
| Conductor en camino / llegó | Conductor publica `pickup_phase` | Etiqueta honesta «En camino»/«Llegó»; ETA solo si GPS/servicio permiten cálculo | Seguir, conversar |
| En viaje | Conductor actualiza fase/estado backend | Una línea de estado activo; detalles plegados | Consultar, conversar |
| Finalizado / cancelado / rechazado | Estados terminales del servidor | Resumen, tarifa acordada e historial consultable | **Ver detalles** / **Ver mensajes archivados** |
| Presupuesto vencido | Backend autoridad de vencimiento; clasificación visual conservadora PWA v55 | «Horario vencido», presupuesto original visible como referencia; sin CTA de aceptación | **Solicitar nuevo traslado**, sin crear automáticamente |

Nunca confundir: «Solicitud enviada» ≠ «presupuesto aceptado» ≠ «viaje confirmado» ≠ «conductor ya está cerca».
El **cálculo A→B** y el **ETA conductor→recogida** son dos métricas independientes. No inventar distancia/tarifa/ETA si el motor real falla.
El supuesto «disponible» necesita presencia reciente + jornada, agenda, estado operativo y GPS conforme backend.

## 3. Jerarquía visual maestra — PWA v58
- **Pantalla Reservar:** los 3 pasos conservan el flujo PWA v57: recorrido completo (origen/destino, km, minutos y valor orientativo), horario/modalidad, pasajero y CTA contextual. Cero reservas se crean por navegación.
- **Pantalla Mis traslados:** solo 3 vistas principales: **Próximos**, **Responder**, **Historial**. El sistema abre la vista más útil inicialmente (responder si hay presupuesto, próximos si hay activos, historial si no hay activos). Filtros técnicos, vista **Todos**, preferencias audio/vibración y aviso de push en «Más filtros y avisos».
- **Tarjeta resumida:** fecha/hora, estado actual, origen/destino **sin truncar**, importe **una vez** y «Ver detalles/Responder». Nada de cronología o chat extendido automáticamente.
- **Tarjeta abierta:** guía del estado y acción principal si existe. **Datos del traslado** y **Historial y etapas del viaje** plegables. Sin duplicar importe, origen o movimientos de rutina en la vista prioritaria. Cancelación con confirmación bajo la sección secundaria.
- **Mensajería:** pantalla/hoja dedicada privada, no el chat incrustado en la ficha. Un envío real solo con backend; conversación terminada = mensajes de solo lectura, SIN formulario ni botón Enviar. En cambio de sesión se cierra el chat para evitar retener datos de otra cuenta. Mensajes se refrescan si llegan con la pantalla abierta. Navegación teclado Esc/cierre/Tab.
- **Cabecera móvil:** logo/identidad se desplaza; una sola fila de pestañas se mantiene pegada arriba. Evitar dos barras superpuestas cubriendo los títulos.
- **Alertas:** los popups son novedades en primer plano y solo una por evento; no prometen push con app cerrada. Sonido/vibración exclusivamente por consentimiento; las preferencias permanecen disponibles en opciones avanzadas.
- **Visual:** petróleo texturado, dorado como énfasis exclusivo de la acción principal, verde discreto como estado, texto accesible, áreas táctiles ≥ 42px, nombres largos multilínea, una sola columna en móvil.

## 4. Contratos y compatibilidad: no negociar sin auditoría
- No cambiar RPC: `customer_list_reservations_v15`, `get_reservation_by_token_v4`, `customer_quote_decision_v13`, `customer_trip_chat_v1`, `customer_trip_chat_send_v1`, `customer_request_pickup_v1`.
- Conservar credenciales solo en rutas y capas existentes; no exponer token de invitado, PIN del conductor ni service role, ni coordenadas GPS precisas.
- Fases de viaje `pickup_phase` son etiquetas comunicadas por Conductor, no seguimiento GPS del pasajero.
- El estado del servidor manda sobre cualquier UI; la UI puede clasificarse conservadoramente como «vencido» sin mutar el estado remoto.
- PWA y WebView v15 deben ser idénticas. Compilar Android DEBUG como regresión sin nuevas funciones nativas.
- No limpiar ni crear reservas, mensajes, jornadas o GPS reales como prueba automática. No confundir los respaldos internos del 11/10 con datos operativos.
- Mantener elementos secundarios accesibles aunque queden detrás de plegables.

## 5. Diagnóstico previo a v58
Capturas Samsung 11/10 ~21:55 UYT: una reserva finalizada por UYU 890. En una sola ficha extendida aparecían origen/destino dos veces, importe dos veces, barra de 5 estados, movimientos, chat incrustado con controles de escritura DESHABILITADOS y aviso «archivada», además de cuatro filtros grandes, desplegables, preferencias y texto largo sobre push. UX redundante, aunque el backend devolvía correctamente la reserva finalizada y sus dos mensajes.
El cierre del flujo real con GPS/conductor/mensajes requiere QA físico. Diferencias de estado entre capturas tomadas antes y después de finalizar pueden ser transiciones temporales; no afirmar corrupción sin una línea temporal de la base.

## 6. Lista de verificación para cada publicación
1. Todas las pruebas Node: contratos de reservas, estados, citación vencida, alertas, chat, rutas, agenda, diseño y prueba especial `guided-passenger-flow-v58-smoke.cjs`.
2. Compilación Android DEBUG de Cliente v15, sin cambiar paquete ni firmas.
3. Pages build+deployment SUCCESS correspondiente al SHA **main** resultante del PR.
4. Samsung Chrome y WebView real: pantalla estrecha sin scroll lateral, sin dos barras superiores encoladas, tarjetas cerradas con dirección aeropuerto completa, agenda con filtros secundarios y presupuesto solo una vez.
5. Chat activo: escritura/envío/lectura con token/cuenta reales autorizados; chat finalizado: no hay textarea/botón de envío, historial disponible, cerrar/Escape, login/logout sin retención de otro usuario.
6. Autorefresco de 20 s mientras visible, mensajes y transiciones sin duplicados; bloqueos, vencimiento y errores del servidor no generan confirmaciones falsas.
7. **La prueba funcional de punta a punta exige un viaje de prueba explícitamente autorizado**; no crear uno por nuestra cuenta.

## 7. Pendientes separados: NO agregarlos furtivamente en v58
- **Push con app cerrada:** no existe aún; requiere FCM, permisos, registro de tokens consentido, Android y backend de envío. Solo avisos mientras PWA abierta actualmente.
- **Chat con acuse, lectura o adjuntos:** todavía no forma parte del contrato; no fingir entregado/leído.
- **Modo paquetería:** producto posterior con modelos de datos, cotización, estados y seguridad propios; no sobrecargar el flujo de pasajeros.
- **Retención GPS:** mantener datos completos de paradas/movimiento en jornada; definir estrategia de compresión/archivado posterior con medición de espacio real.
- **QA físico** Samsung/Conductor/Mapa bajo red/GPS: sigue pendiente aunque todos los tests CI pasen.
- **UX post-solicitud:** asegurar mensajes contextuales «registrada, no confirmada» y seguimiento con código; optimizar en versión siguiente cuando el flujo v58 esté validado.
