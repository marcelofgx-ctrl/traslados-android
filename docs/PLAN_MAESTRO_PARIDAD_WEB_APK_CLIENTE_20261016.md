# Traslados — Plan maestro de paridad Web / PWA / APK Cliente
**Corte de planificación:** 16/10/2026 (Uruguay). **Estado:** plan propuesto, NO implementación, NO autorización para publicar ni modificar reservas reales.

## 0. Objetivo y límites
- **Un producto de pasajeros y una sola fuente de interfaz.** La web HTTPS oficial `web-pasajero/` es el frontend canónico. La PWA instala esa misma web y la APK Cliente Premium (`cliente-premium/app`) la abre en WebView restringida al dominio oficial. No construir otra interfaz Android de pasajeros ni copiar manualmente HTML/CSS/JS en versiones divergentes.
- Web, PWA y APK Cliente deben tener las mismas **pantallas, contenido, idioma, identidad visual, validaciones, reglas de negocio, reservas, historial, chat y estados** cuando se conectan al mismo usuario y a la misma versión frontend.
- **Excepciones controladas de plataforma:** permisos Android, compartir, selector de archivos, abrir Waze/Maps/teléfono/WhatsApp, instalación, offline nativo y notificaciones push, según capacidades verificadas. Ocultar «Instalar APK» al ejecutar dentro de esa APK; nunca duplicar el menú por accidente.
- **Cuenta unificada no significa almacenamiento unido.** PWA, Chrome/Samsung Internet y Android WebView mantienen localStorage/cookies separados. Igualdad del historial se obtiene por inicio de sesión autenticado en cada entorno y `customer_id` del backend, no sincronizando tokens entre apps.
- **Conductor y Mapa:** sistema del chofer separado, biblioteca mosaico compartida `feature:driver` donde corresponda; comparte datos y estados por Supabase. No fusionar interfaces visuales de chofer con pasajeros ni acoplar ciclos de publicación.
- **Backend único:** Supabase `zetaudvvutlouiqxopvg`; las RPC, roles, RLS, privacidad, horarios y tarifas constituyen el contrato canónico. Prohibido realizar limpiezas SQL o adjudicaciones por nombre/teléfono sin titularidad verificada.

## 1. Estado real de partida (verificar de nuevo al ejecutar)
| Componente | Punto de referencia / diferencia |
| --- | --- |
| Web/PWA pública | `main`, PWA v75 en `https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/`. |
| APK Cliente Premium | `cliente-premium/app/`, v16, `versionCode 204`, URL oficial WebView; interfaz depende de la web publicada; actualizaciones frontend no exigen APK nueva si se mantiene el shell. |
| PWA v76 | PR #106, rama `fix/pwa-v76-guest-trip-history-20261016`; CI web, revisión y paridad WebView SUCCESS (ver Actions del PR); **NO fusionado ni publicado**. Añade lectura autenticada + comprobantes de invitado del mismo navegador, sesión obligatoria para nuevas solicitudes y limpieza de formulario tras envío confirmado. |
| Diseño aprobado | PR #99, rama `feature/tu-viaje-premium-visual-20261015`: 8 pantallas y assets premium, con QA visual parcial. La rama no es el producto operativo ni debe suplantar la PWA v76; fidelidad integral al modelo todavía pendiente. |
| Mosaico Cliente | PR #101, rama `feature/traslados-cliente-mosaico-20261016`, borrador basado en PR #99; compilar más rápido sin crear divergencias de frontend. |
| Mapa + Conductor | PR #100/#105/#107, rama y releases propios; decisiones del conductor deben observarse desde cualquier Cliente mediante backend, no acceso directo a almacenamiento de Mapa. |

### Riesgos concretos confirmados
1. Reservas de prueba recientes con `customer_id IS NULL` procedentes de invitado: un pasajero que abre otra app/browser ve un historial vacío aun cuando Conductor ve la reserva. El recibo del navegador anterior NO se transfiere solo.
2. Dos líneas independientes de trabajo: v76 resuelve identidad/historial; PR #99 prioriza diseño. **No reemplazar una por la otra ni mezclar cambios sin pruebas de regresión.**
3. App puede conservar recursos cacheados al actualizar GitHub Pages. Verificar la versión efectiva cargada en WebView, no solo la compilación APK.
4. Solicitudes rechazadas por agenda (`HORARIO_YA_NO_DISPONIBLE`) deben informar el verdadero motivo y refrescar sin aparentar aceptación ni borrar el histórico (cambio de Mapa R25.29 aún en ensayo).
5. QA en CI/emulador no acredita por sí solo la experiencia en Samsung y conexión móvil.

