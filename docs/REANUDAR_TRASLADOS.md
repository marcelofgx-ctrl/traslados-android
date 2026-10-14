# REANUDAR TRASLADOS — contexto vivo del ecosistema

## Rediseño visual R25.12 — tercer mockup aprobado (13/10/2026)

- El último mockup (variante 3, petróleo / mapa oscuro / turquesa / oro) es referencia visual de Mapa Trayectos, no sustitución de la aplicación por imagen. Fuente Android nativa «sans-serif-medium» aproximada, sin importar fuentes desde la red.
- Se adopta OpenFreeMap Dark para el mapa vivo, velocímetro con arco de progreso, cabecera compacta adaptable a Samsung, superficies profundas, borde fino teal/dorado y tipografía jerarquizada con acento para el tipo de servicio.
- Se refina el deslizador de recogida con gradiente turquesa/verde y flechas; nunca cambia los umbrales de arrastre, haptics ni acciones del viaje. Se conservan brújula, GPS real, globito, notificaciones, Conductor integrado, lógica de jornada, rutas y base.
- APK propuesta R25.12 code64; publicación y firma deben verificarse en CI. El tercer render es inspiración visual y no garantiza que los píxeles de cada tamaño de pantalla sean idénticos. QA Samsung pendiente.


## Mejora R25.11 — prueba real del globito (13/10/2026)

- Se distingue **permiso otorgado** de **globito realmente dibujado** al salir. El indicador de última vista solo se actualiza al mostrar, jamás al ocultar; evita falsos positivos en el diagnóstico.
- El Centro de control muestra «comprobado», «en curso», «sin confirmar» o «sin probar» según evidencia; una sola acción principal contextual (autorizar o probar). Ajustes Android/Samsung quedan en «Más opciones» e informe copiable informa la prueba sin PIN, contactos ni coordenadas.
- R25.11 / versionCode 63 preserva R25.10; nuevo test Java de política temporal para regresiones. **Pendiente verificación CI de RELEASE y prueba Samsung física; no se da por probada la instalación con este commit.** No toca lógica GPS, Conductor, jornadas ni reservas.


> **LECTURA RÁPIDA EN NUEVA SESIÓN:** primero [ESTADO AUTOMÁTICO — VERSIONES Y BUILDS](ESTADO_AUTOMATICO_TRASLADOS.md), después este documento para decisiones, reglas de negocio, QA y pendientes. El estado técnico lo genera GitHub Actions sin recompilar. **No copiar manualmente números de versiones acá cada vez.**


> **Última revisión verificada: 13 de octubre de 2026, Uruguay (UYT).** Este documento es el **punto único de entrada** para una nueva conversación; el resto de `docs/` conserva historia detallada y puede contener etiquetas «ACTUAL» ya obsoletas. **Antes de ejecutar trabajo nuevo, verificar `main`, GitHub Actions, migraciones aplicadas y versiones publicadas.** No inferir que esta foto seguirá siendo vigente indefinidamente.

## Instrucción de reanudación para ChatGPT

**ÚNICA FRASE PARA COPIAR EN CUALQUIER CHAT:** **Retomar Traslados Web desde Git**.

**Qué significa esta frase:** recuperar el proyecto completo (Web/PWA + Cliente + Conductor + Mapa Trayectos + Supabase + Cloudflare) de `marcelofgx-ctrl/traslados-android` (`main`), usando primero el [estado técnico automático](ESTADO_AUTOMATICO_TRASLADOS.md) y después este documento. No interpretar «Web» como una orden de ignorar APK.

**Respuesta inicial obligatoria al retomar:** informar *primero* la **fecha y hora de Uruguay de la última compilación de aplicación verificada y el último cambio funcional**, indicando qué aplicación, resultado, commit y enlace; verificar que el registro sea vigente frente a GitHub y no confundir commits de documentación con modificaciones funcionales. Luego resumir brevemente pendientes y continuar el trabajo.

