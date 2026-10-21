# Mapa + Conductor — dos pantallas, una aplicación (QA 20/10/2026)

## Comportamiento

- Producto único: Mapa Trayectos instala Conductor dentro de la misma APK y comparte los datos privados de la aplicación. **No publicar ni mantener una APK Conductor separada.**
- Mapa sigue siendo la única `Activity` operativa: el nuevo `UnifiedDriverPanel` es una vista de pantalla completa superpuesta en el mismo `FrameLayout` y animada horizontalmente. **Nunca usar `startActivity` para navegar entre Mapa y Conductor.**
- Ya NO existe la antigua pestaña lateral flotante grande. La entrada es deslizar hacia la izquierda desde la franja interior derecha del mapa o tocar el pequeño chevrón en el título de la cabecera. El mapa, su GPS y la jornada permanecen activos debajo.
- Volver a Mapa: deslizar a la derecha desde la franja interior izquierda o tocar la flecha de retorno del panel. Al abrir el panel debe tener mayor elevación gráfica que TODOS los controles elevados del mapa; al cerrar no queda ninguna capa fantasma.
- Conductor muestra su cabecera, tarjeta de jornada, contadores, solicitudes y acceso al historial incluso cuando el dispositivo todavía no se vinculó. No inventar reservas ni dibujar una pantalla vacía; mostrar estado de vinculación honesto.
- Conductor **no muestra formulario de PIN**. Si falta vincular la instalación, lo explica dentro del panel con tarjetas de estado. Play tampoco abre un diálogo PIN de forma automática: la autorización GPS comercial se gestiona solo desde Menú de Mapa por decisión explícita.
- La autorización inicial para esta instalación está en Mapa → Menú → **Conductor y recogidas**. En una instalación nueva pide una sola vinculación de conductor y permiso explícito para publicar disponibilidad GPS solo durante jornada. La autenticación de RPC en Supabase sigue activa; **no convertir estas RPC en anónimas**.
- Luego de vincular, el cambio de estado es automático: iniciar jornada + GPS válido + sin pausa/viaje/ocupado y sin bloqueo de agenda → candidato disponible. Cerrar jornada, pausar, iniciar viaje o marcar ocupado bloquea la recogida. El backend conserva las comprobaciones de GPS/heartbeat menores a 90 segundos y de agenda.
- La ubicación exacta nunca se muestra al pasajero: solo distancia vial y tiempo autorizados. Si falta GPS o la red falla se informa estado desconocido/no disponible, no se inventan kilómetros.

## Identidad de las QA

Las APK QA previas generan un `applicationId` nuevo por cada compilación. Android las trata como instalaciones diferentes. Por eso **no comparten** datos privados, credenciales, vinculación ni autorización GPS. Esta característica causa el caso «jornada abierta en Mapa QA pero conductor no localizable» observado el 20/10/2026 en Supabase. El proyecto no debe reactivar remotamente el consentimiento ni copiar secretos de otra instalación para ocultar el problema.

Para próximas versiones que deban conservar el vínculo, distribuir actualizaciones con el **mismo package id, firma estable y versionCode creciente**, usando firma segura en CI; nunca exportar claves a archivos de descarga ni saltarse autorizaciones.

## Pruebas de aceptación Samsung pendientes

1. Instalar APK de integración QA correctamente firmada. Autorizar acceso al conductor **solo desde Mapa** y verificar que el menú reconozca la vinculación.
2. Play / ubicación precisa / jornada sin viaje: comprobar `driver_live_presence` habilitado, con posición, `received_at` reciente; consultar `public_driver_availability_v1` y luego recogida y ETA desde la Web.
3. Capturar la pantalla Conductor en emulador y Samsung para verificar: nada de controles del mapa por encima, fondo petróleo/champagne, tarjeta y pestañas con contenido incluso sin vinculación, y retorno al mapa deslizando. Validar que el deslizamiento horizontal no afecte la manipulación del mapa ni el deslizador de jornada.
4. Pausa, viaje activo, Stop, GPS desactualizado o pérdida de conexión: la web deja de indicar disponible.
5. Nueva sesión o reinicio del mismo paquete: vinculación conservada sin mostrar PIN en Conductor.