## 2. Contrato obligatorio de paridad
| Área | Debe ser idéntico en web/PWA/APK | Excepción técnica aceptable | Prueba de aceptación |
| --- | --- | --- | --- |
| Portada | Marca, fondos, textos, iconos circulares, jerarquía, acceso a Tu viaje / Tu reserva / Tu paquete | Barra Android e instalador oculto en APK | Capturas 360/393/412 + Samsung; diferencias visuales revisadas |
| Recorrido | Selección de departamento, direcciones IDE/POI, origen/destino, hasta 8 paradas, orden/edición | GPS requiere permisos Android, navegador según plataforma | Mismo resultado con la misma entrada y mensajes de error comparables |
| Cuándo | 24 h, Ahora y tiempos próximos, programados independientes | Selector de fecha/hora puede ser nativo | Misma fecha civil y zona America/Montevideo |
| Tus datos | Mismo login/registro y consentimiento; nunca nueva reserva anónima tras activar v76 | Sesión almacenada por instalación, iniciar sesión en cada una | Misma cuenta recupera mismo historial en ambos entornos |
| Confirmación | Idéntico resumen, precio real/orientativo bien etiquetado, disponibilidad y ETA verificadas | Links externos pueden abrir app nativa | Un toque = un request idempotente; backend devuelve mismo código |
| Mis traslados | Próximos, Responder, Historial, Todos; filtros, estados, hora, precio, detalle, chat | Notificación nativa puede abrir la tarjeta concreta | Misma cuenta y reserva => mismo estado e historial |
| Seguimiento | Pendiente → Conductor en camino → En punto → En viaje → Finalizada; mapa, ETA, matrícula si autorizada | GPS background/push solo cuando implementados y permitidos | Progresión basada estrictamente en datos servidor, nunca animación como dato |
| Errores/offline | Mensajes honestos, botón recuperar, sin pérdidas ni reservas offline ficticias | Pantalla offline nativa adicional | Reconexión no duplica solicitud ni pierde última reserva |
| Versiones | Misma versión de frontend activo y contratos | `versionCode` de APK nativa es independiente | Pantalla Diagnóstico muestra versión web, shell nativo, SW/cache y backend |

## 3. Orden de ejecución por fases (trabajo incremental, sin rehacer)
### Fase 0 — Inventario, respaldo y rama de integración [P0]
- Congelar etiquetas: `main` web v75 operativa, PR #106 v76 funcional, PR #99 diseño referencia, PR #101 modularización, APK Cliente v16 y Mapa/Conductor en su propio ciclo.
- Crear **rama integradora desde el HEAD más reciente de main**. Incorporar primero cambios selectivos de PR #106 y luego las vistas/assets necesarios de PR #99. Resolver conflictos por contrato y componente, sin fast-forward ciego.
- Auditar dependencias y scripts en `web-pasajero/index.html`, `app.js`, `styles.css`, service worker, `cliente-premium/app` y PR #101. Poner backup del release/commit/firmas antes de modificar.
- **Entregable:** matriz de versiones/archivos, comparativa de capturas actuales y mapa de rutas de datos; rama staging que no publica.

### Fase 1 — Propiedad de reserva, sesión e historial [P0 bloqueante]
- Login/registro requerido para **nuevas solicitudes** en ambas modalidades (Ahora y Tu reserva) con la misma experiencia; preservar origen/destino/horario mientras inicia sesión. Revisar continuidad del wizard tras autenticarse.
- Enviar token válido a RPC; comprobar que backend fija `customer_id` y que el código se refleja en `customer_list_reservations_v15`. Mantener el mismo usuario en web, PWA y APK, con login separado en cada contenedor.
- Combinar por ID historial de cuenta y recibos de invitado legítimos `public_token` solo del navegador poseedor; no exponer reservas de terceros. Ofrecer un flujo opcional futuro de vinculación verificada (PIN/OTP + comprobante), nunca recuperar por solo nombre o teléfono.
- Guardar el recibo del servidor. **Solo después del éxito**: limpiar campos de nuevo pedido y solicitar refresco del historial. **NO borrar** la reserva ni el chat ni el histórico al limpiar el formulario.
- Tras decisiones del conductor, revalidar estado con RPC antes de retirar tarjeta activa o actualizar contador; errores de agenda claros y visibles. Manejar doble toque/reintento idempotente.
- **Salida:** mismo viaje autenticado aparece en ambos entornos tras login y refresco; ninguna aceptación/rechazo, historial ni cuenta se pierden.