**Al finalizar una compilación o publicación sobre la que se informa al usuario** (correcta o fallida): señalar resultado real, versión, fecha/hora y enlace; dejar SIEMPRE al final, lista para copiar: **Retomar Traslados Web desde Git**. Esto es una instrucción para futuras respuestas de desarrollo, no una promesa de notificaciones en segundo plano.

**Secuencia obligatoria al empezar:** (1) leer ESTE archivo, (2) contrastarlo con los últimos commits y Actions de ambos repositorios, (3) ampliar solo los documentos técnicos pertinentes, (4) preguntarle al usuario qué comportamiento vio en Samsung si depende de QA físico. Ante una contradicción, **el código publicado y la base vigente prevalecen sobre la prosa histórica**. No inventar pruebas ni instalaciones.

## Forma de trabajo solicitada — criterio profesional y proactividad (13/10/2026)

**Preferencia explícita del usuario:** no quiere tener que imaginar ni detallar cada necesidad básica, comportamiento obvio, accesibilidad del sistema operativo o paso necesario para una experiencia completa. Espera que ChatGPT actúe **como un responsable técnico y de producto proactivo**, no como un ejecutor literal. Esta pauta es prioritaria al iniciar cualquier sesión del proyecto.

**Aplicación operativa en cada pedido, sin convertirlo en burocracia:**

1. **Interpretar el objetivo final y el recorrido real de usuario**, no solamente la modificación textual. Revisar el flujo completo, pasos anteriores y posteriores, permisos, primera instalación, reanudación, errores, casos límite y facilidad de uso. Si solicita «abrir el permiso», el objetivo es llegar a la pantalla y la app correctas con la menor fricción posible.
2. **Investigar antes de afirmar que algo es imposible**, especialmente en Android/Samsung: examinar código y versiones históricas que funcionaban, documentación vigente y compatibilidad de ROM/versiones; distinguir entre API oficial, intento condicionado al fabricante y límite real. Evitar respuestas tajantes no comprobadas.
3. **Proponer e incorporar pequeños ajustes obvios y complementarios** (mensajes, accesos directos compatibles, indicadores de estado, ayudas y diagnóstico, recuperación ante fallos, accesibilidad) cuando mejoran la solución y no alteran el alcance sustancial. Para cambios con implicaciones de costo, seguridad, privacidad, negocio o riesgo de regresión, exponer la elección antes de ejecutarlos.
4. **Pensar en experiencia premium además de funcionalidad**: buena jerarquía visual, pocos toques, diseño coherente, estados claros, mensajes orientados al usuario y alternativa cuando una API no responde.
5. **Proteger lo existente**: no perder datos, no desinstalar para actualizar, mantener funciones y diseño ya aprobados; revisar cliente, conductor, mapa, web y backend si comparten el flujo. Añadir verificaciones prácticas de punta a punta; no sustituir QA real en Samsung por CI exitoso.
6. **Entregar resultados verificables**, no solo planes: actualizar GitHub, revisar ejecución CI, firma, publicación, checksum y versión; citar hora/commit reales y APK correcta. Si falta evidencia, comunicar exactamente qué está listo y qué queda por probar.
7. **Anticipar dos o tres problemas probables sin pedir al usuario que los descubra**; corregirlos si son seguros y razonables. Resumir decisiones y resultados con brevedad, sin cargar al usuario con detalles de implementación ni pedir confirmaciones innecesarias.

**Ejemplo de aprendizaje — permiso «Aparecer encima»:** el usuario tuvo que señalar que no bastaba abrir ajustes genéricos, sino que debía quedar ubicado directamente en la aplicación. Antes de declarar el acceso imposible, debimos revisar el Intent anterior `ACTION_MANAGE_OVERLAY_PERMISSION + package:<id>` y contrastar la restricción Android 11+ con el comportamiento real del firmware Samsung. Esta anticipación debe formar parte de la revisión inicial del flujo, no depender de que el usuario la solicite.

## Entrega verificada R25.10 — diseño, permisos, enlaces y publicación robusta

