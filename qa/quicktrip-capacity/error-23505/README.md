# Quick Trip — Contrato de errores 23505 (QA aislado Android/PWA)
**24/10/2026 · NO INTEGRAR / NO DESPLEGAR · datos 100 % ficticios**

## Autorización y aislamiento

La rama de QA `qa/quicktrip-23505-android-pwa-contratos-20261024` parte del commit documental `ada167bd7cf6f98e036694648285b74178af5098`. Solo añade un workflow y archivos en `qa/quicktrip-capacity/error-23505/`. Ningún Java del Android productivo, código PWA publicado, RPC real, SQL de migración, GPS, archivo APK/AAB o dato de reserva fue modificado. No se crean PR ni fusiones. El CI usa Node.js y `javac/java`, **NO ejecuta Gradle, assemble*, Supabase ni ningún build APK**.

## Contrato candidato (NO integrado)

Un error se clasifica como **CONDUCTOR_OCUPADO** exclusivamente si:
- Respuesta estructurada del backend indica PostgreSQL SQLSTATE exactamente `23505`.
- El mensaje de PostgreSQL identifica explícitamente el constraint/index **entre comillas** `"qt_one_active_service_uq"`.
- En los demás casos, incluido `23505` de `reservations_code_key`, `reservations_public_token_key`, `reservations_urgent_request_id_idx`, errores HTTP de red, un texto con nombre parecido, o un mensaje de horario, **NO** etiquetar "Conductor ocupado".
- La forma típica de PostgREST es JSON `{"code":"23505","message":"duplicate key value violates unique constraint \\"qt_one_active_service_uq\\"","details":"...","hint":null}`, HTTP 409. El HTTP 409 **solo no basta** para asignar "ocupado".
- Mensaje de referencia: **Conductor ocupado: terminá o cancelá el servicio anterior antes de aceptar otro.**

## Qué se probó

1. `cases.tsv`: matriz idéntica que recorren Java puro y JavaScript puro, incluyendo positivos y negativos, sufijos/prefijos de nombre, nulls, otro código de unicidad, error de horario.
2. `CapacityErrorContract.java` y `CapacityErrorContractTest.java`: compilan con JDK aislado; **son referencias puras, no clases del APK**.
3. `capacity-error-contract.js` y `test_pwa_contract.cjs`: prueban el mapeo referencial; además cargan por `node:vm` **el `web-pasajero/rpc-client.js` REAL del repositorio**, con HTTP mock y la función real `showError` extraída de `app.js`. Constatan (brecha conocida) que el RPC actual tira `Error(message)` pero **pierde `parsed.code`**; no transforma la colisión de capacidad. Prueban que un timeout no reintenta automáticamente.
4. `test_android_existing.cjs`: extrae y ejecuta con `javac/java` el cuerpo **real y no modificado** `Api.publicMessage` de `conductor/Api.java` con una `ApiException` simulada. Constata que se conserva `backendCode`, pero el error nuevo hoy queda **genérico** en el callback real `TransferAlerts`. Verifica que el error horario mantiene su texto; no toca `android.*`.
5. El workflow revisa un diff EXACTO de archivos de QA contra el commit base; no tiene secrets, service_role ni conexiones a Supabase.

## Criterios de éxito y límites

**PASS en CI** significa: **referencia de contrato comprobada** + **comportamiento ACTUAL de los clientes medido y brechas reproducidas**. **NO significa que Android o PWA estén arreglados ni que el APK instalado muestre el nuevo mensaje.** No se compila el Android ni se valida interfaz real. El contrato candidato solo puede integrarse en una etapa posterior con autorización distinta; después sería necesaria validación con errores estructurados y regresiones de cliente Android/PWA, preservando idempotencia, horarios y mensajes de red.

La rama no crea acciones que publiquen cambios. PR #195 y #197 deben seguir sin fusionar. Última referencia del índice: `qa/quicktrip-capacity/draft_atomic_guard.sql` (V3).

## Etapa de integración aislada de mensajes — 24/10/2026

**Rama:** `feat/quicktrip-23505-ui-errors-isolated-20261024` (basada en `200ee63eb127a0b882e9b4aba20837d077a6142a`). El contrato ya no es solo referencia: **se aplicó exclusivamente en las rutas de presentación/transporte de errores en una rama NO fusionada**.

- **Android:** `conductor/Api.java` traduce a "Conductor ocupado" solo el SQLSTATE `23505` con `constraint "qt_one_active_service_uq"` o `index "qt_one_active_service_uq"` en `backendMessage`. Respeta mensajes existentes de PIN, horario y red. El callback existente de `TransferAlerts` no se modifica y reutiliza `Api.publicMessage`. Ninguna APK fue compilada.
- **PWA:** `web-pasajero/rpc-client.js` conserva `parsed.code` como `Error.code`; `web-pasajero/app.js` usa el código estructurado más nombre exacto para informar al pasajero que el conductor está ocupado. No cambia la URL ni el POST, la gestión de sesión, el tiempo de espera ni las reglas de reintento.
- Los tests de JavaScript cargan **archivos reales de la rama aislada** en VM, con respuestas HTTP ficticias y las 14 filas de la matriz, y verifican que un timeout no reenvía solicitudes. Los tests de Android **extraen y compilan solo el método `publicMessage` real** en un harness JDK ficticio; no compilan `Api.java` con Android ni APK.
- El CI nuevo `.github/workflows/quicktrip-error-integration-isolated.yml` comprueba exactamente los siete archivos autorizados frente a `200ee63eb127a0b882e9b4aba20837d077a6142a`. Se prohíben cambios a `mapatrayectos/`, `supabase/`, módulos de reserva/disponibilidad, GPS o workflows de despliegue.
- **Limitaciones:** es una integración en código aislado, no integrada a PR #195/#197 ni publicada. No se ensaya un dispositivo Samsung, backend PostgreSQL real o PWA instalada. Cualquier decisión de integrar/compilar/publicar requiere una autorización posterior y separada.