### Fase 2 — Rediseño premium fiel de las 8 pantallas [P0]
- Usar las dos láminas aprobadas de PR #99 como referencia bloqueante; mantener la estética petróleo, champagne/dorado, textura, coche y cielo de Montevideo, **emblemas circulares**, tipografía, espaciados y gestos coherentes.
- Pantalla 1: portada independiente con Tu viaje (inmediato), Tu reserva (programado) y Tu paquete (Próximamente), barra Inicio/Mis traslados/Menú.
- Pantallas 2–5: recorrido, cuándo, tus datos, confirmar con pasos claros, datos reales y sin formularios verticales innecesarios. Distinguir precio orientativo de precio fijo; para recogida inmediata con tarifa fija aceptada por conductor, **no reintroducir una segunda negociación**.
- Pantallas 6–8: buscando conductor, conductor en camino y en viaje; estados reales y visibilidad de ETA/matrícula cuando la reserva tiene permiso. No falsas fases ni iconos que aparenten GPS ficticio.
- Modos compactos/expandibles, nada de botones cortados, texto encimado, scroll atrapado o cabeceras duplicadas. Tap targets >=44dp, navegación con teclado y zoom de texto, respetar reducción de movimiento.
- **Salida:** pantallas de APK y navegador generan la misma captura con el mismo tamaño y estado, exceptuando barras del SO/permisos. Cotejar con láminas PR #99 antes de declarar aprobación.

### Fase 3 — Motores y experiencia diaria [P1]
- **Buscador Uruguay:** índice local de POI y direcciones IDE, autocomplete sin perder resultados sin lat/lng en primera respuesta, geocodificación diferida, orden por departamento y cercanía, debounce/cancelación de búsquedas tardías, errores distinguidos de 0 resultados. Casos obligatorios: aeropuerto, Punta Carretas Shopping, Plaza Italia Shopping, Restaurante De Madrid, Roque Sáenz Peña y direcciones de Canelones/Montevideo.
- **Rutas/precio:** km y minutos únicamente de recorrido vial verificado; motor de respaldo con error visible. Tarifa parametrizada en backend (referencia actual 40 UYU/km, confirmar configuración efectiva), peajes/extras explícitos; no afirmar precio cerrado si solo es orientativo.
- **Disponibilidad/ETA:** presencia del conductor distinta de disponibilidad de agenda, distancia y ETA de recogida distintas de km/min del viaje. Mostrar última actualización/frescura; estados `disponible`, `ocupado`, `no verificable` y `solicitud propia pendiente` separados. Ajustar cadencias y batería basados en medición.
- **Chat y estados:** refresco solo en vista activa, mensajes sin pérdida ni scroll inesperado, soporte reconexión, advertir si no se pudo enviar; preservar estados de recogida y matrícula editable desde ajustes de conductor.
- **Salida:** pruebas end-to-end controladas web→Conductor→APK y APK→Conductor→web, sin duplicados ni estados imposibles.

### Fase 4 — Versionado, caché, CI y publicación atómica [P0/P1]
- Web/PWA y APK deben cargar **el mismo URL y los mismos assets online**; APK nativa solo aporta shell segura. Evitar copia web empaquetada operativa que permanezca desfasada. PR #101 mosaico puede acelerar builds sin alterar el contrato.
- Introducir `frontend_release` (commit corto/version y fecha), `API contract revision`, `native_shell_version` y `SW cache version` visibles en Diagnóstico. En JavaScript comprobar recursos y SW cargados; en WebView verificar URL efectiva y resolución de red; no limpiar indiscriminadamente datos de sesión.
- Cache bust de HTML/JS/CSS/SW con activación sin borrado de cookies/localStorage y control de redirecciones; al desplegar, refrescar la interfaz o proponer «Hay una actualización» sin perder borradores. Rollback por commit, con compatibilidad hacia atrás en RPC.
- Staging HTTPS seguro para pruebas funcionales cuando aplique, separado de la web pública y evitando una PWA de ensayo que envíe a producción por accidente. Mantener Secrets y firmas exclusivamente en CI.
- Gating antes de publicar: análisis/JS, regresiones, visual golden y accesibilidad, APK WebView emulator, RPC/RLS y pruebas de concurrencia, controles HTTP/servicio SW; luego Samsung en Chrome, PWA y Cliente WebView, red wifi/datos y teclado.
- Publicación por etapas: tag inmutable de web, publish Pages + confirmar URL/JS/cache; abrir APK Cliente y comprobar misma `frontend_release`; crear APK nueva **solo** si se modificó nativo (incrementar code y verificar firma original). Con rollback comprobado.
- **Salida:** ningún release se anuncia «igual» si la APK está renderizando recursos antiguos, aunque CI compile.

