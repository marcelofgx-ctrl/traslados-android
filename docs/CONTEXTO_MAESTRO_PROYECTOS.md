# CONTEXTO MAESTRO — ECOSISTEMA TRASLADOS / MAPA TRAYECTOS

**Corte comprobado:** 09/10/2026, ~21:20 Uruguay (America/Montevideo).
**Carácter:** documento de inicio de sesión, vivo y versionado en GitHub.
**Repositorio canónico de coordinación:** `marcelofgx-ctrl/traslados-android`, rama `main`.
**Segundo repositorio:** `marcelofgx-ctrl/traslados-web`, rama `main`.
**Regla de confianza:** este archivo fija el estado conocido en el momento del corte. **Antes de cualquier cambio**, consultar commits, código, Actions, página pública y backend actuales. Ante contradicciones, gana el código/estado verificado más reciente, y este documento se actualiza.

> **PROMPT PARA PEGAR EN CUALQUIER NUEVA SESIÓN**
>
> «Retomá el ecosistema **Traslados / Mapa Trayectos** desde el contexto maestro:
> https://github.com/marcelofgx-ctrl/traslados-android/blob/main/docs/CONTEXTO_MAESTRO_PROYECTOS.md.
> Leé el documento, comprobá los últimos commits y GitHub Actions de `traslados-android` y `traslados-web`, y revisá las referencias técnicas allí enlazadas antes de cambiar nada. Trabajá sobre los repositorios reales, no sobre muestras ni datos inventados. Separá IMPLEMENTADO / COMPILADO / PUBLICADO / PROBADO EN SAMSUNG. No borres datos, viajes, PIN, sonidos o reservas. Conservá el diseño premium aprobado. Al terminar actualizá el contexto maestro con lo que cambió y su evidencia».

---

## 1. Mapa de componentes y responsabilidades

