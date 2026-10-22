# Quick Trip / Mapa Trayectos — Conductor V0 Etapa 3 (22/10/2026)

## Autorización explícita
Desarrollar solamente en rama `feat/conductor-v0-etapa3-simulacion-20261022` la proyección del contrato existente de viajes inmediatos y el uso SIMULADO de Aceptar/Rechazar, sin solicitudes ni operaciones reales. Prohibido: modificar Supabase, fusionar a `main`, generar APK, publicar, desplegar y alterar la instalación del usuario.

Origen: etapa 2 commit `4305f4617cb0c1a0750a6faf7ad68fef542a5af3`.

## Verificación de identidad y bloqueo operativo
El código Android existente manda `p_pin` para `driver_list_reservations_v3` y `driver_set_status_v2`. Se verificó además en los metadatos de funciones PostgreSQL del proyecto existente que ambas firmas exigen ese argumento; son funciones `SECURITY DEFINER`. Una clave de aplicación publicable no acredita identidad de conductor. El legado consulta `driver_session.pin` de SharedPreferences, que no debe reutilizarse silenciosamente como una solución de autenticación nueva.

**BLOQUEO DE SEGURIDAD:** no se desarrolló ningún adaptador a la red ni una vía real PIN-less; nada llama a esas RPC. El panel de producción mantiene `setListener(null)` y no recibe reservas reales. Cualquier conexión real queda pendiente de otro permiso y una solución de autenticación revisada.

## Trabajo realizado en rama
- `ImmediateTripRequest.java` es una proyección de SOLO LECTURA de los campos ya usados por el Conductor heredado: `id`, `status`, `quote_status`, `pickup_mode`, `quote_final_total`, `origin_text`, `destination_text`, `route_distance_km`, `route_duration_min`.
- Filtra estrictamente viajes de modalidad inmediata: `PENDIENTE`, `PRECIO_FIJO`, importe positivo y modo `now` o 1–120 minutos. No modifica presupuestos de viajes programados.
- `ImmediateTripSimulation.java`, exclusivamente dentro de `src/debug`, mantiene datos ficticios en memoria y simula las transiciones `ACEPTADA` / `RECHAZADA`. Rechaza duplicados, estados cambiados, importes inválidos, programados y errores simulados sin confirmar éxito falso.
- `ConductorV0MockBinding.java`, también exclusivamente en `src/debug`, enlaza la vista V0 y los botones con ese simulador. Contadores y tarjeta cambian a partir de la muestra sin operaciones reales.
- `ConductorV0PreviewActivity.java` emplea esa conexión solo en previsualización de depuración. La aplicación principal y el navegador Conductor V0 aprobados no se modifican.
- Pruebas JVM y compilación de clases Java en CI. Ningún `assemble`, `bundle`, `package` ni firma de APK.

## Lo que aún NO existe
- No hay autenticación PIN-less segura, ni lectura real de solicitudes ni cambio real de estado.
- No se verificó interacción física de botones ni gestos porque no se generó APK ni ejecutó emulador.
- No se sustituyó `TransferAlerts`, `ReservationMonitorService` ni la gestión legada.
- Los eventos «Aceptar/Rechazar» no llegan a usuarios: solo se modifica el estado ficticio de memoria dentro de un proceso de depuración.

## Alternativas a someter a autorización futura
A. Diseñar identidad de conductor con sesión autenticada (Supabase Auth/JWT, verificación de pertenencia de conductor, autorización en servidor y RPCs seguras). Requiere nuevo permiso para backend y pruebas de seguridad.
B. Autorizar login PIN una sola vez fuera de Conductor con custodia reforzada de credenciales, y revisar si las RPC actuales admiten ese patrón. No cumple una prohibición estricta de mostrar PIN nunca; requiere consentimiento distinto y auditoría.
C. Mantener el sistema legado como único gestor real y dejar V0 en modo visual/simulado mientras se diseña solución A.

## Aprobación pendiente
Antes de introducir un adaptador real, cambiar Supabase, fusionar, generar APK o desplegar, presentar resultados y solicitar aprobación separada.