La prueba de compilación Android no demuestra que la recogida funcione realmente. No modificar tarifas, rutas, reservas ni el historial para estas pruebas.

## Regresión visual detectada y corregida (21/10/2026)

El primer panel embebido fue capturado en QA visual con la cabecera, velocímetro, brújula y panel de jornada del mapa dibujados **por encima de Conductor**: son vistas con `elevation` elevada, y `bringToFront()` no es suficiente por sí solo. Corrección aislada: `driverPanel.setElevation(dp(48))` y cambios de barras de sistema al abrir/cerrar, conservando su estado anterior. `mapatrayectos/scripts/mapa-visual-capture-with-panel.sh` captura Mapa, Conductor y regreso con el deslizamiento, y la prueba visual controla que no sea una superficie vacía.

**Estado de entrega:** no declarar QA visual aprobada ni publicar como oficial sin revisar la captura posterior a la corrección y la prueba física; el backend de reservas aún requiere vinculación segura desde Mapa.


---

# PLAN MAESTRO FUNCIONAL DEFINITIVO · CONDUCTOR V2
Fecha de especificación: 2026-10-21. Alcance: **solo Conductor embebido en la APK de Mapa**.
Fuentes verificadas: \`conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java\` (legado), \`mapatrayectos/src/main/java/uy/com/mapatrayectos/driver/UnifiedDriverPanel.java\`, \`UnifiedDriverGateway.java\`, \`UnifiedDriverPolicy.java\`, y PR #185. Este documento define aceptación, NO prueba que todas las funciones ya estén implementadas.

## 0. Decisiones irrevocables de alcance
- **No cambiar funcionalidad de Mapa Trayectos**: Activity, mapa, rutas, orientación, GPS, permisos, Play/Stop, jornada, overlays, ETA, tarifas ni disponibilidad pública. Solo Conductor puede cambiar. Si algún requisito precisa una modificación compartida, abrir un cambio de alcance separado y pedir aprobación antes.
- Producto único: Mapa + Conductor como dos páginas de la misma APK. **No lanzar APK Conductor separada**. Navegación por deslizamiento, sin reiniciar Activity ni detener tracking.
- Diseño aprobado por el usuario: fondo negro, panel gris carbón #252525, tarjetas negras, texto blanco, auxiliar gris, acentos verdes sobrios. No dorados, degradados, vidrios ni banners. UI nativa, compacta, fluida, sin controles superpuestos.
- En solicitudes entrantes **es obligatorio conservar la tarjeta grande original** de origen → destino, distancia, duración, importe, botones Rechazar/Aceptar, con valores reales. Cuando no hay petición, la tarjeta se **contrae**: nunca reservar alturas muertas.
- Sin PIN recurrente en vista Conductor. Vinculación inicial segura desde configuración ya existente de Mapa. No eludir RLS, RPCs, credenciales ni consentimiento GPS; no copiar PIN de otra instalación.
- Cada capacidad del legado tiene destino explícito, aunque la UI cambie. No retirar el código de respaldo hasta verificar paridad funcional con pruebas reales.
- Fuente de verdad: Supabase para reservas, estados, agenda, disponibilidad, mensajes, tarifas y sus mutaciones autorizadas; Mapa conserva GPS/jornada. No usar mocks o valores ficticios en producción.

## 1. Sistema visual y comportamiento contextual
Tres capas:
1. Encabezado compacto y persistente: QUICK TRIP / Conductor, estado de jornada real, indicador de vinculación/sincronización, 3 contadores Nuevas/Aceptadas/En viaje. Tocar un contador debe filtrar su categoría, salvo datos no sincronizados.
2. Navegación superior: **Peticiones**, **Agenda**, **Histórico**, **Resumen**. Cuatro pestañas compactas, preferiblemente icono + etiqueta accesible; opción Herramientas (engranaje, no una quinta pestaña ancha).
3. Cuerpo dinámico de una sola vista de desplazamiento vertical, sin scrolls anidados o alturas mínimas artificiales.

Estados de la página:
- **PENDIENTE DIRECTA**: tarjeta original expandida, acción Aceptar/Rechazar a primera vista, datos reales. Múltiples peticiones: tarjetas ordenadas con primera visible, navegación o scroll sin colisiones.
- **PENDIENTE PROGRAMADA (según contrato)**: tarjeta claramente identificada por fecha/hora, cotizada o por presupuestar; no aplicar automáticamente el flujo directo a reservas que el servidor todavía trate con propuesta/presupuesto.
- **ACEPTADA/RECOGIDA**: tarjeta plegable que puede autoexpandirse por acción reciente o etapa activa; acciones contextuales Ir al origen, Voy en camino, Llegué, Iniciar viaje; contactos/mensajes en segundo nivel.
- **EN_VIAJE**: tarjeta principal expandida con Finalizar, destino, comunicación y estado de telemetría, sin ocultar botones críticos.
- **SIN SOLICITUDES**: estado compacto con 1–2 líneas, accesos reales a agenda/último viaje/actividad. Si hay una próxima reserva, destacar su tarjeta; si no, no inventarla.
- **HISTÓRICO VACÍO**: nota mínima y acceso Agenda/Resumen; jamás 310dp de caja vacía.
- **SIN VINCULAR**: pequeño aviso de estado + enlace a flujo preexistente en Mapa, sin emitir operaciones ni inventar contadores.
- **SIN RED / CARGANDO / ERROR / SIN DATOS**: estados diferentes; mostrar última actualización y datos cacheados señalizados, botón Reintentar, no convertir error en “0 viajes”.
- Cuando se despliega una fecha o una reserva, las otras pueden contraerse; conservar selección y scroll razonablemente, sin saltos ni parpadeos. Hacer accesible la cabecera completa y su nombre.

## 2. Inventario exhaustivo del legado y destino de cada capacidad

| Función del antiguo Conductor, corroborada en código | Destino V2 | Diseño/acción | Situación en nueva rama |
|---|---|---|---|
| Bandeja nuevas/aceptadas/en viaje y contadores | Peticiones | tres contadores + filtro al tocarlos | listado/contadores básico; filtro pendiente |
| Tarjeta reserva expandible, código, pasajero, fecha, origen/destino, paradas, comentarios, cantidad de pasajeros | Peticiones/Agenda/Histórico | resumen cerrado y detalle expandible | básico expandible; paradas y comentarios/participantes pendientes |
| Aceptar/rechazar directa de precio ya fijado | tarjeta de nueva petición | dos botones visibles con confirmación | implementado en V2, validar mutación y respuesta |
| Reserva programada con presupuesto, cotización enviada/editable, cálculo interno de tarifa, peajes, extras y validación de agenda | Agenda/detalle de petición | acción contextual Presupuesto SOLO cuando backend lo exija | **pendiente de portar**; evitar restaurar presupuesto intermedio para viaje directo |
| Filtros NUEVAS, COTIZADAS, CONFIRMADAS | Peticiones/Agenda | chips compactos | **pendientes** |
| Recogida SIN_INICIAR → EN_CAMINO → EN_PUNTO → EN_VIAJE → FINALIZADA | Peticiones/tarjeta | botones adecuados a etapa, sin saltos | básico; validar con servidor y fases null/SIN_INICIAR |
| Ruta al origen/destino, llamada, WhatsApp | detalle de reserva | iconos/botones compactos | presentes; validar en teléfono |
| Chat pasajero; respuestas predeterminadas; lectura y envío | detalle de reserva + acceso rápido | mensajes y “Llego en 5 min” | presente básico; validar orden/no duplicación/recarga |
| Histórico finalizadas, rechazadas, canceladas | Histórico | días agrupados, filtros, totales | implementado básico; faltan filtros de estado/fecha personalizada |
| Agenda hoy/semana/mes/fecha elegida; próximos viajes y recordatorio | Agenda | grupos por día y próximo viaje destacado | fechas/grupos básicos; calendario personalizado y recordatorios pendientes |
| Bloques de horario crear/ver; configuración día operativo y buffers, solicitudes programadas/inmediatas | Herramientas → Horarios/Bloqueos | formularios/detalles reutilizando contratos existentes | **pendiente de portar**, sin cambiar Mapa ni backend |
| Ajustes de tarifas, mínimo, peajes, espera, distancia de recogida, extras | Herramientas → Tarifas | editor acotado, confirmación explícita | **pendiente de portar**, sin sobrescribir precio configurado |
| Avisos de nueva solicitud, cambios de cliente, viaje, vibración, recordatorio inteligente | Herramientas → Avisos | preferencias y prueba local | **pendiente de portar**, monitoreo en 2.º plano por validar |
| Cola de alertas y anticlonado de notificaciones | Peticiones/sistema de avisos | banner o tarjeta única, deduplicado por id de reserva | **pendiente**, no duplicar monitor actual |
| Estado ONLINE/MONITOR, último sync; error y reintento | encabezado + Herramientas → Diagnóstico | etiqueta discreta y diagnóstico claro | parcial |
| GPS por viaje, traza, distancias reales, velocidad, puntos, GPX, ver recorrido, reintentar | tarjeta en viaje e Histórico → Detalle/Telemetría | opción secundaria solo si datos disponibles | **pendiente de portar en panel V2**, no duplicar GPS de Mapa |
| Ajustes de radar OSM complementario | Herramientas → GPS | configuración explícita; nunca representarlo como fuente oficial | pendiente / requiere revisión de límites |
| Datos vehículo | Herramientas → Vehículo | ficha compacta APP-0061 si corresponde a configuración real | pendiente |
| Compartir detalle y añadir al calendario | menú contextual de tarjeta | iconos compartir/calendario | pendiente |
| Diagnóstico, copiar estado, envío de logs | Herramientas → Diagnóstico | sin credenciales privadas | pendiente |
| Modo prueba de avisos no destructivo | Herramientas → Pruebas | simulación local, cero reservas reales | pendiente |
| Salir/vinculación, sesión/conexión | Mapa posee vinculación; Conductor muestra estado | SIN PIN en Conductor | enlace informativo presente; emparejamiento real por validar |

La funcionalidad avanzada descrita como pendiente **no debe marcarse como completada solo porque el antiguo archivo aún exista en el repositorio**: el panel nuevo es una implementación distinta.

## 3. Contrato exacto de estados
Estados del servidor prevalecen sobre texto de UI:
- PENDIENTE: bandeja Nuevas; distinguir modalidad directa fija frente a programada cotizable mediante datos del contrato y \`quote_status\`. Nunca asumir que todas aceptan igual.
- PENDIENTE + presupuesto ENVIADO: “Cotizada” con propuesta; no esconder la reserva.
- ACEPTADA: Aceptadas. \`pickup_phase\`: SIN_INICIAR o vacío, EN_CAMINO, EN_PUNTO. Botón contextual solo si transición autorizada. \`CONFIRMADA\` debe interpretarse correctamente donde el legado lo deriva de quote_status; no ignorarla.
- EN_VIAJE: En viaje; permitir finalizar con confirmación y actualizar backend.
- FINALIZADA/RECHAZADA/CANCELADA: Histórico; nunca acciones de aceptar, iniciar o finalizar; permitir leer detalle/telemetría/compartir.
- Si el backend rechaza una transición, no animar estado como exitoso; recuperar snapshot remoto e informar la causa sin filtrar secretos.
- Coherencia de contadores y filtros con el mismo snapshot válido; con error conservar “desactualizado” en vez de pintar cero.

## 4. Distribución por pantalla
### A Peticiones (portada)
Solo lo operativo: contadores, filtros por estado, tarjetas dinámicas, actualización discreta. Petición entrante = diseño de referencia intacto; otras tarjetas se pliegan. Próximo viaje aparece al estar programado y no haber urgencias.
### B Agenda
Filtros Todas/Hoy/Semana/Mes/Fecha, estado Nuevas/Cotizadas/Confirmadas, grupos acordeón por fecha, próxima cita, disponibilidad/bloqueos como accesos discretos; pulsar una reserva abre detalle. Los filtros no eliminan reservas ni mutan el backend.
### C Histórico
Filtros de rango/fecha y resultado Finalizadas/Canceladas/Rechazadas. Tarjetas plegadas por defecto dentro de grupos de días, con código, hora, origen/destino, pasajero, distancia e importe si existe. Expandir para paradas, comentarios, tarifa pactada, traza real y compartir.
### D Resumen
Por día, semana, mes y fecha/rango personalizado: finalizados, rechazados, cancelados, total km estimado, km GPS solo cuando existe traza real, sumatoria de importes **cotizados**, monto cobrado solo si hay dato explícito de cobro. Indicadores sin registro = “—”, no 0 falsos. Evitar estadísticas inventadas.
### E Herramientas (menú secundario)
Horarios y disponibilidad, bloqueos, tarifas, avisos, GPS/telemetría, vehículo, diagnóstico, pruebas no destructivas. Acciones de Mapa siguen en Mapa; no crear comandos paralelos de jornada/GPS ni pantallas redundantes.

## 5. Orden de implementación y criterios de cierre
**P0 — Integridad operativa**
1. Resolver vinculación y lectura de \`driver_list_reservations_v3\` / \`driver_history_v2\` desde la instalación QA real, sin PIN visible y sin copiar credenciales.
2. Historial y tarjetas: todos los estados, contadores correctos, fecha/código/detalles/paradas, pendientes cotizadas, filtros. Sin cajas vacías grandes.
3. Flujo pedido directo → aceptar/rechazar → en camino → llegué → iniciar → finalizar, cambios validados por RPC; chat, llamada, WhatsApp, ruta.
4. Navegación de pantalla y estado de jornada: deslizar Mapa↔Conductor sin efectos colaterales. Validar continuidad GPS y ausencia de overlays de Mapa encima.

**P1 — Paridad de gestión**
5. Agenda completa y fecha personalizada, filtros de modalidad y estado, próximo viaje y avisos.
6. Horarios, disponibilidad y bloqueos; respetar las políticas del servidor y fuente única.
7. Tarifas y presupuestos solo donde corresponden (programados), sin cotizar de nuevo el directo fijo; precio configurado respetado.

**P2 — Herramientas y calidad**
8. Notificaciones de fondo/con app abierta, sonidos/vibración únicos, cola sin duplicados, recordatorios y pruebas locales.
9. Telemetría por viaje y GPX, vehículo, compartir, calendario, diagnóstico, logs.
10. Accesibilidad, tamaños de pantalla Samsung, pruebas de regresión y métricas con datos verificados.

**Gates para publicar**:
- G0 scope único de Conductor; diff sin clases de Mapa ni cambios de backend.
- G1 análisis/compilación Android modular en verde.
- G2 tests de estados, filtros, rangos de fecha, expandir/contraer, ausencia de caja muerta.
- G3 QA visual: imagen de petición grande idéntica al diseño aprobado; estados vacíos compactos; no superposición.
- G4 QA real Samsung: instalación con misma firma y package QA estable, datos/vínculo conservados entre actualizaciones, jornadas y desplazamiento.
- G5 QA E2E autorizado: reserva de prueba identificada y reversible o ambiente de prueba separado, ida/vuelta de cada estado, agenda/histórico/recogida/ETA, notificaciones, sin alterar reservas de producción por error.
- G6 recién publicar oficialmente y limpiar correo GitHub pertinente **solo luego del despliegue final**, conforme al procedimiento del proyecto.

## 6. Conformidad y control de regresiones
- El panel integrado actual tiene tarjetas adaptables, botones básicos de aceptación/recogida/cierre, RPC de listado/historial, chat y navegación externa, pestañas Peticiones/Agenda/Historial/Resumen. **No afirmar paridad funcional total**.
- Conductor antiguo permanece como fuente funcional y respaldo; no ejecutar su vieja Activity para mostrar una segunda pantalla.
- No crear tablas RPC alternativas para recuperar simple UI; preferir contratos existentes. Cualquier evolución necesaria del backend exige plan/revisión aparte.
- No eliminar datos reales, reservas, historial, cuentas, PIN, matrícula, tarifas o trazas sin política de retención explícita.
- Entregas QA: un APK combinado y firmado, con ID y firma estables, versionCode creciente. No confundir la compilación de Mapa+Conductor con una aplicación independiente de Conductor.
- Registrar resultados de QA, captura aprobada, limitaciones y faltantes en PR #185 antes del merge.

## 7. Definición de terminado
Solo consideraremos “Conductor completo” cuando cada fila del inventario tenga: vista/acceso, lectura real o contrato identificado, acciones autorizadas donde corresponda, prueba de éxito y error, captura estética aprobada, y ausencia de regresión en Mapa. La mera compilación exitosa **no equivale** a paridad funcional.


## 8. Regla prioritaria aprobada: «Tu viaje» directo ≠ viaje programado
Confirmación de producto del 21/10/2026:
- **Tu viaje directo / inmediato**: el pasajero ya eligió origen, destino y vio **precio fijo final**. Conductor recibe automáticamente la **tarjeta grande original** de la captura, con recorrido, km, tiempo y precio. Al pie hay **solo dos acciones: RECHAZAR y ACEPTAR**. Se ejecutan a un toque, sin abrir cotizador, formulario, contraoferta ni un segundo diálogo. Mientras el servidor registra la decisión se inhiben duplicados y se comunica el estado real. El rechazo también es directo, porque el usuario prioriza agilidad.
- El contrato ya existente del Conductor antiguo identifica esta clase de solicitud exclusivamente cuando \`status=PENDIENTE\`, \`quote_status=PRECIO_FIJO\`, \`quote_final_total>0\` y finito, y \`pickup_mode=now\` o intervalo numérico de 1 a 120 minutos. Implementado y probado en \`UnifiedDriverPolicy.isDirectFixedRequest\`. **Nunca inferir el precio ni habilitar aceptación directa cuando faltan datos**.
- Aceptado el directo, el viaje pasa al flujo normal de recogida: En camino → Llegué → Iniciar viaje → Finalizar. No son pasos previos a aceptar; vienen después.
- **Tu viaje programado**: distinguir claramente por modalidad y fecha/hora. Mostrar tarjeta programada y permitir gestionar agenda, disponibilidad y presupuesto **únicamente según el contrato de reservas programadas**. No renderizar Aceptar/Rechazar directos para una petición todavía no presupuestada. El viejo flujo de presupuestos/consulta de agenda debe portarse a V2 en etapa P1; hasta entonces se señala gestión programada pendiente, sin simular que la cotización se envió.
- Si una petición dice \`now\` pero carece de precio fijo confirmado, se marca **petición por revisar**, no se finge que es programada ni se habilita el botón directo. La actualización debe volver a consultar Supabase.
- Prioridad visible de tarjetas: viaje en curso, petición directa recién recibida, reserva aceptada, resto de solicitudes; no esconder una petición urgente detrás de reservas programadas.
- QA obligatoria: directo con precio fijado, directo 10/120 min, programada sin cotizar, programada cotizada, precio faltante, precio inválido, petición ya aceptada, doble toque, fallos de RPC y refresco. Probar que jamás se abre el cotizador para el directo ni se cambia Mapa.
- Esta regla prevalece sobre cualquier antigua indicación genérica de «Aceptar cualquier PENDIENTE».

**Estado de implementación:** clasificación y botón directo simple incorporados al panel y sus pruebas unitarias; flujo de cotización programada y validación extremo a extremo en Samsung **pendientes**. No fusionar como paridad completa antes de cerrar ambas.
