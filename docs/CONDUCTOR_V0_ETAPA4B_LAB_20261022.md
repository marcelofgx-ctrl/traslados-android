# Quick Trip — etapa 4B, laboratorio aislado (22/10/2026)

## Alcance y límites autorizados
Aprobado exclusivamente desarrollar contratos experimentales y pruebas de identidad de Conductor V0 sobre rama aislada `feat/conductor-v0-etapa4b-lab-20261022`, basándose en la etapa 3 commit `d8b772d30628cfbffd0dcbe171cd5b58d97e0ded`.
**No:** cambios a Supabase real, proyectos remotos, tráfico a servidores de reservas, cambios de `main`, PR/merge, APK, publicación, despliegue, alteración del móvil instalado.
El proyecto Supabase productivo no se contacta desde este laboratorio: la CI usa solo una instancia PostgreSQL 16 efímera local al runner. Sin claves, datos ni cuentas productivas.

## Separación de pruebas
1. `AuthContract.java` y `AuthContractTest.java`: lógica JVM pura de aceptación/rechazo de identidades **ficticias** (firma declarada verificada mediante un booleano de prueba), caducidad, revocación, conductor activo, asignación, modo inmediato y precio fijo. **No validan firmas JWT criptográficas.**
2. `schema.sql`: replica solo el subconjunto necesario del modelo de autorización (drivers, reservations, eventos, idempotencia). Define funciones `lab_api.driver_session_status_auth_v1`, `driver_list_direct_auth_v1` y `driver_decide_direct_auth_v1`, y usa RLS con roles falsos e identidad simulada. La función `auth.uid()` de esta base de prueba **lee un claim artificial de sesión** inyectado exclusivamente por el harness. PostgREST/Supabase real NO está presente.
3. `tests.sql`: permisos anónimo/autenticado, RLS, otro conductor, cuenta inactiva, ausencia de sesión, decisiones, idempotencia, precio fijo, disponibilidad fingida, expiración y eventos de auditoría con únicamente UUID inventados.
4. Compilación Android `compileDebugJavaWithJavac` de regresión sin tareas de empaquetado.

## Diferencias deliberadas con producción
- Sin Supabase Auth ni validación criptográfica JWT real. El controlador futuro deberá delegar verificación real a Supabase, incluyendo renovación y revocación de sesiones.
- Disponibilidad simulada mediante columna booleana `available`: **no sustituye** `availability_route_check_v11_4` ni la regla de turnos/jornada.
- Modelo con oferta asignada a `driver_id`, sin tabla multiconductor. Las reservas sin asignación quedan inaccesibles; rechazo global solo es admisible para un único conductor, antes de evolucionar hacia rechazos por oferta.
- No se mezclan puntos GPS, cuentas reales ni históricos de producción.
- Los SQL de laboratorio NO son migraciones y **no deben copiarse directamente al Supabase real**.

## Futuro backend, pendiente de aprobación separada
Conservar motor actual `driver_decide_fixed_pickup_v1` como referencia semántica (bloqueos, precio fijo, disponibilidad, 90 min, resultado del pasajero). Implementación nueva deberá verificar JWT Supabase, identidad `drivers.id=auth.uid()`, vinculación a solicitud y sesión, transacciones e idempotencia. Revisar privilegios UPDATE/TRUNCATE, triggers y `SECURITY DEFINER` previo a activar.
Ninguna credencial `mqs1` ni PIN heredado puede pasar por una sesión personal JWT.

## Rollback del laboratorio
No existen datos productivos que restaurar. Borrar/recrear base efímera de CI, descartar branch por aprobación posterior, no alterar `main`. Mantener reportes y código de pruebas para revisión. Si falla aislamiento, no correr integración.