**13/10/2026, 18:40 UYT:** [Mapa R25.10 code62 CI firmado SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38472197090) a partir de commit `42d7bcfbd`. APK [mapa-trayectos-r25.10.apk](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r25.10.apk), SHA-256 `4b4e3d192b20dea9708e1e2dad6bf8c0a10c0487a4e4c2f3d0d9c88ff8d96411` verificado en `web-pasajero/downloads/MAPA-R25.10-SHA256SUMS.txt`. **QA Samsung físico pendiente.** Esta es la última entrega de la primera fase de UX que inició en R25.9 y añade reducción de redibujado del indicador de permiso, más robustez en la publicación. No afecta TrackingService, jornadas, backend ni reservas. Instalar encima de R25.8/R25.9 sin borrar datos. La [R25.9 firmada](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r25.9.apk) queda conservada como respaldo, CI SUCCESS [38471408362](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38471408362). Los intentos anteriores de publicación concurrente fallaron por conflicto add/add al intentar sobrescribir R25.9, **no por error en la compilación**. La compilación R25.10 adopta concurrencia serializada y `git fetch + reset origin/main` antes de publicación, comprobada SUCCESS en este run.

**Pruebas recomendadas:** abrir Mapa y menú principal, Configuración plegable, permiso específico, activar avisos si están apagados, probar globito en Inicio, copiar estado y pegar sin coordenadas/PIN; usar `✦ → Enlaces y contacto` para comprobar que reserva enlaza Pages pública y descarga Cliente Premium oficial. No atribuir un resultado físico sin confirmación del usuario.

## Mejora de producto R25.9 — centro de control y enlaces oficiales (13/10/2026)

Revisión y alcance documentados en [REVISION_PRODUCTO_TRASLADOS_R25_9.md](REVISION_PRODUCTO_TRASLADOS_R25_9.md). Tras R25.8, el siguiente lote *exclusivamente de experiencia y enlaces* incluye: panel `BubbleSettingsPanel` premium con estado del globito y notificaciones, permisos primero y herramientas plegables, botón de ajuste específico de notificaciones, informe técnico copiable sin PIN/ubicación, menú principal adaptable y táctil; corrección del enlace Web público de reservas y APK Cliente Premium v15 frente a Worker/Lovable, con migración únicamente de defaults anteriores y confirmación al compartir. **Versión fuente R25.9 code 61: verificar release CI/firmas/checksum y QA Samsung antes de afirmar que está funcionando físicamente.** Preservar rutas del permiso específico R25.8, globito, GPS, base y Conductor. Próximas fases sugeridas: flujo de reservas extremo a extremo y alertas fiables; estimaciones viales con precisión explícita; tablero por estado y fecha.

## Repositorios y servicios canónicos