### Fase 5 — Mejoras opcionales después de paridad [P2]
1. **Notificaciones reales:** push PWA con permiso/opt-in y FCM en APK con backend privado y protección por reserva. No simular alertas con app cerrada.
2. **Seguimiento autorizado:** mapa premium de trayecto/recogida con compartir viaje solo si el contrato y controles de privacidad están definidos. Datos del conductor limitados a reserva activa.
3. **Centro de ayuda contextual:** por qué no hay disponibilidad, cómo recuperar acceso, soporte por WhatsApp, accesibilidad y textos claros.
4. **Panel de observabilidad:** métricas de búsqueda, estado, error RPC, tiempos hasta aceptar, último refresco, porcentajes de desajuste por shell/frontend, sin GPS personal ni tokens en logs.
5. **Analítica e histórico** (solo usuario autorizado): km, gasto, viajes por mes, destinos frecuentes; archivo histórico cuando el tamaño real lo justifique, sin borrar información necesaria para auditoría.
6. **Rendimiento:** medir antes de optimizar; objetivos iniciales revisables: sugerencia visible rápidamente en caché, primer render móvil fluido, actualizaciones de estado visibles en un ciclo activo y paneles sin bloquear mapa. Evitar polling incesante en segundo plano.
7. **Cuenta y conveniencia:** PIN + opción biométrica Android solo después de verificar WebAuthn/credenciales seguras, recuperación verificada, preferencias de origen/destino con consentimiento, dirección favorita.

## 4. Matriz de pruebas de paridad y criterio de lanzamiento
| Escenario | Navegador web | PWA instalada | APK Cliente | Conductor/Backend |
| --- | :---: | :---: | :---: | :---: |
| Misma portada visual y 8 pantallas aprobadas | □ | □ | □ | N/A |
| Buscar 6 lugares difíciles, elegir depto, paradas | □ | □ | □ | Coords verificadas |
| Login, salir/entrar, reabrir app y recuperar historial | □ | □ | □ | RLS + customer_id |
| Enviar Ahora (precio fijo, aceptar/rechazar conductor) | □ | □ | □ | RPC y estados |
| Enviar Programado, conflicto de agenda | □ | □ | □ | Horario y respuesta real |
| Solicitud enviada limpia solo el formulario | □ | □ | □ | Reserva conservada |
| Caso ambiguo por pérdida de red no duplica reserva | □ | □ | □ | Idempotencia |
| Cambio de estado y chat bidireccional | □ | □ | □ | Timestamps reales |
| Conductor en camino + distancia/ETA sin inventar | □ | □ | □ | Permiso, frescura |
| Actualizar web y comprobar mismo frontend en APK | □ | □ | □ | Versiones/hash |
| Permisos GPS, teclado, scroll, 360/393/412, Samsung | □ | □ | □ | Sin fuga de GPS |
| Instalador APK, enlaces externos y offline | N/A | □ | □ | Sin escrituras offline |

**Gate de salida:** todos los casos obligatorios aprobados con evidencia de capturas/logs no sensibles y códigos de reserva de prueba autorizada; cero fallos P0, cero solicitudes duplicadas, cero regresiones visuales; URL HTTPS, `frontend_release` y contratos coinciden; CI verde y Samsung validado. Los P2 son independientes.

## 5. Secuencia de entregables sin bloquear progreso
1. **Entrega A:** base v76 integrada + paridad de sesiones, historial y limpieza; staging y pruebas de estado.
2. **Entrega B:** interfaz premium fiel de PR #99 dentro de esa base; comparación de 8 pantallas web/PWA/APK.
3. **Entrega C:** buscador/rutas/ETA/estados/chat optimizados, con E2E completo Mapa/Conductor y Cliente.
4. **Entrega D:** publicación web aprobada y verificación APK misma versión efectiva; compilar Android solo si hay cambios nativos. Mantener release previo y rollback.
5. **Entrega E:** push, analítica, biometría y mejoras no bloqueantes.

### Regla de continuidad
- Fuente de diseño: `docs/CONTINUIDAD_DISENO_PREMIUM_TU_VIAJE_20261015.md`, **PR #99**.
- Correcciones de pasajeros: **PR #106**.
- Modularización Cliente: **PR #101**.
- Mapa/Conductor: **PR #100, #105, #107**.
- Comando de reanudación: **Retomar Traslados Web desde Git**.
- Al retomar: verificar HEAD de main y ramas, PRs, CI, versiones efectivamente publicadas y últimas compilaciones; NO convertir builds o mockups en afirmaciones de funcionamiento físico. Mantener cuenta/seguridad/historial, aspecto aprobado y URLs únicas.

## 6. Decisiones no resueltas, a confirmar con pruebas (no bloquear el plan)
- Qué pantalla abre la APK en una sesión no iniciada y cómo vuelve exactamente al paso previo tras autenticar.
- Si servicio programado siempre incluye precio cerrado o conserva una fase de cotización explícita, sin mezclarla con el flujo inmediato.
- Cadencia sostenible de chat/ETA/push, según consumo real de batería y limitaciones Android.
- Diseño del proceso de recuperación de recibos de invitado desde otro dispositivo con verificación de titularidad.
- Alcance de notificaciones con app en primer plano, en segundo plano, terminada por el SO y cierre forzado.

**No implica despliegue ni modificación de producción.**