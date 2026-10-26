# Quick Trip v90 — Contrato de aislamiento de solicitudes Web ↔ Mapa QT QA
Estado: **DISEÑO PREPARADO, SIN ACTIVAR** · 2026-10-26 (Uruguay)
Base inspeccionada: `453bc8f308093eb7d630ceb21ed94431b1f31fd1` · rama `qa/quicktrip-v90-acceleration-pilot-20261026`
No hay pruebas reales de recepción Android de este circuito.

## Hechos verificados
- Repositorio: `marcelofgx-ctrl/traslados-android`. `main` al inicio del diseño: `53dd427d5bda65e84f585b4db2c565227aff673c`.
- Arquitectura: `:mapatrayectos` (única APK Mapa QT) **integra** clases de `:conductor` mediante `prepareEmbeddedConductor` (sin construir nueva APK Conductor).
- Identificador estable: `uy.com.mapatrayectos`. Actualmente `mapatrayectos/build.gradle` NO define variante de QA ni `applicationIdSuffix`.
- `conductor.Api.BASE` y `mapatrayectos.Api.BASE` apuntan hoy al Supabase operativo. `RemoteAppConfig` depende de `mapatrayectos.Api.BASE`. En `TrackingService` se publica presencia por `Api.publishPickupPresenceAsync`.
- Conductor existente lee `driver_list_reservations_v3`, actualiza `driver_set_status_v2`/`driver_set_pickup_phase_v1` y `ReservationMonitorService` consulta cada 15 s (en la APK actual, servidor operativo). La QA Web B hoy genera exclusivamente un estado local, sin solicitud a servidor.
- Proyecto Supabase operativo Traslados: `zetaudvvutlouiqxopvg`; NO se encontró un proyecto QA de Traslados entre los proyectos conectados. No reutilizar el otro proyecto ajeno. PR #197 está abierto y sin integrar.
- QA Web usa CSP de lectura y `form-action 'none'`; cambios de escritura QA requieren autorización explícita y únicamente endpoint QA.

## Arquitectura objetivo
```
Quick Trip v90 QA (Web/PWA, dominio fijo Vercel)
   | create(idempotency_key, origen, destino, precio QA, token pasajero)
   v
Backend QA SEPARADO (nuevo proyecto Supabase o rama persistente aislada)
   | persistencia sintética + transición autorizada + eventos privados
   v
Mapa QT QA (misma APK funcional con Conductor integrado)
   | bandeja QA de solicitudes, aceptar/rechazar, fases, avisos
   +--> Backend QA ----> Quick Trip QA (estado actualizado)
```
**No usar** `reservations` ni los RPC del proyecto operativo para pruebas. El código de negocio probado en `trip-board.js`, `owned-pickup.js`, `reservation-live.js` y `premium-map.js` se **reutiliza mediante adaptadores**; no editar motores estables.

## Aislamiento obligatorio
1. Backend QA: instancia y credenciales separadas; SIN clonar reservas, usuarios, PIN ni GPS de producción. Datos sintéticos/consentidos solamente.
2. `qa_requests`: UUID, estado `PENDIENTE|ACEPTADA|RECHAZADA|EN_CAMINO|EN_PUNTO|EN_VIAJE|FINALIZADA`, origen/destino de prueba, expiración, created/updated, idempotencia. `qa_request_events`: transiciones trazables y ordenadas. Acceso solo a su pasajero y conductor QA emparejado.
3. Mutaciones exclusivamente por Edge Functions QA o RPC QA autorizadas, con autenticación, control de rol, rate limit, idempotencia y validación ATÓMICA de transición. Nunca meter credenciales administrativas en Web ni APK.
4. Seguimiento: inicio sin GPS privado, posición solo si conductor QA consiente expresamente y con vigencia/exactitud y acceso por solicitud aceptada. Sin GPS disponible: mostrar estado, NO triángulo como GPS real. Mapa de ruta contractual reutiliza geometría vial ya aprobada.
5. Android: añadir variante en `:mapatrayectos` con `applicationIdSuffix '.qa'` (paquete `uy.com.mapatrayectos.qa`), nombre/ícono QA, datos/permisos/notificaciones separados; no reemplazar APK estable. NO crear APK Conductor separada.
6. En build QA TODAS las salidas Android hacia Traslados deben apuntar al backend QA o desactivarse: ambas `Api.BASE`, sincronización de jornada/presencia, registros, cambios de estado, monitor, configuración remota y servicios embebidos. Incluir auditoría de cadenas/manifest de APK para verificar ausencia del hostname operativo y probar bloqueo de cualquier escritura hacia producción. No bastan un cambio de ícono o `applicationIdSuffix`.
7. Web QA: adaptar envío remoto para QA solamente; mantener consultas de disponibilidad/ETA públicas previas solo de lectura si corresponde. CSP restringida a endpoint QA. Marcar cada solicitud como `QA · NO RESERVA REAL`; la versión Web de producción no se altera.
8. Alertas QA: reutilizar monitor integrado y canal de notificaciones de `Mapa QT QA`, con eventos idempotentes y reconciliación por consulta al reconectar; separar visualmente de producción.