| Pieza | Ubicación real | Función | Último estado observado |
| --- | --- | --- | --- |
| **Mapa Trayectos** — APK conductor principal | `traslados-android/mapatrayectos/` | GPS y mapa, jornadas Uber/Cabify/personal, historial, alertas, acciones y **Conductor integrado** | Código `0.1-R24.3`, `versionCode 45`; APK RELEASE firmada verificada en Actions **37924921923**; validación visual integral en Samsung pendiente. |
| **Conductor nativo** integrado | `traslados-android/conductor/` y tarea `prepareEmbeddedConductor` de Mapa | Reservas Supabase, presupuestos, aceptar/rechazar, monitoreo periódico | Forma parte del APK Mapa R24.x. El build **Conductor independiente v9.2** figura fallido en Actions **38005505455**; investigar si se pretende entregar ese respaldo separado. No confundir con el build Mapa R24.3 exitoso. |
| **Traslados Cliente** — APK nativa | `traslados-android/cliente-pasajero/` y workflows Android | App nativa distinta de la PWA; reservas e historial | Última release nativa documentada **11.5-R10**, run **37945735891**; workflows `Build Traslados Cliente v9.0` registran éxito más reciente (**38005505533**) pero el nombre del workflow no demuestra versión/firma/archivo: inspeccionar artefacto antes de anunciar actualización. |
| **Traslados Pasajero GitHub Pages** | `traslados-android/web-pasajero/` | Web/PWA ligera: invitados y clientes con teléfono/PIN, recorrido, reservas, presupuesto | Está **bajo /web-pasajero/**, no en raíz; smoke público **38005505447** y Pages **38005504188**, ambos success al corte. |
| **Índice público del proyecto** | `traslados-android/index.html` | Página de acceso a apps y documentación; mantener como puerta de entrada y continuidad | Raíz GitHub Pages separada del formulario. |
| **Traslados Web Premium Cloudflare** | `traslados-web/` | Otra interfaz web completa: portada premium, historial avanzado, PWA/passkeys en desarrollo, itinerarios, reservas | `main` **87f0038d0f670605e059b0a1e98732d2c78ba5c6**; CI **38000741722** success. Publicación/experiencia real a validar en sitio. |
| **Backend único de reservas** | Supabase **`zetaudvvutlouiqxopvg`** | Clientes, sesiones, reservas, presupuestos, paradas, funciones RPC, estados | Producción real compartida: proteger esquema y registros existentes. |

**Advertencia central:** la PWA GitHub Pages y la web Cloudflare son **dos frontends distintos**, aunque comparten Supabase. Un cambio visual en una NO aparece automáticamente en la otra. La APK Cliente es otro producto; no confundir APK nativa con PWA instalable. **No crear una tercera base ni reservas ficticias para simular integración.**

### Enlaces correctos (comprobados por estructura y automatizaciones)

- **Índice/continuidad:** https://marcelofgx-ctrl.github.io/traslados-android/
- **Formulario real GitHub Pages / PWA Cliente:** https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/
- **Web premium Cloudflare:** https://traslados-web.marcelof-gx.workers.dev/
- **Calculadora pública Cloudflare:** https://traslados-web.marcelof-gx.workers.dev/distancia
- **Repositorio Android:** https://github.com/marcelofgx-ctrl/traslados-android
- **Repositorio Web Cloudflare:** https://github.com/marcelofgx-ctrl/traslados-web
- **Actions Android:** https://github.com/marcelofgx-ctrl/traslados-android/actions
- **Actions Web:** https://github.com/marcelofgx-ctrl/traslados-web/actions

**Nunca volver a entregar la raíz GitHub Pages como si fuera el formulario.** Se confundieron las URL, el usuario vio el índice del proyecto y no pudo probar la reserva. La estrategia correcta es mantener ambas rutas por separado. El workflow de Pages usa **publicación desde `main`/raíz**, mientras el workflow `deploy-web-pasajero-pwa.yml` verifica/empaca; no instalar dos despliegues de Pages que se sobrescriban.

---

## 2. Experiencia visual y funcional aprobada

- Estética premium petróleo/verde grafito/champagne/dorado **legible** (evitar marrones de bajo contraste), superficies finamente texturadas, bordes suaves, tarjetas compactas, botones accesibles y sin superposiciones.
- **Móvil primero:** el usuario utiliza Samsung Android y comparte capturas; diseñar y probar desplazamiento, teclado, controles flotantes y anchuras ~360–420 px. Los círculos flotantes de Uber y de Mapa pueden ser **overlays de Android**, no HTML; no atribuirlos sin análisis al CSS.
- **Reserva progresiva:** elegir Origen → contraer, elegir Destino → contraer; resumen prioritario Origen/Destino/Km por ruta/Minutos; paradas, datos extra y mapa en desplegables. «Editar», «Invertir», «Ver Google Maps», acceso a itinerario y confirmación sin tarjeta interminable.
- **Búsqueda Uruguay integral:** calles y números (IDE Uruguay) + lugares por nombre (índice OSM de >22.000 POIs) + Geoapify opcional si se configura. Buscar `Punta Carreta Shopping` → `Punta Carretas Shopping`, también Plaza Italia, aeropuertos, hoteles y hospitales sin agregarlos manualmente uno por uno. Departamentos priorizan; **NO filtran** otros destinos uruguayos. Confirmar el acceso físico mediante mapa si importa la puerta exacta.
- **Rutas:** separar línea recta de kilómetros por calles. Cloudflare expone `/api/public/route-estimate`; usa ORS mediante secreto servidor `ORS_API_KEY` **si está configurado**. Sin proveedor, mostrar salida honesta a Google Maps, **no inventar kilómetros ni minutos**. La PWA Pages ya consulta este endpoint Cloudflare. El endpoint fue actualizado para permitir origen CORS específico GitHub Pages.
- **Estados de reserva:** solicitud `PENDIENTE`, presupuesto enviado, aceptada, confirmada, en viaje, finalizada, cancelada/rechazada. Presupuesto enviado por **Conductor**, cliente puede aceptarlo/rechazarlo según estado; no anunciar precio final automático antes de confirmación.
- **Historial:** próximos vs. pasados, búsqueda, filtros por año/mes/estado/periodo, año → mes → semana → reservas plegables, repetir recorrido, exportar cuando esté soportado por frontend.
- **Acceso:** teléfono/PIN en cliente legado y flujos WebAuthn/passkeys preparados en web premium. `PASSKEY_AUTH_ENABLED` solo indica configuración: no equivale a prueba biométrica completa en Android. No registrar credenciales públicas inseguras ni revelar PIN.
- **Audio y hábitos Conductor/Mapa:** conservar MP3 elegidos por usuario, atajos de WhatsApp/tarjeta VCF, menú ✦, flecha flotante, slider de jornada, import/export y apariencia R20–R24. No introducir sonidos genéricos ni tocar automáticamente datos SQLite.

---

## 3. GitHub Pages Pasajero: incidente y últimas correcciones

**Incidente real del 09/10/2026:** al pulsar confirmar, la PWA mostró `function gen_random_bytes(integer) does not exist`. En Supabase el proveedor criptográfico está en el esquema `extensions`; se corrigió `public.generate_reservation_code()` y `public.create_reservation(...)` para usar **`extensions.gen_random_bytes(...)`** y `search_path` explícito mediante migración aplicada `fix_public_reservation_crypto_schema_20261009`. La generación de código se probó bajo rol `anon`. **No afirmar que se creó una reserva completa real hasta ejecutarlo desde el teléfono con autorización.**

**UI de revisión corregida:** panel compacto con Origen/Destino, fecha y hora, pasajeros, estado presupuesto «a confirmar», Maps y datos privados plegados. CTA **«ENVIAR SOLICITUD AL CONDUCTOR»** en vez de sugerir viaje confirmado. PWA con caché red-primero para código CSS/JS, modo offline sin reservas.

**Cambios posteriores presentes en `main`:** `web-pasajero/geo-search.js`, `web-pasajero/data/uy-pois.json`, interfaz de recorrido km/min, enlace Maps, búsqueda nacional por lugares, inicio de selección compacto. `.github/workflows/smoke-public-passenger.yml` comprueba HTTP real, recursos y que el índice tenga los POIs clave.

**Evidencia último corte:**
- GitHub Pages: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38005504188 — **success**.
- Smoke público: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38005505447 — **success**.
- Comprobaciones estáticas/paquete: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38001065374 — **success**.
- Aun con HTTP correcto, queda **prueba humana de envío de reserva, PIN, presupuesto y revisión móvil**. Evitar generar múltiples pedidos reales al probar.

**Regla de publicación:** la raíz del sitio es el índice y `/web-pasajero/` la reserva; no cambiar ese contrato. Un éxito de `build`/smoke no demuestra que todos los estados de negocio funcionen.

---

## 4. Android Conductor / Mapa: referencia y estado

- **Fuente principal de detalle:** [RETOMAR_MAPA_TRAYECTOS.md](RETOMAR_MAPA_TRAYECTOS.md) y [HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md](HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md). El contexto largo histórico [CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md](CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md) **no debe reemplazar las verificaciones en main**.
- Mapa `mapatrayectos/build.gradle`: `versionName '0.1-R24.3'`, `versionCode 45` observado en este corte. Package `uy.com.mapatrayectos`; Android Java/Gradle/SDK35; release firmada.
- Mapa R24.3: corrección del diálogo **Preparar presupuesto** del Conductor integrado ante teclado Samsung; UI scrolleable y acciones visibles. GitHub Actions **37924921923** success; APK respaldada en Drive según el documento RETOMAR. Falta prueba física definitiva.
- Conductor integrado se empaqueta desde `conductor/` mediante `prepareEmbeddedConductor`; dentro de Mapa están reservaciones, presupuesto, estados y monitor. R24.2 introdujo Centro rápido de reservas/agenda/badges y R24.1 menú/burbuja más compactos.
- Conductor independiente sigue como **respaldo**. Último workflow **Build Traslados Conductor v9.2** falla en **38005505455** (y runs previos): **diagnosticar logs antes de usarlo o recomendar su APK**. No afirmar que Mapa falló por ese build.
- Cliente Android nativa 11.5-R10 documentada firmada: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37945735891 . `cliente-pasajero/` contiene scripts/fuentes extra. Consultar los workflows/artefactos más recientes si se pide APK.
- Las alertas de Conductor por ahora consultan Supabase periódicamente (~15 s, si Android permite el servicio), **NO son push FCM garantizados**. No prometer recepción con app forzada a detener. FCM y GPS público vivo siguen pendientes de producto.
- **Datos sensibles:** conservar SQLite, historiales, sonidos importados, versiones instaladas, PIN, SharedPreferences y firma APK. No desinstalar ni borrar datos para actualizar sin backup/permiso.

---

## 5. Backend único, seguridad y límites

**Supabase operativo:** `zetaudvvutlouiqxopvg` (NO el antiguo proyecto Lovable `xetklwcxebcpnkrdrmaa`). El secreto de servicio **NUNCA** se sube a GitHub ni se inserta en Android/PWA. Las variables Cloudflare `PASSKEY_SUPABASE_SERVICE_ROLE_KEY`, `ORS_API_KEY`, `GEOAPIFY_API_KEY` se guardan como secretos en servidor (pueden no estar configuradas: verificar existencia, **no pedir sus valores**). `wrangler.jsonc` conserva origen público WebAuthn y feature flag, no claves.

Funciones/Supabase que requieren compatibilidad: `create_reservation`, `get_reservation_by_token`, `customer_create_reservation_v12`, `customer_list_reservations_v12`, `customer_check_availability_v11_4`, `customer_get_available_slots_v11_4`, `customer_quote_decision_v11_4`, `driver_list_reservations_v2`, `driver_set_status_v2`, RPC de presupuestos, sesiones y passkeys. Revisar esquemas/firmas actuales directamente antes de invocarlas; no depender solo de este inventario.

**No borrar registros reales ni duplicar una reserva para pruebas** sin acuerdo expreso. Versionar migraciones aditivas; proteger las políticas RLS y la titularidad de tokens. La PWA no expone tokens en URL pública ni crea reservas offline. Los servicios de rutas sin clave o en modo demo no son fiables para prometer km.

---

## 6. Solicitudes «Ahora», «En 10 min» y presencia del conductor

**Objetivo acordado:** el pasajero puede elegir «Ahora», «En 10 min» o «Programar». Para recogidas próximas se necesita mostrar **distancia y minutos de conductor → origen** y separarlos de **origen → destino** (tiempo/distancia del propio viaje).

**AÚN NO IMPLEMENTADO COMO DISPONIBILIDAD REAL END-TO-END:**
1. GPS de `TrackingService.java` de Mapa con consentimiento explícito de compartir disponibilidad comercial, envío autenticado de posición fresca al backend y estado manual **DISPONIBLE / OCUPADO / PAUSADO / FUERA DE SERVICIO**.
2. Vincular el dispositivo a conductor autorizado; nunca permitir que un ID auto-registrado publique ubicación del conductor.
3. El conductor puede estar ocupado por Uber/Cabify aunque Supabase no tenga reserva: **no deducir libre por agenda vacía**.
4. Lat/lng exactos deben permanecer en servidor privado. Entregar al pasajero **solo km/ETA y edad de dato**; invalidar GPS caducado (referencia de diseño 90 s), sin rastreo público.
5. Calcular la ruta por carretera conductor→origen en servidor y verificar que ETA + margen permita llegada «En 10 min». Antes de aceptar, chequeo transaccional de conflictos y confirmación del conductor.
6. Sin datos frescos o proveedor de rutas, mostrar `Disponibilidad no confirmada` y ofrecer WhatsApp/programación. No fabricar «Conductor libre» ni «7 minutos».

**Estado web Premium v16.4 en código:** selector visual «Ahora / En 10 min / Programar» y consulta WhatsApp incluyendo itinerario; km/min de trayecto mediante motor compartido si ORS responde. **La opción urgente no constituye reserva automática confirmada.**
**Documento técnico completo:** en `traslados-web/docs/traslados-premium-booking-pickup-2026-10-09.md`.

---

## 7. Diseño de próximos trabajos (priorización)

**P0 — operacional y comercial**
- Probar **una** reserva real controlada desde `/web-pasajero/` → verla en Mapa/Conductor → enviar presupuesto → cliente acepta/rechaza → comprobar estados e historial; confirmar que desapareció `gen_random_bytes` y no se duplican reservas.
- Probar UX Android en Samsung con capturas: búsqueda «Punta Carretas Shopping», «Plaza Italia Shopping», Aeropuerto Carrasco, origen/destino compacto, km/min o Maps, teclado sin tapar CTA, contraste.
- Si falla la distancia en la PWA, verificar Cloudflare ruta `/api/public/route-estimate`, proveedor ORS y CORS autorizado; no crear falsos km.
- Corregir build Conductor independiente **solo si** sigue siendo entregable necesario; no romper la APK Mapa principal.

**P1 — integración Mapa ⇄ Web**
- Implementar GPS/estado del conductor con consentimiento, seguridad, frecuencia, caducidad y privacidad.
- Crear endpoint para km/ETA de recogida que **no** exponga GPS exacto.
- Conectar el modo inmediato con agenda/conflictos y aceptación real.
- Probar Android en segundo plano, energía y pérdida de conexión; no asegurar FCM sin Firebase/backend configurados.

**P2 — mantenimiento y calidad**
- Reducir duplicación de lógica UI entre PWA GitHub Pages y web Premium Cloudflare sin romper acceso legado a clientes.
- Unificar criterios de diseño premium, compactación progresiva, filtros y presupuesto en las tres superficies.
- Revisar métricas de uso y cuotas de proveedores gratuitos antes de abrir al público.
- Mantener trazabilidad de versiones, SHA-256 y archivos Drive/Actions.

---

## 8. Convención de trabajo entre sesiones (OBLIGATORIA)

1. **Leer este documento primero** y seguir enlaces técnicos especializados según el componente solicitado. No pedir al usuario que repita contexto ya guardado.
2. **Verificar `main` y Actions** de ambos repos antes de editar: buscar fallos actuales, tests verdes y si GitHub Pages/Cloudflare entregan el commit esperado. Comprobar la web **correcta**: raíz índice ≠ `/web-pasajero/` ≠ Cloudflare.
3. En cambios multi-repo, mantener **un contrato común** de esquemas/RPC y registrar ambos commits; no fingir que dos frontends se actualizan solos.
4. Nunca dar como terminado algo sin clasificarlo: **PROYECTADO**, **IMPLEMENTADO EN CÓDIGO**, **COMPILADO**, **PUBLICADO**, **PROBADO EN SAMSUNG**, **PROBADO END-TO-END EN SUPABASE**.
5. No usar números ni ETA ficticios como datos reales, no crear reservas de prueba sin consentimiento, no meter secretos en repo público. Nunca borrar SQLite/MP3/reservas ni romper firma de APK.
6. **Al terminar una tanda de cambios**, actualizar **este archivo** (fecha del corte, estado, link a commits/CI y pendientes), más el histórico especializado si procede. No crear infinitos «contexto-v2-v3-v4» ni depender de sesiones previas.
7. Si el archivo se vuelve largo, conservar aquí lo **vigente y navegable**; enviar detalles cerrados a documentos especializados. El historial no se pierde porque está versionado en Git.
8. Para reanudar, basta enlazar este documento en una conversación nueva. La IA podrá consultar GitHub si tiene el conector disponible; si no, abrir el enlace o pegar su contenido.
9. Prefiera un PR pequeño con CI y revisión antes de fusionar si toca producción; preservar comportamiento visual aprobado y tests.
10. Al entregar trabajo, informar **qué repos, archivos, commits y URLs** cambiaron, qué comprobó GitHub Actions, qué se publicó y qué requiere prueba del usuario.

---

## 9. Fuentes técnicas y evidencias

**Android / Mapa**
- [Retomar Mapa Trayectos y entregar APK](RETOMAR_MAPA_TRAYECTOS.md) — versiones firmadas, fixes IME, QA.
- [Historial Android](HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md) — histórico de releases, SHA y pruebas.
- [Contexto histórico Mapa](CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md) — decisiones anteriores.
- [Estado integrado de apps](ESTADO_INTEGRADO_CLIENTE_WEB_PWA_CONDUCTOR.md) — compatibilidad y Supabase.
- [PWA Cliente — instrucciones](../web-pasajero/README.md) — publicador, scripts, claves públicas y UX.
- [Mapa RELEASE CI](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924921923) — última RELEASE principal conocida.
- [Smoke GitHub Pages](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38005505447) — rutas públicas verificadas.

**Web Cloudflare**
- [Repositorio y readme](https://github.com/marcelofgx-ctrl/traslados-web).
- [Guía de rediseño y recogidas](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/traslados-premium-booking-pickup-2026-10-09.md).
- [CI última web](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38000741722).
- Migraciones SQL de web en `traslados-web/supabase/migrations/`; contrastar su estado con **Supabase producción**, no deducir que están aplicadas solo por estar versionadas.

**Regla final:** Esta es la fuente **maestra de coordinación**, pero **no sustituye verificar el presente**. Si alguien cambia un repositorio después del corte, registrar ese cambio aquí antes de dar por terminado el trabajo.