| Parte | Referencia verificada |
|---|---|
| Código Android + PWA + documentación | [`marcelofgx-ctrl/traslados-android`](https://github.com/marcelofgx-ctrl/traslados-android), rama `main` |
| Código backend, SQL y Worker | [`marcelofgx-ctrl/traslados-web`](https://github.com/marcelofgx-ctrl/traslados-web), rama `main` |
| Web/PWA del **pasajero** (única interfaz pública) | https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ |
| Worker de rutas y utilidades, **no** reemplaza el frontend aprobado | https://traslados-web.marcelof-gx.workers.dev/ |
| Supabase productivo del proyecto | `zetaudvvutlouiqxopvg` (ID de proyecto, **no** clave) |
| Web PWA fuente | `web-pasajero/`; desplegada en GitHub Pages desde `main` |
| Android pasajero | `cliente-premium/` / WebView que carga la misma PWA; comprobar estructura exacta antes de editar |
| Conductor y Mapa | `conductor/` y `mapatrayectos/`; APK separadas con funciones complementarias |

## Corte de referencia histórico del 13/10/2026 (consultar estado automático vigente)

| Componente | Versión | Comprobación | Descarga / acceso |
|---|---|---|---|
| Pasajero PWA | **v68**, caché `traslados-cliente-pwa-v68` | [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352735507) | [Abrir web](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/) |
| Cliente Premium Android | **v15**, `versionCode 203`; WebView de PWA v68 | [paridad WebView SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38351814058) (código de PR) | [APK cliente v15](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v15.apk) |
| Conductor Android | **11.5-R6**, `versionCode 128` | [RELEASE firmado SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352100436), R5 conservada | [APK conductor R6](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-conductor-r6.apk) |
| Mapa Trayectos Android | **0.1-R25.0**, `versionCode 52` | [RELEASE SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352100294) | [APK mapa R25.0](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r25.0.apk) |
| Backend Supabase | Políticas R1 y guards aplicados | [CI `traslados-web` SUCCESS](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38352487078), PR [#15](https://github.com/marcelofgx-ctrl/traslados-web/pull/15), [#16](https://github.com/marcelofgx-ctrl/traslados-web/pull/16) | No requiere nueva APK para la lógica SQL |
| Worker de rutas | Recuperación ORS corregida y publicada manualmente | [Cloudflare SUCCESS](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38348786259) | Endpoint de rutas a través del Worker |

Corte Git de referencia previo a este documento: Android `6de4cafd046a192b876129ff6f256206b96856f0`; backend `93e30286381319e5e0608b9e3e9c2590a7a3c05b`. **Son referencias históricas, no punteros permanentes:** siempre comprobar las cabezas actuales.

## Contrato funcional aprobado

1. **Solicitud programada:** cualquier fecha/hora futura si el conductor tiene activada la recepción de programadas; la franja y los días son **horario habitual orientativo**, no veto automático por defecto. La opción `allow_scheduled_any_time=true` está activa inicialmente; si se apaga, las solicitudes se limitan al horario/días configurados. Debe validarse también en backend tanto para invitados como para clientes registrados.
2. **Solicitud inmediata («Ahora» o «En 10 minutos»):** depende de jornada activa y no pausada, GPS reciente/preciso, conductor libre y ausencia de viaje incompatible. Con `allow_live_outside_hours=true` puede seguir recibiendo pedidos fuera del horario habitual mientras trabaja. Activar esa opción NO activa la jornada ni simula ubicación o disponibilidad.
3. **Ninguna solicitud equivale a reserva confirmada:** entra `PENDIENTE`; Conductor revisa la agenda, decide si enviar presupuesto; el pasajero lo acepta o rechaza. Solo entonces se confirma; el servidor revisa nuevamente choques, márgenes, bloqueos y reposicionamiento. No prometer un horario libre por recibir una solicitud.
4. **Vencimientos:** solicitudes inmediatas antiguas (>90 min) no admiten envío/aceptación de presupuesto; solicitudes programadas con hora ya pasada tampoco. Visual de caducidad en historial sin modificar silenciosamente filas antiguas.
5. **Paradas:** pasajero autenticado puede incluir paradas; Conductor R6 recibe la lista en orden y las incorpora a Google Maps. El presupuesto debe considerar la ruta TOTAL, no solo origen→destino directo.
6. **Tarifa:** la cifra pública es **orientativa** y puede carecer de peajes/extras; el conductor decide el precio definitivo que recibe el pasajero. No mezclar km del viaje, km hasta recogida, km GPS reales, ni tarifa aproximada vs. cotización firme.
7. **Privacidad:** no revelar lat/lng crudos del conductor a usuarios; ETA y distancia hasta recogida se calculan en servidor solo bajo autorización y control de cuota. No copiar PIN, tokens, claves privadas ni ubicación de personas a repositorios/logs/documentos.

## Actualización Mapa R25.7 — acceso a permisos en Android 16

- **13/10/2026 — R25.7 fusionada y APK publicada por CI; QA Samsung pendiente:** Con Mapa R25.6 el globito ya funciona en Samsung físico, confirmado por el usuario. La mejora R25.7 **no cambia el servicio flotante**, corrige la navegación a ajustes: `Configuración → Ajustes de Mapa Trayectos` abre directamente la información de esta app mediante `ACTION_APPLICATION_DETAILS_SETTINGS` con URI `package:uy.com.mapatrayectos`. Opción adicional `Lista «Aparecer encima»` mantiene `ACTION_MANAGE_OVERLAY_PERMISSION`; en Android 11+ esa acción IGNORA el paquete por política de seguridad, y el usuario debe elegir Mapa Trayectos en la lista. **No prometer abrir el interruptor específico ni conceder el permiso automáticamente**; si Samsung no ofrece «Aparecer encima» desde la ficha de la app, usar la lista. `Probar globito` conserva el ensayo en Inicio y el diagnóstico. Código R25.7 / versionCode 59, PR #79 fusionada (`db043308`), APK `web-pasajero/downloads/mapa-trayectos-r25.7.apk` publicada por workflow (commit `a257ac0083cc`), SHA-256 `bfc6a621f65629154c600c4defa2fea5ec9057eb0559fc5343c7e9424a00e835`, según checksum en Git. La verificación física Samsung sigue pendiente. Preservar R25.6 como rollback.
- En el Samsung, instalar como actualización SIN desinstalar. Abrir `☰ → Configuración → Ajustes de Mapa Trayectos`; comprobar que aparece la ficha de Mapa Trayectos, no una lista general. Si desde esa ficha no se puede acceder a «Aparecer encima», volver y pulsar `Lista «Aparecer encima» → Mapa Trayectos`. Activar permiso solo por decisión explícita. Volver y `Probar globito` en Inicio, confirmar que no hubo regresión.

## R25.8 — investigar acceso directo a permiso «Aparecer encima» en Samsung Android 16

- **13/10/2026 — cambio de fuente R25.8 (versionCode 60):** El usuario recuerda que Mapa Trayectos anteriormente abría directamente el permiso de su propia aplicación. Se revisaron versiones históricas: el commit `1d8990b15` / R25.2 **sí enviaba** `new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))` sin condicionar el paquete por SDK. Desde R25.3 se cambió a omitir `package:` con API >=30; esto pudo causar regresión en una variante de firmware Samsung que aceptase el destino. R25.8 **restaura literalmente el paquete para todos los SDK**, ofrece como primera opción `Ir al permiso de Mapa`, segunda `Ajustes de Mapa Trayectos`, tercera `Lista Aparecer encima` y conserva probar globito / actualizar estado / diagnóstico. `openBubblePermission` y el caso permiso faltante de `testFloatingShortcut` también intentan primero `ACTION_MANAGE_OVERLAY_PERMISSION + package:`. No se altera el servicio flotante ya probado por el usuario.
- **Límite Android:** el cambio AOSP `CHANGE_RESTRICT_SAW_INTENT` de Android 11 ignora el identificador `package:` en la actividad del permiso. Documentación oficial: https://developer.android.com/about/versions/11/privacy/permissions#manage_overlay. Android 16 de Samsung puede ignorarlo igualmente; NO asegurar que la app sale preseleccionada en Samsung sin QA físico. Pese a ello, restaurar el intent original como intento compatible con OEM no rompe el respaldo. No solicitar ni automatizar autorización sin interacción expresa del usuario.
- **QA específica:** instalar R25.8 encima de R25.7 sin desinstalar. Quitar permiso (solo si se necesita reproducir y acepta hacerlo), abrir `☰ → Configuración → Ir al permiso de Mapa`, verificar si abre el interruptor particular, lista general, o pantalla distinta. Si abre lista, registrar que OEM no respeta paquete. Restablecer permiso manualmente y `Probar globito`, confirmar que no hubo regresión.
- **Estado release:** APK R25.8 firmada publicada por CI en `web-pasajero/downloads/mapa-trayectos-r25.8.apk` (commit `3218c0fddb8e` del 13/10/2026 17:08 UYT), SHA-256 `526382dba7ea24b703cbc9627c5a4a43f93a7798f0afeefac5992bb2afc3f7ed` (archivo checksum confirmado). La prueba física Samsung pendiente; verificar estado de Actions si se requiere el run.

## Últimos cambios realmente implementados

- **13/10 · Mapa R25.6 (APK firmada y publicada, QA Samsung pendiente):** menú ☰ → **Configuración** como centro ampliable de opciones. Primer bloque «Acceso flotante»: permiso real `Settings.canDrawOverlays`, estado de notificaciones, evento/error del servicio, hora de última prueba, acción explícita de acceso a ajustes de superposición, alternativa de ajustes de app, actualización y diagnóstico. «Probar globito» prepara el servicio en primer plano y abre **Inicio de Android** para comprobar la superposición REAL (el icono se oculta mientras Mapa está al frente). No inicia jornada ni GPS; mantiene SQLite, PIN y Conductor intactos. La pantalla de ajustes se abre por pulsación, no desde `onUserLeaveHint`. Nueva fuente `BubbleSettingsPanel.java` y conexión en `MainActivity.java`; objetivo de compilación 0.1-R25.6 / code 58. **CI RELEASE SUCCESS** ([run 38439986465](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38439986465), job firmado/publicación exitosos) y [APK R25.6](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r25.6.apk) en Git con SHA-256 `324eb30814390073ac3d77e71a08e83b42f659fced78db553102f4f173eeb2a7`. **La publicación en Pages y el funcionamiento Samsung deben confirmarse en dispositivo; CI no es QA físico**. QA Samsung: actualizar SIN desinstalar, tocar ☰ → Configuración → Revisar permisos, habilitar «Aparecer encima» para Mapa, volver y usar «Probar globito» (abre Inicio). Si no aparece, volver a Mapa desde Recientes → Configuración → Diagnóstico detallado, registrar permiso, etapa y error. Repetir con jornada inactiva y, solo si corresponde, jornada activa. No simular resultado de prueba real.


- [Android PR #69](https://github.com/marcelofgx-ctrl/traslados-android/pull/69): PWA **v68** y Conductor **R6**. Menú **Conductor → Horarios** con días, franja y dos controles: «Programadas a cualquier hora» e «Inmediatos fuera de horario con jornada activa». Mostrar todas las paradas y navegación con waypoints, aviso de presupuestar recorrido completo. Release R6 firmado con mismo certificado que R5, sin reinstalación obligatoria de Cliente v15.
- [Backend PR #15](https://github.com/marcelofgx-ctrl/traslados-web/pull/15) aplicada en Supabase: bandera de programadas e inmediatas fuera de horario, RPC `driver_set_availability_settings_v12`, función `schedule_request_policy_v1`, validación mediante trigger para invitados/registrados, `requestable` vs. `available` por separado, lista privada de paradas en RPC de Conductor. RPC antigua compatible para R5.
- [Backend PR #16](https://github.com/marcelofgx-ctrl/traslados-web/pull/16) aplicada: frena presupuestos programados enviados o aceptados cuando ya venció la hora, conservando autorización.
- [Android PR #68](https://github.com/marcelofgx-ctrl/traslados-android/pull/68): PWA v67 corrigió espera de ETA (hasta 18 s), concurrencia e invalidaciones; eliminó la leyenda redundante bajo auto animado, mantuvo animación verde. La v68 conserva esto.
- [Backend PR #14](https://github.com/marcelofgx-ctrl/traslados-web/pull/14): ORS presupuesto 5,2 s más caché ROAD 1,2 s dentro de timeout Edge 7,5 s; errores temporales con reintento limitado; sin falsear distancia en línea recta. Worker publicado manualmente y ruta de control HTTP 200 verificada; **ETA real de recogida en Samsung aún no demostrada después del fix**.

## Pruebas realizadas vs. pendientes

**Sí verificado:** CI web, modular, compilación Android DEBUG, builds RELEASE, firma Conductor R6, Pages PWA, compatibilidad WebView; pruebas SQL transaccionales con `ROLLBACK`: programada a las 23:30 fuera de horario 08–22 recibida como solicitud, doble envío sin duplicar, cambio de presupuesto `PENDIENTE → ACEPTADA`, opción de recepción fuera de horario desactivada rechaza la solicitud, y protección contra aceptar presupuesto vencido. Sin reservas falsas persistidas por esas pruebas.

**NO verificado aún (no afirmar que está resuelto):** validación táctil de todas las pantallas en Samsung real; guardar y recargar las dos opciones nuevas de Horarios en Conductor R6; flujo bidireccional real PWA/Cliente→Conductor→presupuesto→aceptación→en camino→en punto→en viaje→finalizada; disponibilidad y ETA concreta conductor→pasajero tras fix ORS, tanto navegador como WebView; ruta y presupuesto con paradas en un viaje verdadero; sonido/vibración/popups con distintos estados. Push **FCM con cliente cerrado** sigue pendiente.

## Deuda técnica y siguiente plan priorizado

1. **QA física Android:** instalar Conductor R6 (no borrar datos; actualizar con misma firma), entrar a Horarios y probar guardado de preferencias; cotejar con pantalla pública del pasajero. Verificar Mapa R25.0 y Cliente v15.
2. **E2E controlado:** probar un pedido programado dentro y fuera de franja, usuario invitado y registrado, propuestas de precio y transiciones sin molestar al conductor ni clientes verdaderos; avisar y obtener autorización antes de insertar datos reales de prueba. Confirmar choques de agenda.
3. **ETA hasta recogida:** registrar resultado funcional del Worker/Edge, revisar errores de ORS sin exponer coordenadas; no confundir distancia del viaje origen→destino con distancia conductor→origen.
4. **Estadísticas del recorrido:** hoy **no se guardan de forma uniforme** km/min orientativos al presentar nuevas solicitudes; definir campos confiables con origen y hora de cálculo y garantizar que el presupuesto definitivo lo decide Conductor. Revisar caso histórico de viaje finalizado con km=0, sin reescribir datos sin permiso.
5. **Notificaciones cerradas:** FCM real para Cliente; actualmente avisos dentro de PWA abierta y posibilidad de vibración/sonido dependiendo del dispositivo. No prometer push cerrado.
6. **Paquetería/mensajería:** aparece en portada como «próximamente», no crear reservas de bultos bajo contratos de pasajeros. Consolidar producto y decidir después la versión `1.0.0`; fue discutida pero no adoptada formalmente para toda la suite.

## Reglas de intervención y publicación

- Preservar estética premium petróleo/grafito/champagne, texturas, medallas/íconos orbitales y UI plegable; no alterar por accidente otras apps.
- **Solo la PWA de GitHub Pages es interfaz pasajero**; Cloudflare Worker complementario, no sustituir frontend ni crear segundo formulario divergente.
- Cambios SQL con migración versionada en `traslados-web/supabase/migrations/`, CI y verificación explícita de aplicación a producción. Cambios a Worker necesitan workflow manual de publicación; CI/merge ≠ deploy.
- Cambios Android firmados requieren comprobar `versionCode` creciente, certificado/keystore existente y build SUCCESS; no publicar o afirmar APK nueva sin verificar archivo real; conservar R5 y versiones anteriores como rollback. Cliente WebView consume PWA sin recompilar APK salvo cambios nativos.
- Las pruebas automáticas NO equivalen a prueba física. Evitar insertar pruebas persistentes en producción, enviar avisos reales sin autorización o alterar reservas existentes sin consentimiento.
- Al cerrar una fase aprobada: las **versiones/links/Actions** se actualizan automáticamente en [ESTADO_AUTOMATICO_TRASLADOS.md](ESTADO_AUTOMATICO_TRASLADOS.md). Solo actualizar **este** documento cuando cambien decisiones, lógica funcional, QA o pendientes; no reescribir historia ni obligar a generar documentación extra en cada commit. Para retomar otra sesión basta con «Retomar Traslados Web desde Git» y leer ambos documentos.

## Documentos para ampliar únicamente cuando haga falta

- [Contexto maestro histórico y decisiones](CONTEXTO_MAESTRO_PROYECTOS.md)
- [Flujo completo pasajero/conductor](FLUJO_MAESTRO_TRASLADOS_CLIENTE_CONDUCTOR.md)
- [Horarios flexibles: alcance y pruebas](HORARIOS_FLEXIBLES_PWA68_CONDUCTOR_R6.md)
- [Arquitectura modular/compilación](ARQUITECTURA_MODULAR_Y_COMPILACION.md)
- [Retomar exclusivamente Mapa Trayectos](RETOMAR_MAPA_TRAYECTOS.md)
- [Backend y reparación de ORS](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/PICKUP_ETA_PROVIDER_RECOVERY_20261013.md)