## Entregas y puertas
- **Aprobación pendiente A:** crear entorno backend QA separado (costos/cuota y opción de proyecto vs rama persistente), desplegar esquema mínimo + funciones/autenticación + datos ficticios. Sin copiar producción.
- **Aprobación pendiente B:** modificaciones en rama QA Android (flavor/BuildConfig/adapter y bloqueo de endpoints productivos) + adaptación Web QA; sin tocar `main`.
- **Aprobación pendiente C:** compilar/firmar APK Mapa QT QA e instalar junto a estable; habilitar notificaciones/permisos separados y ejecutar una única prueba física de extremo a extremo.
- **Aprobación posterior independiente:** usar GPS real/seguimiento privado en QA por consentimiento; promoción a producción requiere nueva autorización y QA de seguridad.
- Pruebas mínimas por etapa: (i) ningún write al proyecto operativo, (ii) una solicitud QA produce un único evento, (iii) aceptación/rechazo y fases sincronizan en ambos teléfonos, (iv) reintento y reconexión no duplican, (v) cancelar/cerrar revoca acceso, (vi) pérdida de GPS no inventa vehículo.
- **Prohibido por este contrato:** alterar Supabase operativo, clonar datos personales, migrar PR #197 a producción, reemplazar APK estable, fusionar/desplegar main, crear un Conductor APK independiente.

## Costo y capacidad verificados 2026-10-26 (sin compra)
- Organización consultada: `Marcelo Fernandez`, ID `fnqaguiyhslevpkxabwj`, plan **Free**.
- Proyecto operativo `Traslados`: ACTIVO; otro proyecto `Oak & Ember`: ACTIVO. **Dos de dos proyectos Free ocupados.**
- API Supabase `get_cost(type=project)`: **USD 0/mes cotizado**, PERO esa cotización no elimina el límite de dos activos. **No afirmar que se puede crear un tercero gratis.**
- API Supabase `get_cost(type=branch)`: **USD 0.01344/h**, sujeto a condiciones de disponibilidad de Branching y del plan; no crear sin aceptación del gasto.
- Fuente oficial https://supabase.com/pricing: Pro desde USD 25/mes y cómputo Micro adicional desde USD 10/mes por proyecto. Con tres proyectos Micro activos y un crédito mensual de USD 10, **estimación ilustrativa USD 45/mes** (USD 25 + USD 30 - USD 10), sin extras; no es factura cotizada por Supabase de la actualización del plan.
- Opciones NO AUTORIZADAS: pasar a Pro, pausar `Oak & Ember` para liberar cupo, crear branch facturable, crear nuevo proyecto en otra cuenta ni modificar Traslados operativo.
- No correr SQL ni habilitar proyecto hasta elegir una opción, obtener cotización aplicable y confirmar por separado cualquier cargo.
- Esquema listo pero **SIN EJECUTAR**: [QA_CANAL_SOLICITUDES_SCHEMA_PROPUESTO.sql](QA_CANAL_SOLICITUDES_SCHEMA_PROPUESTO.sql), fuera de `supabase/migrations`. Comprende `qt_qa_members`, `qt_qa_requests` y `qt_qa_request_events`, RLS denegada por defecto y grants exclusivos de `service_role`. La ejecución posterior exige Edge Functions autenticadas, transiciones atómicas y conexión solo al proyecto QA.

## Decisión a resolver antes de implementación
Conservar el aislamiento fuerte del proyecto separado cuando exista cupo/plan confirmado; no copiar datos ni usar el proyecto ajeno. El siguiente paso es aprobación explícita del usuario sobre la opción de costos, NO creación automática.
